package com.zzz.webvideobrowser

enum class CandidateType {
    DOM_VIDEO,       // 页面内可控 video
    DOM_BLOB_VIDEO,  // 页面内 blob video
    IFRAME_PLAYER,   // iframe 播放器页
    HLS_MASTER,      // 主 m3u8 / master playlist
    HLS_MEDIA,       // 子清晰度 m3u8 / media playlist
    DASH,            // mpd
    MP4,             // mp4 直链
    WEBM,            // webm 直链
    BLOB_HINT,       // blob 线索，不直接外放
    FRAGMENT,        // ts / m4s 分片，默认不直接播放
    CUSTOM_VIEW      // WebChromeClient 全屏线索
}
