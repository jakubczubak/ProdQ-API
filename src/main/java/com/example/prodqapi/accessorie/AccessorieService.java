package com.example.prodqapi.accessorie;


import com.example.prodqapi.FileImage.FileImage;
import com.example.prodqapi.FileImage.FileImageRepository;
import com.example.prodqapi.FileImage.FileImageService;
import com.example.prodqapi.notification.NotificationDescription;
import com.example.prodqapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

@Service
@AllArgsConstructor
public class AccessorieService {

    private final AccessorieReposotory accessorieReposotory;
    private final FileImageService fileImageService;
    private final FileImageRepository fileImageRepository;
    private final NotificationService notificationService;

    @Transactional
    public void createAccessorie(AccessorieDTO accessorieDTO)  throws IOException {

        Accessorie accessorie = Accessorie.builder()
                .name(accessorieDTO.getName())
                .accessorieItems(new ArrayList<>())
                .build();

        if (accessorieDTO.getFile() != null) {
                FileImage fileImage = fileImageService.createFile(accessorieDTO.getFile());
                accessorie.setFileImage(fileImage);
        }

        accessorieReposotory.save(accessorie);
        notificationService.sendNotification(NotificationDescription.AccessoriesAdded, Map.of("name", accessorie.getName()));
    }

    @Transactional
    public void updateAccessorie(AccessorieDTO accessorieDTO) throws IOException {

        Accessorie accessorie = accessorieReposotory.findById(accessorieDTO.getId()).orElseThrow(() -> new RuntimeException("Accessorie not found"));
        accessorie.setName(accessorieDTO.getName());

        if (accessorieDTO.getFile() != null) {
            FileImage fileImage = fileImageService.updateFile(accessorieDTO.getFile(), accessorie.getFileImage());
            accessorie.setFileImage(fileImage);
        }

        accessorieReposotory.save(accessorie);
        notificationService.sendNotification(NotificationDescription.AccessoriesUpdated, Map.of("name", accessorie.getName()));
    }

    public void deleteAccessorie(Integer id) {

        Accessorie accessorie = accessorieReposotory.findById(id).orElseThrow(() -> new RuntimeException("Accessorie not found"));
        accessorieReposotory.delete(accessorie);
        notificationService.sendNotification(NotificationDescription.AccessoriesDeleted, Map.of("name", accessorie.getName()));
    }

    public Accessorie getAccessorie(Integer id) {

        return accessorieReposotory.findById(id).orElseThrow(() -> new RuntimeException("Accessorie not found"));
    }

    public Iterable<Accessorie> getAccessories() {
        return accessorieReposotory.findAll();
    }

    public void deleteAccessorieFile(Integer fileID, Integer accessorieID) {

        Accessorie accessorie = accessorieReposotory.findById(accessorieID).orElseThrow(() -> new RuntimeException("Accessorie not found"));

        FileImage fileImage = fileImageRepository.findById(Long.valueOf(fileID)).orElseThrow(() -> new RuntimeException("File not found"));

        accessorie.setFileImage(null);

        accessorieReposotory.save(accessorie);

        fileImageRepository.delete(fileImage);
    }
}
