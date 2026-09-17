package com.jade.demo.controller;

import com.jade.common.api.R;
import com.jade.demo.service.DataExportService;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * 数据导出接口（演示看门狗）
 */
@Path("/api/v1/export")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "数据导出")
@PermitAll
public class ExportController {

    @Inject
    DataExportService service;

    @POST
    @Path("/long")
    @Operation(summary = "长时间导出（带看门狗，业务跑 60s 锁也不会过期）")
    public R<String> longExport(@QueryParam("task") String task,
                                 @QueryParam("duration") @DefaultValue("60") int duration) {
        return service.export(task, duration);
    }

    @POST
    @Path("/quick")
    @Operation(summary = "快速导出（无看门狗，普通锁）")
    public R<String> quickExport(@QueryParam("task") String task) {
        return service.quickExport(task);
    }
}
