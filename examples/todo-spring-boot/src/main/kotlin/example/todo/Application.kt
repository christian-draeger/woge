package example.todo

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication(proxyBeanMethods = false)
class Application

fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
