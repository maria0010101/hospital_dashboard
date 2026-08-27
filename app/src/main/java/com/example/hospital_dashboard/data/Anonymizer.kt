package com.example.hospital_dashboard.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * 混淆/還原引擎：建立「真實名稱 → 代號」一對一對照表，僅暫存於 App 本機。
 * - obfuscate()：上傳前將真實名稱抽換為代號（AI 伺服器永遠只看得到混淆後文字）
 * - rehydrate()：AI 回傳後將代號還原為真實名稱（僅在 UI 顯示階段進行）
 */
class Anonymizer {

    enum class Kind(val codePrefix: String) {
        Branch("院區"),      // 甲院區、乙院區…
        Dept("科別"),        // 科別01…
        Doctor("醫師"),      // 醫師001…
        Clinic("門診部")     // 門診部01…
    }

    private val realToCode = LinkedHashMap<String, String>()
    private val codeToReal = LinkedHashMap<String, String>()
    private val counters = HashMap<Kind, Int>()
    private val branchNumerals = "甲乙丙丁戊己庚辛壬癸子丑寅卯辰巳午未申酉戌亥"

    val size: Int get() = realToCode.size

    /** 取得（或建立）真實名稱對應的代號。 */
    fun codeOf(real: String, kind: Kind): String {
        val r = real.trim()
        // 防呆：單一字元符號（如 "."）不成為混淆對象，避免污染百分比/小數點
        if (r.length < 2 || !r.any { it.isLetterOrDigit() }) return r
        realToCode[r]?.let { return it }
        val code = when (kind) {
            Kind.Branch -> {
                val i = counters.getOrDefault(kind, 0)
                counters[kind] = i + 1
                if (i < branchNumerals.length) "${branchNumerals[i]}${kind.codePrefix}" else "院區${i + 1}"
            }
            Kind.Dept, Kind.Clinic -> {
                val i = counters.getOrDefault(kind, 0) + 1
                counters[kind] = i
                String.format("%s%02d", kind.codePrefix, i)
            }
            Kind.Doctor -> {
                val i = counters.getOrDefault(kind, 0) + 1
                counters[kind] = i
                String.format("%s%03d", kind.codePrefix, i)
            }
        }
        realToCode[r] = code
        codeToReal[code] = r
        return code
    }

    /** 直接註冊既有對照（還原時使用）。 */
    fun put(real: String, code: String) {
        if (real.isBlank() || code.isBlank()) return
        val rr = real.trim(); val cc = code.trim()
        if (rr.length < 2 || !rr.any { it.isLetterOrDigit() }) return
        realToCode[rr] = cc
        codeToReal[cc] = rr
    }

    /** 混淆：真實名稱 → 代號（依名稱長度由長到短替換，避免子字串誤換）。 */
    fun obfuscate(text: String): String {
        var out = text
        realToCode.entries.sortedByDescending { it.key.length }.forEach { (real, code) ->
            out = out.replace(real, code)
        }
        return out
    }

    /** 還原：代號 → 真實名稱。 */
    fun rehydrate(text: String): String {
        var out = text
        codeToReal.entries.sortedByDescending { it.key.length }.forEach { (code, real) ->
            out = out.replace(code, real)
        }
        return out
    }

    /** 對照表序列化（存 SharedPreferences 快取）。 */
    fun toJson(): String {
        val arr = JSONArray()
        realToCode.forEach { (real, code) ->
            arr.put(JSONObject().put("real", real).put("code", code))
        }
        return arr.toString()
    }

    companion object {
        fun fromJson(s: String?): Anonymizer {
            val a = Anonymizer()
            if (s.isNullOrBlank()) return a
            try {
                val arr = JSONArray(s)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    a.put(o.optString("real"), o.optString("code"))
                }
            } catch (e: Exception) { /* 忽略損毀快取 */ }
            return a
        }
    }
}
