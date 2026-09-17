package ${package}.${module}.controller;

import ${package}.${module}.entity.${className};
import ${package}.${module}.service.${className}Service;
import com.jade.common.api.PageResult;
import com.jade.common.api.R;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * ${className} 接口（自动生成）
 * @author ${author}
 */
@Path("/api/v1/${table.name?replace("^t_", "", "r")?replace("_", "-")}")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "${className}")
@PermitAll
public class ${className}Controller {

    @Inject
    ${className}Service service;

    @GET
    @Operation(summary = "分页列表")
    public R<PageResult<${className}>> page(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("10") int size,
            @QueryParam("keyword") String keyword) {
        return service.page(page, size, keyword);
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "详情")
    public R<${className}> get(@PathParam("id") Long id) {
        return service.getById(id);
    }

    @POST
    @Operation(summary = "创建")
    public R<${className}> create(${className} entity) {
        return service.create(entity);
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "更新")
    public R<${className}> update(@PathParam("id") Long id, ${className} entity) {
        return service.update(id, entity);
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "删除")
    public R<Void> delete(@PathParam("id") Long id) {
        return service.delete(id);
    }
}
