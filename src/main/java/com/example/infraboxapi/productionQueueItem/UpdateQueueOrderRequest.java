package com.example.infraboxapi.productionQueueItem;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQueueOrderRequest {
    private String queueType;
    private List<OrderItem> items;
}
