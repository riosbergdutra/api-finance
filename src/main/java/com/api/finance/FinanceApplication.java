package com.api.finance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.embedded.tomcat.TomcatProtocolHandlerCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executors;

@SpringBootApplication
@EnableAsync
public class FinanceApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinanceApplication.class, args);
	}

	/**
	 * Habilita Virtual Threads do Java 21 no Tomcat.
	 * Com threads virtuais, o gargalo migra para o banco de dados —
	 * pool de conexões mal configurado derruba tudo. Ver application.yaml.
	 */
	@Bean
	public TomcatProtocolHandlerCustomizer<?> virtualThreadsCustomizer() {
		return handler -> handler.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
	}

}
