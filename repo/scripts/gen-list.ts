/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

import { existsSync, readFileSync, readdirSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import { DelegationIndexSchema } from "../src/type/delegation-index";
import { PluginInfoSchema } from "../src/type/plugin-info";

const paperDir = join(__dirname, "..", "public", "paper");
const pluginsDir = join(paperDir, "plugins");
const listFile = join(paperDir, "_list.json");
const delegationsFile = join(paperDir, "_delegations.json");
const snapshotsFile = join(paperDir, "_snapshots.json");

// 委譲先indexの取得タイムアウト。落ちていてもビルドは止めず既存スナップショットを保つ
const FETCH_TIMEOUT_MS = 15000;

type Delegation = {
    root: string;
    index: string;
    allowedSources: string[];
    retired?: string[];
};

const write = (path: string, value: unknown) => {
    writeFileSync(path, `${JSON.stringify(value, null, 2)}\n`);
};

/** 既存のJSONを読む。無ければ undefined */
const readJsonIfExists = <T>(path: string): T | undefined =>
    existsSync(path) ? (JSON.parse(readFileSync(path, "utf-8")) as T) : undefined;

const main = async () => {
    const files = readdirSync(pluginsDir).filter((file) =>
        file.endsWith(".json"),
    );

    // ---- 1. 中央のプラグイン一覧を生成する ----
    // 委譲されたプラグイン名はここに含めない。Worker がリクエスト時に
    // スナップショット/ライブキャッシュと合成するため、中央分だけを持つ。
    const names = files.map((file) => file.replace(".json", "")).sort();
    write(listFile, names);
    console.log(`Generated _list.json with ${names.length} plugins`);

    // ---- 2. 委譲テーブルを抽出する ----
    const delegations: Delegation[] = [];
    for (const file of files) {
        const raw = JSON.parse(readFileSync(join(pluginsDir, file), "utf-8"));
        const parsed = PluginInfoSchema.safeParse(raw);
        if (!parsed.success) {
            console.error(`✗ ${file} はPluginInfoSchemaに適合しません`);
            console.error(parsed.error.message);
            process.exit(1);
        }
        const info = parsed.data;
        if (info.id !== file.replace(".json", "")) {
            console.error(`✗ ${file} の id (${info.id}) がファイル名と一致しません`);
            process.exit(1);
        }
        if (info.delegate) {
            delegations.push({
                root: info.id,
                index: info.delegate.index,
                allowedSources: info.delegate.allowedSources,
                retired: info.delegate.retired,
            });
        }
    }
    delegations.sort((a, b) => a.root.localeCompare(b.root));

    // 委譲スコープ同士が包含関係にあると解決先が曖昧になるため、CIで落とす
    for (const a of delegations) {
        for (const b of delegations) {
            if (a.root !== b.root && `${b.root}-`.startsWith(`${a.root}-`)) {
                console.error(
                    `✗ 委譲スコープが重複しています: "${a.root}" と "${b.root}"`,
                );
                process.exit(1);
            }
        }
    }

    write(delegationsFile, { schemaVersion: 1, delegations });
    console.log(`Generated _delegations.json with ${delegations.length} delegations`);

    // ---- 3. 委譲先indexのスナップショットを更新する ----
    const previous =
        readJsonIfExists<Record<string, { plugins: Record<string, unknown> }>>(
            snapshotsFile,
        ) ?? {};
    const snapshots: Record<string, unknown> = {};
    let failed = false;

    for (const delegation of delegations) {
        const prior = previous[delegation.root];
        let fetched: unknown;

        try {
            const response = await fetch(delegation.index, {
                headers: { accept: "application/json", "user-agent": "mpm-repo" },
                signal: AbortSignal.timeout(FETCH_TIMEOUT_MS),
            });
            if (!response.ok) throw new Error(`HTTP ${response.status}`);
            fetched = await response.json();
        } catch (error) {
            // 取得失敗はビルドを止めない。既存スナップショットを保持して警告に留める
            console.warn(
                `⚠ ${delegation.root} のindex取得に失敗しました (${String(error)})`,
            );
            if (prior) {
                snapshots[delegation.root] = prior;
                console.warn(`  既存のスナップショットを保持します`);
            } else {
                console.warn(`  スナップショットが無いため、この委譲先はダウン時に503になります`);
            }
            continue;
        }

        const parsed = DelegationIndexSchema.safeParse(fetched);
        if (!parsed.success) {
            console.error(
                `✗ ${delegation.root} のindexがDelegationIndexSchemaに適合しません`,
            );
            console.error(parsed.error.message);
            failed = true;
            continue;
        }
        if (parsed.data.root !== delegation.root) {
            console.error(
                `✗ ${delegation.index} の root (${parsed.data.root}) が委譲元 (${delegation.root}) と一致しません`,
            );
            failed = true;
            continue;
        }

        // 単調性チェック: 前回スナップショットにあったキーが消えていたら落とす。
        // zodは通るが中身が欠けたindex（生成スクリプトのバグ等）が「床」を壊すのを防ぐ。
        // 意図的な廃止は中央の delegate.retired に追記して解除する。
        if (prior) {
            const retired = new Set(delegation.retired ?? []);
            const missing = Object.keys(prior.plugins).filter(
                (name) => !(name in parsed.data.plugins) && !retired.has(name),
            );
            if (missing.length > 0) {
                console.error(
                    `✗ ${delegation.root} のindexから消えたプラグインがあります: ${missing.join(", ")}`,
                );
                console.error(
                    `  意図的な廃止なら ${delegation.root}.json の delegate.retired に追記してください`,
                );
                failed = true;
                continue;
            }
        }

        snapshots[delegation.root] = { plugins: parsed.data.plugins };
        console.log(
            `  ✓ ${delegation.root}: ${Object.keys(parsed.data.plugins).length} plugins`,
        );
    }

    if (failed) {
        console.error("✗ スナップショットの更新に失敗しました");
        process.exit(1);
    }

    write(snapshotsFile, snapshots);
    console.log(`Generated _snapshots.json for ${Object.keys(snapshots).length} delegations`);
};

main().catch((error) => {
    console.error(error);
    process.exit(1);
});
