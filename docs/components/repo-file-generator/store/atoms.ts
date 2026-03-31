/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { atom } from "jotai";
import { toast } from "sonner";
import {
    detectPlatform,
    fetchGitHubData,
    fetchHangarData,
    fetchModrinthData,
    fetchSpigotData,
} from "../api/fetch-repository";
import type { PluginInfo, Repository, RepositoryData } from "../types";

// ========================================
// 基本的なAtoms
// ========================================

// リポジトリデータの配列を管理するatom（URLとRepository設定のみ）
export const repositoriesAtom = atom<RepositoryData[]>([]);

// 全体で1つのPluginInfoを管理するatom
export const pluginInfoAtom = atom<PluginInfo>({
    id: "",
    license: "",
    website: "",
    source: "",
    repositories: [],
});

// リポジトリ名（JSONファイル名）を管理するatom
export const repositoryNameAtom = atom<string>("");

// 新しいリポジトリURLを管理するatom
export const newRepositoryUrlAtom = atom<string>("");

// ========================================
// Write-only Atoms（アクション用）
// ========================================

// リポジトリを追加するaction atom
export const addRepositoryAtom = atom(
    null, // read側は不要
    (get, set, url: string) => {
        const repositories = get(repositoriesAtom);

        // 既に同じURLが存在するかチェック
        const exists = repositories.some((repo) => repo.url === url);
        if (exists) {
            toast.error("This URL is already added");
            return;
        }

        // 新しいリポジトリを追加（repository設定は後でfetchで取得）
        const newRepo: RepositoryData = {
            url,
            repository: null,
            downloadFiles: [],
            latestVersion: undefined,
            isLoading: false,
        };

        set(repositoriesAtom, [...repositories, newRepo]);
        toast.success("Repository added successfully");
    },
);

// リポジトリを削除するaction atom
export const removeRepositoryAtom = atom(null, (get, set, index: number) => {
    const repositories = get(repositoriesAtom);
    const newRepositories = repositories.filter((_, i) => i !== index);
    set(repositoriesAtom, newRepositories);
    toast.success("Repository removed");
});

// リポジトリデータを取得するaction atom
export const fetchRepositoryDataAtom = atom(
    null,
    async (get, set, index: number) => {
        const repositories = get(repositoriesAtom);
        const repo = repositories[index];

        if (!repo) return;

        // ローディング状態を設定
        const updatedRepos = [...repositories];
        updatedRepos[index] = { ...repo, isLoading: true };
        set(repositoriesAtom, updatedRepos);

        try {
            // プラットフォームを検出
            const platformInfo = detectPlatform(repo.url);

            if (!platformInfo) {
                throw new Error(`Could not detect platform from URL: ${repo.url}`);
            }

            let repository: Repository | null = null;
            let downloadFiles: string[] = [];
            let latestVersion: string | undefined = undefined;
            let fetchedPluginInfo: Partial<PluginInfo> | null = null;

            // プラットフォームに応じてデータを取得
            switch (platformInfo.type) {
                case "modrinth": {
                    const data = await fetchModrinthData(platformInfo.id);
                    repository = data.pluginInfo.repositories?.[0] || null;
                    downloadFiles = data.files;
                    latestVersion = data.version;
                    fetchedPluginInfo = data.pluginInfo;
                    break;
                }
                case "spigot": {
                    const data = await fetchSpigotData(platformInfo.id);
                    repository = data.pluginInfo.repositories?.[0] || null;
                    downloadFiles = data.files;
                    latestVersion = data.version;
                    fetchedPluginInfo = data.pluginInfo;
                    break;
                }
                case "github": {
                    const data = await fetchGitHubData(platformInfo.id);
                    repository = data.pluginInfo.repositories?.[0] || null;
                    downloadFiles = data.files;
                    latestVersion = data.version;
                    fetchedPluginInfo = data.pluginInfo;
                    break;
                }
                case "hangar": {
                    const data = await fetchHangarData(platformInfo.id);
                    repository = data.pluginInfo.repositories?.[0] || null;
                    downloadFiles = data.files;
                    latestVersion = data.version;
                    fetchedPluginInfo = data.pluginInfo;
                    break;
                }
                default:
                    throw new Error(`Unsupported platform: ${platformInfo.type}`);
            }

            // 取得したデータで更新
            const finalRepos = [...get(repositoriesAtom)];
            finalRepos[index] = {
                ...finalRepos[index],
                repository,
                downloadFiles,
                latestVersion,
                isLoading: false,
            };
            set(repositoriesAtom, finalRepos);

            // pluginInfoAtomを更新（空のフィールドのみ）
            if (fetchedPluginInfo) {
                const currentPluginInfo = get(pluginInfoAtom);
                const updatedPluginInfo = { ...currentPluginInfo };

                // licenseが空の場合のみ更新
                if (!updatedPluginInfo.license && fetchedPluginInfo.license) {
                    updatedPluginInfo.license = fetchedPluginInfo.license;
                }

                // websiteが空の場合のみ更新
                if (!updatedPluginInfo.website && fetchedPluginInfo.website) {
                    updatedPluginInfo.website = fetchedPluginInfo.website;
                }

                // sourceが空の場合のみ更新
                if (!updatedPluginInfo.source && fetchedPluginInfo.source) {
                    updatedPluginInfo.source = fetchedPluginInfo.source;
                }

                set(pluginInfoAtom, updatedPluginInfo);
            }

            toast.success("Repository data fetched successfully");
        } catch (error) {
            // エラー時の処理
            const errorRepos = [...get(repositoriesAtom)];
            errorRepos[index] = { ...errorRepos[index], isLoading: false };
            set(repositoriesAtom, errorRepos);

            toast.error(
                error instanceof Error
                    ? error.message
                    : "Failed to fetch repository data",
            );
        }
    },
);

// 全てのリポジトリデータを取得するaction atom
export const fetchAllRepositoriesAtom = atom(null, async (get, set) => {
    const repositories = get(repositoriesAtom);

    // 全てのリポジトリに対してfetchを実行
    await Promise.all(
        repositories.map((_, index) => set(fetchRepositoryDataAtom, index)),
    );
});

// PluginInfo全体を更新するaction atom
export const updatePluginInfoAtom = atom(
    null,
    (get, set, pluginInfo: PluginInfo) => {
        set(pluginInfoAtom, pluginInfo);
    },
);

// 個別のRepositoryを更新するaction atom
export const updateRepositoryItemAtom = atom(
    null,
    (
        get,
        set,
        {
            index,
            repository,
        }: {
            index: number;
            repository: Repository;
        },
    ) => {
        const repositories = get(repositoriesAtom);
        const updatedRepos = [...repositories];

        if (updatedRepos[index]) {
            updatedRepos[index] = {
                ...updatedRepos[index],
                repository,
            };
            set(repositoriesAtom, updatedRepos);
        }
    },
);
