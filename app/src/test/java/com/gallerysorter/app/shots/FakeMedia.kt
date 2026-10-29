package com.gallerysorter.app.shots

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.io.File

/** Stand-in for the MediaStore "media" provider, serving the demo landscapes in test resources. */
class FakeMedia : ContentProvider() {

    class Bucket(val id: Long, val name: String, val count: Int, val coverImage: Int) {
        val start: Long = id * 10_000 + ((coverImage - (id * 10_000) % PHOTOS + PHOTOS) % PHOTOS)
    }

    companion object {
        const val PHOTOS = 12
        const val TRASH_BASE = 900_000L

        val buckets = listOf(
            Bucket(1, "Camera", 2341, 4),
            Bucket(2, "Travel", 893, 7),
            Bucket(3, "Nature", 317, 2),
            Bucket(4, "Downloads", 156, 8),
            Bucket(5, "Instagram", 653, 1),
            Bucket(6, "WhatsApp", 1284, 10),
        )
        val trashDays = intArrayOf(28, 28, 28, 27, 27, 26, 28, 25, 28)

        fun imageIndex(id: Long): Int =
            if (id >= TRASH_BASE) ((id - TRASH_BASE).toInt() * 5 + 3) % PHOTOS else (id % PHOTOS).toInt()
    }

    override fun onCreate() = true

    override fun query(
        uri: Uri, projection: Array<String>?, queryArgs: Bundle?, cancellationSignal: CancellationSignal?,
    ): Cursor {
        val cols = projection ?: arrayOf(MediaStore.Images.Media._ID)
        val cursor = MatrixCursor(cols)
        val trashed = queryArgs?.getInt(MediaStore.QUERY_ARG_MATCH_TRASHED) == MediaStore.MATCH_ONLY
        fun row(id: Long, bucket: Bucket?, days: Int = 0) {
            cursor.addRow(cols.map {
                when (it) {
                    MediaStore.Images.Media._ID -> id
                    MediaStore.Images.Media.BUCKET_ID -> bucket?.id ?: 0L
                    MediaStore.Images.Media.BUCKET_DISPLAY_NAME -> bucket?.name ?: ""
                    MediaStore.MediaColumns.DATE_EXPIRES -> System.currentTimeMillis() / 1000 + days * 86_400L - 3_600
                    else -> null
                }
            })
        }
        if (trashed) {
            trashDays.forEachIndexed { i, d -> row(TRASH_BASE + i, null, d) }
        } else {
            val only = queryArgs?.getStringArray("android:query-arg-sql-selection-args")?.firstOrNull()?.toLongOrNull()
            for (b in buckets) {
                if (only != null && b.id != only) continue
                for (n in 0 until b.count) row(b.start + n, b)
            }
        }
        return cursor
    }

    override fun query(uri: Uri, p: Array<String>?, s: String?, a: Array<String>?, o: String?): Cursor? = null

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val id = uri.lastPathSegment!!.toLong()
        val name = "p%02d.jpg".format(imageIndex(id) + 1)
        val file = File(context!!.cacheDir, name)
        if (!file.exists()) {
            javaClass.classLoader!!.getResourceAsStream("photos/$name")!!.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri) = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?) = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<String>?) = 0
}
