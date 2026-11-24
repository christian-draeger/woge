package org.springframework.samples.petclinic.woge

import jakarta.annotation.PreDestroy
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.postgresql.PGConnection
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.sql.SQLException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

@Component
@Profile("woge-cluster")
class PostgresVisitListener(private val dataSource: DataSource) {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val ready = CountDownLatch(1)
    private val invalidations = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Volatile private var running = true

    val changes = invalidations.asSharedFlow()

    private val worker = Thread.ofPlatform().daemon().name("petclinic-visit-listener").start(::listen)

    init {
        if (!ready.await(10, TimeUnit.SECONDS)) {
            stop()
            error("PostgreSQL visit listener failed to start")
        }
    }

    private fun listen() {
        while (running) {
            try {
                dataSource.connection.use { connection ->
                    connection.createStatement().use { it.execute("LISTEN woge_petclinic_visits") }
                    val postgres = connection.unwrap(PGConnection::class.java)
                    // Both a fresh subscription and a recovered listener reload authoritative data.
                    invalidations.tryEmit(Unit)
                    ready.countDown()
                    while (running) {
                        if (!postgres.getNotifications(250).isNullOrEmpty()) invalidations.tryEmit(Unit)
                    }
                    connection.createStatement().use { it.execute("UNLISTEN woge_petclinic_visits") }
                }
            } catch (failure: SQLException) {
                if (running) {
                    logger.warn("PostgreSQL visit listener disconnected (SQL state {})", failure.sqlState)
                    Thread.sleep(1_000)
                }
            }
        }
    }

    @PreDestroy
    fun stop() {
        running = false
        worker.join(1_500)
    }
}
