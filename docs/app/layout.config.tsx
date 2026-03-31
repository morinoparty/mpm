import type { BaseLayoutProps } from "@/components/layout/shared";

export function baseOptions(): BaseLayoutProps {
    return {
        nav: {
            title: (
                <div className="flex items-center gap-2">
                    <span className="text-lg font-bold">PluginName</span>
                </div>
            ),
            transparentMode: "top",
        },
        themeSwitch: {
            enabled: false,
        },
        modrinthUrl: "https://modrinth.com/plugin/pluginname",
        githubUrl: "https://github.com/morinoparty/PluginName",
    };
}
