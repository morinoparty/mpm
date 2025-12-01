/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { toast } from "sonner";
import type { RepositoryData } from "./types";

/**
 * URLがhttps://で始まるかチェックする
 *
 * @param url - 検証するURL
 * @returns https://で始まる場合true
 */
export const isValidRepositoryUrl = (url: string): boolean => {
    return url.startsWith("https://");
};

/**
 * URLを検証し、結果とエラーメッセージを返す
 *
 * @param url - 検証するURL
 * @returns 検証結果とエラーメッセージ
 */
export const validateRepositoryUrl = (
    url: string,
): {
    isValid: boolean;
    error?: string;
} => {
    if (!isValidRepositoryUrl(url)) {
        return {
            isValid: false,
            error: "Please enter a valid URL starting with https://",
        };
    }
    return { isValid: true };
};

/**
 * リポジトリデータをJSON形式でダウンロードする
 *
 * @param repo - ダウンロードするリポジトリデータ
 */
export const downloadRepoFile = (repo: RepositoryData): void => {
    // PluginInfoが取得されていない場合はエラーを表示
    if (!repo.pluginInfo) {
        toast.error("Plugin info not available", {
            description: "Please fetch repository data first",
        });
        return;
    }

    // JSON形式でリポジトリファイルを作成
    const repoFile = {
        id: repo.pluginInfo.id,
        website: repo.pluginInfo.website,
        source: repo.pluginInfo.source,
        license: repo.pluginInfo.license,
        repositories: repo.pluginInfo.repositories,
    };

    // Blobオブジェクトを作成してダウンロード
    const blob = new Blob([JSON.stringify(repoFile, null, 2)], {
        type: "application/json",
    });
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url;
    a.download = `${repo.pluginInfo.id}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);

    // 成功メッセージを表示
    toast.success("Repo file downloaded", {
        description: `Saved as ${repo.pluginInfo.id}.json`,
    });
};
