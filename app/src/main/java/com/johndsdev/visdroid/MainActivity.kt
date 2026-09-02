package com.johndsdev.visdroid

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ColorStateList
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import kotlin.math.sin

class MainActivity : Activity() {
    companion object {
        private const val REQ_AUDIO = 41
        private const val REQ_IMAGE = 42
        private const val BG = 0xFF0B0D10.toInt()
        private const val CARD = 0xFF15181E.toInt()
        private const val CARD_ALT = 0xFF20242C.toInt()
        private const val TEXT = 0xFFF5F6F8.toInt()
        private const val SUBTLE = 0xFFA7ADB8.toInt()
        private const val ACCENT = 0xFF9CB8FF.toInt()
    }

    private lateinit var settings: VisSettings
    private lateinit var preview: SpectrumPreviewView
    private lateinit var permissionStatus: TextView
    private lateinit var permissionButton: Button
    private lateinit var backgroundStatus: TextView
    private var analyzer: AudioAnalyzer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = BG
        window.navigationBarColor = BG
        settings = SettingsStore.load(this)
        setContentView(buildUi())
    }

    override fun onResume() {
        super.onResume()
        settings = SettingsStore.load(this)
        preview.updateSettings(settings)
        preview.reloadBackground()
        refreshPermissionUi()
        refreshBackgroundUi()
        startPreviewAnalyzer()
    }

    override fun onPause() {
        analyzer?.stop()
        analyzer = null
        super.onPause()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_AUDIO) {
            refreshPermissionUi()
            startPreviewAnalyzer()
        }
    }

    @Deprecated("Deprecated in Android, retained for broad minSdk compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_IMAGE && resultCode == RESULT_OK) {
            val uri: Uri = data?.data ?: return
            try {
                BackgroundStore.saveFromUri(this, uri)
                preview.reloadBackground()
                refreshBackgroundUi()
                Toast.makeText(this, "background saved", Toast.LENGTH_SHORT).show()
            } catch (_: Throwable) {
                Toast.makeText(this, "couldn't read that image", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(36))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = "visdroid"
            setTextColor(TEXT)
            textSize = 32f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "an audio-reactive live wallpaper, minus the usual pile of decorative nonsense"
            setTextColor(SUBTLE)
            textSize = 14f
            setPadding(0, dp(4), 0, dp(18))
        })

        preview = SpectrumPreviewView(this).apply {
            updateSettings(settings)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(224))
        }
        root.addView(preview)

        root.addView(primaryButton("set as live wallpaper") { openWallpaperPreview() }.apply {
            (layoutParams as? LinearLayout.LayoutParams)?.topMargin = dp(12)
        })

        root.addView(sectionLabel("audio"))
        root.addView(card().apply {
            permissionStatus = TextView(this@MainActivity).apply {
                setTextColor(TEXT)
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(permissionStatus)
            addView(TextView(this@MainActivity).apply {
                text = "Android requires microphone permission for its system output visualizer API. VisDroid does not record or save microphone audio."
                setTextColor(SUBTLE)
                textSize = 13f
                setPadding(0, dp(5), 0, dp(10))
            })
            permissionButton = secondaryButton("grant audio access") {
                if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_AUDIO)
                } else {
                    val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                    startActivity(i)
                }
            }
            addView(permissionButton)
        })

        root.addView(sectionLabel("position"))
        root.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "edge"
                setTextColor(TEXT)
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            val row = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, dp(10), 0, 0)
            }
            val buttons = mutableListOf<Pair<BarSide, Button>>()
            listOf(BarSide.RIGHT to "right", BarSide.LEFT to "left", BarSide.BOTTOM to "bottom", BarSide.TOP to "top").forEach { (side, label) ->
                val b = Button(this@MainActivity).apply {
                    text = label
                    isAllCaps = false
                    textSize = 12f
                    setTextColor(TEXT)
                    minHeight = 0
                    minimumHeight = 0
                    setPadding(dp(5), dp(9), dp(5), dp(9))
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginEnd = dp(6)
                    }
                    setOnClickListener {
                        save(settings.copy(side = side))
                        updateSideButtons(buttons)
                    }
                }
                buttons += side to b
                row.addView(b)
            }
            addView(row)
            updateSideButtons(buttons)
        })

        root.addView(sectionLabel("bars"))
        root.addView(card().apply {
            addView(slider("bar count", 80, settings.barCount - 8, { "${it + 8}" }) {
                save(settings.copy(barCount = it + 8))
            })
            addView(slider("bar thickness", 22, (settings.barThicknessDp - 2f).toInt(), { "${it + 2} dp" }) {
                save(settings.copy(barThicknessDp = (it + 2).toFloat()))
            })
            addView(slider("gap", 16, settings.gapDp.toInt(), { "$it dp" }) {
                save(settings.copy(gapDp = it.toFloat()))
            })
            addView(slider("maximum length", 85, ((settings.maxLengthFraction - .15f) * 100).toInt(), { "${it + 15}%" }) {
                save(settings.copy(maxLengthFraction = (it + 15) / 100f))
            })
            addView(slider("sensitivity", 375, (settings.sensitivity * 100 - 25).toInt(), { String.format("%.2fx", (it + 25) / 100f) }) {
                save(settings.copy(sensitivity = (it + 25) / 100f))
            })
            addView(slider("decay / smoothness", 67, ((settings.decay - .30f) * 100).toInt(), { String.format("%.2f", (it + 30) / 100f) }) {
                save(settings.copy(decay = (it + 30) / 100f))
            })
            addView(slider("opacity", 80, (settings.opacity * 100 - 20).toInt(), { "${it + 20}%" }) {
                save(settings.copy(opacity = (it + 20) / 100f))
            })
            addView(toggleRow("rounded ends", settings.roundedBars) { save(settings.copy(roundedBars = it)) })
        })

        root.addView(sectionLabel("color"))
        root.addView(card().apply {
            addView(TextView(this@MainActivity).apply {
                text = "bar color"
                setTextColor(TEXT)
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })
            val swatches = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(12), 0, dp(4))
            }
            val colors = listOf(Color.WHITE, 0xFF9CB8FF.toInt(), 0xFF77E6B6.toInt(), 0xFFFF9EBC.toInt(), 0xFFFFD479.toInt())
            colors.forEach { c ->
                swatches.addView(View(this@MainActivity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(c)
                        if (c == Color.WHITE) setStroke(dp(1), 0xFF6D737D.toInt())
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(42), dp(42)).apply { marginEnd = dp(10) }
                    setOnClickListener { save(settings.copy(color = c)) }
                })
            }
            swatches.addView(secondaryButton("custom") { showColorDialog() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(swatches)
        })

        root.addView(sectionLabel("background"))
        root.addView(card().apply {
            backgroundStatus = TextView(this@MainActivity).apply {
                setTextColor(TEXT)
                textSize = 16f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            }
            addView(backgroundStatus)
            addView(TextView(this@MainActivity).apply {
                text = "The chosen image is copied into VisDroid, so the wallpaper keeps working even if the original file moves."
                setTextColor(SUBTLE)
                textSize = 13f
                setPadding(0, dp(5), 0, dp(10))
            })
            val row = LinearLayout(this@MainActivity).apply { orientation = LinearLayout.HORIZONTAL }
            row.addView(secondaryButton("choose image") { chooseImage() }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = dp(8) }
            })
            row.addView(secondaryButton("clear") {
                BackgroundStore.clear(this@MainActivity)
                preview.reloadBackground()
                refreshBackgroundUi()
            }.apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, .55f)
            })
            addView(row)
            addView(slider("background dim", 75, (settings.backgroundDim * 100).toInt(), { "$it%" }) {
                save(settings.copy(backgroundDim = it / 100f))
            })
        })

        root.addView(TextView(this).apply {
            text = "VisDroid uses Android's output-mix FFT visualizer. Protected/private audio can be withheld by Android, and some device vendors may implement the audio effect differently."
            setTextColor(SUBTLE)
            textSize = 12f
            setPadding(2, dp(18), 2, 0)
        })

        refreshPermissionUi()
        refreshBackgroundUi()
        return scroll
    }

    private fun startPreviewAnalyzer() {
        analyzer?.stop()
        analyzer = null
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        analyzer = AudioAnalyzer(this, { settings }) { data ->
            runOnUiThread { preview.setAudioLevels(data) }
        }.also { it.start() }
    }

    private fun save(newSettings: VisSettings) {
        settings = newSettings
        SettingsStore.save(this, settings)
        preview.updateSettings(settings)
    }

    private fun refreshPermissionUi() {
        if (!::permissionStatus.isInitialized) return
        val granted = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        permissionStatus.text = if (granted) "audio access: ready" else "audio access: permission needed"
        permissionButton.text = if (granted) "permission settings" else "grant audio access"
    }

    private fun refreshBackgroundUi() {
        if (!::backgroundStatus.isInitialized) return
        backgroundStatus.text = if (BackgroundStore.exists(this)) "custom image selected" else "using VisDroid gradient"
    }

    private fun chooseImage() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
        }
        startActivityForResult(intent, REQ_IMAGE)
    }

    private fun openWallpaperPreview() {
        val component = ComponentName(this, VisDroidWallpaperService::class.java)
        val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
            putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, component)
        }
        try {
            startActivity(direct)
        } catch (_: ActivityNotFoundException) {
            startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
        }
    }

    private fun showColorDialog() {
        val current = settings.color
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(10), dp(20), 0)
        }
        val sample = View(this).apply {
            background = roundedDrawable(current, 12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(10) }
        }
        body.addView(sample)

        val r = colorSlider("red", Color.red(current), body)
        val g = colorSlider("green", Color.green(current), body)
        val b = colorSlider("blue", Color.blue(current), body)
        val listener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                sample.background = roundedDrawable(Color.rgb(r.progress, g.progress, b.progress), 12)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
        r.setOnSeekBarChangeListener(listener)
        g.setOnSeekBarChangeListener(listener)
        b.setOnSeekBarChangeListener(listener)

        AlertDialog.Builder(this)
            .setTitle("custom bar color")
            .setView(body)
            .setNegativeButton("cancel", null)
            .setPositiveButton("apply") { _, _ -> save(settings.copy(color = Color.rgb(r.progress, g.progress, b.progress))) }
            .show()
    }

    private fun colorSlider(label: String, initial: Int, parent: LinearLayout): SeekBar {
        parent.addView(TextView(this).apply {
            text = label
            setTextColor(TEXT)
            textSize = 13f
        })
        return SeekBar(this).apply {
            max = 255
            progress = initial
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(TEXT)
            parent.addView(this)
        }
    }

    private fun updateSideButtons(buttons: List<Pair<BarSide, Button>>) {
        buttons.forEach { (side, button) ->
            button.background = roundedDrawable(if (settings.side == side) 0xFF384760.toInt() else CARD_ALT, 12)
            button.setTextColor(if (settings.side == side) Color.WHITE else 0xFFD0D4DB.toInt())
        }
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text
        setTextColor(SUBTLE)
        textSize = 13f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setPadding(dp(2), dp(24), 0, dp(8))
    }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = roundedDrawable(CARD, 22)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun primaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(0xFF111318.toInt())
        backgroundTintList = ColorStateList.valueOf(0xFFF0F3FA.toInt())
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun secondaryButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 14f
        setTextColor(TEXT)
        backgroundTintList = ColorStateList.valueOf(CARD_ALT)
        setOnClickListener { onClick() }
    }

    private fun slider(title: String, maxValue: Int, initial: Int, formatter: (Int) -> String, onChange: (Int) -> Unit): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(7), 0, dp(7))
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val titleView = TextView(this).apply {
            text = title
            setTextColor(TEXT)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val value = TextView(this).apply {
            text = formatter(initial.coerceIn(0, maxValue))
            setTextColor(SUBTLE)
            textSize = 13f
        }
        row.addView(titleView)
        row.addView(value)
        box.addView(row)
        box.addView(SeekBar(this).apply {
            max = maxValue
            progress = initial.coerceIn(0, maxValue)
            progressTintList = ColorStateList.valueOf(ACCENT)
            thumbTintList = ColorStateList.valueOf(TEXT)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    value.text = formatter(progress)
                    if (fromUser) onChange(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        })
        return box
    }

    @Suppress("DEPRECATION")
    private fun toggleRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit): View {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(2))
            addView(TextView(this@MainActivity).apply {
                text = title
                setTextColor(TEXT)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(Switch(this@MainActivity).apply {
                isChecked = checked
                setOnCheckedChangeListener { _: CompoundButton, value: Boolean -> onChange(value) }
            })
        }
    }

    private fun roundedDrawable(color: Int, radiusDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + .5f).toInt()
}

private class SpectrumPreviewView(context: android.content.Context) : View(context) {
    private var settings = SettingsStore.load(context)
    private var levels = FloatArray(settings.barCount)
    private var backgroundBitmap: Bitmap? = BackgroundStore.load(context)
    private var hasAudio = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f

    init {
        background = GradientDrawable().apply {
            cornerRadius = 24f * resources.displayMetrics.density
            setColor(0xFF11141A.toInt())
        }
        clipToOutline = true
    }

    fun updateSettings(newSettings: VisSettings) {
        settings = newSettings
        if (levels.size != settings.barCount) levels = FloatArray(settings.barCount)
        invalidate()
    }

    fun setAudioLevels(data: FloatArray) {
        levels = data
        hasAudio = true
        invalidate()
    }

    fun reloadBackground() {
        backgroundBitmap?.recycle()
        backgroundBitmap = BackgroundStore.load(context)
        invalidate()
    }

    override fun onDetachedFromWindow() {
        backgroundBitmap?.recycle()
        backgroundBitmap = null
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        drawBackdrop(canvas)
        if (!hasAudio) {
            phase += .11f
            levels = FloatArray(settings.barCount) { i ->
                val a = (sin(phase + i * .37f) + 1f) * .5f
                val b = (sin(phase * .63f + i * .17f) + 1f) * .5f
                (.08f + a * b * .72f).coerceIn(0f, 1f)
            }
        }
        BarRenderer.draw(canvas, levels, settings, resources.displayMetrics.density, paint)
        postInvalidateDelayed(33L)
    }

    private fun drawBackdrop(canvas: Canvas) {
        val bmp = backgroundBitmap
        if (bmp == null || bmp.isRecycled) {
            paint.shader = LinearGradient(0f, 0f, width.toFloat(), height.toFloat(), intArrayOf(0xFF0B0D10.toInt(), 0xFF252D3A.toInt()), null, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.shader = null
        } else {
            val scale = maxOf(width.toFloat() / bmp.width, height.toFloat() / bmp.height)
            val dw = bmp.width * scale
            val dh = bmp.height * scale
            val left = (width - dw) / 2f
            val top = (height - dh) / 2f
            paint.alpha = 255
            canvas.drawBitmap(bmp, null, RectF(left, top, left + dw, top + dh), paint)
        }
        if (settings.backgroundDim > 0f) {
            paint.shader = null
            paint.color = Color.BLACK
            paint.alpha = (settings.backgroundDim * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            paint.alpha = 255
        }
    }
}
