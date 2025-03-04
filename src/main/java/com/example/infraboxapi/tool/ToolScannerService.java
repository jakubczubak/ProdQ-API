package com.example.infraboxapi.tool;

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
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
@EnableScheduling
public class ToolScannerService {

    private final NotificationService notificationService;
    private final ToolRepository toolRepository;
    private final String pdfDirectory;

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.host:}")
    private String serverHost;

    public ToolScannerService(NotificationService notificationService, ToolRepository toolRepository) {
        this.notificationService = notificationService;
        this.toolRepository = toolRepository;
        this.pdfDirectory = System.getProperty("user.dir") + "/tool_reports/";

        File directory = new File(pdfDirectory);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    @Scheduled(cron = "0 0 12 ? * WED")
    public void scanToolsAndNotify() {
        List<Tool> tools = toolRepository.findAll();

        if (tools.stream().anyMatch(t -> t.getQuantity() < t.getMinQuantity())) {
            try {
                deleteOldReports();

                String date = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
                String fileName = "Tool-report-" + date + ".pdf";
                String filePath = pdfDirectory + fileName;
                generatePdf(filePath, tools);

                String host = serverHost.isEmpty() ? getLocalHostAddress() : serverHost;
                String downloadLink = "https://" + host + ":" + serverPort + "/tool_reports/" + fileName;

                notificationService.createAndSendSystemNotification(
                        downloadLink,
                        NotificationDescription.ToolScanner
                );
            } catch (IOException | DocumentException e) {
                e.printStackTrace();
            }
        }
    }

    private void deleteOldReports() {
        File directory = new File(pdfDirectory);
        File[] files = directory.listFiles((dir, name) -> name.startsWith("Tool-report-") && name.endsWith(".pdf"));

        if (files != null) {
            for (File file : files) {
                if (!file.delete()) {
                    System.err.println("Failed to delete file: " + file.getName());
                }
            }
        }
    }

    private void generatePdf(String filePath, List<Tool> tools) throws IOException, DocumentException {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(new File(filePath)));
        document.open();

        // Nagłówek INFRABOX z czcionką Lucida Handwriting
        BaseFont lucidaBase = BaseFont.createFont(Objects.requireNonNull(getClass().getClassLoader().getResource("fonts/LucidaHandwriting/LucidaHandwritingStdThin.TTF")).getPath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font headerFont = new Font(lucidaBase, 20, Font.BOLD);

        Paragraph header = new Paragraph("I N F R A B O X", headerFont);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(20);
        document.add(header);

        // Czcionka Roboto dla reszty dokumentu
        BaseFont robotoBase = BaseFont.createFont(Objects.requireNonNull(getClass().getClassLoader().getResource("fonts/Roboto/Roboto-Regular.ttf")).getPath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font titleFont = new Font(robotoBase, 14, Font.BOLD);
        Font contentFont = new Font(robotoBase, 10);
        Font boldFont = new Font(robotoBase, 10, Font.BOLD);

        // Tytuł z datą
        String date = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
        Paragraph title = new Paragraph("Tool report - " + date, titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        title.setSpacingAfter(15);
        document.add(title);

        // Formatowanie liczb do 2 miejsc po przecinku
        DecimalFormat df = new DecimalFormat("#.##");

        // Treść dokumentu
        int counter = 1;
        for (Tool tool : tools) {
            if (tool.getQuantity() < tool.getMinQuantity()) {
                Paragraph toolInfo = new Paragraph();
                toolInfo.add(new Phrase(counter++ + ". ", contentFont));
                toolInfo.add(new Phrase("Name: " + tool.getName() + "\n", contentFont));

                // Zaokrąglanie wartości i dodanie jednostki pc
                String quantity = df.format(tool.getQuantity());
                String minQuantity = df.format(tool.getMinQuantity());
                String orderQuantity = df.format(tool.getMinQuantity() - tool.getQuantity());

                toolInfo.add(new Phrase("   Quantity: " + quantity + " pc\n", contentFont));
                toolInfo.add(new Phrase("   Min. Quantity: " + minQuantity + " pc\n", contentFont));
                toolInfo.add(new Phrase("   Order Quantity: ", contentFont));
                toolInfo.add(new Phrase(orderQuantity + " pc\n", boldFont));
                toolInfo.setSpacingAfter(8);
                document.add(toolInfo);

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