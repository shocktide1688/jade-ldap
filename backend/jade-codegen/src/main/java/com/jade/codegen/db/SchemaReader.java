package com.jade.codegen.db;

import com.jade.codegen.model.TableMeta;

import java.util.List;

/**
 * 数据库 Schema 读取器接口
 *
 * 实现：PostgresSchemaReader / MySqlSchemaReader / ...
 */
public interface SchemaReader {

    /**
     * 读取指定表的元数据
     */
    TableMeta readTable(String tableName);

    /**
     * 读取所有表
     */
    List<TableMeta> readAllTables();

    /**
     * 表名转 Java 类名（snake_case → PascalCase）
     */
    static String toClassName(String tableName) {
        StringBuilder sb = new StringBuilder();
        boolean upper = true;
        for (char c : tableName.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 字段名转 camelCase（user_name → userName）
     */
    static String toCamelCase(String columnName) {
        StringBuilder sb = new StringBuilder();
        boolean upper = false;
        for (char c : columnName.toCharArray()) {
            if (c == '_') {
                upper = true;
            } else if (upper) {
                sb.append(Character.toUpperCase(c));
                upper = false;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
