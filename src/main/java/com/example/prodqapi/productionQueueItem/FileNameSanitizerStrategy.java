package com.example.prodqapi.productionQueueItem;

import java.util.Map;

/**
 * Interfejs definiujący strategię sanitization (czyszczenia) nazw plików.
 * Każda implementacja będzie zawierać logikę dla konkretnego klienta lub maszyny.
 */
public interface FileNameSanitizerStrategy {
    /**
     * Czyści i modyfikuje oryginalną nazwę pliku zgodnie z daną strategią.
     * @param originalFileName Oryginalna nazwa pliku.
     * @param options Mapa z opcjami, np. {"maxLength": 24}
     * @return Oczyszczona i zmodyfikowana nazwa pliku.
     */
    String sanitize(String originalFileName, Map<String, Object> options);
}