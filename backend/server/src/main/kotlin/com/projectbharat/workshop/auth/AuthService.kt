package com.projectbharat.workshop.auth

import com.projectbharat.workshop.db.Database
import com.projectbharat.workshop.security.AccessTokenClaims
import com.projectbharat.workshop.security.AccessTokenService
import com.projectbharat.workshop.security.PasswordHasher
import com.projectbharat.workshop.security.JwtSettings
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.sql.DataSource

data class BootstrapInput(
    val bootstrapSecret: String,
    val organizationName: String,
    val legalName: String,
    val branchName: String,
    val ownerName: String,
    val email: String,
    val phone: String?,
    val password: String,
    val deviceLabel: String?
)

data class LoginInput(val email: String, val password: String, val deviceLabel: String?)
data class TokenPair(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshExpiresIn: Long,
    val user: AuthUser
)
data class AuthUser(
    val id: String,
    val name: String,
    val email: String?,
    val phone: String?,
    val organizationId: String,
    val organizationName: String,
    val membershipId: String,
    val role: String,
    val permissions: List<String>
)
data class AuthFailure(val status: Int, override val message: String) : RuntimeException(message)

class AuthService(
    private val configuredDataSource: DataSource? = null,
    private val bootstrapSecret: String = System.getenv("BOOTSTRAP_SECRET") ?: ""
) {
    private val random = SecureRandom()
    private val jwtSettings by lazy { JwtSettings.fromEnvironment() }
    private val jwt by lazy { AccessTokenService(jwtSettings) }
    private fun database(): DataSource = configuredDataSource ?: Database.dataSource()
    private val refreshLifetimeSeconds = 60L * 60L * 24L * 30L

    fun bootstrap(input: BootstrapInput): TokenPair {
        if (bootstrapSecret.isBlank() || !constantTimeEquals(input.bootstrapSecret, bootstrapSecret)) {
            throw AuthFailure(403, "Owner bootstrap is not authorized")
        }
        validateIdentity(input.ownerName, input.email, input.phone, input.password)
        if (input.organizationName.isBlank() || input.legalName.isBlank() || input.branchName.isBlank()) {
            throw AuthFailure(400, "Organization and branch names are required")
        }
        val passwordHash = PasswordHasher.hash(input.password)
        return transaction { connection ->
            connection.prepareStatement("SELECT pg_advisory_xact_lock(?)").use {
                it.setLong(1, 8_274_110_221L)
                it.execute()
            }
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT EXISTS(SELECT 1 FROM organizations)").use { rs ->
                    rs.next()
                    if (rs.getBoolean(1)) throw AuthFailure(409, "Owner setup has already been completed")
                }
            }

            val organizationId = connection.insertId(
                "INSERT INTO organizations(legal_name, display_name) VALUES (?, ?) RETURNING id"
            ) { setString(1, input.legalName.trim()); setString(2, input.organizationName.trim()) }
            val branchId = connection.insertId(
                "INSERT INTO branches(organization_id, name, code) VALUES (?, ?, 'MAIN') RETURNING id"
            ) { setObject(1, organizationId); setString(2, input.branchName.trim()) }
            val userId = connection.insertId(
                """INSERT INTO users(email, phone, full_name, password_hash)
                   VALUES (lower(?), ?, ?, ?) RETURNING id"""
            ) {
                setString(1, input.email.trim())
                setNullableString(2, input.phone)
                setString(3, input.ownerName.trim())
                setString(4, passwordHash)
            }
            val membershipId = connection.insertId(
                """INSERT INTO organization_memberships(organization_id, user_id, default_branch_id)
                   VALUES (?, ?, ?) RETURNING id"""
            ) {
                setObject(1, organizationId); setObject(2, userId); setObject(3, branchId)
            }
            val roleId = connection.prepareStatement(
                "SELECT id FROM roles WHERE organization_id IS NULL AND role_key='OWNER_ADMIN'"
            ).use { statement ->
                statement.executeQuery().use { rs ->
                    if (!rs.next()) throw IllegalStateException("OWNER_ADMIN role template is missing")
                    rs.getObject(1, UUID::class.java)
                }
            }
            connection.prepareStatement("INSERT INTO membership_roles(membership_id, role_id) VALUES (?, ?)").use {
                it.setObject(1, membershipId); it.setObject(2, roleId); it.executeUpdate()
            }
            connection.prepareStatement(
                "INSERT INTO membership_branches(membership_id, organization_id, branch_id) VALUES (?, ?, ?)"
            ).use {
                it.setObject(1, membershipId); it.setObject(2, organizationId); it.setObject(3, branchId); it.executeUpdate()
            }
            connection.audit(organizationId, userId, "auth.owner_bootstrap", "organization", organizationId)
            val user = AuthUser(userId.toString(), input.ownerName.trim(), input.email.trim(), input.phone,
                organizationId.toString(), input.organizationName.trim(), membershipId.toString(),
                "OWNER_ADMIN", permissions(connection, roleId))
            issuePair(connection, user, input.deviceLabel)
        }
    }

    fun login(input: LoginInput): TokenPair {
        if (input.email.isBlank() || input.password.isBlank()) throw AuthFailure(400, "Email and password are required")
        return transaction { connection ->
            val row = connection.prepareStatement(
                """SELECT u.id, u.full_name, u.email, u.phone, u.password_hash, u.is_active, u.is_locked,
                          o.id AS org_id, o.display_name AS org_name, m.id AS membership_id, r.id AS role_id,
                          r.role_key
                   FROM users u
                   JOIN organization_memberships m ON m.user_id=u.id AND m.membership_status='ACTIVE'
                   JOIN organizations o ON o.id=m.organization_id AND o.is_active
                   JOIN membership_roles mr ON mr.membership_id=m.id
                   JOIN roles r ON r.id=mr.role_id
                   WHERE lower(u.email)=lower(?) AND u.is_active AND NOT u.is_locked
                   ORDER BY (r.role_key='OWNER_ADMIN') DESC, m.created_at LIMIT 1"""
            ).use { statement ->
                statement.setString(1, input.email.trim())
                statement.executeQuery().use { rs -> if (rs.next()) rs.toAuthRow() else null }
            } ?: throw AuthFailure(401, "Email or password is incorrect")
            if (!PasswordHasher.verify(input.password, row.passwordHash)) throw AuthFailure(401, "Email or password is incorrect")
            connection.prepareStatement("UPDATE users SET last_login_at=now() WHERE id=?").use {
                it.setObject(1, UUID.fromString(row.user.id)); it.executeUpdate()
            }
            connection.audit(UUID.fromString(row.user.organizationId), UUID.fromString(row.user.id),
                "auth.login", "user", UUID.fromString(row.user.id))
            issuePair(connection, row.user.copy(permissions = permissions(connection, row.roleId)), input.deviceLabel)
        }
    }

    fun refresh(rawToken: String, deviceLabel: String?): TokenPair {
        if (rawToken.length < 40) throw AuthFailure(401, "Refresh token is invalid")
        val tokenHash = sha256(rawToken)
        return transaction { connection ->
            val session = connection.prepareStatement(
                """SELECT id, user_id, organization_id, expires_at, revoked_at
                   FROM refresh_sessions WHERE token_hash=? FOR UPDATE"""
            ).use { statement ->
                statement.setString(1, tokenHash)
                statement.executeQuery().use { rs ->
                    if (rs.next()) RefreshSession(
                        rs.getObject("id", UUID::class.java), rs.getObject("user_id", UUID::class.java),
                        rs.getObject("organization_id", UUID::class.java),
                        rs.getTimestamp("expires_at").toInstant(), rs.getTimestamp("revoked_at")?.toInstant()
                    ) else null
                }
            } ?: throw AuthFailure(401, "Refresh token is invalid or expired")

            if (session.revokedAt != null) {
                connection.prepareStatement(
                    "UPDATE refresh_sessions SET revoked_at=COALESCE(revoked_at, now()) WHERE user_id=? AND revoked_at IS NULL"
                ).use { it.setObject(1, session.userId); it.executeUpdate() }
                throw AuthFailure(401, "Refresh token reuse detected; sign in again")
            }
            if (!session.expiresAt.isAfter(Instant.now())) throw AuthFailure(401, "Refresh token has expired")
            val row = connection.loadAuthRow(session.userId, session.organizationId)
                ?: throw AuthFailure(401, "Account access is no longer active")
            val pair = issuePair(connection, row.user.copy(permissions = permissions(connection, row.roleId)), deviceLabel)
            val replacementId = connection.prepareStatement(
                "SELECT id FROM refresh_sessions WHERE token_hash=?"
            ).use { it.setString(1, sha256(pair.refreshToken)); it.executeQuery().use { rs ->
                if (!rs.next()) error("New refresh session was not stored")
                rs.getObject(1, UUID::class.java)
            } }
            connection.prepareStatement(
                "UPDATE refresh_sessions SET revoked_at=now(), last_used_at=now(), replaced_by_session_id=? WHERE id=?"
            ).use {
                it.setObject(1, replacementId); it.setObject(2, session.id); it.executeUpdate()
            }
            pair
        }
    }

    fun logout(rawToken: String?) {
        if (rawToken.isNullOrBlank()) return
        database().connection.use { connection ->
            connection.prepareStatement(
                "UPDATE refresh_sessions SET revoked_at=COALESCE(revoked_at, now()) WHERE token_hash=?"
            ).use { it.setString(1, sha256(rawToken)); it.executeUpdate() }
        }
    }

    fun authenticate(accessToken: String): AuthUser {
        val decoded = runCatching { jwt.verify(accessToken) }.getOrElse {
            throw AuthFailure(401, "Access token is invalid or expired")
        }
        val userId = runCatching { UUID.fromString(decoded.subject) }.getOrElse {
            throw AuthFailure(401, "Access token is invalid")
        }
        val organizationId = runCatching { UUID.fromString(decoded.getClaim("organization_id").asString()) }.getOrElse {
            throw AuthFailure(401, "Access token is invalid")
        }
        return dataSource.connection.use { connection ->
            val row = connection.loadAuthRow(userId, organizationId)
                ?: throw AuthFailure(401, "Account access is no longer active")
            row.user.copy(permissions = permissions(connection, row.roleId))
        }
    }

    private fun issuePair(connection: Connection, user: AuthUser, deviceLabel: String?): TokenPair {
        val refresh = newRefreshToken()
        connection.prepareStatement(
            """INSERT INTO refresh_sessions(user_id, organization_id, token_hash, device_label, expires_at)
               VALUES (?, ?, ?, ?, ?)"""
        ).use {
            it.setObject(1, UUID.fromString(user.id))
            it.setObject(2, UUID.fromString(user.organizationId))
            it.setString(3, sha256(refresh))
            it.setNullableString(4, deviceLabel?.take(120))
            it.setTimestamp(5, java.sql.Timestamp.from(Instant.now().plusSeconds(refreshLifetimeSeconds)))
            it.executeUpdate()
        }
        val access = jwt.issue(AccessTokenClaims(user.id, user.organizationId, user.membershipId, user.permissions))
        return TokenPair(access, refresh, expiresIn = jwtSettings.expiresInSeconds,
            refreshExpiresIn = refreshLifetimeSeconds, user = user)
    }

    private fun permissions(connection: Connection, roleId: UUID): List<String> =
        connection.prepareStatement(
            "SELECT permission_key FROM role_permissions WHERE role_id=? ORDER BY permission_key"
        ).use { statement ->
            statement.setObject(1, roleId)
            statement.executeQuery().use { rs -> buildList { while (rs.next()) add(rs.getString(1)) } }
        }

    private fun Connection.loadAuthRow(userId: UUID, organizationId: UUID): AuthRow? =
        prepareStatement(
            """SELECT u.id, u.full_name, u.email, u.phone, u.password_hash, u.is_active, u.is_locked,
                      o.id AS org_id, o.display_name AS org_name, m.id AS membership_id, r.id AS role_id,
                      r.role_key
               FROM users u
               JOIN organization_memberships m ON m.user_id=u.id AND m.organization_id=? AND m.membership_status='ACTIVE'
               JOIN organizations o ON o.id=m.organization_id AND o.is_active
               JOIN membership_roles mr ON mr.membership_id=m.id
               JOIN roles r ON r.id=mr.role_id
               WHERE u.id=? AND u.is_active AND NOT u.is_locked
               ORDER BY (r.role_key='OWNER_ADMIN') DESC LIMIT 1"""
        ).use { statement ->
            statement.setObject(1, organizationId); statement.setObject(2, userId)
            statement.executeQuery().use { rs -> if (rs.next()) rs.toAuthRow() else null }
        }

    private fun ResultSet.toAuthRow(): AuthRow {
        val user = AuthUser(
            getObject("id", UUID::class.java).toString(), getString("full_name"), getString("email"),
            getString("phone"), getObject("org_id", UUID::class.java).toString(),
            getString("org_name"), getObject("membership_id", UUID::class.java).toString(),
            getString("role_key"), emptyList()
        )
        return AuthRow(user, getObject("role_id", UUID::class.java), getString("password_hash") ?: "")
    }

    private fun transaction(block: (Connection) -> TokenPair): TokenPair =
        dataSource.connection.use { connection ->
            connection.autoCommit = false
            try {
                val result = block(connection)
                connection.commit()
                result
            } catch (failure: AuthFailure) {
                // Persist refresh-token replay revocation before returning the authentication error.
                connection.commit()
                throw failure
            } catch (error: Throwable) {
                connection.rollback()
                throw error
            } finally {
                connection.autoCommit = true
            }
        }

    private fun Connection.audit(org: UUID, actor: UUID, action: String, entity: String, entityId: UUID) {
        prepareStatement(
            "INSERT INTO audit_logs(organization_id, actor_user_id, action_key, entity_type, entity_id) VALUES (?, ?, ?, ?, ?)"
        ).use {
            it.setObject(1, org); it.setObject(2, actor); it.setString(3, action)
            it.setString(4, entity); it.setObject(5, entityId); it.executeUpdate()
        }
    }

    private fun Connection.insertId(sql: String, bind: java.sql.PreparedStatement.() -> Unit): UUID =
        prepareStatement(sql).use { statement ->
            statement.bind()
            statement.executeQuery().use { rs -> rs.next(); rs.getObject(1, UUID::class.java) }
        }

    private fun java.sql.PreparedStatement.setNullableString(index: Int, value: String?) {
        if (value.isNullOrBlank()) setNull(index, java.sql.Types.VARCHAR) else setString(index, value.trim())
    }

    private fun validateIdentity(name: String, email: String, phone: String?, password: String) {
        if (name.isBlank() || email.isBlank() || !email.contains("@")) throw AuthFailure(400, "Owner name and valid email are required")
        if (phone.isNullOrBlank() && email.isBlank()) throw AuthFailure(400, "Email or phone is required")
        if (password.length !in 12..72) throw AuthFailure(400, "Password must contain 12 to 72 characters")
    }

    private fun newRefreshToken(): String {
        val bytes = ByteArray(48)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    private fun constantTimeEquals(left: String, right: String): Boolean =
        MessageDigest.isEqual(left.toByteArray(StandardCharsets.UTF_8), right.toByteArray(StandardCharsets.UTF_8))

    private data class AuthRow(val user: AuthUser, val roleId: UUID, val passwordHash: String)
    private data class RefreshSession(
        val id: UUID, val userId: UUID, val organizationId: UUID,
        val expiresAt: Instant, val revokedAt: Instant?
    )
}
