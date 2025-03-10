package com.example.infraboxapi.accessorieItem;

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
public class AccessorieItemScannerService {

    private final NotificationService notificationService;
    private final AccessorieItemRepository accessorieItemRepository;
    private final String pdfDirectory;

    @Value("${server.port:8443}")
    private int serverPort;

    @Value("${server.host:}")
    private String serverHost;

    public AccessorieItemScannerService(NotificationService notificationService, AccessorieItemRepository accessorieItemRepository) {
        this.notificationService = notificationService;
        this.accessorieItemRepository = accessorieItemRepository;
        this.pdfDirectory = System.getProperty("user.dir") + "/accessorie_reports/";

        File directory = new File(pdfDirectory);
        if (!directory.exists()) {
            directory.mkdirs();
        }
    }

    @Scheduled(cron = "0 * * * * ?")
    public void scanAccessoriesAndNotify() {
        List<AccessorieItem> accessorieItems = accessorieItemRepository.findByQuantityLessThanMinQuantity();

        if (accessorieItems.stream().anyMatch(ai -> ai.getQuantity() < ai.getMinQuantity())) {
            try {
                deleteOldReports();

                String date = new SimpleDateFormat("dd-MM-yyyy").format(new Date());
                String fileName = "Raport-akcesoriow-" + date + ".pdf";
                String filePath = pdfDirectory + fileName;
                generatePdf(filePath, accessorieItems);

                String host = serverHost.isEmpty() ? getLocalHostAddress() : serverHost;
                String downloadLink = "https://" + host + ":" + serverPort + "/accessorie_reports/" + fileName;

                notificationService.createAndSendSystemNotification(
                        downloadLink,
                        NotificationDescription.AccessorieItemScanner
                );
            } catch (IOException | DocumentException e) {
                e.printStackTrace();
            }
        }
    }

    private void deleteOldReports() {
        File directory = new File(pdfDirectory);
        File[] files = directory.listFiles((dir, name) -> name.startsWith("Raport-akcesoriow-") && name.endsWith(".pdf"));

        if (files != null) {
            for (File file : files) {
                if (!file.delete()) {
                    System.err.println("Nie udało się usunąć pliku: " + file.getName());
                }
            }
        }
    }

    private void generatePdf(String filePath, List<AccessorieItem> accessorieItems) throws IOException, DocumentException {
        Document document = new Document();
        PdfWriter.getInstance(document, new FileOutputStream(new File(filePath)));
        document.open();

        // Nagłówek INFRABOX
        BaseFont lucidaBase = BaseFont.createFont(Objects.requireNonNull(getClass().getClassLoader().getResource("fonts/LucidaHandwriting/LucidaHandwritingStdThin.TTF")).getPath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font headerFont = new Font(lucidaBase, 20, Font.BOLD);
        Paragraph header = new Paragraph("I N F R A B O X", headerFont);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(20);
        document.add(header);

        // Czcionka Roboto
        BaseFont robotoBase = BaseFont.createFont(Objects.requireNonNull(getClass().getClassLoader().getResource("fonts/Roboto/Roboto-Regular.ttf")).getPath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
        Font titleFont = new Font(robotoBase, 14, Font.BOLD);
        Font contentFont = new Font(robotoBase, 10);
        Font boldFont = new Font(robotoBase, 10, Font.BOLD);

        // Tytuł
        String date = new SimpleDateFormat("dd/MM/yyyy").format(new Date());
        Paragraph title = new Paragraph("Raport akcesoriów - " + date, titleFont);
        title.setAlignment(Element.ALIGN_LEFT);
        title.setSpacingAfter(15);
        document.add(title);

        // Formatowanie liczb
        DecimalFormat df = new DecimalFormat("#.##");

        // Treść
        int counter = 1;
        for (AccessorieItem accessorieItem : accessorieItems) {
            if (accessorieItem.getQuantity() < accessorieItem.getMinQuantity()) {
                Paragraph itemInfo = new Paragraph();
                itemInfo.add(new Phrase(counter++ + ". ", contentFont));
                itemInfo.add(new Phrase("Nazwa: " + accessorieItem.getName() + "\n", contentFont));

                String quantity = df.format(accessorieItem.getQuantity());
                String minQuantity = df.format(accessorieItem.getMinQuantity());
                String orderQuantity = df.format(accessorieItem.getMinQuantity() - accessorieItem.getQuantity());

                itemInfo.add(new Phrase("   Ilość: " + quantity + " szt.\n", contentFont));
                itemInfo.add(new Phrase("   Minimalna ilość: " + minQuantity + " szt.\n", contentFont));
                itemInfo.add(new Phrase("   Ilość do zamówienia: ", contentFont));
                itemInfo.add(new Phrase(orderQuantity + " szt.\n", boldFont));
                itemInfo.setSpacingAfter(8);
                document.add(itemInfo);

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