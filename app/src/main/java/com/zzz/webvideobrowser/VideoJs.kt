package com.zzz.webvideobrowser

object VideoJs {
    const val SCRIPT = """
(function () {
  const VERSION = "2026-06-28-01";

  if (window.__ZWEB_VIDEO_VERSION__ === VERSION) {
    return;
  }
  window.__ZWEB_VIDEO_VERSION__ = VERSION;

  let activeVideo = null;
  let lastNotify = 0;

  function safeCall(fn) {
    try { fn(); } catch (e) {}
  }

  function isVisible(video) {
    if (!video) return false;
    const rect = video.getBoundingClientRect();
    if (rect.width < 80 || rect.height < 45) return false;
    const style = window.getComputedStyle(video);
    if (style.display === "none" || style.visibility === "hidden" || Number(style.opacity) === 0) {
      return false;
    }
    return true;
  }

  function videoScore(video) {
    if (!isVisible(video)) return -1;

    const rect = video.getBoundingClientRect();
    let score = rect.width * rect.height;

    if (!video.paused) score += 100000000;
    if (video.readyState >= 2) score += 1000000;
    if (isFinite(video.duration) && video.duration > 0) score += 10000;
    if (video.currentSrc || video.src) score += 1000;

    return score;
  }

  function pickBestVideo() {
    const videos = Array.from(document.querySelectorAll("video"));
    let best = null;
    let bestScore = -1;

    for (const v of videos) {
      const score = videoScore(v);
      if (score > bestScore) {
        best = v;
        bestScore = score;
      }
    }

    if (best && bestScore > 0) {
      activeVideo = best;
      return best;
    }

    return activeVideo;
  }

  function notifyDetected(video) {
    if (!video) return;

    safeCall(function () {
      AndroidVideo.onVideoDetected(
        document.title || "",
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
        Number(video.currentTime || 0),
        Number(video.duration || 0),
        Boolean(video.paused),
        Number(video.playbackRate || 1)
      );
    });
  }

  function bindVideo(video) {
    if (!video || video.__ZWEB_BOUND__) return;
    video.__ZWEB_BOUND__ = true;

    video.removeAttribute("controls");

    const activate = function () {
      activeVideo = video;
      notifyDetected(video);
      notifyProgress(video, true);
      safeCall(function () {
        AndroidVideo.onVideoActivated(document.title || "");
      });
    };

    video.addEventListener("play", activate, true);
    video.addEventListener("playing", activate, true);
    video.addEventListener("loadedmetadata", function () {
      activeVideo = video;
      notifyDetected(video);
      notifyProgress(video, true);
    }, true);

    video.addEventListener("durationchange", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);

    video.addEventListener("pause", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);

    video.addEventListener("timeupdate", function () {
      if (activeVideo === video) notifyProgress(video, false);
    }, true);

    video.addEventListener("ratechange", function () {
      if (activeVideo === video) notifyProgress(video, true);
    }, true);
  }

  function scanVideos() {
    const videos = Array.from(document.querySelectorAll("video"));
    for (const video of videos) {
      bindVideo(video);
    }

    const picked = pickBestVideo();
    if (picked) {
      notifyDetected(picked);
      notifyProgress(picked, false);
    }
  }

  function toggle(video) {
    video = video || pickBestVideo();
    if (!video) return false;

    if (video.paused) {
      video.play();
    } else {
      video.pause();
    }

    activeVideo = video;
    notifyProgress(video, true);
    return true;
  }

  function seekBy(video, seconds) {
    video = video || pickBestVideo();
    if (!video || !isFinite(video.duration)) return false;

    let next = video.currentTime + Number(seconds || 0);
    next = Math.max(0, Math.min(video.duration, next));
    video.currentTime = next;

    activeVideo = video;
    notifyProgress(video, true);
    return true;
  }

  function seekTo(video, seconds) {
    video = video || pickBestVideo();
    if (!video || !isFinite(video.duration)) return false;

    let next = Math.max(0, Math.min(video.duration, Number(seconds || 0)));
    video.currentTime = next;

    activeVideo = video;
    notifyProgress(video, true);
    return true;
  }

  function setRate(video, rate) {
    video = video || pickBestVideo();
    if (!video) return false;

    video.playbackRate = Number(rate || 1);
    activeVideo = video;
    notifyProgress(video, true);
    return true;
  }

  function requestFullscreen(video) {
    video = video || pickBestVideo();
    if (!video) return false;

    activeVideo = video;

    const el = video.parentElement || video;

    if (el.requestFullscreen) {
      el.requestFullscreen();
      return true;
    }

    if (el.webkitRequestFullscreen) {
      el.webkitRequestFullscreen();
      return true;
    }

    if (video.webkitEnterFullscreen) {
      video.webkitEnterFullscreen();
      return true;
    }

    return false;
  }

  window.NativeVideo = {
    rescan: function () {
      scanVideos();
      return !!pickBestVideo();
    },

    forceActivate: function () {
      const video = pickBestVideo();
      if (!video) return false;

      activeVideo = video;
      notifyDetected(video);
      notifyProgress(video, true);

      safeCall(function () {
        AndroidVideo.onVideoActivated(document.title || "");
      });

      return true;
    },

    play: function () {
      const video = pickBestVideo();
      if (!video) return false;
      video.play();
      activeVideo = video;
      notifyProgress(video, true);
      return true;
    },

    pause: function () {
      const video = pickBestVideo();
      if (!video) return false;
      video.pause();
      activeVideo = video;
      notifyProgress(video, true);
      return true;
    },

    toggle: function () {
      return toggle(activeVideo || pickBestVideo());
    },

    seekBy: function (seconds) {
      return seekBy(activeVideo || pickBestVideo(), Number(seconds || 0));
    },

    seekTo: function (seconds) {
      return seekTo(activeVideo || pickBestVideo(), Number(seconds || 0));
    },

    setRate: function (rate) {
      return setRate(activeVideo || pickBestVideo(), Number(rate || 1));
    },

    requestFullscreen: function () {
      return requestFullscreen(activeVideo || pickBestVideo());
    },

    getState: function () {
      const video = activeVideo || pickBestVideo();
      if (!video) return null;

      return {
        paused: Boolean(video.paused),
        currentTime: Number(video.currentTime || 0),
        duration: Number(video.duration || 0),
        rate: Number(video.playbackRate || 1),
        src: video.currentSrc || video.src || "",
        width: video.videoWidth || 0,
        height: video.videoHeight || 0
      };
    }
  };

  const style = document.createElement("style");
  style.innerHTML = `
    video::-webkit-media-controls {
      display: none !important;
      opacity: 0 !important;
      pointer-events: none !important;
    }
    video::-webkit-media-controls-enclosure {
      display: none !important;
      opacity: 0 !important;
      pointer-events: none !important;
    }
  `;

  if (document.head) {
    document.head.appendChild(style);
  }

  new MutationObserver(scanVideos).observe(document.documentElement, {
    childList: true,
    subtree: true,
    attributes: true,
    attributeFilter: ["src", "style", "class", "controls"]
  });

  scanVideos();
  setInterval(scanVideos, 1000);

  function extractThemeColor() {
    let color = null;

    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme && metaTheme.content) {
      color = metaTheme.content;
    }

    if (!color || color.toLowerCase() === "transparent") {
      const bg = window.getComputedStyle(document.body).backgroundColor;
      if (bg && bg !== "rgba(0, 0, 0, 0)" && bg !== "transparent") {
        color = bg;
      }
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
    } catch (e) {
      hex = "#F2F2F7";
    }

    safeCall(function () {
      AndroidVideo.onThemeColor(hex);
    });
  }

  setTimeout(extractThemeColor, 300);
})();
"""
}
