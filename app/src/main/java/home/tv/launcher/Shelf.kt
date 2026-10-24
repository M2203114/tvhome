package home.tv.launcher

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.Cache
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.dnsoverhttps.DnsOverHttps
import org.json.JSONObject
import java.io.File
import java.net.InetAddress
import java.util.concurrent.TimeUnit

data class ShelfItem(
    val id: Int,
    val type: String,
    val title: String,
    val year: String,
    val overview: String,
    val rating: Double,
    val backdrop: ImageBitmap,
)

/** Новинки недели из TMDB для верхней полки. Ключ задаётся через adb, см. README. */
class TmdbClient(private val context: Context) {
    private val prefs = context.getSharedPreferences("tmdb", Context.MODE_PRIVATE)

    var key: String?
        get() = prefs.getString("key", null)?.takeIf { it.isNotBlank() }
        set(value) = prefs.edit().putString("key", value).apply()

    private val client: OkHttpClient by lazy {
        val bootstrap = OkHttpClient.Builder()
            .cache(Cache(File(context.cacheDir, "http"), 64L * 1024 * 1024))
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
        // TMDB гео-DNS отдаёт российским резолверам 127.0.0.1, сам сервер доступен.
        // Cloudflare DoH не передаёт ECS и возвращает настоящие адреса.
        val doh = DnsOverHttps.Builder()
            .client(bootstrap)
            .url("https://cloudflare-dns.com/dns-query".toHttpUrl())
            .bootstrapDnsHosts(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("1.0.0.1"))
            .build()
        val dns = Dns { host ->
            if (host.endsWith("tmdb.org") || host.endsWith("themoviedb.org")) doh.lookup(host) else Dns.SYSTEM.lookup(host)
        }
        bootstrap.newBuilder().dns(dns).build()
    }

    suspend fun trending(): List<ShelfItem> = withContext(Dispatchers.IO) {
        val key = key ?: return@withContext emptyList()
        val request = Request.Builder()
            .url("https://api.themoviedb.org/3/trending/all/week?language=ru-RU")
            .apply {
                // Поддерживаем и v3 api_key, и v4 read access token (длинный JWT)
                if (key.length > 40) header("Authorization", "Bearer $key")
                else url("https://api.themoviedb.org/3/trending/all/week?language=ru-RU&api_key=$key")
            }
            .build()
        val body = client.newCall(request).execute().use { if (it.isSuccessful) it.body.string() else null }
            ?: return@withContext emptyList()
        val results = JSONObject(body).getJSONArray("results")
        val raw = (0 until results.length()).map { results.getJSONObject(it) }
            .filter { it.optString("media_type") in setOf("movie", "tv") && it.optString("backdrop_path").isNotEmpty() }
            .take(10)
        coroutineScope {
            raw.map { o ->
                async {
                    val image = backdrop(o.getString("backdrop_path")) ?: return@async null
                    ShelfItem(
                        id = o.getInt("id"),
                        type = o.getString("media_type"),
                        title = o.optString("title").ifEmpty { o.optString("name") },
                        year = (o.optString("release_date").ifEmpty { o.optString("first_air_date") }).take(4),
                        overview = o.optString("overview"),
                        rating = o.optDouble("vote_average", 0.0),
                        backdrop = image,
                    )
                }
            }.awaitAll().filterNotNull()
        }
    }

    private fun backdrop(path: String): ImageBitmap? = runCatching {
        val request = Request.Builder().url("https://image.tmdb.org/t/p/w1280$path").build()
        client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            val bytes = r.body.bytes()
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
        }
    }.getOrNull()
}

fun openInNum(context: Context, item: ShelfItem) {
    val uri = Uri.parse("https://www.themoviedb.org/${item.type}/${item.id}")
    val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(Intent(intent).setPackage("ru.yourok.num")) }
        .onFailure { runCatching { context.startActivity(intent) } }
}
