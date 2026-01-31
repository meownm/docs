const assert = require("assert");
const { createWebcamController } = require("../../backend/app/static/webcam.js");

function flushMicrotasks() {
  return new Promise((resolve) => setImmediate(resolve));
}

function createMockStream() {
  const track = {
    stopped: false,
    stop() {
      this.stopped = true;
    },
    getSettings() {
      return { width: 1280, height: 720 };
    },
  };
  return {
    getTracks() {
      return [track];
    },
    getVideoTracks() {
      return [track];
    },
    _track: track,
  };
}

function createMockElements() {
  const style = () => ({ display: "" });
  const videoEl = {
    style: style(),
    muted: false,
    srcObject: null,
    readyState: 0,
    videoWidth: 640,
    videoHeight: 480,
    playCalls: 0,
    addEventListener(event, handler) {
      if (event === "loadedmetadata") {
        this._loadedHandler = handler;
      }
    },
    removeEventListener(event) {
      if (event === "loadedmetadata") {
        this._loadedHandler = null;
      }
    },
    play() {
      this.playCalls += 1;
      return Promise.resolve();
    },
  };
  const canvasEl = {
    width: 0,
    height: 0,
    getContext() {
      return {
        drawImage() {},
      };
    },
    toBlob(callback) {
      callback({ blob: true });
    },
  };
  const placeholderEl = { style: style() };
  const btnToggle = { textContent: "", disabled: false, addEventListener() {} };
  const btnCameraSwitch = { textContent: "", disabled: false, addEventListener() {} };
  const btnSnapshot = { textContent: "", disabled: false, addEventListener() {} };
  const webcamStatusEl = { textContent: "", className: "" };
  const resultContainer = { style: style() };
  const resultContent = { innerHTML: "" };

  return {
    videoEl,
    canvasEl,
    placeholderEl,
    btnToggle,
    btnCameraSwitch,
    btnSnapshot,
    webcamStatusEl,
    resultContainer,
    resultContent,
  };
}

class MockFormData {
  constructor() {
    this.entries = [];
  }
  append(name, value, filename) {
    this.entries.push({ name, value, filename });
  }
}

async function testStartCameraSuccess() {
  const elements = createMockElements();
  const stream = createMockStream();
  let receivedConstraints = null;
  const controller = createWebcamController({
    ...elements,
    reportError: () => {},
    fetchImpl: () => Promise.resolve({ json: async () => ({}) }),
    mediaDevices: {
      getUserMedia: async (constraints) => {
        receivedConstraints = constraints;
        return stream;
      },
      enumerateDevices: async () => [{ kind: "videoinput" }],
    },
    FormDataClass: MockFormData,
  });

  controller.handleCameraToggle();
  elements.videoEl.readyState = 2;
  await flushMicrotasks();

  assert.ok(receivedConstraints, "getUserMedia called");
  assert.deepStrictEqual(receivedConstraints.video.facingMode, { ideal: "environment" });
  assert.strictEqual(elements.videoEl.srcObject, stream);
  assert.strictEqual(elements.btnSnapshot.disabled, false);
  assert.strictEqual(elements.videoEl.playCalls, 1);
}

async function testStartCameraFailure() {
  const elements = createMockElements();
  const controller = createWebcamController({
    ...elements,
    reportError: () => {},
    fetchImpl: () => Promise.resolve({ json: async () => ({}) }),
    mediaDevices: {
      getUserMedia: async () => {
        throw new Error("denied");
      },
    },
    FormDataClass: MockFormData,
  });

  controller.handleCameraToggle();
  await flushMicrotasks();

  assert.strictEqual(elements.btnSnapshot.disabled, true);
  assert.strictEqual(elements.btnToggle.textContent, "Включить камеру");
  assert.ok(elements.webcamStatusEl.textContent.includes("Ошибка доступа"));
  assert.ok(elements.webcamStatusEl.className.includes("error"));
}

async function testStopCameraStopsTracks() {
  const elements = createMockElements();
  const stream = createMockStream();
  const controller = createWebcamController({
    ...elements,
    reportError: () => {},
    fetchImpl: () => Promise.resolve({ json: async () => ({}) }),
    mediaDevices: {
      getUserMedia: async () => stream,
      enumerateDevices: async () => [{ kind: "videoinput" }],
    },
    FormDataClass: MockFormData,
  });

  controller.handleCameraToggle();
  elements.videoEl.readyState = 2;
  await flushMicrotasks();

  controller.stopCamera();

  assert.strictEqual(stream._track.stopped, true);
  assert.strictEqual(elements.videoEl.srcObject, null);
  assert.strictEqual(elements.btnSnapshot.disabled, true);
}

async function testSnapshotIntegration() {
  const elements = createMockElements();
  const stream = createMockStream();
  let fetchCalled = false;
  const controller = createWebcamController({
    ...elements,
    reportError: () => {},
    fetchImpl: async (url, options) => {
      fetchCalled = true;
      assert.strictEqual(url, "/api/ocr/passport/v2");
      assert.ok(options.body instanceof MockFormData);
      return {
        json: async () => ({
          status: "ok",
          model_confidence: 0.72,
          fields: {
            document_number: { value: "123456", confidence: 0.9, text_type: "printed", language: "ru" },
            date_of_birth: { value: "1990-01-01", confidence: 0.8 },
            date_of_expiry: { value: "2030-01-01", confidence: 0.8 },
          },
          mrz: {
            document_number: { value: "123456", confidence: 0.9 },
            date_of_birth: { value: "1990-01-01", confidence: 0.8 },
            date_of_expiry: { value: "2030-01-01", confidence: 0.8 },
          },
        }),
      };
    },
    mediaDevices: {
      getUserMedia: async () => stream,
      enumerateDevices: async () => [{ kind: "videoinput" }],
    },
    FormDataClass: MockFormData,
  });

  controller.handleCameraToggle();
  elements.videoEl.readyState = 2;
  await flushMicrotasks();

  await controller.handleSnapshot();

  assert.strictEqual(fetchCalled, true);
  assert.strictEqual(elements.resultContainer.style.display, "block");
  assert.ok(elements.resultContent.innerHTML.includes("Номер документа"));
  assert.ok(elements.resultContent.innerHTML.includes("Интегральная уверенность"));
}

module.exports = {
  testStartCameraSuccess,
  testStartCameraFailure,
  testStopCameraStopsTracks,
  testSnapshotIntegration,
};
