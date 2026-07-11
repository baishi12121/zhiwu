package com.hyf.mallcommon.core.page;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 通用分页结果包装。
 *
 * <p>所有分页查询接口的成功响应统一用 {@code PageResult<T>} 承载列表数据，
 * 对外结构为 {@code { "total": long, "page": int, "pageSize": int, "list": T[] }}：
 * 前端用 {@link #total} 渲染分页器、用 {@link #list} 渲染列表。
 *
 * <p>构造方式：
 * <ul>
 *   <li>有数据 —— {@link #of(List, Long, Integer, Integer)}，传入当前页数据与总条数；</li>
 *   <li>无数据 —— {@link #empty(Integer, Integer)}，返回 total=0、空列表的结果。</li>
 * </ul>
 *
 * <p>所有字段在构造时均做了 null 防御：{@code total} 回落 0、{@code page}/{@code pageSize}
 * 回落默认值、{@code list} 回落空集合，保证序列化后字段始终存在、前端不会拿到 null。
 *
 * @param <T> 列表元素类型
 * @author hyf
 * @see PageQuery
 */
@Data
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 总条数 */
    private Long total;
    /** 当前页码 */
    private Integer page;
    /** 每页条数 */
    private Integer pageSize;
    /** 当前页数据列表，永不为 null */
    private List<T> list;

    public PageResult() {
    }

    /**
     * 全参构造，对每个参数做 null 防御。
     *
     * @param total    总条数，null 取 0
     * @param page     当前页码，null 取 1
     * @param pageSize 每页条数，null 取 10
     * @param list     当前页数据，null 取空集合
     */
    public PageResult(Long total, Integer page, Integer pageSize, List<T> list) {
        this.total = total == null ? 0L : total;
        this.page = page == null ? 1 : page;
        this.pageSize = pageSize == null ? 10 : pageSize;
        this.list = list == null ? Collections.emptyList() : list;
    }

    /**
     * 构造空结果，用于查询无数据的场景。
     *
     * @param page     当前页码
     * @param pageSize 每页条数
     * @param <T>      列表元素类型
     * @return total=0、list 为空集合的分页结果
     */
    public static <T> PageResult<T> empty(Integer page, Integer pageSize) {
        return new PageResult<>(0L, page, pageSize, Collections.emptyList());
    }

    /**
     * 由当前页数据与总条数构造分页结果。
     *
     * @param list     当前页数据
     * @param total    总条数
     * @param page     当前页码
     * @param pageSize 每页条数
     * @param <T>      列表元素类型
     * @return 分页结果
     */
    public static <T> PageResult<T> of(List<T> list, Long total, Integer page, Integer pageSize) {
        return new PageResult<>(total, page, pageSize, list);
    }
}
