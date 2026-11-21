package com.api.finance.dashboard.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * Pool isolado e limitado para consultas JDBC paralelas do dashboard.
 * Evita ocupar o ForkJoinPool.commonPool com operações bloqueantes de banco.
 * Quatro workers limitam a concorrência por instância; fila limitada evita
 * crescimento ilimitado de memória sob rajadas. CallerRuns aplica backpressure.
 */
@Configuration
public class DashboardExecutorConfig {

    @Bean(name = "dashboardExecutor")
    public ThreadPoolTaskExecutor dashboardExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("finance-dashboard-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
