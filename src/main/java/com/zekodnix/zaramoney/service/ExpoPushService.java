package com.zekodnix.zaramoney.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.Map;

@Service
public class ExpoPushService {

    private final WebClient webClient;

    public ExpoPushService(WebClient.Builder builder) {
        this.webClient = builder.baseUrl("https://exp.host/--/api/v2").build();
    }

    public void sendPush(String expoToken,
                         String title,
                         String body,
                         Map<String, Object> data) {

        if (expoToken == null || expoToken.isBlank()) {
            throw new IllegalArgumentException("Expo token is missing");
        }

        if (title == null || title.isBlank()) {
            title = "Notification";
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("to", expoToken);
        payload.put("title", title);
        payload.put("body", body);
        payload.put("data", data);
        payload.put("sound", "default");

        webClient.post()
            .uri("/push/send")
            .header("Content-Type", "application/json")
            .bodyValue(payload)
            .retrieve()
            .bodyToMono(String.class)
            .subscribe(
                res -> System.out.println("Push sent: " + res),
                err -> System.err.println("Push error: " + err.getMessage())
            );
    }
}
