package com.api.finance.scheduler;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita @Scheduled no contexto Spring.
 *
 * O scheduler do Spring é independente do executor HTTP do Tomcat.
 * O número de threads é configurado em spring.task.scheduling.pool.size.
 *
 * Em múltiplas instâncias, cada instância executa seus próprios @Scheduled.
 * Para execução distribuída exatamente uma vez, será necessário um lock
 * distribuído ou Quartz com JobStore persistente antes de escalar horizontalmente.
 */
@Configuration
@EnableScheduling
public class SchedulerConfig {
}
