package example.woge

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication(proxyBeanMethods = false)
public class Application {
    @Bean
    public fun homePage(): HomePage = HomePage()
}

@Suppress("SpreadOperator")
public fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
