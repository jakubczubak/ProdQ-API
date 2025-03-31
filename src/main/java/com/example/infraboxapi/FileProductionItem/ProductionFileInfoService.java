package com.example.infraboxapi.FileProductionItem;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductionFileInfoService {

    private final ProductionFileInfoRepository productionFileInfoRepository;

    @Autowired
    public ProductionFileInfoService(ProductionFileInfoRepository productionFileInfoRepository) {
        this.productionFileInfoRepository = productionFileInfoRepository;
    }

    public ProductionFileInfo save(ProductionFileInfo fileInfo) {
        return productionFileInfoRepository.save(fileInfo);
    }

    public List<ProductionFileInfo> saveAll(List<ProductionFileInfo> fileInfos) {
        return productionFileInfoRepository.saveAll(fileInfos);
    }

    public Optional<ProductionFileInfo> findById(Long id) {
        return productionFileInfoRepository.findById(id);
    }

    public List<ProductionFileInfo> findAll() {
        return productionFileInfoRepository.findAll();
    }

    public void deleteById(Long id) {
        productionFileInfoRepository.deleteById(id);
    }
}