package com.example.infraboxapi.productionQueue;

import com.example.infraboxapi.productionQueueItem.ProductionQueueItem;
import com.example.infraboxapi.productionQueueItem.ProductionQueueItemService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class ProductionQueueService {

    private final ProductionQueueRepository productionQueueRepository;
    private final ProductionQueueItemService productionQueueItemService;

    @Autowired
    public ProductionQueueService(
            ProductionQueueRepository productionQueueRepository,
            ProductionQueueItemService productionQueueItemService) {
        this.productionQueueRepository = productionQueueRepository;
        this.productionQueueItemService = productionQueueItemService;
    }

    // Inicjalizacja jedynej instancji ProductionQueue przy starcie
    @PostConstruct
    public void init() {
        if (productionQueueRepository.count() == 0) {
            ProductionQueue queue = ProductionQueue.builder().build();
            productionQueueRepository.save(queue);
        }
    }

    public ProductionQueue getSingleQueue() {
        List<ProductionQueue> queues = productionQueueRepository.findAll();
        if (queues.isEmpty()) {
            ProductionQueue queue = ProductionQueue.builder().build();
            return productionQueueRepository.save(queue);
        }
        return queues.get(0); // Zwraca jedyną instancję
    }

    public ProductionQueue save(ProductionQueue queue) {
        return productionQueueRepository.save(queue);
    }

    public Optional<ProductionQueue> findById(Integer id) {
        return productionQueueRepository.findById(id);
    }

    public List<ProductionQueue> findAll() {
        return productionQueueRepository.findAll();
    }

    public void deleteById(Integer id) {
        productionQueueRepository.deleteById(id);
    }

    // Dodawanie nowego elementu do kolejki
    public ProductionQueue addItemToQueue(ProductionQueueItem item, String queueType) {
        ProductionQueue queue = getSingleQueue();
        item.setProductionQueue(queue);
        item.setQueueType(queueType);
        queue.getItems().add(item);
        return productionQueueRepository.save(queue);
    }

    // Przenoszenie elementu między listami
    public ProductionQueue moveItemToQueue(Integer itemId, String newQueueType) {
        ProductionQueue queue = getSingleQueue();
        Optional<ProductionQueueItem> itemOpt = queue.getItems().stream()
                .filter(item -> item.getId().equals(itemId))
                .findFirst();

        if (itemOpt.isPresent()) {
            ProductionQueueItem item = itemOpt.get();
            item.setQueueType(newQueueType);
            return productionQueueRepository.save(queue);
        } else {
            throw new RuntimeException("ProductionQueueItem with ID " + itemId + " not found");
        }
    }
}