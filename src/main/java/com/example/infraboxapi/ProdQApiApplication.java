package com.example.infraboxapi;

import com.example.infraboxapi.productionQueueItem.DirectoryCleanupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class ProdQApiApplication {

    private static final Logger logger = LoggerFactory.getLogger(ProdQApiApplication.class);

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DirectoryCleanupService directoryCleanupService;

    private final AtomicBoolean isProcessingRequests = new AtomicBoolean(false);

    public static void main(String[] args) {
        SpringApplication.run(ProdQApiApplication.class, args);
    }

    @PostConstruct
    public void onStartup() {
        checkMountOnStartup();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        logger.info("Application is fully started. Running cleanup task for all machines...");
        try {
            directoryCleanupService.cleanupAllMachines();
            logger.info("Cleanup task completed successfully.");
        } catch (Exception e) {
            logger.error("Error during cleanup task after application start: {}", e.getMessage(), e);
        }
    }

    private void checkMountOnStartup() {
        String appEnv = System.getenv("APP_ENV") != null ? System.getenv("APP_ENV") : "local";
        Path mountDir;

        if ("prod".equalsIgnoreCase(appEnv)) {
            mountDir = Paths.get("/cnc");
        } else if ("docker-local".equalsIgnoreCase(appEnv)) {
            mountDir = Paths.get("/cnc");
            ensureDirectoryExists(mountDir);
        } else {
            mountDir = Paths.get("./cnc");
            ensureDirectoryExists(mountDir);
        }
        checkMountedResource(mountDir);
    }

    private void restartApplication() {
        logger.info("Starting application restart...");

        int retryCount = 0;
        int maxRetries = 12;
        while (isProcessingRequests.get() && retryCount < maxRetries) {
            logger.warn("Application is processing requests/tasks. Delaying restart... (Attempt {}/{})", retryCount + 1, maxRetries);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                logger.error("Restart delay interrupted: {}", e.getMessage());
                Thread.currentThread().interrupt();
            }
            retryCount++;
        }

        if (isProcessingRequests.get()) {
            logger.error("Application still processing requests after {} attempts. Canceling restart.", maxRetries);
            return;
        }

        Thread shutdownThread = new Thread(() -> {
            logger.info("Closing Spring application context...");
            System.exit(SpringApplication.exit(applicationContext, () -> 0));
        });
        shutdownThread.setDaemon(false);
        shutdownThread.start();
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        logger.info("Application context closed. Web server should stop automatically.");
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void scheduleApplicationRestart() {
        logger.info("Scheduled application restart at 00:00...");
        restartApplication();
    }

    public void setProcessingRequests(boolean processing) {
        isProcessingRequests.set(processing);
    }

    private void ensureDirectoryExists(Path directory) {
        if (!Files.exists(directory)) {
            try {
                Files.createDirectories(directory);
                logger.info("Created directory {} for local or docker-local mode.", directory.toString());
            } catch (IOException e) {
                logger.error("Error creating directory {}: {}", directory, e.getMessage());
            }
        } else if (!Files.isDirectory(directory)) {
            logger.error("{} exists but is not a directory.", directory);
        }
    }

    private void checkMountedResource(Path mountDir) {
        if (Files.exists(mountDir) && Files.isDirectory(mountDir)) {
            if (Files.isReadable(mountDir)) {
                logger.info("Resource {} is readable.", mountDir);
                listDirectoryContents(mountDir);
            } else {
                logger.error("Resource {} exists but no read permissions.", mountDir);
            }
            if (Files.isWritable(mountDir)) {
                logger.info("Resource {} is writable.", mountDir);
                Path testFile = mountDir.resolve("test.txt");
                try {
                    Files.writeString(testFile, "Test");
                    logger.info("Created test file in {} - write works correctly.", mountDir);
                    Files.deleteIfExists(testFile);
                    logger.info("Deleted test file from {} - delete works correctly.", mountDir);
                } catch (IOException e) {
                    logger.error("Error creating/deleting test file in {}: {}", mountDir, e.getMessage());
                }
            } else {
                logger.error("Resource {} exists but no write permissions.", mountDir);
            }
        } else {
            logger.error("Resource {} is not mounted or does not exist.", mountDir);
        }
    }

    private void listDirectoryContents(Path directory) {
        try (var contents = Files.list(directory)) {
            var files = contents.collect(Collectors.toList());
            if (!files.isEmpty()) {
                logger.info("Contents of directory {}:", directory);
                for (Path file : files) {
                    String type = Files.isDirectory(file) ? "directory" : "file";
                    logger.info("- {} ({})", file.getFileName(), type);
                }
            } else {
                logger.info("Directory {} is empty.", directory);
            }
        } catch (IOException e) {
            logger.error("Failed to list contents of {} - possible permission or I/O error.", directory, e);
        }
    }
}
