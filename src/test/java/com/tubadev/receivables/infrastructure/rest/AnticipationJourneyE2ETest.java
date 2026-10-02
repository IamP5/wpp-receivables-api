package com.tubadev.receivables.infrastructure.rest;

import com.tubadev.receivables.IntegrationTest;
import com.tubadev.receivables.infrastructure.configuration.security.WebhookSignatureVerifier;
import com.tubadev.receivables.infrastructure.mediator.EventMediator;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Drives the whole anticipation journey through the signed webhook, with the mock messaging,
 * customer, eligibility and anticipation adapters.
 */
@IntegrationTest
class AnticipationJourneyE2ETest {

    private static final String API_KEY = "test-api-key";
    // the customer is registered as 5511988887777; WhatsApp reports it without the 9th digit
    private static final String WA_ID = "551188887777";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WebhookSignatureVerifier signatureVerifier;

    @Autowired
    private JdbcClient jdbcClient;

    @Autowired
    private EventMediator eventMediator;

    @Test
    void givenWebhookVerification_whenTokenMatches_shouldEchoChallenge() throws Exception {
        mvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "test-verify-token")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isOk())
                .andExpect(content().string("1158201444"));

        mvc.perform(get("/webhooks/whatsapp")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "wrong")
                        .param("hub.challenge", "1158201444"))
                .andExpect(status().isForbidden());
    }

    @Test
    void givenInvalidSignature_shouldRejectWebhook() throws Exception {
        mvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .content(textMessage("oi")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void givenInternalApiWithoutKey_shouldReturnUnauthorized() throws Exception {
        mvc.perform(get("/campaigns/any")).andExpect(status().isUnauthorized());
    }

    @Test
    void givenCustomer_whenWalksTheJourney_shouldRequestAnticipation() throws Exception {
        webhook(textMessage("Oi"));
        Assertions.assertEquals("main_menu", currentStage());

        webhook(buttonReply("ANTICIPATE", "Antecipar boletos"));
        Assertions.assertEquals("awaiting_amount", currentStage());

        webhook(textMessage("quero 7.700"));
        Assertions.assertEquals("reviewing_offer", currentStage());

        webhook(buttonReply("CONFIRM_OFFER", "Confirmar"));
        Assertions.assertEquals("awaiting_selfie", currentStage());

        webhook(image("mock-selfie-1"));
        Assertions.assertEquals("completed", currentStage());

        final var conversationId = latestConversationId();
        final var outboundWamid = jdbcClient.sql("""
                        SELECT wamid FROM messages WHERE conversation_id = :id AND direction = 'OUTBOUND' ORDER BY created_at LIMIT 1
                        """)
                .param("id", conversationId).query(String.class).single();

        webhook(statusUpdate(outboundWamid, "delivered"));

        mvc.perform(get("/conversations/{id}", conversationId).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("completed"))
                .andExpect(jsonPath("$.stage_data.protocol").value(startsWith("ANT-")))
                .andExpect(jsonPath("$.customer_id").value("cus_test"))
                .andExpect(jsonPath("$.open").value(false))
                // 5 inbound + menu + amount question + offer details + confirmation buttons + selfie request + protocol + contract
                .andExpect(jsonPath("$.messages", hasSize(12)))
                .andExpect(jsonPath("$.messages[1].type").value("buttons"))
                .andExpect(jsonPath("$.messages[1].status").value("delivered"))
                .andExpect(jsonPath("$.messages[5].content.body").value(containsString("Mercado A")))
                .andExpect(jsonPath("$.messages[5].content.body").value(containsString("Mercado B")))
                .andExpect(jsonPath("$.messages[5].content.body").value(containsString("41,30 acima do pedido")))
                .andExpect(jsonPath("$.messages[9].type").value("media"))
                .andExpect(jsonPath("$.messages[11].type").value("media"))
                .andExpect(jsonPath("$.messages[11].content.media_type").value("document"))
                .andExpect(jsonPath("$.messages[11].content.filename").value(startsWith("contrato-ANT-")));

        final var contractId = jdbcClient.sql("SELECT id FROM contracts WHERE conversation_id = :id")
                .param("id", conversationId).query(String.class).single();

        mvc.perform(get("/contracts/{id}", contractId).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer_id").value("cus_test"))
                .andExpect(jsonPath("$.protocol").value(startsWith("ANT-")))
                .andExpect(jsonPath("$.receivables", hasSize(2)))
                .andExpect(jsonPath("$.signature.channel").value("whatsapp"))
                .andExpect(jsonPath("$.signature.biometric_verification_id").value(startsWith("bio_")))
                .andExpect(jsonPath("$.authentication_code").value(matchesPattern("[0-9A-F]{64}")))
                .andExpect(jsonPath("$.document.url").value("/contracts/%s/document".formatted(contractId)));

        final var pdf = mvc.perform(get("/contracts/{id}/document", contractId).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        Assertions.assertEquals("%PDF", new String(pdf, 0, 4, StandardCharsets.US_ASCII));
    }

    @Test
    void givenUnknownContract_shouldReturnNotFound() throws Exception {
        mvc.perform(get("/contracts/{id}", "nope").header("X-Api-Key", API_KEY)).andExpect(status().isNotFound());
    }

    @Test
    void givenUsCustomer_whenSaysHi_shouldReceiveTheMenu() throws Exception {
        webhook(textMessage("15555550188", "oi"));

        final var conversationId = jdbcClient.sql("SELECT id FROM conversations WHERE customer_id = 'cus_us'")
                .query(String.class).single();

        mvc.perform(get("/conversations/{id}", conversationId).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone_number").value("15555550188"))
                .andExpect(jsonPath("$.stage").value("main_menu"))
                .andExpect(jsonPath("$.messages", hasSize(2)))
                .andExpect(jsonPath("$.messages[1].status").value("accepted"));
    }

    @Test
    void givenRedeliveredWebhook_shouldProcessOnlyOnce() throws Exception {
        final var payload = textMessage("5511977771111", "menu");
        webhook(payload);
        final var before = jdbcClient.sql("SELECT COUNT(*) FROM messages").query(Long.class).single();

        webhook(payload);

        Assertions.assertEquals(before, jdbcClient.sql("SELECT COUNT(*) FROM messages").query(Long.class).single());
    }

    @Test
    void givenCampaign_whenStarted_shouldDispatchTemplateThroughTheOutbox() throws Exception {
        final var created = mvc.perform(post("/campaigns")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Antecipação outubro",
                                  "template_name": "antecipacao_disponivel",
                                  "template_language": "pt_BR",
                                  "template_parameters": ["{{customer.first_name}}", "{{receivables.available_amount}}"],
                                  "customer_ids": ["cus_campaign", "cus_unknown"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/campaigns/")))
                .andReturn().getResponse().getContentAsString();

        final var campaignId = created.replaceAll(".*\"campaign_id\":\"([a-f0-9]+)\".*", "$1");

        mvc.perform(post("/campaigns/{id}/start", campaignId).header("X-Api-Key", API_KEY))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("running"));

        eventMediator.relayPending(100, 5);

        mvc.perform(get("/campaigns/{id}", campaignId).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.recipients").value(2))
                .andExpect(jsonPath("$.skipped").value(1))
                .andExpect(jsonPath("$.sent").value(1))
                .andExpect(jsonPath("$.failed").value(0));
    }

    @Test
    void givenInvalidCampaign_shouldReturnValidationErrors() throws Exception {
        mvc.perform(post("/campaigns")
                        .header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"x\", \"template_name\": \"\", \"template_language\": \"pt_BR\", \"customer_ids\": []}"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.errors", hasSize(2)));
    }

    private void webhook(final String payload) throws Exception {
        final var body = payload.getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhooks/whatsapp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature-256", "sha256=" + signatureVerifier.sign(body))
                        .content(body))
                .andExpect(status().isOk());
    }

    private String latestConversationId() {
        return jdbcClient.sql("SELECT id FROM conversations WHERE customer_id = 'cus_test' ORDER BY updated_at DESC LIMIT 1")
                .query(String.class).single();
    }

    private String currentStage() {
        return jdbcClient.sql("SELECT stage FROM conversations WHERE id = :id").param("id", latestConversationId())
                .query(String.class).single();
    }

    private static String textMessage(final String text) {
        return textMessage(WA_ID, text);
    }

    private static String textMessage(final String from, final String text) {
        return envelope("""
                "contacts": [{ "profile": { "name": "Maria" }, "wa_id": "%s" }],
                "messages": [{ "from": "%s", "id": "%s", "timestamp": "%d", "type": "text", "text": { "body": "%s" } }]
                """.formatted(from, from, wamid(), Instant.now().getEpochSecond(), text));
    }

    private static String buttonReply(final String id, final String title) {
        return envelope("""
                "messages": [{
                  "from": "%s", "id": "%s", "timestamp": "%d", "type": "interactive",
                  "interactive": { "type": "button_reply", "button_reply": { "id": "%s", "title": "%s" } }
                }]
                """.formatted(WA_ID, wamid(), Instant.now().getEpochSecond(), id, title));
    }

    private static String image(final String mediaId) {
        return envelope("""
                "messages": [{
                  "from": "%s", "id": "%s", "timestamp": "%d", "type": "image",
                  "image": { "id": "%s", "mime_type": "image/jpeg", "sha256": "abc" }
                }]
                """.formatted(WA_ID, wamid(), Instant.now().getEpochSecond(), mediaId));
    }

    private static String statusUpdate(final String wamid, final String status) {
        return envelope("""
                "statuses": [{ "id": "%s", "status": "%s", "timestamp": "%d", "recipient_id": "%s" }]
                """.formatted(wamid, status, Instant.now().getEpochSecond(), WA_ID));
    }

    private static String envelope(final String value) {
        return """
                {
                  "object": "whatsapp_business_account",
                  "entry": [{
                    "id": "1094752459960221",
                    "changes": [{
                      "field": "messages",
                      "value": {
                        "messaging_product": "whatsapp",
                        "metadata": { "display_phone_number": "15551471409", "phone_number_id": "1357259444133753" },
                        %s
                      }
                    }]
                  }]
                }
                """.formatted(value);
    }

    private static String wamid() {
        return "wamid.TEST_" + UUID.randomUUID();
    }
}
