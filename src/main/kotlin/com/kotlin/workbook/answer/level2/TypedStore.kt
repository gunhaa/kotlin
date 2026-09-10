package com.kotlin.workbook.answer.level2

import com.kotlin.workbook.answer.level1.InMemoryStore
import com.kotlin.workbook.answer.level1.StoredValue

/**
 * Level 1의 문자열 스토어 위에 타입과 연산자를 얹은 뷰.
 *
 * `backing`과 `registry`가 public인 이유는 아래 inline 함수들이 이들에 접근하기 때문이다.
 * (public inline 함수는 non-public API에 접근할 수 없다.)
 */
class TypedStore(
    val backing: InMemoryStore,
    val registry: CodecRegistry = CodecRegistry.withDefaults(),
) {

    /** `store[Key("a")]` — 없으면 null. */
    operator fun get(key: Key): String? = TODO("L2-12")

    /** `store[Key("a")] = "1"` */
    operator fun set(key: Key, value: String) {
        TODO("L2-12")
    }

    /** `Key("a") in store` — 만료되지 않은 키만 true. */
    operator fun contains(key: Key): Boolean = TODO("L2-12")

    /** `store += Key("a") to "1"` */
    operator fun plusAssign(entry: Pair<Key, String>) {
        TODO("L2-12")
    }

    /** 만료되지 않은 항목을 사전순으로 순회한다. `for ((key, stored) in store)`가 가능해야 한다. */
    operator fun iterator(): Iterator<Pair<Key, StoredValue>> = TODO("L2-13")

    inline fun <reified T : Any> putTyped(key: Key, value: T) {
        this[key] = registry.encode(value)
    }

    /** 값이 없으면 null. 디코딩에 실패하면 예외를 그대로 전파한다. */
    inline fun <reified T : Any> getTyped(key: Key): T? = this[key]?.let { registry.decode<T>(it) }

    /**
     * [scan]이 실제로 평가한 항목 수. 지연 평가를 테스트로 증명하기 위한 계측 카운터다.
     * 구현할 때 조건 검사 **직전에** 1씩 올린다. 테스트가 0으로 되돌려 쓸 수 있게 열어둔다.
     */
    var scannedEntryCount: Int = 0

    /**
     * 질의에 맞는 항목을 **지연 평가**로 훑는다.
     *
     * 결과를 `List`로 모으지 말고 `Sequence`로 돌려준다. limit이 걸린 질의에서 필요한 만큼만
     * 평가된다는 것이 [scannedEntryCount]로 증명돼야 한다.
     */
    fun scan(q: Query): Sequence<Pair<Key, StoredValue>> = TODO("L2-14, L2-15")
}
