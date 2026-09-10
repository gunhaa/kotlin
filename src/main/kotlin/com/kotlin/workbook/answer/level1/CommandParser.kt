package com.kotlin.workbook.answer.level1

/**
 * 한 줄짜리 텍스트 명령을 [Command]로 파싱한다.
 *
 * 지원하는 문법(명령어는 대소문자를 구분하지 않는다):
 * ```
 * PUT <key> <value> [ttlMillis]
 * GET <key>
 * DEL <key>
 * KEYS
 * ```
 *
 * - 토큰은 공백으로 나눈다. 앞뒤 공백과 연속 공백은 무시한다.
 * - 잘못된 입력은 예외를 던지지 않고 `Result.failure(IllegalArgumentException(...))`로 돌려준다.
 *   (예외를 "던질지 값으로 돌려줄지"는 Kotlin에서 매번 마주치는 선택이다.)
 */
object CommandParser {

    fun parse(line: String): Result<Command> = TODO("L1-04, L1-05")
}
