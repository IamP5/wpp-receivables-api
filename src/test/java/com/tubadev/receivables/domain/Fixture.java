package com.tubadev.receivables.domain;

import com.tubadev.receivables.domain.campaign.Campaign;
import com.tubadev.receivables.domain.campaign.CampaignId;
import com.tubadev.receivables.domain.campaign.CampaignTemplate;
import com.tubadev.receivables.domain.contract.Contract;
import com.tubadev.receivables.domain.contract.ContractId;
import com.tubadev.receivables.domain.contract.ElectronicSignature;
import com.tubadev.receivables.domain.contract.Party;
import com.tubadev.receivables.domain.conversation.Conversation;
import com.tubadev.receivables.domain.conversation.ConversationId;
import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.person.Document;
import com.tubadev.receivables.domain.person.PhoneNumber;
import com.tubadev.receivables.domain.receivable.AnticipationOffer;
import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.receivable.Receivable;
import com.tubadev.receivables.domain.receivable.ReceivableId;
import com.tubadev.receivables.domain.utils.IdUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

public final class Fixture {

    private Fixture() {
    }

    public static final class Customers {

        public static Customer maria() {
            return new Customer(new CustomerId("cus_maria"), "Maria Silva", Document.create("11222333000181", "cnpj"),
                    new PhoneNumber("5511988887777"), true);
        }

        public static Customer notOptedIn() {
            return new Customer(new CustomerId("cus_no_optin"), "João Souza", null, new PhoneNumber("5511977776666"), false);
        }
    }

    public static final class Conversations {

        public static Conversation of(final Customer customer) {
            return Conversation.newConversation(new ConversationId(IdUtils.uniqueId()), customer.phoneNumber(), customer.id(), null);
        }

        public static Conversation withInbound(final Customer customer, final Instant receivedAt) {
            final var c = of(customer);
            c.execute(new com.tubadev.receivables.domain.conversation.ConversationCommand.RegisterInbound(receivedAt));
            return c;
        }
    }

    public static final class Campaigns {

        public static Campaign anticipationOffer(final CustomerId... recipients) {
            return Campaign.newCampaign(
                    new CampaignId(IdUtils.uniqueId()),
                    "Antecipação outubro",
                    CampaignTemplate.of("antecipacao_disponivel", "pt_BR", List.of("{{customer.first_name}}", "{{receivables.available_amount}}")),
                    List.of(recipients)
            );
        }
    }

    public static final class Offers {

        public static AnticipationOffer tenThousand() {
            return new AnticipationOffer(
                    "off_123",
                    Money.brl("10000"),
                    List.of(
                            new Receivable(new ReceivableId("bol-1"), "Mercado A", "123", LocalDate.now().plusDays(30), Money.brl("6000")),
                            new Receivable(new ReceivableId("bol-2"), "Mercado B", "456", LocalDate.now().plusDays(45), Money.brl("4500"))
                    ),
                    Money.brl("10500"),
                    Money.brl("253.73"),
                    Money.brl("10246.27"),
                    new BigDecimal("0.0199"),
                    Instant.now().plus(30, ChronoUnit.MINUTES)
            );
        }
    }

    public static final class Contracts {

        public static Contract issued(final Customer customer, final ConversationId conversationId) {
            return Contract.issue(
                    new ContractId(IdUtils.uniqueId()),
                    "ANT-20261001-" + IdUtils.uniqueId().substring(0, 6).toUpperCase(),
                    customer.id(),
                    conversationId,
                    Party.of(customer),
                    Offers.tenThousand(),
                    LocalDate.of(2026, 10, 2),
                    new ElectronicSignature(ElectronicSignature.WHATSAPP, customer.phoneNumber().value(), "bio_123",
                            new BigDecimal("0.97"), Instant.parse("2026-10-01T15:00:00Z"))
            );
        }
    }
}
