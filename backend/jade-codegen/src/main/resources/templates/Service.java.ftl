package ${package}.${module}.service;

import ${package}.${module}.entity.${className};
import ${package}.${module}.repository.${className}Repository;
import com.jade.common.api.PageResult;
import com.jade.common.api.R;
import com.jade.common.exception.BizException;
import com.jade.common.constant.ResultCode;
import io.quarkus.panache.common.Page;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * ${className} 业务层（自动生成）
 * @author ${author}
 */
@ApplicationScoped
public class ${className}Service {

    @Inject
    ${className}Repository repository;

    public R<PageResult<${className}>> page(int page, int size, String keyword) {
        boolean hasKw = keyword != null && !keyword.isBlank();
        String query = hasKw ? "1=1" : "1=1";
        var q = repository.findAll(Sort.by("id").descending());
        long total = q.count();
        List<${className}> records = q.page(Page.of(page - 1, size)).list();
        return R.ok(PageResult.of(records, total, page, size));
    }

    public R<${className}> getById(Long id) {
        ${className} entity = repository.findById(id);
        if (entity == null) {
            return R.fail(ResultCode.NOT_FOUND, "${className} 不存在");
        }
        return R.ok(entity);
    }

    @Transactional
    public R<${className}> create(${className} entity) {
        repository.persist(entity);
        return R.ok(entity);
    }

    @Transactional
    public R<${className}> update(Long id, ${className} entity) {
        ${className} existing = repository.findById(id);
        if (existing == null) {
            return R.fail(ResultCode.NOT_FOUND, "${className} 不存在");
        }
        // TODO: 业务字段更新
        return R.ok(existing);
    }

    @Transactional
    public R<Void> delete(Long id) {
        if (!repository.deleteById(id)) {
            return R.fail(ResultCode.NOT_FOUND, "${className} 不存在");
        }
        return R.ok();
    }
}
