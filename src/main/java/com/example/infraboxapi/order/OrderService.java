package com.example.infraboxapi.order;
import com.example.infraboxapi.material.Material;
import com.example.infraboxapi.material.MaterialRepository;
import com.example.infraboxapi.notification.NotificationDescription;
import com.example.infraboxapi.notification.NotificationService;
import com.example.infraboxapi.orderItem.OrderItem;
import com.example.infraboxapi.orderItem.OrderItemDTO;
import com.example.infraboxapi.orderItem.OrderItemRepository;
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
    private final OrderItemRepository orderItemRepository;
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


            if(orderItemDTO.getItemType().equals("tool")){
                OrderItem orderItem = OrderItem.builder()
                        .name(orderItemDTO.getName())
                        .quantity(orderItemDTO.getQuantity())
                        .material(null)
                        .tool(toolRepository.findById(orderItemDTO.getItemID()).orElse(null))
                        .build();

                orderItems.add(orderItem);
            }else{
                OrderItem orderItem = OrderItem.builder()
                        .name(orderItemDTO.getName())
                        .quantity(orderItemDTO.getQuantity())
                        .material(materialRepository.findById(orderItemDTO.getItemID()).orElse(null))
                        .tool(null)
                        .build();

                orderItems.add(orderItem);
            }




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

        notificationService.createAndSendNotification("Order  " + order.getName() + " has been added.", NotificationDescription.OrderAdded);


    }

    public void deleteOrder(Integer id) {
        setQuantityInTransportToZero(id);
        orderRepository.deleteById(id);
    }

    public void setQuantityInTransportToZero(Integer id) {
        Optional<Order> orderOptional = orderRepository.findById(id);

        if (orderOptional.isPresent()) {
            Order order = orderOptional.get();

            for (OrderItem orderItem : order.getOrderItems()) {
                if (orderItem.getMaterial() != null) {
                    Material material = materialRepository.findById(orderItem.getMaterial().getId()).orElse(null);

                    if (material != null) {
                        material.setQuantityInTransit(0);
                        materialRepository.save(material);
                    }
                } else if (orderItem.getTool() != null) {

                    Tool tool = toolRepository.findById(orderItem.getTool().getId()).orElse(null);

                    if (tool != null) {
                        tool.setQuantityInTransit(0);
                        toolRepository.save(tool);
                    }
                }
            }
        }
    }

}
