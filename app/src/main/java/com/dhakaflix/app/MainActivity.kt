package com.dhakaflix.app

import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
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
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8), dp(4), dp(8), dp(4)) }
        val back = Button(this).apply { text = "‹ BACK"; setTextColor(Color.WHITE); setBackgroundColor(Color.TRANSPARENT); setOnClickListener { closePlayer() } }
        bar.addView(back)
        bar.addView(text(entry.name, 13f, Color.WHITE, true), LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(bar)
        playerView = PlayerView(this).apply { useController = true; setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS); setBackgroundColor(Color.BLACK) }
        top.addView(playerView, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(top, LinearLayout.LayoutParams(-1, -1))
        try {
            player = ExoPlayer.Builder(this).build().also { exo ->
                playerView?.player = exo
                exo.setMediaItem(MediaItem.fromUri(entry.url))
                exo.prepare(); exo.playWhenReady = true
            }
        } catch (e: Exception) { Toast.makeText(this, "Unable to play this video format", Toast.LENGTH_LONG).show() }
    }

    private fun closePlayer() {
        releasePlayer()
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
