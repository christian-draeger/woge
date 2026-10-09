@file:JvmName("WogeDevelopmentMain")
@file:OptIn(ExperimentalWogeDevelopmentApi::class)

package dev.woge.development.gradle

import dev.woge.development.ExperimentalWogeDevelopmentApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.file.Path
import kotlin.system.exitProcess

/** Entry point of `./gradlew wogeDev`. The only argument is the settings file written by the plugin. */
public fun main(args: Array<String>) {
    if (args.size != 1) {
        System.err.println("Usage: WogeDevelopmentMain <settings.properties>")
        exitProcess(2)
    }
    val settings = WogeDevelopmentSettings.readFrom(Path.of(args[0]))
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val session = scope.launch { WogeDevelopmentSession(settings).run() }
    Runtime.getRuntime().addShutdownHook(Thread { runBlocking { session.cancelAndJoin() } })
    runBlocking { session.join() }
}
