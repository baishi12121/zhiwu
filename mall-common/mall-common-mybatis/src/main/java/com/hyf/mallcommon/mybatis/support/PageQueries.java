package com.hyf.mallcommon.mybatis.support;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;

/**
 * {@link PageQuery} ↔ MyBatis-Plus {@link Page} 桥接工具。
 *
 * <p>业务服务分页查询的标准写法：
 * <pre>{@code
 * Page<ProductDO> mpPage = PageQueries.toPage(query);
 * Page<ProductDO> result = mapper.selectPage(mpPage, wrapper);
 * return PageQueries.toPageResult(result);
 * }</pre>
 * 对外仍返回项目自有的 {@link PageResult}，MyBatis-Plus 的 {@link IPage}/{@link Page}
 * 仅作内部机制存在，不泄漏到 Controller / API 层。
 *
 * <p>{@link PageQuery} 的 setter 已对页码 / 每页条数做合法性校正，本工具直接取值不再校验。
 *
 * @author hyf
 */
public final class PageQueries {

    private PageQueries() {
        // 工具类禁止实例化
    }

    /**
     * 由 {@link PageQuery} 构造 MP 分页对象（页码从 1 开始）。
     *
     * @param query 分页入参
     * @param <T>   分页元素类型，仅约束 {@link Page#getRecords()} 的元素，与 page/size 无关
     * @return MP 分页对象
     */
    @SuppressWarnings("unchecked")
    public static <T> Page<T> toPage(PageQuery query) {
        return (Page<T>) new Page<>(query.getPage(), query.getPageSize());
    }

    /**
     * 由 MP 分页对象构造项目对外分页结果。
     *
     * @param page MP 分页对象
     * @param <T>  列表元素类型
     * @return {@link PageResult}，{@code total/page/pageSize} 来自 MP，{@code list} 为当前页记录
     */
    public static <T> PageResult<T> toPageResult(IPage<T> page) {
        return PageResult.of(page.getRecords(), page.getTotal(),
                Math.toIntExact(page.getCurrent()), Math.toIntExact(page.getSize()));
    }
}
