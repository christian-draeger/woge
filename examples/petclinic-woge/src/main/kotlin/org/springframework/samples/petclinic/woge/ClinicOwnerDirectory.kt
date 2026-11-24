package org.springframework.samples.petclinic.woge

import dev.woge.html.HtmlWriter
import dev.woge.html.IslandModule
import dev.woge.html.IslandProps
import dev.woge.html.IslandRegistry
import dev.woge.html.IslandType
import dev.woge.html.button
import dev.woge.html.form
import dev.woge.html.input
import dev.woge.html.island
import dev.woge.html.islandLoader
import dev.woge.html.islandManifest
import dev.woge.html.label
import dev.woge.html.p
import dev.woge.html.ul
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty
import org.springframework.http.CacheControl
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

private const val SUGGESTION_PATH = "/woge/owner-suggestions"
private const val MIN_SUGGESTION_LENGTH = 2
internal const val MAX_OWNER_PREFIX = 40
private const val SUGGESTION_LIMIT = 8

data class ClinicOwnerMatch(val id: Int, val label: String)

@RestController
@ConditionalOnBooleanProperty(prefix = "petclinic.woge", name = ["enabled"], matchIfMissing = true)
class ClinicOwnerDirectory {
    @PersistenceContext
    private lateinit var entities: EntityManager

    @GetMapping(SUGGESTION_PATH, produces = [MediaType.APPLICATION_JSON_VALUE])
    fun suggestions(@RequestParam("lastName") lastName: String): ResponseEntity<List<ClinicOwnerMatch>> {
        if (lastName.length > MAX_OWNER_PREFIX) throw ResponseStatusException(HttpStatus.BAD_REQUEST)
        val prefix = lastName.trim()
        val matches = if (prefix.length < MIN_SUGGESTION_LENGTH) emptyList() else lookup(prefix, 0, SUGGESTION_LIMIT)
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(matches)
    }

    /** Project names and IDs before pagination, without loading pets or counting the entire result set. */
    internal fun lookup(prefix: String, offset: Int, limit: Int): List<ClinicOwnerMatch> =
        entities.createQuery(
            """
            select new org.springframework.samples.petclinic.woge.ClinicOwnerMatch(
                owner.id, concat(owner.firstName, ' ', owner.lastName)
            )
            from Owner owner
            where owner.lastName like :prefix escape '!'
            order by owner.lastName, owner.firstName, owner.id
            """.trimIndent(),
            ClinicOwnerMatch::class.java,
        )
            .setParameter("prefix", prefix.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%")
            .setFirstResult(offset)
            .setMaxResults(limit)
            .resultList
}

private val ownerSuggestions = IslandType<Unit>("clinic-owner-suggestions", 1) {
    IslandProps(
        mapOf(
            "endpoint" to SUGGESTION_PATH,
            "minLength" to MIN_SUGGESTION_LENGTH.toString(),
            "maxLength" to MAX_OWNER_PREFIX.toString(),
            "limit" to SUGGESTION_LIMIT.toString(),
        ),
    )
}
private val directoryIslands = IslandRegistry(IslandModule(ownerSuggestions, "/assets/clinic-owner-suggestions.js", "1"))

internal fun HtmlWriter.clinicOwnerSearchAssets() {
    islandManifest(directoryIslands)
    islandLoader()
}

internal fun HtmlWriter.clinicOwnerSearchForm(lastName: String) {
    island(ownerSuggestions, Unit, key = "clinic-owner-search") {
        form(attributes = { url("action", ClinicSearchRoute.url(ClinicSearchInput())) }) {
            label(attributes = { attribute("for", "last-name") }) { text("Last name") }
            input(attributes = {
                attribute("id", "last-name")
                attribute("name", "lastName")
                attribute("value", lastName)
                attribute("maxlength", MAX_OWNER_PREFIX.toString())
            })
            button { text("Find owners") }
        }
        p(attributes = { attribute("role", "status") }) {}
        ul(attributes = { attribute("aria-label", "Owner suggestions") }) {}
    }
}
