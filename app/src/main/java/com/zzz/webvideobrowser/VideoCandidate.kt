package com.zzz.webvideobrowser

data class VideoCandidate(
    val id: String,
    val type: CandidateType,
    val title: String,
    val url: String?,
    val pageUrl: String?,
    val frameSrc: String?,
    val videoId: String?,
    val host: String?,
    val headers: Map<String, String> = emptyMap(),
    val score: Int,
    val confidence: Int,
    val reason: String,
    val seenAt: Long = System.currentTimeMillis()
)
