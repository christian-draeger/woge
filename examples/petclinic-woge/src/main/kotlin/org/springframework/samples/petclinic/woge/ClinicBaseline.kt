package org.springframework.samples.petclinic.woge

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.samples.petclinic.owner.OwnerRepository
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.web.SecurityFilterChain
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@Configuration(proxyBeanMethods = false)
class ClinicSecurity {
    @Bean
    fun referenceSecurity(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { requests ->
                requests.requestMatchers("/webjars/**", "/resources/**").permitAll()
                requests.anyRequest().authenticated()
            }
            .formLogin(Customizer.withDefaults())
            .build()
}

@RestController
class ClinicOwnerApi(private val owners: OwnerRepository) {
    @GetMapping("/api/reference/owners/{ownerId}", produces = [MediaType.APPLICATION_JSON_VALUE])
    fun owner(@PathVariable ownerId: Int): OwnerSummary {
        val owner = owners.findById(ownerId).orElse(null) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return OwnerSummary(ownerId, owner.firstName, owner.lastName)
    }
}

data class OwnerSummary(val id: Int, val firstName: String, val lastName: String)
