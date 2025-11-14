package com.example.infraboxapi.order;

import com.example.infraboxapi.accessorie.Accessorie;
import com.example.infraboxapi.accessorie.AccessorieReposotory;
import com.example.infraboxapi.accessorieItem.AccessorieItem;
import com.example.infraboxapi.accessorieItem.AccessorieItemRepository;
import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.material.MaterialRepository;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.orderItem.OrderItem;
import com.example.infraboxapi.orderItem.OrderItemDTO;
import com.example.infraboxapi.orderItem.OrderItemRepository;
import com.example.infraboxapi.supplier.Supplier;
import com.example.infraboxapi.supplier.SupplierRepository;
import com.example.infraboxapi.tool.Tool;
import com.example.infraboxapi.tool.ToolRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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

    public List<Order> getAllOrders() {
        // Use JOIN FETCH to avoid N+1 query problem
        return orderRepository.findAllWithSupplierAndItems();
    }

    @Transactional
    public void addOrder(OrderDTO orderDTO) {
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
                .totalNet(orderDTO.getTotalNet())
                .totalVat(orderDTO.getTotalVat())
                .totalGross(orderDTO.getTotalGross())
                .supplier(supplier)
                .expectedDeliveryDate(orderDTO.getExpectedDeliveryDate())
                .orderItems(orderItems)
                .externalQuantityUpdated(false)
                .transitQuantitySet(false)
                .build();

        orderRepository.save(order);

        notificationService.createAndSendNotification("Order '" + order.getName() + "' has been added.", NotificationDescription.OrderAdded);
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

        // Handle priceOverride if provided
        if (orderItemDTO.getPriceOverride() != null && orderItemDTO.getPriceOverride() > 0) {
            orderItemBuilder.newPrice(BigDecimal.valueOf(orderItemDTO.getPriceOverride()));
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
                // Delete existing order items
                orderItemRepository.deleteAll(existingOrder.getOrderItems());

                // Create new order items from DTO
                List<OrderItem> newOrderItems = new ArrayList<>();
                for (OrderItemDTO orderItemDTO : orderDTO.getOrderItems()) {
                    OrderItem orderItem = createOrderItemFromDTO(orderItemDTO);
                    newOrderItems.add(orderItem);
                }
                existingOrder.setOrderItems(newOrderItems);
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
                // Use receivedQuantity if set, otherwise use full quantity
                float quantityToAdd = orderItem.getReceivedQuantity() > 0 ? orderItem.getReceivedQuantity() : orderItem.getQuantity();

                if (orderItem.getMaterial() != null) {
                    Material material = materialRepository.findById(orderItem.getMaterial().getId()).orElse(null);

                    if (material != null) {
                        material.setQuantity(material.getQuantity() + quantityToAdd);

                        // Update price if newPrice is set
                        if (orderItem.getNewPrice() != null && orderItem.getNewPrice().compareTo(BigDecimal.ZERO) > 0) {
                            material.setPrice(orderItem.getNewPrice());
                            orderItem.setPriceUpdated(true);
                        }

                        materialRepository.save(material);
                    }
                } else if (orderItem.getTool() != null) {

                    Tool tool = toolRepository.findById(orderItem.getTool().getId()).orElse(null);

                    if (tool != null) {
                        tool.setQuantity(tool.getQuantity() + quantityToAdd);

                        // Update price if newPrice is set
                        if (orderItem.getNewPrice() != null && orderItem.getNewPrice().compareTo(BigDecimal.ZERO) > 0) {
                            tool.setPrice(orderItem.getNewPrice());
                            orderItem.setPriceUpdated(true);
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

                                // Update price if newPrice is set
                                if (orderItem.getNewPrice() != null && orderItem.getNewPrice().compareTo(BigDecimal.ZERO) > 0) {
                                    accessorieItem.setPrice(orderItem.getNewPrice());
                                    orderItem.setPriceUpdated(true);
                                }

                                accessorieItemRepository.save(accessorieItem);
                                break;
                            }
                        }
                    }
                }
            }

            // Save order to persist priceUpdated flags
            orderRepository.save(order);
        }
    }

    /**
     * Partial delivery: Update receivedQuantity and newPrice for specific items
     * Call updateExternalQuantity after this to add only received items to warehouse
     */
    @Transactional
    public void partialDelivery(Integer orderId, List<OrderItem> updatedItems) {
        Optional<Order> orderOptional = orderRepository.findById(orderId);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            for (OrderItem updatedItem : updatedItems) {
                for (OrderItem orderItem : order.getOrderItems()) {
                    if (orderItem.getId().equals(updatedItem.getId())) {
                        orderItem.setReceivedQuantity(updatedItem.getReceivedQuantity());
                        if (updatedItem.getNewPrice() != null) {
                            orderItem.setNewPrice(updatedItem.getNewPrice());
                        }
                        break;
                    }
                }
            }

            orderRepository.save(order);

            // Now process delivery
            if (!order.isExternalQuantityUpdated()) {
                updateExternalQuantity(orderId);
            }

            order.setStatus("delivered");
            order.setExternalQuantityUpdated(true);

            // Remove only received quantities from transit
            deletePartialQuantityInTransport(orderId);

            orderRepository.save(order);

            notificationService.createAndSendNotification(
                "Partial delivery for order '" + order.getName() + "' has been processed.",
                NotificationDescription.OrderDelivered
            );
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
                    orderItem.setNewPrice(newPrice);
                    orderItem.setPriceUpdated(true);

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
}
