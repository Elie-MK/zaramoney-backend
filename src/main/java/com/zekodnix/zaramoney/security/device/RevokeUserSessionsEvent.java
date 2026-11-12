package com.zekodnix.zaramoney.security.device;

/**
 * Published when a user's credentials or security state change (password change/reset, PIN change, PIN lock).
 * Handled synchronously by {@code SessionService}, inside the publisher's transaction.
 *
 * @param keepSid session to keep (the one that made the change), or {@code null} to revoke all
 */
public record RevokeUserSessionsEvent(Long userId, String keepSid, String reason) {}
