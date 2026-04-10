/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { z } from "zod";

/**
 * リポジトリ1件の設定。
 * `id` と `type` は必須。その他はリポジトリ種別ごとに任意。
 */
const RepositorySchema = z
    .object({
        // リポジトリタイプ（例: "modrinth", "github", "spigotmc", "hangar"）
        type: z.enum(["modrinth", "github", "spigotmc", "hangar"]),
        // リポジトリ固有ID（GitHubは "owner/repo"、SpigotMCはリソースID、Modrinthはslug/ID）
        id: z.string().min(1),
        // 複数アセットから対象ファイルを選ぶための正規表現（主にGitHub用、任意）
        fileNameRegex: z.string().optional(),
        // バージョン文字列からsemver部分を抽出する正規表現（任意、デフォルトは `X.Y.Z`）
        versionModifier: z.string().optional(),
        // カスタムダウンロードURLテンプレート（任意）
        downloadUrl: z.string().optional(),
        // ダウンロード後のファイル名テンプレート（任意）
        fileNameTemplate: z.string().optional(),
    })
    .meta({
        id: "Repository",
        description: "プラグインのダウンロード元を定義するリポジトリ設定",
    });

/**
 * プラグインのリポジトリファイル（repo/public/paper/plugins/*.json）のスキーマ。
 *
 * `repositories` 以外はすべて任意。カタログの実態に合わせて website / source / license /
 * dependencies / defaultVersion などは optional として扱う。
 */
export const PluginInfoSchema = z
    .object({
        // IDEでのスキーマ補完用（任意）
        $schema: z.string().optional(),
        // プラグイン識別子。リポジトリファイルのベースネームと一致させる
        id: z.string().min(1),
        // プラグインのウェブサイトURL（任意）
        website: z.url().optional(),
        // ソースコードのURL（任意）
        source: z.url().optional(),
        // SPDXライセンス識別子（任意、例: "GPL-3.0-only"）
        license: z.string().optional(),
        // ダウンロード元のリポジトリ設定（必須、少なくとも1件）
        repositories: z.array(RepositorySchema).min(1),
        // 必須の依存プラグイン（リポジトリ上のプラグイン名、任意）
        dependencies: z.array(z.string()).optional(),
        // オプションの依存プラグイン（任意）
        softDependencies: z.array(z.string()).optional(),
        // デフォルトのバージョン指定。mpm addで明示指定がない場合に使用される
        // 例: "tag:beta"（betaチャンネルの最新）, "sync:OtherPlugin"
        defaultVersion: z.string().optional(),
    })
    .meta({
        id: "PluginInfo",
        title: "mpm Plugin Repository File",
        description:
            "mpmのパブリックリポジトリにおける1プラグイン分の定義ファイル。" +
            "repo.mpm.nikomaru.dev が配信するスキーマと対応する。",
    });

export type PluginInfo = z.infer<typeof PluginInfoSchema>;
