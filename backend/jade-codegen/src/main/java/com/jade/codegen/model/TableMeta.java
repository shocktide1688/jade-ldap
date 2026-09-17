package com.jade.codegen.model;

import lombok.Data;

import java.util.List;

/**
 * DB 表元数据
 */
@Data
public class TableMeta {
    private String name;
    private String comment;
    private List<ColumnMeta> columns;

    @Data
    public static class ColumnMeta {
        private String name;
        private String type;        // varchar, bigint, etc.
        private String javaType;    // String, Long, Integer...
        private String comment;
        private boolean primaryKey;
        private boolean nullable;
        private Integer length;
        private String defaultValue;
    }
}
