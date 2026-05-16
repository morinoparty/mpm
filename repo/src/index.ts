import { Hono } from "hono";
import {
    describeRoute,
    openAPIRouteHandler,
    resolver, validator,
} from "hono-openapi";
import { z } from "zod";
import { PluginInfoSchema } from "./type/plugin-info";

// PluginInfoSchemaのバージョン。スキーマを破壊的変更する際に上げる。
// 参照URL例: `https://repo.mpm.nikomaru.dev/schema/plugin-info/v1.json`
// v1系の後方互換性を保つ変更は v1 のまま更新し、破壊的変更時は v2 を新規追加する。
const PLUGIN_INFO_SCHEMA_VERSION = "v1" as const;

// ビルド時にPluginInfoSchemaからJSONSchemaを1度だけ生成し、
// `/schema/plugin-info/v1.json` で配信する（従来の `/schema/plugin-info.json` は
// 互換エイリアスとして最新版を返す）。
// これにより各プラグインJSONが `$schema` フィールドでIDEの補完・検証を受けられる。
const pluginInfoJsonSchema = z.toJSONSchema(PluginInfoSchema, {
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


app.get(
    "/paper/list",
    async (c) => {
        return c.json(list);
    },

    describeRoute({
        description: "現在時刻を返す",
        responses: {
            200: {
                description: "現在時刻を返す",
                content: {
                    "text/plain": { schema: resolver(z.string()) },
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

const requestSchema = z.object({
    pluginId: z.string().default("LuckPerms.json"),
});

app.get(
    "/paper/plugins/:pluginId",
    validator("param", requestSchema),
    async (c) => {
        const pluginId = c.req.param("pluginId");
        const plugin = list.find((p) => p === pluginId);
        if (!plugin) {
            return c.json({ error: "Plugin not found" }, 404);
        }
        return c.json(plugin);
    },
    describeRoute({
        description: "プラグイン情報を返す",

        responses: {
            200: {
                description: 'Successful response',
                content: {
                    'text/plain': {schema: resolver(PluginInfoSchema)},
                },
            },
        },
    }
));

app.route("/", openAPIRouter);

export default app;