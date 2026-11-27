package it.lgdb.biblenotes.domain

import kotlin.math.max

data class ReadTimerDisplay(
    val text: String,
    val isCountdown: Boolean,
    val isWarning: Boolean,
    val isOvertime: Boolean
)

object ReadTimerFormatter {

    fun computeDisplay(
        elapsedMs: Long,
        targetDurationMs: Long?
    ): ReadTimerDisplay {
        val safeElapsedMs = max(0L, elapsedMs)
        if (targetDurationMs == null || targetDurationMs <= 0L) {
            return ReadTimerDisplay(
                text = formatSeconds(safeElapsedMs / 1000L),
                isCountdown = false,
                isWarning = false,
                isOvertime = false
            )
        }

        val remainingMs = targetDurationMs - safeElapsedMs
        return when {
            remainingMs >= 1000L -> {
                val displaySeconds = (remainingMs + 999L) / 1000L
                val warningThresholdMs = minOf(60_000L, max(15_000L, targetDurationMs / 4L))
                ReadTimerDisplay(
                    text = formatSeconds(displaySeconds),
                    isCountdown = true,
                    isWarning = remainingMs <= warningThresholdMs,
                    isOvertime = false
                )
            }
            remainingMs >= -999L -> {
                ReadTimerDisplay(
                    text = "00:00",
                    isCountdown = true,
                    isWarning = false,
                    isOvertime = true
                )
            }
            else -> {
                val overtimeSeconds = (-remainingMs) / 1000L
                ReadTimerDisplay(
                    text = "+${formatSeconds(overtimeSeconds)}",
                    isCountdown = true,
                    isWarning = false,
                    isOvertime = true
                )
            }
        }
    }

    fun formatSeconds(totalSeconds: Long): String {
        val safe = max(0L, totalSeconds)
        val hours = safe / 3600L
        val minutes = (safe % 3600L) / 60L
        val seconds = safe % 60L
        return if (hours > 0L) {
            "%d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
