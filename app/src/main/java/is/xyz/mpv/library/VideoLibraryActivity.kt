package `is`.xyz.mpv.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import `is`.xyz.mpv.MPVActivity

/**
 * An initial, deliberately simple video library. Existing document/file pickers
 * remain untouched so files outside MediaStore are still accessible.
 */
class VideoLibraryActivity : AppCompatActivity() {
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var loading: ProgressBar
    private var videos: List<LibraryVideo> = emptyList()
    private var scanGeneration = 0

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted || hasVideoPermission()) scan()
            else status.text = "Media access not granted. Use the existing file picker to open a video."
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Video library"

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        status = TextView(this).apply {
            text = "Loading videos…"
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, 12)
        }
        loading = ProgressBar(this).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        list = ListView(this).apply {
            dividerHeight = (4 * resources.displayMetrics.density).toInt()
        }
        root.addView(status)
        root.addView(loading, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { gravity = Gravity.CENTER_HORIZONTAL })
        root.addView(list, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        setContentView(root)

        list.setOnItemClickListener { _, _, position, _ ->
            val video = videos.getOrNull(position) ?: return@setOnItemClickListener
            startActivity(Intent(Intent.ACTION_VIEW, video.uri, this, MPVActivity::class.java).apply {
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            })
        }
        requestPermissionOrScan()
    }

    override fun onDestroy() {
        scanGeneration++
        super.onDestroy()
    }

    private fun hasVideoPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= 33)
            Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissionOrScan() {
        if (hasVideoPermission()) {
            scan()
        } else {
            val permission = if (Build.VERSION.SDK_INT >= 33)
                Manifest.permission.READ_MEDIA_VIDEO
            else Manifest.permission.READ_EXTERNAL_STORAGE
            status.text = "Allow video access to browse your media library."
            permissionLauncher.launch(permission)
        }
    }

    private fun scan() {
        val generation = ++scanGeneration
        loading.visibility = View.VISIBLE
        status.text = "Scanning video library…"
        Thread {
            val result = runCatching { VideoRepository(applicationContext).query() }
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != scanGeneration) return@runOnUiThread
                loading.visibility = View.GONE
                result.onSuccess { found ->
                    videos = found
                    status.text = if (found.isEmpty()) "No indexed videos found. Try the file picker." else
                        "${found.size} videos"
                    list.adapter = ArrayAdapter(
                        this,
                        android.R.layout.simple_list_item_1,
                        android.R.id.text1,
                        found.map { "${it.title}\n${it.folder}" }
                    )
                }.onFailure { error ->
                    status.text = "Couldn't scan videos: ${error.localizedMessage ?: "Unknown error"}"
                }
            }
        }.start()
    }
}
