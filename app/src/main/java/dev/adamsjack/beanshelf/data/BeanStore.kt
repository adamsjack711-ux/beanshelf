package dev.adamsjack.beanshelf.data

import android.content.Context
import dev.adamsjack.beanshelf.model.Bean
import dev.adamsjack.beanshelf.model.Brew
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Plain JSON-file persistence: filesDir/beans.json. Small collection, no DB needed. */
class BeanStore(context: Context) {

    private val file = File(context.filesDir, "beans.json")

    suspend fun load(): List<Bean> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).map { beanFromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    suspend fun save(beans: List<Bean>) = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        beans.forEach { arr.put(beanToJson(it)) }
        val tmp = File(file.parentFile, "beans.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(file)
    }

    private fun beanToJson(b: Bean) = JSONObject().apply {
        put("id", b.id)
        put("name", b.name)
        put("roaster", b.roaster)
        put("origin", b.origin)
        put("roastLevel", b.roastLevel)
        put("process", b.process)
        put("notes", b.notes)
        put("variety", b.variety)
        put("elevation", b.elevation)
        put("producer", b.producer)
        put("roastedOn", b.roastedOn)
        put("rating", b.rating.toDouble())
        put("photoPath", b.photoPath ?: JSONObject.NULL)
        put("backPhotoPath", b.backPhotoPath ?: JSONObject.NULL)
        put("createdAt", b.createdAt)
        put("brews", JSONArray().apply {
            b.brews.forEach { br ->
                put(JSONObject().apply {
                    put("id", br.id)
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
    }

    private fun beanFromJson(o: JSONObject): Bean {
        val brewsArr = o.optJSONArray("brews") ?: JSONArray()
        return Bean(
            id = o.getString("id"),
            name = o.optString("name"),
            roaster = o.optString("roaster"),
            origin = o.optString("origin"),
            roastLevel = o.optString("roastLevel"),
            process = o.optString("process"),
            notes = o.optString("notes"),
            variety = o.optString("variety"),
            elevation = o.optString("elevation"),
            producer = o.optString("producer"),
            roastedOn = o.optString("roastedOn"),
            rating = o.optDouble("rating", 0.0).toFloat(),
            photoPath = if (o.isNull("photoPath")) null else o.optString("photoPath"),
            backPhotoPath = if (o.isNull("backPhotoPath")) null else o.optString("backPhotoPath"),
            createdAt = o.optLong("createdAt"),
            brews = (0 until brewsArr.length()).map { i ->
                val br = brewsArr.getJSONObject(i)
                Brew(
                    id = br.getString("id"),
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
    }
}
