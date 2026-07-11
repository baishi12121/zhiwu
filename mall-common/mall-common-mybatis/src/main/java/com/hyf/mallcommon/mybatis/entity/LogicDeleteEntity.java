package com.hyf.mallcommon.mybatis.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 带逻辑删除标志的基础实体。
 *
 * <p><b>仅当对应表已加 {@code deleted} 列时才继承本类</b>，否则 insert/update 会因列不存在报错。
 * 未加列前，业务实体继续继承 {@link BaseEntity} 即可，逻辑删除功能保持静默。
 *
 * <p>逻辑删除值由 {@code mybatis-plus.global-config.db-config} 统一管理：
 * {@code logic-not-delete-value=0}（未删）/ {@code logic-delete-value=1}（已删），均为 MP 默认值，
 * 无需在 yml 额外配置。delete 操作会被改写为 {@code UPDATE ... SET deleted=1}，
 * select 自动追加 {@code WHERE deleted=0}。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class LogicDeleteEntity extends BaseEntity {

    /** 逻辑删除标志：0 未删 / 1 已删 */
    @TableLogic
    private Integer deleted;
}
