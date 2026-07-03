/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.api.domain.downloader

/**
 * プラグインダウンローダーのリポジトリインターフェース
 * 各種リポジトリからのプラグインダウンロード操作を、URLDataの種類に応じて
 * 適切な[PluginDownloader]実装へ振り分けるファサードとして定義する
 *
 * メソッドシグネチャは[PluginDownloader]と1:1で対応するため、
 * 二重管理を避けて[PluginDownloader]を継承する
 */
interface DownloaderRepository : PluginDownloader