package com.bettercallsoul.spam

import java.io.IOException
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Serializable
enum class Verdict {
    @SerialName("spam") Spam,
    @SerialName("safe") Safe,
    @SerialName("unknown") Unknown,
}

@Serializable
enum class SpamCategory {
    @SerialName("telemarketing") Telemarketing,
    @SerialName("scam") Scam,
    @SerialName("survey") Survey,
    @SerialName("other") Other,
}

@Serializable
data class LookupResult(
    val verdict: Verdict,
    val spamVotes: Int,
    val safeVotes: Int,
    val category: SpamCategory? = null,
    val callerName: String? = null,
)

@Serializable
data class SpamReport(
    val number: String,
    val deviceId: String,
    val isSpam: Boolean,
    val category: SpamCategory? = null,
    val callerName: String? = null,
)

@Serializable
private data class LookupRequest(val number: String)

/**
 * Talks to the spam/caller-ID servers. [baseUrls] is the primary first, then any backups; a lookup
 * tries each in turn until one answers, so a server being down doesn't blind the app. Reports go to
 * the primary alone, since that is where this install's votes are counted.
 */
class SpamClient(baseUrls: List<String>) {
    private val baseUrls = baseUrls.filter { it.isNotBlank() }.ifEmpty { listOf("https://example.invalid") }
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val mediaType = "application/json".toMediaType()

    // A slow answer must never hold up a ringing phone.
    private val lookupHttp = OkHttpClient.Builder().callTimeout(Duration.ofMillis(2500)).build()
    private val reportHttp = OkHttpClient.Builder().callTimeout(Duration.ofSeconds(20)).build()

    /** Returns null when no server can answer in time. */
    suspend fun lookup(number: String): LookupResult? {
        val body = json.encodeToString(LookupRequest(number))
        for (baseUrl in baseUrls) {
            val answer = post(lookupHttp, baseUrl, "lookup", body)
                ?.let { runCatching { json.decodeFromString<LookupResult>(it) }.getOrNull() }
            if (answer != null) return answer
        }
        return null
    }

    suspend fun report(report: SpamReport): Boolean =
        post(reportHttp, baseUrls.first(), "report", json.encodeToString(report)) != null

    private suspend fun post(http: OkHttpClient, baseUrl: String, path: String, body: String): String? =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("${baseUrl.trimEnd('/')}/api/$path")
                .post(body.toRequestBody(mediaType))
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    if (response.isSuccessful) response.body.string() else null
                }
            } catch (_: IOException) {
                null
            }
        }
}
