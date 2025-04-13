package com.example.infraboxapi.material;

import com.example.infraboxapi.FilePDF.FilePDF;
import com.example.infraboxapi.FilePDF.FilePDFService;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@EnableScheduling
public class MaterialScannerService {

    private final NotificationService notificationService;
    private final MaterialRepository materialRepository;
    private final FilePDFService filePDFService;

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.host:}")
    private String serverHost;

    public MaterialScannerService(
            NotificationService notificationService,
            MaterialRepository materialRepository,
            FilePDFService filePDFService) {
        this.notificationService = notificationService;
        this.materialRepository = materialRepository;
        this.filePDFService = filePDFService;
    }

    @Scheduled(fixedRate = 30000)
    public void scanMaterialsAndNotify() {
        List<Material> materials = materialRepository.findByQuantityLessThanMinQuantity();

        if (materials.stream().anyMatch(m -> m.getQuantity() < m.getMinQuantity())) {
            try {
                // Usuń stare raporty z bazy danych
                deleteOldReports();

                String date = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
                String fileName = "Raport-materialow-" + date + ".pdf";

                // Generuj PDF jako strumień bajtów
                byte[] pdfData = generatePdf(materials);

                // Zapisz PDF w bazie danych
                FilePDF filePDF = FilePDF.builder()
                        .name(fileName)
                        .type("application/pdf")
                        .pdfData(pdfData)
                        .build();
                FilePDF savedFile = filePDFService.save(filePDF);

                // Wygeneruj link do pobrania
                String host = serverHost.isEmpty() ? getLocalHostAddress() : serverHost;
                String downloadLink = "https://" + host + ":" + serverPort + "/api/material_reports/" + savedFile.getId();

                notificationService.createAndSendSystemNotification(
                        downloadLink,
                        NotificationDescription.MaterialScanner
                );
            } catch (DocumentException e) {
                System.err.println("Błąd podczas generowania PDF: " + e.getMessage());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private void deleteOldReports() {
        // Usuń wszystkie raporty z bazy danych
        filePDFService.deleteAllReports();
    }

    private String getUnit(Material material) {
        float x = material.getX();
        float y = material.getY();
        float z = material.getZ();
        float diameter = material.getDiameter();
        float length = material.getLength();
        float thickness = material.getThickness();

        if (x > 0 && y > 0 && z > 0 && diameter == 0 && thickness == 0) {
            return " szt.";
        } else if (diameter > 0 && length > 0) {
            return " m.b.";
        }
        return "";
    }

    private byte[] generatePdf(List<Material> materials) throws DocumentException, IOException {
        Document document = new Document();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        // Nagłówek INFRABOX z czcionką Lucida Handwriting
        BaseFont lucidaBase = BaseFont.createFont(
                Objects.requireNonNull(getClass().getClassLoader().getResource("fonts/LucidaHandwriting/LucidaHandwritingStdThin.TTF")).getPath(),
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED
        );
        Font headerFont = new Font(lucidaBase, 20, Font.BOLD);

        Paragraph header = new Paragraph("I N F R A B O X", headerFont);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(20);
        document.add(header);

        // Czcionka Roboto dla reszty dokumentu
        BaseFont robotoBase = BaseFont.createFont(
                Objects.requireNonNull(getClass().getClassLoader().getResource("fonts/Roboto/Roboto-Regular.ttf")).getPath(),
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED
        );
        Font titleFont = new Font(robotoBase, 14, Font.BOLD);
        Font contentFont = new Font(robotoBase, 10);
        Font boldFont = new Font(robotoBase, 10, Font.BOLD);

        // Tytuł z datą w języku polskim
        String date = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
        Paragraph title = new Paragraph("Raport materiałów - " + date, titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        title.setSpacingAfter(15);
        document.add(title);

        // Formatowanie liczb do 2 miejsc po przecinku
        DecimalFormat df = new DecimalFormat("#.##");

        // Treść dokumentu w języku polskim
        int counter = 1;
        for (Material material : materials) {
            if (material.getQuantity() < material.getMinQuantity()) {
                Paragraph materialInfo = new Paragraph();
                materialInfo.add(new Phrase(counter++ + ". ", contentFont));

                // Nazwa z prefixem Φ dla prętów i rur
                String phiPrefix = (material.getDiameter() > 0 && material.getLength() > 0) ? "Φ " : "";
                materialInfo.add(new Phrase("Nazwa: " + phiPrefix + material.getName() + "\n", contentFont));

                // Jednostki na podstawie pól geometrycznych
                String unit = getUnit(material);
                float quantity = material.getQuantity();
                float minQuantity = material.getMinQuantity();
                String formattedQuantity = df.format(quantity);
                String formattedMinQuantity = df.format(minQuantity);
                String formattedOrderQuantity = df.format(minQuantity - quantity);

                materialInfo.add(new Phrase("   Ilość: " + formattedQuantity + unit + "\n", contentFont));
                materialInfo.add(new Phrase("   Minimalna ilość: " + formattedMinQuantity + unit + "\n", contentFont));
                materialInfo.add(new Phrase("   Ilość do zamówienia: ", contentFont));
                materialInfo.add(new Phrase(formattedOrderQuantity + unit + "\n", boldFont));
                materialInfo.setSpacingAfter(8);
                document.add(materialInfo);

                LineSeparator separator = new LineSeparator();
                separator.setLineColor(BaseColor.LIGHT_GRAY);
                document.add(separator);
            }
        }

        document.close();
        return baos.toByteArray();
    }

    private String getLocalHostAddress() {
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException e) {
            System.err.println("Nie można uzyskać adresu hosta: " + e.getMessage());
            return "localhost";
        }
    }
}