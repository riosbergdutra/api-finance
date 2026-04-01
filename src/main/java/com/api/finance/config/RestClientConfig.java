package com.api.finance.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class RestClientConfig {

    /**
     * Bean compartilhado do RestClient.
     * Disponível para injeção em qualquer @Service ou @Component.
     * Introduzido no Spring 6.1 / Boot 3.2 como substituto moderno do RestTemplate.
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        return builder.build();
    }
}