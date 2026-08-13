package com.hyf.mallauthservice.service;

import com.hyf.mallauthservice.dto.request.LoginRequest;
import com.hyf.mallauthservice.dto.request.RefreshTokenRequest;
import com.hyf.mallauthservice.dto.request.RegisterRequest;
import com.hyf.mallauthservice.dto.request.SmsLoginRequest;
import com.hyf.mallauthservice.dto.request.SmsSendRequest;
import com.hyf.mallauthservice.dto.request.BindWechatPhoneByCodeRequest;
import com.hyf.mallauthservice.dto.request.BindWechatPhoneRequest;
import com.hyf.mallauthservice.dto.request.WxLoginRequest;
import com.hyf.mallauthservice.dto.response.LoginResponse;

public interface AuthService {

    public LoginResponse login(LoginRequest req);
    public LoginResponse register(RegisterRequest req);
    public LoginResponse refreshToken(RefreshTokenRequest req);
    public void logout(String accessToken);
    public void sendSmsCode(SmsSendRequest req);
    public LoginResponse smsLogin(SmsLoginRequest req);
    public String getOpenId(String code);
    public LoginResponse wxLogin(WxLoginRequest req);
    public LoginResponse bindWechatPhone(BindWechatPhoneRequest req);
    public String getAccessToken();
    public String decryptPhoneNumber(String phoneCode);
    public LoginResponse bindWechatPhoneByCode(BindWechatPhoneByCodeRequest req);

}
