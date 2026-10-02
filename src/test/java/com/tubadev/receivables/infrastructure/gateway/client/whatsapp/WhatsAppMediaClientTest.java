package com.tubadev.receivables.infrastructure.gateway.client.whatsapp;

import com.tubadev.receivables.domain.message.MediaFile;
import com.tubadev.receivables.infrastructure.configuration.RestClientConfig;
import com.tubadev.receivables.infrastructure.configuration.properties.WhatsAppProperties;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.time.Duration;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("integrationTest")
class WhatsAppMediaClientTest {

    private static final String MEDIA_URL = "https://lookaside.fbsbx.com/whatsapp_business/attachments/?mid=123";

    private MockRestServiceServer server;
    private WhatsAppMediaClient target;

    @BeforeEach
    void setUp() {
        final var properties = new WhatsAppProperties("cloud-api", "https://graph.facebook.com", "v23.0", "1357259444133753",
                "test-token", null, null, Duration.ofSeconds(1), Duration.ofSeconds(1), List.of());
        final var builder = RestClientConfig.whatsAppRestClientBuilder(properties);
        server = MockRestServiceServer.bindTo(builder).build();
        target = new WhatsAppMediaClient(builder.build(), properties);
    }

    @Test
    void givenMediaId_whenDownloading_shouldResolveUrlThenFetchWithToken() {
        server.expect(requestTo("https://graph.facebook.com/v23.0/media-123"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"url":"%s","mime_type":"image/jpeg","sha256":"abc","file_size":3,"id":"media-123","messaging_product":"whatsapp"}
                        """.formatted(MEDIA_URL), MediaType.APPLICATION_JSON));
        server.expect(requestTo(MEDIA_URL))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andRespond(withSuccess(new byte[]{1, 2, 3}, MediaType.IMAGE_JPEG));

        final var media = target.download("media-123").orElseThrow();

        Assertions.assertEquals(new MediaFile(new byte[]{1, 2, 3}, "image/jpeg", null), media);
        server.verify();
    }

    @Test
    void givenExpiredMedia_whenDownloading_shouldReturnEmpty() {
        server.expect(requestTo("https://graph.facebook.com/v23.0/media-123"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"Unsupported get request\",\"code\":100}}"));

        Assertions.assertTrue(target.download("media-123").isEmpty());
    }

    @Test
    void givenDocument_whenUploading_shouldSendMultipartAndReturnMediaId() {
        server.expect(requestTo("https://graph.facebook.com/v23.0/1357259444133753/media"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Content-Type", containsString("multipart/form-data")))
                .andExpect(content().string(containsString("name=\"messaging_product\"")))
                .andExpect(content().string(containsString("filename=\"contrato.pdf\"")))
                .andExpect(content().string(containsString("%PDF-1.7")))
                .andRespond(withSuccess("{\"id\":\"media-up-1\"}", MediaType.APPLICATION_JSON));

        final var mediaId = target.upload(new MediaFile("%PDF-1.7".getBytes(), "application/pdf", "contrato.pdf"));

        Assertions.assertEquals("media-up-1", mediaId.orElseThrow());
        server.verify();
    }
}
