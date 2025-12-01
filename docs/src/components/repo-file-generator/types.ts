/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { Repository } from "./api/fetch-repository";

// API層の型を再エクスポート
export type { PluginInfo, Repository } from "./api/fetch-repository";

// リポジトリデータの型定義
export type RepositoryData = {
    url: string; // リポジトリのURL
    repository: Repository | null; // Repository設定（取得前はnull）
    downloadFiles: string[]; // ダウンロード可能なファイル一覧
    isLoading: boolean; // データ取得中かどうか
};

// RepositoryListコンポーネントのProps
export type RepositoryListProps = {
    repositories: RepositoryData[]; // リポジトリデータの配列
    onFetch: (index: number) => Promise<void>; // 個別リポジトリデータ取得のコールバック
    onFetchAll: () => Promise<void>; // すべてのリポジトリを一括取得するコールバック
    onRemove: (index: number) => void; // リポジトリ削除のコールバック
    onUpdateRepository: (index: number, repository: Repository) => void; // Repository更新のコールバック
};

// RepositoryItemコンポーネントのProps
export type RepositoryItemProps = {
    repo: RepositoryData; // 個別のリポジトリデータ
    index: number; // リポジトリのインデックス
    onFetch: (index: number) => Promise<void>; // リポジトリデータ取得のコールバック
    onRemove: (index: number) => void; // リポジトリ削除のコールバック
    onUpdateRepository: (index: number, repository: Repository) => void; // Repository更新のコールバック
};
