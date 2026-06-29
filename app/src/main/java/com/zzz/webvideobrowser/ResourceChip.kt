package com.zzz.webvideobrowser

data class ResourceChip(
    val text: String,
    val filter: CandidateFilter,
    val level: ChipLevel
)

enum class ChipLevel {
    PRIMARY,
    INFO,
    WARNING,
    DANGER
}
