package com.api.finance.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita @Scheduled no contexto Spring.
 *
 * Os jobs rodam em Virtual Threads pois o executor padrão do Spring
 * herda o executor configurado em FinanceApplication (TomcatProtocolHandlerCustomizer).
 *
 * Para produção com múltiplas instâncias: substitua @Scheduled por Quartz
 * com JobStoreTX (PostgreSQL) para garantir que cada job rode apenas uma vez
 * mesmo com N instâncias do app ativas simultaneamente.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
