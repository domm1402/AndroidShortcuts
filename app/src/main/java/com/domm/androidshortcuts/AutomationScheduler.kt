package com.domm.androidshortcuts

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.util.Calendar

object AutomationScheduler {
    private const val PREFS = "shortcut_store"
    private const val ITEMS = "items"
    private const val IDS = "scheduled_ids"

    fun sync(context: Context, items: List<ShortcutItem>) {
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val oldIds = prefs.getString(IDS, "")!!.split(",").mapNotNull { it.toLongOrNull() }
        oldIds.forEach { cancel(context, alarm, it) }
        val scheduled = items.filter { it.trigger.startsWith("Daily at ") && validTime(it.trigger.removePrefix("Daily at ")) }
        scheduled.forEach { scheduleNext(context, it.id, it.trigger.removePrefix("Daily at ")) }
        prefs.edit().putString(IDS, scheduled.joinToString(",") { it.id.toString() }).apply()
    }

    fun scheduleNext(context: Context, id: Long, time: String) {
        val parts = time.split(":")
        if (parts.size != 2) return
        val hour = parts[0].toIntOrNull() ?: return
        val minute = parts[1].toIntOrNull() ?: return
        if (hour !in 0..23 || minute !in 0..59) return
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AutomationReceiver::class.java).putExtra("shortcut_id", id).putExtra("time", time)
        val pending = PendingIntent.getBroadcast(context, id.toInt(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pending)
    }

    private fun cancel(context: Context, alarm: AlarmManager, id: Long) {
        val intent = Intent(context, AutomationReceiver::class.java)
        val pending = PendingIntent.getBroadcast(context, id.toInt(), intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)
        if (pending != null) { alarm.cancel(pending); pending.cancel() }
    }

    private fun validTime(value: String): Boolean {
        if (!value.matches(Regex("\\d{2}:\\d{2}"))) return false
        val p = value.split(":")
        return (p[0].toIntOrNull() ?: 99) in 0..23 && (p[1].toIntOrNull() ?: 99) in 0..59
    }
}

class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("shortcut_id", -1L)
        val time = intent.getStringExtra("time") ?: return
        if (id < 0) return
        val prefs = context.getSharedPreferences("shortcut_store", Context.MODE_PRIVATE)
        val items = runCatching { JSONArray(prefs.getString("items", "[]")) }.getOrNull() ?: return
        var name = "Scheduled shortcut"
        val messages = mutableListOf<String>()
        var copied = 0
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            if (item.optLong("id") != id) continue
            name = item.optString("name", name)
            val steps = item.optJSONArray("steps") ?: JSONArray()
            for (j in 0 until steps.length()) {
                val step = steps.optJSONObject(j) ?: continue
                when (step.optString("type")) {
                    "Show message" -> messages.add(step.optString("value").ifBlank { name })
                    "Copy to clipboard" -> {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Shortcut", step.optString("value")))
                        copied++
                    }
                }
            }
            break
        }
        AutomationScheduler.scheduleNext(context, id, time)
        postNotification(context, id, name, messages, copied)
    }

    private fun postNotification(context: Context, id: Long, name: String, messages: List<String>, copied: Int) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = "scheduled_shortcuts"
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(channel, "Scheduled shortcuts", NotificationManager.IMPORTANCE_DEFAULT))
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pending = launch?.let { PendingIntent.getActivity(context, id.toInt(), it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE) }
        val detail = buildList {
            if (messages.isNotEmpty()) addAll(messages)
            if (copied > 0) add(if (copied == 1) "Text copied to clipboard" else "$copied items copied to clipboard")
            if (isEmpty()) add("Scheduled time reached. Open the app to run actions that need the screen.")
        }.joinToString("\n")
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(name)
            .setContentText(detail)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detail))
            .setAutoCancel(true)
            .apply { if (pending != null) setContentIntent(pending) }
            .build()
        manager.notify(id.toInt(), notification)
    }
}
