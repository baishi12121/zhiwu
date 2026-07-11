package com.hyf.mallauthservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 通过 getPhoneNumber code 绑定手机号请求体。
 *
 * <p>对应 {@code POST /auth/bindWechatPhoneByCode}：
 * <pre>{@code
 * { "openid": "oxxx123", "phoneCode": "phone_xxx..." }
 * }</pre>
 *
 * <p>前端在微信小程序中通过
 * {@code <button open-type="getPhoneNumber">} 拿到 phoneCode（加密的临时凭证），
 * 由后端调微信 {@code getuserphonenumber} 接口换真实手机号，再走绑定/合并流程。
 *
 * @author hyf
 */
@Data
public class BindWechatPhoneByCodeRequest {

    /** 微信 openid（由 wxLogin 返回） */
    @NotBlank(message = "openid 不能为空")
    private String openid;

    /** getPhoneNumber 返回的加密凭证（前端从 getPhoneNumber 回调的 e.detail.code 取） */
    @NotBlank(message = "phoneCode 不能为空")
    private String phoneCode;
}
