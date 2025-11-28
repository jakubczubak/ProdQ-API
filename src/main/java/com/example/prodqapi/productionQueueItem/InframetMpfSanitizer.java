package com.example.prodqapi.productionQueueItem;

import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FINALNA WERSJA
 * Strategia sanitization specyficzna dla klienta "Inframet" i plików .MPF.
 * Zawiera logikę skracania nazwy do ~24 znaków i obsługę specjalnych końcówek.
 * Skracanie zachowuje początek nazwy pliku.
 */
public class InframetMpfSanitizer implements FileNameSanitizerStrategy {

    private static final List<Pattern> SUFFIX_PATTERNS = List.of(
            Pattern.compile("(?<base>.*)(?<suffix>_[Mm][Aa][Cc]\\d+_[A-Za-z]_[Vv]\\d+)$"),
            Pattern.compile("(?<base>.*)(?<suffix>_[Mm][Aa][Cc]\\d+_[A-Za-z]$)"),
            Pattern.compile("(?<base>.*)(?<suffix>_[Mm][Aa][Cc]\\d+)$")
    );

    @Override
    public String sanitize(String originalFileName, Map<String, Object> options) {
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            return "default_name";
        }

        int maxLength = (int) options.getOrDefault("maxLength", 24);

        String sanitized = normalizeAndClean(originalFileName.trim());

        String ext = getFileExtension(sanitized);
        String nameWithoutExt = sanitized.substring(0, sanitized.length() - ext.length());

        String[] parts = extractSuffixAndBase(nameWithoutExt);
        String baseName = parts[0];
        String suffix = parts[1];

        baseName = truncateBaseName(baseName, suffix, ext, maxLength);

        return baseName + suffix + ext;
    }

    private String[] extractSuffixAndBase(String nameWithoutExt) {
        for (Pattern pattern : SUFFIX_PATTERNS) {
            Matcher matcher = pattern.matcher(nameWithoutExt);
            if (matcher.matches()) {
                return new String[]{matcher.group("base"), matcher.group("suffix")};
            }
        }
        return new String[]{nameWithoutExt, ""};
    }

    /**
     * ZAKTUALIZOWANA LOGIKA SKRACANIA
     * Skraca bazową część nazwy pliku, zachowując tylko jej początek,
     * aby całość zmieściła się w podanym limicie.
     */
    private String truncateBaseName(String baseName, String suffix, String ext, int maxLength) {
        int maxBaseLength = maxLength - suffix.length() - ext.length();
        if (maxBaseLength < 0) {
            maxBaseLength = 0;
        }

        if (baseName.length() > maxBaseLength) {
            return baseName.substring(0, maxBaseLength);
        }

        return baseName;
    }

    private String normalizeAndClean(String text) {
        String normalized = Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("[\\p{InCombiningDiacriticalMarks}]", "")
                .replaceAll("[ąĄ]", "a").replaceAll("[ćĆ]", "c")
                .replaceAll("[ęĘ]", "e").replaceAll("[łŁ]", "l")
                .replaceAll("[ńŃ]", "n").replaceAll("[óÓ]", "o")
                .replaceAll("[śŚ]", "s").replaceAll("[źŹ]", "z")
                .replaceAll("[żŻ]", "z");
        return normalized.replaceAll("[^a-zA-Z0-9_\\-\\.\\s]", "_");
    }

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return (lastDot == -1) ? "" : fileName.substring(lastDot);
    }
}