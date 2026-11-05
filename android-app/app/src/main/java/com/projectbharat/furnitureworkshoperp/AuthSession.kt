package com.projectbharat.furnitureworkshoperp

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class SignedInUser(
    val id: String,
    val name: String,
    val email: String?,
    val organizationId: String,
    val organizationName: String,
    val role: String,
    val permissions: Set<String>
)

data class AuthTokens(val accessToken: String, val refreshToken: String, val expiresIn: Long)

class SecureSession(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "workshop_secure_session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    fun save(tokens: AuthTokens, user: SignedInUser) {
        prefs.edit().putString("access", tokens.accessToken).putString("refresh", tokens.refreshToken)
            .putLong("expires", tokens.expiresIn).putString("user_id", user.id)
            .putString("user_name", user.name).putString("user_email", user.email)
            .putString("org_id", user.organizationId).putString("org_name", user.organizationName)
            .putString("role", user.role).putString("permissions", user.permissions.joinToString(",")).apply()
    }
    fun accessToken() = prefs.getString("access", null)
    fun refreshToken() = prefs.getString("refresh", null)
    fun user(): SignedInUser? {
        val id = prefs.getString("user_id", null) ?: return null
        return SignedInUser(id, prefs.getString("user_name", "") ?: "",
            prefs.getString("user_email", null), prefs.getString("org_id", "") ?: "",
            prefs.getString("org_name", "") ?: "", prefs.getString("role", "") ?: "",
            prefs.getString("permissions", "")?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet())
    }
    fun clear() = prefs.edit().clear().apply()
}

class WorkshopApi(private val baseUrl: String, private val session: SecureSession) {
    private fun call(path: String, method: String, body: JSONObject? = null, token: String? = null): JSONObject {
        val connection = (URL(baseUrl.trimEnd('/') + "/api/v1" + path).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/json")
        if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        connection.disconnect()
        if (code !in 200..299) throw ApiException(code, runCatching { JSONObject(text).optString("error").ifBlank { JSONObject(text).optString("message") } }.getOrDefault("Request failed ($code)"))
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    suspend fun login(base: String, email: String, password: String, device: String, store: SecureSession): SignedInUser =
        withContext(Dispatchers.IO) {
            val response = WorkshopApi(base, store).call("/auth/login", "POST",
                JSONObject().put("email", email.trim()).put("password", password).put("deviceLabel", device))
            val userJson = response.getJSONObject("user")
            val permissions = userJson.optJSONArray("permissions")
            val user = SignedInUser(userJson.getString("id"), userJson.getString("name"),
                userJson.optString("email").takeIf { it != "null" }, userJson.getString("organizationId"),
                userJson.getString("organizationName"), userJson.getString("role"),
                (0 until (permissions?.length() ?: 0)).map { permissions!!.getString(it) }.toSet())
            store.save(AuthTokens(response.getString("accessToken"), response.getString("refreshToken"),
                response.optLong("expiresIn", 900)), user)
            user
        }


    suspend fun bootstrap(
        base: String,
        bootstrapSecret: String,
        organizationName: String,
        legalName: String,
        branchName: String,
        ownerName: String,
        email: String,
        phone: String,
        password: String,
        device: String,
        store: SecureSession
    ): SignedInUser = withContext(Dispatchers.IO) {
        val response = WorkshopApi(base, store).call("/auth/bootstrap", "POST",
            JSONObject()
                .put("bootstrapSecret", bootstrapSecret)
                .put("organizationName", organizationName.trim())
                .put("legalName", legalName.trim())
                .put("branchName", branchName.trim().ifBlank { "Main Workshop" })
                .put("ownerName", ownerName.trim())
                .put("email", email.trim())
                .put("phone", phone.trim().ifBlank { JSONObject.NULL })
                .put("password", password)
                .put("deviceLabel", device))
        val userJson = response.getJSONObject("user")
        val permissions = userJson.optJSONArray("permissions")
        val user = SignedInUser(
            userJson.getString("id"), userJson.getString("name"),
            userJson.optString("email").takeIf { it != "null" },
            userJson.getString("organizationId"), userJson.getString("organizationName"),
            userJson.getString("role"),
            (0 until (permissions?.length() ?: 0)).map { permissions!!.getString(it) }.toSet()
        )
        store.save(AuthTokens(response.getString("accessToken"), response.getString("refreshToken"),
            response.optLong("expiresIn", 900)), user)
        user
    }

    suspend fun authenticated(path: String, method: String = "GET", body: JSONObject? = null): JSONObject =
        withContext(Dispatchers.IO) {
            val access = session.accessToken() ?: throw ApiException(401, "Sign in required")
            try { call(path, method, body, access) }
            catch (e: ApiException) {
                if (e.status != 401) throw e
                val refresh = session.refreshToken() ?: run { session.clear(); throw e }
                try {
                    val pair = call("/auth/refresh", "POST", JSONObject().put("refreshToken", refresh))
                    val userJson = pair.getJSONObject("user")
                    val old = session.user()
                    val arr = userJson.optJSONArray("permissions")
                    val user = SignedInUser(userJson.getString("id"), userJson.getString("name"),
                        userJson.optString("email").takeIf { it != "null" }, userJson.getString("organizationId"),
                        userJson.getString("organizationName"), userJson.getString("role"),
                        (0 until (arr?.length() ?: 0)).map { arr!!.getString(it) }.toSet())
                    session.save(AuthTokens(pair.getString("accessToken"), pair.getString("refreshToken"),
                        pair.optLong("expiresIn", 900)), user)
                    call(path, method, body, session.accessToken())
                } catch (refreshError: Exception) { session.clear(); throw refreshError }
            }
        }

    suspend fun logout() = withContext(Dispatchers.IO) {
        val refresh = session.refreshToken()
        if (refresh != null) runCatching { call("/auth/logout", "POST", JSONObject().put("refreshToken", refresh)) }
        session.clear()
    }
}

class ApiException(val status: Int, message: String) : Exception(message)
