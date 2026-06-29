package com.zzz.webvideobrowser

class ResourceRegistry {
    private val candidates = linkedMapOf<String, VideoCandidate>()

    private val ttlMs = 3 * 60 * 1000L
    private val maxCandidates = 80

    fun upsert(candidate: VideoCandidate) {
        if (!isControllableType(candidate.type)) return

        candidates[candidate.id] = candidate
        trim()
    }

    private fun isControllableType(type: CandidateType): Boolean {
        return when (type) {
            CandidateType.DOM_VIDEO,
            CandidateType.DOM_BLOB_VIDEO,
            CandidateType.IFRAME_PLAYER,
            CandidateType.CUSTOM_VIEW -> true

            CandidateType.HLS_MASTER,
            CandidateType.HLS_MEDIA,
            CandidateType.DASH,
            CandidateType.MP4,
            CandidateType.WEBM,
            CandidateType.BLOB_HINT,
            CandidateType.FRAGMENT -> false
        }
    }

    fun hasCandidates(): Boolean {
        val now = System.currentTimeMillis()
        return candidates.values.any { now - it.seenAt < ttlMs }
    }

    fun listSorted(): List<VideoCandidate> {
        val now = System.currentTimeMillis()

        return candidates.values
            .filter { now - it.seenAt < ttlMs }
            .sortedWith(
                compareByDescending<VideoCandidate> { candidatePriority(it.type) }
                    .thenByDescending { it.score }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.seenAt }
            )
    }

    private fun trim() {
        val now = System.currentTimeMillis()

        candidates.entries.removeIf {
            now - it.value.seenAt > ttlMs
        }

        if (candidates.size > maxCandidates) {
            val removeCount = candidates.size - maxCandidates
            val keys = candidates.values
                .sortedBy { it.seenAt }
                .take(removeCount)
                .map { it.id }

            keys.forEach { candidates.remove(it) }
        }
    }

    private fun candidatePriority(type: CandidateType): Int {
        return when (type) {
            CandidateType.DOM_BLOB_VIDEO -> 1010
            CandidateType.DOM_VIDEO -> 1000
            CandidateType.IFRAME_PLAYER -> 900
            CandidateType.CUSTOM_VIEW -> 350
            else -> 0
        }
    }

    fun clearForPage(pageUrl: String?) {
        if (pageUrl.isNullOrBlank()) return
        candidates.entries.removeIf { it.value.pageUrl == pageUrl }
    }

    fun removeTypeForPage(type: CandidateType, pageUrl: String?) {
        candidates.entries.removeIf {
            it.value.type == type && it.value.pageUrl == pageUrl
        }
    }

    fun clearAll() {
        candidates.clear()
    }
}
