package com.jade.admin.controller;

import com.jade.admin.aspect.Log;
import com.jade.admin.entity.SysDictData;
import com.jade.admin.entity.SysDictType;
import com.jade.admin.repository.SysDictDataRepository;
import com.jade.admin.repository.SysDictTypeRepository;
import com.jade.common.api.R;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/dict")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "字典管理")
public class DictController {

    @Inject
    SysDictTypeRepository typeRepository;

    @Inject
    SysDictDataRepository dataRepository;

    // ---- 类型 ----

    @GET
    @Path("/types")
    @Operation(summary = "所有字典类型")
    public R<List<SysDictType>> types() {
        return R.ok(typeRepository.list("deleted = false"));
    }

    @POST
    @Path("/types")
    @Transactional
    @Log(title = "字典管理", businessType = 1)
    @Operation(summary = "新建字典类型")
    public R<SysDictType> createType(SysDictType type) {
        type.id = null;
        if (type.status == null) type.status = 1;
        typeRepository.persist(type);
        return R.ok(type);
    }

    @DELETE
    @Path("/types/{id}")
    @Transactional
    @Log(title = "字典管理", businessType = 3)
    @Operation(summary = "删除字典类型")
    public R<Void> deleteType(@PathParam("id") Long id) {
        SysDictType t = typeRepository.findById(id);
        if (t == null) return R.fail(404, "字典类型不存在");
        t.deleted = true;
        typeRepository.persist(t);
        return R.ok();
    }

    // ---- 数据 ----

    @GET
    @Path("/data")
    @PermitAll
    @Operation(summary = "按 dictType 查数据")
    public R<List<SysDictData>> dataByType(@QueryParam("type") String type) {
        if (type == null || type.isBlank()) return R.fail(400, "type 必填");
        return R.ok(dataRepository.listByType(type));
    }

    @POST
    @Path("/data")
    @Transactional
    @Log(title = "字典管理", businessType = 1)
    @Operation(summary = "新建字典项")
    public R<SysDictData> createData(SysDictData data) {
        data.id = null;
        if (data.status == null) data.status = 1;
        if (data.isDefault == null) data.isDefault = 0;
        if (data.sortOrder == null) data.sortOrder = 0;
        dataRepository.persist(data);
        return R.ok(data);
    }

    @DELETE
    @Path("/data/{id}")
    @Transactional
    @Log(title = "字典管理", businessType = 3)
    @Operation(summary = "删除字典项")
    public R<Void> deleteData(@PathParam("id") Long id) {
        SysDictData d = dataRepository.findById(id);
        if (d == null) return R.fail(404, "字典项不存在");
        d.deleted = true;
        dataRepository.persist(d);
        return R.ok();
    }
}
