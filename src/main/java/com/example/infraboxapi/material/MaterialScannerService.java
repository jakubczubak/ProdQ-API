package com.example.infraboxapi.material;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@EnableScheduling
public class MaterialScannerService {

    private final NotificationService notificationService;
    private final MaterialRepository materialRepository;
    private final String pdfDirectory;

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.host:}")
    private String serverHost;

    public MaterialScannerService(NotificationService notificationService, MaterialRepository materialRepository) {
        this.notificationService = notificationService;
        this.materialRepository = materialRepository;
        this.pdfDirectory = System.getProperty("user.dir") + "/material_reports/";

        // Upewnij się, że katalog istnieje
        File directory = new File(pdfDirectory);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    @Scheduled(fixedRate = 60 * 1000) // Uruchamianie co 1 minutę
    public void scanMaterialsAndNotify() {
        List<Material> materials = materialRepository.findAll();
        StringBuilder pdfContent = new StringBuilder();

        for (Material material : materials) {
            if (material.getQuantity() < material.getMinQuantity()) {
                String quantityText = (material.getQuantity() % 1 == 0)
                        ? String.valueOf((int) material.getQuantity())
                        : String.valueOf(material.getQuantity());

                String endText = Objects.equals(material.getType(), "Plate")
                        ? "pieces left."
                        : "m left.";

                pdfContent.append("Material: ")
                        .append(material.getName())
                        .append(" - Quantity: ")
                        .append(quantityText)
                        .append(" ")
                        .append(endText)
                        .append("\n");
            }
        }

        if (pdfContent.length() > 0) {
            try {
                String date = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
                String fileName = "Material-report-" + date + ".pdf";
                String filePath = pdfDirectory + fileName;
                generatePdf(filePath, pdfContent.toString());

                // Dynamiczne pobieranie adresu IP, jeśli serverHost nie jest ustawiony
                String host = serverHost.isEmpty() ? getLocalHostAddress() : serverHost;
                String downloadLink = "https://" + host + ":" + serverPort + "/reports/" + fileName;

                notificationService.createAndSendSystemNotification(
                        "Missing materials detected. <a href='" + downloadLink + "' target='_blank'>Download the report here</a>.",
                        NotificationDescription.MaterialScanner
                );
            } catch (IOException | DocumentException e) {
                e.printStackTrace();
            }
        }
    }

    private void generatePdf(String filePath, String content) throws IOException, DocumentException {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(new File(filePath)));
        document.open();
        document.add(new Paragraph(content));
        document.close();
    }

    private String getLocalHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "localhost"; // Fallback w razie błędu
        }
    }
}
