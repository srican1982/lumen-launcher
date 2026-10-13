package com.lumen.launcher.widgets

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import com.lumen.launcher.R
import com.lumen.launcher.data.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TaskWidgetViews {
    fun views(context: Context, id: Int, kind: WidgetKind, data: WidgetSnapshot): RemoteViews {
        val view = RemoteViews(context.packageName, R.layout.widget_task_card)
        // RemoteViews can be reapplied onto the old tree after unlock or resize.
        view.setViewVisibility(R.id.task_card_content, View.VISIBLE)
        view.setViewVisibility(R.id.task_card_list, View.VISIBLE)
        listOf(R.id.task_card_tabs, R.id.task_card_counts, R.id.task_card_filters, R.id.task_card_motivation,
            R.id.task_card_overview, R.id.task_card_next).forEach { view.setViewVisibility(it, View.GONE) }
        val light = kind in setOf(WidgetKind.Tasks, WidgetKind.TasksManager) || LumenThemeMode.parse(data.stored.theme).isWhiteGlass
        val text = if (light) 0xFF152237.toInt() else 0xFFF5F7FF.toInt()
        val muted = if (light) 0xFF526174.toInt() else 0xFFBAC8DB.toInt()
        view.setInt(R.id.task_card_root, "setBackgroundResource", if (light) R.drawable.widget_task_light else R.drawable.widget_task_dark)
        val open = WidgetActions.pending(context, id, "TaskList")
        view.setOnClickPendingIntent(R.id.task_card_root, open)
        view.setOnClickPendingIntent(R.id.task_card_add, WidgetActions.pending(context, id, "AddTask"))
        view.setTextColor(R.id.task_card_title, text); view.setTextColor(R.id.task_card_date, muted)
        view.setTextViewText(R.id.task_card_title, when (kind) {
            WidgetKind.TasksNext -> "Next Up"
            WidgetKind.TasksManager -> "My Tasks"
            WidgetKind.TasksFilters -> "Tasks"
            else -> "Today"
        })
        val now = System.currentTimeMillis()
        view.setTextViewText(R.id.task_card_date, SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(now)))
        if (data.locked) {
            view.setTextViewText(R.id.task_card_date, "Unlock to see your tasks")
            view.setViewVisibility(R.id.task_card_content, View.GONE)
            view.setTextViewText(R.id.task_card_footer, "Open Lumen  ›")
            view.setOnClickPendingIntent(R.id.task_card_footer, open)
            return view
        }
        val all = data.stored.todos.filter { it.space != SpaceKind.Private }
        val filter = context.getSharedPreferences("task_widgets", Context.MODE_PRIVATE).getString("filter_$id", "Today") ?: "Today"
        val rows = TaskWidgetRules.visible(all, filter, now)
        val today = TaskWidgetRules.visible(all, "Today", now)
        val pending = today.count { !it.done }; val done = today.count { it.done }
        val overdue = today.count { TaskAgenda.overdue(it, now) }
        val options = if (id > 0) AppWidgetManager.getInstance(context).getAppWidgetOptions(id) else android.os.Bundle()
        val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, if (kind.wide) 320 else 150)
        val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, kind.heightDp)
        val checks = listOf(R.id.task_card_check1, R.id.task_card_check2, R.id.task_card_check3, R.id.task_card_check4, R.id.task_card_check5)
        val labels = listOf(R.id.task_card_text1, R.id.task_card_text2, R.id.task_card_text3, R.id.task_card_text4, R.id.task_card_text5)
        val times = listOf(R.id.task_card_time1, R.id.task_card_time2, R.id.task_card_time3, R.id.task_card_time4, R.id.task_card_time5)
        val rowIds = listOf(R.id.task_card_row1, R.id.task_card_row2, R.id.task_card_row3, R.id.task_card_row4, R.id.task_card_row5)
        val capacity = if (height >= 220) 5 else if (height >= 170) 3 else 2
        rowIds.forEachIndexed { index, row ->
            val task = rows.getOrNull(index)
            view.setViewVisibility(row, if (task != null && index < capacity) View.VISIBLE else View.GONE)
            view.setTextColor(labels[index], if (task?.done == true) muted else text)
            view.setTextColor(times[index], muted)
            if (task != null) {
                view.setTextViewText(labels[index], task.text)
                view.setTextViewText(checks[index], if (task.done) "✓" else "☐")
                view.setTextColor(checks[index], if (task.done) 0xFF21CF91.toInt() else muted)
                view.setInt(labels[index], "setPaintFlags", Paint.ANTI_ALIAS_FLAG or if (task.done) Paint.STRIKE_THRU_TEXT_FLAG else 0)
                view.setTextViewText(times[index], task.dueAt?.let { due -> if (TodoTime.hasClock(due))
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(due)) else "" }.orEmpty())
                view.setOnClickPendingIntent(labels[index], open)
                view.setOnClickPendingIntent(checks[index], if (task.done) open else WidgetCommandReceiver.pending(context, id, kind, "CompleteListedTask", WidgetTaskTarget.encode(task)))
                view.setContentDescription(checks[index], if (task.done) "${task.text}, completed" else "Mark ${task.text} done")
            }
        }
        view.setTextViewText(R.id.task_card_footer, if (rows.isEmpty()) "Add your first task  +" else "View all (${rows.size})  ›")
        view.setOnClickPendingIntent(R.id.task_card_footer, if (rows.isEmpty()) WidgetActions.pending(context, id, "AddTask") else open)
        val counts = "$pending To do\n\n$done Done\n\n$overdue Overdue"
        view.setTextViewText(R.id.task_card_counts, counts); view.setTextColor(R.id.task_card_counts, muted)
        if (kind == WidgetKind.TasksManager && width >= 320) view.setViewVisibility(R.id.task_card_counts, View.VISIBLE)
        if (kind in setOf(WidgetKind.Tasks, WidgetKind.TasksManager) && width >= 300 && height >= 220)
            view.setViewVisibility(R.id.task_card_motivation, View.VISIBLE)
        val tabs = listOf(R.id.task_card_tabtoday to "Today", R.id.task_card_tabweek to "Week", R.id.task_card_taball to "All")
        if (kind == WidgetKind.TasksManager && height >= 180) {
            view.setViewVisibility(R.id.task_card_tabs, View.VISIBLE)
            tabs.forEach { (button, value) ->
                view.setTextColor(button, if (filter == value) 0xFF28251D.toInt() else muted)
                view.setInt(button, "setBackgroundResource", if (filter == value) R.drawable.widget_trip_status else android.R.color.transparent)
                view.setOnClickPendingIntent(button, WidgetCommandReceiver.pending(context, id, kind, "SetTaskFilter", value))
            }
        }
        if (kind == WidgetKind.TasksFilters && width >= 260 && height >= 180) {
            view.setViewVisibility(R.id.task_card_filters, View.VISIBLE)
            val buttons = listOf(R.id.task_card_filtertoday to "Today", R.id.task_card_filterinbox to "Inbox", R.id.task_card_filterimportant to "Important")
            buttons.forEach { (button, value) ->
                val count = TaskWidgetRules.visible(all, value, now).size
                view.setTextViewText(button, "$value  $count")
                view.setOnClickPendingIntent(button, WidgetCommandReceiver.pending(context, id, kind, "SetTaskFilter", value))
            }
        }
        if (kind == WidgetKind.TasksOverview) {
            view.setViewVisibility(R.id.task_card_list, View.GONE)
            view.setViewVisibility(R.id.task_card_overview, View.VISIBLE)
            view.setTextViewText(R.id.task_card_overview_counts, "$pending To do\n\n$done Done\n\n$overdue Overdue")
            view.setTextColor(R.id.task_card_overview_counts, muted)
            view.setImageViewBitmap(R.id.task_card_ring, progress(done, pending + done, text))
            view.setContentDescription(R.id.task_card_ring, "$done of ${pending + done} tasks completed today")
            view.setTextViewText(R.id.task_card_footer, "Progress, not perfection.  ›")
        }
        if (kind == WidgetKind.TasksNext) {
            view.setViewVisibility(R.id.task_card_list, View.GONE)
            view.setViewVisibility(R.id.task_card_next, View.VISIBLE)
            val next = TaskAgenda.today(all, now).firstOrNull() ?: TaskWidgetRules.visible(all, "All", now).firstOrNull { !it.done }
            view.setTextColor(R.id.task_card_next_title, text); view.setTextColor(R.id.task_card_next_time, muted)
            view.setTextViewText(R.id.task_card_next_title, next?.text ?: "All clear")
            view.setTextViewText(R.id.task_card_next_time, next?.dueAt?.let { SimpleDateFormat(if (TodoTime.hasClock(it)) "EEE, h:mm a" else "EEE, MMM d", Locale.getDefault()).format(Date(it)) } ?: if (next == null) "Enjoy a little breathing room" else "No due time")
            view.setTextViewText(R.id.task_card_footer, if (next == null) "Add task  +" else "✓  Mark done")
            view.setOnClickPendingIntent(R.id.task_card_footer, if (next == null) WidgetActions.pending(context, id, "AddTask") else
                WidgetCommandReceiver.pending(context, id, kind, "CompleteListedTask", WidgetTaskTarget.encode(next)))
        }
        return view
    }

    private fun progress(done: Int, total: Int, color: Int): Bitmap {
        val result = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 16f; strokeCap = Paint.Cap.ROUND; this.color = 0x40577E80 }
        val rect = RectF(16f, 16f, 176f, 176f)
        canvas.drawArc(rect, -90f, 360f, false, paint)
        paint.color = 0xFF21CF91.toInt()
        if (total > 0) canvas.drawArc(rect, -90f, 360f * done / total, false, paint)
        paint.style = Paint.Style.FILL; paint.textAlign = Paint.Align.CENTER; paint.color = color
        paint.textSize = 52f; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
        canvas.drawText(done.toString(), 96f, 100f, paint)
        paint.textSize = 25f; paint.typeface = android.graphics.Typeface.DEFAULT
        canvas.drawText("of $total", 96f, 133f, paint)
        return result
    }
}
