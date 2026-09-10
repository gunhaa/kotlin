package com.kotlin.workbook.answer.level2

/**
 * 노드 식별자.
 *
 * `String`을 그대로 쓰면 노드 ID 자리에 키를 넘겨도 컴파일된다. value class는 그 실수를
 * 컴파일 타임에 막으면서, 대부분의 경우 런타임에 래퍼 객체를 만들지 않는다.
 */
@JvmInline
value class NodeId(val value: String) {

    init {
        // TODO(L2-01): 빈 문자열/공백만 있는 값은 IllegalArgumentException.
    }

    override fun toString(): String = value
}

/**
 * 스토어 키.
 *
 * 네임스페이스는 첫 `:` 앞부분이고, `:`가 없으면 `"_"`다. (Level 1의 규칙과 같다.)
 */
@JvmInline
value class Key(val value: String) : Comparable<Key> {

    init {
        // TODO(L2-01): 빈 문자열/공백만 있는 값은 IllegalArgumentException.
    }

    /** 확장 프로퍼티가 아니라 value class의 계산 프로퍼티로 구현한다(backing field 불가). */
    val namespace: String
        get() = TODO("L2-02")

    override fun compareTo(other: Key): Int = TODO("L2-02")

    override fun toString(): String = value
}
