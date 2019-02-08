package com.kiyosuke.sqlitlin.db

import android.database.Cursor
import android.database.sqlite.SQLiteStatement
import android.util.Log
import com.kiyosuke.sqlitlin.db.column.Column
import com.kiyosuke.sqlitlin.db.core.SupportDatabase
import com.kiyosuke.sqlitlin.db.core.adapter.EntityDeletionOrUpdateAdapter
import com.kiyosuke.sqlitlin.db.core.adapter.EntityInsertionAdapter
import com.kiyosuke.sqlitlin.db.core.adapter.SharedSQLiteStatement
import com.kiyosuke.sqlitlin.db.core.adapter.bind
import com.kiyosuke.sqlitlin.db.core.common.IndexCachedCursor
import com.kiyosuke.sqlitlin.db.core.exception.EmptyResultSetException
import com.kiyosuke.sqlitlin.db.table.Table
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

abstract class Dao<T : Table>(private val database: SupportDatabase) {
    abstract val table: T

    private val insertAdapter by lazy {
        object : EntityInsertionAdapter<ColumnMap>(database) {
            override fun createQuery(): String = table.insertSql

            override fun bind(stmt: SQLiteStatement, entity: ColumnMap) {
                table.columns.forEachIndexed { index, column ->
                    stmt.bind(index + 1, entity[column])
                }
            }
        }
    }

    private val updationAdapter by lazy {
        object : EntityDeletionOrUpdateAdapter<ColumnMap>(database) {
            override fun createQuery(): String = table.updateSql

            override fun bind(stmt: SQLiteStatement, entity: ColumnMap) {
                var index = 0
                table.columns.forEach { column ->
                    ++index
                    stmt.bind(index, entity[column])
                }
                table.columns.filter(Column<*>::primaryKey).forEach { column ->
                    ++index
                    stmt.bind(index, entity[column])
                }
            }
        }
    }

    private val deletionAdapter by lazy {
        object : EntityDeletionOrUpdateAdapter<ColumnMap>(database) {
            override fun createQuery(): String = table.deleteSql

            override fun bind(stmt: SQLiteStatement, entity: ColumnMap) {
                table.columns.filter(Column<*>::primaryKey).forEachIndexed { index, column ->
                    stmt.bind(index + 1, entity[column])
                }
            }
        }
    }

    private val allDeletionAdapter by lazy {
        object : SharedSQLiteStatement(database) {
            override fun createQuery(): String = "DELETE FROM ${table.tableName}"
        }
    }

    suspend fun select(query: Select<T>.() -> Unit): List<ColumnMap> =
        withContext(Dispatchers.IO) {
            val sql = Select(table).apply(query).toSql()
            val result = database.query(sql).toResultMaps(table)
            if (result.isEmpty()) throw EmptyResultSetException("Query returned empty result set: $sql")
            return@withContext result
        }

    suspend fun selectAll(): List<ColumnMap> =
        withContext(Dispatchers.IO) {
            val sql = "SELECT * FROM ${table.tableName}"
            val result = database.query(sql).toResultMaps(table)
            if (result.isEmpty()) throw EmptyResultSetException("Query returned empty result set: $sql")
            return@withContext result
        }

    suspend fun insert(item: ColumnMap) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            insertAdapter.insert(item)
        }
    }

    suspend fun insert(items: List<ColumnMap>) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            Log.d("Dao", "insert: $items")
            insertAdapter.insert(items)
        }
    }

    suspend fun update(item: ColumnMap) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            updationAdapter.handle(item)
        }
    }

    suspend fun update(items: List<ColumnMap>) = withContext(Dispatchers.IO) {
        database.runInTransaction {
            updationAdapter.handleMultiple(items)
        }
    }

    suspend fun delete(item: ColumnMap) = withContext(Dispatchers.IO) {
        deletionAdapter.handle(item)
    }

    suspend fun deleteAll() = withContext(Dispatchers.IO) {
        val stmt = allDeletionAdapter.acquire()
        database.beginTransaction()
        try {
            stmt.executeUpdateDelete()
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
            allDeletionAdapter.release(stmt)
        }
    }

    private fun Cursor.toResultMaps(table: Table): List<ColumnMap> {
        val result: MutableList<ColumnMap> = mutableListOf()
        val indexCachedCursor = IndexCachedCursor(this)
        while (indexCachedCursor.moveToNext()) {
            val resultMap = ColumnMap()
            table.columns.forEach {
                when (it) {
                    is Column.Text -> resultMap[it] = indexCachedCursor.getStringOrNull(it.name)
                    is Column.Integer -> resultMap[it] = indexCachedCursor.getIntOrNull(it.name)
                    is Column.Real -> resultMap[it] = indexCachedCursor.getDoubleOrNull(it.name)
                    is Column.Blob -> resultMap[it] = indexCachedCursor.getBlobOrNull(it.name)
                }
            }
            result.add(resultMap)
        }
        return result
    }

}