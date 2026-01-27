package com.demo.passport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
        assertEquals("/api/passport/recognize", request.getPath());
        assertTrue(request.getHeader("Content-Type").contains("multipart/form-data"));
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
}
