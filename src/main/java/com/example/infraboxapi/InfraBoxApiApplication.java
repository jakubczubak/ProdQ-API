package com.example.infraboxapi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import javax.annotation.PostConstruct;
import java.io.File;

@SpringBootApplication
public class InfraBoxApiApplication {

    private static final Logger logger = LoggerFactory.getLogger(InfraBoxApiApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(InfraBoxApiApplication.class, args);
    }

    @PostConstruct
    public void checkMountOnStartup() {
        File mountDir = new File("/mnt/cnc");
        if (mountDir.exists()) {
            if (mountDir.isDirectory()) {
                if (mountDir.canRead()) {
                    logger.info("Zasób sieciowy /mnt/cnc jest zamontowany i dostępny do odczytu.");
                } else {
                    logger.error("Zasób /mnt/cnc istnieje, ale brak uprawnień do odczytu.");
                }
            } else {
                logger.error("/mnt/cnc istnieje, ale nie jest katalogiem.");
            }
        } else {
            logger.error("Zasób sieciowy /mnt/cnc nie jest zamontowany lub nie istnieje.");
        }
    }
}