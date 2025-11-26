/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
import React from "react";
import { CommandLine } from "@site/src/components/command-line";
import type { Command } from "@site/src/type/command";

// 個別のコマンドセクションを表示するコンポーネント
export const CommandSection: React.FC<{ command: Command }> = ({ command }) => {
    return (
        <div className="mb-8">
            {/* コマンドライン */}
            <CommandLine status={command.status} command={command.command} />

            {/* 説明 */}
            <p className="mt-4">{command.description}</p>

            {/* 引数 */}
            {command.arguments && command.arguments.length > 0 && (
                <div className="mt-4">
                    <h4 className="font-bold">引数:</h4>
                    <ul className="list-disc pl-6">
                        {command.arguments.map((arg, index) => (
                            <li key={index}>
                                <code>{arg.name}</code>
                                {arg.required ? " (必須)" : " (オプション)"}
                            </li>
                        ))}
                    </ul>
                </div>
            )}
        </div>
    );
};
