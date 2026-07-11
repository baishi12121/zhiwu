package com.hyf.mallcommon.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * MyBatis-Plus 自动装配。
 *
 * <p>通过 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 * 注册（同 mall-common-security 模式）。本 jar 的包名 {@code com.hyf.mallcommon.mybatis}
 * 不在各业务服务 base package 下，服务用裸 {@code @SpringBootApplication} 不扫描本包，
 * 故不能依赖组件扫描，必须走 imports-file 自动装配。
 *
 * <p>装配内容：
 * <ol>
 *   <li>{@link MybatisPlusInterceptor}：挂 {@link PaginationInnerInterceptor}(MySQL)
 *       + {@link OptimisticLockerInnerInterceptor}；分页 {@code maxLimit=500} 防 runaway 查询
 *       （API 层由 {@code PageQuery.MAX_PAGE_SIZE=50} 收口，500 留给合法内部批量）。</li>
 *   <li>{@link MybatisMetaObjectHandler}：insert/update 自动填充 createTime/updateTime。</li>
 * </ol>
 *
 * <p>逻辑删除（{@code @TableLogic}）与乐观锁（{@code @Version}）注解由各实体自行声明
 * （见 {@link com.hyf.mallcommon.mybatis.entity.LogicDeleteEntity} /
 * {@link com.hyf.mallcommon.mybatis.entity.VersionedEntity}），本类只保证对应拦截器在链路上。
 *
 * <p>3.5.15 自带 {@code MybatisPlusInnerInterceptorAutoConfiguration} 受
 * {@code @ConditionalOnMissingBean(MybatisPlusInterceptor.class)} 约束，本类直接造总拦截器 bean，
 * 自动那套会 back off，不重复。
 *
 * @author hyf
 */
@AutoConfiguration
public class MybatisPlusAutoConfiguration {

    /** 分页 DB 端硬上限，避免恶意大分页拖垮 DB。API 层由 PageQuery.MAX_PAGE_SIZE=50 收口。 */
    private static final long MAX_LIMIT = 500L;

    /**
     * MyBatis-Plus 总拦截器，挂分页 + 乐观锁两个 inner interceptor。
     *
     * @return MP 总拦截器
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(MAX_LIMIT);
        interceptor.addInnerInterceptor(pagination);
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }

    /**
     * 自动填充 createTime/updateTime。
     *
     * @return 元对象处理器
     */
    @Bean
    public MetaObjectHandler mybatisMetaObjectHandler() {
        return new MybatisMetaObjectHandler();
    }
}
