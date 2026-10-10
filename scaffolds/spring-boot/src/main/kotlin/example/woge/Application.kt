package example.woge

import dev.woge.html.AssetUrls
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication(proxyBeanMethods = false)
public class Application {
    @Bean
    public fun homePage(assets: AssetUrls): HomePage = HomePage(assets)
}

@Suppress("SpreadOperator")
public fun main(args: Array<String>) {
    runApplication<Application>(*args)
}
