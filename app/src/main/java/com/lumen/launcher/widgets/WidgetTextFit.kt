package com.lumen.launcher.widgets

import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.text.TextUtils
import java.util.WeakHashMap
import kotlin.math.roundToInt

/** Applied to native previews and hosted RemoteViews, including subsequent refreshes. */
object WidgetTextFit {
    private val configured = WeakHashMap<TextView, Boolean>()

    fun install(root: View) {
        root.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> fit(view) }
        fit(root)
    }

    private fun fit(view: View) {
        if (view is TextView && configured.put(view, true) == null) {
            val scale = view.resources.displayMetrics.scaledDensity
            val maximum = (view.textSize / scale).roundToInt().coerceAtLeast(10)
            // Keep glyph ascenders/descenders intact; shrink long labels before ellipsizing.
            view.includeFontPadding = true
            if (view.maxLines == 1) view.ellipsize = TextUtils.TruncateAt.END
            view.setAutoSizeTextTypeUniformWithConfiguration(9.coerceAtMost(maximum), maximum, 1,
                TypedValue.COMPLEX_UNIT_SP)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) fit(view.getChildAt(i))
    }
}
