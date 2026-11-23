package com.titradeck.app.data

import com.titradeck.app.chemistry.MoleculeAtom
import com.titradeck.app.chemistry.MoleculeBond
import com.titradeck.app.chemistry.MoleculeModel
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.roundToInt
import kotlin.math.sqrt
import org.json.JSONObject

data class PubChemCompound(
    val cid: Int,
    val name: String,
    val formula: String,
    val smiles: String,
    val molecularWeight: String,
    val heavyAtomCount: Int = 0,
)

data class PubChemSearchResult(
    val compounds: List<PubChemCompound>,
    val pageIndex: Int = 0,
    val hasNext: Boolean = false,
    val translatedName: String? = null,
    internal val listKey: String? = null,
)

internal fun depictionImageSize(heavyAtomCount: Int): Int =
    (600 * sqrt(heavyAtomCount.coerceAtLeast(10) / 10.0)).roundToInt().coerceAtMost(2000)

class PubChem {
    private val base = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/compound"
    private val pageSize = 20

    private class HttpFailure(val status: Int) :
        IllegalArgumentException("在线请求失败（HTTP $status），请稍后重试。")

    fun search(query: String, mode: String): PubChemSearchResult {
        val input = query.trim()
        require(input.isNotEmpty() && input.length <= 200) { "请输入不超过 200 字的名称、分子式、SMILES 或 CID。" }
        if (mode == "cid") {
            val cid = input.toIntOrNull()
            require(cid != null && cid > 0) { "CID 应为正整数。" }
            return PubChemSearchResult(lookup { properties(listOf(cid)) })
        }
        val endpoint = when (mode) {
            "formula", "name" -> "$mode/${encode(input)}"
            "smiles" -> "smiles"
            else -> throw IllegalArgumentException("请选择查询方式。")
        }
        val postBody = if (mode == "smiles") "smiles=${encode(input)}" else null
        val result = try {
            findList(endpoint, postBody)
        } catch (failure: HttpFailure) {
            if (failure.status != 404) throw failure
            if (mode != "name" || input.none { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) {
                throw IllegalArgumentException("未找到匹配的化合物。")
            }
            val translated = translateName(input)
            val listKey = lookup("PubChem 未找到译名「$translated」，可尝试分子式或 CID。") {
                findList("name/${encode(translated)}")
            }
            return lookup { page(listKey, 0, translated) }
        }
        return lookup { page(result, 0) }
    }

    fun loadPage(current: PubChemSearchResult, pageIndex: Int): PubChemSearchResult =
        lookup("PubChem 的查询列表已失效，请重新查询。") {
            page(requireNotNull(current.listKey), pageIndex, current.translatedName)
        }

    private fun findList(endpoint: String, postBody: String? = null): String {
        val response = JSONObject(fetch("$base/$endpoint/cids/JSON?list_return=listkey", postBody))
        return requireNotNull(response.optJSONObject("Waiting")?.getString("ListKey")
            ?: response.optJSONObject("IdentifierList")?.getString("ListKey")) {
            "PubChem 未返回查询列表，请稍后重试。"
        }
    }

    private fun page(listKey: String, pageIndex: Int, translatedName: String? = null): PubChemSearchResult {
        val start = pageIndex * pageSize
        val address = "$base/listkey/${encode(listKey)}/cids/JSON?listkey_start=$start&listkey_count=${pageSize + 1}"
        repeat(11) { attempt ->
            if (attempt > 0) Thread.sleep(1000)
            val response = JSONObject(fetch(address))
            val ids = response.optJSONObject("IdentifierList")?.optJSONArray("CID")
            if (ids != null) {
                val pageIds = (0 until minOf(ids.length(), pageSize)).map { ids.getInt(it) }
                require(pageIds.isNotEmpty()) { "未找到匹配的化合物。" }
                return PubChemSearchResult(properties(pageIds), pageIndex, ids.length() > pageSize, translatedName, listKey)
            }
            if (!response.has("Waiting")) throw IllegalArgumentException("PubChem 未返回化合物编号，请重新查询。")
        }
        throw IllegalArgumentException("PubChem 查询仍在处理中，请稍后重试。")
    }

    private fun properties(ids: List<Int>): List<PubChemCompound> {
        val url = "$base/cid/${ids.joinToString(",")}/property/MolecularFormula,IUPACName,IsomericSMILES,MolecularWeight,HeavyAtomCount/JSON"
        val properties = JSONObject(fetch(url)).getJSONObject("PropertyTable").getJSONArray("Properties")
        val compounds = (0 until properties.length()).associate { index ->
            val item = properties.getJSONObject(index)
            val cid = item.getInt("CID")
            cid to PubChemCompound(
                cid, item.optString("IUPACName", "未提供"), item.optString("MolecularFormula"),
                item.optString("SMILES", item.optString("ConnectivitySMILES")), item.optString("MolecularWeight"),
                heavyAtomCount = item.optInt("HeavyAtomCount"),
            )
        }
        return ids.mapNotNull(compounds::get)
    }

    private inline fun <T> lookup(notFound: String = "未找到匹配的化合物。", request: () -> T): T {
        try {
            return request()
        } catch (failure: HttpFailure) {
            if (failure.status == 404) throw IllegalArgumentException(notFound, failure)
            throw failure
        }
    }

    private fun translateName(name: String): String {
        val error = "暂时无法翻译中文名称，请尝试英文名称、分子式或 CID。"
        val response = try {
            JSONObject(fetch("https://api.mymemory.translated.net/get?q=${encode(name)}&langpair=zh-CN%7Cen"))
        } catch (failure: Exception) {
            throw IllegalArgumentException(error, failure)
        }
        val translated = response.optJSONObject("responseData")?.optString("translatedText")?.trim().orEmpty()
        require(response.optInt("responseStatus") == 200 && translated.any { it in 'a'..'z' || it in 'A'..'Z' } &&
            translated != name) { error }
        return translated
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")

    fun depiction(cid: Int, heavyAtomCount: Int): ByteArray {
        val size = depictionImageSize(heavyAtomCount)
        return download("$base/cid/$cid/PNG?image_size=${size}x$size")
    }

    fun structure(cid: Int): MoleculeModel? {
        val model3d = runCatching { parseModel(fetch("$base/cid/$cid/SDF?record_type=3d")) }.getOrNull()
        return model3d ?: parseModel(fetch("$base/cid/$cid/SDF?record_type=2d"))
    }

    private fun fetch(address: String, postBody: String? = null): String =
        download(address, postBody).toString(Charsets.UTF_8)

    private fun download(address: String, postBody: String? = null): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "TitraDeck/0.2")
            if (postBody != null) {
                connection.requestMethod = "POST"
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                connection.outputStream.use { it.write(postBody.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            if (status !in 200..299) throw HttpFailure(status)
            return connection.inputStream.use { input ->
                val content = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    require(content.size() + count <= 2_000_000) { "查询结果过大。" }
                    content.write(buffer, 0, count)
                }
                content.toByteArray()
            }
        } finally { connection.disconnect() }
    }

    private fun parseModel(sdf: String): MoleculeModel? = runCatching {
        val lines = sdf.lines()
        val count = lines[3]
        require(count.contains("V2000"))
        val atomCount = count.substring(0, 3).trim().toInt()
        val bondCount = count.substring(3, 6).trim().toInt()
        require(atomCount in 1..200 && bondCount in 0..400)
        val atoms = (0 until atomCount).map { index ->
            val line = lines[4 + index]
            val chargeCode = line.substring(36, 39).trim().toIntOrNull() ?: 0
            val charge = when (chargeCode) { 1 -> 3; 2 -> 2; 3 -> 1; 5 -> -1; 6 -> -2; 7 -> -3; else -> 0 }
            MoleculeAtom(line.substring(31, 34).trim(), line.substring(0, 10).trim().toFloat(),
                line.substring(10, 20).trim().toFloat(), line.substring(20, 30).trim().toFloat(), formalCharge = charge)
        }.toMutableList()
        val bonds = (0 until bondCount).map { index ->
            val line = lines[4 + atomCount + index]
            MoleculeBond(line.substring(0, 3).trim().toInt() - 1, line.substring(3, 6).trim().toInt() - 1,
                line.substring(6, 9).trim().toInt().also { require(it in 1..3) })
        }
        lines.drop(4 + atomCount + bondCount).takeWhile { it != "M  END" }.filter { it.startsWith("M  CHG") }.forEach { line ->
            val fields = line.trim().split(Regex("\\s+"))
            val countCharges = fields[2].toInt()
            repeat(countCharges) { index ->
                val atomIndex = fields[3 + index * 2].toInt() - 1
                atoms[atomIndex] = atoms[atomIndex].copy(formalCharge = fields[4 + index * 2].toInt())
            }
        }
        val is3d = lines[1].contains("3D")
        val caption = if (is3d) "PubChem 计算构象。"
            else "PubChem 二维连接结构。"
        MoleculeModel(atoms, bonds, caption)
    }.getOrNull()
}
