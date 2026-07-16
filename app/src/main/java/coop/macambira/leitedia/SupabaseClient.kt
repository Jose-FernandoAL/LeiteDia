package coop.macambira.leitedia

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class UserProfile(
    val id: String,
    val cooperativeId: String,
    val loginId: String,
    val fullName: String,
    val role: String,
    val active: Boolean
)

data class MilkEntryInput(
    val producerId: Long,
    val supplier: String,
    val liters: Double,
    val shift: String,
    val notes: String,
    val clientEntryId: String = UUID.randomUUID().toString(),
    val entryDate: String = LocalDate.now().toString(),
    val entryTime: String = LocalTime.now().withNano(0).toString()
)

data class Producer(
    val id: Long,
    val name: String,
    val document: String,
    val phone: String,
    val community: String,
    val active: Boolean
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

data class LicenseStatus(
    val status: String,
    val trialEndsAt: String,
    val daysRemaining: Int,
    val active: Boolean
)

class SupabaseClient {
    private val baseUrl = "https://lqrddudfczmmlqqkgnly.supabase.co"
    private val apiKey = "sb_publishable_VD6P1JIYMzr9fl-z0eQGLQ_Q4IhtaCK"
    private var accessToken: String? = null
    private var cooperativeId: String? = null
    private var currentUserId: String? = null

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
            .also {
                if (!it.active) { accessToken = null; error("Usuário desativado. Procure a administração.") }
                cooperativeId = it.cooperativeId
                currentUserId = it.id
            }
    }

    fun saveMilkEntry(profile: UserProfile, input: MilkEntryInput) {
        val body = JSONObject()
            .put("cooperative_id", profile.cooperativeId)
            .put("user_id", profile.id)
            .put("entry_date", input.entryDate)
            .put("entry_time", input.entryTime)
            .put("client_entry_id", input.clientEntryId)
            .put("producer_id", input.producerId)
            .put("supplier", input.supplier)
            .put("shift", input.shift)
            .put("liters", input.liters)
            .put("notes", input.notes.ifBlank { JSONObject.NULL })
        request("/rest/v1/milk_entries?on_conflict=user_id,client_entry_id", "POST", body, prefer = "resolution=ignore-duplicates,return=minimal")
    }

    fun hasMilkEntry(profile: UserProfile, input: MilkEntryInput): Boolean {
        val shift = java.net.URLEncoder.encode(input.shift, "UTF-8")
        val result = requestArray(
            "/rest/v1/milk_entries?select=id&user_id=eq.${profile.id}&producer_id=eq.${input.producerId}" +
                "&entry_date=eq.${input.entryDate}&shift=eq.$shift&limit=1"
        )
        return result.length() > 0
    }

    fun listProducers(activeOnly: Boolean = false): List<Producer> {
        val filter = if (activeOnly) "&active=eq.true" else ""
        val result = requestArray("/rest/v1/producers?select=id,name,document,phone,community,active$filter&order=name.asc")
        return (0 until result.length()).map { index ->
            val item = result.getJSONObject(index)
            Producer(
                id = item.getLong("id"),
                name = item.getString("name"),
                document = if (item.isNull("document")) "" else item.getString("document"),
                phone = if (item.isNull("phone")) "" else item.getString("phone"),
                community = if (item.isNull("community")) "" else item.getString("community"),
                active = item.optBoolean("active", true)
            )
        }
    }

    fun createProducer(name: String, document: String, phone: String, community: String) {
        val cooperative = cooperativeId ?: error("Sessão expirada")
        val userId = currentUserId ?: error("Sessão expirada")
        request("/rest/v1/producers", "POST", JSONObject()
            .put("cooperative_id", cooperative)
            .put("user_id", userId)
            .put("name", name.trim())
            .put("document", document.trim().ifBlank { JSONObject.NULL })
            .put("phone", phone.trim().ifBlank { JSONObject.NULL })
            .put("community", community.trim().ifBlank { JSONObject.NULL }))
    }

    fun updateProducer(id: Long, name: String, document: String, phone: String, community: String, active: Boolean) {
        request("/rest/v1/producers?id=eq.$id", "PATCH", JSONObject()
            .put("name", name.trim())
            .put("document", document.trim().ifBlank { JSONObject.NULL })
            .put("phone", phone.trim().ifBlank { JSONObject.NULL })
            .put("community", community.trim().ifBlank { JSONObject.NULL })
            .put("active", active))
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
        return parseEntries(result)
    }

    fun listCooperativeEntries(): List<MilkEntry> {
        val result = requestArray(
            "/rest/v1/milk_entries?select=id,entry_date,supplier,shift,liters,temperature,fat_percentage,notes&order=entry_date.desc&limit=5000"
        )
        return parseEntries(result)
    }

    private fun parseEntries(result: JSONArray): List<MilkEntry> {
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

    fun updateMilkEntry(entryId: Long, liters: Double, shift: String, notes: String) {
        request("/rest/v1/milk_entries?id=eq.$entryId", "PATCH", JSONObject()
            .put("liters", liters)
            .put("shift", shift)
            .put("notes", notes.trim().ifBlank { JSONObject.NULL }))
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

    fun getLicenseStatus(): LicenseStatus {
        val result = request(
            path = "/functions/v1/create-cooperative-user",
            method = "POST",
            body = JSONObject().put("action", "license-status")
        )
        return LicenseStatus(
            status = result.optString("status", "trial"),
            trialEndsAt = result.optString("trial_ends_at"),
            daysRemaining = result.optInt("days_remaining", 0),
            active = result.optBoolean("active", false)
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

    fun changeOwnPassword(newPassword: String) {
        request("/auth/v1/user", "PUT", JSONObject().put("password", newPassword))
    }

    fun logout() {
        accessToken = null
        cooperativeId = null
        currentUserId = null
    }

    private fun requestArray(path: String): JSONArray {
        val connection = open(path, "GET", true)
        return JSONArray(readResponse(connection))
    }

    private fun request(
        path: String,
        method: String,
        body: JSONObject? = null,
        authenticated: Boolean = true,
        prefer: String? = null
    ): JSONObject {
        val connection = open(path, method, authenticated, prefer)
        if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val text = readResponse(connection)
        return if (text.isBlank()) JSONObject() else JSONObject(text)
    }

    private fun open(path: String, method: String, authenticated: Boolean, prefer: String? = null): HttpURLConnection {
        return (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("apikey", apiKey)
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (prefer != null) setRequestProperty("Prefer", prefer)
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
