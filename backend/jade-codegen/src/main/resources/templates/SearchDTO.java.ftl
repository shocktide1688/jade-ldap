package ${package}.${module}.dto;

import lombok.Data;
import java.time.OffsetDateTime;

/**
 * ${className} 查询条件 DTO（自动生成）
 * @author ${author}
 */
@Data
public class ${className}SearchDTO {
    private Integer page = 1;
    private Integer size = 10;
    private String keyword;
    private OffsetDateTime startTime;
    private OffsetDateTime endTime;
}
