package com.example.funder.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.funder.MainActivity
import com.example.funder.R
import com.example.funder.data.local.FundDao
import com.example.funder.data.remote.FundApiService
import com.example.funder.data.repository.SettingsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@HiltWorker
class NavSettlementWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val fundDao: FundDao,
    private val apiService: FundApiService,
    private val settingsRepository: SettingsRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val CHANNEL_ID = "nav_settlement"
        const val NOTIFICATION_ID = 1001
        const val WORK_NAME = "nav_settlement_check"

        fun createNotificationChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "净值结算通知",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "当日基金净值结算后推送最终收益"
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override suspend fun doWork(): Result {
        if (!settingsRepository.isSettlementNotificationEnabledSync()) {
            return Result.success()
        }

        if (!isTradingDay()) {
            return Result.success()
        }

        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(
            Calendar.getInstance().time
        )

        val lastNotified = settingsRepository.getLastSettlementNotifiedDateSync()
        if (lastNotified == todayStr) {
            return Result.success()
        }

        try {
            val holdings = fundDao.getAllHoldingsSync()
            if (holdings.isEmpty()) return Result.success()

            val codes = holdings.map { it.fundCode }
            val valuations = apiService.getValuations(codes)

            if (valuations.isEmpty()) return Result.retry()

            val settledCodes = mutableSetOf<String>()
            for (code in codes) {
                val latestDate = apiService.getNavHistory(code, page = 1, perPage = 1)
                    .firstOrNull()?.date
                if (latestDate == todayStr) settledCodes.add(code)
            }

            if (settledCodes.isEmpty()) return Result.retry()
            if (settledCodes.size < codes.size) return Result.retry()

            var totalDayProfit = 0.0
            val details = mutableListOf<String>()

            for (holding in holdings) {
                if (holding.fundCode !in settledCodes) continue
                val valuation = valuations[holding.fundCode] ?: continue

                val navHistory = apiService.getNavHistory(holding.fundCode, page = 1, perPage = 2)
                val todayNav = navHistory.firstOrNull()?.nav ?: continue
                val yesterdayNav = navHistory.getOrNull(1)?.nav ?: continue

                val profit = holding.shares * (todayNav - yesterdayNav)
                totalDayProfit += profit

                val name = holding.fundName.ifEmpty { valuation.name.ifEmpty { holding.fundCode } }
                val sign = if (profit >= 0) "+" else ""
                details.add("$name ${sign}${"%.2f".format(profit)}")
            }

            settingsRepository.setLastSettlementNotifiedDate(todayStr)
            sendNotification(totalDayProfit, details, settledCodes.size)

            return Result.success()
        } catch (e: Exception) {
            return if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun isTradingDay(): Boolean {
        val cal = Calendar.getInstance()
        val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
        return dayOfWeek != Calendar.SATURDAY && dayOfWeek != Calendar.SUNDAY
    }

    private fun sendNotification(totalProfit: Double, details: List<String>, count: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val sign = if (totalProfit >= 0) "+" else ""
        val title = "今日净值已结算"
        val summary = "总收益 ${sign}${"%.2f".format(totalProfit)} 元（${count}只基金）"

        val detailText = if (details.size <= 4) {
            details.joinToString("\n")
        } else {
            details.take(3).joinToString("\n") + "\n...及其他${details.size - 3}只基金"
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$summary\n\n$detailText")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
