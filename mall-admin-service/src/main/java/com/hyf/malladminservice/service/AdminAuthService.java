package com.hyf.malladminservice.service;

import com.hyf.malladminservice.dto.request.AdminLoginRequest;
import com.hyf.malladminservice.dto.response.AdminLoginResponse;
import com.hyf.malladminservice.entity.AdminUser;

public interface AdminAuthService {

    public AdminLoginResponse login(AdminLoginRequest req);
    public void logout(String accessToken);
    public AdminUser getCurrentAdmin(Long userId);

}
