package com.tubadev.receivables.infrastructure.gateway.client.whatsapp;

import com.tubadev.receivables.domain.message.MessageContent;
import com.tubadev.receivables.domain.message.SendResult;
import com.tubadev.receivables.domain.person.PhoneNumber;
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

import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@Tag("integrationTest")
class WhatsAppCloudApiClientTest {

    private static final String URL = "https://graph.facebook.com/v23.0/1357259444133753/messages";

    private MockRestServiceServer server;
    private WhatsAppCloudApiClient target;

    @BeforeEach
    void setUp() {
        final var properties = new WhatsAppProperties("cloud-api", "https://graph.facebook.com", "v23.0", "1357259444133753",
                "test-token", null, null, Duration.ofSeconds(1), Duration.ofSeconds(1), List.of("1"));
        final var builder = RestClientConfig.whatsAppRestClientBuilder(properties);
        server = MockRestServiceServer.bindTo(builder).build();
        target = new WhatsAppCloudApiClient(builder.build(), properties);
    }

    @Test
    void givenTemplate_whenAccepted_shouldReturnWamid() {
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer test-token"))
                .andExpect(content().json("""
                        {
                          "messaging_product": "whatsapp",
                          "recipient_type": "individual",
                          "to": "15551234567",
                          "type": "template",
                          "template": {
                            "name": "antecipacao_disponivel",
                            "language": { "code": "pt_BR" },
                            "components": [
                              { "type": "body", "parameters": [ { "type": "text", "text": "Maria" } ] }
                            ]
                          }
                        }
                        """, org.springframework.test.json.JsonCompareMode.STRICT))
                .andRespond(withSuccess("""
                        {"messaging_product":"whatsapp","contacts":[{"input":"15551234567","wa_id":"15551234567"}],
                         "messages":[{"id":"wamid.HBgLMTU1NTEyMzQ1NjcVAgARGBI"}]}
                        """, MediaType.APPLICATION_JSON));

        final var result = target.send(new PhoneNumber("15551234567"),
                new MessageContent.Template("antecipacao_disponivel", "pt_BR", List.of("Maria")));

        Assertions.assertEquals(new SendResult.Accepted("wamid.HBgLMTU1NTEyMzQ1NjcVAgARGBI"), result);
        server.verify();
    }

    @Test
    void givenDocument_shouldSendDocumentMessageWithUploadedMedia() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.type").value("document"))
                .andExpect(jsonPath("$.document.id").value("media-up-1"))
                .andExpect(jsonPath("$.document.filename").value("contrato.pdf"))
                .andExpect(jsonPath("$.document.caption").value("Seu contrato"))
                .andExpect(jsonPath("$.image").doesNotExist())
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.doc\"}]}", MediaType.APPLICATION_JSON));

        final var result = target.send(new PhoneNumber("15551234567"),
                new MessageContent.Media("document", "media-up-1", "application/pdf", "Seu contrato", "contrato.pdf"));

        Assertions.assertEquals(new SendResult.Accepted("wamid.doc"), result);
        server.verify();
    }

    @Test
    void givenRecipientOutsideAllowedCountries_shouldRejectWithoutCallingTheApi() {
        final var result = target.send(new PhoneNumber("5511988887777"), new MessageContent.Text("Olá"));

        Assertions.assertInstanceOf(SendResult.Rejected.class, result);
        Assertions.assertEquals(RecipientNotAllowed.CODE, ((SendResult.Rejected) result).code());
        server.verify();
    }

    @Test
    void givenButtons_shouldSendInteractiveMessage() {
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.type").value("interactive"))
                .andExpect(jsonPath("$.interactive.type").value("button"))
                .andExpect(jsonPath("$.interactive.action.buttons[0].reply.id").value("CONFIRM_OFFER"))
                .andExpect(jsonPath("$.text").doesNotExist())
                .andRespond(withSuccess("{\"messages\":[{\"id\":\"wamid.2\"}]}", MediaType.APPLICATION_JSON));

        final var result = target.send(new PhoneNumber("15551234567"), new MessageContent.Buttons("Confirma?",
                List.of(new MessageContent.Buttons.Button("CONFIRM_OFFER", "Confirmar"))));

        Assertions.assertEquals(new SendResult.Accepted("wamid.2"), result);
    }

    @Test
    void givenGraphApiError_shouldReturnRejectionWithCode() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("""
                        {"error":{"message":"(#131047) Re-engagement message","type":"OAuthException","code":131047,
                          "error_data":{"messaging_product":"whatsapp","details":"More than 24 hours have passed"},
                          "fbtrace_id":"AbCdEf"}}
                        """));

        final var result = target.send(new PhoneNumber("15551234567"), new MessageContent.Text("oi"));

        final var rejected = Assertions.assertInstanceOf(SendResult.Rejected.class, result);
        Assertions.assertEquals("131047", rejected.code());
        Assertions.assertEquals("(#131047) Re-engagement message: More than 24 hours have passed", rejected.reason());
    }

    @Test
    void givenServerErrorWithoutBody_shouldReturnRejection() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        final var result = target.send(new PhoneNumber("15551234567"), new MessageContent.Text("oi"));

        Assertions.assertEquals("http_503", ((SendResult.Rejected) result).code());
    }
}
