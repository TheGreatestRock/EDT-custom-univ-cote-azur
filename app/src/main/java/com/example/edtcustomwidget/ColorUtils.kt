package com.example.edtcustomwidget

import android.graphics.Color
import kotlin.math.abs

fun generateColorFromTitle(title: String): Int {
    val hash = abs(title.hashCode()) // Éviter les couleurs négatives

    // Crée une teinte dans un cercle de couleurs (0 à 360 degrés)
    val hue = hash % 360
    val saturation = 0.6f
    val brightness = 0.85f

    return Color.HSVToColor(floatArrayOf(hue.toFloat(), saturation, brightness))
}
