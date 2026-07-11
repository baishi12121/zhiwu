package com.hyf.mallcommon.mybatis.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 基础实体 —— 含 id / create_time / update_time。
 *
 * <p>createTime/updateTime 由 {@code MybatisMetaObjectHandler} 自动填充：
 * <ul>
 *   <li>{@link #createTime} 仅 insert 时填（{@link FieldFill#INSERT}）；</li>
 *   <li>{@link #updateTime} insert + update 都填（{@link FieldFill#INSERT_UPDATE}）。</li>
 * </ul>
 * 调用方显式设值优先于自动填充；DB 端 {@code DEFAULT CURRENT_TIMESTAMP [ON UPDATE]} 仍作兜底。
 *
 * <p>id 不加 {@code @TableId}，MyBatis-Plus 默认 {@code IdType.NONE}，尊重 DB 自增
 * （{@code mall.sql} 各表均为 {@code BIGINT UNSIGNED AUTO_INCREMENT}）。
 *
 * <p><b>逻辑删除 / 乐观锁不在此声明</b>：当前库表无 {@code deleted} / {@code version} 列，
 * 若加在此处会让所有继承 {@code BaseEntity} 的实体引用不存在的列。需要时改继承
 * {@link LogicDeleteEntity} / {@link VersionedEntity}（并确保对应表已加列）。
 *
 * @author hyf
 */
@Data
public class BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
