package dev.adamsjack.beanshelf.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import dev.adamsjack.beanshelf.model.Bean
import dev.adamsjack.beanshelf.model.Brew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Friend-to-friend bean sharing without a server: a `.beanshelf` file is one
 * JSON document with the bean's fields, brews, and both photos embedded as
 * base64. Send it over anything (AirDrop, chat, email); the recipient's
 * Beanshelf imports it onto their own shelf.
 */
object BeanPack {

    private const val VERSION = 1

    suspend fun export(context: Context, bean: Bean): File = withContext(Dispatchers.IO) {
        val root = JSONObject().apply {
            put("beanshelf", VERSION)
            put("bean", JSONObject().apply {
                put("name", bean.name)
                put("roaster", bean.roaster)
                put("origin", bean.origin)
                put("roastLevel", bean.roastLevel)
                put("process", bean.process)
                put("notes", bean.notes)
                put("variety", bean.variety)
                put("elevation", bean.elevation)
                put("producer", bean.producer)
                put("roastedOn", bean.roastedOn)
                put("rating", bean.rating.toDouble())
                put("brews", JSONArray().apply {
                    bean.brews.forEach { br ->
                        put(JSONObject().apply {
                            put("method", br.method)
                            put("rating", br.rating.toDouble())
                            put("note", br.note)
                            put("timestamp", br.timestamp)
                            put("doseG", br.doseG?.toDouble() ?: JSONObject.NULL)
                            put("waterG", br.waterG?.toDouble() ?: JSONObject.NULL)
                            put("grinder", br.grinder)
                            put("grindSize", br.grindSize)
                        })
                    }
                })
            })
            bean.photoPath?.let { p ->
                put("frontPhoto", Base64.encodeToString(File(p).readBytes(), Base64.NO_WRAP))
                put("frontIsCutout", BagCropper.isCutout(p))
            }
            bean.backPhotoPath?.let { p ->
                put("backPhoto", Base64.encodeToString(File(p).readBytes(), Base64.NO_WRAP))
                put("backIsCutout", BagCropper.isCutout(p))
            }
        }
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val safeName = bean.name.ifBlank { "bean" }.replace(Regex("[^A-Za-z0-9 _-]"), "").trim()
            .replace(' ', '-').lowercase().ifBlank { "bean" }
        val out = File(dir, "$safeName.beanshelf")
        out.writeText(root.toString())
        out
    }

    /** Reads a .beanshelf file into a NEW bean (fresh id, photos copied in). */
    suspend fun import(context: Context, uri: Uri): Bean? = withContext(Dispatchers.IO) {
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@runCatching null
            val root = JSONObject(text)
            if (root.optInt("beanshelf", 0) < 1) return@runCatching null
            val b = root.getJSONObject("bean")

            fun savePhoto(key: String, cutoutKey: String): String? {
                val b64 = root.optString(key, "")
                if (b64.isBlank()) return null
                val bytes = Base64.decode(b64, Base64.NO_WRAP)
                val ext = if (root.optBoolean(cutoutKey, false)) "png" else "jpg"
                val f = File(PhotoStore.photosDir(context), "${UUID.randomUUID()}.$ext")
                f.writeBytes(bytes)
                return f.absolutePath
            }

            val brewsArr = b.optJSONArray("brews") ?: JSONArray()
            Bean(
                id = UUID.randomUUID().toString(),
                name = b.optString("name"),
                roaster = b.optString("roaster"),
                origin = b.optString("origin"),
                roastLevel = b.optString("roastLevel"),
                process = b.optString("process"),
                notes = b.optString("notes"),
                variety = b.optString("variety"),
                elevation = b.optString("elevation"),
                producer = b.optString("producer"),
                roastedOn = b.optString("roastedOn"),
                rating = b.optDouble("rating", 0.0).toFloat(),
                photoPath = savePhoto("frontPhoto", "frontIsCutout"),
                backPhotoPath = savePhoto("backPhoto", "backIsCutout"),
                createdAt = System.currentTimeMillis(),
                brews = (0 until brewsArr.length()).map { i ->
                    val br = brewsArr.getJSONObject(i)
                    Brew(
                        id = UUID.randomUUID().toString(),
                        method = br.optString("method"),
                        rating = br.optDouble("rating", 0.0).toFloat(),
                        note = br.optString("note"),
                        timestamp = br.optLong("timestamp"),
                        doseG = if (br.isNull("doseG")) null else br.optDouble("doseG").toFloat(),
                        waterG = if (br.isNull("waterG")) null else br.optDouble("waterG").toFloat(),
                        grinder = br.optString("grinder"),
                        grindSize = br.optString("grindSize"),
                    )
                },
            )
        }.getOrNull()
    }
}
