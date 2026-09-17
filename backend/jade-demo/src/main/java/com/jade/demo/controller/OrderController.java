package com.jade.demo.controller;

import com.jade.common.api.R;
import com.jade.demo.dto.CreateOrderRequest;
import com.jade.demo.dto.OrderResponse;
import com.jade.admin.metrics.BusinessMetrics;
import com.jade.redis.annotation.Idempotent;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * 订单接口（演示幂等性）
 */
@Path("/api/v1/orders")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "订单管理")
@PermitAll
public class OrderController {

    @Inject
    BusinessMetrics metrics;

    @POST
    @Idempotent(key = "order:create", expire = 300)
    @Operation(summary = "创建订单", description = "需带 header: X-Idempotency-Key: <uuid>，5 分钟内同 key 不会重复创建")
    public R<OrderResponse> create(@Valid CreateOrderRequest req) {
        Timer.Sample sample = Timer.start();
        try {
            // 模拟业务逻辑
            String orderNo = "OD" + System.currentTimeMillis();
            OrderResponse resp = new OrderResponse(
                    orderNo,
                    req.getProductName(),
                    req.getAmount().toPlainString(),
                    System.currentTimeMillis()
            );
            // 业务事件埋点（Grafana 可以看）
            metrics.recordOrderCreated();
            return R.ok(resp);
        } finally {
            sample.stop(metrics.orderTimer());
        }
    }
}
