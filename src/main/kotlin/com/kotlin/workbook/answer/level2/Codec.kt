package com.kotlin.workbook.answer.level2

import kotlin.reflect.KClass

/** 도메인 타입 ↔ 저장 문자열 변환기. */
interface Codec<T : Any> {
    fun encode(value: T): String
    fun decode(raw: String): T
}

/**
 * 타입별 코덱 저장소.
 *
 * `reified` 덕분에 호출부는 `registry.encode(42)`처럼 `KClass`를 손으로 넘기지 않아도 된다.
 * 단, **public inline 함수는 private 멤버에 접근할 수 없다** — 그래서 내부 저장소에 닿는
 * 통로([codecFor], [register])는 public non-inline 함수로 열어둔다.
 */
class CodecRegistry {

    private val codecs: MutableMap<KClass<*>, Codec<*>> = mutableMapOf()

    fun <T : Any> register(type: KClass<T>, codec: Codec<T>) {
        TODO("L2-06")
    }

    /** 등록되지 않은 타입이면 IllegalStateException. */
    fun <T : Any> codecFor(type: KClass<T>): Codec<T> = TODO("L2-06, L2-07")

    inline fun <reified T : Any> register(codec: Codec<T>): Unit = register(T::class, codec)

    inline fun <reified T : Any> encode(value: T): String = codecFor(T::class).encode(value)

    inline fun <reified T : Any> decode(raw: String): T = codecFor(T::class).decode(raw)

    companion object {
        /** String / Int / Long / Boolean 코덱이 미리 등록된 레지스트리. */
        fun withDefaults(): CodecRegistry = TODO("L2-06")
    }
}
