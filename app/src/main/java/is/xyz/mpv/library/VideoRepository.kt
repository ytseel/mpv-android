package `is`.xyz.mpv.library

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** A video discovered in the system's indexed media library. */
data class LibraryVideo(
    val id: Long,
    val title: String,
    val uri: Uri,
    val durationMs: Long,
    val sizeBytes: Long,
    val folder: String
)

/**
 * Reads videos the user has allowed this app to see.
 * Do not run query() on the main thread.
 */
class VideoRepository(private val context: Context) {
    fun query(): List<LibraryVideo> {
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val columns = mutableListOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            columns.add(MediaStore.Video.Media.RELATIVE_PATH)
        }

        val results = mutableListOf<LibraryVideo>()
        context.contentResolver.query(
            collection, columns.toTypedArray(), null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val folderColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
            } else -1

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                results.add(
                    LibraryVideo(
                        id = id,
                        title = cursor.getString(nameColumn) ?: "Untitled video",
                        uri = ContentUris.withAppendedId(collection, id),
                        durationMs = if (cursor.isNull(durationColumn)) 0L else cursor.getLong(durationColumn),
                        sizeBytes = if (cursor.isNull(sizeColumn)) 0L else cursor.getLong(sizeColumn),
                        folder = if (folderColumn >= 0 && !cursor.isNull(folderColumn)) {
                            cursor.getString(folderColumn) ?: "Videos"
                        } else "Videos"
                    )
                )
            }
        }
        return results
    }
}
