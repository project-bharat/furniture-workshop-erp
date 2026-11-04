package com.projectbharat.workshop

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertContains

class HealthTest {
    @Test
    fun healthEndpointReturnsOk() = testApplication {
        application { module() }
        val response = client.get("/api/v1/health")
        assertContains(response.bodyAsText(), "\"status\":\"ok\"")
        assertContains(response.bodyAsText(), "furniture-workshop-erp-api")
    }
}
