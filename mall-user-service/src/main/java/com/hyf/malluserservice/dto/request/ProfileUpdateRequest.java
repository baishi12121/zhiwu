package com.hyf.malluserservice.dto.request;

import lombok.Data;

/**
 * 用户资料修改请求（PUT /user/profile）。
 *
 * <p>所有字段可选，仅更新非 null 的字段。
 * 字段对齐前端 {@code ProfileParams} 类型。
 *
 * @author hyf
 */
@Data
public class ProfileUpdateRequest {

    /** 昵称 */
    private String nickname;

    /** 性别：'男' / '女' */
    private String gender;

    /** 生日 yyyy-MM-dd */
    private String birthday;

    /** 职业 */
    private String profession;

    /** 省份编码 */
    private String provinceCode;

    /** 城市编码 */
    private String cityCode;

    /** 区/县编码 */
    private String countyCode;
}
