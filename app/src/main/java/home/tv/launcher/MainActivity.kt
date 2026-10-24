package home.tv.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var homeSignal by mutableIntStateOf(0)
    private var shelf by mutableStateOf<List<ShelfItem>>(emptyList())
    private var shelfLoadedAt = 0L
    private lateinit var store: OrderStore
    private lateinit var tmdb: TmdbClient

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = OrderStore(this)
        tmdb = TmdbClient(this)
        takeKey(intent)
        // Back на главном экране не должен никуда уводить
        onBackPressedDispatcher.addCallback(this) {}

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        registerReceiver(packageReceiver, filter, RECEIVER_NOT_EXPORTED)

        setContent {
            HomeScreen(
                apps = apps,
                shelf = shelf,
                homeSignal = homeSignal,
                onLaunch = { launchApp(this, it) },
                onOpenShelf = { openInNum(this, it) },
                onReorder = { reordered ->
                    apps = reordered
                    store.save(reordered.map { it.pkg })
                },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        reload()
        if (System.currentTimeMillis() - shelfLoadedAt > SHELF_TTL) reloadShelf()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (takeKey(intent)) reloadShelf()
        homeSignal++
    }

    // adb shell am start -n home.tv.launcher/.MainActivity --es tmdb_key <ключ>
    private fun takeKey(intent: Intent): Boolean {
        val key = intent.getStringExtra("tmdb_key") ?: return false
        tmdb.key = key
        return true
    }

    private fun reloadShelf() {
        lifecycleScope.launch {
            val items = runCatching { tmdb.trending() }.getOrDefault(emptyList())
            if (items.isNotEmpty()) {
                shelf = items
                shelfLoadedAt = System.currentTimeMillis()
            }
        }
    }

    override fun onDestroy() {
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    private companion object {
        const val SHELF_TTL = 3 * 60 * 60 * 1000L
    }

    private fun reload() {
        lifecycleScope.launch {
            apps = withContext(Dispatchers.Default) { sortByOrder(loadApps(this@MainActivity), store.load()) }
        }
    }
}
