/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { existsSync, mkdirSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { z } from "zod";
import { PluginInfoSchema, type PluginInfo } from "../src/type/plugin-info";
import {
    RepositoryIndexSchema,
    RepositoryLinkSchema,
    type RepositoryLink,
} from "../src/type/repository-index";

// 配信オリジン。JSON Schema の `$id` と index.json の `$schema` に埋め込む
const ORIGIN = "https://repo.mpm.nikomaru.dev";

// スキーマの版数。破壊的変更時に上げ、旧版のファイルも残す
const PLUGIN_INFO_SCHEMA_VERSION = "v1";
const REPOSITORY_INDEX_SCHEMA_VERSION = "v1";

const publicDir = join(__dirname, "..", "public");
const paperDir = join(publicDir, "paper");
const pluginsDir = join(paperDir, "plugins");
const childrenFile = join(paperDir, "children.json");
const indexFile = join(paperDir, "index.json");
const schemaDir = join(publicDir, "schema");

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
 * public/paper/plugins/*.json を読み込み、検証してプラグイン名 -> 定義のマップにする。
 * バンドルに含める際は各ファイルの `$schema` を落とす（IDE補完用でクライアントには不要）。
 */
const loadPlugins = (): Record<string, PluginInfo> => {
    const files = readdirSync(pluginsDir)
        .filter((file) => file.endsWith(".json"))
        .sort();

    const plugins: Record<string, PluginInfo> = {};
    for (const file of files) {
        const raw = JSON.parse(readFileSync(join(pluginsDir, file), "utf-8"));
        const parsed = PluginInfoSchema.safeParse(raw);
        if (!parsed.success) {
            fail(`${file} はPluginInfoSchemaに適合しません`, parsed.error.message);
        }
        const { $schema: _schema, ...info } = parsed.data;
        const name = file.replace(/\.json$/, "");
        if (info.id !== name) {
            fail(`${file} の id (${info.id}) がファイル名と一致しません`);
        }
        plugins[name] = info;
    }
    return plugins;
};

/**
 * public/paper/children.json（子リポジトリへのリンク一覧）を読み込んで検証する。
 * ファイルが無ければ子なしとして扱う。
 */
const loadChildren = (): RepositoryLink[] => {
    if (!existsSync(childrenFile)) return [];
    const raw = JSON.parse(readFileSync(childrenFile, "utf-8"));
    const parsed = z.array(RepositoryLinkSchema).safeParse(raw);
    if (!parsed.success) {
        fail("children.json はRepositoryLinkSchemaに適合しません", parsed.error.message);
    }
    // 同じ index を2回書くのは誤りとみなす
    const seen = new Set<string>();
    for (const link of parsed.data) {
        if (seen.has(link.index)) fail(`children.json に重複したリンクがあります: ${link.index}`);
        seen.add(link.index);
    }
    return parsed.data;
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
    // ---- 1. プラグイン定義と子リンクを読み込む ----
    const plugins = loadPlugins();
    const children = loadChildren();

    // ---- 2. index.json を組み立てて自己検証する ----
    const index = {
        $schema: `${ORIGIN}/schema/repository-index/${REPOSITORY_INDEX_SCHEMA_VERSION}.json`,
        schemaVersion: 1 as const,
        name: "morinoparty",
        generated: new Date().toISOString(),
        plugins,
        children,
    };
    const verified = RepositoryIndexSchema.safeParse(index);
    if (!verified.success) {
        fail("生成した index.json がRepositoryIndexSchemaに適合しません", verified.error.message);
    }
    write(indexFile, index);
    console.log(
        `Generated paper/index.json with ${Object.keys(plugins).length} plugins and ${children.length} children`,
    );

    // ---- 3. JSON Schema を静的ファイルとして書き出す ----
    // 各プラグインJSONの `$schema` が参照する。旧パス（plugin-info.json）も最新版のエイリアスとして残す
    writeSchema(PluginInfoSchema, `plugin-info/${PLUGIN_INFO_SCHEMA_VERSION}.json`, PLUGIN_INFO_SCHEMA_VERSION);
    writeSchema(PluginInfoSchema, "plugin-info.json", PLUGIN_INFO_SCHEMA_VERSION);
    writeSchema(
        RepositoryIndexSchema,
        `repository-index/${REPOSITORY_INDEX_SCHEMA_VERSION}.json`,
        REPOSITORY_INDEX_SCHEMA_VERSION,
    );
};

main();
