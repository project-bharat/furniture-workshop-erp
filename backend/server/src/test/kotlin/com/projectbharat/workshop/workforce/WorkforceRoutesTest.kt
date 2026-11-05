package com.projectbharat.workshop.workforce

import com.projectbharat.workshop.module
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkforceRoutesTest {
    @Test
    fun workforceAndPayrollEndpointsRequireAuthentication() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/employees").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/attendance").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/payroll").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/payroll/adjustments").status)
    }
}
