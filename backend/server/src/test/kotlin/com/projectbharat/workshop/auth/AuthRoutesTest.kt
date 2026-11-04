package com.projectbharat.workshop.auth

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import com.projectbharat.workshop.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains

class AuthRoutesTest {
    @Test
    fun bootstrapIsDisabledWithoutServerSecret() = testApplication {
        application { module() }
        val response = client.post("/api/v1/auth/bootstrap") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"bootstrapSecret":"not-configured","organizationName":"Workshop","legalName":"Workshop Pvt Ltd","ownerName":"Owner","email":"owner@example.com","password":"long-password-2026"}"""
            )
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertContains(response.bodyAsText(), "not authorized")
    }

    @Test
    fun currentUserRequiresBearerToken() = testApplication {
        application { module() }
        val response = client.get("/api/v1/auth/me")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertContains(response.bodyAsText(), "Bearer access token is required")
    }
}
