package com.jade.codegen.db;

import com.jade.codegen.model.TableMeta;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 JDBC 读 PG / MySQL 元数据
 */
@Slf4j
public class JdbcSchemaReader implements SchemaReader {

    private final Connection connection;
    private final String dbType;  // "postgresql" or "mysql"

    public JdbcSchemaReader(Connection connection, String dbType) {
        this.connection = connection;
        this.dbType = dbType;
    }

    @Override
    public TableMeta readTable(String tableName) {
        List<TableMeta> all = readAllTables();
        return all.stream()
                .filter(t -> t.getName().equalsIgnoreCase(tableName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Table not found: " + tableName));
    }

    @Override
    public List<TableMeta> readAllTables() {
        List<TableMeta> tables = new ArrayList<>();
        try {
            DatabaseMetaData meta = connection.getMetaData();

            // 1. 所有表
            Map<String, TableMeta> tableMap = new HashMap<>();
            try (ResultSet rs = meta.getTables(null, "public", null, new String[]{"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    String comment = rs.getString("REMARKS");
                    TableMeta t = new TableMeta();
                    t.setName(name);
                    t.setComment(comment);
                    t.setColumns(new ArrayList<>());
                    tableMap.put(name, t);
                }
            }

            // 2. 所有列
            for (Map.Entry<String, TableMeta> entry : tableMap.entrySet()) {
                String tableName = entry.getKey();
                TableMeta table = entry.getValue();
                try (ResultSet rs = meta.getColumns(null, "public", tableName, null)) {
                    while (rs.next()) {
                        TableMeta.ColumnMeta col = new TableMeta.ColumnMeta();
                        col.setName(rs.getString("COLUMN_NAME"));
                        col.setType(rs.getString("TYPE_NAME").toLowerCase());
                        col.setJavaType(toJavaType(col.getType()));
                        col.setComment(rs.getString("REMARKS"));
                        col.setNullable("YES".equals(rs.getString("IS_NULLABLE")));
                        col.setLength((int) rs.getLong("COLUMN_SIZE"));
                        col.setDefaultValue(rs.getString("COLUMN_DEF"));
                        table.getColumns().add(col);
                    }
                }
                // 3. 主键
                try (ResultSet rs = meta.getPrimaryKeys(null, "public", tableName)) {
                    while (rs.next()) {
                        String pkCol = rs.getString("COLUMN_NAME");
                        table.getColumns().stream()
                                .filter(c -> c.getName().equals(pkCol))
                                .findFirst()
                                .ifPresent(c -> c.setPrimaryKey(true));
                    }
                }
            }

            tables.addAll(tableMap.values());
        } catch (SQLException e) {
            log.error("Failed to read schema", e);
            throw new RuntimeException(e);
        }
        return tables;
    }

    private String toJavaType(String sqlType) {
        return switch (sqlType.toLowerCase()) {
            case "bigint", "int8" -> "Long";
            case "int", "int4", "integer" -> "Integer";
            case "smallint", "int2" -> "Short";
            case "varchar", "text", "char", "bpchar", "name" -> "String";
            case "timestamp", "timestamptz", "datetime" -> "OffsetDateTime";
            case "date" -> "LocalDate";
            case "time" -> "LocalTime";
            case "boolean", "bool" -> "Boolean";
            case "numeric", "decimal" -> "BigDecimal";
            case "real", "float4" -> "Float";
            case "double", "float8" -> "Double";
            case "json", "jsonb" -> "String";
            case "uuid" -> "UUID";
            default -> "String";
        };
    }
}
