/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.config.model

import kotlinx.serialization.Serializable

/**
 * HTTPメタデータキャッシュに関する設定を表すデータクラス
 *
 * バージョン一覧などのメタデータ取得は同一セッション中に何度も繰り返されるため、
 * 短いTTLのキャッシュを挟むことでネットワーク往復とレート制限の消費を抑える。
 *
 * @property enabled メタデータキャッシュを有効にするかどうか
 * @property metadataTtlSeconds キャッシュエントリの有効期間（秒）。0以下の場合はキャッシュを使用しない
 */
@Serializable
data class CacheSettings(
    val enabled: Boolean = true,
    val metadataTtlSeconds: Long = 300
)
