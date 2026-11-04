package com.projectbharat.workshop.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

/**
 * PostgreSQL connection pool. The pool is created lazily so health checks and
 * unit tests can run without database credentials; data routes return 503 until configured.
 */
object Database {
    @Volatile private var instance: HikariDataSource? = null

    fun dataSource(environment: Map<String, String> = System.getenv()): DataSource {
        instance?.let { return it }
        synchronized(this) {
            instance?.let { return it }
            val databaseUrl = environment["DATABASE_URL"] ?: environment["JDBC_DATABASE_URL"]
                ?: error("DATABASE_URL environment variable is required")
            val config = HikariConfig().apply {
                setJdbcUrl(normalizeJdbcUrl(databaseUrl))
                username = environment["DATABASE_USER"] ?: environment["PGUSER"] ?: ""
                password = environment["DATABASE_PASSWORD"] ?: environment["PGPASSWORD"] ?: ""
                maximumPoolSize = environment["DB_POOL_SIZE"]?.toIntOrNull()?.coerceIn(2, 20) ?: 10
                minimumIdle = 1
                connectionTimeout = 10_000
                validationTimeout = 5_000
                poolName = "furniture-workshop-db"
                driverClassName = "org.postgresql.Driver"
            }
            return HikariDataSource(config).also { instance = it }
        }
    }

    private fun normalizeJdbcUrl(value: String): String =
        if (value.startsWith("jdbc:")) value else "jdbc:postgresql://$value"

    fun close() {
        synchronized(this) {
            instance?.close()
            instance = null
        }
    }
}
