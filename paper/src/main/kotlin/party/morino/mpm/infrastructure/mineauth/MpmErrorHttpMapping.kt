/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import arrow.core.Either
import party.morino.mineauth.api.http.HttpError
import party.morino.mineauth.api.http.HttpStatus
import party.morino.mpm.api.model.dependency.DependencyError
import party.morino.mpm.api.shared.error.MpmError

// ダウンロード時のHTTPステータス判定に使う定数
private const val NOT_FOUND = 404
private const val GONE = 410
private const val TOO_MANY_REQUESTS = 429
private const val SERVER_ERROR_MIN = 500

/**
 * [MpmError] を対応する HTTP ステータスに変換する
 *
 * メッセージの文字列マッチではなく sealed class の型で判定することで、
 * 意図しないメッセージ内容による誤判定を防ぐ。
 * `else` を使わず全リーフを列挙しているため、新しいエラーを追加すると
 * コンパイルエラーとなり、マッピングの追従漏れを防げる。
 */
internal fun MpmError.toHttpStatus(): HttpStatus =
    when (this) {
        // --- 404 Not Found: 対象が存在しない ---
        // （mpm.json未初期化やダウンロード元リポジトリ不明も「対象なし」として扱う）
        is MpmError.PluginError.NotFound,
        is MpmError.PluginError.MetadataNotFound,
        is MpmError.PluginError.RepositoryNotFound,
        is MpmError.PluginError.NotManaged,
        is MpmError.ProjectError.ConfigNotFound,
        is MpmError.ProjectError.NotInitialized,
        is MpmError.DownloadError.RepositoryNotFound,
        is MpmError.BackupError.NotFound -> HttpStatus.NOT_FOUND

        // --- 409 Conflict: リソースの現在の状態と要求が衝突している ---
        // 二重クリックによる UpdateInProgress や、ロック状態の不一致がここに入る。
        // クライアントは「もう一度同じリクエストを送る」以外の対処が必要なため 500 とは区別する。
        is MpmError.PluginError.AlreadyExists,
        is MpmError.PluginError.Locked,
        is MpmError.PluginError.NotLocked,
        is MpmError.PluginError.AlreadyLocked,
        is MpmError.PluginError.UpdateInProgress,
        is MpmError.PluginError.VersionSwitchNotAllowed,
        is MpmError.PluginError.HasDependents,
        is MpmError.PluginError.ApiVersionIncompatible,
        is MpmError.ProjectError.AlreadyInitialized -> HttpStatus.CONFLICT

        // --- 400 Bad Request: リクエストで指定された値が解決できない ---
        // 存在しないバージョン名の指定や、未対応のリポジトリ種別の指定が該当する。
        is MpmError.PluginError.VersionResolutionFailed,
        is MpmError.PluginError.UnsupportedRepository -> HttpStatus.BAD_REQUEST

        // --- 503 Service Unavailable: 上流リポジトリの一時障害 ---
        // タイムアウトやレート制限で上流（Modrinth/Hangar/GitHub等）が応答しない状態。
        // リクエスト自体は正当なため 400 とは区別し、クライアントに再試行の余地を伝える。
        is MpmError.PluginError.UpstreamUnavailable -> HttpStatus.SERVICE_UNAVAILABLE

        // --- ダウンロード先のHTTPステータスはコードごとに振り分ける ---
        // リトライを尽くしても 429 / 5xx だった場合は上流の一時障害として 503 を返し、
        // アーティファクトが存在しない 404 / 410 は 404 として返す（サーバー側の不具合ではないため）。
        // 認証エラーなどそれ以外はサーバー側の設定不備とみなして 500 のままにする。
        is MpmError.DownloadError.HttpStatus -> downloadStatusToHttpStatus(statusCode)

        // --- 500 Internal Server Error: サーバー側の処理失敗・設定不備 ---
        // クライアントが送り直しても直らない類のエラーはすべてここに集約する。
        is MpmError.PluginError.OperationCancelled,
        is MpmError.PluginError.MetadataSaveFailed,
        is MpmError.PluginError.InstallFailed,
        is MpmError.PluginError.AddFailed,
        is MpmError.PluginError.RemoveFailed,
        is MpmError.PluginError.UninstallFailed,
        is MpmError.PluginError.UpdateFailed,
        is MpmError.PluginError.IntegrityCheckFailed,
        is MpmError.ProjectError.ConfigParseError,
        is MpmError.ProjectError.SyncDependencyError,
        is MpmError.ProjectError.SyncValidationFailed,
        is MpmError.ProjectError.CircularDependency,
        is MpmError.ProjectError.InitializationFailed,
        is MpmError.ProjectError.SaveFailed,
        is MpmError.DownloadError.Failed,
        is MpmError.DownloadError.InvalidContentType,
        is MpmError.DownloadError.SizeMismatch,
        is MpmError.BackupError.Failed,
        is MpmError.BackupError.RestoreFailed,
        is MpmError.CacheError.Failed,
        is MpmError.Unknown -> HttpStatus.INTERNAL_SERVER_ERROR
    }

/**
 * ダウンロード時のHTTPステータスコードを、APIが返すHTTPステータスへ振り分ける
 *
 * 上流のレート制限（429）とサーバーエラー（5xx）は時間を置けば成功しうるため、
 * リトライ可能であることが伝わる 503 を返す。
 *
 * @param statusCode ダウンロード時に上流から返されたステータスコード
 */
private fun downloadStatusToHttpStatus(statusCode: Int): HttpStatus =
    when {
        // レート制限・上流のサーバーエラーは一時的な障害として扱う
        statusCode == TOO_MANY_REQUESTS || statusCode >= SERVER_ERROR_MIN -> HttpStatus.SERVICE_UNAVAILABLE
        // アーティファクトが存在しない（削除された等）
        statusCode == NOT_FOUND || statusCode == GONE -> HttpStatus.NOT_FOUND
        // 401/403 などはトークン設定の不備であり、クライアントが直せるものではない
        else -> HttpStatus.INTERNAL_SERVER_ERROR
    }

/**
 * [DependencyError] を対応する HTTP ステータスに変換する
 *
 * 依存関係サービスは [MpmError] ではなく独自のエラー型を返すため、別途マッピングする。
 */
internal fun DependencyError.toHttpStatus(): HttpStatus =
    when (this) {
        // 対象プラグインが存在しない
        is DependencyError.PluginNotFound -> HttpStatus.NOT_FOUND
        // 必須依存の欠落・循環依存はサーバーの現在の状態との衝突として扱う
        is DependencyError.MissingRequiredDependency,
        is DependencyError.CircularDependency -> HttpStatus.CONFLICT
        // plugin.yml の読み込み失敗はサーバー側の問題
        is DependencyError.PluginLoadError -> HttpStatus.INTERNAL_SERVER_ERROR
    }

/**
 * [Either] の左側（[MpmError]）を [HttpError] としてスローし、右側の値を取り出す
 *
 * ハンドラー各メソッドで同じ fold を書き並べるのを避けるためのヘルパー。
 * MineAuth は [HttpError] を捕捉してエラーレスポンスへ変換する。
 *
 * @return 成功時の値
 * @throws HttpError エラーの場合（[toHttpStatus] でマッピングしたステータス）
 */
internal fun <T> Either<MpmError, T>.orThrowHttpError(): T =
    fold(
        ifLeft = { error -> throw HttpError(error.toHttpStatus(), error.message) },
        ifRight = { it }
    )