package com.gallerysorter.app

import android.app.Application
import android.content.Context
import android.content.IntentSender
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class Screen { WELCOME, PERMISSION, HOME, SWIPE, REVIEW, MARKED, TRASH, DONE }

enum class RequestKind { TRASH, RESTORE, DELETE }

data class SwipeUi(
    val currentId: Long? = null,
    val upNext: List<Long> = emptyList(),
    val reviewed: Int = 0,
    val total: Int = 0,
    val kept: Int = 0,
    val marked: Int = 0,
    val trashed: Int = 0,
    val remaining: Int = 0,
    val canUndo: Boolean = false,
    val keptPreview: List<Long> = emptyList(),
    val markedPreview: List<Long> = emptyList(),
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MediaRepo(app)
    private val prefs = app.getSharedPreferences("session", Context.MODE_PRIVATE)

    var screen by mutableStateOf(Screen.HOME)
        private set
    var albums by mutableStateOf<List<Album>>(emptyList())
        private set
    var loadingAlbums by mutableStateOf(false)
        private set
    var busy by mutableStateOf(false)
        private set
    var permissionDenied by mutableStateOf(false)
        private set
    var sessionTitle by mutableStateOf("")
        private set
    var ui by mutableStateOf(SwipeUi())
        private set
    var markedIds by mutableStateOf<List<Long>>(emptyList())
        private set
    var trashItems by mutableStateOf<List<TrashedPhoto>>(emptyList())
        private set
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var pendingRequest by mutableStateOf<IntentSender?>(null)
        private set

    private var session: SortSession? = null
    private var bucketId: Long? = null
    private var started = false
    private var pendingKind: RequestKind? = null
    private var pendingIds: List<Long> = emptyList()
    private var saveJob: Job? = null

    // ---- startup & permission -------------------------------------------------------------

    fun start(granted: Boolean) {
        if (started) return
        started = true
        if (granted) enterGallery() else screen = Screen.WELCOME
    }

    fun onPermission(granted: Boolean) {
        if (granted) {
            permissionDenied = false
            if (screen == Screen.WELCOME || screen == Screen.PERMISSION) enterGallery()
        } else {
            permissionDenied = true
            screen = Screen.PERMISSION
        }
    }

    fun goPermission() {
        screen = Screen.PERMISSION
    }

    private fun enterGallery() {
        viewModelScope.launch {
            loadAlbums()
            if (!restoreSession()) screen = Screen.HOME
        }
    }

    fun refreshAlbums() {
        viewModelScope.launch { loadAlbums() }
    }

    private suspend fun loadAlbums() {
        loadingAlbums = true
        try {
            albums = repo.albums()
        } catch (e: Exception) {
            message = "Couldn't read your gallery."
        }
        loadingAlbums = false
    }

    // ---- sorting session ------------------------------------------------------------------

    fun startSession(bucket: Long?, title: String) {
        viewModelScope.launch {
            busy = true
            val ids = try {
                repo.photoIds(bucket)
            } catch (e: Exception) {
                busy = false
                message = "Couldn't read your gallery."
                return@launch
            }
            busy = false
            if (ids.isEmpty()) {
                message = "No photos to sort here."
                return@launch
            }
            session = SortSession(ids)
            bucketId = bucket
            sessionTitle = title
            publish()
            screen = Screen.SWIPE
            saveNow()
        }
    }

    private suspend fun restoreSession(): Boolean {
        val saved = prefs.getString(KEY_DECISIONS, null) ?: return false
        if (!prefs.contains(KEY_BUCKET)) return false
        val target = if (prefs.getBoolean(KEY_ALL, false)) null else prefs.getLong(KEY_BUCKET, 0L)
        val ids = try {
            repo.photoIds(target)
        } catch (e: Exception) {
            return false
        }
        val restored = SortSession(ids, SortSession.decode(saved))
        if (restored.reviewed == 0 || restored.total == 0) {
            clearSaved()
            return false
        }
        session = restored
        bucketId = target
        sessionTitle = prefs.getString(KEY_TITLE, "") ?: ""
        publish()
        screen = if (restored.current == null) Screen.REVIEW else Screen.SWIPE
        return true
    }

    fun decide(id: Long, keep: Boolean) {
        val s = session ?: return
        if (s.current != id) return
        s.decide(keep)
        publish()
        persistSoon()
        if (s.current == null) screen = Screen.REVIEW
    }

    fun undo() {
        val s = session ?: return
        if (s.undo() != null) {
            publish()
            persistSoon()
        }
    }

    fun openMarked() {
        markedIds = session?.markedIds().orEmpty()
        screen = Screen.MARKED
    }

    fun keepPhoto(id: Long) {
        val s = session ?: return
        s.unmark(id)
        markedIds = s.markedIds()
        publish()
        persistSoon()
    }

    fun keepSorting() {
        if (session?.current != null) screen = Screen.SWIPE
    }

    /** Leaves the session without trashing anything. */
    fun discardSession() {
        endSession()
        screen = Screen.HOME
    }

    fun finish() {
        clearSaved()
        publish()
        screen = Screen.DONE
    }

    fun sortAnother() {
        endSession()
        refreshAlbums()
        screen = Screen.HOME
    }

    private fun endSession() {
        saveJob?.cancel()
        session = null
        ui = SwipeUi()
        clearSaved()
    }

    fun back() {
        when (screen) {
            Screen.SWIPE ->
                if ((session?.reviewed ?: 0) == 0) discardSession() else screen = Screen.REVIEW
            Screen.REVIEW -> {
                if (session?.current == null) undo() // finished all photos: step back to the last one
                keepSorting()
            }
            Screen.MARKED -> screen = Screen.REVIEW
            Screen.TRASH, Screen.DONE -> sortAnother()
            Screen.PERMISSION -> screen = Screen.WELCOME
            else -> Unit
        }
    }

    // ---- system requests (trash / restore / delete) ----------------------------------------

    fun requestTrash() {
        val s = session ?: return
        val ids = s.markedIds().take(REQUEST_CHUNK)
        if (ids.isEmpty()) return
        launchRequest(RequestKind.TRASH, ids) { repo.trashRequest(ids, true) }
    }

    fun requestRestore() {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        launchRequest(RequestKind.RESTORE, ids) { repo.trashRequest(ids, false) }
    }

    fun requestDelete() {
        val ids = selected.toList()
        if (ids.isEmpty()) return
        launchRequest(RequestKind.DELETE, ids) { repo.deleteRequest(ids) }
    }

    private fun launchRequest(kind: RequestKind, ids: List<Long>, build: () -> IntentSender) {
        try {
            val sender = build()
            pendingKind = kind
            pendingIds = ids
            pendingRequest = sender
        } catch (e: Exception) {
            pendingKind = null
            message = "Couldn't start the request."
        }
    }

    fun requestLaunched() {
        pendingRequest = null
    }

    fun onRequestResult(ok: Boolean) {
        val kind = pendingKind ?: return
        val ids = pendingIds
        pendingKind = null
        pendingIds = emptyList()
        when (kind) {
            RequestKind.TRASH -> {
                val s = session ?: return
                if (!ok) {
                    message = "Nothing was moved."
                    return
                }
                s.markTrashed(ids)
                publish()
                persistSoon()
                when {
                    s.markedCount > 0 -> requestTrash()
                    s.remaining == 0 -> finish()
                    else -> message = "Moved to Recently Deleted."
                }
            }
            RequestKind.RESTORE, RequestKind.DELETE -> {
                if (!ok) return
                message = if (kind == RequestKind.RESTORE) "Restored to your gallery." else "Permanently deleted."
                selected = emptySet()
                loadTrash()
            }
        }
    }

    fun messageShown() {
        message = null
    }

    // ---- recently deleted ------------------------------------------------------------------

    fun openTrash() {
        selected = emptySet()
        screen = Screen.TRASH
        loadTrash()
    }

    private fun loadTrash() {
        viewModelScope.launch {
            busy = true
            try {
                trashItems = repo.trashed()
            } catch (e: Exception) {
                message = "Couldn't read Recently Deleted."
            }
            busy = false
        }
    }

    fun toggleSelected(id: Long) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun selectAll() {
        selected = if (selected.size == trashItems.size) emptySet() else trashItems.map { it.id }.toSet()
    }

    // ---- persistence (process death only; rotation is handled by the ViewModel) -----------------

    private fun publish() {
        val s = session ?: return
        ui = SwipeUi(
            currentId = s.current,
            upNext = s.upNext(3),
            reviewed = s.reviewed,
            total = s.total,
            kept = s.keptCount,
            marked = s.markedCount,
            trashed = s.trashedCount,
            remaining = s.remaining,
            canUndo = s.canUndo,
            keptPreview = s.recent(Mark.KEEP, 3),
            markedPreview = s.recent(Mark.DELETE, 3),
        )
    }

    private fun persistSoon() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(400)
            saveNow()
        }
    }

    fun saveNow() {
        val s = session
        if (s == null || screen == Screen.DONE) return
        prefs.edit()
            .putBoolean(KEY_ALL, bucketId == null)
            .putLong(KEY_BUCKET, bucketId ?: 0L)
            .putString(KEY_TITLE, sessionTitle)
            .putString(KEY_DECISIONS, s.encode())
            .apply()
    }

    private fun clearSaved() {
        saveJob?.cancel()
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_BUCKET = "bucket"
        const val KEY_TITLE = "title"
        const val KEY_DECISIONS = "decisions"
        const val KEY_ALL = "all"
        const val REQUEST_CHUNK = 2000
    }
}
