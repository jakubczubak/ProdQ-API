package com.example.infraboxapi.order;
import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.material.MaterialRepository;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.orderItem.OrderItem;
import com.example.infraboxapi.orderItem.OrderItemDTO;
import com.example.infraboxapi.tool.Tool;
import com.example.infraboxapi.tool.ToolRepository;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@AllArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final MaterialRepository materialRepository;
    private final ToolRepository toolRepository;
    private final NotificationService notificationService;
    public List<Order> getAllOrders() {

        return orderRepository.findAll();
    }

    @Transactional
    public void addOrder(OrderDTO orderDTO) {
        List<OrderItem> orderItems = new ArrayList<>();

        for (OrderItemDTO orderItemDTO : orderDTO.getOrderItems()) {
            OrderItem orderItem = createOrderItemFromDTO(orderItemDTO);
            orderItems.add(orderItem);
        }

        Order order = Order.builder()
                .name(orderDTO.getName())
                .date(orderDTO.getDate())
                .status(orderDTO.getStatus())
                .supplierEmail(orderDTO.getSupplierEmail())
                .supplierMessage(orderDTO.getSupplierMessage())
                .totalPrice(orderDTO.getTotalPrice())
                .orderItems(orderItems)
                .build();

        orderRepository.save(order);

        notificationService.createAndSendNotification("Order " + order.getName() + " has been added.", NotificationDescription.OrderAdded);
    }

    private OrderItem createOrderItemFromDTO(OrderItemDTO orderItemDTO) {
        String itemType = orderItemDTO.getItemType();
        OrderItem.OrderItemBuilder orderItemBuilder = OrderItem.builder()
                .name(orderItemDTO.getName())
                .quantity(orderItemDTO.getQuantity());

        if ("tool".equals(itemType)) {
            orderItemBuilder.tool(toolRepository.findById(orderItemDTO.getItemID()).orElse(null));
        } else {
            orderItemBuilder.material(materialRepository.findById(orderItemDTO.getItemID()).orElse(null));
        }

        return orderItemBuilder.build();
    }

    @Transactional
    public void deleteOrder(Integer id) {
        Optional<Order> orderOptional = orderRepository.findById(id);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();
            if ("on the way".equals(order.getStatus())) {
                updateQuantityInTransport(id);
            }
            orderRepository.deleteById(id);
            notificationService.createAndSendNotification("Order  " + order.getName() + " has been deleted.", NotificationDescription.OrderDeleted);
        }



    }

    public void updateQuantityInTransport(Integer id) {
        Optional<Order> orderOptional = orderRepository.findById(id);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            for (OrderItem orderItem : order.getOrderItems()) {
                if (orderItem.getMaterial() != null) {
                    Material material = materialRepository.findById(orderItem.getMaterial().getId()).orElse(null);

                    if (material != null) {
                        if(material.getQuantityInTransit() - orderItem.getQuantity() < 0){
                            material.setQuantityInTransit(0);
                        }else{
                            material.setQuantityInTransit(material.getQuantityInTransit() - orderItem.getQuantity());
                        }

                        materialRepository.save(material);
                    }
                } else if (orderItem.getTool() != null) {

                    Tool tool = toolRepository.findById(orderItem.getTool().getId()).orElse(null);

                    if (tool != null) {
                        if(tool.getQuantityInTransit() - orderItem.getQuantity() < 0){
                            tool.setQuantityInTransit(0);
                        }else{
                            tool.setQuantityInTransit(tool.getQuantityInTransit() - orderItem.getQuantity());
                        }

                        toolRepository.save(tool);
                    }
                }
            }
        }
    }

}
