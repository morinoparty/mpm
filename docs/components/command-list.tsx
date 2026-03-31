/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

"use client";

import { CommandSection } from "@/components/command-section";
import type { Commands } from "@/types/command";
import { commandsSchema } from "@/types/command";
import React from "react";

// 各カテゴリのJSONデータをインポート
import manageData from "@/data/manage.json";
import repoData from "@/data/repo.json";
import backupData from "@/data/backup.json";
import dependencyData from "@/data/dependency.json";
import pluginData from "@/data/plugin.json";

interface CommandListProps {
    category: "manage" | "repo" | "backup" | "dependency" | "plugin";
    section?: string; // セクション名でフィルタリング（オプショナル）
}

// カテゴリに応じたデータを取得
const getCommandsData = (category: string): Commands => {
    const dataMap: Record<string, unknown> = {
        manage: manageData,
        repo: repoData,
        backup: backupData,
        dependency: dependencyData,
        plugin: pluginData,
    };
    return dataMap[category] as Commands;
};

// JSONファイルを読み込んでコマンド一覧を表示するコンポーネント
export const CommandList: React.FC<CommandListProps> = ({ category, section }) => {
    // カテゴリに応じたデータを取得
    const commandsData = getCommandsData(category);

    // Zodでバリデーション
    const validatedData = commandsSchema.parse(commandsData);

    // セクションでフィルタリング（指定された場合のみ）
    const filteredCommands = section
        ? validatedData.commands.filter((command) => command.section === section)
        : validatedData.commands;

    return (
        <div>
            {filteredCommands.map((command, index) => (
                <React.Fragment key={index}>
                    <CommandSection command={command} />
                    {index < filteredCommands.length - 1 && <hr className="mt-8 mb-8" />}
                </React.Fragment>
            ))}
        </div>
    );
};
