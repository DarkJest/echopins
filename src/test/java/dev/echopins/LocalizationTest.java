package dev.echopins;

import dev.echopins.domain.error.EchoPinError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Guards the language files against the mistakes that only show up in front of a player.
 *
 * <p>Two real bugs motivated this. A new error code shipped without a Russian translation, which
 * a Russian player would see as a raw key. And {@code error.create_cooldown} contains a {@code %s}
 * that no caller supplied - Minecraft catches the formatting failure and renders the template
 * verbatim, so the player was shown a literal {@code %s} rather than a number.
 *
 * <p>A placeholder mismatch <em>between</em> languages is the same failure in a nastier form,
 * because it only breaks for players in one locale.
 */
class LocalizationTest {

    private static final String EN = "/assets/echopins/lang/en_us.json";
    private static final String RU = "/assets/echopins/lang/ru_ru.json";

    /** Matches %s, %d and the positional %1$s form. */
    private static final Pattern PLACEHOLDER = Pattern.compile("%(?:\\d+\\$)?[a-zA-Z]");

    private static final Pattern ENTRY =
            Pattern.compile("\"([^\"]+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private static Map<String, String> load(String resource) throws IOException {
        try (InputStream in = LocalizationTest.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IOException("Missing language file on the classpath: " + resource);
            }
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            Map<String, String> entries = new LinkedHashMap<>();
            Matcher matcher = ENTRY.matcher(json);
            while (matcher.find()) {
                entries.put(matcher.group(1), matcher.group(2));
            }
            if (entries.isEmpty()) {
                throw new IOException("Parsed no entries from " + resource);
            }
            return entries;
        }
    }

    private static int placeholders(String value) {
        Matcher matcher = PLACEHOLDER.matcher(value);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    @DisplayName("Every error code has a translation in both languages")
    void everyErrorIsTranslated() throws IOException {
        Map<String, String> en = load(EN);
        Map<String, String> ru = load(RU);

        List<String> missing = new ArrayList<>();
        for (EchoPinError error : EchoPinError.values()) {
            String key = error.translationKey();
            if (!en.containsKey(key)) {
                missing.add(key + " (en_us)");
            }
            if (!ru.containsKey(key)) {
                missing.add(key + " (ru_ru)");
            }
        }
        if (!missing.isEmpty()) {
            fail("Error codes without a translation: " + missing);
        }
    }

    @Test
    @DisplayName("The two language files describe exactly the same set of keys")
    void keySetsMatch() throws IOException {
        Map<String, String> en = load(EN);
        Map<String, String> ru = load(RU);

        TreeSet<String> onlyEn = new TreeSet<>(en.keySet());
        onlyEn.removeAll(ru.keySet());
        TreeSet<String> onlyRu = new TreeSet<>(ru.keySet());
        onlyRu.removeAll(en.keySet());

        assertTrue(onlyEn.isEmpty(), "Keys missing from ru_ru: " + onlyEn);
        assertTrue(onlyRu.isEmpty(), "Keys missing from en_us: " + onlyRu);
    }

    @Test
    @DisplayName("A translation never adds or drops a placeholder")
    void placeholderCountsMatchAcrossLanguages() throws IOException {
        Map<String, String> en = load(EN);
        Map<String, String> ru = load(RU);

        List<String> mismatches = new ArrayList<>();
        for (Map.Entry<String, String> entry : en.entrySet()) {
            String translated = ru.get(entry.getKey());
            if (translated == null) {
                continue;
            }
            int expected = placeholders(entry.getValue());
            int actual = placeholders(translated);
            if (expected != actual) {
                mismatches.add(entry.getKey()
                        + " en=" + expected + " ru=" + actual
                        + "  [" + entry.getValue() + "] / [" + translated + "]");
            }
        }
        if (!mismatches.isEmpty()) {
            fail("Placeholder mismatches, which break only one locale:\n  "
                    + String.join("\n  ", mismatches));
        }
    }

    @Test
    @DisplayName("Errors sent without an argument have no placeholder to fill")
    void argumentlessErrorsHaveNoPlaceholder() throws IOException {
        Map<String, String> en = load(EN);

        // These are the only errors the server ever raises with a numeric argument. Any other
        // error message containing a placeholder would render as a literal "%s" to the player,
        // because Minecraft swallows the formatting failure and prints the raw template.
        var withArgument = java.util.Set.of(EchoPinError.CREATE_COOLDOWN);

        List<String> offenders = new ArrayList<>();
        for (EchoPinError error : EchoPinError.values()) {
            if (withArgument.contains(error)) {
                continue;
            }
            String value = en.get(error.translationKey());
            if (value != null && placeholders(value) > 0) {
                offenders.add(error + " -> \"" + value + "\"");
            }
        }
        if (!offenders.isEmpty()) {
            fail("These errors are never sent with an argument, so their placeholder would be "
                    + "shown to the player verbatim:\n  " + String.join("\n  ", offenders));
        }
    }

    @Test
    @DisplayName("Errors that do take an argument declare exactly one placeholder")
    void argumentBearingErrorsDeclareOnePlaceholder() throws IOException {
        Map<String, String> en = load(EN);
        Map<String, String> ru = load(RU);

        assertEquals(1, placeholders(en.get(EchoPinError.CREATE_COOLDOWN.translationKey())),
                "the create cooldown message must have somewhere to put the remaining seconds");
        assertEquals(1, placeholders(ru.get(EchoPinError.CREATE_COOLDOWN.translationKey())),
                "the Russian create cooldown message must have the same placeholder");
    }
}
