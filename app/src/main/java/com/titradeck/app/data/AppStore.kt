package com.titradeck.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.core.content.edit
import com.titradeck.app.chemistry.*
import com.titradeck.app.ui.BackgroundColors
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class HistoryEntry(val id: String, val toolId: String, val title: String, val timestamp: Long,
    val inputs: Map<String, String>, val result: CalculationResult, val formula: String)

class AppStore(context: Context) : SQLiteOpenHelper(context.applicationContext, "titradeck.db", null, 1) {
    private val preferences = context.applicationContext.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    init {
        // Retire OEM styling without clearing appearance or calculation history.
        if (preferences.contains("style")) preferences.edit { remove("style") }
    }
    var backgroundHex: String?
        get() = readColor("backgroundHex")
        set(value) = saveColor("backgroundHex", value)
    var themeHex: String?
        get() = readColor("themeHex")
        set(value) = saveColor("themeHex", value)

    private fun readColor(key: String): String? = preferences.getString(key, null)?.let(BackgroundColors::normalize)

    private fun saveColor(key: String, value: String?) {
        val normalized = value?.let(BackgroundColors::normalize)
        require(value == null || normalized != null) { "颜色须为六位 HEX，例如 #166B58" }
        preferences.edit {
            if (normalized == null) remove(key) else putString(key, normalized)
        }
    }

    var appearance: String
        get() = preferences.getString("appearance", "system") ?: "system"
        set(value) { preferences.edit { putString("appearance", value) } }
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE history (id TEXT PRIMARY KEY, tool_id TEXT NOT NULL, title TEXT NOT NULL, timestamp INTEGER NOT NULL, inputs TEXT NOT NULL, result TEXT NOT NULL, formula TEXT NOT NULL)")
        db.execSQL("CREATE INDEX history_time ON history(timestamp DESC)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun save(tool: ToolDefinition, inputs: Map<String, String>, result: CalculationResult) {
        writableDatabase.insertOrThrow("history", null, ContentValues().apply {
            put("id", UUID.randomUUID().toString()); put("tool_id", tool.id); put("title", tool.title)
            put("timestamp", System.currentTimeMillis()); put("inputs", JSONObject(inputs).toString())
            put("result", encodeResult(result)); put("formula", tool.formula)
        })
    }
    fun history(limit: Int = 100, query: String = ""): List<HistoryEntry> {
        val result = mutableListOf<HistoryEntry>()
        val where = if (query.isBlank()) null else "title LIKE ? OR inputs LIKE ?"
        val args = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")
        readableDatabase.query("history", null, where, args, null, null, "timestamp DESC, rowid DESC", limit.toString()).use { cursor ->
            while (cursor.moveToNext()) {
                fun text(key: String) = cursor.getString(cursor.getColumnIndexOrThrow(key))
                val input = JSONObject(text("inputs"))
                result += HistoryEntry(text("id"), text("tool_id"), text("title"), cursor.getLong(cursor.getColumnIndexOrThrow("timestamp")),
                    input.keys().asSequence().associateWith { input.getString(it) }, decodeResult(text("result")), text("formula"))
            }
        }
        return result
    }
    fun delete(id: String) { writableDatabase.delete("history", "id = ?", arrayOf(id)) }
    fun clearHistory() { writableDatabase.delete("history", null, null) }
    companion object {
        fun encodeResult(value: CalculationResult): String = JSONObject().apply {
            put("headline", value.headline); put("value", value.value); put("unit", value.unit)
            put("rows", JSONArray().apply { value.rows.forEach { put(JSONObject().put("label", it.label).put("value", it.value).put("unit", it.unit)) } })
            put("steps", JSONArray(value.steps)); put("notes", JSONArray(value.notes))
            value.molecule?.let { model -> put("molecule", JSONObject().apply {
                put("caption", model.caption)
                put("atoms", JSONArray().apply { model.atoms.forEach { put(JSONObject().put("symbol", it.symbol).put("x", it.x).put("y", it.y).put("z", it.z).put("lonePairs", it.lonePairs).put("formalCharge", it.formalCharge)) } })
                put("bonds", JSONArray().apply { model.bonds.forEach { put(JSONObject().put("from", it.from).put("to", it.to).put("order", it.order)) } })
            }) }
        }.toString()
        fun decodeResult(text: String): CalculationResult {
            val json = JSONObject(text)
            fun strings(key: String): List<String> = json.optJSONArray(key)?.let { array -> (0 until array.length()).map { array.getString(it) } } ?: emptyList()
            val rows = json.getJSONArray("rows")
            val molecule = json.optJSONObject("molecule")?.let { obj ->
                val atoms = obj.getJSONArray("atoms"); val bonds = obj.getJSONArray("bonds")
                MoleculeModel((0 until atoms.length()).map { atoms.getJSONObject(it).let { a -> MoleculeAtom(a.getString("symbol"), a.getDouble("x").toFloat(), a.getDouble("y").toFloat(), a.getDouble("z").toFloat(), a.optInt("lonePairs"), a.optInt("formalCharge")) } },
                    (0 until bonds.length()).map { bonds.getJSONObject(it).let { b -> MoleculeBond(b.getInt("from"), b.getInt("to"), b.getInt("order")) } }, obj.getString("caption"))
            }
            return CalculationResult(json.getString("headline"), json.getString("value"), json.optString("unit"),
                (0 until rows.length()).map { rows.getJSONObject(it).let { row -> ResultRow(row.getString("label"), row.getString("value"), row.optString("unit")) } }, strings("steps"), strings("notes"), molecule)
        }
    }
}
