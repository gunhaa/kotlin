package com.kotlin.workbook.answer.level2

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * Level 2 테스트. 이름은 반드시 `L2-XX`로 시작한다.
 *
 * 요구사항 목록: `docs/workbook/level2.md`
 */
class Level2Test {

    @Test
    fun `L2-01 빈 식별자는 만들 수 없다`() {
        assertFailsWith<IllegalArgumentException> { NodeId("") }
        assertFailsWith<IllegalArgumentException> { Key("   ") }
    }

    // TODO: L2-02 ~ L2-18을 여기에 작성한다.
}
