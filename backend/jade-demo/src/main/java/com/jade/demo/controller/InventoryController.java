package com.jade.demo.controller;

import com.jade.common.api.R;
import com.jade.demo.service.InventoryService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 库存接口（演示分布式锁）
 */
@Path("/api/v1/inventory")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "库存")
@PermitAll
public class InventoryController {

    @Inject
    InventoryService service;

    @POST
    @Path("/init")
    @Operation(summary = "初始化库存（管理用）")
    public R<String> init(@QueryParam("sku") String sku, @QueryParam("count") int count) {
        return service.init(sku, count);
    }

    @GET
    @Path("/{sku}")
    @Operation(summary = "查询库存")
    public R<Integer> get(@PathParam("sku") String sku) {
        return service.get(sku);
    }

    @POST
    @Path("/deduct")
    @Operation(summary = "扣减库存（带分布式锁）")
    public R<String> deduct(@QueryParam("sku") String sku, @QueryParam("quantity") int quantity) {
        return service.deduct(sku, quantity);
    }
}
