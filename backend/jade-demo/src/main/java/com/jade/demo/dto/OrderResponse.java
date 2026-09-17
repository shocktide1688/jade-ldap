package com.jade.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class OrderResponse {
    private String orderNo;
    private String productName;
    private String amount;
    private Long createdAt;
}
