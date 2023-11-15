package com.example.infraboxapi.orderItem;


import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order_item/")
@AllArgsConstructor
@CrossOrigin(origins = "http://localhost:3000")
public class OrderItemController {
}
