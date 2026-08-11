package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 用户会员等级调整请求。
 *
 * <p>取值范围：{@code NORMAL / SILVER / GOLD / DIAMOND}。
 *
 * @author hyf
 */
@Data
public class UserLevelRequest {

    @NotBlank(message = "memberLevel 不能为空")
    private String memberLevel;
}
