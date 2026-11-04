package com.projectbharat.workshop.customers

import com.projectbharat.workshop.module
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomerRoutesTest {
    @Test
    fun customerAndEnquiryListsRequireAuthentication() = testApplication {
        application { module() }
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/customers").status)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/v1/enquiries").status)
    }
}
