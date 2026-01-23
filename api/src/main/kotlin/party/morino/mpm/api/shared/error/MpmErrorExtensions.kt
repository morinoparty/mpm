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

package party.morino.mpm.api.shared.error

import arrow.core.Either
import arrow.core.left
import arrow.core.right

/**
 * MpmErrorとEither<String>間の変換を行う拡張関数
 *
 * 段階的な移行を支援するためのユーティリティ
 */

// MpmErrorをEither<String>に変換
fun MpmError.toStringEither(): Either<String, Nothing> = this.message.left()

// Either<String, A>をEither<MpmError, A>に変換
fun <A> Either<String, A>.toMpmError(): Either<MpmError, A> = this.mapLeft { MpmError.Unknown(it) }

// Either<MpmError, A>をEither<String, A>に変換
fun <A> Either<MpmError, A>.toStringError(): Either<String, A> = this.mapLeft { it.message }

// 成功値をEither<MpmError, A>に変換
fun <A> A.rightMpm(): Either<MpmError, A> = this.right()

// MpmErrorをEither<MpmError, Nothing>に変換
fun MpmError.leftMpm(): Either<MpmError, Nothing> = this.left()