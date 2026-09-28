package io.github.xtratter.appshelf

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.util.concurrent.Executors

/** Значки приложений: рисуются в маленькие картинки в фоне и хранятся в памяти (не больше ~8 МБ). */
object Icons {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val missing = HashSet<String>()
    private val pending = HashSet<String>()
    private val worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** Значок из кэша; если его ещё нет — начинаем загрузку и позже вызываем [ready] (в главном потоке). */
    fun get(ctx: Context, pkg: String, sizePx: Int, ready: () -> Unit): Bitmap? {
        cache.get(pkg)?.let { return it }
        if (pkg in missing || !pending.add(pkg)) return null
        val app = ctx.applicationContext
        worker.execute {
            val b = try {
                val d = app.packageManager.getApplicationIcon(pkg)
                Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).also {
                    d.setBounds(0, 0, sizePx, sizePx)
                    d.draw(Canvas(it))
                }
            } catch (e: PackageManager.NameNotFoundException) {
                null    // не установлено (режим восстановления) — нарисуем букву
            }
            main.post {
                pending.remove(pkg)
                if (b != null) cache.put(pkg, b) else missing += pkg
                ready()
            }
        }
        return null
    }

    /** Список приложений изменился: заново проверяем те, которых раньше не было. */
    fun forgetMissing() = missing.clear()
}
