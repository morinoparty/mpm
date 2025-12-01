/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { Accordion } from "@site/src/components/ui/accordion";
import { Button } from "@site/src/components/ui/button";
import { Label } from "@site/src/components/ui/label";
import RefreshCw from "lucide-react/dist/esm/icons/refresh-cw.js";
import type React from "react";
import type { RepositoryListProps } from "../types";
import { RepositoryItem } from "./repository-item";

/**
 * リポジトリ一覧を表示するコンポーネント
 * 複数のRepositoryItemをアコーディオン形式で表示
 */
export const RepositoryList: React.FC<RepositoryListProps> = ({
    repositories,
    onFetch,
    onFetchAll,
    onRemove,
    onUpdateRepository,
}) => {
    // リポジトリが空の場合は何も表示しない
    if (repositories.length === 0) {
        return null;
    }

    // いずれかのリポジトリがローディング中かチェック
    const isAnyLoading = repositories.some((repo) => repo.isLoading);

    return (
        <div className="flex flex-col gap-4">
            {/* ヘッダー部分 */}
            <div className="flex items-center justify-between">
                <Label>Current Repositories ({repositories.length})</Label>
                <Button
                    variant="default"
                    size="sm"
                    onClick={onFetchAll}
                    disabled={isAnyLoading}
                >
                    <RefreshCw
                        className={`h-4 w-4 mr-2 ${isAnyLoading ? "animate-spin" : ""}`}
                    />
                    Fetch All
                </Button>
            </div>

            {/* アコーディオンで各リポジトリを表示 */}
            <Accordion type="multiple" className="w-full">
                {repositories.map((repo, index) => (
                    <RepositoryItem
                        key={index}
                        repo={repo}
                        index={index}
                        onFetch={onFetch}
                        onRemove={onRemove}
                        onUpdateRepository={onUpdateRepository}
                    />
                ))}
            </Accordion>
        </div>
    );
};
