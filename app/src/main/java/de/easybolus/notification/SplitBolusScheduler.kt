package de.easybolus.notification

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import de.easybolus.MainActivity
import de.easybolus.R
import de.easybolus.data.local.DiaryEntryEntity
import de.easybolus.data.local.DiaryEntryType
import de.easybolus.data.local.EasyBolusDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PendingSplitBolus(
    val id: Int,
    val part2Units: Double,
    val totalUnits: Double,
    val note: String?,
    val dueAtMillis: Long,
    val bgAtTimeMgDl: Int?
)

object PendingSplitStore {
    private const val PREFS_NAME = "easybolus_pending_splits"
    private const val KEY_SPLITS = "splits_json"
    private const val KEY_NEXT_ID = "next_id"

    fun nextId(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val id = prefs.getInt(KEY_NEXT_ID, 2100)
        prefs.edit().putInt(KEY_NEXT_ID, if (id >= 2999) 2100 else id + 1).apply()
        return id
    }

    fun save(context: Context, split: PendingSplitBolus) {
        val list = loadAll(context).filterNot { it.id == split.id }.toMutableList()
        list.add(split)
        persist(context, list)
    }

    fun getById(context: Context, id: Int): PendingSplitBolus? =
        loadAll(context).firstOrNull { it.id == id }

    fun remove(context: Context, id: Int) {
        val remaining = loadAll(context).filterNot { it.id == id }
        persist(context, remaining)
    }

    fun loadAll(context: Context): List<PendingSplitBolus> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_SPLITS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { idx ->
                val obj = arr.getJSONObject(idx)
                PendingSplitBolus(
                    id = obj.getInt("id"),
                    part2Units = obj.getDouble("part2Units"),
                    totalUnits = obj.getDouble("totalUnits"),
                    note = obj.optString("note").takeIf { it.isNotBlank() },
                    dueAtMillis = obj.getLong("dueAtMillis"),
                    bgAtTimeMgDl = if (obj.has("bgAtTimeMgDl")) obj.getInt("bgAtTimeMgDl") else null
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun persist(context: Context, list: List<PendingSplitBolus>) {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject().apply {
                    put("id", s.id)
                    put("part2Units", s.part2Units)
                    put("totalUnits", s.totalUnits)
                    if (!s.note.isNullOrBlank()) put("note", s.note)
                    put("dueAtMillis", s.dueAtMillis)
                    if (s.bgAtTimeMgDl != null) put("bgAtTimeMgDl", s.bgAtTimeMgDl)
                }
            )
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_SPLITS, arr.toString())
            .apply()
    }
}

object SplitBolusScheduler {

    const val ACTION_SHOW_SPLIT_REMINDER = "de.easybolus.ACTION_SHOW_SPLIT_REMINDER"
    const val ACTION_CONFIRM_SPLIT = "de.easybolus.ACTION_CONFIRM_SPLIT"
    const val ACTION_DISMISS_SPLIT = "de.easybolus.ACTION_DISMISS_SPLIT"
    const val EXTRA_SPLIT_ID = "extra_split_id"

    fun schedule(
        context: Context,
        part2Units: Double,
        totalUnits: Double,
        delayMinutes: Int,
        note: String?,
        bgAtTimeMgDl: Int?
    ): PendingSplitBolus {
        val appContext = context.applicationContext
        val id = PendingSplitStore.nextId(appContext)
        val dueAtMillis = System.currentTimeMillis() + delayMinutes * 60_000L
        val split = PendingSplitBolus(
            id = id,
            part2Units = part2Units,
            totalUnits = totalUnits,
            note = note?.takeIf { it.isNotBlank() },
            dueAtMillis = dueAtMillis,
            bgAtTimeMgDl = bgAtTimeMgDl
        )
        PendingSplitStore.save(appContext, split)

        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, SplitBolusReceiver::class.java).apply {
            action = ACTION_SHOW_SPLIT_REMINDER
            putExtra(EXTRA_SPLIT_ID, id)
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            id,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()
        if (canExact) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtMillis, pi)
        } else {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, dueAtMillis, pi)
        }
        return split
    }

    fun cancel(context: Context, splitId: Int) {
        val appContext = context.applicationContext
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(appContext, SplitBolusReceiver::class.java).apply {
            action = ACTION_SHOW_SPLIT_REMINDER
            putExtra(EXTRA_SPLIT_ID, splitId)
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            splitId,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pi != null) {
            alarmManager.cancel(pi)
        }
        PendingSplitStore.remove(appContext, splitId)
        NotificationManagerCompat.from(appContext).cancel(splitId)
    }
}

class SplitBolusReceiver : BroadcastReceiver() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val splitId = intent.getIntExtra(SplitBolusScheduler.EXTRA_SPLIT_ID, -1)
        if (splitId <= 0) return

        when (action) {
            SplitBolusScheduler.ACTION_SHOW_SPLIT_REMINDER -> {
                val split = PendingSplitStore.getById(context, splitId) ?: return
                showReminderNotification(context, split)
            }
            SplitBolusScheduler.ACTION_CONFIRM_SPLIT -> {
                val split = PendingSplitStore.getById(context, splitId)
                NotificationManagerCompat.from(context).cancel(splitId)
                if (split == null) return

                val pendingResult = goAsync()
                scope.launch {
                    try {
                        val dao = EasyBolusDatabase.getInstance(context).dao()
                        val latestBg = dao.getLatestGlucose()
                        val baseNote = split.note?.takeIf { it.isNotBlank() }
                        val combinedNote = if (baseNote != null) "$baseNote [Split 2/2]" else "[Split 2/2]"
                        dao.insertDiaryEntry(
                            DiaryEntryEntity(
                                timestampMillis = System.currentTimeMillis(),
                                type = DiaryEntryType.BOLUS,
                                bolusUnits = split.part2Units,
                                note = combinedNote,
                                bgAtTimeMgDl = latestBg?.mgDl ?: split.bgAtTimeMgDl,
                                trendAtTime = latestBg?.trendArrow
                            )
                        )
                        PendingSplitStore.remove(context, splitId)
                        EasyBolusNotifier.resetCorrectionTimerOnBolus(context)
                        EasyBolusNotifier.refreshAll(context, evaluateCorrection = false)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            SplitBolusScheduler.ACTION_DISMISS_SPLIT -> {
                PendingSplitStore.remove(context, splitId)
                NotificationManagerCompat.from(context).cancel(splitId)
            }
        }
    }

    private fun showReminderNotification(context: Context, split: PendingSplitBolus) {
        EasyBolusNotifier.ensureChannels(context)
        val hasPerm = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!hasPerm) return

        val locale = Locale.getDefault()
        val ieUnit = context.getString(R.string.unit_ie)
        val part2Text = String.format(locale, "%.1f %s", split.part2Units, ieUnit)
        val totalText = String.format(locale, "%.1f %s", split.totalUnits, ieUnit)
        val timeFmt = SimpleDateFormat("HH:mm", locale)
        val dueText = timeFmt.format(Date(split.dueAtMillis))

        val openAppIntent = PendingIntent.getActivity(
            context,
            split.id,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmIntent = PendingIntent.getBroadcast(
            context,
            split.id + 10000,
            Intent(context, SplitBolusReceiver::class.java).apply {
                this.action = SplitBolusScheduler.ACTION_CONFIRM_SPLIT
                putExtra(SplitBolusScheduler.EXTRA_SPLIT_ID, split.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            context,
            split.id + 20000,
            Intent(context, SplitBolusReceiver::class.java).apply {
                this.action = SplitBolusScheduler.ACTION_DISMISS_SPLIT
                putExtra(SplitBolusScheduler.EXTRA_SPLIT_ID, split.id)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = context.getString(R.string.notif_split_title, part2Text)
        val body = if (!split.note.isNullOrBlank()) {
            context.getString(R.string.notif_split_body_note, totalText, dueText, split.note)
        } else {
            context.getString(R.string.notif_split_body, totalText, dueText)
        }

        val notification = NotificationCompat.Builder(context, EasyBolusNotifier.CHANNEL_SPLIT_REMINDER)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(false)
            .setContentIntent(openAppIntent)
            .addAction(0, context.getString(R.string.notif_split_action_confirm), confirmIntent)
            .addAction(0, context.getString(R.string.notif_split_action_dismiss), dismissIntent)
            .build()

        NotificationManagerCompat.from(context).notify(split.id, notification)
    }
}
