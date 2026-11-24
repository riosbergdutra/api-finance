package com.api.finance.bff.event;

/**
 * Published after a BFF session has been removed from Redis.
 * Consumers use this event to revoke resources tied to the opaque session.
 */
public record BffSessionRevokedEvent(String sessionId) {
}
