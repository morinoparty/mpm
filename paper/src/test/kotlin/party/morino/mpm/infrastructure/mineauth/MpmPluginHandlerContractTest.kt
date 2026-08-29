/*
 * Written in 2023-2025 by Nikomaru <nikomaru@nikomaru.dev>
 *
 * To the extent possible under law, the author(s) have dedicated all copyright and related and neighboring rights to this software to the public domain worldwide.This software is distributed without any warranty.
 *
 * You should have received a copy of the CC0 Public Domain Dedication along with this software.
 * If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */

package party.morino.mpm.infrastructure.mineauth

import kotlinx.serialization.serializer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import party.morino.mineauth.api.annotations.Authenticated
import party.morino.mineauth.api.annotations.Body
import party.morino.mineauth.api.annotations.Delete
import party.morino.mineauth.api.annotations.Get
import party.morino.mineauth.api.annotations.Patch
import party.morino.mineauth.api.annotations.Path
import party.morino.mineauth.api.annotations.Post
import party.morino.mineauth.api.annotations.Public
import party.morino.mineauth.api.annotations.Put
import party.morino.mineauth.api.annotations.Query
import party.morino.mineauth.api.annotations.QueryMap
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.hasAnnotation
import kotlin.reflect.jvm.jvmErasure

/**
 * MineAuth へのエンドポイント登録は all-or-nothing であり、1つでも検証に失敗すると
 * 全エンドポイントがマウントされない。しかもその失敗は実行時のログにしか現れないため、
 * ビルドは通ったまま HTTP API 全体が沈黙する。
 *
 * このテストは MineAuth の登録時検証（RegistrationError）と同じ観点を
 * リフレクションで再現し、その事故をビルド時に検出する。
 */
@DisplayName("MpmPluginHandler registration contract")
class MpmPluginHandlerContractTest {
    // MineAuth が認識する HTTP メソッドアノテーション
    private val httpMethodAnnotations =
        listOf(Get::class, Post::class, Put::class, Patch::class, Delete::class)

    // MineAuth が認識するパラメータアノテーション（@Caller / @PlayerParam は mpm では未使用）
    private val parameterAnnotations =
        listOf(Path::class, Query::class, QueryMap::class, Body::class)

    // HTTP メソッドアノテーションを持つ公開関数＝エンドポイント
    private val endpoints: List<KFunction<*>> =
        MpmPluginHandler::class
            .declaredMemberFunctions
            .filter { fn -> httpMethodAnnotations.any { fn.hasAnnotationOf(it) } }

    @Test
    @DisplayName("all endpoints are discovered")
    fun endpointsAreDiscovered() {
        // エンドポイントが0件だと RegistrationError.NoEndpoints になる
        assertTrue(endpoints.isNotEmpty(), "no endpoint methods found on MpmPluginHandler")
    }

    @Test
    @DisplayName("each endpoint declares exactly one HTTP method and an access declaration")
    fun endpointsDeclareMethodAndAccess() {
        endpoints.forEach { fn ->
            val methodCount = httpMethodAnnotations.count { fn.hasAnnotationOf(it) }
            assertEquals(1, methodCount, "${fn.name}: must have exactly one HTTP method annotation")

            val authenticated = fn.hasAnnotation<Authenticated>()
            val public = fn.hasAnnotation<Public>()
            assertTrue(
                authenticated != public,
                "${fn.name}: must declare exactly one of @Authenticated / @Public"
            )
        }
    }

    @Test
    @DisplayName("each endpoint uses a read or write api permission")
    fun endpointsUseSplitPermissions() {
        val allowed = setOf(MpmApiPermission.READ, MpmApiPermission.WRITE)
        endpoints.forEach { fn ->
            val permission = fn.findAnnotation<Authenticated>()?.permission
            assertTrue(
                permission in allowed,
                "${fn.name}: permission '$permission' must be one of $allowed"
            )
        }
    }

    @Test
    @DisplayName("every endpoint return type is serializable")
    fun returnTypesAreSerializable() {
        endpoints.forEach { fn ->
            // Unit を返すエンドポイントは MineAuth 側で許容されるため検証対象外
            if (fn.returnType.jvmErasure == Unit::class) return@forEach
            runCatching { serializer(fn.returnType) }
                .onFailure { error ->
                    throw AssertionError("${fn.name}: return type ${fn.returnType} is not serializable", error)
                }
        }
    }

    @Test
    @DisplayName("every parameter has exactly one supported annotation")
    fun parametersAreAnnotated() {
        endpoints.forEach { fn ->
            // 先頭のインスタンスパラメータ（this）はアノテーション対象外
            fn.parameters.drop(1).forEach { parameter ->
                val count =
                    parameterAnnotations.count { annotation ->
                        parameter.annotations.any { annotation.java.isInstance(it) }
                    }
                assertEquals(
                    1,
                    count,
                    "${fn.name}#${parameter.name}: must have exactly one parameter annotation"
                )
            }
        }
    }

    @Test
    @DisplayName("at most one body parameter and it is deserializable")
    fun bodyParametersAreSerializable() {
        endpoints.forEach { fn ->
            val bodyParameters =
                fn.parameters.drop(1).filter { parameter ->
                    parameter.annotations.any { it is Body }
                }
            assertTrue(bodyParameters.size <= 1, "${fn.name}: at most one @Body parameter is allowed")
            bodyParameters.forEach { parameter ->
                runCatching { serializer(parameter.type) }
                    .onFailure { error ->
                        throw AssertionError("${fn.name}#${parameter.name}: @Body type is not deserializable", error)
                    }
            }
        }
    }

    @Test
    @DisplayName("path parameter names match the route path segments")
    fun pathParametersMatchRoute() {
        endpoints.forEach { fn ->
            val route = fn.routePath()
            fn.parameters.drop(1).forEach { parameter ->
                val path = parameter.annotations.filterIsInstance<Path>().firstOrNull() ?: return@forEach
                assertTrue(
                    route.contains("{${path.value}}"),
                    "${fn.name}: path parameter '${path.value}' is missing from route '$route'"
                )
            }
        }
    }

    @Test
    @DisplayName("no duplicate routes are declared")
    fun routesAreUnique() {
        val routes = endpoints.map { "${it.httpMethodName()} ${it.routePath()}" }
        assertEquals(routes.size, routes.toSet().size, "duplicate routes: $routes")
    }

    /** 指定したアノテーションクラスが付与されているかを判定する */
    private fun KFunction<*>.hasAnnotationOf(annotation: KClass<out Annotation>): Boolean =
        annotations.any { annotation.java.isInstance(it) }

    /** 付与された HTTP メソッドアノテーションからルートパスを取り出す */
    private fun KFunction<*>.routePath(): String =
        annotations.firstNotNullOf { annotation ->
            when (annotation) {
                is Get -> annotation.value
                is Post -> annotation.value
                is Put -> annotation.value
                is Patch -> annotation.value
                is Delete -> annotation.value
                else -> null
            }
        }

    /** 付与された HTTP メソッドアノテーション名を返す */
    private fun KFunction<*>.httpMethodName(): String =
        httpMethodAnnotations.first { hasAnnotationOf(it) }.simpleName.orEmpty()
}