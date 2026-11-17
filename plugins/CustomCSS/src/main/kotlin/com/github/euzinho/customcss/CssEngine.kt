package com.github.euzinho.customcss

import android.app.Activity
import android.app.Application
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.widget.TextView
import java.lang.ref.WeakReference
import java.util.WeakHashMap

object CssKeys {
    const val CSS = "css"
    const val ENABLED = "enabled"
    const val BOOT_PENDING = "bootPending"
}

/**
 * Aplica as regras CSS sobre a árvore de views do app.
 *
 * - Percorre a janela de cada Activity (com throttle) e aplica as propriedades por seletor.
 * - Tudo é idempotente: só altera uma view quando o valor atual difere do desejado,
 *   então depois de aplicado não gera novos layouts.
 * - Guarda o estado original de cada view para poder restaurar ao desligar/alterar o CSS.
 */
object CssEngine {
    /** Views com essa tag (e tudo dentro delas) são ignoradas. Usado na tela de edição. */
    const val IGNORE_TAG = "customcss:ignore"

    private val handler = Handler(Looper.getMainLooper())

    @Volatile
    var rules: List<Rule> = emptyList()
        private set

    @Volatile
    var enabled: Boolean = false
        private set

    private var pending = false
    private var app: Application? = null
    private var lifecycle: Application.ActivityLifecycleCallbacks? = null
    private var lastActivity: WeakReference<Activity>? = null

    private val attached = WeakHashMap<Activity, ViewTreeObserver.OnGlobalLayoutListener>()
    private val saved = WeakHashMap<View, Saved>()
    private val classCache = HashMap<Class<*>, Set<String>>()
    private val idCache = HashMap<Int, String>()

    /** Aliases de classe -> nome de id do Discord (podem variar entre versões do Discord). */
    private val ALIASES = mapOf(
        "message-text" to "chat_list_adapter_item_text",
        "message-author" to "chat_list_adapter_item_text_name",
        "message-avatar" to "chat_list_adapter_item_text_avatar",
    )

    // ------------------------------------------------------------------ ciclo de vida

    fun install(application: Application) {
        if (lifecycle != null) return
        app = application
        val cb = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {
                lastActivity = WeakReference(activity)
                attach(activity)
            }
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {
                detach(activity)
            }
        }
        application.registerActivityLifecycleCallbacks(cb)
        lifecycle = cb
    }

    fun shutdown() {
        handler.removeCallbacksAndMessages(null)
        pending = false
        for (act in attached.keys.toList()) detach(act)
        restoreAll()
        lifecycle?.let { app?.unregisterActivityLifecycleCallbacks(it) }
        lifecycle = null
        app = null
        rules = emptyList()
        enabled = false
    }

    fun attach(activity: Activity) {
        lastActivity = WeakReference(activity)
        if (!attached.containsKey(activity)) {
            val decor = activity.window?.decorView ?: return
            val listener = ViewTreeObserver.OnGlobalLayoutListener { schedule() }
            decor.viewTreeObserver.addOnGlobalLayoutListener(listener)
            attached[activity] = listener
        }
        schedule()
    }

    private fun detach(activity: Activity) {
        val listener = attached.remove(activity) ?: return
        val vto = activity.window?.decorView?.viewTreeObserver
        if (vto != null && vto.isAlive) vto.removeOnGlobalLayoutListener(listener)
    }

    // ------------------------------------------------------------------ configuração

    fun configure(css: String, isEnabled: Boolean) {
        runOnMain {
            val parsed = CssParser.parse(css)
            restoreAll()
            rules = parsed.rules
            enabled = isEnabled
            schedule()
        }
    }

    fun setEnabled(isEnabled: Boolean) {
        runOnMain {
            if (!isEnabled) restoreAll()
            enabled = isEnabled
            schedule()
        }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    private fun schedule() {
        if (!enabled || pending) return
        pending = true
        handler.postDelayed(tick, 150)
    }

    private val tick = Runnable {
        pending = false
        if (!enabled) return@Runnable
        val current = rules
        for (act in attached.keys.toList()) {
            val decor = act.window?.decorView ?: continue
            try {
                walk(decor, ArrayList(), current, false)
            } catch (_: Throwable) {
            }
        }
    }

    /** Aplica regras em uma sub-árvore (usado pela pré-visualização; ignora o estado "enabled"). */
    fun applyPreview(root: View, previewRules: List<Rule>) {
        walk(root, ArrayList(), previewRules, true)
    }

    fun restoreAll() {
        val entries = saved.entries.toList()
        for (e in entries) {
            val v = e.key ?: continue
            try {
                e.value.restore(v)
            } catch (_: Throwable) {
            }
        }
        saved.clear()
    }

    // ------------------------------------------------------------------ percurso

    /** Retorna true se a sub-árvore contém uma view protegida (IGNORE_TAG). */
    private fun walk(view: View, ancestors: ArrayList<View>, rules: List<Rule>, preview: Boolean): Boolean {
        if (!preview && view.tag == IGNORE_TAG) return true
        var hasIgnored = false
        if (view is ViewGroup) {
            ancestors.add(view)
            for (i in 0 until view.childCount) {
                if (walk(view.getChildAt(i), ancestors, rules, preview)) hasIgnored = true
            }
            ancestors.removeAt(ancestors.size - 1)
        }
        try {
            val resolved = resolve(view, ancestors, rules)
            if (resolved != null) applyResolved(view, resolved, hasIgnored)
        } catch (_: Throwable) {
        }
        return hasIgnored
    }

    // ------------------------------------------------------------------ seletores

    private class Match(val spec: Int, val order: Int, val decl: Declaration)

    private fun resolve(view: View, ancestors: List<View>, rules: List<Rule>): Resolved? {
        var found: ArrayList<Match>? = null
        for (rule in rules) {
            if (rule.declarations.isEmpty()) continue
            var best = -1
            for (sel in rule.selectors) {
                if (sel.specificity > best && matchesSelector(view, ancestors, sel)) best = sel.specificity
            }
            if (best < 0) continue
            val list = found ?: ArrayList<Match>().also { found = it }
            for ((idx, d) in rule.declarations.withIndex()) {
                list.add(Match((if (d.important) 10000 else 0) + best, rule.order * 1000 + idx, d))
            }
        }
        val list = found ?: return null
        list.sortWith(compareBy<Match>({ it.spec }, { it.order }))
        val r = Resolved()
        for (m in list) r.set(m.decl)
        return r
    }

    private fun matchesSelector(view: View, ancestors: List<View>, sel: Selector): Boolean {
        val parts = sel.parts
        if (!matchesCompound(view, parts[parts.size - 1])) return false
        var ai = ancestors.size - 1
        for (pi in parts.size - 2 downTo 0) {
            while (ai >= 0 && !matchesCompound(ancestors[ai], parts[pi])) ai--
            if (ai < 0) return false
            ai--
        }
        return true
    }

    private fun matchesCompound(v: View, c: Compound): Boolean {
        if (c.type != null && !typeNames(v.javaClass).contains(c.type.lowercase())) return false
        if (c.id != null && idName(v) != c.id) return false
        for (cls in c.classes) if (!hasClass(v, cls)) return false
        return true
    }

    /** Nomes simples (minúsculos) da classe da view e de todas as superclasses. */
    private fun typeNames(cls: Class<*>): Set<String> {
        classCache[cls]?.let { return it }
        val names = HashSet<String>()
        var k: Class<*>? = cls
        while (k != null) {
            names.add(k.simpleName.lowercase())
            k = k.superclass
        }
        classCache[cls] = names
        return names
    }

    private fun idName(v: View): String? {
        val id = v.id
        if (id == View.NO_ID || (id ushr 24) != 0x7f) return null
        val cached = idCache[id]
        if (cached != null) return cached.ifEmpty { null }
        val name = try {
            v.resources.getResourceEntryName(id)
        } catch (_: Throwable) {
            ""
        }
        idCache[id] = name
        return name.ifEmpty { null }
    }

    private fun hasClass(v: View, cls: String): Boolean {
        val tag = v.tag
        if (tag is String && tag.split(' ').contains(cls)) return true
        val alias = ALIASES[cls]
        return alias != null && idName(v) == alias
    }

    // ------------------------------------------------------------------ propriedades

    private class Resolved {
        var color: Int? = null
        var bgColor: Int? = null
        var bgGradient: Gradient? = null
        var opacity: Float? = null
        var fontSize: Float? = null
        var bold: Boolean? = null
        var italic: Boolean? = null
        var family: String? = null
        var letterSpacing: Float? = null
        var radius: Float? = null
        var borderW: Float? = null
        var borderColor: Int? = null
        var pt: Float? = null
        var pr: Float? = null
        var pb: Float? = null
        var pl: Float? = null
        var hide: Int? = null
        var tint: Int? = null

        fun set(d: Declaration) {
            val v = d.value
            when (d.property) {
                "color" -> color = CssValues.parseColor(v)
                "background-color" -> {
                    bgColor = CssValues.parseColor(v)
                    bgGradient = null
                }
                "background" -> {
                    if (v.lowercase() == "none") {
                        bgColor = 0
                        bgGradient = null
                    } else {
                        val g = CssValues.parseGradient(v)
                        if (g != null) {
                            bgGradient = g
                            bgColor = null
                        } else {
                            bgColor = CssValues.parseColor(v)
                            bgGradient = null
                        }
                    }
                }
                "opacity" -> opacity = CssValues.parseNumber(v)
                "font-size" -> fontSize = CssValues.parseLength(v)
                "font-weight" -> bold = CssValues.isBold(v)
                "font-style" -> italic = v.lowercase().let { it == "italic" || it == "oblique" }
                "font-family" -> family = v.split(',')[0].trim().trim('"', '\'').lowercase().ifEmpty { null }
                "letter-spacing" -> letterSpacing = CssValues.parseLength(v)
                "border-radius" -> radius = CssValues.parseRadius(v)
                "border" -> {
                    if (v.lowercase() == "none") {
                        borderW = 0f
                    } else {
                        val b = CssValues.parseBorder(v)
                        if (b != null) {
                            borderW = b.first
                            borderColor = b.second
                        }
                    }
                }
                "padding" -> CssValues.parseBox(v)?.let {
                    pt = it[0]
                    pr = it[1]
                    pb = it[2]
                    pl = it[3]
                }
                "padding-top" -> pt = CssValues.parseLength(v)
                "padding-right" -> pr = CssValues.parseLength(v)
                "padding-bottom" -> pb = CssValues.parseLength(v)
                "padding-left" -> pl = CssValues.parseLength(v)
                "display" -> hide = if (v.lowercase() == "none") View.GONE else null
                "visibility" -> hide = v.lowercase().let { if (it == "hidden" || it == "collapse") View.INVISIBLE else null }
                "tint" -> tint = CssValues.parseColor(v)
            }
        }
    }

    /** Estado original (e o que aplicamos) de uma view, para restaurar depois. */
    private class Saved {
        var visibility: Int? = null
        var alpha: Float? = null
        var padding: IntArray? = null

        var bg: Drawable? = null
        var ownBg: Drawable? = null
        var ownBgSig: String? = null

        var outlineTouched = false
        var outlineProvider: ViewOutlineProvider? = null
        var clip = false

        var textColorTouched = false
        var textColors: ColorStateList? = null
        var textSizePx: Float? = null
        var typefaceTouched = false
        var typeface: Typeface? = null
        var ownTypeface: Typeface? = null
        var ownTypefaceSig: String? = null
        var letterSpacing: Float? = null

        var filterTouched = false
        var filter: ColorFilter? = null
        var ownFilter: ColorFilter? = null

        fun restore(v: View) {
            visibility?.let { v.visibility = it }
            alpha?.let { v.alpha = it }
            padding?.let { v.setPadding(it[0], it[1], it[2], it[3]) }
            if (ownBg != null && v.background === ownBg) v.background = bg
            if (outlineTouched) {
                v.outlineProvider = outlineProvider
                v.clipToOutline = clip
            }
            if (v is TextView) {
                val tc = textColors
                if (textColorTouched && tc != null) v.setTextColor(tc)
                textSizePx?.let { v.setTextSize(TypedValue.COMPLEX_UNIT_PX, it) }
                if (typefaceTouched) v.typeface = typeface
                letterSpacing?.let { v.letterSpacing = it }
            }
            if (v is ImageView && filterTouched) {
                val f = filter
                if (f == null) v.clearColorFilter() else v.setColorFilter(f)
            }
        }
    }

    private class RadiusOutline(val radius: Float) : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            val max = minOf(view.width, view.height) / 2f
            outline.setRoundRect(0, 0, view.width, view.height, minOf(radius, max))
        }
    }

    private fun orientationFor(angle: Int): GradientDrawable.Orientation {
        val all = arrayOf(
            GradientDrawable.Orientation.BOTTOM_TOP,
            GradientDrawable.Orientation.BL_TR,
            GradientDrawable.Orientation.LEFT_RIGHT,
            GradientDrawable.Orientation.TL_BR,
            GradientDrawable.Orientation.TOP_BOTTOM,
            GradientDrawable.Orientation.TR_BL,
            GradientDrawable.Orientation.RIGHT_LEFT,
            GradientDrawable.Orientation.BR_TL,
        )
        return all[((angle + 22) / 45) % 8]
    }

    private fun applyResolved(view: View, r: Resolved, protectedNode: Boolean) {
        val dm = view.resources.displayMetrics
        val density = dm.density
        val s = saved.getOrPut(view) { Saved() }

        // display / visibility (nunca esconde algo que contém a tela do próprio plugin)
        val hide = r.hide
        if (hide != null && !protectedNode && view.visibility != hide) {
            if (s.visibility == null) s.visibility = view.visibility
            view.visibility = hide
        }

        // opacity
        val op = r.opacity
        if (op != null) {
            val a = op.coerceIn(0f, 1f)
            if (Math.abs(view.alpha - a) > 0.001f) {
                if (s.alpha == null) s.alpha = view.alpha
                view.alpha = a
            }
        }

        // padding
        if (r.pl != null || r.pt != null || r.pr != null || r.pb != null) {
            val l = r.pl?.let { (it * density).toInt() } ?: view.paddingLeft
            val t = r.pt?.let { (it * density).toInt() } ?: view.paddingTop
            val rr = r.pr?.let { (it * density).toInt() } ?: view.paddingRight
            val b = r.pb?.let { (it * density).toInt() } ?: view.paddingBottom
            if (l != view.paddingLeft || t != view.paddingTop || rr != view.paddingRight || b != view.paddingBottom) {
                if (s.padding == null) {
                    s.padding = intArrayOf(view.paddingLeft, view.paddingTop, view.paddingRight, view.paddingBottom)
                }
                view.setPadding(l, t, rr, b)
            }
        }

        // background / border / radius
        val radiusPx = r.radius?.let { it * density }
        val borderPx = (r.borderW ?: 0f) * density
        val cur = view.background
        if (cur !== s.ownBg) s.bg = cur
        val appBg = s.bg
        val wantsBg = r.bgColor != null || r.bgGradient != null || borderPx > 0f ||
            (radiusPx != null && appBg is ColorDrawable)
        if (wantsBg) {
            val sig = "${r.bgColor}|${r.bgGradient?.signature}|$radiusPx|$borderPx|${r.borderColor}"
            if (cur !== s.ownBg || s.ownBgSig != sig) {
                val gd = GradientDrawable()
                val g = r.bgGradient
                if (g != null) {
                    gd.orientation = orientationFor(g.angle)
                    gd.setColors(g.colors)
                } else {
                    gd.setColor(r.bgColor ?: (appBg as? ColorDrawable)?.color ?: Color.TRANSPARENT)
                }
                if (radiusPx != null) gd.cornerRadius = radiusPx
                if (borderPx > 0f) gd.setStroke(maxOf(1, borderPx.toInt()), r.borderColor ?: 0xFF888888.toInt())
                s.ownBg = gd
                s.ownBgSig = sig
                view.background = gd
            }
        }

        // recorte arredondado (para ImageView/avatar etc.)
        if (radiusPx != null) {
            val provider = view.outlineProvider
            val same = provider is RadiusOutline && provider.radius == radiusPx
            if (!(same && view.clipToOutline)) {
                if (!s.outlineTouched) {
                    s.outlineTouched = true
                    s.outlineProvider = provider
                    s.clip = view.clipToOutline
                }
                view.outlineProvider = RadiusOutline(radiusPx)
                view.clipToOutline = true
            }
        }

        if (view is TextView) applyText(view, r, s, dm)

        if (view is ImageView) {
            val tint = r.tint
            if (tint != null) {
                val ownF = s.ownFilter
                val currentColorMatches = ownF != null && view.colorFilter === ownF
                if (!currentColorMatches) {
                    if (!s.filterTouched) {
                        s.filterTouched = true
                        s.filter = view.colorFilter
                    }
                    val f = PorterDuffColorFilter(tint, PorterDuff.Mode.SRC_IN)
                    s.ownFilter = f
                    view.setColorFilter(f)
                }
            }
        }
    }

    private fun applyText(tv: TextView, r: Resolved, s: Saved, dm: android.util.DisplayMetrics) {
        val color = r.color
        if (color != null && tv.currentTextColor != color) {
            if (!s.textColorTouched) {
                s.textColorTouched = true
                s.textColors = tv.textColors
            }
            tv.setTextColor(color)
        }

        val size = r.fontSize
        if (size != null) {
            val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, size, dm)
            if (Math.abs(tv.textSize - px) > 0.5f) {
                if (s.textSizePx == null) s.textSizePx = tv.textSize
                tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
            }
        }

        val ls = r.letterSpacing
        if (ls != null && Math.abs(tv.letterSpacing - ls) > 0.001f) {
            if (s.letterSpacing == null) s.letterSpacing = tv.letterSpacing
            tv.letterSpacing = ls
        }

        if (r.family != null || r.bold != null || r.italic != null) {
            if (!s.typefaceTouched) {
                s.typefaceTouched = true
                s.typeface = tv.typeface
            } else if (tv.typeface !== s.ownTypeface) {
                // o app trocou a fonte (rebind): guarda a nova como original
                s.typeface = tv.typeface
            }
            val orig = s.typeface
            val sig = "${r.family}|${r.bold}|${r.italic}"
            if (tv.typeface !== s.ownTypeface || s.ownTypefaceSig != sig) {
                val origStyle = orig?.style ?: Typeface.NORMAL
                val bold = r.bold ?: ((origStyle and Typeface.BOLD) != 0)
                val italic = r.italic ?: ((origStyle and Typeface.ITALIC) != 0)
                val style = (if (bold) Typeface.BOLD else 0) or (if (italic) Typeface.ITALIC else 0)
                val fam = r.family
                val base = if (fam != null) Typeface.create(fam, Typeface.NORMAL) else orig
                val desired = Typeface.create(base, style)
                s.ownTypeface = desired
                s.ownTypefaceSig = sig
                tv.typeface = desired
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    /** Lista tipos/ids de views da tela atual, para o usuário descobrir seletores. */
    fun dump(): String {
        val act = lastActivity?.get() ?: return "Nenhuma tela ativa para inspecionar."
        val counts = HashMap<String, Int>()
        fun visit(v: View) {
            val id = idName(v)
            if (id != null) {
                val key = v.javaClass.simpleName + "#" + id
                counts[key] = (counts[key] ?: 0) + 1
            }
            if (v is ViewGroup) for (i in 0 until v.childCount) visit(v.getChildAt(i))
        }
        visit(act.window.decorView)
        if (counts.isEmpty()) return "Nenhuma view com id encontrada."
        val sb = StringBuilder("Seletores disponíveis nesta tela (Tipo#id ×quantidade):\n")
        for (e in counts.entries.sortedByDescending { it.value }) {
            val line = "${e.key} ×${e.value}\n"
            if (sb.length + line.length > 1800) break
            sb.append(line)
        }
        return sb.toString().trimEnd()
    }
}
