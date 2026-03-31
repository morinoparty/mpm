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
            stable: "bg-green-50 border-green-200 dark:bg-green-950/30 dark:border-green-800",
            newly: "bg-blue-50 border-blue-200 dark:bg-blue-950/30 dark:border-blue-800",
            beta: "bg-orange-50 border-orange-200 dark:bg-orange-950/30 dark:border-orange-800",
            proposal: "bg-gray-50 border-gray-200 dark:bg-gray-950/30 dark:border-gray-700",
            deprecated: "bg-purple-50 border-purple-200 dark:bg-purple-950/30 dark:border-purple-800",
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
