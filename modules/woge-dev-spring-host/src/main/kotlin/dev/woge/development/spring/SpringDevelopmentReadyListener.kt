package dev.woge.development.spring

import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.ApplicationListener
import java.nio.file.Files
import java.nio.file.Path

/**
 * Development-only child handshake. ApplicationReadyEvent runs after application runners, unlike
 * Spring's ordinary startup log. A fresh request token correlates readiness with one restart attempt.
 */
public class SpringDevelopmentReadyListener : ApplicationListener<ApplicationReadyEvent> {
    override fun onApplicationEvent(event: ApplicationReadyEvent) {
        val requestFile = System.getenv("WOGE_DEV_TRIGGER_FILE") ?: return
        val token = Files.readString(Path.of(requestFile)).trim()
        check(token.matches(Regex("[a-f0-9-]{36}"))) { "Invalid Woge development readiness token" }
        println("WOGE-DEV-READY $token")
    }
}
