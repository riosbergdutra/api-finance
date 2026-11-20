package com.api.finance.bff.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Internal authentication result. The session identifier is only used by the
 * controller to issue an HttpOnly cookie and is never serialized to the browser.
 */
public record AuthResponseDTO(@JsonIgnore String sessionId) {
}
