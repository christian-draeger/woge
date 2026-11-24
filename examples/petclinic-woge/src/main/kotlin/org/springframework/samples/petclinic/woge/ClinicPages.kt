package org.springframework.samples.petclinic.woge

import dev.woge.host.FailureCategory
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRoute
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.button
import dev.woge.html.form
import dev.woge.html.h1
import dev.woge.html.h2
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.input
import dev.woge.html.label
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.metadata
import dev.woge.html.p
import dev.woge.html.title
import dev.woge.html.ul
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.samples.petclinic.owner.OwnerRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping

@WogeRoute("/woge/owners")
data class ClinicSearchInput(val lastName: String? = null)

@WogeRoute("/woge/owners/{ownerId}")
data class ClinicOwnerInput(val ownerId: Int)

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(prefix = "petclinic.woge", name = ["enabled"], matchIfMissing = true)
class ClinicPages {
    @Bean
    fun clinicRoutes(
        owners: OwnerRepository,
        handlers: WogeSpringMvcHandlers,
    ): SimpleUrlHandlerMapping {
        val search = PageUseCase<ClinicSearchInput> { request ->
            val lastName = request.input.lastName.orEmpty()
            val matches = owners.findByLastNameStartingWith(
                lastName,
                PageRequest.of(0, 20, Sort.by("lastName", "firstName", "id")),
            )
            htmlPage {
                clinicDocument("Find owners") {
                    form(attributes = { url("action", ClinicSearchRoute.url(ClinicSearchInput())) }) {
                        label(attributes = { attribute("for", "last-name") }) { text("Last name") }
                        input(attributes = {
                            attribute("id", "last-name")
                            attribute("name", "lastName")
                            attribute("value", lastName)
                        })
                        button { text("Find owners") }
                    }
                    if (matches.isEmpty) p { text("No matching owners.") }
                    ul {
                        for (owner in matches) {
                            li {
                                a(attributes = {
                                    url("href", ClinicOwnerRoute.url(ClinicOwnerInput(requireNotNull(owner.id))))
                                }) { text("${owner.firstName} ${owner.lastName}") }
                            }
                        }
                    }
                }
            }
        }
        val ownerDetails = PageUseCase<ClinicOwnerInput> { request ->
            val owner = owners.findById(request.input.ownerId).orElse(null)
                ?: return@PageUseCase failure(FailureCategory.NOT_FOUND, request.context.correlationId)
            htmlPage {
                clinicDocument("${owner.firstName} ${owner.lastName}") {
                    p {
                        a(attributes = { url("href", applicationUrl("/owners/${owner.id}")) }) {
                            text("Open the existing PetClinic owner page")
                        }
                    }
                    for (pet in owner.pets) {
                        h2 { text(pet.name) }
                        ul {
                            for (visit in pet.visits) li { text("${visit.date}: ${visit.description}") }
                        }
                        p {
                            a(attributes = {
                                url("href", applicationUrl("/woge/owners/${owner.id}/pets/${pet.id}/visits/new"))
                            }) { text("Record a visit for ${pet.name}") }
                        }
                    }
                }
            }
        }
        return SimpleUrlHandlerMapping(
            mapOf(
                ClinicSearchRoute.path to handlers.page(search, ClinicSearchRoute),
                ClinicOwnerRoute.path to handlers.page(ownerDetails, ClinicOwnerRoute),
            ),
            0,
        )
    }
}

@RestController
@ConditionalOnBooleanProperty(prefix = "petclinic.woge", name = ["enabled"], matchIfMissing = true)
class ClinicVisitBridge(
    private val owners: OwnerRepository,
    private val enhancement: ClinicVisitEnhancement,
) {
    @GetMapping("/woge/owners/{ownerId}/pets/{petId}/visits/new", produces = [MediaType.TEXT_HTML_VALUE])
    fun visitForm(
        @PathVariable ownerId: Int,
        @PathVariable petId: Int,
        request: HttpServletRequest,
    ): String {
        val owner = owners.findById(ownerId).orElse(null)
        val pet = owner?.getPet(petId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        return enhancement.visitPage(request, ownerId, petId, pet.name)
    }
}

private fun HtmlWriter.clinicDocument(
    heading: String,
    content: HtmlWriter.() -> Unit,
) {
    doctype()
    html(attributes = { attribute("lang", "en") }) {
        head {
            metadata("viewport", "width=device-width, initial-scale=1")
            title("$heading · PetClinic")
        }
        body {
            main {
                h1 { text(heading) }
                content()
            }
        }
    }
}
