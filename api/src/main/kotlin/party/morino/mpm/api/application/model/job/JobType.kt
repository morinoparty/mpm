/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright
 * and related and neighboring rights to this software to the public domain worldwide.
 * This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.application.model.job

/**
 * 非同期ジョブの種別
 *
 * 同じ種別のジョブは同時に1つしか実行しない。これは実処理側のロック
 * （`PluginUpdateService` のMutex）と二重に守るためではなく、
 * 「すでに走っているので受け付けない」という判断をHTTPの入り口で
 * 409として返せるようにするためである。
 */
enum class JobType {
    /** 管理下の全プラグインを更新する（`PluginUpdateService.update()`） */
    UPDATE_ALL
}