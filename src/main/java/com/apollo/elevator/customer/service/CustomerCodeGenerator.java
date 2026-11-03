package com.apollo.elevator.customer.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Customer codes have the form {@code APL-ELV-<initials>-<id>}, e.g. {@code APL-ELV-TEV-001}.
 * <p>
 * The initials are always 3 letters: the first letter of each of the first three significant words
 * ("OF", "AND", ... are skipped); with fewer words the remaining letters come from the start of the
 * earliest words: "TEJAS V" → TEV, "SIDDAGANGA INSTITUTE OF TECHNOLOGY" → SIT, "BHARGAV" → BHA.
 * The id is zero-padded to at least 3 digits.
 */
public final class CustomerCodeGenerator {

    public static final String PREFIX = "APL-ELV-";
    private static final int INITIALS_LENGTH = 3;
    private static final Set<String> STOP_WORDS = Set.of("OF", "AND", "THE", "FOR", "IN", "AT", "ON", "TO", "A", "AN");

    private CustomerCodeGenerator() {
    }

    public static String generate(String customerName, long customerId) {
        return PREFIX + initials(customerName) + "-" + String.format(Locale.ROOT, "%03d", customerId);
    }

    static String initials(String customerName) {
        List<String> words = words(customerName);
        List<String> significant = words.stream().filter(w -> !STOP_WORDS.contains(w)).toList();
        if (!significant.isEmpty()) {
            words = significant;
        }
        words = words.subList(0, Math.min(INITIALS_LENGTH, words.size()));

        int[] take = new int[words.size()];
        int remaining = INITIALS_LENGTH;
        for (int i = 0; i < take.length; i++) {
            take[i] = 1;
            remaining--;
        }
        for (int i = 0; i < take.length && remaining > 0; i++) {
            int extra = Math.min(remaining, words.get(i).length() - take[i]);
            take[i] += extra;
            remaining -= extra;
        }

        StringBuilder initials = new StringBuilder(INITIALS_LENGTH);
        for (int i = 0; i < take.length; i++) {
            initials.append(words.get(i), 0, take[i]);
        }
        while (initials.length() < INITIALS_LENGTH) {
            initials.append('X');
        }
        return initials.toString();
    }

    private static List<String> words(String name) {
        if (name == null) {
            return List.of();
        }
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return Arrays.stream(ascii.toUpperCase(Locale.ROOT).split("[^A-Z0-9]+"))
                .filter(w -> !w.isEmpty())
                .toList();
    }
}
