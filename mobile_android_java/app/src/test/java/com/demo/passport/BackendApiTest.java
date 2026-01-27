package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class BackendApiTest {
    private MockWebServer server;

    @Before
    public void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        BackendConfig.setBaseUrlForTesting(server.url("").toString().replaceAll("/$", ""));
    }

    @After
    public void tearDown() throws Exception {
        BackendConfig.setBaseUrlForTesting(BackendConfig.DEFAULT_BASE_URL);
        BackendApi.resetErrorReportDebounceForTesting();
        BackendApi.setDebugListener(null);
        server.shutdown();
    }

    @Test
    public void recognizePassport_parsesMrzFromSuccessResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"mrz\":{\"document_number\":\"123\",\"date_of_birth\":\"1990-01-01\",\"date_of_expiry\":\"2030-01-01\"}}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Models.MRZKeys> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.recognizePassport(new byte[] {0x01, 0x02}, new BackendApi.Callback<Models.MRZKeys>() {
            @Override
            public void onSuccess(Models.MRZKeys value) {
                result.set(value);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("123", result.get().document_number);
        assertEquals("1990-01-01", result.get().date_of_birth);
        assertEquals("2030-01-01", result.get().date_of_expiry);
        assertEquals(null, error.get());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/recognize", request.getPath());
        assertTrue(request.getHeader("Content-Type").contains("multipart/form-data"));
    }

    @Test
    public void recognizePassport_parsesMrzFromFlatResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"document_number\":\"456\",\"date_of_birth\":\"1985-05-05\",\"date_of_expiry\":\"2035-05-05\"}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Models.MRZKeys> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.recognizePassport(new byte[] {0x03, 0x04}, new BackendApi.Callback<Models.MRZKeys>() {
            @Override
            public void onSuccess(Models.MRZKeys value) {
                result.set(value);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("456", result.get().document_number);
        assertEquals("1985-05-05", result.get().date_of_birth);
        assertEquals("2035-05-05", result.get().date_of_expiry);
        assertEquals(null, error.get());
    }

    @Test
    public void recognizePassport_handlesHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(500).setBody("boom"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.recognizePassport(new byte[] {0x01}, new BackendApi.Callback<Models.MRZKeys>() {
            @Override
            public void onSuccess(Models.MRZKeys value) {
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertTrue(error.get().contains("HTTP 500"));
    }

    @Test
    public void recognizePassport_handlesErrorPayload() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"error\":\"bad photo\"}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.recognizePassport(new byte[] {0x01}, new BackendApi.Callback<Models.MRZKeys>() {
            @Override
            public void onSuccess(Models.MRZKeys value) {
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertTrue(error.get().contains("RECOGNIZE_ERROR"));
    }

    @Test
    public void recognizePassport_handlesMissingMrzFields() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"mrz\":{\"document_number\":\"123\"}}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.recognizePassport(new byte[] {0x01}, new BackendApi.Callback<Models.MRZKeys>() {
            @Override
            public void onSuccess(Models.MRZKeys value) {
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertTrue(error.get().contains("missing MRZ fields"));
    }

    @Test
    public void recognizePassport_emitsRawResponseToDebugListener() throws Exception {
        String rawJson = "{\"mrz\":{\"document_number\":\"789\",\"date_of_birth\":\"1999-09-09\",\"date_of_expiry\":\"2039-09-09\"}}";
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(rawJson));

        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch debugLatch = new CountDownLatch(1);
        AtomicReference<Models.MRZKeys> result = new AtomicReference<>();
        AtomicReference<String> debug = new AtomicReference<>();

        BackendApi.setDebugListener((source, raw) -> {
            if ("recognize".equals(source)) {
                debug.set(raw);
                debugLatch.countDown();
            }
        });

        BackendApi.recognizePassport(new byte[] {0x05}, new BackendApi.Callback<Models.MRZKeys>() {
            @Override
            public void onSuccess(Models.MRZKeys value) {
                result.set(value);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                latch.countDown();
            }
        });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertTrue("Debug listener timeout", debugLatch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals(rawJson, debug.get());
    }

    @Test
    public void recognizePassport_parsesMrzFromTopLevelFields() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"document_number\":\"456\",\"date_of_birth\":\"1991-02-02\",\"date_of_expiry\":\"2031-02-02\"}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Models.MRZKeys> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.recognizePassport(new byte[] {0x03, 0x04}, new BackendApi.Callback<Models.MRZKeys>() {
            @Override
            public void onSuccess(Models.MRZKeys value) {
                result.set(value);
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("456", result.get().document_number);
        assertEquals("1991-02-02", result.get().date_of_birth);
        assertEquals("2031-02-02", result.get().date_of_expiry);
        assertEquals(null, error.get());
    }

    @Test
    public void sendNfcRaw_postsJsonPayload() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.sendNfcRaw(JsonParser.parseString("{\"foo\":\"bar\"}").getAsJsonObject(),
                new BackendApi.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                        latch.countDown();
                    }
                });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertEquals(null, error.get());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/nfc", request.getPath());
        assertTrue(request.getHeader("Content-Type").contains("application/json"));
        assertTrue(request.getBody().readUtf8().contains("\"foo\":\"bar\""));
    }

    @Test
    public void sendNfcRaw_postsNfcPayloadBuilderOutput() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        Models.NfcResult result = new Models.NfcResult();
        result.passport = new java.util.HashMap<>();
        result.passport.put("doc", "999");
        result.faceImageJpeg = new byte[NfcPayloadBuilder.MIN_FACE_IMAGE_BYTES];

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.sendNfcRaw(NfcPayloadBuilder.build(result), new BackendApi.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertEquals(null, error.get());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"doc\":\"999\""));
        assertTrue(body.contains("\"face_image_b64\""));
    }

    @Test
    public void sendNfcRaw_handlesHttpError() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(400).setBody("bad nfc"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();
        CountDownLatch debugLatch = new CountDownLatch(1);
        AtomicReference<String> debug = new AtomicReference<>();

        BackendApi.setDebugListener((source, raw) -> {
            if ("nfc".equals(source)) {
                debug.set(raw);
                debugLatch.countDown();
            }
        });

        BackendApi.sendNfcRaw(JsonParser.parseString("{\"baz\":1}").getAsJsonObject(),
                new BackendApi.Callback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                        latch.countDown();
                    }
                });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertTrue("Debug listener timeout", debugLatch.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertTrue(error.get().contains("HTTP 400"));
        assertEquals("bad nfc", debug.get());
    }

    @Test
    public void sendNfcRawAndParse_returnsScanResponse() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"scan_id\":\"scan-1\",\"face_image_url\":\"/api/nfc/scan-1/face.jpg\",\"passport\":{\"doc\":\"x\"}}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Models.NfcScanResponse> response = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.sendNfcRawAndParse(JsonParser.parseString("{\"payload\":1}").getAsJsonObject(),
                new BackendApi.Callback<Models.NfcScanResponse>() {
                    @Override
                    public void onSuccess(Models.NfcScanResponse value) {
                        response.set(value);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                        latch.countDown();
                    }
                });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(response.get());
        assertEquals("scan-1", response.get().scan_id);
        assertEquals("/api/nfc/scan-1/face.jpg", response.get().face_image_url);
        assertEquals("x", response.get().passport.get("doc").getAsString());
        assertEquals(null, error.get());
    }

    @Test
    public void sendNfcRawAndParse_handlesMissingFields() throws Exception {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{\"scan_id\":\"scan-1\"}"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.sendNfcRawAndParse(JsonParser.parseString("{\"payload\":1}").getAsJsonObject(),
                new BackendApi.Callback<Models.NfcScanResponse>() {
                    @Override
                    public void onSuccess(Models.NfcScanResponse value) {
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                        latch.countDown();
                    }
                });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(error.get());
        assertTrue(error.get().contains("missing response fields"));
    }

    @Test
    public void fetchFaceImage_returnsBytes() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("JPEGDATA"));

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<byte[]> result = new AtomicReference<>();
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.fetchFaceImage(server.url("/api/nfc/scan-1/face.jpg").toString(),
                new BackendApi.Callback<byte[]>() {
                    @Override
                    public void onSuccess(byte[] value) {
                        result.set(value);
                        latch.countDown();
                    }

                    @Override
                    public void onError(String message) {
                        error.set(message);
                        latch.countDown();
                    }
                });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertNotNull(result.get());
        assertEquals("JPEGDATA", new String(result.get()));
        assertEquals(null, error.get());
    }

    @Test
    public void reportError_postsPayloadWithContext() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        JsonObject context = JsonParser.parseString(
                "{\"request_url\":\"https://example.com/recognize\",\"method\":\"POST\",\"http_status\":500,\"response_body\":\"boom\"}"
        ).getAsJsonObject();

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> error = new AtomicReference<>();

        BackendApi.reportError("HTTP 500: boom", "trace", context, new BackendApi.Callback<Void>() {
            @Override
            public void onSuccess(Void value) {
                latch.countDown();
            }

            @Override
            public void onError(String message) {
                error.set(message);
                latch.countDown();
            }
        });

        assertTrue("Callback timeout", latch.await(5, TimeUnit.SECONDS));
        assertEquals(null, error.get());

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);
        assertEquals("POST", request.getMethod());
        assertEquals("/errors", request.getPath());
        String body = request.getBody().readUtf8();
        assertTrue(body.contains("\"platform\":\"android\""));
        assertTrue(body.contains("\"error_message\":\"HTTP 500: boom\""));
        assertTrue(body.contains("\"stacktrace\":\"trace\""));
        assertTrue(body.contains("\"context_json\""));
        assertTrue(body.contains("\"http_status\":500"));
        assertTrue(body.contains("\"response_body\":\"boom\""));
    }

    @Test
    public void reportError_debounceSkipsSecondRequest() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200));

        BackendApi.setErrorReportIntervalMsForTesting(10000);
        BackendApi.reportError("first", null, null, null);
        BackendApi.reportError("second", null, null, null);

        RecordedRequest request = server.takeRequest(5, TimeUnit.SECONDS);
        assertNotNull(request);

        RecordedRequest second = server.takeRequest(500, TimeUnit.MILLISECONDS);
        assertEquals(null, second);
    }
}
