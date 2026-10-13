/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

/**
 * mpm プラグインリポジトリ（https://mpm.plugin.morino.party）の静的ファイルを生成する。
 *
 * repo/scripts/build.ts（旧 repo.mpm.nikomaru.dev 向け）をもとに、docs サイトの public/ から
 * 配信する形へ移植したもの。旧リポジトリとはディレクトリを共有せず、別々に管理する。
 *
 * 生成物:
 *  - public/paper/index.json                  ルート。プラグインは持たず、チャンネルと外部リポジトリを子に持つ
 *  - public/paper/channel/<channel>/index.json  各チャンネル（orbit / grid / spark）
 *  - public/velocity/index.json               Velocity 向けカタログ
 *  - public/schema/**                         各 JSON の `$schema` が参照する JSON Schema
 *
 * docs ディレクトリをカレントにして実行する（`pnpm run build:repository`）。
 */

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join, relative } from "node:path";
import { z } from "zod/v4";
import { type PluginInfo, PluginInfoSchema } from "../lib/repository/plugin-info";
import { type RepositoryLink, RepositoryIndexSchema, RepositoryLinkSchema } from "../lib/repository/repository-index";

// 配信オリジン。JSON Schema の `$id` と index.json の `$schema` に埋め込む
const ORIGIN = "https://mpm.plugin.morino.party";

// スキーマの版数。破壊的変更時に上げ、旧版のファイルも残す
const PLUGIN_INFO_SCHEMA_VERSION = "v1";
const REPOSITORY_INDEX_SCHEMA_VERSION = "v1";

// Paper 向けのチャンネル。ルートの children にはこの順で並べる（先に書いたものほど同名定義で優先される）
const PAPER_CHANNELS = ["orbit", "grid", "spark"] as const;

// スクリプトは docs/ をカレントにして実行される前提（package.json の scripts から呼ぶ）
const publicDir = join(process.cwd(), "public");
const schemaDir = join(publicDir, "schema");

/** 1ノード（= index.json 1枚）の生成設定 */
interface NodeConfig {
    // public/ からの相対ディレクトリ（例: "paper/channel/grid"）
    path: string;
    // index.json の name フィールド（ログ・デバッグ用）
    name: string;
    // children.json より前に置く、スクリプト側で決まる子リンク（ルートのチャンネルなど）
    builtinChildren?: RepositoryLink[];
}

/** JSONを整形して書き出す（親ディレクトリが無ければ作る） */
const write = (path: string, value: unknown) => {
    mkdirSync(dirname(path), { recursive: true });
    writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`);
};

/**
 * 検証エラーを表示して終了する。
 * 関数宣言にしているのは、TypeScriptの制御フロー解析が `never` 戻り値を
 * 認識して以降のコードで型を絞り込めるようにするため（const宣言の矢印関数では効かない）。
 */
function fail(message: string, detail?: string): never {
    console.error(`✗ ${message}`);
    if (detail) console.error(detail);
    process.exit(1);
}

/**
 * <dir>/plugins/*.json を読み込み、検証してプラグイン名 -> 定義のマップにする。
 * plugins/ が無いノード（ルートなど）は空のマップを返す。
 * バンドルに含める際は各ファイルの `$schema` を落とす（IDE補完用でクライアントには不要）。
 */
const loadPlugins = (dir: string): Record<string, PluginInfo> => {
    const pluginsDir = join(dir, "plugins");
    if (!existsSync(pluginsDir)) return {};

    const files = readdirSync(pluginsDir)
        .filter((file) => file.endsWith(".json"))
        .sort();

    const plugins: Record<string, PluginInfo> = {};
    for (const file of files) {
        const label = relative(publicDir, join(pluginsDir, file));
        const raw = JSON.parse(readFileSync(join(pluginsDir, file), "utf-8"));
        const parsed = PluginInfoSchema.safeParse(raw);
        if (!parsed.success) {
            fail(`${label} はPluginInfoSchemaに適合しません`, parsed.error.message);
        }
        // `$schema` は配信オリジンのスキーマを指していなければならない（旧オリジンのコピー漏れを防ぐ）
        if (parsed.data.$schema && !parsed.data.$schema.startsWith(`${ORIGIN}/schema/`)) {
            fail(`${label} の $schema が ${ORIGIN} を指していません: ${parsed.data.$schema}`);
        }
        const { $schema: _schema, ...info } = parsed.data;
        const name = file.replace(/\.json$/, "");
        if (info.id !== name) {
            fail(`${label} の id (${info.id}) がファイル名と一致しません`);
        }
        plugins[name] = info;
    }
    return plugins;
};

/**
 * <dir>/children.json（子リポジトリへのリンク一覧）を読み込んで検証する。
 * ファイルが無ければ子なしとして扱う。
 */
const loadChildren = (dir: string): RepositoryLink[] => {
    const childrenFile = join(dir, "children.json");
    if (!existsSync(childrenFile)) return [];
    const raw = JSON.parse(readFileSync(childrenFile, "utf-8"));
    const parsed = z.array(RepositoryLinkSchema).safeParse(raw);
    if (!parsed.success) {
        fail(`${relative(publicDir, childrenFile)} はRepositoryLinkSchemaに適合しません`, parsed.error.message);
    }
    return parsed.data;
};

/** 同じ index を2回書くのは誤りとみなす */
const assertUniqueLinks = (path: string, links: RepositoryLink[]) => {
    const seen = new Set<string>();
    for (const link of links) {
        if (seen.has(link.index)) fail(`${path} の子リンクが重複しています: ${link.index}`);
        seen.add(link.index);
    }
};

/**
 * 1ノード分の index.json を組み立て、自己検証してから書き出す。
 * 生成物を git にコミットするため、差分が出ないよう `generated`（生成時刻）は付けない。
 *
 * @return 読み込んだプラグイン名の一覧（チャンネル間の重複検査に使う）
 */
const buildNode = (config: NodeConfig): string[] => {
    const dir = join(publicDir, config.path);
    const plugins = loadPlugins(dir);
    const children = [...(config.builtinChildren ?? []), ...loadChildren(dir)];
    assertUniqueLinks(config.path, children);

    const index = {
        $schema: `${ORIGIN}/schema/repository-index/${REPOSITORY_INDEX_SCHEMA_VERSION}.json`,
        schemaVersion: 1 as const,
        name: config.name,
        plugins,
        children,
    };
    const verified = RepositoryIndexSchema.safeParse(index);
    if (!verified.success) {
        fail(`生成した ${config.path}/index.json がRepositoryIndexSchemaに適合しません`, verified.error.message);
    }
    write(join(dir, "index.json"), index);
    console.log(
        `Generated ${config.path}/index.json with ${Object.keys(plugins).length} plugins and ${children.length} children`,
    );
    return Object.keys(plugins);
};

/** JSON Schema を `$id` 付きで書き出す */
const writeSchema = (schema: z.ZodType, relativePath: string, version: string) => {
    const jsonSchema = z.toJSONSchema(schema, { target: "draft-2020-12" });
    write(join(schemaDir, relativePath), {
        ...jsonSchema,
        $id: `${ORIGIN}/schema/${relativePath}`,
        "x-schema-version": version,
    });
    console.log(`Generated schema/${relativePath}`);
};

const main = () => {
    // ---- 1. Paper の各チャンネルを生成し、プラグインがちょうど1つのチャンネルに属することを確かめる ----
    // 同名定義は祖先側（先に並べたチャンネル）が勝つため、重複すると後ろのチャンネルの定義が黙って隠れてしまう
    const owner = new Map<string, string>();
    for (const channel of PAPER_CHANNELS) {
        const names = buildNode({ path: `paper/channel/${channel}`, name: `morinoparty-paper-${channel}` });
        for (const name of names) {
            const existing = owner.get(name);
            if (existing) fail(`${name} が ${existing} と ${channel} の両方に定義されています`);
            owner.set(name, channel);
        }
    }

    // ---- 2. Paper のルートを生成する（チャンネル -> 外部リポジトリの順に子として並べる） ----
    buildNode({
        path: "paper",
        name: "morinoparty-paper",
        builtinChildren: PAPER_CHANNELS.map((channel) => ({
            index: `${ORIGIN}/paper/channel/${channel}/index.json`,
        })),
    });

    // ---- 3. Velocity のカタログを生成する ----
    buildNode({ path: "velocity", name: "morinoparty-velocity" });

    // ---- 4. JSON Schema を静的ファイルとして書き出す ----
    // 各プラグインJSONの `$schema` が参照する。plugin-info.json は最新版のエイリアス
    writeSchema(PluginInfoSchema, `plugin-info/${PLUGIN_INFO_SCHEMA_VERSION}.json`, PLUGIN_INFO_SCHEMA_VERSION);
    writeSchema(PluginInfoSchema, "plugin-info.json", PLUGIN_INFO_SCHEMA_VERSION);
    writeSchema(
        RepositoryIndexSchema,
        `repository-index/${REPOSITORY_INDEX_SCHEMA_VERSION}.json`,
        REPOSITORY_INDEX_SCHEMA_VERSION,
    );
};

main();
