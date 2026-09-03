package dev.woge.baseline.mvc

import dev.woge.baseline.shared.BaselineDelays
import dev.woge.baseline.shared.ReferenceProjectStore
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.core.env.Environment

@SpringBootApplication
class MvcBaselineApplication {
    @Bean
    fun referenceProjectStore() = ReferenceProjectStore()

    @Bean
    fun baselineDelays(environment: Environment) = BaselineDelays(
        summaryMillis = environment.getProperty("baseline.delay.summary", Long::class.java, 80L),
        tasksMillis = environment.getProperty("baseline.delay.tasks", Long::class.java, 350L),
        activityMillis = environment.getProperty("baseline.delay.activity", Long::class.java, 160L),
    )
}

fun main(args: Array<String>) {
    runApplication<MvcBaselineApplication>(*args)
}
