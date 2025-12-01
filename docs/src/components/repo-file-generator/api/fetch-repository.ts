/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

// PluginInfoの型定義
export type Repository = {
    type: string;
    id: string;
    fileNameRegex: string;
    versionModifier?: string;
    downloadUrl?: string;
    fileNameTemplate?: string;
};

export type PluginInfo = {
    id: string;
    website: string;
    source: string;
    license: string;
    repositories: Repository[];
};

// URLからプラットフォームを判定
export const detectPlatform = (
    url: string,
): { type: string; id: string } | null => {
    // Modrinth: https://modrinth.com/plugin/{slug}
    const modrinthMatch = url.match(/modrinth\.com\/plugin\/([^/?]+)/);
    if (modrinthMatch) {
        return { type: "modrinth", id: modrinthMatch[1] };
    }

    // Spigot: https://www.spigotmc.org/resources/{slug}.{id}/
    const spigotMatch = url.match(/spigotmc\.org\/resources\/([^/?]+)/);
    if (spigotMatch) {
        const slugAndId = spigotMatch[1];
        const idMatch = slugAndId.match(/\.(\d+)$/);
        if (idMatch) {
            return { type: "spigot", id: idMatch[1] };
        }
    }

    // GitHub: https://github.com/{owner}/{repo}
    const githubMatch = url.match(/github\.com\/([^/]+)\/([^/?]+)/);
    if (githubMatch) {
        return { type: "github", id: `${githubMatch[1]}/${githubMatch[2]}` };
    }

    // Hangar: https://hangar.papermc.io/{author}/{slug}
    const hangarMatch = url.match(/hangar\.papermc\.io\/([^/]+)\/([^/?]+)/);
    if (hangarMatch) {
        return { type: "hangar", id: `${hangarMatch[1]}/${hangarMatch[2]}` };
    }

    return null;
};

// Modrinth APIからデータを取得
export const fetchModrinthData = async (
    slug: string,
): Promise<{ pluginInfo: Partial<PluginInfo>; files: string[] }> => {
    const response = await fetch(`https://api.modrinth.com/v2/project/${slug}`);
    if (!response.ok) {
        throw new Error(`Modrinth API error: ${response.statusText}`);
    }
    const project = await response.json();

    // 最新バージョンを取得
    const versionsResponse = await fetch(
        `https://api.modrinth.com/v2/project/${slug}/version?featured=true&loaders=["paper"]`,
    );
    const versions = versionsResponse.ok ? await versionsResponse.json() : [];

    // 最新バージョンのファイルのみを取得
    const latestVersion = versions[versions.length - 1];
    const files =
        latestVersion?.files
            ?.map((f: any) => f.filename)
            .filter((f: string) => f) || [];

    return {
        pluginInfo: {
            id: project.slug || project.id,
            website: project.page_url || `https://modrinth.com/plugin/${slug}`,
            source:
                project.source_url ||
                project.page_url ||
                `https://modrinth.com/plugin/${slug}`,
            license: project.license?.id || project.license?.name || "Unknown",
            repositories: [
                {
                    type: "modrinth",
                    id: project.id || slug,
                    fileNameRegex: ".*\\.jar$",
                    versionModifier:
                        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$",
                },
            ],
        },
        files,
    };
};

// Spigot APIからデータを取得
export const fetchSpigotData = async (
    id: string,
): Promise<{ pluginInfo: Partial<PluginInfo>; files: string[] }> => {
    const response = await fetch(`https://api.spiget.org/v2/resources/${id}`);
    if (!response.ok) {
        throw new Error(`Spigot API error: ${response.statusText}`);
    }
    const resource = await response.json();

    // 最新バージョンを取得
    const versionsResponse = await fetch(
        `https://api.spiget.org/v2/resources/${id}/versions?size=1&sort=-releaseDate`,
    );
    const versions = versionsResponse.ok ? await versionsResponse.json() : [];

    // 最新バージョンのファイル名のみを取得
    const latestVersion = versions[0];
    const files = latestVersion?.name ? [latestVersion.name] : [];

    return {
        pluginInfo: {
            id: resource.id?.toString() || id,
            website: resource.url || `https://www.spigotmc.org/resources/${id}`,
            source:
                resource.sourceCodeUrl ||
                resource.url ||
                `https://www.spigotmc.org/resources/${id}`,
            license: "Unknown",
            repositories: [
                {
                    type: "spigot",
                    id: id,
                    fileNameRegex: ".*\\.jar$",
                    versionModifier:
                        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$",
                },
            ],
        },
        files,
    };
};

// GitHub APIからデータを取得
export const fetchGitHubData = async (
    ownerRepo: string,
): Promise<{ pluginInfo: Partial<PluginInfo>; files: string[] }> => {
    const [owner, repo] = ownerRepo.split("/");
    const response = await fetch(
        `https://api.github.com/repos/${owner}/${repo}`,
    );
    if (!response.ok) {
        throw new Error(`GitHub API error: ${response.statusText}`);
    }
    const repository = await response.json();

    // 最新リリースを取得
    const releasesResponse = await fetch(
        `https://api.github.com/repos/${owner}/${repo}/releases/latest`,
    );
    const latestRelease = releasesResponse.ok
        ? await releasesResponse.json()
        : null;

    // 最新リリースのファイルのみを取得
    const files =
        latestRelease?.assets
            ?.map((a: any) => a.name)
            .filter((f: string) => f) || [];

    return {
        pluginInfo: {
            id: repo,
            website: repository.html_url,
            source: repository.html_url,
            license:
                repository.license?.spdx_id ||
                repository.license?.name ||
                "Unknown",
            repositories: [
                {
                    type: "github",
                    id: ownerRepo,
                    fileNameRegex: ".*\\.jar$",
                    versionModifier:
                        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$",
                },
            ],
        },
        files,
    };
};

// Hangar APIからデータを取得
export const fetchHangarData = async (
    authorSlug: string,
): Promise<{ pluginInfo: Partial<PluginInfo>; files: string[] }> => {
    const [author, slug] = authorSlug.split("/");
    const response = await fetch(
        `https://hangar.papermc.io/api/v1/projects/${author}/${slug}`,
    );
    if (!response.ok) {
        throw new Error(`Hangar API error: ${response.statusText}`);
    }
    const project = await response.json();

    // 最新バージョンを取得
    const versionsResponse = await fetch(
        `https://hangar.papermc.io/api/v1/projects/${author}/${slug}/versions?limit=1`,
    );
    const versions = versionsResponse.ok ? await versionsResponse.json() : [];

    // 最新バージョンのファイルのみを取得
    const latestVersion = versions.result?.[0] || versions[0];
    const files =
        latestVersion?.downloads
            ?.map((d: any) => d.fileName)
            .filter((f: string) => f) || [];

    return {
        pluginInfo: {
            id: slug,
            website: `https://hangar.papermc.io/${author}/${slug}`,
            source:
                project.externalUrl ||
                `https://hangar.papermc.io/${author}/${slug}`,
            license: project.license?.name || "Unknown",
            repositories: [
                {
                    type: "hangar",
                    id: authorSlug,
                    fileNameRegex: ".*\\.jar$",
                    versionModifier:
                        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$",
                },
            ],
        },
        files,
    };
};
