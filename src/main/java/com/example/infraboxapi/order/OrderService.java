package com.example.infraboxapi.order;


import com.example.infraboxapi.orderItem.OrderItem;
import com.example.infraboxapi.orderItem.OrderItemDTO;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@AllArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    public List<Order> getAllOrders() {

        return orderRepository.findAll();
    }

    public void addOrder(OrderDTO orderDTO) {


    }
}
