package com.api.finance.user.application.port;

import java.util.UUID;

/** Identificadores públicos do usuário para processamento interno em lote. */
public record UserIdentity(UUID internalUserId, UUID identityProviderUserId) {
}
