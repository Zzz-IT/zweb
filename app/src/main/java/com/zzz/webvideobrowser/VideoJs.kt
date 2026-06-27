package com.zzz.webvideobrowser

object VideoJs {
    const val SCRIPT = """
(function () {
  const VERSION = "2026-06-28-takeover-v2";

  if (window.__ZWEB_VIDEO_VERSION__ === VERSION) {
    return;
  }
  window.__ZWEB_VIDEO_VERSION__ = VERSION;

  let activeVideo = null;
  let pendingGestureVideo = null;
  let lastGesture = null;
  let lastPlayVideo = null;
  let videoIdSeed = 1;
  let lastNotify = 0;

  function safeCall(fn) {
    try { fn(); } catch (e) {}
  }

  function getVideoId(video) {
    if (!video) return "";
    if (!video.__ZWEB_VIDEO_ID__) {
      video.__ZWEB_VIDEO_ID__ = "zweb-video-" + videoIdSeed++;
    }
    return video.__ZWEB_VIDEO_ID__;
  }

  function findVideoInPath(event) {
    const path = event.composedPath ? event.composedPath() : [];
    for (const node of path) {
      if (node && node.tagName && node.tagName.toLowerCase() === "video") {
        return node;
      }
    }
    let el = event.target;
    while (el) {
      if (el.tagName && el.tagName.toLowerCase() === "video") {
        return el;
      }
      el = el.parentElement || el.host || null;
    }
    return null;
  }

  function isDomVisibleEnough(video) {
    if (!video) return false;
    const rect = video.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return false;
    const style = window.getComputedStyle(video);
    if (style.display === "none") return false;
    if (style.visibility === "hidden") return false;
    return true;
  }

  function findNearestVideo(x, y) {
    const videos = Array.from(document.querySelectorAll("video"));
    let best = null;
    let bestScore = -Infinity;

    for (const video of videos) {
      const rect = video.getBoundingClientRect();
      if (rect.width <= 0 || rect.height <= 0) continue;

      const inside = x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom;
      const cx = rect.left + rect.width / 2;
      const cy = rect.top + rect.height / 2;
      const dx = x - cx;
      const dy = y - cy;
      const dist = Math.sqrt(dx * dx + dy * dy);

      let score = 0;
      if (inside) score += 100000000;
      score += Math.min(rect.width * rect.height, 3000000);
      score -= dist * 1000;

      if (!video.paused) score += 50000000;
      if (video.readyState >= 2) score += 20000000;
      if (isFinite(video.duration) && video.duration > 0) score += 10000000;
      if (video.currentTime > 0) score += 5000000;
      if (video.currentSrc || video.src) score += 1000000;

      if (!isDomVisibleEnough(video)) {
        score -= 100000;
      }

      if (score > bestScore) {
        bestScore = score;
        best = video;
      }
    }
    return best;
  }

  function rememberUserGestureFromPoint(x, y, event) {
    let video = null;
    if (event) video = findVideoInPath(event);
    if (!video) video = findNearestVideo(x, y);

    lastGesture = { x: x, y: y, time: Date.now(), video: video || null };

    if (video) {
      pendingGestureVideo = video;
      bindVideo(video);
    }
  }

  function rememberPointerGesture(event) {
    rememberUserGestureFromPoint(event.clientX || 0, event.clientY || 0, event);
  }

  function rememberTouchGesture(event) {
    if (event.touches && event.touches.length > 0) {
      const t = event.touches[0];
      rememberUserGestureFromPoint(t.clientX || 0, t.clientY || 0, event);
    }
  }

  document.addEventListener("pointerdown", rememberPointerGesture, true);
  document.addEventListener("touchstart", rememberTouchGesture, true);
  document.addEventListener("click", rememberPointerGesture, true);

  function activateVideo(video, reason) {
    if (!video) return false;
    activeVideo = video;
    lastPlayVideo = video;
    bindVideo(video);

    const id = getVideoId(video);
    safeCall(function () {
      AndroidVideo.onVideoActivated(document.title || "", id, reason || "unknown");
    });

    notifyDetected(video);
    notifyProgress(video, true);
    return true;
  }

  function hookMediaPlay() {
    if (window.__ZWEB_PLAY_HOOKED__) return;
    window.__ZWEB_PLAY_HOOKED__ = true;

    const rawPlay = HTMLMediaElement.prototype.play;
    HTMLMediaElement.prototype.play = function () {
      const media = this;
      if (media && media.tagName && media.tagName.toLowerCase() === "video") {
        const now = Date.now();
        if (pendingGestureVideo === media || (lastGesture && lastGesture.video === media && now - lastGesture.time < 3000)) {
          activateVideo(media, "user-play");
        } else {
          activateVideo(media, "play-hook");
        }
      }
      return rawPlay.apply(this, arguments);
    };
  }

  function bindVideo(video) {
    if (!video || video.__ZWEB_BOUND__) return;
    video.__ZWEB_BOUND__ = true;

    getVideoId(video);

    const activate = function (reason) {
      return function () { activateVideo(video, reason); };
    };

    video.addEventListener("play", activate("event-play"), true);
    video.addEventListener("playing", activate("event-playing"), true);
    video.addEventListener("loadedmetadata", activate("loadedmetadata"), true);
    video.addEventListener("loadeddata", activate("loadeddata"), true);
    video.addEventListener("canplay", activate("canplay"), true);
    video.addEventListener("canplaythrough", activate("canplaythrough"), true);

    video.addEventListener("pause", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);
    video.addEventListener("timeupdate", function () {
      if (activeVideo === video) notifyProgress(video, false);
    }, true);
    video.addEventListener("durationchange", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);
    video.addEventListener("ratechange", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);
    video.addEventListener("seeking", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);
    video.addEventListener("seeked", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);
    video.addEventListener("waiting", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);
    video.addEventListener("stalled", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);

    video.addEventListener("emptied", function () {
      if (activeVideo === video) {
        safeCall(function () { AndroidVideo.onVideoLost(getVideoId(video), "emptied"); });
      }
    }, true);
    video.addEventListener("error", function () {
      if (activeVideo === video) {
        safeCall(function () { AndroidVideo.onVideoLost(getVideoId(video), "error"); });
      }
    }, true);
  }

  function notifyDetected(video) {
    if (!video) return;
    safeCall(function () {
      AndroidVideo.onVideoDetected(
        document.title || "",
        getVideoId(video),
        Boolean(video.paused),
        Number(video.currentTime || 0),
        Number(video.duration || 0)
      );
    });
  }

  function notifyProgress(video, force) {
    if (!video) return;
    const now = Date.now();
    if (!force && now - lastNotify < 450) return;
    lastNotify = now;

    safeCall(function () {
      AndroidVideo.onProgress(
        getVideoId(video),
        Number(video.currentTime || 0),
        Number(video.duration || 0),
        Boolean(video.paused),
        Number(video.playbackRate || 1)
      );
    });
  }

  function videoScore(video) {
    if (!video) return -1;
    const rect = video.getBoundingClientRect();
    let score = 0;
    if (!video.paused) score += 100000000;
    if (video.readyState >= 2) score += 50000000;
    if (isFinite(video.duration) && video.duration > 0) score += 10000000;
    if (video.currentTime > 0) score += 5000000;
    if (video.currentSrc || video.src) score += 1000000;
    if (rect.width > 0 && rect.height > 0) score += Math.min(rect.width * rect.height, 3000000);

    const style = window.getComputedStyle(video);
    if (style.display === "none" || style.visibility === "hidden") score -= 100000;
    return score;
  }

  function pickActuallyPlayingVideo() {
    const videos = Array.from(document.querySelectorAll("video"));
    for (const video of videos) {
      if (!video.paused && video.readyState >= 2) return video;
    }
    for (const video of videos) {
      if (video.currentTime > 0 || video.readyState >= 2) return video;
    }
    return null;
  }

  function pickBestVideo() {
    const videos = Array.from(document.querySelectorAll("video"));
    let best = null;
    let bestScore = -1;
    for (const video of videos) {
      bindVideo(video);
      const score = videoScore(video);
      if (score > bestScore) {
        best = video;
        bestScore = score;
      }
    }
    return bestScore > 0 ? best : null;
  }

  window.NativeVideo = {
    rescan: function () {
      scanVideos();
      return !!(activeVideo || pickActuallyPlayingVideo() || pickBestVideo());
    },
    forceActivate: function () {
      const video = pendingGestureVideo || (lastGesture && Date.now() - lastGesture.time < 5000 ? lastGesture.video : null) || lastPlayVideo || activeVideo || pickActuallyPlayingVideo() || pickBestVideo();
      if (!video) return false;
      return activateVideo(video, "force");
    },
    play: function () {
      const video = activeVideo || pickActuallyPlayingVideo() || pickBestVideo();
      if (!video) return false;
      activateVideo(video, "native-play");
      video.play();
      return true;
    },
    pause: function () {
      const video = activeVideo || pickActuallyPlayingVideo() || pickBestVideo();
      if (!video) return false;
      video.pause();
      notifyProgress(video, true);
      return true;
    },
    toggle: function () {
      const video = activeVideo || pickActuallyPlayingVideo() || pickBestVideo();
      if (!video) return false;
      activateVideo(video, "native-toggle");
      if (video.paused) video.play(); else video.pause();
      notifyProgress(video, true);
      return true;
    },
    seekBy: function (seconds) {
      const video = activeVideo || pickActuallyPlayingVideo() || pickBestVideo();
      if (!video || !isFinite(video.duration)) return false;
      let next = video.currentTime + Number(seconds || 0);
      video.currentTime = Math.max(0, Math.min(video.duration, next));
      notifyProgress(video, true);
      return true;
    },
    seekTo: function (seconds) {
      const video = activeVideo || pickActuallyPlayingVideo() || pickBestVideo();
      if (!video || !isFinite(video.duration)) return false;
      video.currentTime = Math.max(0, Math.min(video.duration, Number(seconds || 0)));
      notifyProgress(video, true);
      return true;
    },
    setRate: function (rate) {
      const video = activeVideo || pickActuallyPlayingVideo() || pickBestVideo();
      if (!video) return false;
      video.playbackRate = Number(rate || 1);
      notifyProgress(video, true);
      return true;
    },
    requestFullscreen: function () {
      const video = activeVideo || pickActuallyPlayingVideo() || pickBestVideo();
      if (!video) return false;
      activateVideo(video, "native-fullscreen");
      const el = video.parentElement || video;
      if (el.requestFullscreen) return el.requestFullscreen(), true;
      if (el.webkitRequestFullscreen) return el.webkitRequestFullscreen(), true;
      if (video.webkitEnterFullscreen) return video.webkitEnterFullscreen(), true;
      return false;
    },
    getState: function () {
      const video = activeVideo || pickActuallyPlayingVideo() || pickBestVideo();
      if (!video) return null;
      return {
        id: getVideoId(video),
        paused: Boolean(video.paused),
        currentTime: Number(video.currentTime || 0),
        duration: Number(video.duration || 0),
        rate: Number(video.playbackRate || 1),
        src: video.currentSrc || video.src || "",
        width: video.videoWidth || 0,
        height: video.videoHeight || 0,
        readyState: Number(video.readyState || 0)
      };
    }
  };

  const style = document.createElement("style");
  style.innerHTML = `
    video::-webkit-media-controls { display: none !important; opacity: 0 !important; pointer-events: none !important; }
    video::-webkit-media-controls-enclosure { display: none !important; opacity: 0 !important; pointer-events: none !important; }
  `;
  if (document.head) document.head.appendChild(style);

  function scanVideos() {
    const videos = Array.from(document.querySelectorAll("video"));
    for (const video of videos) bindVideo(video);
    const playing = pickActuallyPlayingVideo();
    if (playing && !activeVideo) activateVideo(playing, "scan-playing");
  }

  hookMediaPlay();
  scanVideos();

  new MutationObserver(function () { scanVideos(); }).observe(document.documentElement, {
    childList: true, subtree: true, attributes: true, attributeFilter: ["src", "style", "class", "controls"]
  });

  setInterval(scanVideos, 1000);

  function extractThemeColor() {
    let color = null;
    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme && metaTheme.content) color = metaTheme.content;
    if (!color || color.toLowerCase() === "transparent") {
      const bg = window.getComputedStyle(document.body).backgroundColor;
      if (bg && bg !== "rgba(0, 0, 0, 0)" && bg !== "transparent") color = bg;
    }
    if (!color) color = "#F2F2F7";

    let hex = "#F2F2F7";
    try {
      if (color.startsWith("#")) {
        hex = color;
      } else if (color.startsWith("rgb")) {
        const rgb = color.match(/\d+/g);
        if (rgb && rgb.length >= 3) {
          hex = "#" + ((1 << 24) + (parseInt(rgb[0]) << 16) + (parseInt(rgb[1]) << 8) + parseInt(rgb[2])).toString(16).slice(1).toUpperCase();
        }
      }
    } catch (e) { hex = "#F2F2F7"; }
    safeCall(function () { AndroidVideo.onThemeColor(hex); });
  }

  setTimeout(extractThemeColor, 300);
})();
"""
}
