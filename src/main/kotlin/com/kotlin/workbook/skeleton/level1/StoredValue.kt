package com.kotlin.workbook.skeleton.level1

/**
 * 스토어에 저장되는 값 하나.
 *
 * @property value        저장된 문자열 값
 * @property version      같은 키에 쓸 때마다 1부터 증가하는 버전
 * @property expiresAtMillis 만료 시각(밀리초). null이면 만료되지 않는다.
 */
data class StoredValue(
    val value: String,
    val version: Long,
    val expiresAtMillis: Long? = null,
) {

    init {
        // TODO: version은 1 이상이어야 한다. 위반 시 IllegalArgumentException.
    }

    /** [nowMillis] 시점 기준으로 만료됐는지. 만료 시각과 같은 순간은 "아직 살아 있다"로 본다. */
    fun isExpired(nowMillis: Long): Boolean = TODO("L1-02")

    /** 남은 수명(밀리초). 만료 시각이 없으면 null, 이미 만료됐으면 0. */
    fun remainingTtlMillis(nowMillis: Long): Long? = TODO("L1-03")
}
