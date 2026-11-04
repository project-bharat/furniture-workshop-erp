package com.projectbharat.workshop.auth

import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

@Serializable
data class BootstrapRequest(
    val bootstrapSecret: String,
    val organizationName: String,
    val legalName: String,
    val branchName: String = "Main Workshop",
    val ownerName: String,
    val email: String,
    val phone: String? = null,
    val password: String,
    val deviceLabel: String? = null
)
@Serializable data class LoginRequest(val email: String, val password: String, val deviceLabel: String? = null)
@Serializable data class RefreshRequest(val refreshToken: String, val deviceLabel: String? = null)
@Serializable data class LogoutRequest(val refreshToken: String? = null)
@Serializable data class ErrorResponse(val error: String)
@Serializable data class UserResponse(
    val id: String, val name: String, val email: String?, val phone: String?,
    val organizationId: String, val organizationName: String, val membershipId: String,
    val role: String, val permissions: List<String>
)
@Serializable data class TokenResponse(
    val accessToken: String, val refreshToken: String, val tokenType: String,
    val expiresIn: Long, val refreshExpiresIn: Long, val user: UserResponse
)

private fun AuthUser.toResponse() = UserResponse(
    id, name, email, phone, organizationId, organizationName, membershipId, role, permissions
)
private fun TokenPair.toResponse() = TokenResponse(
    accessToken, refreshToken, tokenType, expiresIn, refreshExpiresIn, user.toResponse()
)

fun Route.authRoutes(service: AuthService) {
    route("/auth") {
        post("/bootstrap") {
            try {
                val request = call.receive<BootstrapRequest>()
                val result = service.bootstrap(BootstrapInput(
                    request.bootstrapSecret, request.organizationName, request.legalName,
                    request.branchName, request.ownerName, request.email, request.phone,
                    request.password, request.deviceLabel
                ))
                call.respond(HttpStatusCode.Created, result.toResponse())
            } catch (failure: AuthFailure) {
                call.respond(HttpStatusCode.fromValue(failure.status), ErrorResponse(failure.message))
            } catch (error: IllegalArgumentException) {
                call.respond(HttpStatusCode.BadRequest, ErrorResponse(error.message ?: "Invalid request"))
            }
        }
        post("/login") {
            try {
                val request = call.receive<LoginRequest>()
                call.respond(service.login(LoginInput(request.email, request.password, request.deviceLabel)).toResponse())
            } catch (failure: AuthFailure) {
                call.respond(HttpStatusCode.fromValue(failure.status), ErrorResponse(failure.message))
            }
        }
        post("/refresh") {
            try {
                val request = call.receive<RefreshRequest>()
                call.respond(service.refresh(request.refreshToken, request.deviceLabel).toResponse())
            } catch (failure: AuthFailure) {
                call.respond(HttpStatusCode.fromValue(failure.status), ErrorResponse(failure.message))
            }
        }
        post("/logout") {
            val request = call.receive<LogoutRequest>()
            service.logout(request.refreshToken)
            call.respond(HttpStatusCode.OK, mapOf("status" to "signed_out"))
        }
        get("/me") {
            try {
                val header = call.request.headers[HttpHeaders.Authorization]
                val token = header?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substringAfter(' ')
                    ?: throw AuthFailure(401, "Bearer access token is required")
                call.respond(service.authenticate(token).toResponse())
            } catch (failure: AuthFailure) {
                call.respond(HttpStatusCode.fromValue(failure.status), ErrorResponse(failure.message))
            }
        }
    }
}
