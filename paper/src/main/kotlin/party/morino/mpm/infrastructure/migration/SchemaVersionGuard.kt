/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.migration

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import party.morino.mpm.api.domain.migration.SchemaVersions
import party.morino.mpm.utils.Utils
import java.io.File

/**
 * 「現行版数より新しいファイルを、通常の保存処理で巻き戻さない」ためのガード
 *
 * ## なぜ必要か
 * [party.morino.mpm.api.domain.migration.SchemaMigrator] は schemaVersion が
 * [SchemaVersions.CURRENT] より大きいファイルを触らない。しかし通常の保存経路は
 * 無条件に現行版数をスタンプするため、そのままでは
 *
 * 1. 新しい mpm でファイルが v3 になる
 * 2. 古い mpm で起動する（マイグレータは v3 を触らない = ここまでは安全）
 * 3. 何らかの操作で保存が走る -> v2 にダウングレードされ、未知フィールドも消える
 *
 * という経路でデータが壊れる。特に `Utils.json` は `ignoreUnknownKeys = true` のため、
 * v3 の mpm.json でも読み込み自体は成功してしまい、失われたことに気付けない。
 *
 * ## 判定の対象
 * 判定するのは常に「ディスク上の既存ファイル」であり、保存しようとしている DTO ではない。
 * DTO 側の schemaVersion は読み込み時のデフォルト値に引きずられて信用できないため。
 *
 * ## 判定できない場合は許可する
 * ファイルが存在しない場合（新規作成）と、壊れていて版数を読み取れない場合は書き込みを許可する。
 * 拒否の対象を「明確に未来版数と判った場合」だけに絞ることで、
 * 壊れたファイルからの復旧（再インストール・再生成）経路を塞がないようにしている。
 */
object SchemaVersionGuard {
    // 未知フィールドを無視して schemaVersion だけを読むための非strictな Yaml
    // Yaml.default は strictMode = true のため、未来の版数で増えたフィールドを持つファイルで例外になってしまう
    private val probeYaml = Yaml(configuration = YamlConfiguration(strictMode = false))

    /**
     * JSON ファイル（mpm.json / config.json）への書き込みが安全かどうかを判定する
     *
     * @param file 書き込み先ファイル
     * @return 書き込んでよい場合はUnit、未来版数のため拒否する場合はその理由
     */
    fun ensureJsonWritable(file: File): Either<String, Unit> =
        ensureWritable(file) { text ->
            // Utils.json は ignoreUnknownKeys = true なので、未知キーがあっても版数だけを取り出せる
            Utils.json.decodeFromString(SchemaVersionProbe.serializer(), text).schemaVersion
        }

    /**
     * metadata の YAML ファイルへの書き込みが安全かどうかを判定する
     *
     * @param file 書き込み先ファイル
     * @return 書き込んでよい場合はUnit、未来版数のため拒否する場合はその理由
     */
    fun ensureYamlWritable(file: File): Either<String, Unit> =
        ensureWritable(file) { text ->
            probeYaml.decodeFromString(SchemaVersionProbe.serializer(), text).schemaVersion
        }

    /**
     * ディスク上の版数を先読みし、現行版数より新しければ書き込みを拒否する
     *
     * @param file 書き込み先ファイル
     * @param probe ファイル内容から schemaVersion を読み取る処理（形式ごとに差し替える）
     * @return 書き込んでよい場合はUnit、未来版数のため拒否する場合はその理由
     */
    private fun ensureWritable(
        file: File,
        probe: (String) -> Int
    ): Either<String, Unit> {
        // 新規作成は当然許可する（既存データが無いので壊しようがない）
        if (!file.exists()) return Unit.right()

        // 読めない・解釈できないファイルは版数を判定できないため許可側に倒す
        // （復旧のための再生成を塞がないことを優先する）
        val found = runCatching { probe(file.readText()) }.getOrNull() ?: return Unit.right()

        if (found > SchemaVersions.CURRENT) {
            return (
                "${file.name} は、このmpmが対応するスキーマ版数(v${SchemaVersions.CURRENT})より新しいv${found}で書かれています。" +
                    "そのまま保存するとv${SchemaVersions.CURRENT}へ巻き戻り未知の設定が失われるため、書き込みを中止しました。" +
                    "mpmを最新版へ更新するか、該当ファイルを退避してください。"
            ).left()
        }
        return Unit.right()
    }
}