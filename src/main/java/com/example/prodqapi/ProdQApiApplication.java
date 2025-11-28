package com.example.prodqapi;

import com.example.prodqapi.productionQueueItem.DirectoryCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class ProdQApiApplication {

    private static final Logger logger = LoggerFactory.getLogger(ProdQApiApplication.class);

    @Autowired
    private DirectoryCleanupService directoryCleanupService;

    public static void main(String[] args) {
        SpringApplication.run(ProdQApiApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("ProdQ API started successfully. Running initial cleanup...");
        try {
            directoryCleanupService.cleanupAllMachines();
            logger.info("Initial cleanup completed.");
        } catch (Exception e) {
            logger.error("Error during initial cleanup: {}", e.getMessage(), e);
        }
    }
}
