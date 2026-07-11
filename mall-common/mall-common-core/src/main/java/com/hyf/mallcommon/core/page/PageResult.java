package com.hyf.mallcommon.core.page;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果包装。
 *
 * <p>所有分页查询接口的成功响应统一用 {@code PageResult<T>} 承载列表数据，
 * 对外序列化为 {@code { "counts": long, "page": int, "pages": int, "pageSize": int, "items": T[] }}
 * 对齐前端契约。
 *
 * <p>构造方式：
 * <ul>
 *   <li>有数据 —— {@link #of(List, Long, Integer, Integer)}，传入当前页数据与总条数；</li>
 *   <li>无数据 —— {@link #empty(Integer, Integer)}，返回 counts=0、空列表的结果。</li>
 * </ul>
 *
 * <p>所有字段在构造时均做了 null 防御：{@code counts} 回落 0、{@code page}/{@code pageSize}
 * 回落默认值、{@code items} 回落空集合，保证序列化后字段始终存在、前端不会拿到 null。
 *
 * @param <T> 列表元素类型
 * @author hyf
 * @see PageQuery
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总条数（序列化 key = "counts"） */
    private Long counts;
    /** 当前页码 */
    private Integer page;
    /** 每页条数 */
    private Integer pageSize;
    /** 当前页数据列表，永不为 null（序列化 key = "items"） */
    private List<T> items;

    public PageResult() {
    }

    /**
     * 全参构造，对每个参数做 null 防御。
     *
     * @param counts   总条数，null 取 0
     * @param page     当前页码，null 取 1
     * @param pageSize 每页条数，null 取 10
     * @param items    当前页数据，null 取空集合
     */
    public PageResult(Long counts, Integer page, Integer pageSize, List<T> items) {
        this.counts = counts == null ? 0L : counts;
        this.page = page == null ? 1 : page;
        this.pageSize = pageSize == null ? 10 : pageSize;
        this.items = items == null ? Collections.emptyList() : items;
    }

    // ---- 兼容旧调用方的 getter/setter 别名 ----

    @JsonIgnore
    public Long getTotal() { return counts; }

    @JsonIgnore
    public void setTotal(Long total) { this.counts = total; }

    @JsonIgnore
    public List<T> getList() { return items; }

    @JsonIgnore
    public void setList(List<T> list) { this.items = list; }

    // ---- 工厂方法 ----

    /**
     * 构造空结果，用于查询无数据的场景。
     */
    public static <T> PageResult<T> empty(Integer page, Integer pageSize) {
        return new PageResult<>(0L, page, pageSize, Collections.emptyList());
    }

    /**
     * 总页数，由 {@code counts / pageSize} 向上取整派生。
     */
    public Integer getPages() {
        if (counts == null || counts == 0L) {
            return 0;
        }
        int ps = (pageSize != null && pageSize > 0) ? pageSize : 10;
        return Math.toIntExact((counts + ps - 1) / ps);
    }

    /**
     * 由当前页数据与总条数构造分页结果。
     */
    public static <T> PageResult<T> of(List<T> items, Long counts, Integer page, Integer pageSize) {
        return new PageResult<>(counts, page, pageSize, items);
    }
}
