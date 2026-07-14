package coop.macambira.leitedia

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime

data class UserProfile(
    val id: String,
    val cooperativeId: String,
    val loginId: String,
    val fullName: String,
    val role: String,
    val active: Boolean
)

data class MilkEntryInput(
    val supplier: String,
    val liters: Double,
    val shift: String,
    val temperature: Double?,
    val fatPercentage: Double?,
    val notes: String
)

data class MilkEntry(
    val id: Long,
    val entryDate: String,
    val supplier: String,
    val shift: String,
    val liters: Double,
    val temperature: Double?,
    val fatPercentage: Double?,
    val notes: String
)

class SupabaseClient {
    private val baseUrl = "https://lqrddudfczmmlqqkgnly.supabase.co"
    private val apiKey = "sb_publishable_VD6P1JIYMzr9fl-z0eQGLQ_Q4IhtaCK"
    private var accessToken: String? = null

    fun login(loginId: String, password: String): UserProfile {
        val email = if ('@' in loginId) loginId.trim() else "${loginId.trim().lowercase()}@leitedia.local"
        val response = request(
            path = "/auth/v1/token?grant_type=password",
            method = "POST",
            body = JSONObject().put("email", email).put("password", password),
            authenticated = false
        )
        accessToken = response.getString("access_token")
        val userId = response.getJSONObject("user").getString("id")
        return loadProfile(userId)
    }

    private fun loadProfile(userId: String): UserProfile {
        val result = requestArray("/rest/v1/profiles?id=eq.$userId&select=id,cooperative_id,login_id,full_name,role,active")
        if (result.length() == 0) error("Usuário sem perfil na cooperativa")
        val item = result.getJSONObject(0)
        return UserProfile(
            id = item.getString("id"),
            cooperativeId = item.getString("cooperative_id"),
            loginId = item.getString("login_id"),
            fullName = item.getString("full_name"),
            role = item.getString("role"),
            active = item.optBoolean("active", true)
        )
            .also { if (!it.active) { accessToken = null; error("Usuário desativado. Procure a administração.") } }
    }

    fun saveMilkEntry(profile: UserProfile, input: MilkEntryInput) {
        val body = JSONObject()
            .put("cooperative_id", profile.cooperativeId)
            .put("user_id", profile.id)
            .put("entry_date", LocalDate.now().toString())
            .put("entry_time", LocalTime.now().withNano(0).toString())
            .put("supplier", input.supplier)
            .put("shift", input.shift)
            .put("liters", input.liters)
            .put("notes", input.notes.ifBlank { JSONObject.NULL })
        body.put("temperature", input.temperature ?: JSONObject.NULL)
        body.put("fat_percentage", input.fatPercentage ?: JSONObject.NULL)
        request("/rest/v1/milk_entries", "POST", body)
    }

    fun listUsers(): List<UserProfile> {
        val result = requestArray("/rest/v1/profiles?select=id,cooperative_id,login_id,full_name,role,active&order=full_name.asc")
        return (0 until result.length()).map { index ->
            val item = result.getJSONObject(index)
            UserProfile(
                id = item.getString("id"),
                cooperativeId = item.getString("cooperative_id"),
                loginId = item.getString("login_id"),
                fullName = item.getString("full_name"),
                role = item.getString("role"),
                active = item.optBoolean("active", true)
            )
        }
    }

    fun listEntries(userId: String): List<MilkEntry> {
        val result = requestArray(
            "/rest/v1/milk_entries?user_id=eq.$userId&select=id,entry_date,supplier,shift,liters,temperature,fat_percentage,notes&order=entry_date.desc"
        )
        return (0 until result.length()).map { index ->
            val item = result.getJSONObject(index)
            MilkEntry(
                id = item.getLong("id"),
                entryDate = item.getString("entry_date"),
                supplier = item.getString("supplier"),
                shift = item.getString("shift"),
                liters = item.getDouble("liters"),
                temperature = if (item.isNull("temperature")) null else item.getDouble("temperature"),
                fatPercentage = if (item.isNull("fat_percentage")) null else item.getDouble("fat_percentage"),
                notes = if (item.isNull("notes")) "" else item.getString("notes")
            )
        }
    }

    fun createUser(loginId: String, fullName: String, password: String) {
        request(
            path = "/functions/v1/create-cooperative-user",
            method = "POST",
            body = JSONObject()
                .put("login_id", loginId.trim().uppercase())
                .put("full_name", fullName.trim())
                .put("password", password)
                .put("action", "create")
        )
    }

    fun updateUser(userId: String, loginId: String, fullName: String, active: Boolean, newPassword: String?) {
        val body = JSONObject()
            .put("action", "update")
            .put("user_id", userId)
            .put("login_id", loginId.trim().uppercase())
            .put("full_name", fullName.trim())
            .put("active", active)
        if (!newPassword.isNullOrBlank()) body.put("password", newPassword)
        request("/functions/v1/create-cooperative-user", "POST", body)
    }

    fun logout() {
        accessToken = null
    }

    private fun requestArray(path: String): JSONArray {
        val connection = open(path, "GET", true)
        return JSONArray(readResponse(connection))
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        authenticated: Boolean = true
    ): JSONObject {
        val connection = open(path, method, authenticated)
        if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val text = readResponse(connection)
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun open(path: String, method: String, authenticated: Boolean): HttpURLConnection {
        return (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (authenticated) {
                val token = accessToken ?: error("Sessão expirada")
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (method != "GET") doOutput = true
        }
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (code !in 200..299) {
            val message = runCatching {
                val json = JSONObject(text)
                json.optString("msg").ifBlank { json.optString("message") }.ifBlank { json.optString("error_description") }
            }.getOrDefault("")
            error(message.ifBlank { "Falha de comunicação ($code)" })
        }
        return text
    }
}
