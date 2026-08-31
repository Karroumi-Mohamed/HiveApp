package com.hiveapp.platform.client.plan.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/** Generates immutable internal identifiers for operator-created commercial records. */
public final class CommercialCodeGenerator {

    private static final int MAX_CODE_LENGTH = 100;
    private static final int SUFFIX_LENGTH = 8;
    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^A-Z0-9]+");
    private static final Pattern EDGE_UNDERSCORES = Pattern.compile("^_+|_+$");

    private CommercialCodeGenerator() {}

    public static String generate(String name, String fallbackPrefix, Predicate<String> alreadyExists) {
        String base = slug(name);
        if (base.isBlank()) {
            base = slug(fallbackPrefix);
        }
        if (base.isBlank()) {
            base = "ITEM";
        }
        if (!Character.isLetter(base.charAt(0))) {
            base = "ITEM_" + base;
        }
        int maximumBaseLength = MAX_CODE_LENGTH - SUFFIX_LENGTH - 1;
        if (base.length() > maximumBaseLength) {
            base = EDGE_UNDERSCORES.matcher(base.substring(0, maximumBaseLength)).replaceAll("");
        }

        String candidate;
        do {
            String suffix = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, SUFFIX_LENGTH).toUpperCase(Locale.ROOT);
            candidate = base + "_" + suffix;
        } while (alreadyExists.test(candidate));
        return candidate;
    }

    private static String slug(String value) {
        if (value == null) {
            return "";
        }
        String ascii = MARKS.matcher(Normalizer.normalize(value, Normalizer.Form.NFD)).replaceAll("");
        String normalized = NON_ALPHANUMERIC.matcher(ascii.toUpperCase(Locale.ROOT)).replaceAll("_");
        return EDGE_UNDERSCORES.matcher(normalized).replaceAll("");
    }
}
