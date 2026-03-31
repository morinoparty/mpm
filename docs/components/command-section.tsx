/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { CommandLine } from "@/components/command-line";
import type { Command } from "@/types/command";
import type React from "react";

// 個別のコマンドセクションを表示するコンポーネント
export const CommandSection: React.FC<{ command: Command }> = ({ command }) => {
    return (
        <div>
            {/* コマンドライン */}
            <CommandLine status={command.status} command={command.command} />

            {/* 説明 */}
            <p className="mt-2 text-sm text-fd-muted-foreground">{command.description}</p>

            {/* 引数 */}
            {command.arguments && command.arguments.length > 0 && (
                <div className="mt-2">
                    <h4 className="text-sm font-semibold">引数:</h4>
                    <ul className="list-disc pl-6 text-sm">
                        {command.arguments.map((arg, index) => (
                            <li key={index}>
                                <code className="text-xs">{arg.name}</code>
                                <span className="text-fd-muted-foreground">
                                    {arg.required ? " (必須)" : " (オプション)"}
                                </span>
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
};
