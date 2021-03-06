package com.kiyosuke.sqlitlin.db

import com.kiyosuke.sqlitlin.db.column.Column

class ColumnMap {
    private val map: MutableMap<Column<*>, Any?> = mutableMapOf()

    fun isNull(column: Column<*>): Boolean {
        val result = map[column]
        return result == null
    }

    operator fun <T> get(key: Column<T>): T? {
        val result = map[key]
        @Suppress("UNCHECKED_CAST")
        return result as? T
    }

    fun <T> getValue(key: Column<T>): T {
        val result = requireNotNull(map[key]) {
            "${key.name} is null"
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    fun forEach(action: (Map.Entry<Column<*>, Any?>) -> Unit) {
        this.map.forEach(action)
    }

    operator fun <T> set(key: Column<T>, value: T?) {
        this.map[key] = value
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ColumnMap

        if (map != other.map) return false

        return true
    }

    override fun hashCode(): Int {
        return map.hashCode()
    }

    override fun toString(): String {
        return "ColumnMap(map=$map)"
    }

}