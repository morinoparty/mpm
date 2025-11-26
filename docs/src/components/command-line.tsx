import {
    HoverCard,
    HoverCardContent,
    HoverCardTrigger,
} from "@site/src/components/ui/hover-card";
import { CircleCheckBig, AlertCircle } from "lucide-react";
import { Check } from "lucide-react";
import { CircleAlert } from "lucide-react";
import { HandHelping } from "lucide-react";
import React from "react";

// バッジのプロパティを定義
interface CommandLineProps {
    status: "stable" | "newly" | "beta" | "proposal";
    command: string;
}

// バッジコンポーネントを作成
export const CommandLine: React.FC<CommandLineProps> = ({
    status,
    command,
}) => {
    // 状態に応じたスタイルを設定
    // スタイルを適用するための関数を定義
    // Tailwind CSSを使用してスタイルを定義
    const commonStyle = "rounded-lg max-w-[1024px] h-[48px] text-black"; // 文字色を黒に変更

    // 状態に応じたスタイルを返す
    const getCommandLineStyle = (status: string) => {
        const styles: { [key: string]: string } = {
            stable: `${commonStyle} bg-green-500/50 dark:bg-green-300/70`, // 緑色を見やすく
            newly: `${commonStyle} bg-blue-500/50 dark:bg-blue-300/70`, // 青色を見やすく
            beta: `${commonStyle} bg-orange-500/50 dark:bg-orange-200/70`, // オレンジ色を見やすく
            proposal: `${commonStyle} bg-gray-500/50 dark:bg-gray-200/70`, // グレーを見やすく
            deprecated: `${commonStyle} bg-purple-600/50 dark:bg-purple-200/70`,
        };
        // スタイルが存在する場合は返し、存在しない場合は空の文字列を返す
        return styles[status] || "";
    };

    const getBadgeStyle = (status: string): string => {
        const styles: { [key: string]: string } = {
            proposal: "mr-12 text-gray-800", // 明るいグレー
            beta: "mr-12 text-orange-800", // 明るいオレンジ
            newly: "mr-12 text-blue-800", // 明るい青
            stable: "mr-12 text-green-800", // 明るい緑
            deprecated : "mr-12 text-purple-400", // 明るい緑
        };
        return styles[status] || "";
    };

    return (
        <HoverCard>
            <HoverCardTrigger className={getCommandLineStyle(status)}>
                <div
                    className={`flex items-center ${getCommandLineStyle(status)}`}
                >
                    <div className="flex items-center ml-8">
                        {status === "deprecated" && (
                            <AlertCircle className={getBadgeStyle(status)} />
                        )}{" "}
                        {status === "proposal" && (
                            <HandHelping className={getBadgeStyle(status)} />
                        )}{" "}
                        {/* バッジとコマンドの間隔を広げるためにmr-4に変更 */}
                        {status === "beta" && (
                            <CircleAlert className={getBadgeStyle(status)} />
                        )}{" "}
                        {/* バッジとコマンドの間隔を広げるためにmr-4に変更 */}
                        {status === "newly" && (
                            <Check className={getBadgeStyle(status)} />
                        )}{" "}
                        {/* バッジとコマンドの間隔を広げるためにmr-4に変更 */}
                        {status === "stable" && (
                            <CircleCheckBig className={getBadgeStyle(status)} />
                        )}{" "}
                        {/* バッジとコマンドの間隔を広げるためにmr-4に変更 */}
                        <span className="text-black">{command}</span>
                    </div>
                </div>
            </HoverCardTrigger>
            <HoverCardContent className="bg-white dark:bg-[var(--ifm-background-color)] dark:text-white">
                {status === "proposal" && <span>提案中のコマンド</span>}
                {status === "beta" && <span>ベータ版のコマンド</span>}
                {status === "newly" && <span>新しいコマンド</span>}
                {status === "stable" && <span>安定版のコマンド</span>}
                {status === "deprecated" && <span>非推奨のコマンド</span>}
            </HoverCardContent>
        </HoverCard>
    );
};
