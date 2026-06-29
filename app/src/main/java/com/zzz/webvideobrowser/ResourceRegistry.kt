package com.zzz.webvideobrowser

class ResourceRegistry {
    private val candidates = linkedMapOf<String, VideoCandidate>()

    private fun candidatePriority(type: CandidateType): Int {
        return when (type) {
            CandidateType.DOM_BLOB_VIDEO -> 1010
            CandidateType.DOM_VIDEO -> 1000
            CandidateType.IFRAME_PLAYER -> 900
            CandidateType.HLS_MASTER -> 700
            CandidateType.HLS_MEDIA -> 650
            CandidateType.MP4 -> 620
            CandidateType.WEBM -> 610
            CandidateType.DASH -> 600
            CandidateType.BLOB_HINT -> 400
            CandidateType.CUSTOM_VIEW -> 350
            CandidateType.FRAGMENT -> 100
        }
    }

    fun upsert(candidate: VideoCandidate) {
        candidates[candidate.id] = candidate
    }

    fun hasCandidates(): Boolean {
        return listSorted().isNotEmpty()
    }

    fun listSorted(): List<VideoCandidate> {
        val now = System.currentTimeMillis()

        return candidates.values
            .filter { now - it.seenAt < 10 * 60 * 1000 }
            .sortedWith(
                compareByDescending<VideoCandidate> { candidatePriority(it.type) }
                    .thenByDescending { it.score }
                    .thenByDescending { it.confidence }
                    .thenByDescending { it.seenAt }
            )
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
