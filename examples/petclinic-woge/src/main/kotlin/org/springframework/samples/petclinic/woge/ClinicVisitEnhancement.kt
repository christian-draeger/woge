package org.springframework.samples.petclinic.woge

import dev.woge.host.ACTION_NAVIGATION_HEADER
import dev.woge.host.ACTION_VALIDATION_HEADER
import dev.woge.host.CsrfVerification
import dev.woge.host.MUTATION_REQUEST_IDENTITY_HEADER
import dev.woge.host.RenderIdentitySecret
import dev.woge.host.RenderSession
import dev.woge.host.RenderSessions
import dev.woge.host.ResponseMetadata
import dev.woge.host.ResponseStatus
import dev.woge.host.acceptsActionPatches
import dev.woge.host.htmlPage
import dev.woge.host.requestsUnsupportedActionPatchVersion
import dev.woge.host.withMutationRequestIdentity
import dev.woge.html.ApplicationUrl
import dev.woge.html.applicationUrl
import dev.woge.html.renderHtml
import dev.woge.protocol.PatchStreamV1
import dev.woge.spring.mvc.SameOriginSpringMvcRequestContextFactory
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.context.MessageSource
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.samples.petclinic.owner.OwnerRepository
import org.springframework.security.web.csrf.CsrfToken
import org.springframework.validation.BindingResult
import org.springframework.validation.FieldError
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.HandlerMapping
import org.springframework.web.servlet.ModelAndView
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.support.RequestContextUtils
import java.nio.charset.StandardCharsets.UTF_8
import java.time.LocalDate

/** Lets the secured upstream controller bind, validate and store the visit before translating its view result. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty(prefix = "petclinic.woge", name = ["enabled"], matchIfMissing = true)
class ClinicVisitEnhancement(
    private val owners: OwnerRepository,
    private val messages: MessageSource,
    renderIdentitySecret: RenderIdentitySecret,
) : WebMvcConfigurer, HandlerInterceptor {
    private val renderSessions = RenderSessions(renderIdentitySecret)

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(this).addPathPatterns("/owners/{ownerId}/pets/{petId}/visits/new")
    }

    internal fun visitPage(request: HttpServletRequest, ownerId: Int, petId: Int, petName: String): String {
        val csrf = request.csrfToken()
        val view = ClinicVisitFormView(
            request.visitAction(ownerId, petId),
            csrf.parameterName,
            csrf.token,
            LocalDate.now().plusDays(1),
        )
        val render = renderSessions.create()
        return renderHtml {
            clinicVisitDocument("Record a visit for $petName", request.browserAsset(), render, view)
        }
    }

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (request.method != "POST" || handler !is HandlerMethod ||
            handler.method.declaringClass.name != "org.springframework.samples.petclinic.owner.VisitController" ||
            handler.method.name != "processNewVisitForm"
        ) {
            return true
        }
        val accepts = request.getHeaders("Accept").toList()
        if (accepts.none { acceptsActionPatches(it) || requestsUnsupportedActionPatchVersion(it) }) return true
        val accept = accepts.singleOrNull() ?: return reject(response, HttpServletResponse.SC_BAD_REQUEST)
        if (requestsUnsupportedActionPatchVersion(accept)) {
            response.setHeader("Woge-Protocol-Error", "unsupported-version")
            return reject(response, HttpServletResponse.SC_NOT_ACCEPTABLE)
        }

        val context = SameOriginSpringMvcRequestContextFactory.create(request)
        // The existing filter chain has already checked login and CSRF. Enhancement also requires same-origin evidence.
        if (request.userPrincipal == null || context.csrf != CsrfVerification.VERIFIED) {
            return reject(response, HttpServletResponse.SC_FORBIDDEN)
        }
        if (!request.isUrlEncoded()) return reject(response, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE)
        if (request.queryString != null) return reject(response, HttpServletResponse.SC_BAD_REQUEST)
        if (request.contentLengthLong > MAX_ENHANCED_FORM_BYTES) {
            return reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE)
        }

        val fields = request.parameterMap
        if (fields.values.any { it.size != 1 }) return reject(response, HttpServletResponse.SC_BAD_REQUEST)
        val decodedCharacters = fields.entries.sumOf { (name, values) -> name.length.toLong() + values.single().length }
        if (decodedCharacters > MAX_ENHANCED_FORM_BYTES ||
            fields.entries.sumOf { (name, values) ->
                name.toByteArray(UTF_8).size + values.single().toByteArray(UTF_8).size
            } > MAX_ENHANCED_FORM_BYTES
        ) {
            return reject(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE)
        }

        val variables = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE) as Map<*, *>
        val ownerId = variables["ownerId"].toString().toIntOrNull()
        val petId = variables["petId"].toString().toIntOrNull()
        if (ownerId == null || petId == null) return reject(response, HttpServletResponse.SC_BAD_REQUEST)
        val render = try {
            val identities = request.getHeaders(MUTATION_REQUEST_IDENTITY_HEADER).toList()
            require(identities.size == 1 && request.getHeaders("Woge-Page-Epoch").toList().size == 1)
            val snapshot = context.withMutationRequestIdentity(identities)
            renderSessions.forRequest(snapshot.headers).also { session ->
                val id = session.target(ClinicVisitFormRegion).target.region.value
                val targetRevisions = request.getHeader("Woge-Region-Revisions").orEmpty().split(',')
                require(targetRevisions.any { it.substringBefore(':') == id })
            }
        } catch (_: IllegalArgumentException) {
            return reject(response, HttpServletResponse.SC_BAD_REQUEST)
        }
        val pet = owners.findById(ownerId).orElse(null)?.getPet(petId)
            ?: return reject(response, HttpServletResponse.SC_NOT_FOUND)
        request.setAttribute(VISIT_ENHANCEMENT, EnhancedVisit(ownerId, petId, pet.name, render))
        return true
    }

    override fun postHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any,
        modelAndView: ModelAndView?,
    ) {
        val visit = request.getAttribute(VISIT_ENHANCEMENT) as? EnhancedVisit ?: return
        val result = checkNotNull(modelAndView)
        response.addHeader("Vary", "Accept")
        response.setHeader("Cache-Control", "no-store, no-transform")
        when (result.viewName) {
            "redirect:/owners/{ownerId}" -> {
                val target = applicationUrl("${request.contextPath}/owners/${visit.ownerId}").value
                RequestContextUtils.saveOutputFlashMap(target, request, response)
                response.status = HttpServletResponse.SC_OK
                response.setHeader(ACTION_NAVIGATION_HEADER, target)
            }
            "pets/createOrUpdateVisitForm" -> writeValidation(request, response, visit, result)
            else -> error("Unexpected view returned by the pinned PetClinic visit controller")
        }
        result.clear()
    }

    private fun writeValidation(
        request: HttpServletRequest,
        response: HttpServletResponse,
        visit: EnhancedVisit,
        result: ModelAndView,
    ) {
        val binding = result.model[BindingResult.MODEL_KEY_PREFIX + "visit"] as BindingResult
        val locale = RequestContextUtils.getLocale(request)
        val errors = binding.allErrors.map {
            val field = (it as? FieldError)?.field?.takeIf { name -> name == "date" || name == "description" }
            VisitError(field, messages.getMessage(it, locale))
        }
        check(errors.isNotEmpty())
        val token = request.csrfToken()
        val view = ClinicVisitFormView(
            request.visitAction(visit.ownerId, visit.petId),
            token.parameterName,
            token.token,
            result.model["minVisitDate"] as LocalDate,
            request.getParameter("date").orEmpty(),
            request.getParameter("description").orEmpty(),
            errors,
        )
        val asset = request.browserAsset()
        val document = htmlPage(ResponseMetadata(status = ResponseStatus.BAD_REQUEST)) {
            clinicVisitDocument(
                "Record a visit for ${visit.petName}",
                asset,
                visit.render.resume(visit.render.epoch),
                view,
            )
        }
        val validation = visit.render.validation(document, visitSummary) { replace(ClinicVisitFormRegion, view) }
        response.status = validation.metadata.status.code
        response.contentType = PatchStreamV1.MEDIA_TYPE
        response.setHeader(ACTION_VALIDATION_HEADER, visitSummary.value)
        response.outputStream.write(PatchStreamV1.encode(validation.patches))
    }
}

private class EnhancedVisit(val ownerId: Int, val petId: Int, val petName: String, val render: RenderSession)

private const val MAX_ENHANCED_FORM_BYTES = 4096
private const val VISIT_ENHANCEMENT = "org.springframework.samples.petclinic.woge.enhancedVisit"

private fun HttpServletRequest.csrfToken(): CsrfToken = getAttribute(CsrfToken::class.java.name) as CsrfToken

private fun HttpServletRequest.visitAction(ownerId: Int, petId: Int): ApplicationUrl =
    applicationUrl("$contextPath/owners/$ownerId/pets/$petId/visits/new")

private fun HttpServletRequest.browserAsset(): ApplicationUrl =
    applicationUrl("$contextPath/assets/woge/index.js")

private fun HttpServletRequest.isUrlEncoded(): Boolean {
    val contentType = contentType ?: return false
    val media = runCatching { MediaType.parseMediaType(contentType) }.getOrNull() ?: return false
    return MediaType.APPLICATION_FORM_URLENCODED.equalsTypeAndSubtype(media) &&
        (media.charset == null || media.charset == UTF_8)
}

private fun reject(response: HttpServletResponse, status: Int): Boolean {
    response.status = status
    response.addHeader("Vary", "Accept")
    response.setHeader("Cache-Control", "no-store")
    return false
}
