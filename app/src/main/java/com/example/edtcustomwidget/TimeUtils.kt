package com.example.edtcustomwidget

fun Float.toHourString(): String {
    val h = toInt()
    val m = if ((this - h) >= 0.5f) "30" else "00"
    return "${h}h$m"
}
