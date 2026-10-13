/*
 * Written in 2026 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.ui.command

import party.morino.mpm.ui.command.manage.control.BackupCommand
import party.morino.mpm.ui.command.manage.control.CacheCommand
import party.morino.mpm.ui.command.manage.control.InitCommand
import party.morino.mpm.ui.command.manage.control.LockCommand
import party.morino.mpm.ui.command.manage.control.PinCommand
import party.morino.mpm.ui.command.manage.info.DependencyCommand
import party.morino.mpm.ui.command.manage.info.DoctorCommand
import party.morino.mpm.ui.command.manage.info.InfoCommand
import party.morino.mpm.ui.command.manage.info.ListCommand
import party.morino.mpm.ui.command.manage.info.OutdatedCommand
import party.morino.mpm.ui.command.manage.info.SearchCommand
import party.morino.mpm.ui.command.manage.info.VerifyCommand
import party.morino.mpm.ui.command.manage.info.VersionsCommand
import party.morino.mpm.ui.command.manage.lifecycle.AddCommand
import party.morino.mpm.ui.command.manage.lifecycle.AdoptCommand
import party.morino.mpm.ui.command.manage.lifecycle.InstallCommand
import party.morino.mpm.ui.command.manage.lifecycle.RemoveCommand
import party.morino.mpm.ui.command.manage.lifecycle.RollbackCommand
import party.morino.mpm.ui.command.manage.lifecycle.UninstallCommand
import party.morino.mpm.ui.command.manage.lifecycle.UpdateCommand
import party.morino.mpm.ui.command.repo.RepositoryCommands

/**
 * Paper / Velocity の両方で登録する mpm のコマンド一覧
 *
 * 登録するコマンドを一箇所にまとめ、プラットフォーム間で差が出ないようにする。
 */
object MpmCommands {
    /**
     * 登録するコマンドのインスタンスを作る
     *
     * コマンドは Koin から依存を取得するため、Koin の起動後に呼ぶこと。
     *
     * @return Lamp に登録するコマンドのインスタンス
     */
    fun all(): List<Any> =
        listOf(
            AddCommand(),
            AdoptCommand(),
            BackupCommand(),
            CacheCommand(),
            DependencyCommand(),
            InitCommand(),
            InstallCommand(),
            ListCommand(),
            LockCommand(),
            OutdatedCommand(),
            RemoveCommand(),
            RollbackCommand(),
            UninstallCommand(),
            PinCommand(),
            UpdateCommand(),
            InfoCommand(),
            SearchCommand(),
            DoctorCommand(),
            VerifyCommand(),
            VersionsCommand(),
            RepositoryCommands(),
            ReloadCommand()
        )
}