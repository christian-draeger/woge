package example.quarkus

import dev.woge.examples.gradient.t01staticpage.StaticPage
import dev.woge.examples.gradient.t01staticpage.StaticPageInput
import dev.woge.examples.gradient.t04validation.AddValidatedNoteAction
import dev.woge.examples.gradient.t04validation.NotesPage as ValidatedNotesPage
import dev.woge.examples.gradient.t04validation.ValidatedNotesRoute
import dev.woge.examples.gradient.t04validation.addNoteAction as validatedAddNote
import dev.woge.examples.gradient.t04validation.addNoteForm as validatedForm
import dev.woge.examples.gradient.t05enhancedaction.AddEnhancedNoteAction
import dev.woge.examples.gradient.t05enhancedaction.EnhancedNotesRoute
import dev.woge.examples.gradient.t05enhancedaction.NotesPage as EnhancedNotesPage
import dev.woge.examples.gradient.t05enhancedaction.addNoteForm as enhancedForm
import dev.woge.examples.gradient.t06deferredregion.ForecastPage
import dev.woge.examples.gradient.t06deferredregion.ForecastPatchesRoute
import dev.woge.examples.gradient.t06deferredregion.ForecastRoute
import dev.woge.examples.gradient.t07liveupdate.AnnouncementBoard
import dev.woge.examples.gradient.t07liveupdate.AnnouncementsRoute
import dev.woge.host.AuthenticationFacts
import dev.woge.host.Capability
import dev.woge.host.FailureCategory
import dev.woge.host.PageUseCase
import dev.woge.host.PrincipalFacts
import dev.woge.host.PrincipalId
import dev.woge.host.WogeObserver
import dev.woge.host.failure
import dev.woge.host.htmlPage
import dev.woge.html.h1
import dev.woge.quarkus.QuarkusPageInput
import dev.woge.quarkus.QuarkusRequestContextFactory
import dev.woge.quarkus.WogeQuarkusHandlers
import dev.woge.quarkus.quarkusInput
import dev.woge.quarkus.quarkusRequestContext
import dev.woge.quarkus.quarkusSubmission
import io.quarkus.security.identity.SecurityIdentity
import io.vertx.ext.web.Router
import io.vertx.ext.web.RoutingContext
import jakarta.enterprise.context.ApplicationScoped
import jakarta.enterprise.context.control.ActivateRequestContext
import jakarta.enterprise.event.Observes
import jakarta.inject.Inject
import kotlinx.coroutines.yield
import org.jboss.logging.Logger

@ApplicationScoped
class Routes @Inject constructor(
    private val contexts: IdentityContexts,
) {
    private val logger = Logger.getLogger(Routes::class.java)
    private val handlers =
        WogeQuarkusHandlers(
            contexts = QuarkusRequestContextFactory(contexts::snapshot),
            observer = WogeObserver { event -> logger.debug(event) },
        )

    fun install(@Observes router: Router) {
        val static = handlers.page(StaticPage(), QuarkusPageInput { StaticPageInput })
        router.get("/").handler(static)
        router.head("/").handler(static)
        val forecast = ForecastPage()
        router.get(ForecastRoute.path).handler(handlers.page(forecast, ForecastRoute))
        router.get("/forecast/woge-patches/:epoch").handler(handlers.deferred(forecast, ForecastPatchesRoute.quarkusInput()))
        router.get(ValidatedNotesRoute.path).handler(handlers.page(ValidatedNotesPage(), ValidatedNotesRoute))
        router.post(AddValidatedNoteAction.path).handler(handlers.action(validatedAddNote, validatedForm.quarkusSubmission()))
        router.get(EnhancedNotesRoute.path).handler(handlers.page(EnhancedNotesPage(), EnhancedNotesRoute))
        router.post(AddEnhancedNoteAction.path).handler(handlers.action(AddEnhancedNoteAction, enhancedForm.quarkusInput()))
        router.get(AnnouncementsRoute.path).handler(handlers.liveRegion(AnnouncementBoard().live, AnnouncementsRoute))
        router.get("/identity").handler(
            handlers.page(
                PageUseCase { request ->
                    val principal = (request.context.authentication as? AuthenticationFacts.Authenticated)?.principal
                    if (principal == null || !principal.has(Capability.of("reader"))) {
                        failure(FailureCategory.FORBIDDEN, request.context.correlationId)
                    } else {
                        yield()
                        htmlPage { h1 { text("Signed in as ${principal.subject.value}") } }
                    }
                },
                QuarkusPageInput { Unit },
            ),
        )
    }
}

@ApplicationScoped
class IdentityContexts @Inject constructor(
    private val identity: SecurityIdentity,
) {
    @ActivateRequestContext
    fun snapshot(call: RoutingContext): dev.woge.host.RequestContext {
        val authentication =
            if (identity.isAnonymous) {
                AuthenticationFacts.Anonymous
            } else {
                AuthenticationFacts.Authenticated(
                    PrincipalFacts(PrincipalId.of(identity.principal.name), identity.roles.map(Capability::of)),
                )
            }
        return quarkusRequestContext(call, authentication)
    }
}
