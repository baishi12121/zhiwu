package com.hyf.mallcommon.mybatis.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 元对象处理器：自动填充 createTime / updateTime。
 *
 * <p>仅对实体类上带 {@code @TableField(fill = ...)} 声明的字段生效（见 {@link com.hyf.mallcommon.mybatis.entity.BaseEntity}），
 * 未继承 {@code BaseEntity} 或未声明 fill 的实体不受影响。
 *
 * <p>使用 {@code strict*Fill} 系列：仅在字段有对应 {@link com.baomidou.mybatisplus.annotation.FieldFill} 注解，
 * 且（insert 时）字段当前为 null 时才填入 —— 调用方显式设值优先，不会被覆盖。
 *
 * @author hyf
 */
public class MybatisMetaObjectHandler implements MetaObjectHandler {

    /** insert 时填充 createTime + updateTime */
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now();
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);
    }

    /** update 时仅填充 updateTime */
    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
