package com.lumen.launcher.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import com.lumen.launcher.data.IconTreatment

val LocalIconTreatment = staticCompositionLocalOf { IconTreatment.Original }

fun iconColorFilter(treatment: IconTreatment): ColorFilter? = when (treatment) {
    IconTreatment.Original, IconTreatment.Glass, IconTreatment.WhiteGlass, IconTreatment.GlassColor -> null
    IconTreatment.Work -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.52f) })
    IconTreatment.Mono -> ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0.16f) })
    IconTreatment.Contrast -> ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                1.24f, 0f, 0f, 0f, -8f,
                0f, 1.24f, 0f, 0f, -8f,
                0f, 0f, 1.24f, 0f, -8f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

fun iconElevation(treatment: IconTreatment, rest: Float): Float = when (treatment) {
    IconTreatment.Glass -> rest + 6f
    IconTreatment.WhiteGlass, IconTreatment.GlassColor -> rest + 3f
    IconTreatment.Work -> rest + 2f
    IconTreatment.Mono -> rest - 1f
    IconTreatment.Contrast -> rest + 4f
    IconTreatment.Original -> rest
}

fun iconHasGlass(treatment: IconTreatment): Boolean =
    treatment == IconTreatment.Glass || treatment == IconTreatment.Work ||
        treatment == IconTreatment.Contrast || treatment == IconTreatment.WhiteGlass ||
        treatment == IconTreatment.GlassColor
