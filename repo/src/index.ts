import { Hono } from "hono";
import type { Context } from "hono";
import {
    describeRoute,
    openAPIRouteHandler,
    resolver,
} from "hono-openapi";
import { z } from "zod";
import { PluginInfoSchema } from "./type/plugin-info";
import { DelegationIndexSchema } from "./type/delegation-index";
import type { Delegation } from "./delegation";
import {
    COLD_FETCH_TIMEOUT_MS,
    DELEGATIONS,
    findDelegationFor,
    isMarkedDown,
    markDelegationDown,
    refreshDelegation,
    viewDelegation,
} from "./delegation";

// PluginInfoSchemaのバージョン。スキーマを破壊的変更する際に上げる。
// 参照URL例: `https://repo.mpm.nikomaru.dev/schema/plugin-info/v1.json`
// v1系の後方互換性を保つ変更は v1 のまま更新し、破壊的変更時は v2 を新規追加する。
const PLUGIN_INFO_SCHEMA_VERSION = "v1" as const;

// DelegationIndexSchemaのバージョン。同じく破壊的変更時に上げる
const DELEGATION_INDEX_SCHEMA_VERSION = "v1" as const;

// /paper/list のキャッシュ寿命（秒）
const LIST_TTL_SECONDS = 300;

// ビルド時にPluginInfoSchemaからJSONSchemaを1度だけ生成し、
// `/schema/plugin-info/v1.json` で配信する（従来の `/schema/plugin-info.json` は
// 互換エイリアスとして最新版を返す）。
// これにより各プラグインJSONが `$schema` フィールドでIDEの補完・検証を受けられる。
const pluginInfoJsonSchema = z.toJSONSchema(PluginInfoSchema, {
    target: "draft-2020-12",
});

// 委譲先が置くバンドルのJSONSchema。委譲先の `$schema` から参照される
const delegationIndexJsonSchema = z.toJSONSchema(DelegationIndexSchema, {
    target: "draft-2020-12",
});
import dayjs from "dayjs";
import timezone from "dayjs/plugin/timezone.js";
import utc from "dayjs/plugin/utc.js";
import list from "../public/paper/_list.json"
import { trimTrailingSlash } from "hono/trailing-slash";
dayjs.extend(utc);
dayjs.extend(timezone);

import openAPIRouter from "./open-api";

const app = new Hono();

app.use(trimTrailingSlash());

openAPIRouter.get(
    "/openapi",
    openAPIRouteHandler(app, {
        documentation: {
            info: {
                title: "morinoparty mpm API",
                version: "1.0.0",
                description: "mpm repository for morinoparty",
            },
        },
    }),
);


/**
 * 第三者へのfetchを待たずに委譲先の更新を予約する。
 * executionCtx が無い実行環境（テスト等）では何もしない。
 */
const scheduleRefresh = (c: Context, delegation: Delegation) => {
    try {
        c.executionCtx.waitUntil(refreshDelegation(c.req.url, delegation));
    } catch {
        // executionCtx が利用できない環境では裏更新を諦める
    }
};

app.get(
    "/paper/list",
    async (c) => {
        // クライアントの isAvailable() は毎回 HEAD {url}/list を打つ。
        // Honoは HEAD を GET ハンドラで実行して本文を捨てる実装（hono-base.js:173）なので、
        // ここで短絡しないと疎通確認のたびに委譲解決のコストを払うことになる。
        if (c.req.method === "HEAD") {
            return c.body(null, 200);
        }

        // 中央の一覧に、委譲先の分を重ねる。
        // viewDelegation はローカルキャッシュとスナップショットしか見ないため、
        // 委譲先がダウンしていても一覧の件数は減らない（部分縮退させない）。
        // 一覧が縮むと getRepositoryFile は成功するのに mpm add だけ
        // 「リポジトリに見つかりません」で落ち、しかもクライアント側の3分キャッシュに焼き付く。
        const names = new Set<string>(list);
        for (const delegation of DELEGATIONS) {
            const view = await viewDelegation(delegation);
            for (const name of Object.keys(view.plugins)) {
                names.add(name);
            }
            scheduleRefresh(c, delegation);
        }

        c.header("cache-control", `public, max-age=${LIST_TTL_SECONDS}`);
        return c.json([...names].sort());
    },

    describeRoute({
        description:
            "利用可能なプラグイン名の一覧を返す。中央の定義に加えて、委譲先のプラグインも含む",
        responses: {
            200: {
                description: "プラグイン名の一覧",
                content: {
                    "application/json": { schema: resolver(z.array(z.string())) },
                },
            },
        },
    }),
);

/**
 * PluginInfo JSON Schemaを返すレスポンスを構築する。
 * レスポンスの `$id` と `x-schema-version` はリクエストされたURLとバージョンに合わせる。
 */
const buildSchemaResponse = (requestUrl: string, version: string) => {
    const url = new URL(requestUrl);
    return {
        ...pluginInfoJsonSchema,
        $id: `${url.origin}/schema/plugin-info/${version}.json`,
        "x-schema-version": version,
    };
};

// プラグインリポジトリファイルのJSONSchemaをバージョン付きパスで配信する
// 推奨URL: `/schema/plugin-info/v1.json`
// 将来の破壊的変更時は `/schema/plugin-info/v2.json` を別実装として追加する。
app.get(
    // NOTE: Honoの RegExpRouter は `:param{regex}.literal` 形式のパターンで
    // クラッシュするため、`.json` 部分を正規表現制約内に含めている。
    // その結果、`version` パラメータは `v1.json` の形で取得されるので末尾を取り除く。
    "/schema/plugin-info/:version{v\\d+\\.json}",
    async (c) => {
        const version = c.req.param("version").replace(/\.json$/, "");
        // 現時点では v1 のみサポート
        if (version !== PLUGIN_INFO_SCHEMA_VERSION) {
            return c.json(
                { error: `Unknown schema version: ${version}` },
                404,
            );
        }
        return c.json(buildSchemaResponse(c.req.url, version));
    },
    describeRoute({
        description:
            "プラグインリポジトリファイル(PluginInfo)の指定バージョンのJSONSchemaを返す。" +
            "現在は v1 のみサポート",
        responses: {
            200: {
                description: "PluginInfoのJSONSchema",
                content: {
                    "application/json": { schema: resolver(z.any()) },
                },
            },
            404: {
                description: "未知のスキーマバージョン",
                content: {
                    "application/json": { schema: resolver(z.object({ error: z.string() })) },
                },
            },
        },
    }),
);

// 後方互換エイリアス: バージョンを省略した場合は最新のサポート版を返す
// 既存のプラグインJSONは `$schema: .../schema/plugin-info.json` で記述されていたので
// 一定期間このパスも配信する
app.get(
    "/schema/plugin-info.json",
    async (c) => c.json(buildSchemaResponse(c.req.url, PLUGIN_INFO_SCHEMA_VERSION)),
    describeRoute({
        description:
            "プラグインリポジトリファイル(PluginInfo)のJSONSchemaを返す（最新サポート版へのエイリアス）",
        responses: {
            200: {
                description: "PluginInfoのJSONSchema",
                content: {
                    "application/json": { schema: resolver(z.any()) },
                },
            },
        },
    }),
);

// 中央に実体がある名前（public/paper/plugins/<id>.json）は Workers Assets が
// Workerより先に応答するため、このハンドラには到達しない。
// つまり「中央の明示定義が常に委譲に勝つ」がホスティング機構によって保証される。
// ここへ来るのは、中央に実体が無い名前だけである。
app.get(
    "/paper/plugins/:pluginId",
    async (c) => {
        const pluginId = c.req.param("pluginId").replace(/\.json$/, "");
        // URLパスセグメントとして安全な名前だけを扱う
        if (!/^[A-Za-z0-9_-]+$/.test(pluginId)) {
            return c.json({ error: "Invalid plugin id" }, 400);
        }

        // 委譲は深さ1固定。担当が見つからなければ確定的に不在
        const delegation = findDelegationFor(pluginId);
        if (!delegation) {
            return c.json({ error: "Plugin not found" }, 404);
        }

        // まずローカル（キャッシュ／スナップショット）だけで解決を試みる
        const view = await viewDelegation(delegation);
        const cached = view.plugins[pluginId];
        if (cached) {
            scheduleRefresh(c, delegation);
            c.header("x-mpm-resolved-via", view.via);
            if (view.via === "snapshot") {
                // 実データが取れず凍結コピーで応答していることを示す
                c.header("x-mpm-delegate-degraded", "1");
            }
            return c.json(cached);
        }

        // スコープ内だがローカルに無い＝委譲先で新しく追加された可能性がある。
        // この経路だけは実データの取得を待つ。
        if (await isMarkedDown(c.req.url, delegation)) {
            return c.json({ error: "Delegate unavailable" }, 503);
        }
        const fresh = await refreshDelegation(
            c.req.url,
            delegation,
            COLD_FETCH_TIMEOUT_MS,
        );
        if (!fresh) {
            // 到達不能を「不在(404)」と混同しない。連打を防ぐためマーカーを立てる
            await markDelegationDown(c.req.url, delegation);
            return c.json({ error: "Delegate unavailable" }, 503);
        }

        const resolved = fresh[pluginId];
        if (!resolved) {
            return c.json({ error: "Plugin not found" }, 404);
        }
        c.header("x-mpm-resolved-via", "live");
        return c.json(resolved);
    },
    describeRoute({
        description:
            "プラグイン情報を返す。中央に実体が無い名前は委譲先のindexから解決する",
        responses: {
            200: {
                description: "Successful response",
                content: {
                    "application/json": { schema: resolver(PluginInfoSchema) },
                },
            },
            404: { description: "プラグインが存在しない" },
            503: { description: "委譲先に到達できない" },
        },
    }),
);

// 委譲先が置くバンドルのJSONSchemaを配信する。
// 委譲先の index.json は `$schema` でこのURLを参照する。
app.get(
    "/schema/delegation-index/:version{v\\d+\\.json}",
    async (c) => {
        const version = c.req.param("version").replace(/\.json$/, "");
        if (version !== DELEGATION_INDEX_SCHEMA_VERSION) {
            return c.json({ error: `Unknown schema version: ${version}` }, 404);
        }
        const url = new URL(c.req.url);
        return c.json({
            ...delegationIndexJsonSchema,
            $id: `${url.origin}/schema/delegation-index/${version}.json`,
            "x-schema-version": version,
        });
    },
    describeRoute({
        description:
            "委譲先リポジトリのバンドル(DelegationIndex)のJSONSchemaを返す。現在は v1 のみサポート",
        responses: {
            200: {
                description: "DelegationIndexのJSONSchema",
                content: {
                    "application/json": { schema: resolver(z.any()) },
                },
            },
            404: { description: "未知のスキーマバージョン" },
        },
    }),
);

app.route("/", openAPIRouter);

export default app;