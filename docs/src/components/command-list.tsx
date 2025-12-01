/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { CommandSection } from "@site/src/components/command-section";
import type { Commands } from "@site/src/type/command";
import { commandsSchema } from "@site/src/type/command";
import React from "react";

interface CommandListProps {
    category: "manage" | "repo";
    section?: string; // セクション名でフィルタリング（オプショナル）
}

// JSONファイルを読み込んでコマンド一覧を表示するコンポーネント
export const CommandList: React.FC<CommandListProps> = ({
    category,
    section,
}) => {
    // JSONファイルを読み込む
    const commandsData = require(`../../data/${category}.json`) as Commands;

    // Zodでバリデーション
    const validatedData = commandsSchema.parse(commandsData);

    // セクションでフィルタリング（指定された場合のみ）
    const filteredCommands = section
        ? validatedData.commands.filter(
              (command) => command.section === section,
          )
        : validatedData.commands;

    return (
        <div>
            {filteredCommands.map((command, index) => (
                <React.Fragment key={index}>
                    <CommandSection command={command} />
                    {index < filteredCommands.length - 1 && (
                        <hr className="my-8" />
                    )}
                </React.Fragment>
            ))}
        </div>
    );
};
