package com.projectbharat.workshop

import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant

fun Application.module() {
    install(CallLogging)
    install(ContentNegotiation) {
        json(Json {
            ignoreUnknownKeys = false
            explicitNulls = false
            encodeDefaults = true
        })
    }

    routing {
        route("/api/v1") {
            get("/health") {
                call.respond(
                    HealthResponse(
                        status = "ok",
                        service = "furniture-workshop-erp-api",
                        timestamp = Instant.now().toString()
                    )
                )
            }
        }
    }
}

@Serializable
data class HealthResponse(
    val status: String,
    val service: String,
    val timestamp: String
)
