package com.johndsdev.visdroid

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class CaptureLauncherActivity : Activity() {
    companion object {
        private const val REQ_AUDIO = 301
        private const val REQ_CAPTURE = 302
        private const val BG = 0xFF0B0D10.toInt()
        private const val CARD = 0xFF15181E.toInt()
        private const val TEXT = 0xFFF5F6F8.toInt()
        private const val SUBTLE = 0xFFA7ADB8.toInt()
        private const val ACCENT = 0xFF9CB8FF.toInt()
    }

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            requestPlaybackCapture()
        } else if (requestCode == REQ_AUDIO) {
            Toast.makeText(this, "audio permission is required", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Kept for minSdk compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "system audio capture wasn't enabled", Toast.LENGTH_LONG).show()
            refreshStatus()
            return
        }

        val serviceIntent = Intent(this, PlaybackCaptureService::class.java).apply {
            action = PlaybackCaptureService.ACTION_START
            putExtra(PlaybackCaptureService.EXTRA_RESULT_CODE, resultCode)
            putExtra(PlaybackCaptureService.EXTRA_RESULT_DATA, data)
        }
        if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        status.postDelayed({
            refreshStatus()
            openSettings()
        }, 350L)
    }

    private fun enableAudio() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
        } else {
            requestPlaybackCapture()
        }
    }

    private fun requestPlaybackCapture() {
        val manager = getSystemService(MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    private fun stopAudio() {
        startService(Intent(this, PlaybackCaptureService::class.java).apply {
            action = PlaybackCaptureService.ACTION_STOP
        })
        PlaybackSpectrumBus.clear("off")
        refreshStatus()
    }

    private fun openSettings() {
        startActivity(Intent(this, MainActivity::class.java))
    }

    private fun refreshStatus() {
        if (!::status.isInitialized) return
        status.text = if (PlaybackSpectrumBus.active.get()) {
            "system audio: active"
        } else {
            "system audio: off"
        }
    }

    private fun buildUi(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(28), dp(22), dp(28))
            setBackgroundColor(BG)

            addView(TextView(this@CaptureLauncherActivity).apply {
                text = "visdroid"
                setTextColor(TEXT)
                textSize = 32f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            addView(TextView(this@CaptureLauncherActivity).apply {
                text = "audio-reactive live wallpaper"
                setTextColor(SUBTLE)
                textSize = 14f
                setPadding(0, dp(3), 0, dp(24))
            })

            addView(LinearLayout(this@CaptureLauncherActivity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(18), dp(18), dp(18), dp(18))
                background = rounded(CARD, 22)

                status = TextView(this@CaptureLauncherActivity).apply {
                    setTextColor(TEXT)
                    textSize = 18f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                addView(status)
                addView(TextView(this@CaptureLauncherActivity).apply {
                    text = "Modern Android blocks silent global playback capture. Enable system audio here and approve Android's capture prompt. VisDroid uses the permission only for playback audio analysis; it does not create a screen recording or save audio."
                    setTextColor(SUBTLE)
                    textSize = 14f
                    setPadding(0, dp(8), 0, dp(16))
                })
                addView(button("enable system audio", true) { enableAudio() })
                addView(button("stop audio capture", false) { stopAudio() }.apply {
                    (layoutParams as LinearLayout.LayoutParams).topMargin = dp(8)
                })
            })

            addView(button("visualizer settings", false) { openSettings() }.apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(14)
            })

            addView(TextView(this@CaptureLauncherActivity).apply {
                text = "A small persistent notification is required by Android while playback capture is active. Some protected audio can still opt out of capture."
                setTextColor(SUBTLE)
                textSize = 12f
                setPadding(2, dp(16), 2, 0)
            })

            refreshStatus()
        }
    }

    private fun button(label: String, primary: Boolean, click: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(if (primary) Color.rgb(10, 15, 25) else TEXT)
            background = rounded(if (primary) ACCENT else 0xFF252A33.toInt(), 15)
            setOnClickListener { click() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
        }
    }

    private fun rounded(color: Int, radiusDp: Int) = GradientDrawable().apply {
        cornerRadius = dp(radiusDp).toFloat()
        setColor(color)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density + .5f).toInt()
}
