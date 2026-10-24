package home.tv.launcher

import android.graphics.Color
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.service.dreams.DreamService
import android.util.TypedValue
import android.view.Gravity
import android.view.SurfaceView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONArray
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Заставка в духе Apple TV: видео Apple Aerial по кругу, часы и название места. */
class AerialDream : DreamService() {
    private var player: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var time: TextView
    private lateinit var date: TextView
    private lateinit var place: TextView
    private var names: List<String> = emptyList()

    private val tick = object : Runnable {
        override fun run() {
            val now = LocalDateTime.now()
            time.text = now.format(TIME)
            date.text = now.format(DATE).replaceFirstChar { it.uppercase() }
            handler.postDelayed(this, 60_000L - now.second * 1000L - now.nano / 1_000_000)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isInteractive = false
        isFullscreen = true
        isScreenBright = true

        val surface = SurfaceView(this)
        time = text(64f, "sans-serif-light")
        date = text(18f, "sans-serif").apply { alpha = 0.75f }
        place = text(20f, "sans-serif-medium").apply { alpha = 0f; maxWidth = dp(560) }

        val clock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.END
            addView(time)
            addView(date)
        }
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            addView(surface, FrameLayout.LayoutParams(-1, -1))
            addView(clock, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply {
                topMargin = dp(40); rightMargin = dp(56)
            })
            addView(place, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply {
                bottomMargin = dp(48); leftMargin = dp(56)
            })
        }
        setContentView(root)

        val clips = loadClips().shuffled()
        names = clips.map { it.second }
        player = ExoPlayer.Builder(this).build().apply {
            setVideoSurfaceView(surface)
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ALL
            setMediaItems(clips.map { MediaItem.fromUri(it.first) })
            addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = showPlace()
            })
            prepare()
            play()
        }
        showPlace()
        tick.run()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        super.onDetachedFromWindow()
    }

    // Как на tvOS: подпись появляется в начале ролика и через несколько секунд гаснет
    private fun showPlace() {
        val index = player?.currentMediaItemIndex ?: return
        place.animate().cancel()
        place.alpha = 0f
        place.text = names.getOrNull(index).orEmpty()
        place.animate().alpha(0.9f).setStartDelay(2_000).setDuration(1_200).withEndAction {
            place.animate().alpha(0f).setStartDelay(10_000).setDuration(1_500)
        }
    }

    private fun loadClips(): List<Pair<String, String>> {
        val json = JSONArray(assets.open("aerials.json").bufferedReader().readText())
        return (0 until json.length()).map { json.getJSONObject(it).let { o -> o.getString("url") to o.getString("name") } }
    }

    private fun text(sizeSp: Float, family: String) = TextView(this).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        typeface = Typeface.create(family, Typeface.NORMAL)
        setShadowLayer(dp(12).toFloat(), 0f, 0f, 0x80000000.toInt())
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private companion object {
        val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
        val DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.forLanguageTag("ru"))
    }
}
