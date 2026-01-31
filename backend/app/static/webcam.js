(function (global) {
  "use strict";

  function createWebcamController(options) {
    if (!options) {
      throw new Error("Webcam options are required");
    }

    const {
      videoEl,
      canvasEl,
      placeholderEl,
      btnToggle,
      btnCameraSwitch,
      btnSnapshot,
      webcamStatusEl,
      resultContainer,
      resultContent,
      reportError,
      fetchImpl,
      mediaDevices,
      FormDataClass,
    } = options;

    if (!videoEl || !canvasEl || !placeholderEl) {
      throw new Error("Missing required DOM elements for webcam");
    }

    const state = {
      mediaStream: null,
      isRecognizing: false,
      currentFacingMode: "environment",
      hasMultipleCameras: false,
      lastGestureAt: 0,
      requestToken: 0,
    };

    const deps = {
      fetchImpl: fetchImpl || (global.fetch ? global.fetch.bind(global) : null),
      mediaDevices: mediaDevices || (global.navigator ? global.navigator.mediaDevices : null),
      FormDataClass: FormDataClass || global.FormData,
    };

    function setStatus(message, variant) {
      if (!webcamStatusEl) return;
      webcamStatusEl.textContent = message || "";
      webcamStatusEl.className = variant ? `webcam-status ${variant}` : "webcam-status";
    }

    function setUiIdle() {
      videoEl.style.display = "none";
      placeholderEl.style.display = "flex";
      btnToggle.textContent = "Включить камеру";
      btnSnapshot.disabled = true;
      btnCameraSwitch.disabled = true;
      setStatus("", "");
    }

    function setUiRequesting() {
      placeholderEl.style.display = "flex";
      videoEl.style.display = "none";
      btnToggle.textContent = "Выключить камеру";
      btnSnapshot.disabled = true;
      btnCameraSwitch.disabled = true;
      setStatus("Запрашиваю доступ к камере...", "");
    }

    function setUiActive({ cameraType, width, height }) {
      placeholderEl.style.display = "none";
      videoEl.style.display = "block";
      btnToggle.textContent = "Выключить камеру";
      btnSnapshot.disabled = false;
      btnCameraSwitch.disabled = !state.hasMultipleCameras;
      setStatus(`Камера активна: ${cameraType} (${width}×${height})`, "success");
    }

    function setUiError(message) {
      setUiIdle();
      setStatus(message, "error");
    }

    function stopCamera(options = {}) {
      const { resetUi = true } = options;
      if (state.mediaStream) {
        state.mediaStream.getTracks().forEach((track) => track.stop());
        state.mediaStream = null;
      }
      if (videoEl.srcObject) {
        videoEl.srcObject = null;
      }
      if (resetUi) {
        setUiIdle();
      }
    }

    async function checkMultipleCameras() {
      if (!deps.mediaDevices || !deps.mediaDevices.enumerateDevices) {
        state.hasMultipleCameras = false;
        return;
      }
      try {
        const devices = await deps.mediaDevices.enumerateDevices();
        const videoDevices = devices.filter((device) => device.kind === "videoinput");
        state.hasMultipleCameras = videoDevices.length > 1;
      } catch (err) {
        state.hasMultipleCameras = false;
      }
    }

    function buildConstraints() {
      return {
        video: {
          width: { ideal: 2560 },
          height: { ideal: 1440 },
          facingMode: { ideal: state.currentFacingMode },
        },
        audio: false,
      };
    }

    async function waitForMetadata() {
      if (videoEl.readyState >= 2) {
        return;
      }
      await new Promise((resolve) => {
        const handler = () => {
          videoEl.removeEventListener("loadedmetadata", handler);
          resolve();
        };
        videoEl.addEventListener("loadedmetadata", handler);
      });
    }

    async function startCameraFromPromise(streamPromise) {
      const requestToken = ++state.requestToken;
      try {
        const stream = await streamPromise;
        if (requestToken !== state.requestToken) {
          stream.getTracks().forEach((track) => track.stop());
          return;
        }
        state.mediaStream = stream;
        videoEl.srcObject = stream;
        await waitForMetadata();
        await videoEl.play();
        await checkMultipleCameras();

        const track = stream.getVideoTracks()[0];
        const settings = track.getSettings ? track.getSettings() : {};
        const actualWidth = settings.width || videoEl.videoWidth;
        const actualHeight = settings.height || videoEl.videoHeight;
        const cameraType = state.currentFacingMode === "user" ? "передняя" : "задняя";

        setUiActive({ cameraType, width: actualWidth, height: actualHeight });
      } catch (err) {
        handleCameraError(err);
      }
    }

    function requestCameraFromGesture() {
      if (!deps.mediaDevices || !deps.mediaDevices.getUserMedia) {
        setUiError("Камера не поддерживается в этом браузере");
        if (reportError) {
          reportError({
            error_message: "Camera not supported",
            context_json: { feature: "mediaDevices.getUserMedia" },
          });
        }
        return;
      }

      setUiRequesting();
      const streamPromise = deps.mediaDevices.getUserMedia(buildConstraints());
      startCameraFromPromise(streamPromise);
    }

    function handleCameraError(err) {
      const message = err && err.message ? err.message : "Неизвестная ошибка";
      console.error("Camera error:", err);
      stopCamera();
      setUiError(`Ошибка доступа к камере: ${message}`);
      btnCameraSwitch.disabled = true;
      if (reportError) {
        reportError({
          error_message: `Camera access error: ${message}`,
          context_json: {
            error_name: err && err.name ? err.name : "unknown",
            facingMode: state.currentFacingMode,
          },
        });
      }
    }

    function isDuplicateGesture() {
      const now = Date.now();
      if (now - state.lastGestureAt < 400) {
        return true;
      }
      state.lastGestureAt = now;
      return false;
    }

    function handleCameraToggle() {
      if (isDuplicateGesture()) return;
      if (state.mediaStream) {
        stopCamera();
        return;
      }
      requestCameraFromGesture();
    }

    function handleCameraSwitch() {
      if (isDuplicateGesture()) return;
      if (!state.mediaStream || state.isRecognizing) return;
      state.currentFacingMode =
        state.currentFacingMode === "environment" ? "user" : "environment";
      stopCamera({ resetUi: false });
      requestCameraFromGesture();
    }

    async function handleSnapshot() {
      if (!state.mediaStream || state.isRecognizing) return;
      if (!deps.fetchImpl || !deps.FormDataClass) {
        setStatus("Распознавание недоступно", "error");
        return;
      }

      state.isRecognizing = true;
      btnSnapshot.disabled = true;
      btnSnapshot.textContent = "Распознавание...";
      setStatus("Делаю снимок и отправляю на распознавание...", "");
      resultContainer.style.display = "none";

      try {
        const track = state.mediaStream.getVideoTracks()[0];
        const settings = track.getSettings ? track.getSettings() : {};
        const width = settings.width || videoEl.videoWidth;
        const height = settings.height || videoEl.videoHeight;

        canvasEl.width = width;
        canvasEl.height = height;
        const ctx = canvasEl.getContext("2d");
        ctx.drawImage(videoEl, 0, 0, width, height);

        const blob = await new Promise((resolve) => {
          canvasEl.toBlob(resolve, "image/jpeg", 0.95);
        });

        const formData = new deps.FormDataClass();
        formData.append("image", blob, "snapshot.jpg");

        const response = await deps.fetchImpl("/api/recognize", {
          method: "POST",
          body: formData,
        });

        const result = await response.json();
        resultContainer.style.display = "block";
        if (result.error) {
          resultContent.innerHTML = `<div class="result-error">Ошибка: ${escapeHtml(result.error)}</div>`;
          setStatus("Распознавание завершено с ошибкой", "error");
        } else {
          resultContent.innerHTML = `
            <div class="result-success">
              <div class="result-row"><span class="result-label">Номер документа:</span> <span class="result-value">${escapeHtml(result.document_number || "—")}</span></div>
              <div class="result-row"><span class="result-label">Дата рождения:</span> <span class="result-value">${formatDate(result.date_of_birth)}</span></div>
              <div class="result-row"><span class="result-label">Дата истечения:</span> <span class="result-value">${formatDate(result.date_of_expiry)}</span></div>
            </div>
          `;
          setStatus("Распознавание успешно завершено", "success");
        }
      } catch (err) {
        console.error("Recognition error:", err);
        resultContainer.style.display = "block";
        resultContent.innerHTML = `<div class="result-error">Ошибка сети: ${escapeHtml(err.message || "")}</div>`;
        setStatus("Ошибка при распознавании", "error");
        if (reportError) {
          reportError({
            error_message: `Recognition request error: ${err.message || ""}`,
            stacktrace: err.stack || null,
          });
        }
      } finally {
        state.isRecognizing = false;
        btnSnapshot.disabled = false;
        btnSnapshot.textContent = "Сделать снимок и распознать";
      }
    }

    function escapeHtml(str) {
      if (!str) return "";
      return String(str)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
    }

    function formatDate(yymmdd) {
      if (!yymmdd || yymmdd.length !== 6) return yymmdd || "—";
      const yy = yymmdd.substring(0, 2);
      const mm = yymmdd.substring(2, 4);
      const dd = yymmdd.substring(4, 6);
      const year = parseInt(yy, 10);
      const fullYear = year > 50 ? 1900 + year : 2000 + year;
      return `${dd}.${mm}.${fullYear}`;
    }

    function init() {
      videoEl.muted = true;
      if (!btnToggle || !btnCameraSwitch || !btnSnapshot) {
        throw new Error("Missing required webcam control buttons");
      }

      const wrapGesture = (handler) => (event) => {
        if (event && event.type && event.type.startsWith("touch")) {
          event.preventDefault();
        }
        handler();
      };

      btnToggle.addEventListener("click", wrapGesture(handleCameraToggle));
      btnToggle.addEventListener("touchend", wrapGesture(handleCameraToggle));
      btnCameraSwitch.addEventListener("click", wrapGesture(handleCameraSwitch));
      btnCameraSwitch.addEventListener("touchend", wrapGesture(handleCameraSwitch));
      btnSnapshot.addEventListener("click", handleSnapshot);

      setUiIdle();
    }

    return {
      init,
      handleCameraToggle,
      handleCameraSwitch,
      handleSnapshot,
      stopCamera,
      getState: () => ({ ...state }),
      _test: {
        buildConstraints,
        requestCameraFromGesture,
        startCameraFromPromise,
        handleCameraError,
      },
    };
  }

  const WebcamUI = {
    createWebcamController,
    init(options) {
      const controller = createWebcamController(options);
      controller.init();
      return controller;
    },
  };

  if (typeof module !== "undefined" && module.exports) {
    module.exports = { createWebcamController };
  }

  global.WebcamUI = WebcamUI;
})(typeof window !== "undefined" ? window : globalThis);
