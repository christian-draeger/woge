package org.springframework.samples.petclinic.woge

import dev.woge.host.FailureCategory
import dev.woge.host.PageUseCase
import dev.woge.host.ResponseHeaders
import dev.woge.host.ResponseMetadata
import dev.woge.host.WogeRoute
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.httpHeader
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.body
import dev.woge.html.h1
import dev.woge.html.h2
import dev.woge.html.head
import dev.woge.html.html
import dev.woge.html.li
import dev.woge.html.main
import dev.woge.html.metadata
import dev.woge.html.nav
import dev.woge.html.p
import dev.woge.html.title
import dev.woge.html.ul
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import jakarta.servlet.http.HttpServletRequest
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.samples.petclinic.owner.OwnerRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping

@WogeRoute("/woge/owners")
data class ClinicSearchInput(val lastName: String? = null, val page: Int? = null)

@WogeRoute("/woge/owners/{ownerId}")
data class ClinicOwnerInput(val ownerId: Int)

@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(prefix = "petclinic.woge", name = ["enabled"], matchIfMissing = true)
class ClinicPages {
    @Bean
    fun clinicRoutes(
        owners: OwnerRepository,
        directory: ClinicOwnerDirectory,
        handlers: WogeSpringMvcHandlers,
    ): SimpleUrlHandlerMapping {
        val search = PageUseCase<ClinicSearchInput> { request ->
            val lastName = request.input.lastName.orEmpty()
            val page = request.input.page ?: 0
            if (lastName.length > MAX_OWNER_PREFIX || page !in 0..MAX_OWNER_PAGE) {
                return@PageUseCase failure(FailureCategory.BAD_REQUEST, request.context.correlationId)
            }
            val matches = directory.lookup(lastName.trim(), page * OWNER_PAGE_SIZE, OWNER_PAGE_SIZE + 1)
            htmlPage(ResponseMetadata(headers = ownerSearchHeaders)) {
                clinicDocument("Find owners", headExtras = { clinicOwnerSearchAssets() }) {
                    clinicOwnerSearchForm(lastName)
                    if (matches.isEmpty()) p { text("No matching owners.") }
                    ul(attributes = { attribute("aria-label", "Owner search results") }) {
                        for (owner in matches.take(OWNER_PAGE_SIZE)) {
                            li {
                                a(attributes = {
                                    url("href", ClinicOwnerRoute.url(ClinicOwnerInput(owner.id)))
                                }) { text(owner.label) }
                            }
                        }
                    }
                    val hasNext = page < MAX_OWNER_PAGE && matches.size > OWNER_PAGE_SIZE
                    if (page > 0 || hasNext) {
                        nav(attributes = { attribute("aria-label", "Owner search pages") }) {
                            ul {
                                if (page > 0) li { ownerPageLink(lastName, page - 1, "Previous page") }
                                if (hasNext) li { ownerPageLink(lastName, page + 1, "Next page") }
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

internal fun HtmlWriter.clinicDocument(
    heading: String,
    headExtras: HtmlWriter.() -> Unit = {},
    content: HtmlWriter.() -> Unit,
) {
    doctype()
    html(attributes = { attribute("lang", "en") }) {
        head {
            metadata("viewport", "width=device-width, initial-scale=1")
            title("$heading · PetClinic")
            headExtras()
        }
        body {
            main {
                h1 { text(heading) }
                content()
            }
        }
    }
}

private fun HtmlWriter.ownerPageLink(lastName: String, page: Int, label: String) {
    a(attributes = { url("href", ClinicSearchRoute.url(ClinicSearchInput(lastName, page))) }) { text(label) }
}

private const val OWNER_PAGE_SIZE = 20
private const val MAX_OWNER_PAGE = 1_000
private val ownerSearchHeaders = ResponseHeaders.of(
    httpHeader(
        "Content-Security-Policy",
        "default-src 'none'; script-src 'self'; connect-src 'self'; form-action 'self'; base-uri 'none'; frame-ancestors 'none'",
    ),
)
