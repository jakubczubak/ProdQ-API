package com.example.infraboxapi.material;

import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
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

        File directory = new File(pdfDirectory);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    @Scheduled(cron = "0 0 12 ? * WED")
    public void scanMaterialsAndNotify() {
        List<Material> materials = materialRepository.findAll();

        if (materials.stream().anyMatch(m -> m.getQuantity() < m.getMinQuantity())) {
            try {
                deleteOldReports();

                String date = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
                String fileName = "Material-report-" + date + ".pdf";
                String filePath = pdfDirectory + fileName;
                generatePdf(filePath, materials);

                String host = serverHost.isEmpty() ? getLocalHostAddress() : serverHost;
                String downloadLink = "https://" + host + ":" + serverPort + "/material_reports/" + fileName;

                notificationService.createAndSendSystemNotification(
                        downloadLink,
                        NotificationDescription.MaterialScanner
                );
            } catch (IOException | DocumentException e) {
                e.printStackTrace();
            }
        }
    }

    private void deleteOldReports() {
        File directory = new File(pdfDirectory);
        File[] files = directory.listFiles((dir, name) -> name.startsWith("Material-report-") && name.endsWith(".pdf"));

        if (files != null) {
            for (File file : files) {
                if (!file.delete()) {
                    System.err.println("Failed to delete file: " + file.getName());
                }
            }
        }
    }

    private void generatePdf(String filePath, List<Material> materials) throws IOException, DocumentException {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(new File(filePath)));
        document.open();

        // Nagłówek INFRABOX z czcionką Lucida Handwriting
        BaseFont lucidaBase = BaseFont.createFont(getClass().getClassLoader().getResource("fonts/LucidaHandwriting/LucidaHandwritingStdThin.TTF").getPath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font headerFont = new Font(lucidaBase, 20, Font.BOLD);

        Paragraph header = new Paragraph("I N F R A B O X", headerFont);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(20);
        document.add(header);

        // Czcionka Roboto dla reszty dokumentu
        BaseFont robotoBase = BaseFont.createFont(getClass().getClassLoader().getResource("fonts/Roboto/Roboto-Regular.ttf").getPath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font titleFont = new Font(robotoBase, 14, Font.BOLD);
        Font contentFont = new Font(robotoBase, 10);
        Font boldFont = new Font(robotoBase, 10, Font.BOLD);

        // Tytuł z datą
        String date = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
        Paragraph title = new Paragraph("Material report - " + date, titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        title.setSpacingAfter(15);
        document.add(title);

        // Treść dokumentu
        int counter = 1;
        for (Material material : materials) {
            if (material.getQuantity() < material.getMinQuantity()) {
                Paragraph materialInfo = new Paragraph();
                materialInfo.add(new Phrase(counter++ + ". ", contentFont));
                materialInfo.add(new Phrase("Name: " + material.getName() + "\n", contentFont));
                materialInfo.add(new Phrase("   Quantity: " + material.getQuantity() + "\n", contentFont));
                materialInfo.add(new Phrase("   Min. Quantity: " + material.getMinQuantity() + "\n", contentFont));
                materialInfo.add(new Phrase("   Order Quantity: ", contentFont));
                materialInfo.add(new Phrase(String.valueOf(material.getMinQuantity() - material.getQuantity()) + "\n", boldFont));
                materialInfo.setSpacingAfter(8);
                document.add(materialInfo);

                LineSeparator separator = new LineSeparator();
                separator.setLineColor(BaseColor.LIGHT_GRAY);
                document.add(separator);
            }
        }

        document.close();
    }

    private String getLocalHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return "localhost";
        }
    }
}