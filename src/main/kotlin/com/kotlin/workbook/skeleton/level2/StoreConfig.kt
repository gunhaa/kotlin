package com.kotlin.workbook.skeleton.level2

import kotlin.properties.Delegates
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * 설정 값 접근을 위임 프로퍼티로 푼다.
 *
 * - `by source` — 맵에서 프로퍼티 **이름**을 키로 값을 꺼낸다.
 * - `by lazy` — 비싼 계산을 첫 접근까지 미룬다.
 * - `by CountingDelegate` — 직접 만든 위임으로 읽기/쓰기 횟수를 센다.
 *
 * @param source 설정 원본. 키는 프로퍼티 이름과 같다.
 */
class StoreConfig(private val source: Map<String, Any?>) {

    /** 값이 바뀔 때마다 "프로퍼티이름: 이전 -> 이후" 형식으로 쌓인다. */
    val changeLog: MutableList<String> = mutableListOf()

    /** 없으면 NoSuchElementException이 나야 한다(맵 위임의 기본 동작). */
    val nodeId: String by source

    val replicationFactor: Int by source

    var readTimeoutMillis: Long by Delegates.observable(500L) { property, old, new ->
        // TODO(L2-16): changeLog에 "${property.name}: $old -> $new"를 추가한다.
    }

    /** 읽기/쓰기 횟수를 세는, 직접 만든 위임. */
    val writeTimeoutDelegate: CountingDelegate<Long> = CountingDelegate(200L)

    var writeTimeoutMillis: Long by writeTimeoutDelegate

    var summaryComputeCount: Int = 0

    /**
     * 첫 접근에만 계산된다([summaryComputeCount]로 증명한다).
     * 형식: `"<nodeId> rf=<replicationFactor>"`
     */
    val summary: String by lazy {
        summaryComputeCount++
        TODO("L2-17")
    }
}

/**
 * 값을 들고 있으면서 읽기/쓰기 횟수를 세는 위임.
 *
 * `ReadWriteProperty`를 구현하는 대신 `operator fun getValue/setValue`를 직접 선언해도 된다.
 */
class CountingDelegate<T>(initial: T) : ReadWriteProperty<Any?, T> {

    var reads: Int = 0
        private set

    var writes: Int = 0
        private set

    private var current: T = initial

    override fun getValue(thisRef: Any?, property: KProperty<*>): T = TODO("L2-18")

    override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        TODO("L2-18")
    }
}
