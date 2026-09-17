package com.jade.common.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;

/**
 * 统一分页响应
 *
 * @param <T> 列表元素类型
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> records;
    private long total;
    private int page;
    private int size;
    private int totalPages;

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new PageResult<>(records, total, page, size, totalPages);
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(Collections.emptyList(), 0, page, size, 0);
    }
}
