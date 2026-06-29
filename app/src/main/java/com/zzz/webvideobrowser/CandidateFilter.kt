package com.zzz.webvideobrowser

enum class CandidateFilter(val label: String) {
    ALL("全部"),
    RECOMMENDED("推荐"),
    CONTROLLABLE("可接管"),
    BLOB("Blob"),
    IFRAME("iframe"),
    FALLBACK("兜底")
}
