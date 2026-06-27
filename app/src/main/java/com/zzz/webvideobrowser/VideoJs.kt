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
  let lastTapTime = 0;

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

    // 2. 绝对接管：Z轴强行提权，把视频层浮到网站自带的控制层上面
    const computed = window.getComputedStyle(video);
    if (computed.position === 'static') {
      video.style.setProperty('position', 'relative', 'important');
    }
    video.style.setProperty('z-index', '2147483647', 'important');
    
    // 强制移除 controls 属性
    video.removeAttribute("controls");
    video.addEventListener("play", function () {
      video.removeAttribute("controls");
    });

    let downX = 0;
    let downY = 0;
    let downTime = 0;
    let oldRate = 1;
    let longPressTimer = null;
    let longPressActive = false;
    let moved = false;

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

    // 3. 绝对接管：直接在最高层级的 video 上强行拦截手势，并掐断向网页事件的传递
    video.addEventListener("pointerdown", function (e) {
      activeVideo = video;
      downX = e.clientX;
      downY = e.clientY;
      downTime = Date.now();
      moved = false;
      longPressActive = false;
      oldRate = video.playbackRate || 1;

      clearTimeout(longPressTimer);
      longPressTimer = setTimeout(function () {
        longPressActive = true;
        video.playbackRate = 2.0;
        callAndroid(function () {
          AndroidVideo.onHint("2.0x");
        });
      }, 450);
      
      e.stopPropagation(); // 绝对接管：阻止事件冒泡到网页原 UI
    }, true);

    video.addEventListener("pointermove", function (e) {
      const dx = e.clientX - downX;
      const dy = e.clientY - downY;
      if (Math.abs(dx) > 16 || Math.abs(dy) > 16) moved = true;

      if (Math.abs(dx) > 40 && Math.abs(dx) > Math.abs(dy) * 1.2) {
        clearTimeout(longPressTimer);
        callAndroid(function () {
          const sec = Math.round(dx / 8);
          AndroidVideo.onHint((sec >= 0 ? "+" : "") + sec + "s");
        });
        e.preventDefault();
        e.stopPropagation();
      }
    }, true);

    video.addEventListener("pointerup", function (e) {
      clearTimeout(longPressTimer);

      const dx = e.clientX - downX;
      const dy = e.clientY - downY;
      const dt = Date.now() - downTime;

      if (longPressActive) {
        video.playbackRate = oldRate;
        longPressActive = false;
        callAndroid(function () {
          AndroidVideo.onHint("恢复 " + oldRate + "x");
        });
        e.preventDefault();
        e.stopPropagation();
        return;
      }

      if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.2) {
        seekBy(video, Math.round(dx / 8));
        e.preventDefault();
        e.stopPropagation();
        return;
      }

      if (!moved && dt < 260) {
        const now = Date.now();
        if (now - lastTapTime < 320) {
          toggle(video);
          lastTapTime = 0;
          e.preventDefault();
          e.stopPropagation();
          return;
        }
        lastTapTime = now;
      }
      
      e.stopPropagation(); // 绝对接管：阻止网页的原生点击处理
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
})();
"""
}
