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
        // ビルド時に docs/public/dokka にDokkaが出力されるため、/dokka/で配信される
        dokkaUrl: "/dokka/",
    };
}
