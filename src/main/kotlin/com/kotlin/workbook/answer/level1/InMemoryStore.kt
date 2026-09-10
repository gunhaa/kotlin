package com.kotlin.workbook.answer.level1

/**
 * 단일 노드 인메모리 키-값 스토어.
 *
 * 동시성은 아직 없다 — Level 3에서 이 자리를 코루틴과 [kotlinx.coroutines.sync.Mutex]가 채운다.
 *
 * 규칙:
 * - `put`은 같은 키에 쓸 때마다 version을 1씩 올린다(첫 저장은 1).
 * - TTL이 지난 항목은 **조회 시점에** 없는 것으로 취급하고 내부 맵에서도 제거한다(lazy expiration).
 * - `keys()`는 만료되지 않은 키만 사전순으로 돌려준다.
 *
 * @param clock 시간 소스. 테스트에서는 가짜 시계를 주입한다.
 */
class InMemoryStore(private val clock: Clock = Clock.SYSTEM) {

    private val entries: MutableMap<String, StoredValue> = mutableMapOf()

    /** 만료되지 않은 항목 수. */
    val size: Int
        get() = TODO("L1-06")

    /**
     * 값을 저장하고 새 version을 돌려준다.
     *
     * @param ttlMillis 지금부터의 수명. null이면 만료 없음. 0 이하면 IllegalArgumentException.
     */
    fun put(key: String, value: String, ttlMillis: Long? = null): Long = TODO("L1-06, L1-07")

    /** 만료되지 않은 값. 없거나 만료됐으면 null. */
    fun get(key: String): StoredValue? = TODO("L1-07")

    /** 삭제되면 true, 원래 없었거나 이미 만료됐으면 false. */
    fun delete(key: String): Boolean = TODO("L1-08")

    /** 만료되지 않은 키를 사전순으로. */
    fun keys(): List<String> = TODO("L1-09")

    /** 만료되지 않은 항목 전체의 읽기 전용 스냅숏. */
    fun snapshot(): Map<String, StoredValue> = TODO("L1-09")

    /**
     * [Command]를 실행해 [CommandResult]로 돌려준다.
     *
     * - `Put` → 저장 후 `CommandResult.Value(value, newVersion)`
     * - `Get` → 있으면 `Value`, 없거나 만료면 `Empty`
     * - `Delete` → 지워졌으면 `Value(value, version)`(지워진 값), 아니면 `Empty`
     * - `Keys` → `KeyList`
     * - 인자가 잘못돼 예외가 나는 경우 → `Failure(예외 메시지)`
     */
    fun execute(command: Command): CommandResult = TODO("L1-10")
}
