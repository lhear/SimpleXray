package com.simplexray.an.common

enum class ThemeMode(val value: String) {
    Light("Light"),
    Dark("Dark"),
    Auto("Auto");

    companion object {
        fun fromString(value: String): ThemeMode = when (value) {
            "Light" -> Light
            "Dark" -> Dark
            else -> Auto
        }
    }
}
