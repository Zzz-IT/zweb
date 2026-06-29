package com.zzz.webvideobrowser

enum class CandidateFilter(val label: String) {
    ALL("全部"),
    RECOMMENDED("推荐"),
    CONTROLLABLE("可接管"),
    BLOB("Blob"),
    HLS("HLS"),
    FILE("直链"),
    IFRAME("iframe"),
    FRAGMENT("分片"),
    DIAGNOSTIC("诊断")
}
