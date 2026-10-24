package home.tv.launcher

import android.view.KeyEvent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val COLUMNS = 6
private val TILE_W = 168.dp
private val TILE_H = 101.dp // 5:3, как иконки tvOS
private val GAP = 32.dp
private val SIDE = 56.dp
private val SHELF = 400.dp
private val TILE_SHAPE = RoundedCornerShape(14.dp)

@Composable
fun HomeScreen(
    apps: List<AppEntry>,
    shelf: List<ShelfItem>,
    homeSignal: Int,
    onLaunch: (AppEntry) -> Unit,
    onOpenShelf: (ShelfItem) -> Unit,
    onReorder: (List<AppEntry>) -> Unit,
) {
    var shelfIndex by remember { mutableIntStateOf(0) }
    var shelfFocused by remember { mutableStateOf(false) }
    var focusedPkg by remember { mutableStateOf<String?>(null) }
    var movingPkg by remember { mutableStateOf<String?>(null) }
    val requesters = remember { mutableMapOf<String, FocusRequester>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun focus(pkg: String?) {
        pkg ?: return
        runCatching { requesters[pkg]?.requestFocus() }
    }

    fun goHome() {
        movingPkg = null
        scope.launch { listState.animateScrollToItem(0) }
        focus(apps.firstOrNull()?.pkg)
    }

    fun move(delta: Int) {
        val from = apps.indexOfFirst { it.pkg == movingPkg }
        if (from < 0) return
        val to = (from + delta).coerceIn(0, apps.lastIndex)
        if (to == from) return
        onReorder(apps.toMutableList().apply { add(to, removeAt(from)) })
    }

    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isNotEmpty() && focusedPkg == null) focus(apps.first().pkg)
    }
    // После перестановки плитка переезжает в другой ряд и теряет фокус
    LaunchedEffect(apps) { focus(movingPkg) }
    LaunchedEffect(homeSignal) { if (homeSignal > 0) goHome() }

    val focusedApp = apps.firstOrNull { it.pkg == focusedPkg }
    val current = shelf.getOrNull(shelfIndex % shelf.size.coerceAtLeast(1))
    val scrolled by remember { derivedStateOf { listState.firstVisibleItemIndex > 0 } }

    // Как Top Shelf на tvOS: пока полка не в фокусе, новинки листаются сами
    LaunchedEffect(shelfIndex, shelf.size, shelfFocused) {
        if (shelf.size > 1 && !shelfFocused) {
            delay(9_000)
            shelfIndex = (shelfIndex + 1) % shelf.size
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF14161C), Color(0xFF050506))))
            .onPreviewKeyEvent { e ->
                val k = e.nativeKeyEvent
                when {
                    k.keyCode == KeyEvent.KEYCODE_BACK -> {
                        if (k.action == KeyEvent.ACTION_UP) {
                            if (movingPkg != null) movingPkg = null else goHome()
                        }
                        true
                    }
                    movingPkg != null && k.action == KeyEvent.ACTION_DOWN -> when (k.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> { move(-1); true }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> { move(1); true }
                        KeyEvent.KEYCODE_DPAD_UP -> { move(-COLUMNS); true }
                        KeyEvent.KEYCODE_DPAD_DOWN -> { move(COLUMNS); true }
                        else -> false
                    }
                    else -> false
                }
            },
    ) {
        if (current != null) Backdrop(current.backdrop, dimmed = scrolled)
        else Ambient(focusedApp?.banner ?: focusedApp?.icon)

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item(key = "shelf") {
                if (current != null) {
                    ShelfInfo(
                        item = current,
                        index = shelfIndex % shelf.size,
                        count = shelf.size,
                        onFocused = {
                            shelfFocused = it
                            if (it) scope.launch { listState.animateScrollToItem(0) }
                        },
                        onStep = { shelfIndex = (shelfIndex + it + shelf.size) % shelf.size },
                        onOpen = { onOpenShelf(current) },
                    )
                } else {
                    Box(Modifier.fillMaxWidth().height(SHELF))
                }
            }
            itemsIndexed(apps.chunked(COLUMNS), key = { i, _ -> i }) { rowIndex, row ->
                Row(
                    Modifier.padding(start = SIDE, end = SIDE, bottom = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    row.forEach { app ->
                        Tile(
                            app = app,
                            requester = requesters.getOrPut(app.pkg) { FocusRequester() },
                            moving = app.pkg == movingPkg,
                            onFocused = {
                                focusedPkg = app.pkg
                                if (rowIndex == 0) scope.launch { listState.animateScrollToItem(0) }
                            },
                            onClick = {
                                if (movingPkg != null) movingPkg = null else onLaunch(app)
                            },
                            onLongPress = { movingPkg = if (movingPkg == null) app.pkg else null },
                        )
                    }
                }
            }
        }

        Clock(Modifier.align(Alignment.TopEnd).padding(top = 40.dp, end = SIDE))

        if (movingPkg != null) {
            BasicText(
                "Стрелками переставьте приложение, OK - готово",
                Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp),
            )
        }
    }
}

/** Кадр из новинки на весь экран; при прокрутке к сетке приложений приглушается. */
@Composable
private fun Backdrop(image: ImageBitmap, dimmed: Boolean) {
    val alpha by animateFloatAsState(if (dimmed) 0.3f else 1f, tween(500), label = "dim")
    Crossfade(targetState = image, animationSpec = tween(900), label = "backdrop") { img ->
        Image(
            bitmap = img,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(alpha),
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.55f to Color.Transparent)
        )
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.25f),
                0.3f to Color.Transparent,
                0.5f to Color.Black.copy(alpha = 0.35f),
                0.75f to Color.Black.copy(alpha = 0.9f),
                1f to Color.Black,
            )
        )
    )
}

@Composable
private fun ShelfInfo(
    item: ShelfItem,
    index: Int,
    count: Int,
    onFocused: (Boolean) -> Unit,
    onStep: (Int) -> Unit,
    onOpen: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val buttonAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(200), label = "button")
    Box(
        Modifier
            .fillMaxWidth()
            .height(SHELF)
            .onFocusChanged {
                focused = it.isFocused
                onFocused(it.isFocused)
            }
            .onPreviewKeyEvent { e ->
                val k = e.nativeKeyEvent
                when (k.keyCode) {
                    KeyEvent.KEYCODE_DPAD_LEFT -> { if (k.action == KeyEvent.ACTION_DOWN) onStep(-1); true }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> { if (k.action == KeyEvent.ACTION_DOWN) onStep(1); true }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (k.action == KeyEvent.ACTION_UP) onOpen()
                        true
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = SIDE, bottom = 36.dp).width(620.dp)) {
            val meta = listOfNotNull(
                if (item.type == "tv") "СЕРИАЛ" else "ФИЛЬМ",
                item.year.ifEmpty { null },
                if (item.rating > 0) "TMDB %.1f".format(item.rating) else null,
            ).joinToString(" · ")
            BasicText(meta, style = TextStyle(color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.2.sp))
            BasicText(
                item.title,
                Modifier.padding(top = 8.dp),
                style = TextStyle(color = Color.White, fontSize = 40.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                item.overview,
                Modifier.padding(top = 10.dp),
                style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 15.sp, lineHeight = 21.sp),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                "Смотреть в NUM",
                Modifier
                    .padding(top = 18.dp)
                    .alpha(buttonAlpha)
                    .background(Color.White, RoundedCornerShape(50))
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                style = TextStyle(color = Color.Black, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = SIDE, bottom = 48.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(count) { i ->
                Box(
                    Modifier
                        .size(if (i == index) 18.dp else 6.dp, 6.dp)
                        .background(Color.White.copy(alpha = if (i == index) 0.9f else 0.35f), RoundedCornerShape(3.dp))
                )
            }
        }
    }
}

/** Размытый баннер приложения в фокусе на весь экран, как фон верхней полки tvOS. */
@Composable
private fun Ambient(image: ImageBitmap?) {
    Crossfade(targetState = image, animationSpec = tween(600), label = "ambient") { img ->
        if (img != null) {
            Image(
                bitmap = img,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(110.dp).alpha(0.45f),
            )
        }
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.35f),
                0.45f to Color.Black.copy(alpha = 0.45f),
                1f to Color.Black.copy(alpha = 0.85f),
            )
        )
    )
}

@Composable
private fun Tile(
    app: AppEntry,
    requester: FocusRequester,
    moving: Boolean,
    onFocused: () -> Unit,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    var longFired by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, tween(220), label = "scale")
    val labelAlpha by animateFloatAsState(if (focused) 1f else 0f, tween(220), label = "label")
    val wiggle = if (moving) {
        rememberInfiniteTransition(label = "wiggle").animateFloat(
            initialValue = -1.6f,
            targetValue = 1.6f,
            animationSpec = infiniteRepeatable(tween(130), RepeatMode.Reverse),
            label = "wiggle",
        ).value
    } else 0f

    Column(Modifier.width(TILE_W), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(TILE_W, TILE_H)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    rotationZ = wiggle
                    shadowElevation = if (focused) 28.dp.toPx() else 0f
                    shape = TILE_SHAPE
                    clip = true
                }
                .background(Color(0xFF2A2D35))
                .focusRequester(requester)
                .onFocusChanged {
                    focused = it.isFocused
                    if (it.isFocused) onFocused()
                }
                .onPreviewKeyEvent { e ->
                    val k = e.nativeKeyEvent
                    when (k.keyCode) {
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                            // Удержание OK = режим перестановки, как долгое нажатие на тачпаде Apple TV
                            if (k.action == KeyEvent.ACTION_DOWN) {
                                if (k.repeatCount == 1) {
                                    longFired = true
                                    onLongPress()
                                }
                            } else if (longFired) {
                                longFired = false
                            } else {
                                onClick()
                            }
                            true
                        }
                        KeyEvent.KEYCODE_MENU -> {
                            if (k.action == KeyEvent.ACTION_UP) onLongPress()
                            true
                        }
                        else -> false
                    }
                }
                .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            if (app.banner != null) {
                Image(app.banner, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (app.icon != null) {
                Image(app.icon, app.label, Modifier.size(60.dp))
            }
            if (focused) {
                // Блик сверху, как подсветка плитки в фокусе на tvOS
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent))
                    )
                )
            }
        }
        BasicText(
            app.label,
            Modifier.padding(top = 16.dp).alpha(labelAlpha),
            style = TextStyle(color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val CLOCK = DateTimeFormatter.ofPattern("EEE, d MMM  H:mm", Locale.forLanguageTag("ru"))

/** Компактные часы в углу, как строка статуса tvOS. */
@Composable
private fun Clock(modifier: Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(60_000L - now.second * 1000L - now.nano / 1_000_000)
        }
    }
    BasicText(
        now.format(CLOCK).replaceFirstChar { it.uppercase() },
        modifier,
        style = TextStyle(color = Color.White.copy(alpha = 0.85f), fontSize = 20.sp, fontWeight = FontWeight.Medium),
    )
}
