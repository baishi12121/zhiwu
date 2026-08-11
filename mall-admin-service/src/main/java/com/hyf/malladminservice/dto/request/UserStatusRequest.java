package com.hyf.malladminservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户状态调整请求。
 *
 * <p>0 禁用 1 正常。
 *
 * @author hyf
 */
@Data
public class UserStatusRequest {

    @NotNull(message = "status 不能为空")
    private Integer status;
}
