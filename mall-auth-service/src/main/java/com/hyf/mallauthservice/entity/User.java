package com.hyf.mallauthservice.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.hyf.mallcommon.mybatis.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户主表实体，映射 {@code user} 表。
 *
 * <p>继承 {@link BaseEntity} 获得 id / createTime / updateTime 及自动填充。
 * 密码存储使用 MD5（与种子数据一致），生产环境建议升级为 BCrypt。
 *
 * @author hyf
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("user")
public class User extends BaseEntity {

    /** 账号 */
    private String account;

    /** 昵称 */
    private String nickname;

    /** MD5 密文 */
    private String password;

    /** 手机号 */
    private String mobile;

    /** 头像 URL */
    private String avatar;

    /** 性别：0 未知，1 男，2 女 */
    private Integer gender;

    /** 生日 */
    private LocalDate birthday;

    /** 职业 */
    private String profession;

    /** 省份编码 */
    private String provinceCode;

    /** 城市编码 */
    private String cityCode;

    /** 区/县编码 */
    private String countyCode;

    /** 模拟余额 */
    private BigDecimal balance;

    /** 会员等级：NORMAL / SILVER / GOLD / DIAMOND */
    private String memberLevel;

    /** 成长值 */
    private Integer growth;

    /** 状态：0 禁用，1 正常 */
    private Integer status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;
}
