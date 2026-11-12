package com.zekodnix.zaramoney.security.device;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * In-memory, single-instance store for login challenges and used request nonces.
 * With several app instances, move it to Redis so all instances share it.
 */
@Component
public class NonceStore {

    public static final Duration CHALLENGE_TTL = Duration.ofMinutes(2);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Map<String, Long> challenges = new ConcurrentHashMap<>();
    private final Map<String, Long> usedNonces = new ConcurrentHashMap<>();
    private Clock clock = Clock.systemUTC();

    @Autowired(required = false)
    void setClock(Clock clock) {
        this.clock = clock;
    }

    /** New single-use login challenge, valid {@link #CHALLENGE_TTL}. */
    public String issueChallenge() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        challenges.put(nonce, clock.millis() + CHALLENGE_TTL.toMillis());
        return nonce;
    }

    /** True once per issued, unexpired challenge. */
    public boolean consumeChallenge(String nonce) {
        if (nonce == null) {
            return false;
        }
        Long expiresAt = challenges.remove(nonce);
        return expiresAt != null && expiresAt > clock.millis();
    }

    /** Records a request nonce; false if it was already used (replay). */
    public boolean markUsed(String key, Duration ttl) {
        long now = clock.millis();
        Long previous = usedNonces.putIfAbsent(key, now + ttl.toMillis());
        if (previous == null) {
            return true;
        }
        if (previous <= now) {
            // expired entry not purged yet: reuse is allowed again (the timestamp check already bounds replays)
            usedNonces.put(key, now + ttl.toMillis());
            return true;
        }
        return false;
    }

    @Scheduled(fixedDelay = 60_000)
    public void purge() {
        long now = clock.millis();
        challenges.values().removeIf(exp -> exp <= now);
        usedNonces.values().removeIf(exp -> exp <= now);
    }
}
