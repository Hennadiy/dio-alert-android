package com.dio.alert

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** A region the phone can follow (an oblast, Kyiv or Sevastopol), by its alerts.in.ua uid. The list comes from Mind. */
data class Region(val uid: Int, val name: String, val nameEn: String) {
    /** Ukrainian on a Ukrainian phone, English otherwise (like the app's strings). */
    val displayName: String
        get() = displayName(name, nameEn)

    fun toJson(): JSONObject = JSONObject().put("uid", uid).put("name", name).put("nameEn", nameEn)

    companion object {
        /** Kyiv city: followed until the user picks another region. */
        val DEFAULT = Region(31, "м. Київ", "Kyiv city")

        fun displayName(name: String, nameEn: String): String =
            if (Locale.getDefault().language == "uk") name else nameEn

        fun fromJson(json: JSONObject) = Region(json.getInt("uid"), json.getString("name"), json.getString("nameEn"))

        fun listFromJson(json: JSONArray): List<Region> = (0 until json.length()).map { fromJson(json.getJSONObject(it)) }
    }
}
