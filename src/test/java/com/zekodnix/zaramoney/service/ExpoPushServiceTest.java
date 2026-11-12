package com.zekodnix.zaramoney.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.service.push.PushResult;
import com.zekodnix.zaramoney.service.push.PushResult.Outcome;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/** Expo client against a local HTTP server: ticket parsing, which failures are retried, masking, metrics. */
class ExpoPushServiceTest {

    private static final String TOKEN = "ExponentPushToken[abcdefghij123456]";
    private static final String OK = "{\"data\":{\"status\":\"ok\",\"id\":\"ticket-1\"}}";
    private static final String NOT_REGISTERED =
        "{\"data\":{\"status\":\"error\",\"message\":\"not a registered push token\",\"details\":{\"error\":\"DeviceNotRegistered\"}}}";

    /** One scripted reply per request: HTTP status, body, delay. The last one repeats. */
    private record Reply(int status, String body, long delayMs) {}

    private HttpServer server;
    private final Deque<Reply> replies = new ArrayDeque<>();
    private final AtomicInteger requests = new AtomicInteger();
    private SimpleMeterRegistry metrics;
    private ExpoPushService service;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/push/send", exchange -> {
            requests.incrementAndGet();
            Reply reply = replies.size() > 1 ? replies.poll() : replies.peek();
            try {
                Thread.sleep(reply.delayMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool()); // a slow reply must not block the retry
        server.start();

        ApplicationProperties props = new ApplicationProperties();
        props.getPush().setExpoBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        props.getPush().setMaxRetries(2);
        props.getPush().setInitialBackoff(Duration.ofMillis(10));
        props.getPush().setTimeout(Duration.ofSeconds(2)); // > HTTP client cold start
        metrics = new SimpleMeterRegistry();
        service = new ExpoPushService(WebClient.builder(), props, metrics);
    }

    @AfterEach
    void stop() {
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }

    private PushResult send() {
        return service.sendPush(TOKEN, "Title", "Body", Map.of("type", "TEST")).block(Duration.ofSeconds(10));
    }

    @Test
    void acceptedTicketIsSent() {
        replies.add(new Reply(200, OK, 0));
        PushResult result = send();
        assertThat(result.outcome()).isEqualTo(Outcome.SENT);
        assertThat(result.ticketId()).isEqualTo("ticket-1");
        assertThat(requests).hasValue(1);
        assertThat(metrics.counter("zaramoney.push", "result", "sent").count()).isEqualTo(1);
    }

    @Test
    void deviceNotRegisteredIsFinalAndReportedAsInvalidToken() {
        replies.add(new Reply(200, NOT_REGISTERED, 0));
        assertThat(send().outcome()).isEqualTo(Outcome.INVALID_TOKEN);
        assertThat(requests).hasValue(1);
        assertThat(metrics.counter("zaramoney.push", "result", "invalid_token").count()).isEqualTo(1);
    }

    @Test
    void serverErrorsAndRateLimitsAreRetried() {
        replies.add(new Reply(503, "{}", 0));
        replies.add(new Reply(429, "{}", 0));
        replies.add(new Reply(200, OK, 0));
        assertThat(send().outcome()).isEqualTo(Outcome.SENT);
        assertThat(requests).hasValue(3);
    }

    @Test
    void persistentServerErrorsFailAfterTheRetries() {
        replies.add(new Reply(500, "{}", 0));
        PushResult result = send();
        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.detail()).isEqualTo("HTTP 500");
        assertThat(requests).hasValue(3); // 1 + maxRetries
        assertThat(metrics.counter("zaramoney.push", "result", "failed").count()).isEqualTo(1);
    }

    @Test
    void clientErrorsAreNotRetried() {
        replies.add(new Reply(400, "{\"errors\":[{\"code\":\"VALIDATION_ERROR\"}]}", 0));
        assertThat(send().outcome()).isEqualTo(Outcome.FAILED);
        assertThat(requests).hasValue(1);
    }

    @Test
    void slowAnswersTimeOutAndAreRetried() {
        replies.add(new Reply(200, OK, 4_000)); // > 2 s timeout
        replies.add(new Reply(200, OK, 0));
        assertThat(send().outcome()).isEqualTo(Outcome.SENT);
        assertThat(requests).hasValue(2);
    }

    @Test
    void otherExpoErrorsFailWithoutRetry() {
        replies.add(new Reply(200, "{\"data\":{\"status\":\"error\",\"message\":\"too big\",\"details\":{\"error\":\"MessageTooBig\"}}}", 0));
        PushResult result = send();
        assertThat(result.outcome()).isEqualTo(Outcome.FAILED);
        assertThat(result.detail()).isEqualTo("MessageTooBig: too big");
        assertThat(requests).hasValue(1);
    }

    @Test
    void missingTokenFailsWithoutCallingExpo() {
        assertThat(service.sendPush(" ", "t", "b", Map.of()).block().outcome()).isEqualTo(Outcome.FAILED);
        assertThat(requests).hasValue(0);
    }

    @Test
    void tokensAreMaskedInLogs() {
        assertThat(ExpoPushService.mask(TOKEN)).isEqualTo("ExponentPushToken[…123456]").doesNotContain("abcdefghij");
        assertThat(ExpoPushService.mask(null)).isEqualTo("<none>");
    }
}
