package com.example.infraboxapi.FilePDF;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@AllArgsConstructor
public class FilePDFService {

    public FilePDF createFile(MultipartFile file) throws IOException {

        return FilePDF.builder()
                .name(file.getOriginalFilename())
                .type(file.getContentType())
                .pdfData(file.getBytes())
                .build();
    }

    public FilePDF updateFile(MultipartFile file, FilePDF oldFilePDF) throws IOException {

        if (oldFilePDF == null) {
            return createFile(file);
        }
        oldFilePDF.setName(file.getOriginalFilename());
        oldFilePDF.setType(file.getContentType());
        oldFilePDF.setPdfData(file.getBytes());

        return oldFilePDF;
    }
}
