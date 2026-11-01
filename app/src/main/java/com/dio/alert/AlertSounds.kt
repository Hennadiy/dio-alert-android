package com.dio.alert

import androidx.annotation.RawRes
import androidx.annotation.StringRes

/**
 * The sounds a user can pick for each alert kind (res/raw, made by tools/make_sounds.py).
 * [id] is what Prefs stores, so keep ids stable when renaming or reordering.
 */
enum class AlertSounds(val id: String, @StringRes val nameRes: Int, @RawRes val rawRes: Int) {
    BUZZ("buzz", R.string.sound_buzz, R.raw.alert_drones),
    RISING_SIREN("rising_siren", R.string.sound_rising_siren, R.raw.alert_missiles),
    CHIME("chime", R.string.sound_chime, R.raw.alert_end),
    AIR_SIREN("air_siren", R.string.sound_air_siren, R.raw.sound_air_siren),
    KLAXON("klaxon", R.string.sound_klaxon, R.raw.sound_klaxon),
    BEEPS("beeps", R.string.sound_beeps, R.raw.sound_beeps),
    ALARM_CLOCK("alarm_clock", R.string.sound_alarm_clock, R.raw.sound_alarm_clock),
    BELL("bell", R.string.sound_bell, R.raw.sound_bell),
    HORN("horn", R.string.sound_horn, R.raw.sound_horn),
    PULSE("pulse", R.string.sound_pulse, R.raw.sound_pulse),
    MARIMBA("marimba", R.string.sound_marimba, R.raw.sound_marimba),

    // Softer ones.
    HARP("harp", R.string.sound_harp, R.raw.sound_harp),
    MUSIC_BOX("music_box", R.string.sound_music_box, R.raw.sound_music_box),
    GLASS("glass", R.string.sound_glass, R.raw.sound_glass),
    SOFT_PAD("soft_pad", R.string.sound_soft_pad, R.raw.sound_soft_pad),
    WIND_CHIMES("wind_chimes", R.string.sound_wind_chimes, R.raw.sound_wind_chimes),
    DOORBELL("doorbell", R.string.sound_doorbell, R.raw.sound_doorbell);

    companion object {
        /** The alert kinds a sound can be picked for, in the order the settings card lists them. */
        val KINDS = listOf("drones", "missiles", "end")

        /** What each kind sounds like until the user picks something else. Unknown kinds sound like missiles. */
        fun defaultFor(kind: String): AlertSounds = when (kind) {
            "drones" -> BUZZ
            "end" -> CHIME
            else -> RISING_SIREN
        }

        fun byId(id: String?): AlertSounds? = entries.firstOrNull { it.id == id }
    }
}
