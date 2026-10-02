package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.customer.Customer;
import com.tubadev.receivables.domain.customer.CustomerId;
import com.tubadev.receivables.domain.person.Document;
import com.tubadev.receivables.domain.person.PhoneNumber;
import com.tubadev.receivables.domain.receivable.AnticipationOffer;
import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.receivable.Receivable;
import com.tubadev.receivables.domain.receivable.ReceivableId;
import com.tubadev.receivables.domain.utils.InstantUtils;
import com.tubadev.receivables.infrastructure.configuration.properties.MockProperties;
import com.tubadev.receivables.infrastructure.configuration.properties.MockProperties.MockCustomer;
import com.tubadev.receivables.infrastructure.configuration.properties.MockProperties.MockReceivable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

/**
 * In-memory stand-in for the Customer and Receivables services, seeded from {@code mock.*} properties.
 * Customers without explicit receivables get a deterministic set of boletos, so offers always come from fixed data.
 */
@Component
@ConditionalOnProperty(name = "receivables.adapter", havingValue = "mock", matchIfMissing = true)
public class ReceivablesMockStore {

    private static final List<String> PAYERS = List.of(
            "Mercado Bom Preço LTDA", "Construtora Horizonte SA", "Farmácia Vida LTDA",
            "Auto Peças Rodovia ME", "Padaria Pão Quente LTDA", "Distribuidora Sol SA"
    );

    private final Map<CustomerId, Customer> customers = new ConcurrentHashMap<>();
    private final Map<CustomerId, List<Receivable>> receivables = new ConcurrentHashMap<>();
    private final Set<ReceivableId> anticipated = ConcurrentHashMap.newKeySet();
    private final Map<String, StoredOffer> offers = new ConcurrentHashMap<>();
    private final MockProperties.Anticipation policy;

    public record StoredOffer(CustomerId customerId, AnticipationOffer offer) {}

    public ReceivablesMockStore(final MockProperties properties, final Clock clock) {
        this.policy = properties.anticipation();
        final var today = LocalDate.ofInstant(clock.instant(), InstantUtils.BUSINESS_ZONE);
        properties.customers().forEach(c -> seed(c, today));
    }

    private void seed(final MockCustomer c, final LocalDate today) {
        final var id = new CustomerId(c.id());
        final var document = c.document() == null ? null : Document.create(c.document(), c.documentType() == null ? "cnpj" : c.documentType());
        customers.put(id, new Customer(id, c.name(), document, PhoneNumber.of(c.phoneNumber()), c.optedIn()));

        final var boletos = c.receivables().isEmpty() ? generated(c.id()) : c.receivables();
        receivables.put(id, IntStream.range(0, boletos.size())
                .mapToObj(i -> toReceivable(c.id(), i, boletos.get(i), today))
                .toList());
    }

    public MockProperties.Anticipation policy() {
        return policy;
    }

    public Optional<Customer> customer(final CustomerId id) {
        return Optional.ofNullable(customers.get(id));
    }

    public Optional<Customer> customerByPhone(final PhoneNumber phoneNumber) {
        final var keys = phoneNumber.equivalents();
        return customers.values().stream().filter(c -> keys.contains(c.phoneNumber().value())).findFirst();
    }

    public void optOut(final CustomerId id) {
        customers.computeIfPresent(id, (_, c) -> new Customer(c.id(), c.name(), c.document(), c.phoneNumber(), false));
    }

    /** Eligible = not anticipated yet and not overdue, sorted by due date (shorter term, lower fee). */
    public List<Receivable> eligibleReceivables(final CustomerId id, final LocalDate today) {
        return receivables.getOrDefault(id, List.of()).stream()
                .filter(r -> !anticipated.contains(r.id()))
                .filter(r -> r.dueDate().isAfter(today))
                .sorted(Comparator.comparing(Receivable::dueDate))
                .toList();
    }

    public void saveOffer(final CustomerId customerId, final AnticipationOffer offer) {
        offers.put(offer.offerId(), new StoredOffer(customerId, offer));
    }

    public Optional<StoredOffer> offer(final String offerId) {
        return Optional.ofNullable(offers.get(offerId));
    }

    /** Marks the boletos as anticipated; false when any of them was already used by another offer. */
    public synchronized boolean anticipate(final Collection<ReceivableId> ids) {
        if (ids.stream().anyMatch(anticipated::contains)) {
            return false;
        }
        anticipated.addAll(ids);
        return true;
    }

    /** Deterministic portfolio for customers seeded without boletos: varied amounts (with cents) and due dates. */
    private static List<MockReceivable> generated(final String customerId) {
        final var seed = Math.abs((long) customerId.hashCode());
        return IntStream.range(0, 10)
                .mapToObj(i -> new MockReceivable(
                        PAYERS.get((int) ((seed + i) % PAYERS.size())),
                        BigDecimal.valueOf(35_000L + ((seed * (i + 7) * 7919L) % 900_000L), 2),
                        9 + i * 9
                ))
                .toList();
    }

    private static Receivable toReceivable(final String customerId, final int index, final MockReceivable r, final LocalDate today) {
        return new Receivable(
                new ReceivableId("%s-bol-%02d".formatted(customerId, index + 1)),
                r.payerName(),
                "34191.79001 01043.510047 91020.150008 %d %014d".formatted(index + 1, r.amount().movePointRight(2).longValue()),
                today.plusDays(r.dueInDays()),
                Money.brl(r.amount())
        );
    }
}
