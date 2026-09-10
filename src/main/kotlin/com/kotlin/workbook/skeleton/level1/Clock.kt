package com.kotlin.workbook.skeleton.level1

/**
 * 현재 시각을 밀리초로 돌려주는 추상화.
 *
 * TTL 만료 같은 "시간에 의존하는 동작"을 테스트로 증명하려면 시간이 주입 가능해야 한다.
 * 구현 코드에서 `System.currentTimeMillis()`를 직접 부르면 테스트가 `Thread.sleep`에
 * 의존하게 되고, 그 순간 테스트는 느려지고 불안정해진다.
 *
 * 이 인터페이스는 [완성본] 이다 — 수정하지 않는다.
 */
fun interface Clock {

    fun nowMillis(): Long

    companion object {
        val SYSTEM: Clock = Clock { System.currentTimeMillis() }
    }
}
