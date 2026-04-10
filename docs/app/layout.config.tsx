import type { BaseLayoutProps } from "@/components/layout/shared";

export function baseOptions(): BaseLayoutProps {
    return {
        nav: {
            title: (
                <div className="flex items-center gap-2">
                    <span className="text-lg font-bold">MPM</span>
                </div>
            ),
            transparentMode: "top",
        },
        themeSwitch: {
            enabled: false,
        },
        githubUrl: "https://github.com/morinoparty/mpm",
        modrinthUrl: "https://modrinth.com/plugin/mpm-package",
        // Kotlin APIリファレンス（Dokka生成ドキュメント）
        // Dokkaは docs/public/dokka に生成されるため、Next.jsのbasePath配下の /dokka/ で配信される
        // preview環境ではBASE_PATHが /mpm/<sha>/docs なので、正しく解決するために明示的にprefixを付与する
        dokkaUrl: `${process.env.NEXT_PUBLIC_BASE_PATH ?? ""}/dokka/`,
    };
}
