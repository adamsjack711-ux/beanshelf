package dev.adamsjack.beanshelf.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import dev.adamsjack.beanshelf.model.Bean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Client for the Beanshelf social server (server/main.py). Plain
 * HttpURLConnection + org.json — no networking deps. All calls suspend on IO.
 * Sign-in state (server URL, token, username) lives in SharedPreferences.
 */
object SocialClient {

    data class Account(val serverUrl: String, val token: String, val username: String, val display: String)
    data class FeedPost(
        val id: String,
        val username: String,
        val display: String,
        val name: String,
        val roaster: String,
        val origin: String,
        val variety: String,
        val process: String,
        val notes: String,
        val rating: Float,
        val photoUrl: String?, // absolute
        val createdAt: Long,
    )
    data class UserHit(val username: String, val display: String, val following: Boolean)

    class SocialException(message: String) : Exception(message)

    private const val PREFS = "social"

    fun account(context: Context): Account? {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val url = p.getString("serverUrl", null) ?: return null
        val token = p.getString("token", null) ?: return null
        val username = p.getString("username", null) ?: return null
        return Account(url, token, username, p.getString("display", username) ?: username)
    }

    fun signOut(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }

    fun lastServerUrl(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString("lastServerUrl", "") ?: ""

    private fun saveAccount(context: Context, a: Account) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("serverUrl", a.serverUrl)
            .putString("lastServerUrl", a.serverUrl)
            .putString("token", a.token)
            .putString("username", a.username)
            .putString("display", a.display)
            .apply()
    }

    // ── HTTP plumbing ─────────────────────────────────────────────────────
    private suspend fun request(
        base: String,
        path: String,
        method: String,
        token: String?,
        body: JSONObject?,
    ): String = withContext(Dispatchers.IO) {
        val conn = URL(base.trimEnd('/') + path).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 6000
            conn.readTimeout = 15000
            token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
            if (code !in 200..299) {
                val detail = runCatching { JSONObject(text).optString("detail") }.getOrNull()
                throw SocialException(detail?.ifBlank { null } ?: "Server error ($code)")
            }
            text
        } finally {
            conn.disconnect()
        }
    }

    // ── auth ──────────────────────────────────────────────────────────────
    suspend fun register(context: Context, serverUrl: String, username: String, display: String, password: String): Account =
        authCall(context, serverUrl, "/auth/register", username, display, password)

    suspend fun login(context: Context, serverUrl: String, username: String, password: String): Account =
        authCall(context, serverUrl, "/auth/login", username, "", password)

    private suspend fun authCall(
        context: Context, serverUrl: String, path: String,
        username: String, display: String, password: String,
    ): Account {
        val res = JSONObject(
            request(serverUrl, path, "POST", null, JSONObject().apply {
                put("username", username.trim().lowercase())
                put("password", password)
                put("display", display.trim())
            })
        )
        val account = Account(
            serverUrl = serverUrl.trimEnd('/'),
            token = res.getString("token"),
            username = res.getString("username"),
            display = res.optString("display", username),
        )
        saveAccount(context, account)
        return account
    }

    // ── social ────────────────────────────────────────────────────────────
    suspend fun search(a: Account, query: String): List<UserHit> {
        val arr = JSONArray(request(a.serverUrl, "/users/search?q=" + java.net.URLEncoder.encode(query, "UTF-8"), "GET", a.token, null))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            UserHit(o.getString("username"), o.optString("display"), o.optBoolean("following"))
        }
    }

    suspend fun setFollowing(a: Account, username: String, follow: Boolean) {
        request(a.serverUrl, "/follow/$username", if (follow) "POST" else "DELETE", a.token, JSONObject().takeIf { follow })
    }

    suspend fun feed(a: Account): List<FeedPost> = parsePosts(a, request(a.serverUrl, "/feed", "GET", a.token, null))

    suspend fun userLeaderboard(a: Account, username: String): List<FeedPost> =
        parsePosts(a, request(a.serverUrl, "/users/$username/leaderboard", "GET", a.token, null))

    private fun parsePosts(a: Account, text: String): List<FeedPost> {
        val arr = JSONArray(text)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            FeedPost(
                id = o.getString("id"),
                username = o.getString("username"),
                display = o.optString("display"),
                name = o.optString("name"),
                roaster = o.optString("roaster"),
                origin = o.optString("origin"),
                variety = o.optString("variety"),
                process = o.optString("process"),
                notes = o.optString("notes"),
                rating = o.optDouble("rating", 0.0).toFloat(),
                photoUrl = if (o.isNull("photoUrl")) null else a.serverUrl + o.getString("photoUrl"),
                createdAt = o.optLong("createdAt"),
            )
        }
    }

    /** Posts a bean check-in with its photo downscaled to ~800px JPEG. */
    suspend fun postBean(a: Account, bean: Bean): Unit = withContext(Dispatchers.IO) {
        val photoB64 = bean.photoPath?.let { path ->
            BitmapFactory.decodeFile(path)?.let { bmp ->
                val scale = minOf(1f, 800f / maxOf(bmp.width, bmp.height))
                val scaled = if (scale < 1f) {
                    Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
                } else bmp
                val out = ByteArrayOutputStream()
                // keep PNG for cutouts so transparency survives into the feed
                if (BagCropper.isCutout(path)) scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                else scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
                Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            }
        }
        request(a.serverUrl, "/beans", "POST", a.token, JSONObject().apply {
            put("name", bean.name)
            put("roaster", bean.roaster)
            put("origin", bean.origin)
            put("variety", bean.variety)
            put("process", bean.process)
            put("notes", bean.notes)
            put("rating", bean.rating.toDouble())
            photoB64?.let { put("photo_b64", it) }
        })
    }

    /** Fetch + cache a feed photo (keyed by post id) for display. */
    suspend fun fetchPhoto(context: Context, post: FeedPost): File? = withContext(Dispatchers.IO) {
        val url = post.photoUrl ?: return@withContext null
        val dir = File(context.cacheDir, "feed").apply { mkdirs() }
        val ext = if (url.endsWith(".png")) "png" else "jpg"
        val f = File(dir, "${post.id}.$ext")
        if (f.exists() && f.length() > 0) return@withContext f
        runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout = 15000
            conn.inputStream.use { input -> f.outputStream().use { input.copyTo(it) } }
            conn.disconnect()
            f
        }.getOrNull()
    }
}
