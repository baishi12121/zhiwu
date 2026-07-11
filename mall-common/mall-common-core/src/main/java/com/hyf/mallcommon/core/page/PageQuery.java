package com.hyf.mallcommon.core.page;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询入参。
 *
 * <p>所有分页查询接口统一接收 {@code page}（页码，从 1 开始）+ {@code pageSize}（每页条数），
 * 由 {@link #setPage(Integer)} / {@link #setPageSize(Integer)} 在 setter 内自动校正非法值，
 * 因此 Controller 可直接把前端传入的参数绑定到本类，无需额外校验逻辑。
 *
 * <p>校正规则：
 * <ul>
 *   <li>{@code page} 为 null 或 &lt; 1 时回落到 {@link #DEFAULT_PAGE}；</li>
 *   <li>{@code pageSize} 为 null 或 &lt; 1 时回落到 {@link #DEFAULT_PAGE_SIZE}，
 *       &gt; {@link #MAX_PAGE_SIZE} 时截断到 {@link #MAX_PAGE_SIZE}，防止恶意大分页拖垮 DB。</li>
 * </ul>
 *
 * <p>具体分页实现：MyBatis 项目可调用 {@link #offset()} 计算 limit 偏移量，
 * 或交给 PageHelper 等分页插件按 {@code page}/{@code pageSize} 自动处理。
 *
 * @author hyf
 */
@Data
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 默认页码 */
    public static final int DEFAULT_PAGE = 1;
    /** 默认每页条数 */
    public static final int DEFAULT_PAGE_SIZE = 10;
    /** 每页最大条数，超过则截断 */
    public static final int MAX_PAGE_SIZE = 50;

    /** 页码，从 1 开始 */
    private Integer page = DEFAULT_PAGE;
    /** 每页条数 */
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /**
     * 设置页码，自动校正非法值。
     *
     * @param page 页码，null 或 &lt; 1 时取默认值
     */
    public void setPage(Integer page) {
        this.page = (page == null || page < 1) ? DEFAULT_PAGE : page;
    }

    /**
     * 设置每页条数，自动校正非法值并截断到上限。
     *
     * @param pageSize 每页条数，null 或 &lt; 1 取默认值，&gt; {@link #MAX_PAGE_SIZE} 取上限
     */
    public void setPageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            this.pageSize = DEFAULT_PAGE_SIZE;
        } else if (pageSize > MAX_PAGE_SIZE) {
            this.pageSize = MAX_PAGE_SIZE;
        } else {
            this.pageSize = pageSize;
        }
    }

    /**
     * 计算 limit 偏移量，供原生 SQL 分页使用。
     *
     * @return {@code (page - 1) * pageSize}
     */
    public long offset() {
        return (long) (page - 1) * pageSize;
    }
}
