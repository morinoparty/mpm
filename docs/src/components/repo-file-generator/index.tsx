/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { Button } from "@site/src/components/ui/button";
import { Input } from "@site/src/components/ui/input";
import { Label } from "@site/src/components/ui/label";
import { useAtom, useAtomValue, useSetAtom } from "jotai";
import Download from "lucide-react/dist/esm/icons/download.js";
import React from "react";
import { Toaster, toast } from "sonner";
import { RepositoryList } from "./components/repository-list";
import {
    addRepositoryAtom,
    fetchAllRepositoriesAtom,
    fetchRepositoryDataAtom,
    newRepositoryUrlAtom,
    pluginInfoAtom,
    removeRepositoryAtom,
    repositoriesAtom,
    updatePluginInfoAtom,
    updateRepositoryItemAtom,
} from "./store/atoms";
import type { Repository } from "./types";
import { validateRepositoryUrl } from "./utils";

/**
 * リポジトリファイルジェネレーター
 * プラグインのリポジトリ情報を取得してJSON形式でエクスポートする
 */
export const RepoFileGenerator = () => {
    // Jotaiのatomsを使用した状態管理
    const repositories = useAtomValue(repositoriesAtom); // リポジトリデータ一覧を取得
    const [pluginInfo, setPluginInfo] = useAtom(pluginInfoAtom); // PluginInfo全体を管理
    const [newRepositoryUrl, setNewRepositoryUrl] = useAtom(
        newRepositoryUrlAtom,
    ); // 新規追加するURL

    // Action atomsの取得（write-only）
    const addRepository = useSetAtom(addRepositoryAtom);
    const removeRepository = useSetAtom(removeRepositoryAtom);
    const fetchRepositoryData = useSetAtom(fetchRepositoryDataAtom);
    const fetchAllRepositories = useSetAtom(fetchAllRepositoriesAtom);
    const updateRepositoryItem = useSetAtom(updateRepositoryItemAtom);

    /**
     * リポジトリURLを追加する
     * URL検証を行い、有効な場合のみ追加
     */
    const handleAddRepository = () => {
        // URL検証
        const validation = validateRepositoryUrl(newRepositoryUrl);
        if (!validation.isValid) {
            toast.error("Invalid URL", {
                description: validation.error,
            });
            return;
        }

        // リポジトリを追加
        addRepository(newRepositoryUrl);
        // 入力フィールドをクリア
        setNewRepositoryUrl("");
    };

    /**
     * Repositoryを更新するラッパー関数
     * atomのインターフェースに合わせて引数を整形
     */
    const handleUpdateRepository = (index: number, repository: Repository) => {
        updateRepositoryItem({ index, repository });
    };

    /**
     * JSONファイルをダウンロードする
     * 全体のpluginInfoと各repositoriesを組み合わせてエクスポート
     */
    const handleDownload = () => {
        // 各リポジトリのrepositoryを収集
        const validRepositories = repositories
            .filter((repo) => repo.repository !== null)
            .map((repo) => repo.repository!);

        if (validRepositories.length === 0) {
            toast.error("No repositories fetched", {
                description:
                    "Please fetch repository data before downloading",
            });
            return;
        }

        // PluginInfo全体を更新（repositoriesフィールドを設定）
        const finalPluginInfo = {
            ...pluginInfo,
            repositories: validRepositories,
        };

        // JSONとしてダウンロード
        const jsonString = JSON.stringify(finalPluginInfo, null, 2);
        const blob = new Blob([jsonString], { type: "application/json" });
        const url = URL.createObjectURL(blob);
        const link = document.createElement("a");
        link.href = url;
        link.download = `${pluginInfo.id || "repository"}.json`;
        document.body.appendChild(link);
        link.click();
        document.body.removeChild(link);
        URL.revokeObjectURL(url);

        toast.success("Repository file downloaded successfully");
    };

    return (
        <>
            {/* トースト通知用コンポーネント */}
            <Toaster />

            <div className="flex flex-col gap-4 pb-4">
                {/* Plugin Info入力セクション */}
                <div className="flex flex-col gap-2">
                    <Label className="text-base font-semibold">
                        Plugin Information
                    </Label>
                    <div className="grid grid-cols-2 gap-4">
                        <div className="flex flex-col gap-2">
                            <Label htmlFor="plugin-id">Plugin ID</Label>
                            <Input
                                id="plugin-id"
                                type="text"
                                placeholder="my-plugin"
                                value={pluginInfo.id}
                                onChange={(e) =>
                                    setPluginInfo({
                                        ...pluginInfo,
                                        id: e.target.value,
                                    })
                                }
                            />
                        </div>

                        {/* License */}
                        <div className="flex flex-col gap-2">
                            <Label htmlFor="plugin-license">License</Label>
                            <Input
                                id="plugin-license"
                                type="text"
                                placeholder="MIT"
                                value={pluginInfo.license}
                                onChange={(e) =>
                                    setPluginInfo({
                                        ...pluginInfo,
                                        license: e.target.value,
                                    })
                                }
                            />
                        </div>

                        {/* Website */}
                        <div className="flex flex-col gap-2">
                            <Label htmlFor="plugin-website">Website</Label>
                            <Input
                                id="plugin-website"
                                type="url"
                                placeholder="https://example.com"
                                value={pluginInfo.website}
                                onChange={(e) =>
                                    setPluginInfo({
                                        ...pluginInfo,
                                        website: e.target.value,
                                    })
                                }
                            />
                        </div>

                        {/* Source */}
                        <div className="flex flex-col gap-2">
                            <Label htmlFor="plugin-source">Source Code</Label>
                            <Input
                                id="plugin-source"
                                type="url"
                                placeholder="https://github.com/user/repo"
                                value={pluginInfo.source}
                                onChange={(e) =>
                                    setPluginInfo({
                                        ...pluginInfo,
                                        source: e.target.value,
                                    })
                                }
                            />
                        </div>
                    </div>
                </div>

                {/* Repository URL入力 */}
                <div className="flex flex-col gap-2">
                    <Label htmlFor="repository-url">Repository URL</Label>
                    <div className="flex flex-row gap-2">
                        <Input
                            id="repository-url"
                            type="text"
                            placeholder="https://modrinth.com/plugin/minecraftpluginmanager"
                            value={newRepositoryUrl}
                            onChange={(e) => setNewRepositoryUrl(e.target.value)}
                        />
                        <Button onClick={handleAddRepository}>Add URL</Button>
                    </div>
                </div>

                {/* リポジトリ一覧 */}
                <RepositoryList
                    repositories={repositories}
                    onFetch={fetchRepositoryData}
                    onFetchAll={fetchAllRepositories}
                    onRemove={removeRepository}
                    onUpdateRepository={handleUpdateRepository}
                />

                {/* Downloadボタン */}
                {repositories.some((repo) => repo.repository !== null) && (
                    <div className="flex justify-end">
                        <Button
                            variant="default"
                            size="lg"
                            onClick={handleDownload}
                        >
                            <Download className="h-5 w-5 mr-2" />
                            Download Repository File
                        </Button>
                    </div>
                )}
            </div>
        </>
    );
};
