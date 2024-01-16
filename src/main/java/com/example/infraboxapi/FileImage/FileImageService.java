package com.example.infraboxapi.FileImage;


import com.example.infraboxapi.materialGroup.MaterialGroupRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Service
@AllArgsConstructor
public class FileImageService {

    public FileImage createFile(MultipartFile file) throws IOException {

        return FileImage.builder()
                .name(file.getOriginalFilename())
                .type(file.getContentType())
                .imageData(file.getBytes())
                .build();
    }

    public FileImage updateFile(MultipartFile file, FileImage oldFileImage) throws IOException {

        if(oldFileImage == null) {
            return createFile(file);
        }
        oldFileImage.setName(file.getOriginalFilename());
        oldFileImage.setType(file.getContentType());
        oldFileImage.setImageData(file.getBytes());

        return oldFileImage;
    }


}
