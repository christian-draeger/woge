package org.springframework.samples.petclinic.woge

import dev.woge.host.FailureCategory
import dev.woge.host.LiveRegionPage
import dev.woge.host.PageUseCase
import dev.woge.host.WogeRegion
import dev.woge.host.WogeRoute
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.host.liveRegion
import dev.woge.html.HtmlWriter
import dev.woge.html.a
import dev.woge.html.applicationUrl
import dev.woge.html.li
import dev.woge.html.moduleScript
import dev.woge.html.p
import dev.woge.html.ul
import dev.woge.spring.mvc.WogeSpringMvcHandlers
import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.samples.petclinic.owner.Owner
import org.springframework.samples.petclinic.owner.OwnerRepository
import org.springframework.session.jdbc.config.annotation.web.http.EnableJdbcHttpSession
import org.springframework.session.web.http.SessionRepositoryFilter
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping

@WogeRoute("/woge/cluster/owners/{ownerId}")
data class ClinicLiveVisitsInput(val ownerId: Int)

@Configuration(proxyBeanMethods = false)
@Profile("woge-cluster")
@EnableJdbcHttpSession
class ClinicLiveVisits {
    @Bean
    fun clinicSessionFilter(): DelegatingFilterProxyRegistrationBean =
        DelegatingFilterProxyRegistrationBean("springSessionRepositoryFilter").apply {
            order = SessionRepositoryFilter.DEFAULT_ORDER
            setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR)
        }

    @Bean
    fun clinicLiveVisitsRoute(
        owners: OwnerRepository,
        listener: PostgresVisitListener,
        handlers: WogeSpringMvcHandlers,
        jdbc: JdbcTemplate,
    ): SimpleUrlHandlerMapping {
        val page = PageUseCase<ClinicLiveVisitsInput> { request ->
            val owner = owners.findById(request.input.ownerId).orElse(null)
                ?: return@PageUseCase failure(FailureCategory.NOT_FOUND, request.context.correlationId)
            val visits = owner.visitRows()
            htmlPage {
                val render = request.context.render.resume(request.context.render.epoch)
                clinicDocument(
                    "Live visits for ${owner.firstName} ${owner.lastName}",
                    headExtras = {
                        render.metadata(this)
                        moduleScript(applicationUrl("/assets/woge/index.js"))
                    },
                ) {
                    liveRegion(
                        render,
                        ClinicVisitRowsRegion,
                        visits,
                        ClinicLiveVisitsRoute.url(request.input),
                        attributes = { attribute("id", "clinic-live-visits") },
                    )
                    p {
                        a(attributes = { url("href", applicationUrl("/woge/owners/${owner.id}")) }) {
                            text("Open owner details and record a visit")
                        }
                    }
                }
            }
        }
        val live = LiveRegionPage(
            page = page,
            region = ClinicVisitRowsRegion,
            load = { request -> owners.findById(request.input.ownerId).orElse(null)?.visitRows().orEmpty() },
            changes = { listener.changes },
            authorize = { request ->
                if (owners.existsById(request.input.ownerId)) null
                else failure(FailureCategory.NOT_FOUND, request.context.correlationId)
            },
        )
        return SimpleUrlHandlerMapping(
            mapOf(ClinicLiveVisitsRoute.path to handlers.liveRegion(live, ClinicLiveVisitsRoute)),
            0,
        ).apply { setInterceptors(ClinicLiveOwnerAccess(jdbc)) }
    }
}

private class ClinicLiveOwnerAccess(private val jdbc: JdbcTemplate) : HandlerInterceptor {
    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as? Map<*, *>
        val ownerId = (variables?.get("ownerId") as? String)?.toIntOrNull() ?: return true
        val granted = jdbc.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM clinic_live_owner_access WHERE username = ? AND owner_id = ?)",
            Boolean::class.java,
            request.remoteUser,
            ownerId,
        ) == true
        // The original Spring Security chain authenticates before MVC. Keep owner grants route-local.
        if (!granted) response.status = HttpServletResponse.SC_NOT_FOUND
        return granted
    }
}

@WogeRegion
fun HtmlWriter.clinicVisitRows(visits: List<String>) {
    if (visits.isEmpty()) p { text("No recorded visits.") }
    ul {
        for (visit in visits) li { text(visit) }
    }
}

private fun Owner.visitRows(): List<String> =
    pets.flatMap { pet ->
        pet.visits.sortedWith(compareBy({ it.date }, { it.id })).map { visit ->
            "${pet.name}, ${visit.date}: ${visit.description}"
        }
    }
