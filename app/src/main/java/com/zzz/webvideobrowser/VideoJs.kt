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

  function diagnoseVideos() {
    const videos = Array.from(document.querySelectorAll("video"));
    const items = videos.map(function (video, index) {
      const rect = video.getBoundingClientRect();
      const style = window.getComputedStyle(video);
      return {
        index: index,
        id: getVideoId(video),
        isActive: video === activeVideo,
        isPendingGesture: video === pendingGestureVideo,
        isLastPlay: video === lastPlayVideo,
        paused: Boolean(video.paused),
        currentTime: Number(video.currentTime || 0),
        duration: Number(video.duration || 0),
        readyState: Number(video.readyState || 0),
        networkState: Number(video.networkState || 0),
        playbackRate: Number(video.playbackRate || 1),
        currentSrc: video.currentSrc || "",
        src: video.src || "",
        width: Number(video.videoWidth || 0),
        height: Number(video.videoHeight || 0),
        rectWidth: Number(rect.width || 0),
        rectHeight: Number(rect.height || 0),
        rectLeft: Number(rect.left || 0),
        rectTop: Number(rect.top || 0),
        display: style.display || "",
        visibility: style.visibility || "",
        opacity: style.opacity || "",
        score: videoScore(video)
      };
    });

    const iframes = Array.from(document.querySelectorAll("iframe")).map(function (iframe, index) {
      const rect = iframe.getBoundingClientRect();
      let sameOrigin = false;
      let childVideoCount = -1;
      try {
        const doc = iframe.contentWindow && iframe.contentWindow.document;
        if (doc) {
          sameOrigin = true;
          childVideoCount = doc.querySelectorAll("video").length;
        }
      } catch (e) {
        sameOrigin = false;
        childVideoCount = -1;
      }
      return {
        index: index,
        src: iframe.src || "",
        rectWidth: Number(rect.width || 0),
        rectHeight: Number(rect.height || 0),
        sameOrigin: sameOrigin,
        childVideoCount: childVideoCount
      };
    });

    return {
      version: window.__ZWEB_VIDEO_VERSION__ || "",
      pageUrl: location.href,
      title: document.title || "",
      activeVideoId: activeVideo ? getVideoId(activeVideo) : "",
      pendingGestureVideoId: pendingGestureVideo ? getVideoId(pendingGestureVideo) : "",
      lastPlayVideoId: lastPlayVideo ? getVideoId(lastPlayVideo) : "",
      lastGestureAgeMs: lastGesture ? Date.now() - lastGesture.time : -1,
      videoCount: videos.length,
      iframeCount: iframes.length,
      videos: items,
      iframes: iframes
    };
  }

  let theaterVideo = null;
  let theaterRoot = null;
  let theaterSnapshot = null;

  function enterTheaterById(id) {
    const videos = Array.from(document.querySelectorAll("video"));
    let video = null;

    for (const v of videos) {
      if (getVideoId(v) === id) {
        video = v;
        break;
      }
    }

    if (!video) return false;

    exitTheater();

    theaterVideo = video;

    theaterSnapshot = {
      bodyOverflow: document.body.style.overflow,
      htmlOverflow: document.documentElement.style.overflow,
      parent: video.parentNode,
      nextSibling: video.nextSibling,
      videoStyle: video.getAttribute("style") || "",
      videoClass: video.getAttribute("class") || "",
      controls: video.hasAttribute("controls")
    };

    document.documentElement.style.overflow = "hidden";
    document.body.style.overflow = "hidden";

    theaterRoot = document.createElement("div");
    theaterRoot.id = "__zweb_theater_root__";
    theaterRoot.style.position = "fixed";
    theaterRoot.style.left = "0";
    theaterRoot.style.top = "0";
    theaterRoot.style.width = "100vw";
    theaterRoot.style.height = "100vh";
    theaterRoot.style.zIndex = "2147483646";
    theaterRoot.style.background = "#000";
    theaterRoot.style.display = "flex";
    theaterRoot.style.alignItems = "center";
    theaterRoot.style.justifyContent = "center";
    theaterRoot.style.pointerEvents = "none";

    document.documentElement.appendChild(theaterRoot);
    theaterRoot.appendChild(video);

    video.style.position = "relative";
    video.style.left = "auto";
    video.style.top = "auto";
    video.style.width = "100vw";
    video.style.height = "100vh";
    video.style.maxWidth = "100vw";
    video.style.maxHeight = "100vh";
    video.style.objectFit = "contain";
    video.style.background = "#000";
    video.style.opacity = "1";
    video.style.visibility = "visible";
    video.style.display = "block";
    video.style.zIndex = "2147483647";
    video.style.pointerEvents = "auto";

    video.removeAttribute("controls");

    activeVideo = video;
    activateVideo(video, "manual-theater-video-only");

    return true;
  }

  function exitTheater() {
    if (!theaterVideo || !theaterSnapshot) {
      if (theaterRoot && theaterRoot.parentNode) {
        theaterRoot.parentNode.removeChild(theaterRoot);
      }
      theaterRoot = null;
      return false;
    }

    const video = theaterVideo;
    const snap = theaterSnapshot;

    try {
      if (snap.parent) {
        if (snap.nextSibling && snap.nextSibling.parentNode === snap.parent) {
          snap.parent.insertBefore(video, snap.nextSibling);
        } else {
          snap.parent.appendChild(video);
        }
      }

      if (snap.videoStyle) {
        video.setAttribute("style", snap.videoStyle);
      } else {
        video.removeAttribute("style");
      }

      if (snap.videoClass) {
        video.setAttribute("class", snap.videoClass);
      } else {
        video.removeAttribute("class");
      }

      if (snap.controls) {
        video.setAttribute("controls", "");
      } else {
        video.removeAttribute("controls");
      }

      document.body.style.overflow = snap.bodyOverflow || "";
      document.documentElement.style.overflow = snap.htmlOverflow || "";
    } catch (e) {}

    if (theaterRoot && theaterRoot.parentNode) {
      theaterRoot.parentNode.removeChild(theaterRoot);
    }

    theaterVideo = null;
    theaterRoot = null;
    theaterSnapshot = null;

    return true;
  }

  window.NativeVideo = {
    enterTheaterById: function (id) {
      return enterTheaterById(id);
    },
    exitTheater: function () {
      return exitTheater();
    },
    listVideos: function () {
      scanVideos();
      const videos = Array.from(document.querySelectorAll("video"));
      return videos.map(function (video, index) {
        return {
          index: index,
          id: getVideoId(video),
          title: document.title || "",
          paused: Boolean(video.paused),
          currentTime: Number(video.currentTime || 0),
          duration: Number(video.duration || 0),
          readyState: Number(video.readyState || 0),
          currentSrc: video.currentSrc || "",
          src: video.src || "",
          width: Number(video.videoWidth || 0),
          height: Number(video.videoHeight || 0),
          score: videoScore(video)
        };
      }).filter(function (item) {
        return item.score > 0 || item.readyState > 0 || item.currentSrc || item.src;
      });
    },
    activateById: function (id) {
      const videos = Array.from(document.querySelectorAll("video"));
      for (const video of videos) {
        if (getVideoId(video) === id) {
          return activateVideo(video, "manual-activate");
        }
      }
      return false;
    },
    diagnose: function () {
      return diagnoseVideos();
    },
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

    Array.from(document.querySelectorAll("iframe")).forEach(function (frame) {
      if (!frame.__ZWEB_REPORTED__) {
        try {
          if (frame.contentWindow && frame.contentWindow.document) {
            // Same origin, will be handled by its own script injection
          }
        } catch (e) {
          frame.__ZWEB_REPORTED__ = true;
          safeCall(function () {
            AndroidVideo.onIframeDetected(frame.src || "");
          });
        }
      }
    });
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
