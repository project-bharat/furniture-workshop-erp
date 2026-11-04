package com.projectbharat.workshop.customers

import com.projectbharat.workshop.auth.AuthFailure
import com.projectbharat.workshop.auth.AuthService
import com.projectbharat.workshop.auth.AuthUser
import com.projectbharat.workshop.db.Database
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

@Serializable
data class CustomerRequest(
    val displayName: String,
    val customerType: String = "INDIVIDUAL",
    val phone: String? = null,
    val alternatePhone: String? = null,
    val email: String? = null,
    val gstin: String? = null,
    val notes: String? = null
)

@Serializable
data class CustomerResponse(
    val id: String,
    val displayName: String,
    val customerType: String,
    val phone: String?,
    val alternatePhone: String?,
    val email: String?,
    val gstin: String?,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class EnquiryRequest(
    val customerId: String,
    val title: String,
    val description: String? = null,
    val source: String = "WALK_IN",
    val priority: String = "NORMAL",
    val expectedValue: Double? = null,
    val nextFollowUpAt: String? = null
)

@Serializable
data class EnquiryStatusRequest(val status: String)

@Serializable
data class EnquiryResponse(
    val id: String,
    val enquiryNumber: String,
    val customerId: String,
    val customerName: String,
    val title: String,
    val description: String?,
    val source: String,
    val status: String,
    val priority: String,
    val expectedValue: Double?,
    val nextFollowUpAt: String?,
    val createdAt: String,
    val updatedAt: String
)

@Serializable
data class CustomerApiError(val error: String)

private suspend fun ApplicationCall.authenticatedUser(service: AuthService, permission: String): AuthUser {
    val header = request.headers[HttpHeaders.Authorization]
    val token = header?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }?.substringAfter(' ')
        ?: throw AuthFailure(401, "Bearer access token is required")
    val user = service.authenticate(token)
    if (permission !in user.permissions) throw AuthFailure(403, "Permission denied")
    return user
}

private suspend fun ApplicationCall.respondFailure(failure: AuthFailure) {
    respond(HttpStatusCode.fromValue(failure.status), CustomerApiError(failure.message))
}

fun Route.customerAndEnquiryRoutes(service: AuthService) {
    route("/customers") {
        get {
            try {
                val user = call.authenticatedUser(service, "customers.view")
                val rows = Database.dataSource().connection.use { connection ->
                    connection.prepareStatement(
                        """SELECT id, display_name, customer_type, phone, alternate_phone, email, gstin, notes,
                                  created_at, updated_at
                           FROM customers WHERE organization_id=? AND archived_at IS NULL
                           ORDER BY lower(display_name) LIMIT 200"""
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(user.organizationId))
                        statement.executeQuery().use { rs ->
                            buildList {
                                while (rs.next()) add(CustomerResponse(
                                    rs.getObject("id", UUID::class.java).toString(),
                                    rs.getString("display_name"), rs.getString("customer_type"),
                                    rs.getString("phone"), rs.getString("alternate_phone"),
                                    rs.getString("email"), rs.getString("gstin"), rs.getString("notes"),
                                    rs.getTimestamp("created_at").toInstant().toString(),
                                    rs.getTimestamp("updated_at").toInstant().toString()
                                ))
                            }
                        }
                    }
                }
                call.respond(rows)
            } catch (failure: AuthFailure) { call.respondFailure(failure) }
        }

        post {
            try {
                val user = call.authenticatedUser(service, "customers.manage")
                val input = call.receive<CustomerRequest>()
                val name = input.displayName.trim()
                val type = input.customerType.uppercase()
                if (name.isBlank() || name.length > 180) throw AuthFailure(400, "Customer name is required (maximum 180 characters)")
                if (type !in setOf("INDIVIDUAL", "BUSINESS")) throw AuthFailure(400, "Customer type must be INDIVIDUAL or BUSINESS")
                val created = Database.dataSource().connection.use { connection ->
                    connection.autoCommit = false
                    try {
                        val id = connection.prepareStatement(
                            """INSERT INTO customers(organization_id, display_name, customer_type, phone,
                                      alternate_phone, email, gstin, notes, created_by)
                               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id, created_at, updated_at"""
                        ).use { statement ->
                            statement.setObject(1, UUID.fromString(user.organizationId))
                            statement.setString(2, name); statement.setString(3, type)
                            statement.setNullable(4, input.phone); statement.setNullable(5, input.alternatePhone)
                            statement.setNullable(6, input.email); statement.setNullable(7, input.gstin)
                            statement.setNullable(8, input.notes); statement.setObject(9, UUID.fromString(user.id))
                            statement.executeQuery().use { rs ->
                                rs.next()
                                Triple(rs.getObject("id", UUID::class.java), rs.getTimestamp("created_at"), rs.getTimestamp("updated_at"))
                            }
                        }
                        connection.audit(user, "customer.created", "customer", id.first)
                        connection.commit()
                        CustomerResponse(id.first.toString(), name, type, input.phone, input.alternatePhone,
                            input.email, input.gstin, input.notes, id.second.toInstant().toString(), id.third.toInstant().toString())
                    } catch (error: Throwable) { connection.rollback(); throw error }
                }
                call.respond(HttpStatusCode.Created, created)
            } catch (failure: AuthFailure) { call.respondFailure(failure) }
        }

        get("/{id}") {
            try {
                val user = call.authenticatedUser(service, "customers.view")
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw AuthFailure(400, "Customer id is invalid")
                val customer = Database.dataSource().connection.use { connection ->
                    connection.prepareStatement(
                        """SELECT id, display_name, customer_type, phone, alternate_phone, email, gstin, notes,
                                  created_at, updated_at FROM customers
                           WHERE organization_id=? AND id=? AND archived_at IS NULL"""
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(user.organizationId)); statement.setObject(2, id)
                        statement.executeQuery().use { rs ->
                            if (!rs.next()) null else CustomerResponse(
                                rs.getObject("id", UUID::class.java).toString(), rs.getString("display_name"),
                                rs.getString("customer_type"), rs.getString("phone"), rs.getString("alternate_phone"),
                                rs.getString("email"), rs.getString("gstin"), rs.getString("notes"),
                                rs.getTimestamp("created_at").toInstant().toString(), rs.getTimestamp("updated_at").toInstant().toString()
                            )
                        }
                    }
                } ?: throw AuthFailure(404, "Customer not found")
                call.respond(customer)
            } catch (failure: AuthFailure) { call.respondFailure(failure) }
        }

        patch("/{id}") {
            try {
                val user = call.authenticatedUser(service, "customers.manage")
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw AuthFailure(400, "Customer id is invalid")
                val input = call.receive<CustomerRequest>()
                val name = input.displayName.trim()
                val type = input.customerType.uppercase()
                if (name.isBlank() || name.length > 180 || type !in setOf("INDIVIDUAL", "BUSINESS")) {
                    throw AuthFailure(400, "Valid customer name and type are required")
                }
                val count = Database.dataSource().connection.use { connection ->
                    connection.prepareStatement(
                        """UPDATE customers SET display_name=?, customer_type=?, phone=?, alternate_phone=?,
                                  email=?, gstin=?, notes=?, updated_at=now()
                           WHERE organization_id=? AND id=? AND archived_at IS NULL"""
                    ).use { statement ->
                        statement.setString(1, name); statement.setString(2, type)
                        statement.setNullable(3, input.phone); statement.setNullable(4, input.alternatePhone)
                        statement.setNullable(5, input.email); statement.setNullable(6, input.gstin)
                        statement.setNullable(7, input.notes); statement.setObject(8, UUID.fromString(user.organizationId))
                        statement.setObject(9, id); statement.executeUpdate()
                    }.also { if (it > 0) connection.audit(user, "customer.updated", "customer", id) }
                }
                if (count == 0) throw AuthFailure(404, "Customer not found")
                call.respond(mapOf("status" to "updated"))
            } catch (failure: AuthFailure) { call.respondFailure(failure) }
        }

        delete("/{id}") {
            try {
                val user = call.authenticatedUser(service, "customers.manage")
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw AuthFailure(400, "Customer id is invalid")
                val count = Database.dataSource().connection.use { connection ->
                    connection.prepareStatement(
                        """UPDATE customers SET archived_at=now(), updated_at=now()
                           WHERE organization_id=? AND id=? AND archived_at IS NULL"""
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(user.organizationId)); statement.setObject(2, id)
                        statement.executeUpdate()
                    }.also { if (it > 0) connection.audit(user, "customer.archived", "customer", id) }
                }
                if (count == 0) throw AuthFailure(404, "Customer not found")
                call.respond(HttpStatusCode.NoContent)
            } catch (failure: AuthFailure) { call.respondFailure(failure) }
        }
    }

    route("/enquiries") {
        get {
            try {
                val user = call.authenticatedUser(service, "customers.view")
                val rows = Database.dataSource().connection.use { connection ->
                    connection.prepareStatement(
                        """SELECT e.id, e.enquiry_number, e.customer_id, c.display_name AS customer_name,
                                  e.title, e.description, e.source, e.status, e.priority, e.expected_value,
                                  e.next_follow_up_at, e.created_at, e.updated_at
                           FROM enquiries e JOIN customers c ON c.id=e.customer_id AND c.organization_id=e.organization_id
                           WHERE e.organization_id=? ORDER BY e.updated_at DESC LIMIT 200"""
                    ).use { statement ->
                        statement.setObject(1, UUID.fromString(user.organizationId))
                        statement.executeQuery().use { rs ->
                            buildList {
                                while (rs.next()) add(EnquiryResponse(
                                    rs.getObject("id", UUID::class.java).toString(), rs.getString("enquiry_number"),
                                    rs.getObject("customer_id", UUID::class.java).toString(), rs.getString("customer_name"),
                                    rs.getString("title"), rs.getString("description"), rs.getString("source"),
                                    rs.getString("status"), rs.getString("priority"),
                                    rs.getBigDecimal("expected_value")?.toDouble(),
                                    rs.getTimestamp("next_follow_up_at")?.toInstant()?.toString(),
                                    rs.getTimestamp("created_at").toInstant().toString(),
                                    rs.getTimestamp("updated_at").toInstant().toString()
                                ))
                            }
                        }
                    }
                }
                call.respond(rows)
            } catch (failure: AuthFailure) { call.respondFailure(failure) }
        }

        post {
            try {
                val user = call.authenticatedUser(service, "customers.manage")
                val input = call.receive<EnquiryRequest>()
                val customerId = runCatching { UUID.fromString(input.customerId) }.getOrNull()
                    ?: throw AuthFailure(400, "Customer id is invalid")
                val title = input.title.trim()
                val source = input.source.uppercase()
                val priority = input.priority.uppercase()
                if (title.isBlank() || title.length > 180) throw AuthFailure(400, "Enquiry title is required")
                if (source !in setOf("WALK_IN", "PHONE", "WHATSAPP", "WEBSITE", "REFERRAL", "OTHER")) {
                    throw AuthFailure(400, "Enquiry source is invalid")
                }
                if (priority !in setOf("LOW", "NORMAL", "HIGH", "URGENT")) throw AuthFailure(400, "Enquiry priority is invalid")
                if (input.expectedValue != null && input.expectedValue < 0) throw AuthFailure(400, "Expected value cannot be negative")
                val followUp = input.nextFollowUpAt?.let {
                    runCatching { Timestamp.from(Instant.parse(it)) }.getOrNull()
                        ?: throw AuthFailure(400, "Follow-up date must be an ISO-8601 timestamp")
                }
                val created = Database.dataSource().connection.use { connection ->
                    connection.autoCommit = false
                    try {
                        val id = connection.prepareStatement(
                            """INSERT INTO enquiries(organization_id, customer_id, enquiry_number, source, title,
                                      description, priority, expected_value, next_follow_up_at, created_by)
                               SELECT ?, c.id, ?, ?, ?, ?, ?, ?, ?, ?
                               FROM customers c WHERE c.organization_id=? AND c.id=? AND c.archived_at IS NULL
                               RETURNING id, enquiry_number, created_at"""
                        ).use { statement ->
                            val enquiryNumber = "ENQ-" + UUID.randomUUID().toString().take(8).uppercase()
                            statement.setObject(1, UUID.fromString(user.organizationId)); statement.setString(2, enquiryNumber)
                            statement.setString(3, source); statement.setString(4, title); statement.setNullable(5, input.description)
                            statement.setString(6, priority)
                            if (input.expectedValue == null) statement.setNull(7, java.sql.Types.NUMERIC)
                            else statement.setBigDecimal(7, java.math.BigDecimal.valueOf(input.expectedValue))
                            if (followUp == null) statement.setNull(8, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
                            else statement.setTimestamp(8, followUp)
                            statement.setObject(9, UUID.fromString(user.id))
                            statement.setObject(10, UUID.fromString(user.organizationId)); statement.setObject(11, customerId)
                            statement.executeQuery().use { rs ->
                                if (!rs.next()) throw AuthFailure(404, "Active customer not found")
                                Triple(rs.getObject("id", UUID::class.java), rs.getString("enquiry_number"), rs.getTimestamp("created_at"))
                            }
                        }
                        connection.audit(user, "enquiry.created", "enquiry", id.first)
                        connection.commit()
                        mapOf("id" to id.first.toString(), "enquiryNumber" to id.second, "createdAt" to id.third.toInstant().toString())
                    } catch (error: Throwable) { connection.rollback(); throw error }
                }
                call.respond(HttpStatusCode.Created, created)
            } catch (failure: AuthFailure) { call.respondFailure(failure) }
        }

        patch("/{id}/status") {
            try {
                val user = call.authenticatedUser(service, "customers.manage")
                val id = call.parameters["id"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    ?: throw AuthFailure(400, "Enquiry id is invalid")
                val input = call.receive<EnquiryStatusRequest>()
                val status = input.status.uppercase()
                if (status !in setOf("NEW", "CONTACTED", "SITE_VISIT", "QUOTATION_PENDING", "QUOTED", "WON", "LOST", "ON_HOLD")) {
                    throw AuthFailure(400, "Enquiry status is invalid")
                }
                val count = Database.dataSource().connection.use { connection ->
                    connection.prepareStatement(
                        """UPDATE enquiries SET status=?, updated_at=now()
                           WHERE organization_id=? AND id=?"""
                    ).use { statement ->
                        statement.setString(1, status); statement.setObject(2, UUID.fromString(user.organizationId))
                        statement.setObject(3, id); statement.executeUpdate()
                    }.also { if (it > 0) connection.audit(user, "enquiry.status_changed", "enquiry", id) }
                }
                if (count == 0) throw AuthFailure(404, "Enquiry not found")
                call.respond(mapOf("status" to "updated"))
            } catch (failure: AuthFailure) { call.respondFailure(failure) }
        }
    }
}

private fun java.sql.PreparedStatement.setNullable(index: Int, value: String?) {
    if (value.isNullOrBlank()) setNull(index, java.sql.Types.VARCHAR) else setString(index, value.trim())
}

private fun Connection.audit(user: AuthUser, action: String, entity: String, entityId: UUID) {
    prepareStatement(
        "INSERT INTO audit_logs(organization_id, actor_user_id, action_key, entity_type, entity_id) VALUES (?, ?, ?, ?, ?)"
    ).use {
        it.setObject(1, UUID.fromString(user.organizationId)); it.setObject(2, UUID.fromString(user.id))
        it.setString(3, action); it.setString(4, entity); it.setObject(5, entityId); it.executeUpdate()
    }
}
