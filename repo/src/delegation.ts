/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import {
    type DelegatedPluginInfo,
    DelegationIndexSchema,
} from "./type/delegation-index";
import delegationsFile from "../public/paper/_delegations.json";
import snapshotsFile from "../public/paper/_snapshots.json";

/**
 * 中央の plugins/*.json から生成された委譲テーブル1件分。
 * gen-list が `_delegations.json` として書き出す。
 */
export type Delegation = {
    // 委譲元のプラグインid。委譲スコープは `${root}-` プレフィックスで導出される
    root: string;
    // 委譲先バンドルの絶対URL
    index: string;
    // 委譲先が名乗れる配布元のallowlist（"type:id" 形式、末尾 "*" のみワイルドカード）
    allowedSources: string[];
    // 廃止した委譲プラグイン名（スナップショット単調性チェックの解除用）
    retired?: string[];
};

// ビルド時にバンドルへ焼き込まれる委譲テーブル。
// 委譲を外す＝再デプロイで即座に参照されなくなる（キャッシュ経由で生き延びない）。
export const DELEGATIONS: Delegation[] = (
    delegationsFile as { delegations: Delegation[] }
).delegations;

// 委譲先indexの凍結コピー。委譲先がダウンしたときの「床」として使う。
// git でレビュー可能な形で中央にコミットされている。
const SNAPSHOTS = snapshotsFile as Record<
    string,
    { plugins: Record<string, DelegatedPluginInfo> } | undefined
>;

// 委譲先が名乗れるリポジトリタイプ。Kotlin側の RepositoryType と対応する
const ALLOWED_TYPES = new Set(["modrinth", "github", "spigotmc", "hangar"]);

// プラグイン名として許可する文字（Kotlin側 RemoteRepositorySource と同じ規則）
const PLUGIN_NAME_PATTERN = /^[A-Za-z0-9_-]+$/;

// 委譲先indexのキャッシュ寿命。委譲先のCache-Controlは信用せずここで上書きする
const LIVE_TTL_SECONDS = 300;
// 到達不能な委譲先へ連打しないためのネガティブマーカーの寿命
const DOWN_TTL_SECONDS = 60;
// 委譲先バンドルの最大サイズ（Content-Lengthは信用せずストリーム上で計数する）
const MAX_INDEX_BYTES = 1024 * 1024;
// 「scope内だがsnapshotにもcacheにも無い」名前を解決するときだけ待つ時間
export const COLD_FETCH_TIMEOUT_MS = 1500;
// バックグラウンド更新のタイムアウト
const BACKGROUND_FETCH_TIMEOUT_MS = 8000;

/**
 * `type:id` の allowlist エントリ1件と、リポジトリ設定1件を突き合わせる。
 *
 * GitHub の `owner/repo` は大小文字を区別しないため、比較は小文字化して行う。
 * ワイルドカードは末尾の `*` のみをプレフィックス一致として扱う。
 */
const matchesAllowedSource = (
    repo: { type: string; id: string },
    allowedSources: string[],
): boolean => {
    const actual = `${repo.type}:${repo.id}`.toLowerCase();
    return allowedSources.some((allowed) => {
        const pattern = allowed.toLowerCase();
        if (pattern.endsWith("*")) {
            return actual.startsWith(pattern.slice(0, -1));
        }
        return actual === pattern;
    });
};

/**
 * 委譲先から受け取ったindexを、信頼できる定義だけに絞り込む。
 *
 * ここが信頼境界であり、この関数を通らない定義は配信されない。
 * 条件を満たさないエントリは黙って捨てる（1件の不正で全体を落とさない）。
 *
 * @param delegation 委譲テーブルのエントリ
 * @param index 委譲先から取得した（zod検証済みの）index
 * @returns 配信して良いプラグイン定義のマップ
 */
export const sanitizeIndex = (
    delegation: Delegation,
    index: { plugins: Record<string, DelegatedPluginInfo> } | undefined,
): Record<string, DelegatedPluginInfo> => {
    if (!index) return {};
    const scopePrefix = `${delegation.root}-`;
    const out: Record<string, DelegatedPluginInfo> = {};

    for (const [name, def] of Object.entries(index.plugins)) {
        // キーと定義中のidが食い違うものは受け付けない
        if (name !== def.id) continue;
        // URLパスセグメントとして安全な名前だけを許可する
        if (!PLUGIN_NAME_PATTERN.test(name)) continue;
        // 委譲スコープの外（他プラグインのなりすまし）を弾く。
        // ルート名そのもの（例: "MineAuth"）はプレフィックス一致しないため常に除外される。
        if (!name.startsWith(scopePrefix)) continue;
        // 配布元が中央の許可リストに収まっているか検証する
        const sourcesOk = def.repositories.every(
            (repo) =>
                ALLOWED_TYPES.has(repo.type) &&
                matchesAllowedSource(repo, delegation.allowedSources),
        );
        if (!sourcesOk) continue;

        out[name] = def;
    }

    return out;
};

/** 委譲先indexのキャッシュキー。委譲先の絶対URLをそのまま使う */
const liveCacheKey = (delegation: Delegation) => new Request(delegation.index);

/**
 * ネガティブマーカーのキャッシュキー。
 * 自オリジン配下の合成URLを使う（Cache APIはhttp(s)のURLを要求するため）。
 */
const downCacheKey = (requestUrl: string, delegation: Delegation) => {
    const url = new URL(requestUrl);
    return new Request(
        `${url.origin}/__delegate-down/${encodeURIComponent(delegation.root)}`,
    );
};

/**
 * 委譲先が「落ちている」とマークされているかを返す。
 */
export const isMarkedDown = async (
    requestUrl: string,
    delegation: Delegation,
): Promise<boolean> => {
    const hit = await caches.default.match(downCacheKey(requestUrl, delegation));
    return hit !== undefined;
};

/**
 * 委譲先を一定時間「落ちている」とマークする。
 *
 * `mpm add` 1回で getRepositoryFile が複数回呼ばれるため、
 * これが無いと死んだ委譲先に対して待ち時間を何度も払うことになる。
 */
const markDown = async (
    requestUrl: string,
    delegation: Delegation,
): Promise<void> => {
    await caches.default.put(
        downCacheKey(requestUrl, delegation),
        new Response("", {
            headers: { "cache-control": `public, max-age=${DOWN_TTL_SECONDS}` },
        }),
    );
};

/**
 * ローカルキャッシュにある委譲先indexを返す。無ければスナップショットへフォールバックする。
 *
 * 第三者への fetch は一切行わない（待つのはWorkerローカルのCache APIのみ）。
 * 一覧の生成はこの関数だけを使うため、委譲先がダウンしてもスナップショットが
 * 「床」となり、一覧の件数が減ることはない。
 *
 * @returns 定義マップと、その出所（live=キャッシュ済みの実データ / snapshot=凍結コピー）
 */
export const viewDelegation = async (
    delegation: Delegation,
): Promise<{
    plugins: Record<string, DelegatedPluginInfo>;
    via: "live" | "snapshot";
}> => {
    const hit = await caches.default.match(liveCacheKey(delegation));
    if (hit) {
        try {
            const parsed = DelegationIndexSchema.safeParse(await hit.json());
            if (parsed.success && parsed.data.root === delegation.root) {
                return {
                    plugins: sanitizeIndex(delegation, parsed.data),
                    via: "live",
                };
            }
        } catch {
            // 壊れたキャッシュはスナップショットへ落とす
        }
    }
    return {
        plugins: sanitizeIndex(delegation, SNAPSHOTS[delegation.root]),
        via: "snapshot",
    };
};

/**
 * 委譲先indexのURLが安全かを検証する（SSRF対策）。
 *
 * httpsのみ、IPリテラル・localhost・.local を拒否、自オリジンを拒否する。
 */
const isSafeDelegateUrl = (indexUrl: string, requestUrl: string): boolean => {
    let url: URL;
    try {
        url = new URL(indexUrl);
    } catch {
        return false;
    }
    if (url.protocol !== "https:") return false;
    const host = url.hostname.toLowerCase();
    if (host === "localhost" || host.endsWith(".local")) return false;
    // IPリテラル（IPv4 / IPv6）を拒否する
    if (/^\d+\.\d+\.\d+\.\d+$/.test(host)) return false;
    if (host.includes(":") || host.startsWith("[")) return false;
    // 自分自身へのリクエストを拒否する（ループ防止）
    if (host === new URL(requestUrl).hostname.toLowerCase()) return false;
    return true;
};

/**
 * 委譲先から実データを取得し、検証してキャッシュへ格納する。
 *
 * 親（中央）のリクエストヘッダは一切転送しない。リダイレクトも追わない。
 * 非2xxは決してキャッシュしない。
 *
 * @returns 取得できた場合はsanitize済みの定義マップ、到達不能ならnull
 */
export const refreshDelegation = async (
    requestUrl: string,
    delegation: Delegation,
    timeoutMs: number = BACKGROUND_FETCH_TIMEOUT_MS,
): Promise<Record<string, DelegatedPluginInfo> | null> => {
    if (!isSafeDelegateUrl(delegation.index, requestUrl)) return null;

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), timeoutMs);
    try {
        const response = await fetch(delegation.index, {
            headers: {
                accept: "application/json",
                "user-agent": "mpm-repo",
            },
            redirect: "manual",
            signal: controller.signal,
        });

        // 非2xxは到達不能として扱い、決してキャッシュしない
        if (!response.ok) return null;

        // Content-Lengthは信用せず、読み出したバイト数で打ち切る
        const body = await response.arrayBuffer();
        if (body.byteLength > MAX_INDEX_BYTES) return null;

        const parsed = DelegationIndexSchema.safeParse(
            JSON.parse(new TextDecoder().decode(body)),
        );
        if (!parsed.success) return null;
        // URL取り違えの検知
        if (parsed.data.root !== delegation.root) return null;

        // 委譲先のCache-Controlをそのまま使わず、こちらのTTLで上書きして格納する
        await caches.default.put(
            liveCacheKey(delegation),
            new Response(JSON.stringify(parsed.data), {
                headers: {
                    "content-type": "application/json",
                    "cache-control": `public, max-age=${LIVE_TTL_SECONDS}`,
                },
            }),
        );

        return sanitizeIndex(delegation, parsed.data);
    } catch {
        return null;
    } finally {
        clearTimeout(timer);
    }
};

/**
 * 到達不能だった委譲先にネガティブマーカーを立てる。
 */
export const markDelegationDown = markDown;

/**
 * 指定した名前を担当する委譲テーブルのエントリを返す。
 *
 * 委譲は深さ1固定なので、ここで見つからなければ確定的に不在である。
 */
export const findDelegationFor = (pluginId: string): Delegation | undefined =>
    DELEGATIONS.find((d) => pluginId.startsWith(`${d.root}-`));
