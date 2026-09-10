package com.kotlin.workbook.skeleton.level3

/**
 * 지수 백오프 재시도.
 *
 * - `block`을 최대 [maxAttempts]번 호출한다(첫 시도 포함).
 * - n번째 시도가 실패하면 `baseDelayMillis * 2^(n-1)` 밀리초만큼 기다린 뒤 다시 시도한다.
 *   ([maxDelayMillis]로 상한을 건다.)
 * - 마지막 시도까지 실패하면 마지막 예외를 그대로 던진다.
 * - **[kotlinx.coroutines.CancellationException]은 재시도하지 않고 즉시 다시 던진다.**
 *   취소는 "실패"가 아니라 "그만두라는 신호"다. 이걸 삼키면 구조화된 동시성이 깨진다.
 *
 * @param onRetry 재시도 직전에 호출된다(계측·로깅용).
 */
suspend fun <T> retryWithBackoff(
    maxAttempts: Int,
    baseDelayMillis: Long,
    maxDelayMillis: Long = Long.MAX_VALUE,
    onRetry: (attempt: Int, cause: Throwable) -> Unit = { _, _ -> },
    block: suspend (attempt: Int) -> T,
): T = TODO("L3-06, L3-07")
