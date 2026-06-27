package com.zzz.webvideobrowser

object VideoJs {
    const val SCRIPT = """
(function () {
  if (window.__WB_VIDEO_INSTALLED__) return;
  window.__WB_VIDEO_INSTALLED__ = true;

  // 1. 绝对接管：全局封杀原生控件的渲染
  const style = document.createElement('style');
  style.innerHTML = `
    video::-webkit-media-controls { display: none !important; opacity: 0 !important; pointer-events: none !important; }
    video::-webkit-media-controls-enclosure { display: none !important; opacity: 0 !important; pointer-events: none !important; }
  `;
  document.head.appendChild(style);

  let activeVideo = null;

  function callAndroid(fn) {
    try { fn(); } catch (e) {}
  }

  function notifyActivated(video) {
    callAndroid(function () {
      AndroidVideo.onVideoActivated(document.title || "");
    });
    notifyProgress(video);
  }

  function notifyProgress(video) {
    if (!video) return;
    callAndroid(function () {
      AndroidVideo.onProgress(
        Number(video.currentTime || 0),
        Number(video.duration || 0),
        Boolean(video.paused),
        Number(video.playbackRate || 1)
      );
    });
  }

  function toggle(video) {
    if (!video) return;
    if (video.paused) {
      video.play();
    } else {
      video.pause();
    }
    notifyProgress(video);
  }

  function seekBy(video, seconds) {
    if (!video || !isFinite(video.duration)) return;
    let next = video.currentTime + seconds;
    next = Math.max(0, Math.min(video.duration, next));
    video.currentTime = next;
    notifyProgress(video);
  }

  function bindVideo(video) {
    if (!video || video.__WB_BOUND__) return;
    video.__WB_BOUND__ = true;

    // 强制移除 controls 属性
    video.removeAttribute("controls");
    video.addEventListener("play", function () {
      video.removeAttribute("controls");
    });

    video.addEventListener("play", function () {
      activeVideo = video;
      notifyActivated(video);
    }, true);

    video.addEventListener("pause", function () {
      if (activeVideo === video) notifyProgress(video);
    }, true);

    video.addEventListener("timeupdate", function () {
      if (activeVideo === video) notifyProgress(video);
    }, true);
  }

  function scanVideos() {
    const videos = document.querySelectorAll("video");
    for (const video of videos) bindVideo(video);
  }

  const observer = new MutationObserver(scanVideos);
  observer.observe(document.documentElement, {
    childList: true,
    subtree: true
  });

  scanVideos();

  window.NativeVideo = {
    play: function () {
      if (activeVideo) activeVideo.play();
    },
    pause: function () {
      if (activeVideo) activeVideo.pause();
    },
    toggle: function () {
      toggle(activeVideo);
    },
    seekBy: function (seconds) {
      seekBy(activeVideo, Number(seconds || 0));
    },
    seekTo: function (seconds) {
      if (!activeVideo || !isFinite(activeVideo.duration)) return;
      activeVideo.currentTime = Math.max(0, Math.min(activeVideo.duration, Number(seconds || 0)));
      notifyProgress(activeVideo);
    },
    setRate: function (rate) {
      if (!activeVideo) return;
      activeVideo.playbackRate = Number(rate || 1);
      notifyProgress(activeVideo);
    },
    requestFullscreen: function () {
      if (!activeVideo) return;
      const el = activeVideo.parentElement || activeVideo;
      if (el.requestFullscreen) {
        el.requestFullscreen();
      } else if (el.webkitRequestFullscreen) {
        el.webkitRequestFullscreen();
      }
    },
    getState: function () {
      if (!activeVideo) return null;
      return {
        paused: activeVideo.paused,
        currentTime: activeVideo.currentTime || 0,
        duration: activeVideo.duration || 0,
        rate: activeVideo.playbackRate || 1,
        src: activeVideo.currentSrc || activeVideo.src || ""
      };
    }
  };

  function extractThemeColor() {
    let color = null;
    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme && metaTheme.content) {
      color = metaTheme.content;
    }
    if (!color || color.toLowerCase() === 'transparent') {
      const bg = window.getComputedStyle(document.body).backgroundColor;
      if (bg && bg !== 'rgba(0, 0, 0, 0)' && bg !== 'transparent') {
        color = bg;
      }
    }
    if (!color) color = "#FFFFFF";
    
    let hex = "#FFFFFF";
    if (color.startsWith("#")) {
        hex = color;
    } else if (color.startsWith("rgb")) {
        const rgb = color.match(/\d+/g);
        if (rgb && rgb.length >= 3) {
            hex = "#" + ((1 << 24) + (parseInt(rgb[0]) << 16) + (parseInt(rgb[1]) << 8) + parseInt(rgb[2])).toString(16).slice(1).toUpperCase();
        }
    } else {
        const ctx = document.createElement("canvas").getContext("2d");
        ctx.fillStyle = color;
        hex = ctx.fillStyle;
    }
    
    callAndroid(function () {
      if (window.AndroidVideo && AndroidVideo.onThemeColor) {
         AndroidVideo.onThemeColor(hex);
      }
    });
  }

  setTimeout(extractThemeColor, 300);
  if (document.head) {
      new MutationObserver(extractThemeColor).observe(document.head, { childList: true, subtree: true, attributes: true });
  }

})();
"""
}

