package io.u11.skytrainsim.backend.util

import org.jdbi.v3.core.Handle
import org.jdbi.v3.core.Jdbi
import org.jdbi.v3.core.result.ResultIterable
import org.jdbi.v3.spring5.JdbiUtil
import java.sql.ResultSet
import java.util.UUID

fun <T> Jdbi.withSpringHande(block: (Handle) -> T): T {
    val handle = JdbiUtil.getHandle(this)
    return try {
        block(handle)
    } finally {
        JdbiUtil.closeIfNeeded(handle)
    }
}

fun Handle.uuidSet(table: String) =
    this
        .createQuery("SELECT id FROM $table")
        .map { rs, _ -> rs.getUuid("id") }
        .collectIntoSet().toSet()

fun ResultSet.getUuid(columnLabel: String) = UUID.fromString(this.getString(columnLabel))

fun ResultSet.getUuid(columnIndex: Int) = UUID.fromString(this.getString(columnIndex))

fun <T : Any, K : Any> ResultIterable<T>.collectIntoMapBy(keyFunc: (T) -> K): Map<K, T> = this.collectToMap(keyFunc) { it }.toMap()
