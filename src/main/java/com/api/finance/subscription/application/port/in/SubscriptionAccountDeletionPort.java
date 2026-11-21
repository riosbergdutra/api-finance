package com.api.finance.subscription.application.port.in;

import java.util.UUID;

/** Public use-case contract for canceling provider billing before local account erasure. */
public interface SubscriptionAccountDeletionPort {
    void cancelarParaExclusao(UUID internalUserId);
}
