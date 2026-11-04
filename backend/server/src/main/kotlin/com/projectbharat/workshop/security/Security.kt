package com.projectbharat.workshop.security

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.time.Instant
import java.util.Date
import org.mindrot.jbcrypt.BCrypt

/**
 * BCrypt password hashing. Passwords and hashes must never be written to logs.
 */
object PasswordHasher {
    fun hash(password: String): String {
        require(password.length >= 12) { "Password must contain at least 12 characters" }
        require(password.length <= 72) { "Password must not exceed 72 characters" }
        return BCrypt.hashpw(password, BCrypt.gensalt(12))
    }

    fun verify(password: String, hash: String): Boolean =
        runCatching { BCrypt.checkpw(password, hash) }.getOrDefault(false)
}

data class AccessTokenClaims(
    val userId: String,
    val organizationId: String,
    val membershipId: String,
    val permissions: List<String>
)

data class JwtSettings(
    val secret: String,
    val issuer: String,
    val audience: String,
    val expiresInSeconds: Long
) {
    init {
        require(secret.toByteArray(Charsets.UTF_8).size >= 32) {
            "JWT_SECRET must contain at least 32 UTF-8 bytes"
        }
        require(issuer.isNotBlank()) { "JWT issuer must not be blank" }
        require(audience.isNotBlank()) { "JWT audience must not be blank" }
        require(expiresInSeconds in 60..3600) { "Access-token lifetime must be between 60 and 3600 seconds" }
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): JwtSettings =
            JwtSettings(
                secret = environment["JWT_SECRET"]
                    ?: error("JWT_SECRET environment variable is required"),
                issuer = environment["JWT_ISSUER"] ?: "furniture-workshop-erp",
                audience = environment["JWT_AUDIENCE"] ?: "furniture-workshop-clients",
                expiresInSeconds = environment["ACCESS_TOKEN_TTL_SECONDS"]?.toLongOrNull() ?: 900L
            )
    }
}

/**
 * Stateless access-token issuer/verifier. Refresh tokens remain opaque random values
 * and must be stored only as hashes in refresh_sessions.
 */
class AccessTokenService(private val settings: JwtSettings) {
    private val algorithm = Algorithm.HMAC256(settings.secret)

    fun issue(claims: AccessTokenClaims, now: Instant = Instant.now()): String {
        val issuedAt = now
        val expiresAt = issuedAt.plusSeconds(settings.expiresInSeconds)
        return JWT.create()
            .withIssuer(settings.issuer)
            .withAudience(settings.audience)
            .withSubject(claims.userId)
            .withClaim("organization_id", claims.organizationId)
            .withClaim("membership_id", claims.membershipId)
            .withArrayClaim("permissions", claims.permissions.distinct().sorted().toTypedArray())
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(expiresAt))
            .sign(algorithm)
    }

    fun verify(token: String) = JWT.require(algorithm)
        .withIssuer(settings.issuer)
        .withAudience(settings.audience)
        .build()
        .verify(token)
}
