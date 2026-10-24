package home.tv.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

data class AppEntry(
    val pkg: String,
    val component: ComponentName,
    val label: String,
    val banner: ImageBitmap?,
    val icon: ImageBitmap?,
)

// Стоковые лаунчеры Ugoos: показывать их среди приложений незачем
private val HIDDEN = setOf("com.uapplication.launcher", "com.android.launcher3")

private val DEFAULT_ORDER = listOf(
    "com.google.android.youtube.tv",
    "ru.kinopoisk.tv",
    "ru.yourok.num",
    "xyz.anilabx.app",
    "ru.rutube.app",
    "ru.vokino.web",
    "com.yandex.browser.tv",
    "ru.yandex.disk",
)

fun loadApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val result = LinkedHashMap<String, AppEntry>()
    // Leanback-активити первыми: у них есть баннер 16:9, у мобильных только иконка
    for ((category, leanback) in listOf(
        Intent.CATEGORY_LEANBACK_LAUNCHER to true,
        Intent.CATEGORY_LAUNCHER to false,
    )) {
        val query = Intent(Intent.ACTION_MAIN).addCategory(category)
        for (ri in pm.queryIntentActivities(query, 0)) {
            val ai = ri.activityInfo
            val pkg = ai.packageName
            if (pkg == context.packageName || pkg in HIDDEN || pkg in result) continue
            val banner = if (leanback) ai.loadBanner(pm) ?: ai.applicationInfo.loadBanner(pm) else null
            result[pkg] = AppEntry(
                pkg = pkg,
                component = ComponentName(pkg, ai.name),
                label = ri.loadLabel(pm).toString(),
                banner = banner?.render(BANNER_W, BANNER_H),
                icon = ai.loadIcon(pm)?.render(ICON_SIZE, ICON_SIZE),
            )
        }
    }
    return result.values.toList()
}

fun sortByOrder(apps: List<AppEntry>, order: List<String>): List<AppEntry> {
    val rank = order.withIndex().associate { it.value to it.index }
    return apps.sortedWith(compareBy<AppEntry> { rank[it.pkg] ?: Int.MAX_VALUE }.thenBy { it.label.lowercase() })
}

fun launchApp(context: Context, app: AppEntry) {
    val intent = Intent(Intent.ACTION_MAIN)
        .setComponent(app.component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { context.startActivity(intent) }
}

class OrderStore(context: Context) {
    private val prefs = context.getSharedPreferences("home", Context.MODE_PRIVATE)

    fun load(): List<String> =
        prefs.getString(KEY, null)?.split(',')?.filter { it.isNotEmpty() } ?: DEFAULT_ORDER

    fun save(order: List<String>) {
        prefs.edit().putString(KEY, order.joinToString(",")).apply()
    }

    private companion object {
        const val KEY = "order"
    }
}

private const val BANNER_W = 640
private const val BANNER_H = 360
private const val ICON_SIZE = 192

private fun Drawable.render(w: Int, h: Int): ImageBitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    setBounds(0, 0, w, h)
    draw(Canvas(bmp))
    return bmp.asImageBitmap()
}
