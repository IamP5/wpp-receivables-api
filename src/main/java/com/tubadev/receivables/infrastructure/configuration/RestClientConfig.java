package com.tubadev.receivables.infrastructure.configuration;

import com.tubadev.receivables.infrastructure.configuration.annotations.WhatsApp;
import com.tubadev.receivables.infrastructure.configuration.properties.WhatsAppProperties;
import com.tubadev.receivables.infrastructure.json.Json;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class RestClientConfig {

    @Bean
    @WhatsApp
    @ConditionalOnProperty(name = "whatsapp.adapter", havingValue = "cloud-api", matchIfMissing = true)
    RestClient whatsAppRestClient(final WhatsAppProperties properties) {
        return whatsAppRestClientBuilder(properties).build();
    }

    public static RestClient.Builder whatsAppRestClientBuilder(final WhatsAppProperties properties) {
        final var httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();

        final var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(properties.readTimeout());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.accessToken())
                // defaults (bytes for media downloads, multipart for uploads) + JSON with the application's mapper
                .configureMessageConverters(converters -> converters
                        .registerDefaults()
                        .withJsonConverter(new JacksonJsonHttpMessageConverter(Json.mapper())));
    }
}
