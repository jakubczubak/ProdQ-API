package com.example.infraboxapi.File;


import com.example.infraboxapi.materialGroup.MaterialGroup;
import com.example.infraboxapi.materialGroup.MaterialGroupRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@AllArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final MaterialGroupRepository materialGroupRepository;

    public File createFile(MultipartFile file) throws IOException {

        return File.builder()
                .name(file.getOriginalFilename())
                .type(file.getContentType())
                .imageData(file.getBytes())
                .build();
    }

    public File updateFile(MultipartFile file, File oldFile) throws IOException {

        if(oldFile == null) {
            return createFile(file);
        }
        oldFile.setName(file.getOriginalFilename());
        oldFile.setType(file.getContentType());
        oldFile.setImageData(file.getBytes());

        return oldFile;
    }


}
