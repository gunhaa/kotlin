package com.kotlin.workbook.skeleton.level3

import com.kotlin.workbook.skeleton.level2.Key
import com.kotlin.workbook.skeleton.level2.NodeId
import com.kotlin.workbook.skeleton.level2.Versioned

/** 노드가 받는 요청. */
sealed interface NodeMessage {

    data class Write(val key: Key, val value: String, val version: Long) : NodeMessage

    data class Read(val key: Key) : NodeMessage

    data object Ping : NodeMessage
}

/** 노드가 돌려주는 응답. */
sealed interface NodeResponse {

    /** @property applied 이미 더 높은 version이 있어 무시했으면 false. */
    data class WriteAck(
        val nodeId: NodeId,
        val key: Key,
        val version: Long,
        val applied: Boolean,
    ) : NodeResponse

    data class ReadResult(
        val nodeId: NodeId,
        val key: Key,
        val value: Versioned<String>?,
    ) : NodeResponse

    data class Pong(val nodeId: NodeId) : NodeResponse
}

/** 노드에 실제로 값이 반영됐을 때 흘러나오는 이벤트. */
data class ChangeEvent(
    val nodeId: NodeId,
    val key: Key,
    val value: String,
    val version: Long,
)

/** 네트워크가 상대 노드에 닿지 못했다. */
class NodeUnreachableException(val nodeId: NodeId) :
    Exception("node $nodeId is unreachable")
