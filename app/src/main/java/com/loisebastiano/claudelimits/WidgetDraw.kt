package com.loisebastiano.claudelimits

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Resolved appearance settings. */
class Look(
    val style: Style, val traffic: Boolean, val accent: Int,
    val bg: Int, val opacity: Int, val text: Int,
    val showHeader: Boolean, val showReset: Boolean, val radius: Float,
) {
    companion object {
        fun from(c: Context): Look {
            val bgSetting = Prefs.background(c)
            val bg = if (bgSetting != 0) bgSetting else if (Items.isNight(c)) 0xFF1F1F22.toInt() else 0xFFFFFFFF.toInt()
            val opacity = Prefs.opacity(c)
            val textSetting = Prefs.textColor(c)
            val text = when {
                textSetting != 0 -> textSetting
                opacity < 45 -> 0xFFFFFFFF.toInt() // mostly see-through: wallpaper behind, use white with shadow
                luminance(bg) > 0.5 -> 0xFF1C1B1F.toInt()
                else -> 0xFFF2F0F4.toInt()
            }
            return Look(Prefs.style(c), Prefs.traffic(c), Prefs.accent(c), bg, opacity, text,
                Prefs.showHeader(c), Prefs.showReset(c), Items.cornerRadiusPx(c))
        }

        fun luminance(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
    }
}

/**
 * Draws the whole widget into one bitmap sized exactly like the widget, so every style
 * can scale freely with the widget size and the number of items.
 */
class WidgetDraw(
    private val c: Context, private val look: Look, private val w: Float, private val h: Float, private val u: Float,
) {
    private val bmp = Bitmap.createBitmap(max(1, w.toInt()), max(1, h.toInt()), Bitmap.Config.ARGB_8888)
    private val cv = Canvas(bmp)
    private val shadow = look.opacity < 45
    private val primary = look.text
    private val secondary = withAlpha(look.text, 0xB0)
    private val track = withAlpha(look.text, 0x30)
    private val regular = Typeface.create("sans-serif", Typeface.NORMAL)
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val bold = Typeface.create("sans-serif", Typeface.BOLD)

    private fun withAlpha(c: Int, a: Int) = (c and 0x00FFFFFF) or (a shl 24)

    private fun textPaint(size: Float, color: Int, face: Typeface, align: Paint.Align = Paint.Align.LEFT) =
        TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = face; textAlign = align
            if (shadow) setShadowLayer(2.5f * u, 0f, 0.5f * u, 0x99000000.toInt())
        }

    private fun fill(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }

    private fun colorFor(used: Double) = if (!look.traffic) look.accent else when {
        used < 50 -> Palette.GREEN
        used < 80 -> Palette.ORANGE
        else -> Palette.RED
    }

    private fun pct(used: Double) = "${Math.round(used.coerceIn(0.0, 100.0))}%"

    private fun fit(text: String, p: TextPaint, maxW: Float) =
        TextUtils.ellipsize(text, p, max(0f, maxW), TextUtils.TruncateAt.END).toString()

    /** Draws text so that its vertical center is at [cy]. */
    private fun textCentered(s: String, x: Float, cy: Float, p: Paint) {
        val fm = p.fontMetrics
        cv.drawText(s, x, cy - (fm.ascent + fm.descent) / 2, p)
    }

    fun render(title: String, stamp: String, items: List<Item>, message: String?): Bitmap {
        val pad = (min(w, h) * 0.07f).coerceIn(8 * u, 16 * u)
        cv.drawRoundRect(RectF(0f, 0f, w, h), look.radius, look.radius,
            fill(withAlpha(look.bg, look.opacity * 255 / 100)))
        var top = pad
        if (look.showHeader) {
            val hs = (min(w, h) * 0.085f).coerceIn(11 * u, 15 * u)
            val tp = textPaint(hs, primary, bold)
            val sp = textPaint(hs * 0.85f, secondary, regular, Paint.Align.RIGHT)
            val stampW = sp.measureText(stamp)
            val cy = top + hs * 0.6f
            textCentered(fit(title, tp, w - 2 * pad - stampW - 8 * u), pad, cy, tp)
            textCentered(stamp, w - pad, cy, sp)
            top += hs * 1.2f + 6 * u
        }
        val area = RectF(pad, top, w - pad, h - pad)
        when {
            message != null -> drawMessage(message, area)
            items.isEmpty() -> drawMessage(c.getString(R.string.w_no_data), area)
            look.style == Style.RING || look.style == Style.BATTERY -> drawGrid(items, area)
            look.style == Style.VBAR -> drawColumns(items, area)
            else -> drawRows(items, area)
        }
        return bmp
    }

    private fun drawMessage(msg: String, a: RectF) {
        val p = textPaint((min(w, h) * 0.09f).coerceIn(12 * u, 16 * u), primary, medium)
        val lay = StaticLayout.Builder.obtain(msg, 0, msg.length, p, a.width().toInt())
            .setAlignment(Layout.Alignment.ALIGN_CENTER).setMaxLines(4).setEllipsize(TextUtils.TruncateAt.END).build()
        cv.save()
        cv.translate(a.left, a.centerY() - lay.height / 2f)
        lay.draw(cv)
        cv.restore()
    }

    // ---------- Rings and batteries: grid that maximizes the shape size ----------
    private fun drawGrid(items: List<Item>, a: RectF) {
        val n = items.size
        val lines = if (look.showReset) 2 else 1
        val ring = look.style == Style.RING
        val widthFactor = if (ring) 1.0f else 0.9f
        var best = 0f; var bestCols = 1
        for (cols in 1..n) {
            val rows = ceil(n / cols.toFloat()).toInt()
            val cw = a.width() / cols; val ch = a.height() / rows
            val d = min(cw * 0.9f / widthFactor, ch / (1.06f + 0.24f * lines))
            if (d > best) { best = d; bestCols = cols }
        }
        val cols = bestCols
        val rows = ceil(n / cols.toFloat()).toInt()
        val cw = a.width() / cols; val ch = a.height() / rows
        val labelSize = (best * 0.17f).coerceIn(9 * u, 16 * u)
        val resetSize = labelSize * 0.86f
        val gap = best * 0.06f
        val textH = labelSize * 1.25f + if (look.showReset) resetSize * 1.25f else 0f
        val d = min(best, ch - gap - textH).coerceAtLeast(8 * u)
        val blockH = d + gap + textH
        val lp = textPaint(labelSize, primary, medium, Paint.Align.CENTER)
        val rp = textPaint(resetSize, secondary, regular, Paint.Align.CENTER)
        items.forEachIndexed { i, it ->
            val r = i / cols
            val inRow = if (r == rows - 1) n - r * cols else cols
            val col = i % cols
            val rowLeft = a.left + (a.width() - inRow * cw) / 2
            val cx = rowLeft + cw * (col + 0.5f)
            val y0 = a.top + ch * r + (ch - blockH) / 2
            val shapeCy = y0 + d / 2
            if (ring) drawRing(it, cx, shapeCy, d) else drawBattery(it, cx, shapeCy, d)
            var ty = y0 + d + gap + labelSize * 0.62f
            textCentered(fit(it.label, lp, cw - 4 * u), cx, ty, lp)
            if (look.showReset) {
                ty += labelSize * 0.62f + resetSize * 0.66f
                textCentered(fit(it.resetsAt?.let { t -> "↺ " + Items.untilReset(c, t) } ?: " ", rp, cw - 4 * u), cx, ty, rp)
            }
        }
    }

    private fun drawRing(it: Item, cx: Float, cy: Float, d: Float) {
        val stroke = d * 0.1f
        val r = d / 2 - stroke / 2
        val oval = RectF(cx - r, cy - r, cx + r, cy + r)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }
        p.color = track
        cv.drawArc(oval, 0f, 360f, false, p)
        val sweep = (360f * it.used / 100f).toFloat().coerceIn(0f, 360f)
        if (sweep > 0) { p.color = colorFor(it.used); cv.drawArc(oval, -90f, sweep, false, p) }
        textCentered(pct(it.used), cx, cy, textPaint(d * 0.24f, primary, bold, Paint.Align.CENTER))
    }

    /** Vertical battery that empties as you use the limit; the number is the used share. */
    private fun drawBattery(it: Item, cx: Float, cy: Float, d: Float) {
        val bw = d * 0.56f; val capH = d * 0.07f
        val body = RectF(cx - bw / 2, cy - d / 2 + capH, cx + bw / 2, cy + d / 2)
        val sw = d * 0.04f
        val rad = bw * 0.16f
        cv.drawRoundRect(RectF(cx - bw * 0.2f, cy - d / 2, cx + bw * 0.2f, cy - d / 2 + capH + rad), rad / 2, rad / 2, fill(secondary))
        cv.drawRoundRect(body, rad, rad, fill(track))
        val inner = RectF(body.left + sw * 1.5f, body.top + sw * 1.5f, body.right - sw * 1.5f, body.bottom - sw * 1.5f)
        val left = (100.0 - it.used).coerceIn(0.0, 100.0) / 100.0
        if (left > 0) {
            val top = inner.bottom - (inner.height() * left).toFloat()
            cv.drawRoundRect(RectF(inner.left, top, inner.right, inner.bottom), rad * 0.6f, rad * 0.6f, fill(colorFor(it.used)))
        }
        val p = textPaint(bw * 0.36f, primary, bold, Paint.Align.CENTER)
        p.setShadowLayer(2f * u, 0f, 0.5f * u, if (Look.luminance(primary) > 0.5) 0xAA000000.toInt() else 0x66FFFFFF)
        textCentered(pct(it.used), cx, body.centerY(), p)
    }

    // ---------- Vertical bars: one column per item ----------
    private fun drawColumns(items: List<Item>, a: RectF) {
        val n = items.size
        val colW = a.width() / n
        val pctSize = (colW * 0.24f).coerceIn(10 * u, 20 * u).coerceAtMost(a.height() * 0.14f)
        val labelSize = (colW * 0.17f).coerceIn(8.5f * u, 14 * u).coerceAtMost(a.height() * 0.11f)
        val resetSize = labelSize * 0.86f
        val barW = (colW * 0.42f).coerceIn(6 * u, 40 * u)
        val pp = textPaint(pctSize, primary, bold, Paint.Align.CENTER)
        val lp = textPaint(labelSize, primary, medium, Paint.Align.CENTER)
        val rp = textPaint(resetSize, secondary, regular, Paint.Align.CENTER)
        val bottomText = labelSize * 1.3f + if (look.showReset) resetSize * 1.3f else 0f
        val barTop = a.top + pctSize * 1.35f
        val barBottom = a.bottom - bottomText - 4 * u
        items.forEachIndexed { i, it ->
            val cx = a.left + colW * (i + 0.5f)
            textCentered(pct(it.used), cx, a.top + pctSize * 0.6f, pp)
            val bar = RectF(cx - barW / 2, barTop, cx + barW / 2, max(barTop + barW, barBottom))
            val rad = barW * 0.3f
            cv.drawRoundRect(bar, rad, rad, fill(track))
            val fh = (bar.height() * it.used.coerceIn(0.0, 100.0) / 100.0).toFloat()
            if (fh > 0) cv.drawRoundRect(RectF(bar.left, bar.bottom - max(fh, rad * 2), bar.right, bar.bottom), rad, rad, fill(colorFor(it.used)))
            var ty = bar.bottom + 4 * u + labelSize * 0.65f
            textCentered(fit(it.label, lp, colW - 3 * u), cx, ty, lp)
            if (look.showReset) {
                ty += labelSize * 0.65f + resetSize * 0.65f
                textCentered(fit(it.resetsAt?.let { t -> Items.untilReset(c, t) } ?: " ", rp, colW - 3 * u), cx, ty, rp)
            }
        }
    }

    // ---------- Horizontal bars: rows, in more columns when the widget is wide and short ----------
    private fun drawRows(items: List<Item>, a: RectF) {
        val n = items.size
        val minRow = 30 * u
        val maxRows = max(1, (a.height() / minRow).toInt())
        var cols = ceil(n / maxRows.toFloat()).toInt().coerceAtLeast(1)
        if (cols == 1 && n >= 4 && a.width() > a.height() * 2.6f) cols = 2
        val rows = ceil(n / cols.toFloat()).toInt()
        val colGap = 14 * u
        val colW = (a.width() - colGap * (cols - 1)) / cols
        val rowH = min(a.height() / rows, 58 * u)
        val y0 = a.top + (a.height() - rowH * rows) / 2
        val textSize = (rowH * 0.3f).coerceIn(10 * u, 16 * u)
        val barH = (rowH * 0.2f).coerceIn(5 * u, 14 * u)
        val lp = textPaint(textSize, primary, medium)
        val pp = textPaint(textSize, primary, bold, Paint.Align.RIGHT)
        val rp = textPaint(textSize * 0.86f, secondary, regular, Paint.Align.RIGHT)
        items.forEachIndexed { i, it ->
            val col = i / rows; val r = i % rows
            val x0 = a.left + col * (colW + colGap)
            val rowTop = y0 + r * rowH
            val textCy = rowTop + (rowH - barH) / 2 - 1 * u
            val p = pct(it.used)
            val pw = pp.measureText(p)
            cv.drawText(p, x0 + colW, textCy - (pp.fontMetrics.ascent + pp.fontMetrics.descent) / 2, pp)
            var right = x0 + colW - pw - 8 * u
            if (look.showReset && it.resetsAt != null) {
                val rs = "↺ " + Items.untilReset(c, it.resetsAt)
                val rw = rp.measureText(rs)
                if (rw < colW * 0.45f) {
                    textCentered(rs, right, textCy, rp)
                    right -= rw + 8 * u
                }
            }
            textCentered(fit(it.label, lp, right - x0), x0, textCy, lp)
            val barTop = rowTop + rowH - barH - (rowH - barH * 2.6f).coerceAtLeast(0f) / 4
            val bar = RectF(x0, barTop, x0 + colW, barTop + barH)
            cv.drawRoundRect(bar, barH / 2, barH / 2, fill(track))
            val fw = (colW * it.used.coerceIn(0.0, 100.0) / 100.0).toFloat()
            if (fw > 0) cv.drawRoundRect(RectF(x0, bar.top, x0 + max(fw, barH), bar.bottom), barH / 2, barH / 2, fill(colorFor(it.used)))
        }
    }
}
