package com.example.infraboxapi.productionQueueItem;

import org.springframework.stereotype.Component;

/**
 * Fabryka (lub rejestr) decydująca, której strategii sanitization użyć.
 * Adnotacja @Component sprawia, że Spring automatycznie zarządza tym obiektem.
 */
@Component
public class SanitizerFactory {

    public FileNameSanitizerStrategy getStrategy(String clientOrMachine) {
        // W przyszłości 'clientOrMachine' może pochodzić z ustawień, bazy danych itp.
        // Na razie na sztywno wybieramy strategię dla Inframet.
        if ("inframet".equalsIgnoreCase(clientOrMachine)) {
            return new InframetMpfSanitizer();
        }

        // W przyszłości można dodać domyślną, prostszą strategię
        // return new DefaultSanitizer();
        return new InframetMpfSanitizer(); // Na razie używamy tej samej dla wszystkich
    }
}