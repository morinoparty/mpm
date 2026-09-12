/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { z } from "zod";
import { PluginInfoSchema, RepositorySchema } from "./plugin-info";

/**
 * 委譲先が書いてはいけないリポジトリフィールドをスキーマレベルで落とす。
 *
 * - downloadUrl: Kotlin側に読み手が存在しない死にフィールド。将来実装された場合に
 *   第三者が任意のURLを差し込める入口になるため、委譲先には最初から持たせない。
 * - fileNameTemplate: FileNameTemplate.render がパス区切りを一切サニタイズしないため、
 *   `../` を含むテンプレートで plugins/ の外へ書き込める。中央のレビュー済み定義に限定する。
 */
const DelegatedRepositorySchema = RepositorySchema.omit({
    downloadUrl: true,
    fileNameTemplate: true,
});

/**
 * 委譲先が書ける1プラグイン分の定義。
 *
 * `delegate` を落とすことで委譲の深さを1に固定する。
 * 深さが1で固定されている限り、訪問済み集合も深さカウンタも不要になる。
 */
const DelegatedPluginInfoSchema = PluginInfoSchema.omit({
    $schema: true,
    delegate: true,
}).extend({
    repositories: z.array(DelegatedRepositorySchema).min(1),
});

/**
 * 委譲先が配信するバンドル（例: https://mineauth.plugin.morino.party/mpm/paper/index.json）のスキーマ。
 *
 * インデックスと全プラグイン定義を1ファイルに含めるため、取得はHTTP1往復で済む。
 * 静的ホスティングの public/ に置くだけで配信でき、拡張子なしパスやサーバーロジックを必要としない。
 */
export const DelegationIndexSchema = z
    .object({
        // IDEでのスキーマ補完用（任意）
        $schema: z.string().optional(),
        // バンドル形式の版数。破壊的変更時に上げる
        schemaVersion: z.literal(1),
        // 中央のルートレコードのidと一致していなければならない。
        // URLの取り違え（別プラグインのindexを指してしまう事故）を検知するために使う。
        root: z.string().regex(/^[A-Za-z0-9_-]+$/),
        // 生成時刻（任意、デバッグ用）
        generated: z.string().optional(),
        // プラグイン名 -> 定義。キーと value.id は完全一致していなければならない
        plugins: z.record(
            z.string().regex(/^[A-Za-z0-9_-]+$/),
            DelegatedPluginInfoSchema,
        ),
    })
    .meta({
        id: "DelegationIndex",
        title: "mpm Delegated Repository Index",
        description:
            "プラグイン提供者が自身のサイトで配信する、委譲されたプラグイン定義のバンドル。",
    });

export type DelegationIndex = z.infer<typeof DelegationIndexSchema>;
export type DelegatedPluginInfo = z.infer<typeof DelegatedPluginInfoSchema>;
