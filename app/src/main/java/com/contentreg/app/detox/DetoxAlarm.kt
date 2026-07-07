package com.contentreg.app.detox

import android.content.Context
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * The sensory cue when a detox is armed: a firm vibration plus a short notification tone, so locking
 * yourself in feels deliberate rather than like an accidental tap. Fire-and-forget; failures (no
 * vibrator, silent mode) are swallowed.
 */
object DetoxAlarm {

    fun fire(context: Context) {
        vibrate(context)
        playTone(context)
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        } ?: return
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(600L, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }

    private fun playTone(context: Context) {
        runCatching {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            RingtoneManager.getRingtone(context.applicationContext, uri)?.play()
        }
    }
}
