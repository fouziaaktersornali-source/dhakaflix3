package com.dhakaflix.app

import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.view.WindowManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.setPadding
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import java.net.URLDecoder

private data class Entry(val name: String, val url: String, val folder: Boolean)

@UnstableApi
class MainActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val baseUrl = "http://172.16.50.14/"
    private var currentUrl = baseUrl
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private lateinit var search: EditText
    private var allEntries: List<Entry> = emptyList()
    private var currentTitle = "DhakaFlix"
    private val videoExt = listOf(".mp4", ".mkv", ".m4v", ".webm", ".mov", ".m3u8", ".mpd", ".ts", ".mpg", ".mpeg", ".3gp", ".avi")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(9, 9, 9)
        window.navigationBarColor = Color.BLACK
        showLibraryShell()
        loadDirectory(baseUrl)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false): TextView =
        TextView(this).apply {
            text = value; textSize = size; setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
        }

    private fun showLibraryShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(9,9,9)) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(10)); setBackgroundColor(Color.rgb(15,15,15))
        }
        val logo = TextView(this).apply {
            text = "DhakaFlix"; textSize = 25f; setTextColor(Color.WHITE); setTypeface(typeface, Typeface.BOLD)
        }
        val red = TextView(this).apply { text = "  ●"; textSize = 12f; setTextColor(Color.rgb(229,9,20)) }
        header.addView(logo); header.addView(red)
        val spacer = Space(this); header.addView(spacer, LinearLayout.LayoutParams(0, 1, 1f))
        val refresh = Button(this).apply {
            text = "REFRESH"; textSize = 10f; setTextColor(Color.WHITE); setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(45,45,45)))
            setOnClickListener { loadDirectory(currentUrl) }
        }
        header.addView(refresh)
        root.addView(header)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(16), dp(18), dp(16))
            setBackgroundColor(Color.rgb(27,20,22))
        }
        hero.addView(text("YOUR ENTERTAINMENT HUB", 10f, Color.rgb(229,9,20), true))
        hero.addView(text("Discover your next movie.", 21f, Color.WHITE, true).apply { setPadding(0, dp(5), 0, dp(4)) })
        hero.addView(text("Browse the DhakaFlix library directly from your network.", 12f, Color.LTGRAY))
        root.addView(hero)
        search = EditText(this).apply {
            hint = "Search movies and folders"; setHintTextColor(Color.GRAY); setTextColor(Color.WHITE)
            textSize = 14f; setSingleLine(true); setPadding(dp(14), dp(8), dp(14), dp(8))
            setBackgroundColor(Color.rgb(35,35,35))
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { renderEntries(s?.toString().orEmpty()) }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        val searchWrap = LinearLayout(this).apply { setPadding(dp(16), dp(12), dp(16), dp(8)); addView(search) }
        root.addView(searchWrap)
        val scroll = ScrollView(this)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14), dp(4), dp(14), dp(24)) }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun loadDirectory(url: String) {
        currentUrl = url
        content.removeAllViews()
        content.addView(text("Connecting to movie server…", 14f, Color.LTGRAY).apply { setPadding(0, dp(18), 0, dp(18)) })
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { fetchEntries(url) }
                allEntries = result
                currentTitle = URLDecoder.decode(Uri.parse(url).lastPathSegment ?: "DhakaFlix", "UTF-8").ifBlank { "DhakaFlix" }
                content.removeAllViews()
                content.addView(text(currentTitle.replace('/', ' ').uppercase(), 13f, Color.rgb(229,9,20), true).apply { setPadding(0, dp(10), 0, dp(12)) })
                renderEntries(search.text.toString())
                if (result.isEmpty()) showMessage("No folders or video files were detected. Check the server address and directory permissions.")
            } catch (e: Exception) {
                showMessage("Could not connect to $url\n\n${e.message ?: "Network error"}\n\nMake sure your phone is connected to the same Wi-Fi/LAN as the DhakaFlix server.")
            }
        }
    }

    private fun fetchEntries(url: String): List<Entry> {
        val doc = Jsoup.connect(url).userAgent("Mozilla/5.0 DhakaFlix Android").timeout(15000).ignoreContentType(true).get()
        val out = mutableListOf<Entry>()
        for (a in doc.select("a[href]")) {
            val href = a.attr("href").trim()
            val label = a.text().trim().ifBlank { href.substringAfterLast('/').ifBlank { href } }
            if (href.isBlank() || href.startsWith("#") || href.startsWith("?")) continue
            if (label.equals("parent directory", true) || label == "../" || href == "../") continue
            val absolute = a.absUrl("href")
            if (!absolute.startsWith("http://") && !absolute.startsWith("https://")) continue
            val path = Uri.parse(absolute).path.orEmpty().lowercase()
            val isVideo = videoExt.any { path.endsWith(it) }
            val isFolder = !isVideo && (href.endsWith("/") || label.endsWith("/"))
            if (isVideo || isFolder) out.add(Entry(label.trimEnd('/'), absolute, isFolder))
        }
        return out.distinctBy { it.url }.sortedWith(compareByDescending<Entry> { it.folder }.thenBy { it.name.lowercase() })
    }

    private fun renderEntries(filter: String) {
        if (!::content.isInitialized || !::search.isInitialized) return
        content.removeAllViews()
        val filtered = allEntries.filter { it.name.contains(filter, true) }
        val folders = filtered.filter { it.folder }
        val videos = filtered.filterNot { it.folder }
        if (folders.isNotEmpty()) {
            content.addView(text("COLLECTIONS  ·  ${folders.size}", 12f, Color.LTGRAY, true).apply { setPadding(0, dp(8), 0, dp(10)) })
            folders.forEach { entry ->
                val row = makeEntryRow(entry, true)
                content.addView(row)
            }
        }
        if (videos.isNotEmpty()) {
            content.addView(text("MOVIES  ·  ${videos.size}", 12f, Color.LTGRAY, true).apply { setPadding(0, dp(18), 0, dp(10)) })
            videos.forEach { entry -> content.addView(makeEntryRow(entry, false)) }
        }
        if (filtered.isEmpty()) content.addView(text(if (allEntries.isEmpty()) "" else "No matching movies or folders.", 13f, Color.GRAY).apply { setPadding(0, dp(20), 0, dp(20)) })
    }

    private fun makeEntryRow(entry: Entry, folder: Boolean): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(13), dp(13), dp(13), dp(13)); setBackgroundColor(Color.rgb(27,27,27))
        }
        val symbol = text(if (folder) "▰" else "▶", 20f, if (folder) Color.rgb(229,9,20) else Color.WHITE, true)
        row.addView(symbol, LinearLayout.LayoutParams(dp(34), -2))
        val label = text(entry.name, 14f, Color.WHITE, folder)
        row.addView(label, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(if (folder) "›" else "PLAY", if (folder) 24f else 10f, Color.LTGRAY, true))
        val margin = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(5) }
        row.layoutParams = margin
        row.setOnClickListener { if (folder) loadDirectory(entry.url) else playVideo(entry) }
        return row
    }

    private fun showMessage(message: String) {
        content.removeAllViews()
        content.addView(text(message, 14f, Color.LTGRAY).apply { setPadding(dp(8), dp(20), dp(8), dp(20)) })
        val retry = Button(this).apply { text = "RETRY"; setOnClickListener { loadDirectory(currentUrl) } }
        content.addView(retry)
    }

    private fun playVideo(entry: Entry) {
        releasePlayer()
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        root.removeAllViews()
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val originalBrightness = window.attributes.screenBrightness
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(2), dp(8), dp(2)); setBackgroundColor(Color.rgb(12,12,12)) }
        val back = Button(this).apply { text = "‹ BACK"; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { closePlayer() } }
        header.addView(back)
        header.addView(text(entry.name, 13f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        screen.addView(header)

        val videoFrame = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = false
            setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
            setBackgroundColor(Color.BLACK)
            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
        videoFrame.addView(playerView, FrameLayout.LayoutParams(-1, -1))
        screen.addView(videoFrame, LinearLayout.LayoutParams(-1, 0, 1f))

        val controls = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(4), dp(12), dp(7)); setBackgroundColor(Color.rgb(15,15,15)) }
        val transport = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun controlButton(label: String, action: () -> Unit): Button = Button(this).apply {
            text = label; textSize = 11f; isAllCaps = false; setTextColor(Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(43,43,43)))
            setPadding(dp(5), 0, dp(5), 0); setOnClickListener { action() }
        }
        val playPause = controlButton("Ⅱ  Pause") { player?.let { p -> if (p.isPlaying) p.pause() else p.play() } }
        transport.addView(playPause, LinearLayout.LayoutParams(dp(82), dp(42)).apply { rightMargin = dp(5) })
        transport.addView(controlButton("−10s") { player?.let { it.seekTo((it.currentPosition - 10_000L).coerceAtLeast(0L)) } }, LinearLayout.LayoutParams(dp(64), dp(42)).apply { rightMargin = dp(5) })
        val time = text("00:00 / 00:00", 10f, Color.LTGRAY)
        transport.addView(time, LinearLayout.LayoutParams(dp(90), -2))
        transport.addView(controlButton("+10s") { player?.let { it.seekTo((it.currentPosition + 10_000L).coerceAtMost(if (it.duration > 0) it.duration else Long.MAX_VALUE)) } }, LinearLayout.LayoutParams(dp(64), dp(42)).apply { leftMargin = dp(5); rightMargin = dp(5) })
        lateinit var fullscreenButton: Button
        fullscreenButton = controlButton("⛶ Full") {
            val currentlyImmersive = (window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_FULLSCREEN) != 0
            window.decorView.systemUiVisibility = if (currentlyImmersive) View.SYSTEM_UI_FLAG_VISIBLE else
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            fullscreenButton.text = if (currentlyImmersive) "⛶ Full" else "⛶ Exit"
        }
        transport.addView(fullscreenButton, LinearLayout.LayoutParams(0, dp(42), 1f))
        controls.addView(transport)

        val seek = SeekBar(this).apply {
            max = 1000
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(229,9,20))
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        controls.addView(seek, LinearLayout.LayoutParams(-1, dp(28)))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {
                val p = player ?: return
                if (p.duration > 0) p.seekTo((p.duration * bar.progress / 1000L).coerceIn(0L, p.duration))
            }
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {}
        })

        val levels = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        levels.addView(text("VOL", 10f, Color.LTGRAY, true))
        val volume = SeekBar(this).apply {
            max = maxVolume; progress = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE); thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        levels.addView(volume, LinearLayout.LayoutParams(0, dp(30), 1f).apply { leftMargin = dp(5); rightMargin = dp(14) })
        volume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) { if (fromUser) audio.setStreamVolume(AudioManager.STREAM_MUSIC, progress, 0) }
        })
        levels.addView(text("BRIGHT", 10f, Color.LTGRAY, true))
        val brightness = SeekBar(this).apply {
            max = 100
            progress = ((if (originalBrightness < 0f) 0.65f else originalBrightness) * 100).toInt()
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(255,190,70)); thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
        }
        levels.addView(brightness, LinearLayout.LayoutParams(0, dp(30), 1f).apply { leftMargin = dp(5) })
        brightness.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onStartTrackingTouch(bar: SeekBar) {}
            override fun onStopTrackingTouch(bar: SeekBar) {}
            override fun onProgressChanged(bar: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) window.attributes = window.attributes.apply { screenBrightness = (progress / 100f).coerceIn(0.05f, 1f) }
            }
        })
        controls.addView(levels)
        screen.addView(controls)
        root.addView(screen, LinearLayout.LayoutParams(-1, -1))

        try {
            player = ExoPlayer.Builder(this).build().also { exo ->
                playerView?.player = exo
                exo.setMediaItem(MediaItem.fromUri(entry.url)); exo.prepare(); exo.playWhenReady = true
                exo.addListener(object : androidx.media3.common.Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) { playPause.text = if (isPlaying) "Ⅱ  Pause" else "▶  Play" }
                })
            }
            val update = object : Runnable {
                override fun run() {
                    val p = player ?: return
                    val duration = p.duration.takeIf { it > 0 } ?: 0L
                    if (!seek.isPressed && duration > 0) seek.progress = ((p.currentPosition * 1000L) / duration).toInt()
                    time.text = "${formatTime(p.currentPosition)} / ${formatTime(duration)}"
                    playerView?.postDelayed(this, 500)
                }
            }
            playerView?.post(update)
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to play this video format: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0L)) / 1000L
        return String.format("%02d:%02d", total / 60L, total % 60L)
    }

    private fun closePlayer() {
        releasePlayer()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        showLibraryShell()
        loadDirectory(currentUrl)
    }

    private fun releasePlayer() { playerView?.player = null; player?.release(); player = null; playerView = null }

    override fun onBackPressed() {
        if (player != null) { closePlayer(); return }
        if (currentUrl != baseUrl) {
            val parent = currentUrl.trimEnd('/').substringBeforeLast('/', "")
            loadDirectory(if (parent.isBlank()) baseUrl else "$parent/")
        } else super.onBackPressed()
    }

    override fun onDestroy() { super.onDestroy(); releasePlayer(); scope.cancel() }
}
