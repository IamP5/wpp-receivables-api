# wpp-receivables-api

WhatsApp (Cloud API) integration and conversational journey for **antecipação de recebíveis (boletos)**.

The service owns only two things:

1. **The WhatsApp integration**: webhooks (inbound messages and delivery statuses), outbound messages, campaigns (approved templates) and the 24h customer service window rule.
2. **The anticipation journey**: the conversation state machine. The customer says how much they want to receive, the Receivables service picks the boletos that cover that amount, the customer confirms, and the anticipation is requested.

Customer data, eligibility and the anticipation itself belong to other services. They are reached through **ports** (domain interfaces) that currently have **mock adapters**.

Stack: Java 25, Spring Boot 4.1 (Spring 7, Jackson 3), Maven, PostgreSQL + Flyway, JdbcClient (no ORM), springdoc.

---

## Architecture

It follows [FC3-subscription-service-java](https://github.com/devfullcycle/FC3-subscription-service-java) (Clean Architecture + DDD). That project uses three Gradle modules; here they are three packages of a single Maven module:

```
com.tubadev.receivables
├── domain          pure Java: aggregates, value objects, sealed commands/events, ports (gateways)
├── application     use cases (UseCase<IN,OUT>, UnitUseCase, Presenter) + the journey engine
└── infrastructure  Spring: REST/webhook, JDBC repositories, WhatsApp client, mock adapters, outbox
```

| Pattern from the reference | Here |
|---|---|
| `AggregateRoot` / `Entity` / `Identifier` / `ValueObject` / `AssertionConcern` | same base classes |
| `execute(Command...)` with a **sealed command** interface + exhaustive `switch` | `Conversation`, `Message`, `Campaign` |
| Sealed **domain events** registered by the aggregate, persisted with it | `ConversationStarted`, `JourneyAdvanced`, `ConversationEnded`, `MessageReceived`, `MessageStatusChanged`, `MessageFailed`, `Campaign*` |
| **State pattern** for statuses | `JourneyStage`, `MessageStatus`, `CampaignStatus`, as immutable records whose transitions return the next state |
| Gateways as ports, adapters in infrastructure | `MessagingGateway` → `WhatsAppCloudApiClient`; `CustomerGateway` / `EligibilityGateway` / `AnticipationGateway` → mocks |
| Abstract use case + `Default*` impl + `Input`/`Output` interfaces + `Presenter` | every use case; controllers pass local `record Input(...)` and `Response::new` as presenter |
| `Notification` validation handler | `DefaultCreateCampaign` |
| `DatabaseClient` / `RowMap` over `JdbcClient`, optimistic locking with `version` | all repositories |
| Events table + `EventMediator` + `Publisher`/`Subscriber` (observer) | transactional outbox relayed by a scheduled job (Debezium/Kafka in the reference) |
| `@Configuration` use case wiring | `configuration/usecases/*UseCaseConfig` |

Java 21–25 features in use:
- Sealed hierarchies: commands, events, `MessageContent`, `Intent`, `JourneyStage`, `SendResult`, `OfferResult`.
- Record patterns and nested deconstruction: the journey `switch (new Turn(stage, intent))`.
- Unnamed variables `_`, `case null`, guarded patterns.
- `SequencedSet` / `getFirst()` / `getLast()`.
- Virtual threads and a `Semaphore` for campaign dispatch, with `spring.threads.virtual.enabled`.
- Text blocks for SQL and message copy, `HexFormat`, `Locale.of`.

### Ports and mock adapters

| Port (domain) | Responsibility | Adapter today |
|---|---|---|
| `CustomerGateway` | customer by phone/id, WhatsApp opt-out | `CustomerMockClient` |
| `EligibilityGateway` | available amount and eligible boletos count | `EligibilityMockClient` |
| `AnticipationGateway` | **selects the boletos that cover the requested amount**, prices the offer, requests the anticipation | `AnticipationMockClient` |
| `MessagingGateway` | sends messages | `WhatsAppCloudApiClient` (`whatsapp.adapter=cloud-api`) or `MessagingMockClient` (`mock`) |
| `MediaGateway` | downloads inbound media (the selfie), uploads documents (the contract) | `WhatsAppMediaClient` (`cloud-api`) or `MediaMockClient` (`mock`, keeps uploads in `$TMPDIR/wpp-mock-media`) |
| `BiometricsGateway` | validates the selfie against the customer's biometrics | `BiometricsMockClient` (`biometrics.adapter=mock`) |
| `ContractDocumentGateway` | renders the contract PDF | `ContractPdfRenderer` (OpenPDF) |

The mocks share an in-memory store seeded from `mock.customers` in `application.yml`:
- Each customer has a fixed boleto portfolio: `cus_sandbox_01` ships 12 sample boletos (R$ 392,15 to R$ 9.870,00, due in 9 to 90 days); customers without `receivables` get a deterministic generated set. Offers never invent boletos to match a request.
- Boletos are indivisible, so the offer is the combination whose **net** amount reaches the request with the smallest excess (ties: fewer boletos, then earlier due dates). The bot tells the customer when it can't be exact, e.g. R$ 4.789,57 asked → R$ 4.814,34 offered (R$ 24,77 above). The search is bounded, so large portfolios stay fast.
- The fee is `amount × 1.99% × days/30`.
- The minimum is R$ 500.
- Offers expire after 30 min.
- Dates (protocol, credit date, contract) follow Brasília time.

The biometrics mock rejects anything that isn't JPEG/PNG (`unsupported_media`) and photos under 10 KB (`low_quality`), and approves the rest with score 0.97. Set `MOCK_BIOMETRICS_FAIL_FIRST=1` to reject each customer's first selfie as `face_mismatch` and exercise the retry path.

To plug in the real services, implement the port in `infrastructure/gateway/...` and change `receivables.adapter`.

---

## The journey

```
 "oi" / campaign template
          │
       STARTED ──▶ MAIN_MENU ──[Antecipar boletos]──▶ AWAITING_AMOUNT ──"10 mil"──▶ REVIEWING_OFFER ──[Confirmar]──▶ AWAITING_SELFIE
                      │                                  ▲     │                     │   │                          │  📸 selfie
                      │                                  └─────┴──[Alterar valor]────┘   └─[Cancelar]──▶ CLOSED     ▼
                      │                                                                        biometrics ✔ ──▶ anticipation requested
                      │                                                                                        ──▶ contract PDF ──▶ COMPLETED
                      │                                                                        biometrics ✘ ──▶ retry (3 attempts) ──▶ HUMAN_HANDOFF
                      └─[Falar com atendente]──▶ HUMAN_HANDOFF (bot silent; agent replies via API)
   any stage: "parar" ──▶ opt-out + CLOSED      not eligible ──▶ CLOSED      unknown phone ──▶ HUMAN_HANDOFF
```

- **Intents** (`domain/conversation/journey/Intent`) come from typed text, interactive buttons or template quick replies. For example: "oi", "quero antecipar", "R$ 7.500,00", "10 mil", "1,5 mil", "sim", "cancelar", "atendente", "parar".
- **Amounts** typed at any stage go straight to the offer, so "quero antecipar 10 mil" skips the question.
- **Selfie / biometrics**: confirming the offer asks for a selfie. The image is downloaded through the Cloud API media endpoint and validated by `BiometricsGateway`. A non-image gets a "send a photo" reply, and 3 rejected selfies hand the customer to an agent.
- **Contract**: once the anticipation is requested, `IssueContract` creates a `Contract` aggregate (assignor snapshot, boletos, prices, signature evidence and a SHA-256 authenticity code), renders the PDF, stores both and the bot sends the PDF as a WhatsApp document. While the mocks are on, the PDF carries a "sem validade jurídica" banner and watermark (`contract.disclaimer`). The clauses are a template for the legal team to review.
- The flow lives in `application/journey/impl/DefaultAnticipationJourney`. The copy (pt-BR) is in `JourneyReplies`.
- **24h window**: free-form messages need an inbound in the last 24h (`Conversation.assertCanSend`). Outside it, only templates can be sent. Campaigns therefore always use templates.
- **Idempotency**: inbound messages are deduplicated by `wamid`, because Meta re-delivers webhooks.
- **Brazilian 9th digit**: `PhoneNumber.equivalents()` matches `55 11 9XXXX-XXXX` with WhatsApp's `55 11 XXXX-XXXX`.
- **Allowed recipient countries**: `whatsapp.allowed-country-codes` (`WHATSAPP_ALLOWED_COUNTRY_CODES`, comma separated) limits who the app messages. Other numbers are rejected locally as `recipient_not_allowed` by both the Cloud API and the mock adapters. It defaults to `1` (US) in the `development` profile and to empty (any country) elsewhere.
- **Out-of-order statuses**: `MessageStatus` never regresses (for example, `read` followed by a late `delivered`).

---

## Running

### 1. Locally, without Meta (mock WhatsApp)

```bash
docker compose up -d
```

```bash
WHATSAPP_ADAPTER=mock API_KEY=local-key MOCK_CUSTOMER_PHONE=15555550101 mvn spring-boot:run
```

Drive the journey with simulated webhooks. Bot replies appear in the log as `[whatsapp-mock] → ...`:

```bash
FROM=15555550101 ./scripts/simulate-inbound.sh text "oi"
```

Then send `button ANTICIPATE "Antecipar boletos"`, `text "10 mil"`, `button CONFIRM_OFFER Confirmar` and `image` (the selfie) the same way. `image small` simulates a low-quality photo. The contract PDF is logged as `[whatsapp-mock] media uploaded: ...`.

Swagger UI: http://localhost:8080/api/swagger-ui.html. Internal endpoints need the `X-Api-Key` header.

### 2. With the Meta sandbox (your "Tuba Sandbox" app)

1. Copy `.env.example` to `.env` and fill in `WHATSAPP_PHONE_NUMBER_ID`, `WHATSAPP_ACCESS_TOKEN` and `WHATSAPP_APP_SECRET` (App settings → Basic). Pick a `WHATSAPP_VERIFY_TOKEN`.
2. Expose the app, for example with `cloudflared tunnel --url http://localhost:8080` or `ngrok http 8080`.
3. In Meta → WhatsApp → Configuration → Webhook:
   - Callback URL: `https://<tunnel>/api/webhooks/whatsapp`
   - Verify token: the value of `WHATSAPP_VERIFY_TOKEN`
   - Subscribe to the **messages** field.
4. Set `MOCK_CUSTOMER_PHONE` (and optionally `MOCK_CUSTOMER_2_PHONE`) to your **US** recipient number, for example `1XXXXXXXXXX`. It must be one of the 5 allowed recipients of the test number. Then send "oi" from WhatsApp to the test number.

Sandbox notes:
- The temporary token lasts about 24h. Use a System User token for anything longer.
- Messages to **Brazilian numbers fail with 130497** until the business is verified. This is a Meta restriction on cross-country messages to BR/ID, so the `development` profile only allows US (`1`) recipients. Once the business is verified, set `WHATSAPP_ALLOWED_COUNTRY_CODES=1,55` (or leave it empty).
- Out of the box only the `hello_world` (en_US) template exists. For campaigns, create a template such as `antecipacao_disponivel` (pt_BR):
  - Body: `Olá {{1}}! Você tem {{2}} disponíveis para antecipar.`
  - Add a quick-reply button "Quero antecipar".
  - Create the campaign with `template_parameters: ["{{customer.first_name}}", "{{receivables.available_amount}}"]`.

### API

| Method | Path | |
|---|---|---|
| GET/POST | `/api/webhooks/whatsapp` | Meta verification handshake / inbound messages and statuses (HMAC-SHA256 signature checked) |
| POST | `/api/campaigns` | create a draft campaign (template + customer ids) |
| POST | `/api/campaigns/{id}/start` | start; dispatch runs async via the outbox (`CampaignStarted` → `DispatchCampaignSubscriber`) |
| GET | `/api/campaigns/{id}` | status and stats (sent / failed / skipped) |
| GET | `/api/conversations/{id}` | journey stage + messages with delivery status |
| POST | `/api/conversations/{id}/messages` | human agent reply (24h window); moves the journey to HUMAN_HANDOFF |
| GET | `/api/contracts/{id}` | contract terms, boletos, signature evidence and authenticity code |
| GET | `/api/contracts/{id}/document` | the contract PDF (same file sent on WhatsApp) |

---

## Tests

```bash
mvn test
```

```bash
mvn test -Punit
```

```bash
mvn test -Pintegration
```

- **Unit**: domain (aggregates, transitions, amount parsing, intents) and use cases with Mockito.
- **Integration**: JDBC repositories on H2 (PostgreSQL mode) + Flyway, and the WhatsApp client against `MockRestServiceServer`.
- **E2E**: the whole journey and the campaign flow through the signed webhook (`AnticipationJourneyE2ETest`).

---

## Next steps / known limits

- Replace the mock adapters with HTTP clients for the Customer and Receivables services.
- Webhooks are processed synchronously. For volume, persist the raw payload (inbox) and process it async, keeping per-phone ordering.
- The outbox relay assumes a single instance. For more, use `FOR UPDATE SKIP LOCKED` or CDC (Debezium → Kafka) like the reference.
- Add a partial unique index (one open conversation per phone) on Postgres.
- Add conversation inactivity timeout and retries for transient send failures.
- Biometrics on a WhatsApp photo has no liveness check. Real providers usually require their SDK or a web link for liveness, so the production flow will likely send a link (or a WhatsApp Flow) and receive the result by callback. `BiometricsGateway` stays the same.
- Issuing the contract and uploading the PDF run inside the webhook request. With real providers, move them to the outbox (`ContractIssued`) so a slow provider doesn't make Meta retry the webhook.
- Replace the API key with OAuth2/JWT for the internal API.
- Add a "resume bot" action for agents to return a HUMAN_HANDOFF conversation to the menu.
