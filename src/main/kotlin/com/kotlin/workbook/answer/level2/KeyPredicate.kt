package com.kotlin.workbook.answer.level2

import com.kotlin.workbook.answer.level1.StoredValue

/**
 * 스캔 조건. 자기 자신을 자식으로 갖는 재귀적 sealed 계층이다.
 *
 * 이런 "표현식 트리 + 해석기" 구조는 Java에서 방문자(Visitor) 패턴이나 instanceof 사슬로
 * 쓰던 것을 `when`으로 평평하게 푼다.
 */
sealed interface KeyPredicate {

    data object All : KeyPredicate

    data class Namespace(val namespace: String) : KeyPredicate

    data class Prefix(val prefix: String) : KeyPredicate

    data class ValueEquals(val expected: String) : KeyPredicate

    data class MinVersion(val version: Long) : KeyPredicate

    data class And(val left: KeyPredicate, val right: KeyPredicate) : KeyPredicate

    data class Or(val left: KeyPredicate, val right: KeyPredicate) : KeyPredicate

    data class Not(val inner: KeyPredicate) : KeyPredicate
}

/** 조건 평가. `when`에 else 가지를 쓰지 않는다(채점기가 검사한다). */
fun KeyPredicate.matches(key: Key, stored: StoredValue): Boolean = TODO("L2-08, L2-09")

/** 조건 두 개를 결합하는 중위 함수. `Prefix("a") and MinVersion(2)`처럼 쓴다. */
infix fun KeyPredicate.and(other: KeyPredicate): KeyPredicate = TODO("L2-08")

infix fun KeyPredicate.or(other: KeyPredicate): KeyPredicate = TODO("L2-08")

/** 단항 연산자 오버로딩. `!Prefix("tmp:")`로 부정한다. */
operator fun KeyPredicate.not(): KeyPredicate = TODO("L2-08")
