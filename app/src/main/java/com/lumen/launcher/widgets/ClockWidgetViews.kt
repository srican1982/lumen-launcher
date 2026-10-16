package com.lumen.launcher.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.widget.RemoteViews
import com.lumen.launcher.R
import com.lumen.launcher.data.WeatherSnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.cos
import kotlin.math.sin

/** Apple-style clock / weather / calendar widgets. */
object ClockWidgetViews {
    fun views(context: Context, id: Int, kind: WidgetKind, data: WidgetSnapshot): RemoteViews {
        val layout = when (kind) {
            WidgetKind.ClockWeather -> R.layout.widget_clock_weather
            WidgetKind.WorldClock -> R.layout.widget_clock_world
            WidgetKind.ClockFeatured -> R.layout.widget_clock_featured
            WidgetKind.CalendarDay -> R.layout.widget_clock_calendar
            WidgetKind.MomentClock -> R.layout.widget_clock_moment
            else -> R.layout.widget_clock_weather
        }
        val view = RemoteViews(context.packageName, layout)
        val now = System.currentTimeMillis()
        when (kind) {
            WidgetKind.ClockWeather -> bindWeather(context, id, view, data, now)
            WidgetKind.WorldClock -> bindWorld(context, id, view, now)
            WidgetKind.ClockFeatured -> bindFeatured(context, id, view, data, now)
            WidgetKind.CalendarDay -> bindCalendar(context, id, view, now)
            WidgetKind.MomentClock -> bindMoment(context, id, view, now)
            else -> Unit
        }
        return view
    }

    private fun bindWeather(context: Context, id: Int, view: RemoteViews, data: WidgetSnapshot, now: Long) {
        view.setTextViewText(R.id.clock_widget_date, formatLocal(now, "EEE, MMM d"))
        view.setTextViewText(R.id.clock_widget_time, formatLocal(now, "h:mm a"))
        bindWeatherFields(view, data.weather)
        view.setTextViewText(R.id.clock_widget_greeting, greeting(now))
        val open = WidgetActions.pending(context, id, "ClockWeather")
        view.setOnClickPendingIntent(R.id.clock_widget_root, open)
    }

    private fun bindWorld(context: Context, id: Int, view: RemoteViews, now: Long) {
        view.setTextViewText(R.id.clock_widget_date, formatLocal(now, "EEE, MMM d"))
        view.setImageViewBitmap(R.id.clock_widget_analog, analogFace(now, 288))
        val cities = WidgetWorldCities.read(context, id)
        val rows = listOf(
            Triple(R.id.clock_widget_city1, R.id.clock_widget_city_time1, R.id.clock_widget_city_day1),
            Triple(R.id.clock_widget_city2, R.id.clock_widget_city_time2, R.id.clock_widget_city_day2),
            Triple(R.id.clock_widget_city3, R.id.clock_widget_city_time3, R.id.clock_widget_city_day3),
            Triple(R.id.clock_widget_city4, R.id.clock_widget_city_time4, R.id.clock_widget_city_day4)
        )
        val localDay = dayKey(now, TimeZone.getDefault())
        rows.forEachIndexed { index, (nameId, timeId, dayId) ->
            val city = cities.getOrNull(index)
            if (city == null) {
                view.setTextViewText(nameId, "Add city")
                view.setTextViewText(timeId, "")
                view.setTextViewText(dayId, "")
            } else {
                view.setTextViewText(nameId, city.name)
                view.setTextViewText(timeId, formatInZone(now, city.zone, "h:mm a"))
                view.setTextViewText(dayId, dayLabel(now, city.zone, localDay))
            }
            view.setOnClickPendingIntent(
                listOf(
                    R.id.clock_widget_city_row1, R.id.clock_widget_city_row2,
                    R.id.clock_widget_city_row3, R.id.clock_widget_city_row4
                )[index],
                WidgetActions.worldCities(context, id, index)
            )
        }
        view.setOnClickPendingIntent(R.id.clock_widget_root, WidgetActions.worldCities(context, id, -1))
        view.setOnClickPendingIntent(R.id.clock_widget_cities, WidgetActions.worldCities(context, id, -1))
    }

    private fun bindFeatured(context: Context, id: Int, view: RemoteViews, data: WidgetSnapshot, now: Long) {
        view.setTextViewText(R.id.clock_widget_date, formatLocal(now, "EEE, MMM d"))
        view.setTextViewText(R.id.clock_widget_time, formatLocal(now, "h:mm a"))
        bindWeatherFields(view, data.weather)
        val high = data.weather?.highLabel()
        val low = data.weather?.lowLabel()
        view.setTextViewText(
            R.id.clock_widget_hi_lo,
            when {
                high != null && low != null -> "H: $high  L: $low"
                else -> ""
            }
        )
        val event = data.nextEvent
        if (event != null) {
            view.setTextViewText(R.id.clock_widget_event_title, event.title.ifBlank { "Upcoming" })
            view.setTextViewText(R.id.clock_widget_event_time, formatLocal(event.begin, "h:mm a"))
        } else {
            view.setTextViewText(R.id.clock_widget_event_title, if (data.locked) "Unlock for events" else "No upcoming events")
            view.setTextViewText(R.id.clock_widget_event_time, "Open calendar")
        }
        view.setOnClickPendingIntent(R.id.clock_widget_root, WidgetActions.pending(context, id, "ClockFeatured"))
        view.setOnClickPendingIntent(R.id.clock_widget_event, WidgetActions.pending(context, id, "Calendar"))
    }

    private fun bindCalendar(context: Context, id: Int, view: RemoteViews, now: Long) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        view.setTextViewText(R.id.clock_widget_weekday, SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(now)))
        view.setTextViewText(R.id.clock_widget_day, cal.get(Calendar.DAY_OF_MONTH).toString())
        view.setTextViewText(R.id.clock_widget_month, SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date(now)))
        val labels = listOf(
            R.id.clock_widget_week_label_0, R.id.clock_widget_week_label_1, R.id.clock_widget_week_label_2,
            R.id.clock_widget_week_label_3, R.id.clock_widget_week_label_4, R.id.clock_widget_week_label_5,
            R.id.clock_widget_week_label_6
        )
        val days = listOf(
            R.id.clock_widget_week_day_0, R.id.clock_widget_week_day_1, R.id.clock_widget_week_day_2,
            R.id.clock_widget_week_day_3, R.id.clock_widget_week_day_4, R.id.clock_widget_week_day_5,
            R.id.clock_widget_week_day_6
        )
        val start = (cal.clone() as Calendar).apply {
            firstDayOfWeek = Calendar.SUNDAY
            set(Calendar.DAY_OF_WEEK, Calendar.SUNDAY)
        }
        val today = cal.get(Calendar.DAY_OF_YEAR)
        val year = cal.get(Calendar.YEAR)
        val short = SimpleDateFormat("EEEEE", Locale.getDefault())
        for (i in 0 until 7) {
            val day = (start.clone() as Calendar).apply { add(Calendar.DAY_OF_MONTH, i) }
            view.setTextViewText(labels[i], short.format(day.time).take(1))
            view.setTextViewText(days[i], day.get(Calendar.DAY_OF_MONTH).toString())
            val isToday = day.get(Calendar.DAY_OF_YEAR) == today && day.get(Calendar.YEAR) == year
            view.setInt(
                days[i], "setBackgroundResource",
                if (isToday) R.drawable.widget_clock_day_on else 0
            )
            view.setTextColor(days[i], if (isToday) 0xFFFFFFFF.toInt() else 0xE6FFFFFF.toInt())
        }
        view.setOnClickPendingIntent(R.id.clock_widget_root, WidgetActions.pending(context, id, "Calendar"))
    }

    private fun bindMoment(context: Context, id: Int, view: RemoteViews, now: Long) {
        view.setTextViewText(R.id.clock_widget_quote, "One moment at a time.")
        view.setTextViewText(R.id.clock_widget_time, formatLocal(now, "h:mm a"))
        view.setTextViewText(R.id.clock_widget_date, formatLocal(now, "EEE, MMM d"))
        view.setOnClickPendingIntent(R.id.clock_widget_root, WidgetActions.pending(context, id, "MomentClock"))
    }

    private fun bindWeatherFields(view: RemoteViews, weather: WeatherSnapshot?) {
        if (weather == null) {
            view.setTextViewText(R.id.clock_widget_weather_icon, "☁")
            view.setTextViewText(R.id.clock_widget_temp, "--°")
            view.setTextViewText(R.id.clock_widget_place, "Weather")
            view.setTextViewText(R.id.clock_widget_summary, "Open Lumen to refresh")
            return
        }
        view.setTextViewText(R.id.clock_widget_weather_icon, weatherIcon(weather.summary))
        view.setTextViewText(R.id.clock_widget_temp, weather.temperatureLabel())
        val place = weather.place.takeIf { it.isNotBlank() && !it.equals("Local", true) }
            ?: TimeZone.getDefault().id.substringAfterLast('/').replace('_', ' ')
        view.setTextViewText(R.id.clock_widget_place, place)
        view.setTextViewText(R.id.clock_widget_summary, weather.summary)
    }

    private fun weatherIcon(summary: String): String {
        val s = summary.lowercase(Locale.US)
        return when {
            s.contains("storm") -> "⛈"
            s.contains("snow") -> "❄"
            s.contains("rain") || s.contains("drizzle") -> "🌧"
            s.contains("fog") || s.contains("overcast") -> "☁"
            s.contains("clear") || s.contains("sun") -> "☀"
            else -> "⛅"
        }
    }

    private fun greeting(now: Long): String {
        val hour = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good Morning\nMake it a great day."
            in 12..16 -> "Good Afternoon\nKeep a steady pace."
            in 17..21 -> "Good Evening\nEase into the night."
            else -> "Quiet Hours\nRest well tonight."
        }
    }

    private fun formatLocal(now: Long, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).format(Date(now))

    private fun formatInZone(now: Long, zone: TimeZone, pattern: String): String =
        SimpleDateFormat(pattern, Locale.getDefault()).apply { timeZone = zone }.format(Date(now))

    private fun dayKey(now: Long, zone: TimeZone): String =
        SimpleDateFormat("yyyyMMdd", Locale.US).apply { timeZone = zone }.format(Date(now))

    private fun dayLabel(now: Long, zone: TimeZone, localDay: String): String {
        val cityDay = dayKey(now, zone)
        return when {
            cityDay == localDay -> "Today"
            else -> SimpleDateFormat("EEE", Locale.getDefault()).apply { timeZone = zone }.format(Date(now))
        }
    }

    private fun analogFace(now: Long, size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f - 4f
        val face = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33101828; style = Paint.Style.FILL }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x66FFFFFF.toInt(); style = Paint.Style.STROKE; strokeWidth = size * 0.02f
        }
        canvas.drawCircle(cx, cy, radius, face)
        canvas.drawCircle(cx, cy, radius, ring)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x99FFFFFF.toInt(); strokeWidth = size * 0.012f; strokeCap = Paint.Cap.ROUND
        }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xE6FFFFFF.toInt(); textAlign = Paint.Align.CENTER
            textSize = size * 0.11f; isFakeBoldText = true
        }
        for (hour in 0 until 12) {
            val angle = Math.toRadians(hour * 30.0 - 90.0)
            val outer = radius - size * 0.04f
            val inner = radius - size * 0.1f
            canvas.drawLine(
                (cx + cos(angle) * inner).toFloat(), (cy + sin(angle) * inner).toFloat(),
                (cx + cos(angle) * outer).toFloat(), (cy + sin(angle) * outer).toFloat(), tick
            )
            if (hour % 3 == 0) {
                val value = if (hour == 0) "12" else hour.toString()
                val tx = (cx + cos(angle) * (radius - size * 0.2f)).toFloat()
                val ty = (cy + sin(angle) * (radius - size * 0.2f)).toFloat() + label.textSize * 0.35f
                canvas.drawText(value, tx, ty, label)
            }
        }
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val seconds = cal.get(Calendar.SECOND) + cal.get(Calendar.MILLISECOND) / 1000f
        val minutes = cal.get(Calendar.MINUTE) + seconds / 60f
        val hours = (cal.get(Calendar.HOUR) % 12) + minutes / 60f
        fun hand(angleDeg: Double, length: Float, width: Float, color: Int) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color; strokeWidth = width; strokeCap = Paint.Cap.ROUND
            }
            val rad = Math.toRadians(angleDeg - 90.0)
            canvas.drawLine(cx, cy, (cx + cos(rad) * length).toFloat(), (cy + sin(rad) * length).toFloat(), paint)
        }
        hand(hours * 30.0, radius * 0.5f, size * 0.035f, 0xFFFFFFFF.toInt())
        hand(minutes * 6.0, radius * 0.72f, size * 0.025f, 0xFFFFFFFF.toInt())
        hand(seconds * 6.0, radius * 0.78f, size * 0.012f, 0xFF8B5CF6.toInt())
        val hub = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF8B5CF6.toInt() }
        canvas.drawCircle(cx, cy, size * 0.03f, hub)
        return bitmap
    }
}
