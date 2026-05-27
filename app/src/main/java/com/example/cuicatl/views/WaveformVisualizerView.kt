package com.example.cuicatl.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.View

/**
 * WaveformVisualizerView
 * Conecta con MediaPlayer.audioSessionId para mostrar la forma de onda real en vivo.
 * Diseño glass: gradiente neon cyan -> neon purple con glow.
 */
class WaveformVisualizerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private var visualizer: Visualizer? = null
    private var bytes: ByteArray? = null

    private val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 12f
        alpha = 80
    }
    private val midLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3300E5FF
        strokeWidth = 1f
    }

    private val path = Path()

    fun attach(audioSessionId: Int) {
        release()
        if (audioSessionId == 0) return
        try {
            val v = Visualizer(audioSessionId)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    bytes = waveform
                    postInvalidateOnAnimation()
                }
                override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
            }, Visualizer.getMaxCaptureRate() / 2, true, false)
            v.enabled = true
            visualizer = v
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun release() {
        try {
            visualizer?.enabled = false
            visualizer?.release()
        } catch (_: Exception) {}
        visualizer = null
        bytes = null
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val shader = LinearGradient(
            0f, 0f, w.toFloat(), 0f,
            intArrayOf(0xFF00E5FF.toInt(), 0xFF81D4FA.toInt(), 0xFFD500F9.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        wavePaint.shader = shader
        glowPaint.shader = shader
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val mid = h / 2f
        canvas.drawLine(0f, mid, w, mid, midLinePaint)

        val data = bytes
        path.reset()
        if (data == null || data.isEmpty()) {
            // idle: linea senoidal sutil
            path.moveTo(0f, mid)
            val steps = 80
            for (i in 0..steps) {
                val x = w * i / steps
                val y = mid + (h / 8f) * kotlin.math.sin((i * 6.2831853 / steps).toFloat()).toFloat()
                path.lineTo(x, y)
            }
        } else {
            val step = w / (data.size - 1).coerceAtLeast(1)
            for (i in data.indices) {
                val b = (data[i].toInt() and 0xFF) - 128
                val y = mid + (b / 128f) * (h * 0.45f)
                if (i == 0) path.moveTo(0f, y) else path.lineTo(i * step, y)
            }
        }
        canvas.drawPath(path, glowPaint)
        canvas.drawPath(path, wavePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }
}
