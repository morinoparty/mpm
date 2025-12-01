/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { useMemo, useState } from "react";
import { toast } from "sonner";
import {
    detectPlatform,
    fetchGitHubData,
    fetchHangarData,
    fetchModrinthData,
    fetchSpigotData,
} from "../api/fetch-repository";
import type { PluginInfo, Repository, RepositoryData } from "../types";

/**
 * リポジトリデータの状態管理とAPI呼び出しを行うカスタムフック
 *
 * @returns リポジトリデータと操作関数
 */
export const useRepositoryData = () => {
    // リポジトリデータの状態管理
    const [repositories, setRepositories] = useState<RepositoryData[]>([]);

    /**
     * 新しいリポジトリを追加する
     *
     * @param url - 追加するリポジトリのURL
     */
    const addRepository = (url: string) => {
        setRepositories([
            ...repositories,
            {
                url,
                pluginInfo: null,
                downloadFiles: [],
                isLoading: false,
            },
        ]);
    };

    /**
     * リポジトリを削除する
     *
     * @param index - 削除するリポジトリのインデックス
     */
    const removeRepository = (index: number) => {
        setRepositories(repositories.filter((_, i) => i !== index));
    };

    /**
     * リポジトリデータをAPIから取得する
     *
     * @param index - データを取得するリポジトリのインデックス
     */
    const fetchRepositoryData = async (index: number) => {
        const repo = repositories[index];
        if (!repo) return;

        // ローディング状態を開始
        setRepositories(
            repositories.map((r, i) =>
                i === index ? { ...r, isLoading: true } : r,
            ),
        );

        try {
            // URLからプラットフォームを判定
            const platform = detectPlatform(repo.url);
            if (!platform) {
                throw new Error(
                    "Unsupported platform. Supported: Modrinth, Spigot, GitHub, Hangar",
                );
            }

            let data: { pluginInfo: Partial<PluginInfo>; files: string[] };

            // プラットフォームごとにAPIを呼び出し
            switch (platform.type) {
                case "modrinth":
                    data = await fetchModrinthData(platform.id);
                    break;
                case "spigot":
                    data = await fetchSpigotData(platform.id);
                    break;
                case "github":
                    data = await fetchGitHubData(platform.id);
                    break;
                case "hangar":
                    data = await fetchHangarData(platform.id);
                    break;
                default:
                    throw new Error(`Unsupported platform: ${platform.type}`);
            }

            // PluginInfoを完成させる（不足情報を補完）
            const pluginInfo: PluginInfo = {
                id: data.pluginInfo.id || platform.id,
                website: data.pluginInfo.website || repo.url,
                source: data.pluginInfo.source || repo.url,
                license: data.pluginInfo.license || "Unknown",
                repositories: data.pluginInfo.repositories || [],
            };

            // 取得したデータで状態を更新
            setRepositories(
                repositories.map((r, i) =>
                    i === index
                        ? {
                              ...r,
                              pluginInfo,
                              downloadFiles: data.files,
                              isLoading: false,
                          }
                        : r,
                ),
            );

            // 成功メッセージを表示
            toast.success("Repository data fetched", {
                description: `Found ${data.files.length} files from ${platform.type}`,
            });
        } catch (error) {
            // エラーメッセージを表示
            toast.error("Failed to fetch repository data", {
                description:
                    error instanceof Error ? error.message : "Unknown error",
            });

            // ローディング状態を終了
            setRepositories(
                repositories.map((r, i) =>
                    i === index ? { ...r, isLoading: false } : r,
                ),
            );
        }
    };

    /**
     * すべてのリポジトリデータをまとめて取得する
     * 最初のリポジトリのwebsite/source/licenseを基準として、他のリポジトリにも適用する
     */
    const fetchAllRepositories = async () => {
        // まだデータ取得していないリポジトリのみ対象
        const reposToFetch = repositories
            .map((repo, index) => ({ repo, index }))
            .filter(({ repo }) => !repo.pluginInfo);

        if (reposToFetch.length === 0) {
            toast.info("All repositories have already been fetched");
            return;
        }

        // すべてローディング状態に設定
        setRepositories(repositories.map((r) => ({ ...r, isLoading: true })));

        try {
            // すべてのリポジトリを並列で取得
            const results = await Promise.allSettled(
                reposToFetch.map(async ({ repo, index }) => {
                    // URLからプラットフォームを判定
                    const platform = detectPlatform(repo.url);
                    if (!platform) {
                        throw new Error(`Unsupported platform: ${repo.url}`);
                    }

                    let data: {
                        pluginInfo: Partial<PluginInfo>;
                        files: string[];
                    };

                    // プラットフォームごとにAPIを呼び出し
                    switch (platform.type) {
                        case "modrinth":
                            data = await fetchModrinthData(platform.id);
                            break;
                        case "spigot":
                            data = await fetchSpigotData(platform.id);
                            break;
                        case "github":
                            data = await fetchGitHubData(platform.id);
                            break;
                        case "hangar":
                            data = await fetchHangarData(platform.id);
                            break;
                        default:
                            throw new Error(
                                `Unsupported platform: ${platform.type}`,
                            );
                    }

                    return { index, data, platform };
                }),
            );

            // 結果を処理
            let basePluginInfo: Partial<PluginInfo> | null = null;
            const updatedRepos = [...repositories];

            // 最初に成功したリポジトリのwebsite, source, licenseを基準とする
            for (const result of results) {
                if (result.status === "fulfilled") {
                    basePluginInfo = result.value.data.pluginInfo;
                    break;
                }
            }

            // 各リポジトリを更新
            for (let i = 0; i < results.length; i++) {
                const result = results[i];
                const { index } = reposToFetch[i];

                if (result.status === "fulfilled") {
                    const { data, platform } = result.value;

                    // 最初のリポジトリの情報をマージ
                    const pluginInfo: PluginInfo = {
                        id: data.pluginInfo.id || platform.id,
                        website:
                            basePluginInfo?.website ||
                            data.pluginInfo.website ||
                            repositories[index].url,
                        source:
                            basePluginInfo?.source ||
                            data.pluginInfo.source ||
                            repositories[index].url,
                        license:
                            basePluginInfo?.license ||
                            data.pluginInfo.license ||
                            "Unknown",
                        repositories: data.pluginInfo.repositories || [],
                    };

                    updatedRepos[index] = {
                        ...updatedRepos[index],
                        pluginInfo,
                        downloadFiles: data.files,
                        isLoading: false,
                    };
                } else {
                    // エラーの場合
                    updatedRepos[index] = {
                        ...updatedRepos[index],
                        isLoading: false,
                    };
                    toast.error(`Failed to fetch ${repositories[index].url}`, {
                        description: result.reason?.message || "Unknown error",
                    });
                }
            }

            setRepositories(updatedRepos);

            // 成功メッセージ
            const successCount = results.filter(
                (r) => r.status === "fulfilled",
            ).length;
            toast.success(
                `Fetched ${successCount}/${results.length} repositories`,
            );
        } catch (error) {
            // 全体のエラーハンドリング
            toast.error("Failed to fetch repositories", {
                description:
                    error instanceof Error ? error.message : "Unknown error",
            });

            // すべてのローディング状態を解除
            setRepositories(
                repositories.map((r) => ({ ...r, isLoading: false })),
            );
        }
    };

    /**
     * PluginInfoを更新する
     *
     * @param index - 更新するリポジトリのインデックス
     * @param pluginInfo - 新しいPluginInfo
     */
    const updatePluginInfo = (index: number, pluginInfo: PluginInfo) => {
        setRepositories(
            repositories.map((r, i) =>
                i === index ? { ...r, pluginInfo } : r,
            ),
        );
    };

    /**
     * Repository情報を更新する
     *
     * @param repoIndex - リポジトリのインデックス
     * @param repositoryItemIndex - Repository配列内のインデックス
     * @param repository - 新しいRepository情報
     */
    const updateRepository = (
        repoIndex: number,
        repositoryItemIndex: number,
        repository: Repository,
    ) => {
        const repo = repositories[repoIndex];
        if (!repo || !repo.pluginInfo) return;

        // Repository配列をコピーして更新
        const updatedRepositories = [...repo.pluginInfo.repositories];
        updatedRepositories[repositoryItemIndex] = repository;

        // PluginInfoを更新
        updatePluginInfo(repoIndex, {
            ...repo.pluginInfo,
            repositories: updatedRepositories,
        });
    };

    /**
     * 正規表現でフィルタリングされたファイル一覧を取得する
     * useMemoでメモ化して、パフォーマンスを最適化
     *
     * @param downloadFiles - 全ファイル一覧
     * @param fileNameRegex - フィルタリング用の正規表現
     * @returns フィルタリングされたファイル一覧
     */
    const getFilteredFiles = useMemo(() => {
        return (downloadFiles: string[], fileNameRegex?: string): string[] => {
            if (!fileNameRegex) return downloadFiles;

            try {
                const regexPattern = new RegExp(fileNameRegex);
                return downloadFiles.filter((file) => regexPattern.test(file));
            } catch (error) {
                toast.error("Invalid regex pattern", {
                    description: `Regex: ${fileNameRegex}`,
                });
                return downloadFiles;
            }
        };
    }, []); // 依存配列は空（関数自体は変化しない）

    return {
        repositories,
        addRepository,
        removeRepository,
        fetchRepositoryData,
        fetchAllRepositories,
        updatePluginInfo,
        updateRepository,
        getFilteredFiles,
    };
};
