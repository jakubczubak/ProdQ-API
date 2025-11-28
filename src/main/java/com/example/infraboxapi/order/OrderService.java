package com.example.infraboxapi.order;

import com.example.infraboxapi.accessorie.Accessorie;
import com.example.infraboxapi.accessorie.AccessorieReposotory;
import com.example.infraboxapi.accessorieItem.AccessorieItem;
import com.example.infraboxapi.accessorieItem.AccessorieItemRepository;
import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.material.MaterialRepository;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.orderChangeLog.OrderChangeLog;
import com.example.infraboxapi.orderChangeLog.OrderChangeLogRepository;
import com.example.infraboxapi.orderItem.OrderItem;
import com.example.infraboxapi.orderItem.OrderItemDTO;
import com.example.infraboxapi.orderItem.OrderItemRepository;
import com.example.infraboxapi.supplier.Supplier;
import com.example.infraboxapi.supplier.SupplierRepository;
import com.example.infraboxapi.supplier.SupplierPerformanceService;
import com.example.infraboxapi.tool.Tool;
import com.example.infraboxapi.tool.ToolRepository;
import com.example.infraboxapi.invoiceItem.InvoiceItem;
import com.example.infraboxapi.invoiceItem.InvoiceItemRepository;
import com.example.infraboxapi.invoiceReconciliation.InvoiceReconciliation;
import com.example.infraboxapi.invoiceReconciliation.InvoiceReconciliationRepository;
import com.example.infraboxapi.invoiceReconciliation.InvoiceDiscrepancy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@AllArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MaterialRepository materialRepository;
    private final ToolRepository toolRepository;
    private final AccessorieReposotory accessorieRepository;
    private final AccessorieItemRepository accessorieItemRepository;
    private final SupplierRepository supplierRepository;
    private final NotificationService notificationService;
    private final OrderChangeLogRepository orderChangeLogRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final InvoiceReconciliationRepository invoiceReconciliationRepository;
    private final ObjectMapper objectMapper;
    private final SupplierPerformanceService supplierPerformanceService;

    public List<Order> getAllOrders() {
        // Use JOIN FETCH to avoid N+1 query problem
        return orderRepository.findAllWithSupplierAndItems();
    }

    public Order getOrderById(Integer id) {
        return orderRepository.findById(id).orElse(null);
    }

    @Transactional
    public Order addOrder(OrderDTO orderDTO) {
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemDTO orderItemDTO : orderDTO.getOrderItems()) {
            OrderItem orderItem = createOrderItemFromDTO(orderItemDTO);
            orderItems.add(orderItem);
        }

        // Fetch supplier if supplierId provided
        Supplier supplier = null;
        String supplierEmail = orderDTO.getSupplierEmail();

        if (orderDTO.getSupplierId() != null) {
            supplier = supplierRepository.findById(orderDTO.getSupplierId()).orElse(null);
            // Use supplier email if available
            if (supplier != null && supplier.getEmail() != null) {
                supplierEmail = supplier.getEmail();
            }
        }

        Order order = Order.builder()
                .name(orderDTO.getName())
                .date(orderDTO.getDate())
                .status(orderDTO.getStatus())
                .supplierEmail(supplierEmail)
                .supplierMessage(orderDTO.getSupplierMessage())
                .trackingNumber(orderDTO.getTrackingNumber())
                .totalNet(orderDTO.getTotalNet())
                .totalVat(orderDTO.getTotalVat())
                .totalGross(orderDTO.getTotalGross())
                .supplier(supplier)
                .expectedDeliveryDate(orderDTO.getExpectedDeliveryDate())
                .orderItems(orderItems)
                .externalQuantityUpdated(false)
                .transitQuantitySet(false)
                .build();

        Order savedOrder = orderRepository.save(order);

        notificationService.createAndSendNotification("Order '" + order.getName() + "' has been added.", NotificationDescription.OrderAdded);

        return savedOrder;
    }

    private OrderItem createOrderItemFromDTO(OrderItemDTO orderItemDTO) {
        String itemType = orderItemDTO.getItemType();
        OrderItem.OrderItemBuilder orderItemBuilder = OrderItem.builder()
                .name(orderItemDTO.getName())
                .quantity(orderItemDTO.getQuantity())
                .receivedQuantity(0) // Initially 0
                .priceUpdated(false)
                .vatRate(orderItemDTO.getVatRate() != null ? orderItemDTO.getVatRate() : 23)
                .discount(orderItemDTO.getDiscount() != null ? orderItemDTO.getDiscount() : 0.0f);

        // Handle priceOverride if provided (allow 0 values for explicit price overrides)
        if (orderItemDTO.getPriceOverride() != null) {
            BigDecimal newPrice = BigDecimal.valueOf(orderItemDTO.getPriceOverride());
            orderItemBuilder.newPrice(newPrice);
        }

        // Handle pricePerKg if provided (for materials, allow 0 values)
        if (orderItemDTO.getPricePerKg() != null) {
            BigDecimal pricePerKg = BigDecimal.valueOf(orderItemDTO.getPricePerKg());
            orderItemBuilder.pricePerKg(pricePerKg);
        }

        if ("tool".equals(itemType)) {
            orderItemBuilder.tool(toolRepository.findById(orderItemDTO.getItemID()).orElse(null));
        } else if ("material".equals(itemType)) {
            orderItemBuilder.material(materialRepository.findById(orderItemDTO.getItemID()).orElse(null));
        } else if ("accessorie".equals(itemType)) {
            // Find Accessorie by AccessorieItem ID
            Optional<AccessorieItem> accessorieItemOpt = accessorieItemRepository.findById(orderItemDTO.getItemID());
            if (accessorieItemOpt.isPresent()) {
                AccessorieItem accessorieItem = accessorieItemOpt.get();
                // Find parent Accessorie
                Optional<Accessorie> accessorieOpt = accessorieRepository.findAll().stream()
                        .filter(acc -> acc.getAccessorieItems().stream()
                                .anyMatch(item -> item.getId().equals(accessorieItem.getId())))
                        .findFirst();
                orderItemBuilder.accessorie(accessorieOpt.orElse(null));
            }
        }

        return orderItemBuilder.build();
    }

    @Transactional
    public void deleteOrder(Integer id) {
        Optional<Order> orderOptional = orderRepository.findById(id);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            if (order.isTransitQuantitySet() && "on the way".equals(order.getStatus())) {
                deleteQuantityInTransport(id);
            }
            // Delete change logs first (to avoid foreign key constraint violation)
            orderChangeLogRepository.deleteByOrderId(id);
            orderItemRepository.deleteAll(order.getOrderItems());
            orderRepository.deleteById(id);
            notificationService.createAndSendNotification("Order  '" + order.getName() + "' has been deleted.", NotificationDescription.OrderDeleted);
        }


    }


    @Transactional
    public void updateOrderFromDTO(OrderDTO orderDTO) {

        Optional<Order> orderOptional = orderRepository.findById(orderDTO.getId());

        if (orderOptional.isPresent()) {
            Order existingOrder = orderOptional.get();

            // Update basic fields
            if (orderDTO.getName() != null) {
                existingOrder.setName(orderDTO.getName());
            }
            if (orderDTO.getSupplierMessage() != null) {
                existingOrder.setSupplierMessage(orderDTO.getSupplierMessage());
            }
            if (orderDTO.getExpectedDeliveryDate() != null) {
                existingOrder.setExpectedDeliveryDate(orderDTO.getExpectedDeliveryDate());
            }
            if (orderDTO.getTrackingNumber() != null) {
                existingOrder.setTrackingNumber(orderDTO.getTrackingNumber());
            }

            // Update price totals (VAT calculations)
            if (orderDTO.getTotalNet() >= 0) {
                existingOrder.setTotalNet(orderDTO.getTotalNet());
            }
            if (orderDTO.getTotalVat() >= 0) {
                existingOrder.setTotalVat(orderDTO.getTotalVat());
            }
            if (orderDTO.getTotalGross() >= 0) {
                existingOrder.setTotalGross(orderDTO.getTotalGross());
            }

            // Update orderItems if provided
            if (orderDTO.getOrderItems() != null && !orderDTO.getOrderItems().isEmpty()) {
                // Step 1: Delete old items and flush to DB
                orderItemRepository.deleteAll(existingOrder.getOrderItems());
                orderItemRepository.flush();  // Force commit deletion

                // Step 2: Create new items from DTO
                List<OrderItem> newOrderItems = new ArrayList<>();
                for (OrderItemDTO orderItemDTO : orderDTO.getOrderItems()) {
                    OrderItem orderItem = createOrderItemFromDTO(orderItemDTO);
                    newOrderItems.add(orderItem);
                }

                // Step 3: Set items on Order (establishes relationship via @JoinColumn)
                existingOrder.setOrderItems(newOrderItems);

                // Step 4: Explicitly save new items to ensure persistence
                orderItemRepository.saveAll(newOrderItems);
                orderItemRepository.flush();  // Ensure persistence before continuing
            }

            // Update supplier if supplierId provided
            Supplier supplier = null;
            String supplierEmail = orderDTO.getSupplierEmail();

            if (orderDTO.getSupplierId() != null) {
                supplier = supplierRepository.findById(orderDTO.getSupplierId()).orElse(null);
                if (supplier != null) {
                    existingOrder.setSupplier(supplier);
                    if (supplier.getEmail() != null) {
                        supplierEmail = supplier.getEmail();
                    }
                }
            }

            if (supplierEmail != null) {
                existingOrder.setSupplierEmail(supplierEmail);
            }

            // Handle status changes with quantity management
            String newStatus = orderDTO.getStatus();
            if (newStatus != null && !newStatus.equals(existingOrder.getStatus())) {
                String currentStatus = existingOrder.getStatus();

                // Validate status transition - prevent backward transitions
                validateStatusTransition(currentStatus, newStatus);

                if ("pending".equals(newStatus)) {
                    if (existingOrder.isTransitQuantitySet()) {
                        deleteQuantityInTransport(orderDTO.getId());
                    }
                    existingOrder.setStatus(newStatus);
                    existingOrder.setExternalQuantityUpdated(false);
                    existingOrder.setTransitQuantitySet(false);

                } else if ("on the way".equals(newStatus)) {
                    if (!existingOrder.isTransitQuantitySet()) {
                        updateQuantityInTransit(orderDTO.getId());
                    }
                    existingOrder.setStatus(newStatus);
                    existingOrder.setExternalQuantityUpdated(false);
                    existingOrder.setTransitQuantitySet(true);
                    notificationService.createAndSendNotification("Order '" + existingOrder.getName() + " " + existingOrder.getDate() + "' is on the way. Check virtual magazine.", NotificationDescription.OrderOnTheWay);

                } else if ("delivered".equals(newStatus)) {
                    if (!existingOrder.isExternalQuantityUpdated()) {
                        updateExternalQuantity(orderDTO.getId());
                    }
                    existingOrder.setStatus(newStatus);
                    existingOrder.setExternalQuantityUpdated(true);
                    deleteQuantityInTransport(orderDTO.getId());
                    notificationService.createAndSendNotification("Order '" + existingOrder.getName() + " " + existingOrder.getDate() + "' has been delivered. Check virtual magazine.", NotificationDescription.OrderDelivered);
                }
            }

            // Save changes to audit log if provided
            if (orderDTO.getChanges() != null && !orderDTO.getChanges().isEmpty()) {
                saveChangesToAuditLog(existingOrder.getId(), orderDTO.getChanges());
            }

            orderRepository.save(existingOrder);
        }
    }

    @Transactional
    public void updateOrder(Order order) {
        Optional<Order> orderOptional = orderRepository.findById(order.getId());

        if (orderOptional.isPresent()) {
            Order existingOrder = orderOptional.get();

            // Update basic fields
            if (order.getName() != null) {
                existingOrder.setName(order.getName());
            }
            if (order.getSupplierMessage() != null) {
                existingOrder.setSupplierMessage(order.getSupplierMessage());
            }
            if (order.getExpectedDeliveryDate() != null) {
                existingOrder.setExpectedDeliveryDate(order.getExpectedDeliveryDate());
            }

            // Update supplier if provided
            if (order.getSupplier() != null) {
                existingOrder.setSupplier(order.getSupplier());
                // Update supplier email from supplier
                if (order.getSupplier().getEmail() != null) {
                    existingOrder.setSupplierEmail(order.getSupplier().getEmail());
                }
            } else if (order.getSupplierEmail() != null) {
                existingOrder.setSupplierEmail(order.getSupplierEmail());
            }

            // Handle status changes with quantity management
            if (order.getStatus() != null && !order.getStatus().equals(existingOrder.getStatus())) {
                if ("pending".equals(order.getStatus())) {
                    if (existingOrder.isTransitQuantitySet()) {
                        deleteQuantityInTransport(order.getId());
                    }
                    existingOrder.setStatus(order.getStatus());
                    existingOrder.setExternalQuantityUpdated(false);
                    existingOrder.setTransitQuantitySet(false);

                } else if ("on the way".equals(order.getStatus())) {
                    if (!existingOrder.isTransitQuantitySet()) {
                        updateQuantityInTransit(order.getId());
                    }
                    existingOrder.setStatus(order.getStatus());
                    existingOrder.setExternalQuantityUpdated(false);
                    existingOrder.setTransitQuantitySet(true);
                    notificationService.createAndSendNotification("Order '" + existingOrder.getName() + " " + existingOrder.getDate() + "' is on the way. Check virtual magazine.", NotificationDescription.OrderOnTheWay);

                } else if ("delivered".equals(order.getStatus())) {
                    if (!existingOrder.isExternalQuantityUpdated()) {
                        updateExternalQuantity(order.getId());
                    }
                    existingOrder.setStatus(order.getStatus());
                    existingOrder.setExternalQuantityUpdated(true);
                    deleteQuantityInTransport(order.getId());
                    notificationService.createAndSendNotification("Order '" + existingOrder.getName() + " " + existingOrder.getDate() + "' has been delivered. Check virtual magazine.", NotificationDescription.OrderDelivered);
                }
            }

            orderRepository.save(existingOrder);
        }
    }

    /**
     * Save changes to audit log for timeline tracking
     * @param orderId The order ID
     * @param changes List of changes from frontend
     */
    private void saveChangesToAuditLog(Integer orderId, List<Map<String, Object>> changes) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String currentDate = now.format(formatter);

        for (Map<String, Object> change : changes) {
            OrderChangeLog changeLog = OrderChangeLog.builder()
                    .orderId(orderId)
                    .type((String) change.get("type"))
                    .itemName((String) change.get("itemName"))
                    .field((String) change.get("field"))
                    .oldValue(change.get("oldValue") != null ? change.get("oldValue").toString() : null)
                    .newValue(change.get("newValue") != null ? change.get("newValue").toString() : null)
                    .description((String) change.get("description"))
                    .date(currentDate)
                    .build();

            orderChangeLogRepository.save(changeLog);
        }
    }


    public void deleteQuantityInTransport(Integer id) {
        Optional<Order> orderOptional = orderRepository.findById(id);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            for (OrderItem orderItem : order.getOrderItems()) {
                if (orderItem.getMaterial() != null) {
                    Material material = materialRepository.findById(orderItem.getMaterial().getId()).orElse(null);

                    if (material != null) {
                        material.setQuantityInTransit(Math.max(material.getQuantityInTransit() - orderItem.getQuantity(), 0));

                        materialRepository.save(material);
                    }
                } else if (orderItem.getTool() != null) {

                    Tool tool = toolRepository.findById(orderItem.getTool().getId()).orElse(null);

                    if (tool != null) {
                        if (tool.getQuantityInTransit() - orderItem.getQuantity() < 0) {
                            tool.setQuantityInTransit(0);
                        } else {
                            tool.setQuantityInTransit(tool.getQuantityInTransit() - orderItem.getQuantity());
                        }

                        toolRepository.save(tool);
                    }
                } else if (orderItem.getAccessorie() != null) {
                    // Handle accessories
                    Accessorie accessorie = accessorieRepository.findById(orderItem.getAccessorie().getId()).orElse(null);

                    if (accessorie != null && accessorie.getAccessorieItems() != null) {
                        for (AccessorieItem accessorieItem : accessorie.getAccessorieItems()) {
                            if (accessorieItem.getName().equals(orderItem.getName())) {
                                accessorieItem.setQuantityInTransit(Math.max(accessorieItem.getQuantityInTransit() - orderItem.getQuantity(), 0));
                                accessorieItemRepository.save(accessorieItem);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }


    private void updateQuantityInTransit(Integer id) {
        Optional<Order> orderOptional = orderRepository.findById(id);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            for (OrderItem orderItem : order.getOrderItems()) {
                if (orderItem.getMaterial() != null) {
                    Material material = materialRepository.findById(orderItem.getMaterial().getId()).orElse(null);

                    if (material != null) {
                        material.setQuantityInTransit(material.getQuantityInTransit() + orderItem.getQuantity());

                        materialRepository.save(material);
                    }
                } else if (orderItem.getTool() != null) {

                    Tool tool = toolRepository.findById(orderItem.getTool().getId()).orElse(null);

                    if (tool != null) {
                        tool.setQuantityInTransit(tool.getQuantityInTransit() + orderItem.getQuantity());

                        toolRepository.save(tool);
                    }
                } else if (orderItem.getAccessorie() != null) {
                    // Handle accessories
                    Accessorie accessorie = accessorieRepository.findById(orderItem.getAccessorie().getId()).orElse(null);

                    if (accessorie != null && accessorie.getAccessorieItems() != null) {
                        for (AccessorieItem accessorieItem : accessorie.getAccessorieItems()) {
                            if (accessorieItem.getName().equals(orderItem.getName())) {
                                accessorieItem.setQuantityInTransit(accessorieItem.getQuantityInTransit() + orderItem.getQuantity());
                                accessorieItemRepository.save(accessorieItem);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    private void updateExternalQuantity(Integer id) {
        Optional<Order> orderOptional = orderRepository.findById(id);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            for (OrderItem orderItem : order.getOrderItems()) {
                // Calculate delta: only add the NEW quantity, not the total receivedQuantity
                // This prevents adding the same quantity multiple times during partial deliveries
                float previouslyAdded = orderItem.getPreviouslyAddedToInventory() != null
                    ? orderItem.getPreviouslyAddedToInventory()
                    : 0.0f;

                float quantityToAdd = orderItem.getReceivedQuantity() > 0
                    ? orderItem.getReceivedQuantity() - previouslyAdded
                    : orderItem.getQuantity();

                if (orderItem.getMaterial() != null) {
                    Material material = materialRepository.findById(orderItem.getMaterial().getId()).orElse(null);

                    if (material != null) {
                        // Determine material type from MaterialGroup
                        String materialType = material.getMaterialGroup() != null
                            ? material.getMaterialGroup().getType()
                            : null;

                        // Add to appropriate stock field based on material type
                        if ("Plate".equalsIgnoreCase(materialType)) {
                            // For plates: add to stockQuantity (pieces)
                            Integer currentStock = material.getStockQuantity() != null ? material.getStockQuantity() : 0;
                            material.setStockQuantity(currentStock + (int) quantityToAdd);
                        } else {
                            // For Rod/Tube: add to totalStockLength (mm)
                            Float currentLength = material.getTotalStockLength() != null ? material.getTotalStockLength() : 0.0f;
                            material.setTotalStockLength(currentLength + quantityToAdd);
                        }

                        // Update price if newPrice is set and actually different from current price
                        if (orderItem.getNewPrice() != null && orderItem.getNewPrice().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal currentPrice = material.getPrice();
                            // Only update if price actually changed
                            if (currentPrice == null || orderItem.getNewPrice().compareTo(currentPrice) != 0) {
                                material.setPrice(orderItem.getNewPrice());
                                orderItem.setPriceUpdated(true);
                            }
                        }

                        materialRepository.save(material);
                    }
                } else if (orderItem.getTool() != null) {

                    Tool tool = toolRepository.findById(orderItem.getTool().getId()).orElse(null);

                    if (tool != null) {
                        tool.setQuantity(tool.getQuantity() + quantityToAdd);

                        // Update price if newPrice is set and actually different from current price
                        if (orderItem.getNewPrice() != null && orderItem.getNewPrice().compareTo(BigDecimal.ZERO) > 0) {
                            BigDecimal currentPrice = tool.getPrice();
                            // Only update if price actually changed
                            if (currentPrice == null || orderItem.getNewPrice().compareTo(currentPrice) != 0) {
                                tool.setPrice(orderItem.getNewPrice());
                                orderItem.setPriceUpdated(true);
                            }
                        }

                        toolRepository.save(tool);
                    }
                } else if (orderItem.getAccessorie() != null) {
                    // Handle accessories
                    Accessorie accessorie = accessorieRepository.findById(orderItem.getAccessorie().getId()).orElse(null);

                    if (accessorie != null && accessorie.getAccessorieItems() != null) {
                        for (AccessorieItem accessorieItem : accessorie.getAccessorieItems()) {
                            if (accessorieItem.getName().equals(orderItem.getName())) {
                                accessorieItem.setQuantity(accessorieItem.getQuantity() + quantityToAdd);

                                // Update price if newPrice is set and actually different from current price
                                if (orderItem.getNewPrice() != null && orderItem.getNewPrice().compareTo(BigDecimal.ZERO) > 0) {
                                    BigDecimal currentPrice = accessorieItem.getPrice();
                                    // Only update if price actually changed
                                    if (currentPrice == null || orderItem.getNewPrice().compareTo(currentPrice) != 0) {
                                        accessorieItem.setPrice(orderItem.getNewPrice());
                                        orderItem.setPriceUpdated(true);
                                    }
                                }

                                accessorieItemRepository.save(accessorieItem);
                                break;
                            }
                        }
                    }
                }

                // Update tracking field to prevent duplicate inventory additions
                if (orderItem.getReceivedQuantity() > 0) {
                    orderItem.setPreviouslyAddedToInventory(orderItem.getReceivedQuantity());
                }
            }

            // Save order to persist priceUpdated and previouslyAddedToInventory flags
            orderRepository.save(order);
        }
    }

    /**
     * Partial delivery: Update receivedQuantity and newPrice for specific items
     * Call updateExternalQuantity after this to add only received items to warehouse
     * Supports multiple partial deliveries - status changes to "delivered" only when all items fully received
     */
    @Transactional
    public void partialDelivery(Integer orderId, List<OrderItem> updatedItems) {
        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            // Update receivedQuantity for the items in this delivery
            List<Map<String, Object>> deliveryChanges = new ArrayList<>();
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String currentDate = now.format(formatter);

            for (OrderItem updatedItem : updatedItems) {
                for (OrderItem orderItem : order.getOrderItems()) {
                    if (orderItem.getId().equals(updatedItem.getId())) {
                        // Accumulate received quantities (support multiple deliveries)
                        float previouslyReceived = orderItem.getReceivedQuantity();
                        float nowReceiving = updatedItem.getReceivedQuantity();
                        orderItem.setReceivedQuantity(previouslyReceived + nowReceiving);

                        // Log partial delivery to OrderChangeLog (if quantity was received)
                        if (nowReceiving > 0) {
                            // Determine unit based on material type
                            String unit = "szt";
                            if (orderItem.getMaterial() != null && orderItem.getMaterial().getMaterialGroup() != null) {
                                String materialType = orderItem.getMaterial().getMaterialGroup().getType();
                                if ("Rod".equals(materialType) || "Tube".equals(materialType)) {
                                    unit = "mm";
                                }
                            }

                            // Create change log entry for this partial delivery
                            // Store structured data in description as JSON for i18n on frontend
                            // Use Locale.US to ensure dots instead of commas in JSON numbers
                            String structuredDescription = String.format(
                                java.util.Locale.US,
                                "{\"quantityReceived\":%.2f,\"totalReceived\":%.2f,\"totalOrdered\":%.2f,\"unit\":\"%s\"}",
                                nowReceiving, previouslyReceived + nowReceiving, orderItem.getQuantity(), unit
                            );

                            OrderChangeLog changeLog = OrderChangeLog.builder()
                                    .orderId(orderId)
                                    .type("partial_delivery")
                                    .itemName(orderItem.getName())
                                    .field("receivedQuantity")
                                    .oldValue(String.format("%.2f", previouslyReceived))
                                    .newValue(String.format("%.2f", previouslyReceived + nowReceiving))
                                    .description(structuredDescription)
                                    .date(currentDate)
                                    .build();
                            orderChangeLogRepository.save(changeLog);
                        }

                        // Handle price changes with validation for >10% difference
                        if (updatedItem.getNewPrice() != null) {
                            // Get original price for comparison
                            BigDecimal originalPrice = orderItem.getNewPrice(); // Use previously updated price if exists
                            if (originalPrice == null) {
                                // Get price from related entity
                                if (orderItem.getMaterial() != null) {
                                    originalPrice = orderItem.getMaterial().getPrice();
                                } else if (orderItem.getTool() != null) {
                                    originalPrice = orderItem.getTool().getPrice();
                                }
                                // Note: Accessories don't have a direct price field, they use priceOverride from OrderItem
                            }

                            // Calculate percentage difference if original price exists
                            if (originalPrice != null && originalPrice.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal priceDifference = updatedItem.getNewPrice().subtract(originalPrice);
                                BigDecimal percentageDifference = priceDifference
                                        .divide(originalPrice, 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .abs();

                                // If difference > 10%, require reason
                                if (percentageDifference.compareTo(BigDecimal.valueOf(10)) > 0) {
                                    if (updatedItem.getPriceChangeReason() == null || updatedItem.getPriceChangeReason().trim().isEmpty()) {
                                        throw new IllegalArgumentException(
                                                "Price change reason is required when price difference exceeds 10%. " +
                                                        "Item: " + orderItem.getName() + ", Difference: " + percentageDifference.setScale(2, RoundingMode.HALF_UP) + "%"
                                        );
                                    }
                                }
                            }

                            // Update price and reason
                            orderItem.setNewPrice(updatedItem.getNewPrice());
                            orderItem.setPriceUpdated(true);
                            if (updatedItem.getPriceChangeReason() != null && !updatedItem.getPriceChangeReason().trim().isEmpty()) {
                                orderItem.setPriceChangeReason(updatedItem.getPriceChangeReason().trim());

                                // Create changelog entry for price change with reason
                                OrderChangeLog priceChangeLog = OrderChangeLog.builder()
                                        .orderId(orderId)
                                        .type("price_changed")
                                        .itemName(orderItem.getName())
                                        .field("newPrice")
                                        .oldValue(originalPrice != null ? originalPrice.toString() : "0")
                                        .newValue(updatedItem.getNewPrice().toString())
                                        .description(updatedItem.getPriceChangeReason().trim())
                                        .date(currentDate)
                                        .build();
                                orderChangeLogRepository.save(priceChangeLog);
                            }
                        }

                        // Handle pricePerKg update (for materials only)
                        if (updatedItem.getPricePerKg() != null && orderItem.getMaterial() != null) {
                            orderItem.setPricePerKg(updatedItem.getPricePerKg());
                        }
                        break;
                    }
                }
            }

            orderRepository.save(order);

            // Always process delivery (removed externalQuantityUpdated check)
            updateExternalQuantity(orderId);

            // Remove only received quantities from transit
            deletePartialQuantityInTransport(orderId);

            // Check if ALL items are fully received
            boolean allItemsFullyReceived = true;
            for (OrderItem item : order.getOrderItems()) {
                if (item.getReceivedQuantity() < item.getQuantity()) {
                    allItemsFullyReceived = false;
                    break;
                }
            }

            // Set status based on completion
            if (allItemsFullyReceived) {
                // Auto-transition to invoice_pending (Issue #4 fix)
                order.setStatus("invoice_pending");

                // Set actual delivery date for supplier performance tracking
                ZonedDateTime nowDelivery = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
                DateTimeFormatter deliveryFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                order.setActualDeliveryDate(nowDelivery.format(deliveryFormatter));

                // DO NOT set externalQuantityUpdated - allow invoice workflow to continue
                notificationService.createAndSendNotification(
                    "All materials for order '" + order.getName() + "' have been received. Status automatically set to awaiting invoice.",
                    NotificationDescription.OrderDelivered
                );
            } else {
                order.setStatus("partially_delivered");
                // Do NOT set externalQuantityUpdated = true (allow future deliveries)
                notificationService.createAndSendNotification(
                    "Partial delivery for order '" + order.getName() + "' has been processed. Some items are still pending.",
                    NotificationDescription.OrderDelivered
                );
            }

            orderRepository.save(order);
        }
    }

    /**
     * Update quality rating for a delivered order
     * Used for supplier performance tracking
     * @param orderId Order ID
     * @param rating Quality rating (1-5 stars)
     * @param notes Optional notes about delivery quality
     */
    @Transactional
    public void updateQualityRating(Integer orderId, Integer rating, String notes) {
        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            // Validate rating
            if (rating < 1 || rating > 5) {
                throw new IllegalArgumentException("Quality rating must be between 1 and 5");
            }

            // Only allow rating for orders that have been at least partially delivered
            List<String> allowedStatuses = Arrays.asList(
                "partially_delivered", "invoice_pending", "invoice_data_pending",
                "invoice_reconciliation", "invoice_received", "closed", "closed_short"
            );

            if (!allowedStatuses.contains(order.getStatus())) {
                throw new IllegalStateException(
                    "Quality rating can only be set for orders that have been at least partially delivered. " +
                    "Current status: " + order.getStatus()
                );
            }

            order.setQualityRating(rating);
            order.setQualityNotes(notes != null ? notes.trim() : null);

            // Create changelog entry
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            OrderChangeLog changeLog = OrderChangeLog.builder()
                    .orderId(orderId)
                    .type("quality_rating")
                    .field("qualityRating")
                    .oldValue(null)
                    .newValue(String.valueOf(rating))
                    .description(notes != null && !notes.isBlank() ? notes.trim() : "Delivery quality rated " + rating + "/5 stars")
                    .date(now.format(formatter))
                    .build();
            orderChangeLogRepository.save(changeLog);

            orderRepository.save(order);

            // Immediately recalculate supplier performance KPI after quality rating
            if (order.getSupplier() != null) {
                try {
                    supplierPerformanceService.recalculateSupplier(order.getSupplier().getId());
                } catch (Exception e) {
                    // Log error but don't fail the rating save
                    System.err.println("Failed to recalculate supplier performance: " + e.getMessage());
                }
            }

            notificationService.createAndSendNotification(
                "Quality rating (" + rating + "/5) added for order '" + order.getName() + "'.",
                NotificationDescription.OrderUpdated
            );
        } else {
            throw new IllegalArgumentException("Order not found with ID: " + orderId);
        }
    }

    /**
     * Mark order as awaiting invoice (goods_received → invoice_pending)
     * Called when all materials are in warehouse and we're waiting for invoice
     */
    @Transactional
    public void markInvoicePending(Integer orderId) {
        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            if ("goods_received".equals(order.getStatus())) {
                order.setStatus("invoice_pending");
                notificationService.createAndSendNotification(
                    "Order '" + order.getName() + "' is now awaiting invoice.",
                    NotificationDescription.OrderDelivered
                );
                orderRepository.save(order);
            }
        }
    }

    /**
     * Mark invoice as received (invoice_pending → invoice_received)
     * Called when invoice file has been uploaded and verified
     */
    @Transactional
    public void markInvoiceReceived(Integer orderId) {
        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            if ("invoice_pending".equals(order.getStatus()) && order.getInvoiceFileName() != null) {
                order.setStatus("invoice_received");
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                order.setInvoiceReceivedDate(now.format(formatter));
                notificationService.createAndSendNotification(
                    "Invoice received for order '" + order.getName() + "'.",
                    NotificationDescription.OrderDelivered
                );
                orderRepository.save(order);
            }
        }
    }

    /**
     * Close order (invoice_received → closed)
     * Final status - order is complete and archived
     */
    @Transactional
    public void closeOrder(Integer orderId) {
        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            if ("invoice_received".equals(order.getStatus())) {
                // Update prices from invoice before closing
                updatePricesFromInvoice(order);

                order.setStatus("closed");
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                String currentDate = now.format(formatter);
                order.setClosedDate(currentDate);
                order.setExternalQuantityUpdated(true); // Mark as fully processed

                // Create changelog entry for order closure
                OrderChangeLog changeLog = OrderChangeLog.builder()
                        .orderId(orderId)
                        .type("status_change")
                        .field("status")
                        .oldValue("invoice_received")
                        .newValue("closed")
                        .description("") // Empty description - will be translated on frontend
                        .date(currentDate)
                        .build();
                orderChangeLogRepository.save(changeLog);

                notificationService.createAndSendNotification(
                    "Order '" + order.getName() + "' has been closed.",
                    NotificationDescription.OrderDelivered
                );
                orderRepository.save(order);
            }
        }
    }

    /**
     * Updates material, tool, and accessory prices based on final invoice prices.
     * Called when order is closed after invoice reconciliation.
     * Uses InvoiceItem prices as the authoritative source after invoice approval.
     */
    private void updatePricesFromInvoice(Order order) {
        List<InvoiceItem> invoiceItems = order.getInvoiceItems();
        if (invoiceItems == null || invoiceItems.isEmpty()) {
            return;
        }

        for (InvoiceItem invoiceItem : invoiceItems) {
            Double invoiceUnitPrice = invoiceItem.getInvoiceUnitPrice();
            if (invoiceUnitPrice == null || invoiceUnitPrice <= 0) {
                continue; // Skip items without valid invoice price
            }

            // Get OrderItem directly from InvoiceItem (no name matching needed)
            OrderItem orderItem = invoiceItem.getOrderItem();
            if (orderItem == null) {
                continue;
            }

            // Update Material price
            if (orderItem.getMaterial() != null) {
                Material material = orderItem.getMaterial();
                material.setPrice(BigDecimal.valueOf(invoiceUnitPrice));

                // Update pricePerKg if provided in invoice
                Double invoicePricePerKg = invoiceItem.getInvoicePricePerKg();
                if (invoicePricePerKg != null && invoicePricePerKg > 0) {
                    material.setPricePerKg(BigDecimal.valueOf(invoicePricePerKg));
                }

                materialRepository.save(material);
            }

            // Update Tool price
            if (orderItem.getTool() != null) {
                Tool tool = orderItem.getTool();
                tool.setPrice(BigDecimal.valueOf(invoiceUnitPrice));
                toolRepository.save(tool);
            }

            // Note: OrderItem.accessorie links to Accessorie (group), not AccessorieItem directly
            // Price update for individual accessory items would require different data model
        }
    }

    /**
     * Mark order for incomplete closure (partially_delivered → invoice_pending)
     * Sets pendingClosedShort flag and awaits invoice before finalizing as closed_short
     * Does NOT set closed_short status - that happens after invoice is received
     */
    @Transactional
    public void closeOrderShort(Integer orderId, String reason) {
        // Validate reason
        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Reason is required for closing order as incomplete");
        }
        if (reason.trim().length() < 10) {
            throw new IllegalArgumentException("Reason must be at least 10 characters long");
        }

        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            // Only allow marking for incomplete closure from partially_delivered status
            if (!"partially_delivered".equals(order.getStatus())) {
                throw new IllegalStateException(
                    "Order can only be marked for incomplete closure from 'partially_delivered' status. " +
                    "Current status: " + order.getStatus()
                );
            }

            // Set invoice_pending status and pendingClosedShort flag
            order.setStatus("invoice_pending");
            order.setPendingClosedShort(true); // Flag: awaiting invoice before closing as incomplete
            order.setClosedShortReason(reason.trim()); // Store reason now
            // Note: closedShortDate will be set when finalizing (after invoice)

            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String currentDate = now.format(formatter);

            // Create changelog entry for marking incomplete closure
            OrderChangeLog changeLog = OrderChangeLog.builder()
                    .orderId(orderId)
                    .type("status_change")
                    .field("status")
                    .oldValue("partially_delivered")
                    .newValue("invoice_pending")
                    .description("Order marked for incomplete closure - awaiting invoice. Reason: " + reason.trim())
                    .date(currentDate)
                    .build();
            orderChangeLogRepository.save(changeLog);

            notificationService.createAndSendNotification(
                "Order '" + order.getName() + "' marked for incomplete closure. Awaiting invoice.",
                NotificationDescription.OrderMarkedForIncompleteClose
            );
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Order not found with ID: " + orderId);
        }
    }

    /**
     * Finalize order as incomplete (invoice_received → closed_short)
     * Final step after invoice has been uploaded for an incomplete order
     * Sets closed_short status and closedShortDate
     */
    @Transactional
    public void finalizeClosedShort(Integer orderId) {
        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            // Validation 1: Must be in invoice_received status
            if (!"invoice_received".equals(order.getStatus())) {
                throw new IllegalStateException(
                    "Order can only be finalized as incomplete from 'invoice_received' status. " +
                    "Current status: " + order.getStatus()
                );
            }

            // Validation 2: Must have pendingClosedShort flag set
            if (!Boolean.TRUE.equals(order.getPendingClosedShort())) {
                throw new IllegalStateException(
                    "Order is not marked for incomplete closure. Cannot finalize as closed_short."
                );
            }

            // Validation 3: Must have closure reason saved
            if (order.getClosedShortReason() == null || order.getClosedShortReason().trim().isEmpty()) {
                throw new IllegalStateException(
                    "Missing closure reason. Cannot finalize as closed_short."
                );
            }

            // Update prices from invoice before closing
            updatePricesFromInvoice(order);

            // Set closed_short status and date
            order.setStatus("closed_short");
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
            String currentDate = now.format(formatter);
            order.setClosedShortDate(currentDate);
            order.setPendingClosedShort(false); // Clear flag
            order.setExternalQuantityUpdated(true); // Mark as processed (even if incomplete)

            // Create changelog entry for finalization
            OrderChangeLog changeLog = OrderChangeLog.builder()
                    .orderId(orderId)
                    .type("status_change")
                    .field("status")
                    .oldValue("invoice_received")
                    .newValue("closed_short")
                    .description("Order finalized as incomplete. Reason: " + order.getClosedShortReason())
                    .date(currentDate)
                    .build();
            orderChangeLogRepository.save(changeLog);

            notificationService.createAndSendNotification(
                "Order '" + order.getName() + "' finalized as incomplete.",
                NotificationDescription.OrderClosedShort
            );
            orderRepository.save(order);
        } else {
            throw new IllegalArgumentException("Order not found with ID: " + orderId);
        }
    }

    /**
     * Remove only received quantities from quantityInTransit (for partial delivery)
     */
    private void deletePartialQuantityInTransport(Integer id) {
        Optional<Order> orderOptional = orderRepository.findById(id);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            for (OrderItem orderItem : order.getOrderItems()) {
                float quantityToRemove = orderItem.getReceivedQuantity() > 0 ? orderItem.getReceivedQuantity() : orderItem.getQuantity();

                if (orderItem.getMaterial() != null) {
                    Material material = materialRepository.findById(orderItem.getMaterial().getId()).orElse(null);
                    if (material != null) {
                        material.setQuantityInTransit(Math.max(material.getQuantityInTransit() - quantityToRemove, 0));
                        materialRepository.save(material);
                    }
                } else if (orderItem.getTool() != null) {
                    Tool tool = toolRepository.findById(orderItem.getTool().getId()).orElse(null);
                    if (tool != null) {
                        tool.setQuantityInTransit(Math.max(tool.getQuantityInTransit() - quantityToRemove, 0));
                        toolRepository.save(tool);
                    }
                } else if (orderItem.getAccessorie() != null) {
                    Accessorie accessorie = accessorieRepository.findById(orderItem.getAccessorie().getId()).orElse(null);
                    if (accessorie != null && accessorie.getAccessorieItems() != null) {
                        for (AccessorieItem accessorieItem : accessorie.getAccessorieItems()) {
                            if (accessorieItem.getName().equals(orderItem.getName())) {
                                accessorieItem.setQuantityInTransit(Math.max(accessorieItem.getQuantityInTransit() - quantityToRemove, 0));
                                accessorieItemRepository.save(accessorieItem);
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * Update price for a specific order item and corresponding material/tool/accessorie
     */
    @Transactional
    public void updateItemPrice(Integer orderId, Integer orderItemId, BigDecimal newPrice) {
        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            for (OrderItem orderItem : order.getOrderItems()) {
                if (orderItem.getId().equals(orderItemId)) {
                    // Check if price actually changed before setting priceUpdated flag
                    BigDecimal oldPrice = orderItem.getNewPrice();
                    if (oldPrice == null) {
                        // Get old price from associated entity
                        if (orderItem.getMaterial() != null) {
                            Material material = materialRepository.findById(orderItem.getMaterial().getId()).orElse(null);
                            if (material != null) {
                                oldPrice = material.getPrice();
                            }
                        } else if (orderItem.getTool() != null) {
                            Tool tool = toolRepository.findById(orderItem.getTool().getId()).orElse(null);
                            if (tool != null) {
                                oldPrice = tool.getPrice();
                            }
                        } else if (orderItem.getAccessorie() != null) {
                            Accessorie accessorie = accessorieRepository.findById(orderItem.getAccessorie().getId()).orElse(null);
                            if (accessorie != null && accessorie.getAccessorieItems() != null) {
                                for (AccessorieItem accessorieItem : accessorie.getAccessorieItems()) {
                                    if (accessorieItem.getName().equals(orderItem.getName())) {
                                        oldPrice = accessorieItem.getPrice();
                                        break;
                                    }
                                }
                            }
                        }
                    }

                    orderItem.setNewPrice(newPrice);
                    // Only set priceUpdated flag if price actually changed
                    if (oldPrice == null || newPrice.compareTo(oldPrice) != 0) {
                        orderItem.setPriceUpdated(true);
                    }

                    // Update price in database immediately
                    if (orderItem.getMaterial() != null) {
                        Material material = materialRepository.findById(orderItem.getMaterial().getId()).orElse(null);
                        if (material != null) {
                            material.setPrice(newPrice);
                            materialRepository.save(material);
                        }
                    } else if (orderItem.getTool() != null) {
                        Tool tool = toolRepository.findById(orderItem.getTool().getId()).orElse(null);
                        if (tool != null) {
                            tool.setPrice(newPrice);
                            toolRepository.save(tool);
                        }
                    } else if (orderItem.getAccessorie() != null) {
                        Accessorie accessorie = accessorieRepository.findById(orderItem.getAccessorie().getId()).orElse(null);
                        if (accessorie != null && accessorie.getAccessorieItems() != null) {
                            for (AccessorieItem accessorieItem : accessorie.getAccessorieItems()) {
                                if (accessorieItem.getName().equals(orderItem.getName())) {
                                    accessorieItem.setPrice(newPrice);
                                    accessorieItemRepository.save(accessorieItem);
                                    break;
                                }
                            }
                        }
                    }

                    break;
                }
            }

            orderRepository.save(order);

            notificationService.createAndSendNotification(
                "Price updated for item in order '" + order.getName() + "'.",
                NotificationDescription.OrderUpdated
            );
        }
    }

    // Invoice file upload directory
    private static final String INVOICE_UPLOAD_DIR = "uploads/invoices/";
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10MB
    private static final List<String> ALLOWED_EXTENSIONS = Arrays.asList("pdf", "jpg", "jpeg", "png", "doc", "docx", "xls", "xlsx");

    /**
     * Upload invoice file for an order
     */
    @Transactional
    public void uploadInvoice(Integer orderId, MultipartFile file) throws IOException {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException("File size exceeds maximum limit of 10MB");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isEmpty()) {
            throw new IllegalArgumentException("Invalid filename");
        }

        String fileExtension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(fileExtension)) {
            throw new IllegalArgumentException("File type not allowed. Allowed types: " + String.join(", ", ALLOWED_EXTENSIONS));
        }

        // Delete old invoice if exists
        if (order.getInvoiceFilePath() != null) {
            deleteInvoiceFile(order.getInvoiceFilePath());
        }

        // Create directory if not exists
        Path uploadPath = Paths.get(INVOICE_UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // Generate unique filename
        String uniqueFilename = "invoice_" + orderId + "_" + UUID.randomUUID() + "." + fileExtension;
        Path filePath = uploadPath.resolve(uniqueFilename);

        // Save file
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        // Update order
        order.setInvoiceFileName(originalFilename);
        order.setInvoiceFilePath(filePath.toString());

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        order.setInvoiceUploadDate(now.format(formatter));

        // Change status to invoice_data_pending (awaiting invoice items entry)
        order.setStatus("invoice_data_pending");

        orderRepository.save(order);

        // Create changelog entry for invoice upload
        OrderChangeLog changeLog = OrderChangeLog.builder()
                .orderId(orderId)
                .type("invoice_uploaded")
                .field("invoice")
                .oldValue(null)
                .newValue(originalFilename)
                .description("Invoice uploaded: " + originalFilename)
                .date(now.format(formatter))
                .build();
        orderChangeLogRepository.save(changeLog);

        // Note: Frontend handles success notification with i18n translation
        // notificationService.createAndSendNotification(
        //         "Invoice uploaded for order '" + order.getName() + "'.",
        //         NotificationDescription.OrderUpdated
        // );
    }

    /**
     * Get invoice file path for download
     */
    public Path getInvoiceFilePath(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        if (order.getInvoiceFilePath() == null) {
            throw new IllegalArgumentException("No invoice found for this order");
        }

        return Paths.get(order.getInvoiceFilePath());
    }

    /**
     * Get invoice filename
     */
    public String getInvoiceFileName(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        return order.getInvoiceFileName();
    }

    /**
     * Delete invoice for an order
     */
    @Transactional
    public void deleteInvoice(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found with ID: " + orderId));

        if (order.getInvoiceFilePath() != null) {
            deleteInvoiceFile(order.getInvoiceFilePath());
            order.setInvoiceFileName(null);
            order.setInvoiceFilePath(null);
            order.setInvoiceUploadDate(null);
            orderRepository.save(order);

            // Note: Frontend handles success notification with i18n translation
            // notificationService.createAndSendNotification(
            //         "Invoice deleted for order '" + order.getName() + "'.",
            //         NotificationDescription.OrderUpdated
            // );
        }
    }

    /**
     * Helper method to delete invoice file from filesystem
     */
    private void deleteInvoiceFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // Log error but don't throw exception
            System.err.println("Failed to delete invoice file: " + filePath);
        }
    }

    /**
     * Helper method to get file extension
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex + 1);
    }

    /**
     * Validates that the status transition is allowed (prevents backward transitions)
     * Order status flow: pending → on the way → partially_delivered → delivered →
     *                    invoice_pending → invoice_data_pending → invoice_reconciliation → invoice_received → closed
     *
     * @param currentStatus The current order status
     * @param newStatus The desired new status
     * @throws IllegalStateException if the transition is invalid
     */
    private void validateStatusTransition(String currentStatus, String newStatus) {
        // Define status order (higher number = later in workflow)
        java.util.Map<String, Integer> statusOrder = new java.util.HashMap<>();
        statusOrder.put("pending", 1);
        statusOrder.put("on the way", 2);
        statusOrder.put("partially_delivered", 3);
        statusOrder.put("delivered", 4);
        statusOrder.put("invoice_pending", 5);
        statusOrder.put("invoice_data_pending", 6);  // Three-way match: awaiting invoice items entry
        statusOrder.put("invoice_reconciliation", 7); // Three-way match: reconciliation in progress
        statusOrder.put("invoice_received", 8);
        statusOrder.put("closed", 9);
        statusOrder.put("closed_short", 10); // Final status for incomplete orders

        Integer currentOrder = statusOrder.get(currentStatus);
        Integer newOrder = statusOrder.get(newStatus);

        // If either status is not in the map, allow the transition (for backward compatibility)
        if (currentOrder == null || newOrder == null) {
            return;
        }

        // Special validation for closed_short:
        // Now can only be reached from invoice_received (via finalizeClosedShort)
        // Old direct transition from partially_delivered is no longer allowed
        if ("closed_short".equals(newStatus)) {
            // Allow only from invoice_received (workflow redesign)
            if (!"invoice_received".equals(currentStatus)) {
                throw new IllegalStateException(
                    "Invalid status transition: Orders can only be closed as incomplete (closed_short) " +
                    "from 'invoice_received' status after invoice upload. Current status: " + currentStatus
                );
            }
            return; // Allow this specific transition
        }

        // Prevent backward transitions from partially_delivered or later stages
        if (currentOrder >= 3 && newOrder < currentOrder) {
            throw new IllegalStateException(
                "Invalid status transition: Cannot change order status from '" + currentStatus +
                "' to '" + newStatus + "'. Backward transitions are not allowed after partial delivery."
            );
        }

        // Allow forward transitions or staying at same status
        // Allow transitions within early stages (pending <-> on the way is acceptable for manual edits)
        if (currentOrder < 3 && newOrder < 3) {
            // Allow transitions between pending and on the way
            return;
        }
    }

    // ==================== THREE-WAY MATCH METHODS ====================

    /**
     * Save invoice items entered by user
     * Changes order status from invoice_data_pending → invoice_reconciliation
     */
    @Transactional
    public void saveInvoiceItems(Integer orderId, InvoiceItemsDTO dto) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Validation: must be in invoice_data_pending status
        if (!"invoice_data_pending".equals(order.getStatus())) {
            throw new IllegalStateException("Order not ready for invoice items entry");
        }

        // Delete existing invoice items (if any)
        invoiceItemRepository.deleteByOrderId(orderId);

        // Create new invoice items
        List<InvoiceItem> invoiceItems = new ArrayList<>();
        for (InvoiceItemDTO itemDTO : dto.getItems()) {
            OrderItem orderItem = orderItemRepository.findById(itemDTO.getOrderItemId())
                    .orElseThrow(() -> new IllegalArgumentException("OrderItem not found"));

            InvoiceItem invoiceItem = InvoiceItem.builder()
                    .order(order)
                    .orderItem(orderItem)
                    .invoiceQuantity(itemDTO.getInvoiceQuantity())
                    .invoiceUnitPrice(itemDTO.getInvoiceUnitPrice())
                    .invoicePricePerKg(itemDTO.getInvoicePricePerKg())
                    .invoiceVatRate(itemDTO.getInvoiceVatRate())
                    .invoiceDiscount(itemDTO.getInvoiceDiscount())
                    .build();

            // Calculate amounts
            double netAmount = invoiceItem.getInvoiceQuantity() * invoiceItem.getInvoiceUnitPrice();
            double afterDiscount = netAmount * (1 - invoiceItem.getInvoiceDiscount() / 100);
            double vatAmount = afterDiscount * (invoiceItem.getInvoiceVatRate() / 100);
            double grossAmount = afterDiscount + vatAmount;

            invoiceItem.setInvoiceNetAmount(afterDiscount);
            invoiceItem.setInvoiceVatAmount(vatAmount);
            invoiceItem.setInvoiceGrossAmount(grossAmount);

            invoiceItems.add(invoiceItem);
        }

        invoiceItemRepository.saveAll(invoiceItems);

        // Update order status
        order.setInvoiceDataEntered(true);
        order.setStatus("invoice_reconciliation");
        orderRepository.save(order);

        // Create changelog
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        OrderChangeLog changeLog = OrderChangeLog.builder()
                .orderId(orderId)
                .type("invoice_items_entered")
                .field("invoice_items")
                .oldValue(null)
                .newValue(invoiceItems.size() + " items entered")
                .description("Invoice line items entered from " + order.getInvoiceFileName())
                .date(now.format(formatter))
                .build();
        orderChangeLogRepository.save(changeLog);

        notificationService.createAndSendNotification(
                "Invoice items entered for order '" + order.getName() + "'",
                NotificationDescription.InvoiceItemsEntered
        );
    }

    /**
     * Perform three-way match reconciliation
     * Compares PO, Delivery, and Invoice
     * Returns reconciliation results with discrepancies
     */
    @Transactional
    public InvoiceReconciliation performThreeWayMatch(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        // Validation
        if (!"invoice_reconciliation".equals(order.getStatus())) {
            throw new IllegalStateException("Order not ready for reconciliation");
        }

        // CHECK IF RECONCILIATION ALREADY EXISTS
        InvoiceReconciliation reconciliation = order.getInvoiceReconciliation();

        // FALLBACK: If relationship not synced, check database directly
        if (reconciliation == null) {
            System.out.println("DEBUG: Reconciliation is null from order relationship, checking database directly...");
            reconciliation = invoiceReconciliationRepository.findByOrderId(orderId).orElse(null);
            if (reconciliation != null) {
                System.out.println("DEBUG: Found existing reconciliation in database (id=" + reconciliation.getId() + "), relationship was not synced in JPA session");
                // Sync the relationship for this session
                order.setInvoiceReconciliation(reconciliation);
            }
        }

        // IDEMPOTENCY CHECK: If reconciliation was just created (within last 10 seconds), return it
        if (reconciliation != null && reconciliation.getReconciliationDate() != null) {
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
                ZonedDateTime reconciliationTime = ZonedDateTime.parse(
                    reconciliation.getReconciliationDate() + " +01:00",
                    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm XXX")
                ).withZoneSameInstant(ZoneId.of("Europe/Warsaw"));
                ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
                long secondsSinceReconciliation = java.time.Duration.between(reconciliationTime, now).getSeconds();

                if (secondsSinceReconciliation < 10) {
                    System.out.println("DEBUG: Idempotency check - reconciliation was just created " + secondsSinceReconciliation + " seconds ago. Returning existing reconciliation (id=" + reconciliation.getId() + ")");
                    return reconciliation;
                }
            } catch (Exception e) {
                // If date parsing fails, continue with normal flow
                System.out.println("DEBUG: Idempotency check failed to parse date, continuing normally");
            }
        }

        List<OrderItem> orderItems = order.getOrderItems();
        List<InvoiceItem> invoiceItems = invoiceItemRepository.findByOrderId(orderId);

        // Calculate totals
        ThreeWayTotals totals = calculateThreeWayTotals(orderItems, invoiceItems);

        // Detect discrepancies
        List<InvoiceDiscrepancy> discrepancies = detectDiscrepancies(orderItems, invoiceItems);

        // Convert discrepancies to JSON
        String discrepanciesJson = null;
        try {
            discrepanciesJson = objectMapper.writeValueAsString(discrepancies);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize discrepancies", e);
        }

        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        String currentDateTime = now.format(formatter);
        String reconciliationStatus = discrepancies.isEmpty() ? "matched" : "discrepancy_pending";

        // DEBUG: Log discrepancy detection
        System.out.println("=== THREE-WAY MATCH DEBUG ===");
        System.out.println("Order ID: " + orderId);
        System.out.println("Discrepancies found: " + discrepancies.size());
        System.out.println("Discrepancies isEmpty: " + discrepancies.isEmpty());
        System.out.println("Reconciliation status: " + reconciliationStatus);
        System.out.println("Existing reconciliation: " + (reconciliation != null ? "YES (id=" + reconciliation.getId() + ")" : "NO (will create new)"));

        // UPDATE EXISTING OR CREATE NEW
        if (reconciliation == null) {
            // Create new reconciliation record
            reconciliation = InvoiceReconciliation.builder()
                    .order(order)
                    .reconciliationDate(currentDateTime)
                    .reconciliationStatus(reconciliationStatus)
                    .poTotalNet(totals.getPoNet())
                    .deliveryTotalNet(totals.getDeliveryNet())
                    .invoiceTotalNet(totals.getInvoiceNet())
                    .poTotalVat(totals.getPoVat())
                    .deliveryTotalVat(totals.getDeliveryVat())
                    .invoiceTotalVat(totals.getInvoiceVat())
                    .poTotalGross(totals.getPoGross())
                    .deliveryTotalGross(totals.getDeliveryGross())
                    .invoiceTotalGross(totals.getInvoiceGross())
                    .discrepanciesJson(discrepanciesJson)
                    .build();

            // CRITICAL: Sync bidirectional relationship
            // Without this, order.getInvoiceReconciliation() returns null later!
            order.setInvoiceReconciliation(reconciliation);
            System.out.println("DEBUG: Created NEW reconciliation and synced bidirectional relationship");
            System.out.println("DEBUG: order.getInvoiceReconciliation() after sync: " + (order.getInvoiceReconciliation() != null ? "NOT NULL" : "NULL"));
        } else {
            // Update existing reconciliation record
            System.out.println("DEBUG: UPDATING existing reconciliation (id=" + reconciliation.getId() + ")");
            reconciliation.setReconciliationDate(currentDateTime);
            reconciliation.setReconciliationStatus(reconciliationStatus);
            reconciliation.setPoTotalNet(totals.getPoNet());
            reconciliation.setDeliveryTotalNet(totals.getDeliveryNet());
            reconciliation.setInvoiceTotalNet(totals.getInvoiceNet());
            reconciliation.setPoTotalVat(totals.getPoVat());
            reconciliation.setDeliveryTotalVat(totals.getDeliveryVat());
            reconciliation.setInvoiceTotalVat(totals.getInvoiceVat());
            reconciliation.setPoTotalGross(totals.getPoGross());
            reconciliation.setDeliveryTotalGross(totals.getDeliveryGross());
            reconciliation.setInvoiceTotalGross(totals.getInvoiceGross());
            reconciliation.setDiscrepanciesJson(discrepanciesJson);
            // Clear approval data if re-reconciling
            reconciliation.setDiscrepancyJustification(null);
            reconciliation.setApprovedBy(null);
            reconciliation.setApprovedDate(null);
        }

        // Save (will UPDATE if id exists, INSERT if new)
        System.out.println("DEBUG: Saving reconciliation (id=" + (reconciliation.getId() != null ? reconciliation.getId() : "null-will-generate") + ")");
        invoiceReconciliationRepository.save(reconciliation);
        System.out.println("DEBUG: Reconciliation saved successfully (id=" + reconciliation.getId() + ")");

        // If no discrepancies, auto-approve
        System.out.println("DEBUG: Checking if should auto-approve: discrepancies.isEmpty() = " + discrepancies.isEmpty());
        if (discrepancies.isEmpty()) {
            System.out.println("DEBUG: AUTO-APPROVE TRIGGERED - No discrepancies found");
            System.out.println("DEBUG: Changing order status from '" + order.getStatus() + "' to 'invoice_received'");
            order.setStatus("invoice_received");
            order.setInvoiceReconciliationCompleted(true);
            orderRepository.save(order);
            System.out.println("DEBUG: Order status updated and saved");

            OrderChangeLog changeLog = OrderChangeLog.builder()
                    .orderId(orderId)
                    .type("invoice_reconciliation")
                    .field("reconciliation")
                    .oldValue(null)
                    .newValue("matched")
                    .description("Three-way match completed. No discrepancies found.")
                    .date(now.format(formatter))
                    .build();
            orderChangeLogRepository.save(changeLog);
        }

        return reconciliation;
    }

    /**
     * Calculate totals for PO, Delivery, and Invoice
     */
    private ThreeWayTotals calculateThreeWayTotals(List<OrderItem> orderItems, List<InvoiceItem> invoiceItems) {
        double poNet = 0, poVat = 0, poGross = 0;
        double deliveryNet = 0, deliveryVat = 0, deliveryGross = 0;
        double invoiceNet = 0, invoiceVat = 0, invoiceGross = 0;

        // Calculate PO totals
        for (OrderItem item : orderItems) {
            double price = getMaterialOrToolPrice(item);
            double quantity = item.getQuantity();
            double vatRate = item.getVatRate() != null ? item.getVatRate().doubleValue() : 23.0;

            double net = quantity * price;
            double vat = net * (vatRate / 100);
            double gross = net + vat;

            poNet += net;
            poVat += vat;
            poGross += gross;
        }

        // Calculate Delivery totals (using receivedQuantity and newPrice if available)
        for (OrderItem item : orderItems) {
            double deliveredQuantity = item.getReceivedQuantity() > 0 ? item.getReceivedQuantity() : item.getQuantity();
            double deliveredPrice = item.getNewPrice() != null ? item.getNewPrice().doubleValue() : getMaterialOrToolPrice(item);
            double vatRate = item.getVatRate() != null ? item.getVatRate().doubleValue() : 23.0;

            double net = deliveredQuantity * deliveredPrice;
            double vat = net * (vatRate / 100);
            double gross = net + vat;

            deliveryNet += net;
            deliveryVat += vat;
            deliveryGross += gross;
        }

        // Calculate Invoice totals
        for (InvoiceItem item : invoiceItems) {
            invoiceNet += item.getInvoiceNetAmount();
            invoiceVat += item.getInvoiceVatAmount();
            invoiceGross += item.getInvoiceGrossAmount();
        }

        return ThreeWayTotals.builder()
                .poNet(roundToTwoDecimals(poNet))
                .poVat(roundToTwoDecimals(poVat))
                .poGross(roundToTwoDecimals(poGross))
                .deliveryNet(roundToTwoDecimals(deliveryNet))
                .deliveryVat(roundToTwoDecimals(deliveryVat))
                .deliveryGross(roundToTwoDecimals(deliveryGross))
                .invoiceNet(roundToTwoDecimals(invoiceNet))
                .invoiceVat(roundToTwoDecimals(invoiceVat))
                .invoiceGross(roundToTwoDecimals(invoiceGross))
                .build();
    }

    /**
     * Detect discrepancies between order and invoice
     * NOTE: We compare quantities (Invoice vs Delivery) but prices (Invoice vs PO)
     * WZ (delivery note) only contains quantity, not prices
     */
    private List<InvoiceDiscrepancy> detectDiscrepancies(List<OrderItem> orderItems, List<InvoiceItem> invoiceItems) {
        List<InvoiceDiscrepancy> discrepancies = new ArrayList<>();

        for (InvoiceItem invoiceItem : invoiceItems) {
            OrderItem orderItem = invoiceItem.getOrderItem();

            // Compare quantities (Invoice vs Delivery)
            double deliveryQuantity = orderItem.getReceivedQuantity() > 0
                    ? orderItem.getReceivedQuantity()
                    : orderItem.getQuantity();
            double invoiceQuantity = invoiceItem.getInvoiceQuantity();

            // PO values
            double poQuantity = orderItem.getQuantity();
            double poPrice = getMaterialOrToolPrice(orderItem);

            // Invoice price
            double invoicePrice = invoiceItem.getInvoiceUnitPrice();

            // Delivery price (for display only - WZ doesn't have prices, so we use PO price)
            double deliveryPrice = poPrice;

            // Detect discrepancies:
            // - Quantity: compare Invoice vs Delivery (tolerance: 0.01)
            // - Price: compare Invoice vs PO (tolerance: 0.01) - WZ doesn't have prices
            boolean quantityMismatch = Math.abs(invoiceQuantity - deliveryQuantity) > 0.01;
            boolean priceMismatch = Math.abs(invoicePrice - poPrice) > 0.01;

            if (quantityMismatch || priceMismatch) {
                InvoiceDiscrepancy discrepancy = InvoiceDiscrepancy.builder()
                        .orderItemId(orderItem.getId())
                        .itemName(orderItem.getName())
                        .poQuantity(poQuantity)
                        .deliveryQuantity(deliveryQuantity)
                        .invoiceQuantity(invoiceQuantity)
                        .poUnitPrice(poPrice)
                        .deliveryUnitPrice(deliveryPrice)
                        .invoiceUnitPrice(invoicePrice)
                        .quantityDifference(invoiceQuantity - deliveryQuantity)
                        .priceDifference(invoicePrice - poPrice)
                        .amountDifference((invoiceQuantity * invoicePrice) - (deliveryQuantity * poPrice))
                        .discrepancyType(
                                quantityMismatch && priceMismatch ? "both" :
                                        quantityMismatch ? "quantity" : "price"
                        )
                        .severity(calculateSeverity(invoiceItem, deliveryQuantity, poPrice))
                        .build();

                discrepancies.add(discrepancy);
            }
        }

        return discrepancies;
    }

    /**
     * Calculate severity of discrepancy based on percentage difference
     * Compares invoice total vs expected total (delivery qty * PO price)
     */
    private String calculateSeverity(InvoiceItem invoiceItem, double deliveryQuantity, double poPrice) {
        double invoiceTotal = invoiceItem.getInvoiceQuantity() * invoiceItem.getInvoiceUnitPrice();
        double expectedTotal = deliveryQuantity * poPrice;

        if (expectedTotal == 0) return "minor";

        double percentageDiff = Math.abs((invoiceTotal - expectedTotal) / expectedTotal) * 100;

        if (percentageDiff < 5) return "minor";
        if (percentageDiff < 15) return "moderate";
        return "major";
    }

    /**
     * Get material or tool price from OrderItem
     */
    private double getMaterialOrToolPrice(OrderItem item) {
        if (item.getMaterial() != null && item.getMaterial().getPrice() != null) {
            return item.getMaterial().getPrice().doubleValue();
        } else if (item.getTool() != null && item.getTool().getPrice() != null) {
            return item.getTool().getPrice().doubleValue();
        } else if (item.getAccessorie() != null) {
            // For accessories, find the matching AccessorieItem by name
            Accessorie accessorie = item.getAccessorie();
            if (accessorie.getAccessorieItems() != null) {
                for (AccessorieItem accessorieItem : accessorie.getAccessorieItems()) {
                    if (accessorieItem.getName().equals(item.getName())) {
                        if (accessorieItem.getPrice() != null) {
                            return accessorieItem.getPrice().doubleValue();
                        }
                        break;
                    }
                }
            }
        }
        return 0.0;
    }

    /**
     * Get existing reconciliation for an order
     * Used when catching race conditions
     */
    public InvoiceReconciliation getExistingReconciliation(Integer orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        InvoiceReconciliation reconciliation = order.getInvoiceReconciliation();

        // Fallback to database query if relationship not loaded
        if (reconciliation == null) {
            reconciliation = invoiceReconciliationRepository.findByOrderId(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Reconciliation not found"));
        }

        return reconciliation;
    }

    /**
     * Approve invoice discrepancies with justification
     * Changes order status from invoice_reconciliation → invoice_received
     */
    @Transactional
    public void approveInvoiceDiscrepancies(Integer orderId, String justification) {
        System.out.println("=== APPROVE DISCREPANCIES DEBUG ===");
        System.out.println("Order ID: " + orderId);
        System.out.println("Justification provided: " + (justification != null ? "YES (length=" + justification.length() + ")" : "NO"));

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found"));

        System.out.println("Order found - Status: " + order.getStatus());
        System.out.println("Order.getInvoiceReconciliation(): " + (order.getInvoiceReconciliation() != null ? "NOT NULL (id=" + order.getInvoiceReconciliation().getId() + ")" : "NULL - THIS IS THE BUG!"));

        // FIX 10: Idempotent check - if already approved, just return success
        // This handles race conditions where auto-approve already worked
        if ("invoice_received".equals(order.getStatus())) {
            System.out.println("DEBUG: Order already in invoice_received status. Returning success (idempotent).");
            return;  // Already done, nothing to do
        }

        InvoiceReconciliation reconciliation = order.getInvoiceReconciliation();
        if (reconciliation == null) {
            System.out.println("ERROR: Reconciliation is NULL - attempting INSERT will cause unique constraint violation");
            throw new IllegalArgumentException("Reconciliation not found");
        }

        System.out.println("Reconciliation status: " + reconciliation.getReconciliationStatus());
        System.out.println("Reconciliation discrepanciesJson: " + reconciliation.getDiscrepanciesJson());

        // Validation
        if (!"invoice_reconciliation".equals(order.getStatus())) {
            throw new IllegalStateException("Order not in reconciliation status");
        }

        // Check if reconciliation has discrepancies
        boolean hasDiscrepancies = reconciliation.getDiscrepanciesJson() != null &&
                                   !reconciliation.getDiscrepanciesJson().equals("[]") &&
                                   !reconciliation.getDiscrepanciesJson().equals("null");

        System.out.println("hasDiscrepancies: " + hasDiscrepancies);

        // Only require justification if there are discrepancies
        if (hasDiscrepancies && (justification == null || justification.trim().length() < 10)) {
            throw new IllegalArgumentException("Justification required when discrepancies exist (min 10 characters)");
        }

        // Get current user
        String currentUser = org.springframework.security.core.context.SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        // Update reconciliation
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Warsaw"));
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        reconciliation.setReconciliationStatus("discrepancy_approved");
        reconciliation.setDiscrepancyJustification(justification);
        reconciliation.setApprovedBy(currentUser);
        reconciliation.setApprovedDate(now.format(formatter));
        invoiceReconciliationRepository.save(reconciliation);

        // Update order status
        order.setStatus("invoice_received");
        order.setInvoiceReconciliationCompleted(true);
        orderRepository.save(order);

        // Create changelog
        OrderChangeLog changeLog = OrderChangeLog.builder()
                .orderId(orderId)
                .type("invoice_reconciliation")
                .field("reconciliation")
                .oldValue("discrepancy_pending")
                .newValue("discrepancy_approved")
                .description("Discrepancies approved. Justification: " + justification)
                .date(now.format(formatter))
                .build();
        orderChangeLogRepository.save(changeLog);

        notificationService.createAndSendNotification(
                "Invoice discrepancies approved for order '" + order.getName() + "'",
                NotificationDescription.InvoiceDiscrepanciesApproved
        );
    }

    /**
     * Helper method to round to 2 decimal places
     */
    private double roundToTwoDecimals(double value) {
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
