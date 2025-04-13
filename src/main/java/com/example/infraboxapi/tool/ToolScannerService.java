package com.example.infraboxapi.tool;

import com.example.infraboxapi.FilePDF.FilePDF;
import com.example.infraboxapi.FilePDF.FilePDFService;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfWriter;
import com.itextpdf.text.pdf.draw.LineSeparator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RestController
@RequestMapping("/api/tool")
@EnableScheduling
public class ToolScannerService {

    private final NotificationService notificationService;
    private final ToolRepository toolRepository;
    private final FilePDFService filePDFService;

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.host:}")
    private String serverHost;

    public ToolScannerService(
            NotificationService notificationService,
            ToolRepository toolRepository,
            FilePDFService filePDFService) {
        this.notificationService = notificationService;
        this.toolRepository = toolRepository;
        this.filePDFService = filePDFService;
    }

    @Scheduled(cron = "0 0 10 1-7 * MON") // Pierwszy poniedziałek miesiąca
    @Scheduled(cron = "0 0 10 15-21 * MON") // Trzeci poniedziałek miesiąca
    public void scanToolsAndNotify() {
        List<Tool> tools = toolRepository.findByQuantityLessThanMinQuantity();

        if (tools.stream().anyMatch(t -> t.getQuantity() < t.getMinQuantity())) {
            try {
                // Usuń stare raporty z bazy danych
                deleteOldReports();

                String date = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
                String fileName = "Raport-narzedzi-" + date + ".pdf";

                // Generuj PDF jako strumień bajtów
                byte[] pdfData = generatePdf(tools);

                // Zapisz PDF w bazie danych
                FilePDF filePDF = FilePDF.builder()
                        .name(fileName)
                        .type("application/pdf")
                        .pdfData(pdfData)
                        .build();
                FilePDF savedFile = filePDFService.save(filePDF);

                // Wygeneruj link do pobrania
                String host = serverHost.isEmpty() ? getLocalHostAddress() : serverHost;
                String downloadLink = "https://" + host + ":" + serverPort + "/api/tool/reports/" + savedFile.getId();

                notificationService.createAndSendSystemNotification(
                        downloadLink,
                        NotificationDescription.ToolScanner
                );
            } catch (DocumentException e) {
                System.err.println("Błąd podczas generowania PDF: " + e.getMessage());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id) {
        FilePDF filePDF = filePDFService.findById(id)
                .orElseThrow(() -> new RuntimeException("Report not found"));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filePDF.getName() + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .contentType(org.springframework.http.MediaType.APPLICATION_PDF)
                .body(filePDF.getPdfData());
    }

    private void deleteOldReports() {
        // Usuń wszystkie raporty z bazy danych
        filePDFService.deleteAllReports();
    }

    private byte[] generatePdf(List<Tool> tools) throws DocumentException, IOException {
        Document document = new Document();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfWriter.getInstance(document, baos);
        document.open();

        // Dodaj logo
        try {
            String logoPath = Objects.requireNonNull(getClass().getClassLoader().getResource("logo/logo_white.png")).toString();
            Image logo = Image.getInstance(logoPath);
            logo.scaleToFit(150, 50);
            logo.setAlignment(Element.ALIGN_CENTER);
            logo.setSpacingAfter(20);
            document.add(logo);
        } catch (Exception e) {
            System.err.println("Błąd podczas dodawania logo: " + e.getMessage());
            e.printStackTrace();
            // Fallback do pustego akapitu
            Paragraph fallback = new Paragraph("");
            fallback.setSpacingAfter(20);
            document.add(fallback);
        }

        // Czcionka Roboto dla reszty dokumentu
        BaseFont robotoBase = BaseFont.createFont(
                Objects.requireNonNull(getClass().getClassLoader().getResource("fonts/Roboto/Roboto-Regular.ttf")).getPath(),
                BaseFont.IDENTITY_H,
                BaseFont.EMBEDDED
        );
        Font titleFont = new Font(robotoBase, 14, Font.BOLD);
        Font contentFont = new Font(robotoBase, 10);
        Font boldFont = new Font(robotoBase, 10, Font.BOLD);

        // Tytuł z datą
        String date = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
        Paragraph title = new Paragraph("Raport narzędzi - " + date, titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        title.setSpacingAfter(15);
        document.add(title);

        // Formatowanie liczb
        DecimalFormat df = new DecimalFormat("#.##");

        // Treść raportu
        int counter = 1;
        for (Tool tool : tools) {
            if (tool.getQuantity() < tool.getMinQuantity()) {
                Paragraph toolInfo = new Paragraph();
                toolInfo.add(new Phrase(counter++ + ". ", contentFont));
                toolInfo.add(new Phrase("Nazwa: " + tool.getName() + "\n", contentFont));

                String quantity = df.format(tool.getQuantity());
                String minQuantity = df.format(tool.getMinQuantity());
                String orderQuantity = df.format(tool.getMinQuantity() - tool.getQuantity());

                toolInfo.add(new Phrase("   Ilość: " + quantity + " szt.\n", contentFont));
                toolInfo.add(new Phrase("   Minimalna ilość: " + minQuantity + " szt.\n", contentFont));
                toolInfo.add(new Phrase("   Ilość do zamówienia: ", contentFont));
                toolInfo.add(new Phrase(orderQuantity + " szt.\n", boldFont));
                toolInfo.setSpacingAfter(8);
                document.add(toolInfo);

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