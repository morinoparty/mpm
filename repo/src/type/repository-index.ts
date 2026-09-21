/*
 * Written in 2023-2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { z } from "zod";
import { PluginInfoSchema } from "./plugin-info";

// プラグイン名として許可する文字（Kotlin側 RepositoryLinkPolicy と同じ規則）
const PLUGIN_NAME_PATTERN = /^[A-Za-z0-9_-]+$/;

// scope のパターン。プラグイン名の文字に加えて、末尾の `*` のみワイルドカードとして許可する
const SCOPE_PATTERN = /^[A-Za-z0-9_-]+\*?$|^\*$/;

/**
 * 子リポジトリへのリンク（グラフの辺）。
 *
 * 親は、子が「どの名前を」「どの配布元から」定義してよいかをここで宣言する。
 * クライアント（mpm）はこの宣言に収まらない子の定義を捨てる。
 * 孫以降には経路上のすべてのリンクの制約が重ねて適用されるため、子は権限を広げられない。
 */
export const RepositoryLinkSchema = z
    .object({
        // 子リポジトリの index.json の絶対URL。https限定・.json拡張子必須
        // （静的ホスティングで確実に配信できる形に限定する）
        index: z.string().regex(/^https:\/\/.+\.json$/),
        // 子が定義してよいプラグイン名のパターン。完全一致、または末尾 `*` のプレフィックス一致
        scope: z.array(z.string().regex(SCOPE_PATTERN)).min(1),
        // 子が repositories[] に書いてよい配布元。"type:id" 形式、末尾 "*" のみワイルドカード
        allowedSources: z.array(z.string().regex(/^[a-z]+:[^\s]+$/)).min(1),
    })
    .meta({
        id: "RepositoryLink",
        description:
            "子リポジトリへのリンク。子が定義してよいプラグイン名（scope）と配布元（allowedSources）を親が宣言する",
    });

/**
 * リポジトリが配信するインデックス（index.json）のスキーマ。
 *
 * ルートも子も同じ形式で、静的ホスティングの public/ に置くだけで配信できる。
 * インデックスと全プラグイン定義を1ファイルに含めるため、1ノードの取得はHTTP1往復で済む。
 * クライアントは children を再帰的にたどってカタログを組み立てる（最大深さ5）。
 */
export const RepositoryIndexSchema = z
    .object({
        // IDEでのスキーマ補完用（任意）
        $schema: z.string().optional(),
        // インデックス形式の版数。破壊的変更時に上げる
        schemaVersion: z.literal(1),
        // リポジトリの表示名（任意、ログ・デバッグ用）
        name: z.string().optional(),
        // 生成時刻（任意、デバッグ用）
        generated: z.string().optional(),
        // プラグイン名 -> 定義。キーと value.id は完全一致していなければならない
        plugins: z.record(z.string().regex(PLUGIN_NAME_PATTERN), PluginInfoSchema),
        // 子リポジトリへのリンク（任意）
        children: z.array(RepositoryLinkSchema).optional(),
    })
    .meta({
        id: "RepositoryIndex",
        title: "mpm Repository Index",
        description:
            "mpmリポジトリのインデックス。全プラグイン定義と子リポジトリへのリンクを1ファイルに含む。",
    });

export type RepositoryIndex = z.infer<typeof RepositoryIndexSchema>;
export type RepositoryLink = z.infer<typeof RepositoryLinkSchema>;
