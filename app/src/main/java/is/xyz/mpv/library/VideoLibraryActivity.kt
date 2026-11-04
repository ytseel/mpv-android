package `is`.xyz.mpv.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.LruCache
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Button
import android.widget.ProgressBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import `is`.xyz.mpv.MPVActivity
import java.util.concurrent.Executors

/**
 * A native Android library for local indexed videos. A regular document picker
 * remains on the home screen for inaccessible or non-indexed files.
 */
class VideoLibraryActivity : AppCompatActivity() {
    private val io = Executors.newSingleThreadExecutor()
    private val thumbnails = LruCache<String, Bitmap>(48)
    private lateinit var status: TextView
    private lateinit var loading: ProgressBar
    private lateinit var recycler: RecyclerView
    private lateinit var search: EditText
    private lateinit var folderSpinner: Spinner
    private lateinit var historyButton: Button
    private lateinit var adapter: VideoAdapter
    private var videos: List<LibraryVideo> = emptyList()
    private var shown: List<LibraryVideo> = emptyList()
    private var historyOnly = false
    private var folders = listOf("All folders")
    private var generation = 0
    private val history by lazy { PlaybackHistory(this) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasPermission()) scan()
            else status.text = "No video access. You can still use the file picker on the home screen."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "My videos"
        val padding = dp(12)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, 0)
        }
        val heading = TextView(this).apply {
            text = "My videos"
            textSize = 28f
            setTypeface(null, Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(heading)
        search = EditText(this).apply {
            hint = "Search videos"
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        root.addView(search)
        val filters = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        folderSpinner = Spinner(this)
        filters.addView(folderSpinner, LinearLayout.LayoutParams(0, dp(52), 1f))
        historyButton = Button(this).apply { text = "Continue watching"; isAllCaps = false }
        filters.addView(historyButton)
        root.addView(filters)
        status = TextView(this).apply {
            text = "Scanning…"
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)
        loading = ProgressBar(this).apply { visibility = View.GONE }
        root.addView(loading, LinearLayout.LayoutParams(dp(40), dp(40)).apply { gravity = Gravity.CENTER_HORIZONTAL })
        recycler = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@VideoLibraryActivity)
        }
        adapter = VideoAdapter()
        recycler.adapter = adapter
        root.addView(recycler, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)

        search.addTextChangedListener { refresh() }
        folderSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = refresh()
        }
        historyButton.setOnClickListener {
            historyOnly = !historyOnly
            historyButton.text = if (historyOnly) "All videos" else "Continue watching"
            refresh()
        }
        requestOrScan()
    }

    override fun onResume() {
        super.onResume()
        if (::adapter.isInitialized) refresh()
    }

    override fun onDestroy() {
        generation++
        io.shutdownNow()
        super.onDestroy()
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value).toInt()

    private fun hasPermission(): Boolean {
        val required = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
            else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, required) == PackageManager.PERMISSION_GRANTED) return true
        return Build.VERSION.SDK_INT >= 34 &&
            ContextCompat.checkSelfPermission(this, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED") ==
                PackageManager.PERMISSION_GRANTED
    }

    private fun requestOrScan() {
        if (hasPermission()) return scan()
        val permissions = if (Build.VERSION.SDK_INT >= 34)
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, "android.permission.READ_MEDIA_VISUAL_USER_SELECTED")
        else if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        status.text = "Allow video access to browse the media library."
        permissionLauncher.launch(permissions)
    }

    private fun scan() {
        val currentGeneration = ++generation
        loading.visibility = View.VISIBLE
        status.text = "Scanning videos…"
        io.execute {
            val result = runCatching { VideoRepository(applicationContext).query() }
            runOnUiThread {
                if (isFinishing || isDestroyed || currentGeneration != generation) return@runOnUiThread
                loading.visibility = View.GONE
                result.onSuccess {
                    videos = it
                    folders = listOf("All folders") + it.map { v -> v.folder }.distinct().sorted()
                    folderSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, folders)
                    refresh()
                }.onFailure { e ->
                    status.text = "Scan failed: ${e.localizedMessage ?: "Unknown error"}"
                }
            }
        }
    }

    private fun refresh() {
        if (!::adapter.isInitialized) return
        val query = search.text?.toString()?.trim().orEmpty()
        val folder = folders.getOrNull(folderSpinner.selectedItemPosition) ?: "All folders"
        val recent = history.entries().associateBy { it.uri }
        shown = videos.asSequence()
            .filter { folder == "All folders" || it.folder == folder }
            .filter { query.isEmpty() || it.title.contains(query, ignoreCase = true) }
            .filter { !historyOnly || (recent[it.uri.toString()]?.positionMs ?: 0) > 0 }
            .sortedWith(if (historyOnly) compareByDescending { recent[it.uri.toString()]?.lastPlayed ?: 0L }
                        else compareBy { it.title.lowercase() })
            .toList()
        status.text = "${shown.size} videos" + if (historyOnly) " in Continue watching" else ""
        adapter.notifyDataSetChanged()
    }

    private inner class VideoAdapter : RecyclerView.Adapter<VideoHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VideoHolder {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(4), dp(8), dp(4), dp(8))
            }
            val preview = ImageView(parent.context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.DKGRAY)
            }
            row.addView(preview, LinearLayout.LayoutParams(dp(120), dp(72)))
            val text = TextView(parent.context).apply {
                setPadding(dp(12), 0, dp(4), 0)
                textSize = 14f
                maxLines = 3
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            row.addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            return VideoHolder(row, preview, text)
        }

        override fun getItemCount() = shown.size

        override fun onBindViewHolder(holder: VideoHolder, position: Int) {
            val video = shown[position]
            val record = history.entries().firstOrNull { it.uri == video.uri.toString() }
            val progress = if (record != null && record.durationMs > 0)
                " • ${((record.positionMs.toDouble() / record.durationMs) * 100).toInt().coerceIn(0, 100)}% watched"
                else ""
            holder.text.text = "${video.title}\n${video.folder}\n${video.durationMs / 60000} min$progress"
            val key = video.uri.toString()
            holder.preview.tag = key
            holder.preview.setImageBitmap(thumbnails.get(key))
            if (thumbnails.get(key) == null) {
                io.execute {
                    val bitmap = runCatching {
                        if (Build.VERSION.SDK_INT >= 29)
                            contentResolver.loadThumbnail(video.uri, Size(240, 144), null)
                        else android.provider.MediaStore.Video.Thumbnails.getThumbnail(
                            contentResolver, video.id,
                            android.provider.MediaStore.Video.Thumbnails.MINI_KIND, null)
                    }.getOrNull()
                    if (bitmap != null) thumbnails.put(key, bitmap)
                    runOnUiThread {
                        if (!isFinishing && !isDestroyed && holder.preview.tag == key)
                            holder.preview.setImageBitmap(bitmap)
                    }
                }
            }
            holder.itemView.setOnClickListener {
                val intent = Intent(Intent.ACTION_VIEW, video.uri, this@VideoLibraryActivity, MPVActivity::class.java)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                if (record != null && record.positionMs > 10000 && record.durationMs > 0 &&
                    record.positionMs < record.durationMs - 15000) {
                    androidx.appcompat.app.AlertDialog.Builder(this@VideoLibraryActivity)
                        .setTitle("Resume playback?")
                        .setMessage("Continue from ${record.positionMs / 60000} min?")
                        .setPositiveButton("Resume") { _, _ ->
                            intent.putExtra("position", record.positionMs.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                            startActivity(intent)
                        }
                        .setNegativeButton("From start") { _, _ -> startActivity(intent) }
                        .show()
                } else startActivity(intent)
            }
        }
    }

    private class VideoHolder(row: View, val preview: ImageView, val text: TextView) :
        RecyclerView.ViewHolder(row)
}
