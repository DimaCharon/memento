package ru.memento.ui.view

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import ru.memento.R

/**
 * Кольцо прогресса как на сайте: тусклая дорожка + светлая дуга с градиентом.
 * Рисуется вручную — быстро и одинаково на любом экране.
 */
class RingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#26FFFFFF")
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val rect = RectF()

    private var stroke = context.resources.getDimension(R.dimen.s2)
    private var progress = 0f
    private var animatedProgress = 0f
    private var animator: ValueAnimator? = null

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.RingView)
        stroke = a.getDimension(R.styleable.RingView_ringStroke, stroke)
        trackPaint.strokeWidth = stroke
        arcPaint.strokeWidth = stroke
        a.recycle()
    }

    fun setProgress(value: Float, animate: Boolean = true) {
        val target = value.coerceIn(0f, 1f)
        progress = target
        animator?.cancel()
        if (!animate || !isAttachedToWindow) {
            animatedProgress = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(animatedProgress, target).apply {
            duration = 620
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val inset = stroke / 2f
        rect.set(inset, inset, w - inset, h - inset)
        arcPaint.shader = LinearGradient(
            rect.left, rect.top, rect.right, rect.bottom,
            intArrayOf(
                Color.parseColor("#E6ECEEF3"),
                Color.parseColor("#B3AAB2C6"),
                Color.parseColor("#99CFD6E4"),
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawArc(rect, 0f, 360f, false, trackPaint)
        if (animatedProgress > 0f) {
            canvas.drawArc(rect, -90f, 360f * animatedProgress, false, arcPaint)
        }
    }

    override fun onDetachedFromWindow() {
        animator?.cancel()
        super.onDetachedFromWindow()
    }
}
