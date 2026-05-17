package com.example.stzx_family

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.random.Random

/**
 * 微光粒子漂浮背景 View
 * 稀疏半透明小圆点缓慢向上漂浮，营造高级感
 */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val particles = mutableListOf<Particle>()
    private val random = Random(System.currentTimeMillis())
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 16L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            updateParticles()
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            initParticles(w, h)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 绘制背景色
        canvas.drawColor(Color.parseColor("#FFF5F5F7"))
        // 绘制粒子
        particles.forEach { p ->
            paint.color = p.color
            paint.alpha = (p.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawCircle(p.x, p.y, p.radius, paint)
        }
    }

    private fun initParticles(width: Int, height: Int) {
        particles.clear()
        val count = 12 + random.nextInt(5) // 12-16 个粒子
        repeat(count) {
            particles.add(createParticle(width, height))
        }
    }

    /**
     * 生成偏向屏幕上半部分的 Y 坐标
     * 70% 概率在上半部分，30% 在下半部分
     */
    private fun generateY(h: Int): Float {
        return if (random.nextFloat() < 0.7f) {
            // 70%：上半部分（0 ~ h*0.55）
            random.nextFloat() * h * 0.55f
        } else {
            // 30%：下半部分（h*0.55 ~ h）
            h * 0.55f + random.nextFloat() * h * 0.45f
        }
    }

    private fun createParticle(w: Int, h: Int): Particle {
        val y = generateY(h)
        val colors = listOf(
            Color.parseColor("#FFD1D1D6"), // 浅灰
            Color.parseColor("#FFE5E5EA"), // 更浅
            Color.parseColor("#FFF2F2F7")  // 最浅
        )
        return Particle(
            x = random.nextFloat() * w,
            y = y,
            radius = dpToPx(2f + random.nextFloat() * 2.5f), // 2-4.5dp
            alpha = 0.2f + random.nextFloat() * 0.3f,        // 0.2-0.5
            color = colors[random.nextInt(colors.size)],
            speedY = 0.3f + random.nextFloat() * 0.5f,
            speedX = (random.nextFloat() - 0.5f) * 0.3f      // 轻微左右摆动
        )
    }

    private fun updateParticles() {
        val h = height
        val w = width
        if (w <= 0 || h <= 0) return
        particles.forEach { p ->
            p.y -= p.speedY
            p.x += p.speedX
            // 左右边界反弹
            if (p.x < -p.radius || p.x > w + p.radius) {
                p.speedX *= -1
            }
            // 飘出顶部后重置，70% 概率在上半部分重生
            if (p.y < -p.radius * 3) {
                p.y = generateY(h)
                p.x = random.nextFloat() * w
                p.speedX = (random.nextFloat() - 0.5f) * 0.3f
            }
        }
    }

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        val radius: Float,
        val alpha: Float,
        val color: Int,
        var speedY: Float,
        var speedX: Float
    )
}
