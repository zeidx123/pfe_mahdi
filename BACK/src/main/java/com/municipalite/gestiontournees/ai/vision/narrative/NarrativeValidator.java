package com.municipalite.gestiontournees.ai.vision.narrative;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic checks of a generated summary (rules V1 to V7). Mirrored by scripts/evaluation/narrative_validator.py
 * and checked against the shared vectors src/test/resources/vision/narrative_validator_cases.json.
 * It cannot detect every misleading wording: it only bounds length, numbers, vocabulary and form.
 */
@Component
public class NarrativeValidator {
    public static final int MAX_LENGTH = 600;
    static final String FORBIDDEN_RESOURCE = "prompts/vision-narrative-forbidden.txt";
    private static final Pattern NUMBER = Pattern.compile("(?<![0-9])[0-9]+(?:[.,][0-9]+)?");
    private static final Pattern URL = Pattern.compile("https?://|www\\.");
    private static final Pattern SPACES = Pattern.compile("[ \\t\\r\\n\\f\\u000B]+");
    private static final Pattern MARKS = Pattern.compile("\\p{M}");
    private static final String MARKUP = "*#{}`";
    private static final List<String> HUMAN = List.of("controle humain", "verification humaine", "verifier", "confirmer");
    private static final Map<String, String> QUALITY_WORDS = Map.of("TROP_SOMBRE", "sombre", "FLOUE", "flou", "SUREXPOSEE", "surexpos");
    private final List<Pattern> forbidden;

    public NarrativeValidator() {
        this(loadForbidden());
    }

    NarrativeValidator(List<String> entries) {
        List<Pattern> patterns = new ArrayList<>();
        for (String entry : entries) {
            patterns.add(Pattern.compile("(?<![a-z0-9])" + Pattern.quote(normalize(entry)) + "(?:e|s|es|x)?(?![a-z0-9])"));
        }
        this.forbidden = List.copyOf(patterns);
    }

    /** Sorted codes of the violated rules; an empty list means the text is accepted. */
    public List<String> validate(String text, NarrativeFacts facts) {
        String trimmed = trim(text);
        if (trimmed.isEmpty()) return List.of("V1");
        Set<String> codes = new TreeSet<>();
        if (trimmed.codePointCount(0, trimmed.length()) > MAX_LENGTH) codes.add("V1");
        String normalized = normalize(trimmed);
        List<BigDecimal> allowed = List.of(BigDecimal.valueOf(facts.propre()), BigDecimal.valueOf(facts.debordement()),
                BigDecimal.valueOf(facts.indetermine()), BigDecimal.valueOf(facts.sharpness()), BigDecimal.valueOf(facts.brightness()));
        Matcher number = NUMBER.matcher(normalized);
        while (number.find()) {
            BigDecimal value = new BigDecimal(number.group().replace(',', '.'));
            if (allowed.stream().noneMatch(a -> a.compareTo(value) == 0)) {
                codes.add("V2");
                break;
            }
        }
        if (forbidden.stream().anyMatch(p -> p.matcher(normalized).find())) codes.add("V3");
        if (!normalized.contains("automatique") || HUMAN.stream().noneMatch(normalized::contains)) codes.add("V4");
        boolean percentMissing = !Pattern.compile("(?<![0-9])(?<![0-9][.,])" + facts.predictedPercent()
                + "(?![.,]?[0-9]) ?(?:%|pour cent)").matcher(normalized).find();
        if (facts.qualityIssue() != null) {
            String word = QUALITY_WORDS.get(facts.qualityIssue());
            if (word == null || !normalized.contains(word) || !normalized.contains("photo")) percentMissing = true;
        }
        if (percentMissing) codes.add("V5");
        if (trimmed.chars().anyMatch(c -> MARKUP.indexOf(c) >= 0)) codes.add("V6");
        if (URL.matcher(normalized).find()) codes.add("V7");
        return List.copyOf(codes);
    }

    /** Trims only spaces, tabs and line breaks (same rule as the Python validator). */
    public static String trim(String text) {
        if (text == null) return "";
        int start = 0, end = text.length();
        while (start < end && " \t\r\n".indexOf(text.charAt(start)) >= 0) start++;
        while (end > start && " \t\r\n".indexOf(text.charAt(end - 1)) >= 0) end--;
        return text.substring(start, end);
    }

    static String normalize(String text) {
        String t = text.replace(' ', ' ').replace(' ', ' ').replace(' ', ' ');
        t = MARKS.matcher(Normalizer.normalize(t, Normalizer.Form.NFD)).replaceAll("");
        t = t.toLowerCase(Locale.ROOT).replace('’', '\'').replace('‘', '\'').replace('ʼ', '\'');
        return SPACES.matcher(t).replaceAll(" ");
    }

    private static List<String> loadForbidden() {
        try {
            String content = new String(new ClassPathResource(FORBIDDEN_RESOURCE).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            List<String> entries = new ArrayList<>();
            for (String line : content.split("\\R")) {
                String entry = line.strip();
                if (!entry.isEmpty() && !entry.startsWith("#")) entries.add(entry);
            }
            return entries;
        } catch (IOException e) {
            throw new UncheckedIOException("Liste de mots interdits introuvable : " + FORBIDDEN_RESOURCE, e);
        }
    }
}
