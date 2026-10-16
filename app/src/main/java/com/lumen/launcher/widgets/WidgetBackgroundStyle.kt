package com.lumen.launcher.widgets

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import java.util.WeakHashMap

/** Remove decoration, while retaining real photos, avatars, controls and widget content. */
object WidgetBackgroundStyle {
    private val backgrounds = WeakHashMap<View, Drawable>()
    private val visibility = WeakHashMap<View, Int>()
    fun install(root: View, context: Context, id: Int) {
        root.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ -> apply(view, context, id) }
        apply(root, context, id)
    }
    fun apply(root: View, context: Context, id: Int) {
        val clear = WidgetHostStore.transparent(context, id)
        fun visit(view: View) {
            if (view is ViewGroup || view.javaClass == View::class.java) {
                if (clear) {
                    view.background?.let { backgrounds[view] = it }
                    view.background = null
                } else if (view.background == null) backgrounds.remove(view)?.let { view.background = it }
            }
            if (view is ImageView) {
                val name = if (view.id == View.NO_ID) "" else runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("")
                val decoration = name.endsWith("_bg") || name.contains("background") || name.contains("scrim") || name.contains("wallpaper") || name == "clock_widget_cover" || name == "trip_widget_cover"
                if (decoration && clear) {
                    if (view.visibility != View.GONE) visibility[view] = view.visibility
                    view.visibility = View.GONE
                } else if (!clear) visibility.remove(view)?.let { view.visibility = it }
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
    }
}
