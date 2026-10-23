package com.ai.assistance.operit.data.db

import androidx.sqlite.SQLITE_DATA_BLOB
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.SQLiteDriver
import androidx.sqlite.SQLiteStatement
import java.sql.Connection as JdbcConnection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.ResultSetMetaData
import java.sql.SQLException
import java.sql.Types

/**
 * 单元测试用纯 JVM SQLite 驱动：基于 org.xerial:sqlite-jdbc 实现
 * androidx.sqlite 的 KMP [SQLiteDriver]/[SQLiteConnection]/[SQLiteStatement] 接口，
 * 让 Android Room 2.8 生成的数据库实现（kapt 产物）可以在 JVM 单元测试中真实打开、
 * 迁移与读写。
 *
 * 仅用于测试；应用运行时仍使用 Android 平台的驱动。
 *
 * 实现约定：
 * - 列索引与 androidx 原生（sqlite3 C API）一致，从 0 开始；JDBC 从 1 开始，内部 +1。
 * - 事务命令（BEGIN/END/ROLLBACK/SAVEPOINT/RELEASE）在 JDBC 层直接翻译，
 *   避免 sqlite-jdbc 在显式事务 SQL 下的内部状态冲突。
 * - 无结果集的语句（DDL、PRAGMA 赋值等）在 sqlite-jdbc 的 executeQuery 下会抛出
 *   “query does not return ResultSet”，按 step() 返回 false 处理；其他 SQLException 照常抛出。
 */
class JdbcSQLiteDriver : SQLiteDriver {
    override fun open(fileName: String): SQLiteConnection = JdbcSQLiteConnection(fileName)
}

class JdbcSQLiteConnection(fileName: String) : SQLiteConnection {

    private val connection: JdbcConnection =
        DriverManager.getConnection("jdbc:sqlite:$fileName").apply {
            // Room 连接池会在同一文件上开多个连接；Windows 上 sqlite-jdbc 的
            // journal 文件删除（SQLITE_IOERR_DELETE）会被其它连接/杀软短暂锁定。
            // 内存日志模式彻底避开 journal 文件（仅测试用，无崩溃恢复需求）。
            createStatement().use { it.execute("PRAGMA journal_mode = MEMORY") }
        }

    override fun prepare(sql: String): SQLiteStatement {
        val trimmed = sql.trim()
        return when {
            trimmed.startsWith("BEGIN ") ->
                TransactionStatement { beginJdbcTransaction() }
            trimmed == "END TRANSACTION" ->
                TransactionStatement { endJdbcTransaction(commit = true) }
            trimmed.startsWith("ROLLBACK TRANSACTION TO SAVEPOINT") ||
                trimmed.startsWith("SAVEPOINT ") ||
                trimmed.startsWith("RELEASE SAVEPOINT ") ->
                // Room 连接池的嵌套事务通过 SAVEPOINT 实现（语句形如
                // `SAVEPOINT '1'`）：原样执行即可，不要拆名字（sqlite-jdbc
                // 的 JDBC Savepoint API 转义有问题）
                TransactionStatement { executeRawSql(trimmed) }
            trimmed == "ROLLBACK TRANSACTION" ->
                TransactionStatement { endJdbcTransaction(commit = false) }
            else -> JdbcSQLiteStatement(connection.prepareStatement(sql))
        }
    }

    override fun inTransaction(): Boolean = !connection.autoCommit

    override fun close() {
        connection.close()
    }

    private fun executeRawSql(sql: String) {
        try {
            connection.createStatement().use { it.execute(sql) }
        } catch (e: Exception) {
            throw IllegalStateException("raw sql failed: [$sql]", e)
        }
    }

    private fun beginJdbcTransaction() {
        if (connection.autoCommit) {
            connection.autoCommit = false
        }
    }

    private fun endJdbcTransaction(commit: Boolean) {
        if (connection.autoCommit) return
        if (commit) connection.commit() else connection.rollback()
        connection.autoCommit = true
    }

    private class TransactionStatement(private val action: () -> Unit) : SQLiteStatement {
        override fun bindBlob(index: Int, value: ByteArray) = Unit
        override fun bindDouble(index: Int, value: Double) = Unit
        override fun bindLong(index: Int, value: Long) = Unit
        override fun bindText(index: Int, value: String) = Unit
        override fun bindNull(index: Int) = Unit
        override fun getBlob(index: Int): ByteArray = ByteArray(0)
        override fun getDouble(index: Int): Double = 0.0
        override fun getLong(index: Int): Long = 0L
        override fun getText(index: Int): String = ""
        override fun isNull(index: Int): Boolean = true
        override fun getColumnCount(): Int = 0
        override fun getColumnName(index: Int): String = ""
        override fun getColumnType(index: Int): Int = SQLITE_DATA_NULL
        override fun step(): Boolean {
            action()
            return false
        }
        override fun reset() = Unit
        override fun clearBindings() = Unit
        override fun close() = Unit
    }
}

private class JdbcSQLiteStatement(
    private val statement: PreparedStatement,
) : SQLiteStatement {

    private var resultSet: ResultSet? = null
    private var executed = false

    override fun bindBlob(index: Int, value: ByteArray) = statement.setBytes(index, value)

    override fun bindDouble(index: Int, value: Double) = statement.setDouble(index, value)

    override fun bindLong(index: Int, value: Long) = statement.setLong(index, value)

    override fun bindText(index: Int, value: String) = statement.setString(index, value)

    override fun bindNull(index: Int) = statement.setNull(index, Types.NULL)

    override fun getBlob(index: Int): ByteArray = resultSetOrThrow().getBytes(index + 1) ?: ByteArray(0)

    override fun getDouble(index: Int): Double = resultSetOrThrow().getDouble(index + 1)

    override fun getLong(index: Int): Long = resultSetOrThrow().getLong(index + 1)

    override fun getText(index: Int): String = resultSetOrThrow().getString(index + 1)

    override fun isNull(index: Int): Boolean = resultSetOrThrow().getObject(index + 1) == null

    override fun getColumnCount(): Int = metadataOrNull()?.columnCount ?: 0

    override fun getColumnName(index: Int): String =
        metadataOrNull()?.getColumnName(index + 1) ?: ""

    override fun getColumnType(index: Int): Int {
        val meta = metadataOrNull() ?: return SQLITE_DATA_NULL
        return when (meta.getColumnType(index + 1)) {
            Types.INTEGER, Types.SMALLINT, Types.TINYINT, Types.BIGINT, Types.BIT, Types.BOOLEAN ->
                SQLITE_DATA_INTEGER
            Types.REAL, Types.FLOAT, Types.DOUBLE, Types.NUMERIC, Types.DECIMAL ->
                SQLITE_DATA_FLOAT
            Types.BLOB, Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY ->
                SQLITE_DATA_BLOB
            Types.NULL ->
                SQLITE_DATA_NULL
            else ->
                SQLITE_DATA_TEXT
        }
    }

    override fun step(): Boolean {
        ensureExecuted()
        return resultSet?.next() ?: false
    }

    /**
     * 尽早执行查询：Room 生成的代码在 step() 之前先取列元数据
     * （getColumnIndexOrThrow），而 sqlite-jdbc 的 PreparedStatement.getMetaData()
     * 会在同一连接上再开一条语句导致 “inconsistent internal state”。
     * 因此元数据访问触发真正的执行，之后 step() 直接从结果集取行。
     */
    private fun ensureExecuted() {
        if (executed) return
        executed = true
        try {
            resultSet = statement.executeQuery()
        } catch (e: SQLException) {
            // sqlite-jdbc 的 executeQuery 对无结果集语句（DDL、PRAGMA 赋值、
            // INSERT/UPDATE/DELETE 等）会直接抛错且**不执行**语句，
            // 这里改用 execute() 真正执行（报错文案随版本不同）。
            val message = e.message.orEmpty()
            if (message.contains("does not return", ignoreCase = true)) {
                statement.execute()
            } else {
                throw e
            }
        }
    }

    override fun reset() {
        resultSet?.close()
        resultSet = null
        executed = false
    }

    override fun clearBindings() {
        statement.clearParameters()
    }

    override fun close() {
        resultSet?.close()
        statement.close()
    }

    private fun resultSetOrThrow(): ResultSet =
        resultSet ?: throw IllegalStateException("statement has not been stepped")

    /** 列元数据：已执行时直接用结果集元数据，未执行时先执行查询再取。 */
    private fun metadataOrNull(): ResultSetMetaData? {
        if (resultSet == null) {
            ensureExecuted()
        }
        return runCatching { resultSet?.metaData }.getOrNull()
    }
}
