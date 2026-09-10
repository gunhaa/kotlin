package com.kotlin.workbook.skeleton.level2

/** 이 마커가 붙은 리시버 안에서는 바깥 리시버의 멤버를 암묵적으로 호출할 수 없다. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
annotation class QueryDsl

/**
 * 스캔 질의.
 *
 * @property limit 최대 결과 수. 기본값은 무제한.
 */
data class Query(
    val predicate: KeyPredicate,
    val limit: Int = Int.MAX_VALUE,
)

/**
 * 질의 빌더.
 *
 * ```kotlin
 * val q = query {
 *     limit = 10
 *     namespace("user")
 *     any {                       // 내부 조건들을 Or로 묶는다
 *         prefix("user:a")
 *         valueEquals("admin")
 *     }
 * }
 * ```
 *
 * - 같은 블록 안의 조건들은 기본적으로 **And**로 묶인다.
 * - [any] 블록 안의 조건들은 **Or**로 묶이고, 그 결과가 바깥 블록의 And에 하나로 참여한다.
 * - 조건이 하나도 없으면 [KeyPredicate.All].
 */
@QueryDsl
class QueryBuilder {

    var limit: Int = Int.MAX_VALUE

    fun namespace(namespace: String) {
        TODO("L2-10")
    }

    fun prefix(prefix: String) {
        TODO("L2-10")
    }

    fun valueEquals(expected: String) {
        TODO("L2-10")
    }

    fun minVersion(version: Long) {
        TODO("L2-10")
    }

    fun any(block: QueryBuilder.() -> Unit) {
        TODO("L2-11")
    }

    fun all(block: QueryBuilder.() -> Unit) {
        TODO("L2-11")
    }

    fun build(): Query = TODO("L2-10, L2-11")
}

fun query(block: QueryBuilder.() -> Unit): Query = QueryBuilder().apply(block).build()
