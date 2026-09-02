package com.johndsdev.visdroid

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlin.concurrent.thread
import kotlin.math.max
import kotlin.math.min

class PlaybackCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.johndsdev.visdroid.START_PLAYBACK_CAPTURE"
        const val ACTION_STOP = "com.johndsdev.visdroid.STOP_PLAYBACK_CAPTURE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "visdroid_playback_capture"
        private const val NOTIFICATION_ID = 6001
        private const val SAMPLE_RATE = 48_000
        private const val FFT_SIZE = 2048
        // 48,000 / 800 = exactly 60 spectrum updates per second.
        private const val HOP_SIZE = 800
    }

    private var projection: MediaProjection? = null
    private var recorder: AudioRecord? = null
    @Volatile private var running = false
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (running) return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Int.MIN_VALUE)
        val resultData = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_RESULT_DATA)
        }
        if (resultCode == Int.MIN_VALUE || resultData == null) {
            PlaybackSpectrumBus.clear("missing capture permission")
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val manager = getSystemService(MediaProjectionManager::class.java)
            val mp = manager.getMediaProjection(resultCode, resultData)
            mp.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    PlaybackSpectrumBus.clear("capture stopped by Android")
                    stopSelf()
                }
            }, Handler(Looper.getMainLooper()))
            projection = mp
            startRecorder(mp)
        } catch (t: Throwable) {
            PlaybackSpectrumBus.clear("capture failed: ${t.javaClass.simpleName}")
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRecorder(mediaProjection: MediaProjection) {
        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
            .build()

        val config = android.media.AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
            .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
            .addMatchingUsage(AudioAttributes.USAGE_GAME)
            .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
            .build()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferBytes = max(minBuffer * 2, FFT_SIZE * 2 * 2)
        val record = AudioRecord.Builder()
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufferBytes)
            .setAudioPlaybackCaptureConfig(config)
            .build()

        record.startRecording()
        recorder = record
        running = true
        PlaybackSpectrumBus.active.set(true)
        PlaybackSpectrumBus.status.set("capturing")

        worker = thread(name = "VisDroidPlaybackCapture", isDaemon = true) {
            // Keep a full FFT window for frequency resolution, but advance it by only 800 samples.
            // That gives us overlapping windows at 60 Hz instead of one new 2048-sample frame at ~23 Hz.
            val hop = ShortArray(HOP_SIZE)
            val fftWindow = ShortArray(FFT_SIZE)
            val analyzer = PcmSpectrumAnalyzer(FFT_SIZE)
            var filled = 0

            try {
                while (running) {
                    var offset = 0
                    while (running && offset < HOP_SIZE) {
                        val read = record.read(hop, offset, HOP_SIZE - offset, AudioRecord.READ_BLOCKING)
                        if (read > 0) {
                            offset += read
                        } else if (read < 0) {
                            throw IllegalStateException("AudioRecord.read returned $read")
                        }
                    }
                    if (!running || offset <= 0) break

                    val shift = min(offset, FFT_SIZE)
                    System.arraycopy(fftWindow, shift, fftWindow, 0, FFT_SIZE - shift)
                    System.arraycopy(hop, offset - shift, fftWindow, FFT_SIZE - shift, shift)
                    filled = min(FFT_SIZE, filled + shift)

                    // Once there is at least one hop of real PCM, analyze the full rolling window.
                    // Its leading zeros disappear naturally during the first ~43 ms after capture starts.
                    if (filled >= HOP_SIZE) {
                        val settings = SettingsStore.load(this)
                        PlaybackSpectrumBus.publish(
                            analyzer.analyze(fftWindow, FFT_SIZE, SAMPLE_RATE, settings)
                        )
                    }
                }
            } catch (t: Throwable) {
                if (running) PlaybackSpectrumBus.status.set("audio read failed: ${t.javaClass.simpleName}")
            } finally {
                analyzer.reset()
            }
        }
    }

    override fun onDestroy() {
        running = false
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        worker?.interrupt()
        worker = null
        runCatching { projection?.stop() }
        projection = null
        PlaybackSpectrumBus.clear("off")
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Audio reactive wallpaper",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps VisDroid's system-audio capture active for the live wallpaper"
                    setShowBadge(false)
                }
            )
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, CaptureLauncherActivity::class.java)
        val openPending = PendingIntent.getActivity(
            this,
            1,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = Intent(this, PlaybackCaptureService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this,
            2,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_visdroid)
            .setContentTitle("VisDroid audio reactive mode")
            .setContentText("Listening to capturable device playback for the wallpaper")
            .setContentIntent(openPending)
            .setOngoing(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Stop", stopPending).build())
            .build()
    }
}
