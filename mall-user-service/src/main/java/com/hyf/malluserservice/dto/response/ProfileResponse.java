package com.hyf.malluserservice.dto.response;

import lombok.Builder;
import lombok.Data;

/**
 * 用户资料响应（GET/PUT /user/profile 共用）。
 *
 * <p>字段对齐前端 {@code ProfileDetail} 类型。
 *
 * @author hyf
 */
@Data
@Builder
public class ProfileResponse {

    /** 用户 ID */
    private Long id;

    /** 账号 */
    private String account;

    /** 昵称 */
    private String nickname;

    /** 头像 URL */
    private String avatar;

    /** 性别：'男' / '女' / null（未知） */
    private String gender;

    /** 生日 yyyy-MM-dd */
    private String birthday;

    /** 完整行政区（省 市 区），需要 region 表反查，当前可能为 null */
    private String fullLocation;

    /** 省份编码 */
    private String provinceCode;

    /** 城市编码 */
    private String cityCode;

    /** 区/县编码 */
    private String countyCode;

    /** 职业 */
    private String profession;
}
