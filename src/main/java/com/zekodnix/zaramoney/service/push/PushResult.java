package com.zekodnix.zaramoney.service.push;

/**
 * Outcome of one Expo push. {@code ticketId}: Expo's ticket when accepted; {@code detail}: Expo's or our error text.
 */
public record PushResult(Outcome outcome, String ticketId, String detail) {
    public enum Outcome {
        /** Expo accepted the message (ticket "ok"). */
        SENT,
        /** Expo says the token is dead ("DeviceNotRegistered"): stop using it. */
        INVALID_TOKEN,
        /** Rejected by Expo, or still failing after the retries (network, 5xx, 429, timeout). */
        FAILED,
    }

    public static PushResult sent(String ticketId) {
        return new PushResult(Outcome.SENT, ticketId, null);
    }

    public static PushResult invalidToken(String detail) {
        return new PushResult(Outcome.INVALID_TOKEN, null, detail);
    }

    public static PushResult failed(String detail) {
        return new PushResult(Outcome.FAILED, null, detail);
    }

    public boolean delivered() {
        return outcome == Outcome.SENT;
    }
}
