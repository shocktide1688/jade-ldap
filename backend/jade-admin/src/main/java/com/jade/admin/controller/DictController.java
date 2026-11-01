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

import java.time.LocalDateTime;
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
        if (type.dictType == null || type.dictType.isBlank()) return R.fail(400, "dictType 必填");
        if (type.dictName == null || type.dictName.isBlank()) return R.fail(400, "dictName 必填");
        if (isDupType(type.dictType, null)) return R.fail(4009, "字典类型 key 已存在: " + type.dictType);
        type.id = null;
        if (type.status == null) type.status = 1;
        if (type.createdAt == null) type.createdAt = LocalDateTime.now();
        type.updatedAt = type.createdAt;
        typeRepository.persist(type);
        return R.ok(type);
    }

    @PUT
    @Path("/types/{id}")
    @Transactional
    @Log(title = "字典管理", businessType = 2)
    @Operation(summary = "修改字典类型", description = "修改 dictType 时同步更新其下所有字典项的 dictType")
    public R<SysDictType> updateType(@PathParam("id") Long id, SysDictType req) {
        SysDictType t = typeRepository.findById(id);
        if (t == null || Boolean.TRUE.equals(t.deleted)) return R.fail(404, "字典类型不存在");
        if (req.dictType != null && !req.dictType.isBlank() && !req.dictType.equals(t.dictType)) {
            if (isDupType(req.dictType, id)) return R.fail(4009, "字典类型 key 已存在: " + req.dictType);
            String oldKey = t.dictType;
            t.dictType = req.dictType;
            // 级联迁移字典项的 dictType, 避免孤儿数据
            for (SysDictData d : dataRepository.list("dictType = ?1 and deleted = false", oldKey)) {
                d.dictType = req.dictType;
                d.updatedAt = LocalDateTime.now();
                dataRepository.persist(d);
            }
        }
        if (req.dictName != null && !req.dictName.isBlank()) t.dictName = req.dictName;
        if (req.remark != null) t.remark = req.remark;
        if (req.status != null) t.status = req.status;
        t.updatedAt = LocalDateTime.now();
        typeRepository.persist(t);
        return R.ok(t);
    }

    @DELETE
    @Path("/types/{id}")
    @Transactional
    @Log(title = "字典管理", businessType = 3)
    @Operation(summary = "删除字典类型", description = "级联软删其下所有字典项")
    public R<Void> deleteType(@PathParam("id") Long id) {
        SysDictType t = typeRepository.findById(id);
        if (t == null || Boolean.TRUE.equals(t.deleted)) return R.fail(404, "字典类型不存在");
        t.deleted = true;
        t.updatedAt = LocalDateTime.now();
        typeRepository.persist(t);
        for (SysDictData d : dataRepository.list("dictType = ?1 and deleted = false", t.dictType)) {
            d.deleted = true;
            d.updatedAt = LocalDateTime.now();
            dataRepository.persist(d);
        }
        return R.ok();
    }

    // ---- 数据 ----

    @GET
    @Path("/data")
    @PermitAll
    @Operation(summary = "按 dictType 查数据", description = "all=true 返回含停用项(管理用); 默认仅启用项")
    public R<List<SysDictData>> dataByType(@QueryParam("type") String type,
                                           @QueryParam("all") boolean all) {
        if (type == null || type.isBlank()) return R.fail(400, "type 必填");
        return R.ok(all
                ? dataRepository.list("dictType = ?1 and deleted = false order by sortOrder", type)
                : dataRepository.listByType(type));
    }

    @POST
    @Path("/data")
    @Transactional
    @Log(title = "字典管理", businessType = 1)
    @Operation(summary = "新建字典项")
    public R<SysDictData> createData(SysDictData data) {
        if (data.dictType == null || data.dictType.isBlank()) return R.fail(400, "dictType 必填");
        if (data.dictLabel == null || data.dictLabel.isBlank()) return R.fail(400, "dictLabel 必填");
        if (data.dictValue == null || data.dictValue.isBlank()) return R.fail(400, "dictValue 必填");
        data.id = null;
        if (data.status == null) data.status = 1;
        if (data.isDefault == null) data.isDefault = 0;
        if (data.sortOrder == null) data.sortOrder = 0;
        if (data.createdAt == null) data.createdAt = LocalDateTime.now();
        data.updatedAt = data.createdAt;
        dataRepository.persist(data);
        return R.ok(data);
    }

    @PUT
    @Path("/data/{id}")
    @Transactional
    @Log(title = "字典管理", businessType = 2)
    @Operation(summary = "修改字典项")
    public R<SysDictData> updateData(@PathParam("id") Long id, SysDictData req) {
        SysDictData d = dataRepository.findById(id);
        if (d == null || Boolean.TRUE.equals(d.deleted)) return R.fail(404, "字典项不存在");
        if (req.dictLabel != null && !req.dictLabel.isBlank()) d.dictLabel = req.dictLabel;
        if (req.dictValue != null && !req.dictValue.isBlank()) d.dictValue = req.dictValue;
        if (req.cssClass != null) d.cssClass = req.cssClass;
        if (req.sortOrder != null) d.sortOrder = req.sortOrder;
        if (req.status != null) d.status = req.status;
        if (req.isDefault != null) d.isDefault = req.isDefault;
        if (req.remark != null) d.remark = req.remark;
        d.updatedAt = LocalDateTime.now();
        dataRepository.persist(d);
        return R.ok(d);
    }

    @DELETE
    @Path("/data/{id}")
    @Transactional
    @Log(title = "字典管理", businessType = 3)
    @Operation(summary = "删除字典项")
    public R<Void> deleteData(@PathParam("id") Long id) {
        SysDictData d = dataRepository.findById(id);
        if (d == null || Boolean.TRUE.equals(d.deleted)) return R.fail(404, "字典项不存在");
        d.deleted = true;
        d.updatedAt = LocalDateTime.now();
        dataRepository.persist(d);
        return R.ok();
    }

    private boolean isDupType(String dictType, Long excludeId) {
        SysDictType dup = typeRepository.find("dictType = ?1 and deleted = false", dictType).firstResult();
        return dup != null && !dup.id.equals(excludeId);
    }
}
