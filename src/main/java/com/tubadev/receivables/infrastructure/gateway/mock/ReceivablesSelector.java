package com.tubadev.receivables.infrastructure.gateway.mock;

import com.tubadev.receivables.domain.receivable.Money;
import com.tubadev.receivables.domain.receivable.Receivable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Chooses which of the customer's existing boletos cover a requested amount.
 * <p>
 * Boletos are indivisible, so the target is the combination whose net amount reaches the request with the smallest
 * excess; ties go to fewer boletos, then to earlier due dates. The boletos are never shaped to the request: when no
 * combination is exact, the customer sees the closest one above it.
 */
final class ReceivablesSelector {

    /** Upper bound of combinations visited, so a large portfolio can't stall a webhook; the best found so far wins. */
    static final int MAX_VISITS = 200_000;

    record Candidate(Receivable receivable, Money fee, Money net) {

        long netCents() {
            return net.amount().movePointRight(2).longValueExact();
        }
    }

    private ReceivablesSelector() {
    }

    /** Empty when even all the boletos together don't reach the requested amount. */
    static Optional<List<Candidate>> select(final List<Candidate> eligible, final Money requested) {
        final var target = requested.amount().movePointRight(2).longValueExact();
        final var sorted = eligible.stream()
                .sorted(Comparator.comparingLong(Candidate::netCents).reversed()
                        .thenComparing(c -> c.receivable().dueDate()))
                .toList();

        final var search = new Search(sorted, target);
        if (search.remaining[0] < target) {
            return Optional.empty();
        }
        search.visit(0, 0, new ArrayList<>());
        return Optional.of(search.best.stream().sorted(Comparator.comparing(c -> c.receivable().dueDate())).toList());
    }

    private static final class Search {

        private final List<Candidate> candidates;
        private final long target;
        /** remaining[i] = sum of net amounts from index i to the end. */
        private final long[] remaining;

        private List<Candidate> best = List.of();
        private long bestExcess = Long.MAX_VALUE;
        private long bestDueDays = Long.MAX_VALUE;
        private int visits;

        Search(final List<Candidate> candidates, final long target) {
            this.candidates = candidates;
            this.target = target;
            this.remaining = new long[candidates.size() + 1];
            for (int i = candidates.size() - 1; i >= 0; i--) {
                remaining[i] = remaining[i + 1] + candidates.get(i).netCents();
            }
        }

        void visit(final int index, final long sum, final List<Candidate> chosen) {
            if (++visits > MAX_VISITS) {
                return;
            }
            if (sum >= target) {
                // adding more boletos would only grow the excess
                consider(sum - target, chosen);
                return;
            }
            if (index == candidates.size() || sum + remaining[index] < target) {
                return;
            }
            if (bestExcess == 0 && chosen.size() + 1 > best.size()) {
                return;
            }

            final var next = candidates.get(index);
            if (sum + next.netCents() - target <= bestExcess) {
                chosen.add(next);
                visit(index + 1, sum + next.netCents(), chosen);
                chosen.removeLast();
            }
            visit(index + 1, sum, chosen);
        }

        private void consider(final long excess, final List<Candidate> chosen) {
            final long dueDays = chosen.stream().mapToLong(c -> c.receivable().dueDate().toEpochDay()).sum();
            final boolean better = excess < bestExcess
                    || excess == bestExcess && chosen.size() < best.size()
                    || excess == bestExcess && chosen.size() == best.size() && dueDays < bestDueDays;
            if (better) {
                best = List.copyOf(chosen);
                bestExcess = excess;
                bestDueDays = dueDays;
            }
        }
    }
}
