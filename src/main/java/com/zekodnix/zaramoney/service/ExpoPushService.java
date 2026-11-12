package com.zekodnix.zaramoney.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.zekodnix.zaramoney.config.ApplicationProperties;
import com.zekodnix.zaramoney.service.push.PushResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * Expo push API client ({@code POST /push/send}). Never throws and never blocks: returns a {@link Mono} of the outcome.
 * <ul>
 *   <li>timeout per attempt ({@code application.push.timeout})</li>
 *   <li>retries with exponential backoff ({@code max-retries}, {@code initial-backoff}) only for transient failures:
 *       network errors, timeouts, HTTP 5xx and 429. A 4xx or an Expo "error" ticket is final</li>
 *   <li>Expo answers HTTP 200 per message: {@code data.status = "ok"} → SENT; {@code "error"} with
 *       {@code details.error = "DeviceNotRegistered"} → INVALID_TOKEN (the caller removes the token); other errors → FAILED</li>
 * </ul>
 * Counter {@code zaramoney.push{result=sent|invalid_token|failed}}. Tokens are masked in logs.
 * Not done: the second step of Expo's protocol (polling push receipts for delivery errors).
 */
@Service
public class ExpoPushService {

    private static final Logger LOG = LoggerFactory.getLogger(ExpoPushService.class);

    private final WebClient webClient;
    private final ApplicationProperties.Push config;
    private final MeterRegistry meterRegistry;

    public ExpoPushService(WebClient.Builder builder, ApplicationProperties properties, MeterRegistry meterRegistry) {
        this.config = properties.getPush();
        this.webClient = builder.baseUrl(config.getExpoBaseUrl()).build();
        this.meterRegistry = meterRegistry;
    }

    public Mono<PushResult> sendPush(String expoToken, String title, String body, Map<String, Object> data) {
        if (expoToken == null || expoToken.isBlank()) {
            return Mono.just(record(null, PushResult.failed("no push token")));
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("to", expoToken);
        payload.put("title", title == null || title.isBlank() ? "Notification" : title);
        payload.put("body", body);
        payload.put("data", data);
        payload.put("sound", "default");
        payload.put("badge", 1);

        return webClient
            .post()
            .uri("/push/send")
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(JsonNode.class)
            .timeout(config.getTimeout())
            .retryWhen(
                Retry.backoff(config.getMaxRetries(), config.getInitialBackoff())
                    .filter(ExpoPushService::isTransient)
                    .doBeforeRetry(s ->
                        LOG.warn("Expo push to {} failed ({}), retry {}", mask(expoToken), describe(s.failure()), s.totalRetries() + 1)
                    )
            )
            .map(ExpoPushService::parseTicket)
            .onErrorResume(e -> Mono.just(PushResult.failed(describe(e))))
            .map(result -> record(expoToken, result));
    }

    /** Expo ticket for a single message: {@code {"data": {"status": "ok", "id": "..."}}} or status "error". */
    static PushResult parseTicket(JsonNode response) {
        JsonNode ticket = response == null ? null : response.path("data");
        if (ticket != null && ticket.isArray()) {
            ticket = ticket.path(0);
        }
        if (ticket == null || ticket.isMissingNode() || ticket.isNull()) {
            return PushResult.failed("unexpected Expo response: " + response);
        }
        if ("ok".equals(ticket.path("status").asText())) {
            return PushResult.sent(ticket.path("id").asText(null));
        }
        String error = ticket.path("details").path("error").asText("");
        String message = ticket.path("message").asText("Expo error");
        return "DeviceNotRegistered".equals(error) ? PushResult.invalidToken(message) : PushResult.failed(error.isEmpty() ? message : error + ": " + message);
    }

    static boolean isTransient(Throwable e) {
        if (e instanceof WebClientResponseException r) {
            return r.getStatusCode().is5xxServerError() || r.getStatusCode().value() == 429;
        }
        return e instanceof WebClientRequestException || e instanceof TimeoutException;
    }

    private PushResult record(String token, PushResult result) {
        meterRegistry.counter("zaramoney.push", "result", result.outcome().name().toLowerCase()).increment();
        switch (result.outcome()) {
            case SENT -> LOG.debug("Expo push to {} accepted, ticket {}", mask(token), result.ticketId());
            case INVALID_TOKEN -> LOG.info("Expo push to {}: device not registered ({})", mask(token), result.detail());
            case FAILED -> LOG.error("PUSH FAILED to {}: {}", mask(token), result.detail());
        }
        return result;
    }

    private static String describe(Throwable e) {
        Throwable cause = Exceptions.isRetryExhausted(e) && e.getCause() != null ? e.getCause() : e;
        if (cause instanceof WebClientResponseException r) {
            return "HTTP " + r.getStatusCode().value();
        }
        if (cause instanceof TimeoutException) {
            return "timeout";
        }
        return cause.getClass().getSimpleName() + (cause.getMessage() == null ? "" : ": " + cause.getMessage());
    }

    /** "ExponentPushToken[…ab12cd]": enough to correlate, not enough to reuse. */
    static String mask(String token) {
        if (token == null) {
            return "<none>";
        }
        String tail = token.length() > 7 ? token.substring(token.length() - 7, token.length() - 1) : "";
        int open = token.indexOf('[');
        return (open > 0 ? token.substring(0, open) : "token") + "[…" + tail + "]";
    }
}
