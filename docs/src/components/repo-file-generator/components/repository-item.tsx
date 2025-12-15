/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import {
    AccordionContent,
    AccordionItem,
    AccordionTrigger,
} from "@site/src/components/ui/accordion";
import { Button } from "@site/src/components/ui/button";
import { Input } from "@site/src/components/ui/input";
import { Label } from "@site/src/components/ui/label";
import ExternalLink from "lucide-react/dist/esm/icons/external-link.js";
import RefreshCw from "lucide-react/dist/esm/icons/refresh-cw.js";
import X from "lucide-react/dist/esm/icons/x.js";
import React from "react";
import type { RepositoryItemProps } from "../types";

/**
 * 個別のリポジトリ情報を表示・編集するコンポーネント
 * URLとRepository設定のみを管理
 */
export const RepositoryItem: React.FC<RepositoryItemProps> = ({
    repo,
    index,
    onFetch,
    onRemove,
    onUpdateRepository,
}) => {
    // フィルタリングされたファイル一覧を取得
    const getFilteredFiles = (): string[] => {
        if (!repo.repository?.fileNameRegex) {
            return repo.downloadFiles;
        }

        try {
            const regexPattern = new RegExp(repo.repository.fileNameRegex);
            return repo.downloadFiles.filter((file) => regexPattern.test(file));
        } catch (error) {
            return repo.downloadFiles;
        }
    };

    // versionModifierを適用してバージョン文字列を正規化
    const applyVersionModifier = (version: string, modifier?: string): string | null => {
        if (!modifier) return version;

        try {
            const regex = new RegExp(modifier);
            const match = version.match(regex);
            return match ? match[0] : null;
        } catch (error) {
            return null;
        }
    };

    // Version Modifier Previewのためのバージョン情報を取得
    // latestVersionフィールド（API取得時のバージョン文字列）を使用
    // versionModifierは実際にはこのバージョン文字列に対して適用される
    const getVersionInfo = (): { original: string | null; modified: string | null } => {
        // latestVersionが存在する場合はそれを使用（全プラットフォーム対応）
        const originalVersion = repo.latestVersion;

        if (!originalVersion) {
            return { original: null, modified: null };
        }

        const modifiedVersion = applyVersionModifier(
            originalVersion,
            repo.repository?.versionModifier
        );

        return { original: originalVersion, modified: modifiedVersion };
    };

    const filteredFiles = getFilteredFiles();
    const versionInfo = getVersionInfo();

    return (
        <div className="border rounded-md p-4 mb-2 bg-card">
            {/* 基本情報エリア（常に表示） */}
            <div className="flex flex-col gap-3">
                {/* 1行目: URL + アクションボタン */}
                <div className="flex items-center justify-between gap-2">
                    {/* URL表示 */}
                    <div className="flex items-center gap-2 flex-1 min-w-0">
                        <ExternalLink className="h-4 w-4 text-muted-foreground shrink-0" />
                        <a
                            href={repo.url}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-sm text-foreground hover:text-primary truncate font-medium"
                        >
                            {repo.url}
                        </a>
                    </div>

                    {/* Fetchボタン */}
                    <Button
                        variant="outline"
                        size="sm"
                        onClick={() => onFetch(index)}
                        disabled={repo.isLoading}
                    >
                        <RefreshCw
                            className={`h-4 w-4 mr-2 ${repo.isLoading ? "animate-spin" : ""}`}
                        />
                        Fetch
                    </Button>

                    {/* Removeボタン */}
                    <Button
                        variant="ghost"
                        size="icon"
                        className="h-8 w-8 hover:bg-destructive hover:text-destructive-foreground"
                        onClick={() => onRemove(index)}
                        aria-label="Remove URL"
                    >
                        <X className="h-4 w-4" />
                    </Button>
                </div>

                {/* Repository情報の簡易表示 */}
                {repo.repository && (
                    <div className="text-sm text-muted-foreground pl-6">
                        <span className="font-semibold">{repo.repository.type}</span> /{" "}
                        <span>{repo.repository.id}</span>
                        {repo.downloadFiles.length > 0 && (
                            <span className="ml-4">
                                Files: {filteredFiles.length} / {repo.downloadFiles.length}
                            </span>
                        )}
                    </div>
                )}
            </div>

            {/* 詳細設定エリア（Accordion） */}
            {repo.repository && (
                <div className="mt-4">
                    <AccordionItem value={`repo-${index}`} className="border-none">
                        <AccordionTrigger className="py-2 hover:no-underline">
                            <span className="text-sm font-semibold">
                                Repository Configuration
                            </span>
                        </AccordionTrigger>
                        <AccordionContent>
                            <div className="flex flex-col gap-4 pt-2">
                                {/* Repository編集フォーム */}
                                <div className="flex flex-col gap-4 p-4 border rounded-md bg-muted/30">
                                    <div className="grid grid-cols-2 gap-4">
                                        {/* Type */}
                                        <div className="flex flex-col gap-2">
                                            <Label htmlFor={`repo-type-${index}`}>
                                                Type
                                            </Label>
                                            <Input
                                                id={`repo-type-${index}`}
                                                value={repo.repository.type}
                                                onChange={(e) =>
                                                    onUpdateRepository(index, {
                                                        ...repo.repository!,
                                                        type: e.target.value,
                                                    })
                                                }
                                            />
                                        </div>

                                        {/* ID */}
                                        <div className="flex flex-col gap-2">
                                            <Label htmlFor={`repo-id-${index}`}>
                                                ID
                                            </Label>
                                            <Input
                                                id={`repo-id-${index}`}
                                                value={repo.repository.id}
                                                onChange={(e) =>
                                                    onUpdateRepository(index, {
                                                        ...repo.repository!,
                                                        id: e.target.value,
                                                    })
                                                }
                                            />
                                        </div>

                                        {/* File Name Regex */}
                                        <div className="flex flex-col gap-2 col-span-2">
                                            <Label htmlFor={`repo-regex-${index}`}>
                                                File Name Regex
                                            </Label>
                                            <Input
                                                id={`repo-regex-${index}`}
                                                value={repo.repository.fileNameRegex}
                                                onChange={(e) =>
                                                    onUpdateRepository(index, {
                                                        ...repo.repository!,
                                                        fileNameRegex: e.target.value,
                                                    })
                                                }
                                                placeholder=".*\\.jar$"
                                                disabled={repo.repository.type === "spigotmc"}
                                            />
                                            {repo.repository.type === "spigotmc" && (
                                                <p className="text-xs text-muted-foreground">
                                                    SpigotMC では1つのバージョンにつき1つのファイルのみのため、ファイル名フィルタは不要です
                                                </p>
                                            )}
                                        </div>

                                        {/* Version Modifier (Optional) */}
                                        <div className="flex flex-col gap-2 col-span-2">
                                            <Label htmlFor={`repo-version-${index}`}>
                                                Version Modifier (Optional)
                                            </Label>
                                            <Input
                                                id={`repo-version-${index}`}
                                                value={
                                                    repo.repository.versionModifier || ""
                                                }
                                                onChange={(e) =>
                                                    onUpdateRepository(index, {
                                                        ...repo.repository!,
                                                        versionModifier:
                                                            e.target.value || undefined,
                                                    })
                                                }
                                                placeholder="(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)"
                                            />
                                        </div>

                                        {/* Version Preview（全プラットフォーム対応） */}
                                        {/*
                                            latestVersionフィールドを使用してバージョン文字列のプレビューを表示
                                            versionModifierは実際にこのバージョン文字列に対して適用される
                                        */}
                                        {versionInfo.original && (
                                            <div className="flex flex-col gap-2 col-span-2 p-3 bg-muted/50 rounded-md">
                                                <Label className="text-sm font-semibold">
                                                    Version Modifier Preview
                                                </Label>
                                                <p className="text-xs text-muted-foreground">
                                                    バージョン文字列に対してversionModifierを適用した結果のプレビュー
                                                </p>
                                                <div className="flex items-center gap-4 text-sm">
                                                    <div className="flex items-center gap-2">
                                                        <span className="text-muted-foreground">元のバージョン:</span>
                                                        <code className="px-2 py-1 bg-background rounded">
                                                            {versionInfo.original}
                                                        </code>
                                                    </div>
                                                    {repo.repository.versionModifier && (
                                                        <>
                                                            <span className="text-muted-foreground">→</span>
                                                            <div className="flex items-center gap-2">
                                                                <span className="text-muted-foreground">正規化後:</span>
                                                                {versionInfo.modified ? (
                                                                    <code className="px-2 py-1 bg-primary/10 text-primary rounded font-semibold">
                                                                        {versionInfo.modified}
                                                                    </code>
                                                                ) : (
                                                                    <code className="px-2 py-1 bg-destructive/10 text-destructive rounded text-xs">
                                                                        変換失敗
                                                                    </code>
                                                                )}
                                                            </div>
                                                        </>
                                                    )}
                                                </div>
                                            </div>
                                        )}

                                        {/* Download URL (Optional) */}
                                        <div className="flex flex-col gap-2">
                                            <Label htmlFor={`repo-download-${index}`}>
                                                Download URL (Optional)
                                            </Label>
                                            <Input
                                                id={`repo-download-${index}`}
                                                type="url"
                                                value={repo.repository.downloadUrl || ""}
                                                onChange={(e) =>
                                                    onUpdateRepository(index, {
                                                        ...repo.repository!,
                                                        downloadUrl:
                                                            e.target.value || undefined,
                                                    })
                                                }
                                            />
                                        </div>

                                        {/* File Name Template (Optional) */}
                                        <div className="flex flex-col gap-2 col-span-2">
                                            <Label htmlFor={`repo-template-${index}`}>
                                                File Name Template (Optional)
                                            </Label>
                                            <Input
                                                id={`repo-template-${index}`}
                                                value={
                                                    repo.repository.fileNameTemplate || ""
                                                }
                                                onChange={(e) =>
                                                    onUpdateRepository(index, {
                                                        ...repo.repository!,
                                                        fileNameTemplate:
                                                            e.target.value || undefined,
                                                    })
                                                }
                                            />
                                        </div>
                                    </div>
                                </div>

                                {/* ダウンロードファイル一覧 */}
                                {repo.downloadFiles.length > 0 && (
                                    <div className="flex flex-col gap-2 p-4 border rounded-md">
                                        <Label className="text-base font-semibold">
                                            Download Files
                                        </Label>
                                        <div className="flex flex-col gap-1 max-h-48 overflow-y-auto">
                                            {filteredFiles.map((file, fileIndex) => (
                                                <div
                                                    key={fileIndex}
                                                    className="text-sm p-2 bg-muted/50 rounded hover:bg-muted transition-colors"
                                                >
                                                    {file}
                                                </div>
                                            ))}
                                            {filteredFiles.length === 0 && (
                                                <div className="text-sm text-muted-foreground p-2">
                                                    No files match the regex pattern
                                                </div>
                                            )}
                                        </div>
                                    </div>
                                )}
                            </div>
                        </AccordionContent>
                    </AccordionItem>
                </div>
            )}
        </div>
    );
};
