package com.kotlin.workbook.answer.level1

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Level 1 테스트.
 *
 * 규칙: **테스트 이름은 반드시 요구사항 ID로 시작한다.** 채점기가 JUnit 결과 XML에서
 * 이름이 `L1-01`처럼 시작하는 테스트를 찾아 점수를 매긴다.
 *
 * 아래 L1-01은 형식을 보여주는 예시다. 나머지 요구사항(L1-02 ~ L1-12)은 직접 채운다.
 * 요구사항 목록: `docs/workbook/level1.md`
 */
class Level1Test {

    /** 시간을 직접 밀어주는 가짜 시계. 시간 의존 로직은 전부 이걸로 검증한다. */
    private class FakeClock(private var now: Long = 0L) : Clock {

        override fun nowMillis(): Long = now

        fun advance(millis: Long) {
            now += millis
        }
    }

    @Test
    fun `L1-01 version이 1 미만이면 StoredValue를 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { StoredValue(value = "v", version = 0) }
        assertFailsWith<IllegalArgumentException> { StoredValue(value = "v", version = -1) }
    }

    // TODO: L1-02 ~ L1-12를 여기에 작성한다.
    //
    // 예: fun `L1-07 TTL이 지난 키는 get에서 보이지 않는다`() {
    //         val clock = FakeClock()
    //         val store = InMemoryStore(clock)
    //         ...
    //     }
}
