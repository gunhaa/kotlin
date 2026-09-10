package com.kotlin.workbook.skeleton.level2

/**
 * 버전이 붙은 값.
 *
 * `T`는 **오직 꺼내지기만** 하므로 공변(`out`)으로 선언할 수 있다.
 * 그래서 `Versioned<String>`을 `Versioned<Any>`가 필요한 자리에 넘길 수 있다.
 *
 * @property version 단조 증가하는 버전. 1 이상.
 */
class Versioned<out T>(
    val value: T,
    val version: Long,
) : Comparable<Versioned<*>> {

    init {
        // TODO(L2-03): version은 1 이상.
    }

    /** version만 비교한다. */
    override fun compareTo(other: Versioned<*>): Int = TODO("L2-03")

    /** 값을 변환하면서 version은 유지한다. */
    fun <R> map(transform: (T) -> R): Versioned<R> = TODO("L2-03")

    override fun equals(other: Any?): Boolean = TODO("L2-04")

    override fun hashCode(): Int = TODO("L2-04")

    override fun toString(): String = "Versioned(value=$value, version=$version)"
}

/**
 * 값을 받아 쌓기만 하는 소비자. `T`는 **오직 들어오기만** 하므로 반공변(`in`)이다.
 * `Sink<Any>`를 `Sink<String>` 자리에 넘길 수 있어야 한다.
 */
fun interface Sink<in T> {
    fun accept(value: T)
}

/** 여러 복제본 중 최신 버전 하나를 고른다(Last-Write-Wins). 비어 있으면 null. */
fun <T> Iterable<Versioned<T>>.latest(): Versioned<T>? = TODO("L2-05")
