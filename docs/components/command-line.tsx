/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { AlertCircle, Check, CircleAlert, CircleCheckBig, HandHelping } from "lucide-react";
import type React from "react";

interface CommandLineProps {
    status: "stable" | "newly" | "beta" | "proposal" | "deprecated";
    command: string;
}

export const CommandLine: React.FC<CommandLineProps> = ({ status, command }) => {
    // アイコンの色を状態に応じて設定
    const getIconColor = (status: string): string => {
        const colors: { [key: string]: string } = {
            stable: "text-green-600 dark:text-green-400",
            newly: "text-blue-600 dark:text-blue-400",
            beta: "text-orange-600 dark:text-orange-400",
            proposal: "text-gray-500 dark:text-gray-400",
            deprecated: "text-purple-600 dark:text-purple-400",
        };
        return colors[status] || "";
    };

    // 背景色を状態に応じて設定
    const getBackgroundColor = (status: string): string => {
        const colors: { [key: string]: string } = {
            stable: "bg-green-100 border-green-300 dark:bg-green-900/50 dark:border-green-700",
            newly: "bg-blue-100 border-blue-300 dark:bg-blue-900/50 dark:border-blue-700",
            beta: "bg-orange-100 border-orange-300 dark:bg-orange-900/50 dark:border-orange-700",
            proposal: "bg-gray-100 border-gray-300 dark:bg-gray-800/50 dark:border-gray-600",
            deprecated: "bg-purple-100 border-purple-300 dark:bg-purple-900/50 dark:border-purple-700",
        };
        return colors[status] || "bg-fd-secondary border";
    };

    const iconClass = `w-4 h-4 mr-3 flex-shrink-0 ${getIconColor(status)}`;
    const bgClass = getBackgroundColor(status);

    return (
        <div
            className={`not-prose flex items-center rounded-lg border px-4 py-2.5 text-sm font-mono text-fd-foreground ${bgClass}`}
        >
            {status === "deprecated" && <AlertCircle className={iconClass} />}
            {status === "proposal" && <HandHelping className={iconClass} />}
            {status === "beta" && <CircleAlert className={iconClass} />}
            {status === "newly" && <Check className={iconClass} />}
            {status === "stable" && <CircleCheckBig className={iconClass} />}
            <span>{command}</span>
        </div>
    );
};
