package dev.woge.tck

import dev.woge.host.RequestMethod
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SharedCacheRulesTest {
    @Test
    fun `plain and fingerprinted GET responses are storable`() {
        assertTrue(store(RequestMethod.GET, 200))
        assertTrue(store(RequestMethod.GET, 405))
        assertTrue(store(RequestMethod.HEAD, 200, "public, max-age=31536000, immutable"))
    }

    @Test
    fun `no-store, private, cookies and unsafe methods are not storable`() {
        assertFalse(store(RequestMethod.GET, 200, "no-store"))
        assertFalse(store(RequestMethod.GET, 200, "Private, no-cache"))
        assertFalse(store(RequestMethod.POST, 200, "public, max-age=60"))
        assertFalse(store(RequestMethod.GET, 500))
        assertFalse(
            sharedCacheMayStore(
                RequestMethod.GET,
                200,
                emptyMap(),
                mapOf("cache-control" to listOf("public"), "set-cookie" to listOf("id=1")),
            ),
        )
    }

    @Test
    fun `authorized requests need explicit shared permission`() {
        val authorization = mapOf("authorization" to "Bearer x")
        val maxAge = mapOf("Cache-Control" to listOf("max-age=60"))
        assertFalse(sharedCacheMayStore(RequestMethod.GET, 200, authorization, maxAge))
        assertTrue(
            sharedCacheMayStore(RequestMethod.GET, 200, authorization, mapOf("Cache-Control" to listOf("s-maxage=60"))),
        )
    }

    private fun store(
        method: RequestMethod,
        status: Int,
        cacheControl: String? = null,
    ): Boolean =
        sharedCacheMayStore(
            method,
            status,
            emptyMap(),
            cacheControl?.let { mapOf("Cache-Control" to listOf(it)) } ?: emptyMap(),
        )
}
