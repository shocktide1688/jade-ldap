package com.jade.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class CreateOrderRequest {
    @NotBlank(message = "商品不能为空")
    private String productName;

    @NotNull(message = "金额不能为空")
    private BigDecimal amount;
}
