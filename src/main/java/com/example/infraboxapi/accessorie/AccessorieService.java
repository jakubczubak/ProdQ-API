package com.example.infraboxapi.accessorie;


import com.example.infraboxapi.FileImage.FileImage;
import com.example.infraboxapi.FileImage.FileImageRepository;
import com.example.infraboxapi.FileImage.FileImageService;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;

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
        notificationService.createAndSendNotification("A new accessorie has been added: `" + accessorie.getName() + "`", NotificationDescription.AccessorieAdded);
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
        notificationService.createAndSendNotification("Accessorie updated: `" + accessorie.getName() + "`", NotificationDescription.AccessorieUpdated);
    }

    public void deleteAccessorie(Integer id) {

        Accessorie accessorie = accessorieReposotory.findById(id).orElseThrow(() -> new RuntimeException("Accessorie not found"));
        accessorieReposotory.delete(accessorie);
        notificationService.createAndSendNotification("Accessorie deleted: `" + accessorie.getName() + "`", NotificationDescription.AccessorieDeleted);
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
