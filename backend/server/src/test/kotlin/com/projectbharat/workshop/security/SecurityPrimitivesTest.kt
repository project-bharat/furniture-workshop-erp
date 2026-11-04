package com.projectbharat.workshop.security

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class SecurityPrimitivesTest {
    @Test
    fun passwordHashVerifiesWithoutStoringPlaintext() {
        val password = "Workshop-Owner-Password-2026"
        val hash = PasswordHasher.hash(password)
        assertTrue(hash != password)
        assertTrue(PasswordHasher.verify(password, hash))
        assertFalse(PasswordHasher.verify("wrong-password-2026", hash))
    }

    @Test
    fun accessTokenCarriesTenantAndPermissionClaims() {
        val service = AccessTokenService(
            JwtSettings(
                secret = "test-secret-that-is-at-least-thirty-two-bytes-long",
                issuer = "test-issuer",
                audience = "test-audience",
                expiresInSeconds = 900
            )
        )
        val token = service.issue(
            AccessTokenClaims(
                userId = "user-123",
                organizationId = "org-456",
                membershipId = "membership-789",
                permissions = listOf("projects.view", "customers.view")
            ),
            Instant.parse("2026-11-04T10:00:00Z")
        )

        val decoded = service.verify(token)
        assertEquals("user-123", decoded.subject)
        assertEquals("org-456", decoded.getClaim("organization_id").asString())
        assertEquals("membership-789", decoded.getClaim("membership_id").asString())
        assertEquals(
            listOf("customers.view", "projects.view"),
            decoded.getClaim("permissions").asList(String::class.java)
        )
    }

    @Test
    fun jwtSecretMustBeAtLeastThirtyTwoBytes() {
        assertFailsWith<IllegalArgumentException> {
            JwtSettings("too-short", "issuer", "audience", 900)
        }
    }
}
