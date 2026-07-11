package com.hyf.mallcommon.mybatis.entity;

import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带乐观锁版本号的基础实体。
 *
 * <p><b>仅当对应表已加 {@code version} 列时才继承本类</b>，否则 insert/update 会因列不存在报错。
 * 需配合 {@code OptimisticLockerInnerInterceptor}（已由 {@code MybatisPlusAutoConfiguration} 自动装配）使用：
 * update 时 MP 自动把 {@code version} 加入 WHERE 条件并 {@code +1}，若版本冲突则影响行数为 0，
 * 由业务层据此判断并发冲突并重试。
 *
 * <p>insert 时本字段由 MP 自动置 1（首次版本号），无需在 {@code MetaObjectHandler} 填充。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class VersionedEntity extends BaseEntity {

    /** 乐观锁版本号，update 时自动参与条件判定并 +1 */
    @Version
    private Integer version;
}
