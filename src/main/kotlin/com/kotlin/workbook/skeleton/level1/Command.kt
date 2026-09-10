package com.kotlin.workbook.skeleton.level1

/**
 * 스토어가 처리할 수 있는 명령.
 *
 * sealed 계층이므로 `when(command)`에 else 가지 없이 모든 경우를 다룰 수 있다.
 * 새 명령을 추가하면 컴파일러가 처리하지 않은 `when`을 전부 에러로 잡아준다.
 *
 * 이 파일의 타입 선언은 [완성본] 이다 — 시그니처를 바꾸지 않는다.
 */
sealed interface Command {

    data class Put(val key: String, val value: String, val ttlMillis: Long? = null) : Command

    data class Get(val key: String) : Command

    data class Delete(val key: String) : Command

    data object Keys : Command
}

/** 명령 실행 결과. */
sealed interface CommandResult {

    data class Value(val value: String, val version: Long) : CommandResult

    /** 키가 없거나 만료됐을 때. */
    data object Empty : CommandResult

    data class KeyList(val keys: List<String>) : CommandResult

    data class Failure(val reason: String) : CommandResult
}
