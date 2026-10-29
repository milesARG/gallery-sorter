package com.gallerysorter.app

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max

data class Album(val bucketId: Long, val name: String, val count: Int, val coverId: Long)

data class TrashedPhoto(val id: Long, val daysLeft: Int)

fun photoUri(id: Long): Uri =
    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)

/** All gallery access goes through MediaStore; nothing is copied or cached by the app. */
class MediaRepo(private val context: Context) {

    private val resolver: ContentResolver get() = context.contentResolver
    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    private class Acc(val name: String, val coverId: Long) {
        var count = 0
    }

    suspend fun albums(): List<Album> = withContext(Dispatchers.IO) {
        val map = LinkedHashMap<Long, Acc>()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
        )
        resolver.query(collection, projection, args(sort = MediaStore.Images.Media.DATE_ADDED), null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val bucketCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            while (c.moveToNext()) {
                val bucket = c.getLong(bucketCol)
                val acc = map.getOrPut(bucket) { Acc(c.getString(nameCol) ?: "Unknown", c.getLong(idCol)) }
                acc.count++
            }
        }
        map.map { (bucket, a) -> Album(bucket, a.name, a.count, a.coverId) }
    }

    /** Ids of all non-trashed photos, newest first; [bucketId] null means the whole gallery. */
    suspend fun photoIds(bucketId: Long?): List<Long> = withContext(Dispatchers.IO) {
        val out = ArrayList<Long>()
        val selection = bucketId?.let { "${MediaStore.Images.Media.BUCKET_ID} = ?" }
        val selArgs = bucketId?.let { arrayOf(it.toString()) }
        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            args(selection, selArgs, sort = MediaStore.Images.Media.DATE_ADDED),
            null,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (c.moveToNext()) out.add(c.getLong(idCol))
        }
        out
    }

    suspend fun trashed(): List<TrashedPhoto> = withContext(Dispatchers.IO) {
        val out = ArrayList<TrashedPhoto>()
        val now = System.currentTimeMillis()
        resolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID, MediaStore.MediaColumns.DATE_EXPIRES),
            args(trashed = true, sort = MediaStore.MediaColumns.DATE_EXPIRES),
            null,
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val expCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_EXPIRES)
            while (c.moveToNext()) {
                val leftMs = c.getLong(expCol) * 1000L - now
                val days = max(0L, (leftMs + DAY_MS - 1) / DAY_MS).toInt()
                out.add(TrashedPhoto(c.getLong(idCol), days))
            }
        }
        out
    }

    /** System-confirmed move into (or out of) Android's recoverable trash. */
    fun trashRequest(ids: List<Long>, trash: Boolean): IntentSender =
        MediaStore.createTrashRequest(resolver, ids.map(::photoUri), trash).intentSender

    /** System-confirmed permanent deletion. */
    fun deleteRequest(ids: List<Long>): IntentSender =
        MediaStore.createDeleteRequest(resolver, ids.map(::photoUri)).intentSender

    private fun args(
        selection: String? = null,
        selectionArgs: Array<String>? = null,
        trashed: Boolean = false,
        sort: String,
    ) = Bundle().apply {
        if (selection != null) {
            putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
            putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
        }
        putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(sort))
        putInt(ContentResolver.QUERY_ARG_SORT_DIRECTION, ContentResolver.QUERY_SORT_DIRECTION_DESCENDING)
        putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, if (trashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE)
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}
