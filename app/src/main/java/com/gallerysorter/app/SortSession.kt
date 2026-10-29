package com.gallerysorter.app

enum class Mark { KEEP, DELETE, TRASHED }

/**
 * Pure in-memory sorting state: photos are visited in order, each gets KEEP or DELETE.
 * DELETE only marks the photo; it becomes TRASHED after the system trash request succeeds.
 */
class SortSession(val photos: List<Long>, restored: List<Pair<Long, Mark>> = emptyList()) {

    private class Entry(val id: Long, var mark: Mark)

    private val history = ArrayList<Entry>()
    private val decided = HashSet<Long>()
    private var cursor = 0

    var keptCount = 0
        private set
    var markedCount = 0
        private set
    var trashedCount = 0
        private set

    init {
        val valid = photos.toHashSet()
        for ((id, mark) in restored) {
            if (id in valid && decided.add(id)) {
                history.add(Entry(id, mark))
                count(mark, 1)
            }
        }
        skipDecided()
    }

    val total: Int get() = photos.size
    val reviewed: Int get() = history.size
    val remaining: Int get() = total - reviewed
    val current: Long? get() = photos.getOrNull(cursor)
    val canUndo: Boolean get() = history.lastOrNull()?.let { it.mark != Mark.TRASHED } ?: false

    fun upNext(n: Int): List<Long> {
        val out = ArrayList<Long>(n)
        var i = cursor + 1
        while (i < photos.size && out.size < n) {
            if (photos[i] !in decided) out.add(photos[i])
            i++
        }
        return out
    }

    fun decide(keep: Boolean): Boolean {
        val id = current ?: return false
        val mark = if (keep) Mark.KEEP else Mark.DELETE
        history.add(Entry(id, mark))
        decided.add(id)
        count(mark, 1)
        cursor++
        skipDecided()
        return true
    }

    /** Reverts the most recent decision (not allowed once it has been trashed). */
    fun undo(): Long? {
        if (!canUndo) return null
        val e = history.removeAt(history.lastIndex)
        decided.remove(e.id)
        count(e.mark, -1)
        cursor = photos.indexOf(e.id)
        return e.id
    }

    fun markedIds(): List<Long> = history.filter { it.mark == Mark.DELETE }.map { it.id }

    /** Switches a marked photo back to kept. */
    fun unmark(id: Long): Boolean {
        val e = history.firstOrNull { it.id == id && it.mark == Mark.DELETE } ?: return false
        count(e.mark, -1)
        e.mark = Mark.KEEP
        count(e.mark, 1)
        return true
    }

    fun markTrashed(ids: Collection<Long>) {
        val set = ids.toHashSet()
        for (e in history) {
            if (e.mark == Mark.DELETE && e.id in set) {
                count(e.mark, -1)
                e.mark = Mark.TRASHED
                count(e.mark, 1)
            }
        }
    }

    /** Most recent photos with the given mark, newest first. */
    fun recent(mark: Mark, n: Int): List<Long> {
        val out = ArrayList<Long>(n)
        var i = history.lastIndex
        while (i >= 0 && out.size < n) {
            if (history[i].mark == mark) out.add(history[i].id)
            i--
        }
        return out
    }

    fun encode(): String {
        val sb = StringBuilder(history.size * 10)
        for (e in history) {
            sb.append(e.id).append(':').append(
                when (e.mark) {
                    Mark.KEEP -> 'k'
                    Mark.DELETE -> 'd'
                    Mark.TRASHED -> 't'
                }
            ).append(';')
        }
        return sb.toString()
    }

    private fun skipDecided() {
        while (cursor < photos.size && photos[cursor] in decided) cursor++
    }

    private fun count(mark: Mark, delta: Int) {
        when (mark) {
            Mark.KEEP -> keptCount += delta
            Mark.DELETE -> markedCount += delta
            Mark.TRASHED -> trashedCount += delta
        }
    }

    companion object {
        fun decode(text: String): List<Pair<Long, Mark>> {
            val out = ArrayList<Pair<Long, Mark>>()
            for (part in text.split(';')) {
                if (part.isEmpty()) continue
                val id = part.substringBefore(':').toLongOrNull() ?: continue
                val mark = when (part.substringAfter(':', "")) {
                    "k" -> Mark.KEEP
                    "d" -> Mark.DELETE
                    "t" -> Mark.TRASHED
                    else -> continue
                }
                out.add(id to mark)
            }
            return out
        }
    }
}
