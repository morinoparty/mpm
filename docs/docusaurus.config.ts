import type * as Preset from "@docusaurus/preset-classic";
import type { Config } from "@docusaurus/types";
import { themes as prismThemes } from "prism-react-renderer";

// This runs in Node.js - Don't use client-side code here (browser APIs, JSX...)

const config: Config = {
    title: "MPM Documentation",
    favicon: "img/favicon.ico",
    staticDirectories: ["static"],
    trailingSlash: true,

    url: "https://mpm.plugin.morino.party",
    baseUrl: "/",

    organizationName: "morinoparty",
    projectName: "mpm",
    onBrokenLinks: "throw",
    markdown: {
        hooks: {
            onBrokenMarkdownLinks: "warn",
        },
    },

    i18n: {
        defaultLocale: "ja",
        locales: ["ja"],
    },

    presets: [
        [
            "classic",
            {
                docs: {
                    sidebarPath: "./sidebars.ts",
                    routeBasePath: "",
                    editUrl:
                        "https://github.com/morinoparty/mpm/tree/master/docs/",
                },
                theme: {
                    customCss: "./src/css/custom.css",
                },
            } satisfies Preset.Options,
        ],
    ],
    plugins: [
        ["./src/plugins/tailwind-config.js", {}],
        ["./src/plugins/llms-txt.ts", {}],
        [
            require.resolve("@easyops-cn/docusaurus-search-local"),
            {
                indexDocs: true,
                language: "ja",
                docsRouteBasePath: "/",
            },
        ],
    ],
    themeConfig: {
        image: "img/docusaurus-social-card.jpg",
        navbar: {
            title: "mpm",
            logo: {
                alt: "mpm Logo",
                src: "img/logo.svg",
            },
            items: [
                {
                    href: "https://github.com/morinoparty/mpm",
                    label: "GitHub",
                    position: "right",
                },
                {
                    href: "https://modrinth.com/plugin/mpm-package",
                    label: "Modrinth",
                    position: "right",
                },
                {
                    href: "https://github.com/morinoparty/mpm/releases",
                    label: "Release",
                    position: "right",
                },
                {
                    href: "/dokka",
                    label: "Dokka",
                    position: "left",
                    target: "_blank",
                },
            ],
        },
        footer: {
            style: "dark",
            links: [
                {
                    title: "ドキュメント",
                    items: [
                        {
                            label: "はじめに",
                            to: "/intro",
                        },
                    ],
                },
                {
                    title: "コミュニティ",
                    items: [
                        {
                            label: "ホームページ",
                            href: "https://morino.party",
                        },
                        {
                            label: "Discord",
                            href: "https://discord.com/invite/9HdanPM",
                        },
                        {
                            label: "X",
                            href: "https://x.com/morinoparty",
                        },
                    ],
                },
                {
                    title: "その他",
                    items: [
                        {
                            label: "GitHub",
                            href: "https://github.com/morinoparty/mpm",
                        },
                    ],
                },
            ],
            copyright:
                "No right reserved. This docs under CC0. Built with Docusaurus.",
        },
        prism: {
            additionalLanguages: [
                "java",
                "groovy",
                "diff",
                "toml",
                "yaml",
                "kotlin",
            ],
            theme: prismThemes.github,
            darkTheme: prismThemes.dracula,
        },
    } satisfies Preset.ThemeConfig,
    future: {
        v4: true,
        experimental_faster: {
            swcJsLoader: true,
            swcJsMinimizer: true,
            swcHtmlMinimizer: true,
            lightningCssMinimizer: true,
            rspackBundler: true,
            rspackPersistentCache: true,
            ssgWorkerThreads: true,
            mdxCrossCompilerCache: true,
        },
    },
};

export default config;
