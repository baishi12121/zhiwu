package com.hyf.malluserservice.service.impl;

import com.hyf.malluserservice.dto.LoginRequest;
import com.hyf.malluserservice.dto.LoginResponse;
import com.hyf.malluserservice.entity.User;
import com.hyf.malluserservice.entity.UserToken;
import com.hyf.malluserservice.mapper.UserMapper;
import com.hyf.malluserservice.mapper.UserTokenMapper;
import com.hyf.malluserservice.service.LoginService;
import com.hyf.malluserservice.util.PasswordUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 登录服务实现
 */
@Service
public class LoginServiceImpl implements LoginService {

    /**
     * token 默认有效期(秒): 7 天
     */
    private static final long DEFAULT_EXPIRES_SECONDS = 7L * 24 * 60 * 60;

    private final UserMapper userMapper;
    private final UserTokenMapper userTokenMapper;

    @Autowired
    public LoginServiceImpl(UserMapper userMapper, UserTokenMapper userTokenMapper) {
        this.userMapper = userMapper;
        this.userTokenMapper = userTokenMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponse loginByPhone(LoginRequest req) {
        if (req == null) {
            throw new IllegalArgumentException("登录请求不能为空");
        }
        String phone = req.getPhone();
        String password = req.getPassword();
        if (phone == null || phone.trim().isEmpty()) {
            throw new IllegalArgumentException("手机号不能为空");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }

        User user = userMapper.selectByPhone(phone.trim());
        if (user == null) {
            throw new IllegalArgumentException("手机号未注册");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new IllegalArgumentException("账号已被禁用");
        }

        String inputMd5 = PasswordUtil.md5(password);
        if (!inputMd5.equalsIgnoreCase(user.getPassword())) {
            throw new IllegalArgumentException("手机号或密码错误");
        }

        // 写会话
        String token = UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        UserToken ut = new UserToken();
        ut.setUserId(user.getId());
        ut.setToken(token);
        ut.setClient(req.getClient() == null ? "H5" : req.getClient());
        ut.setExpireAt(now.plusSeconds(DEFAULT_EXPIRES_SECONDS));
        userTokenMapper.insert(ut);

        // 更新最后登录时间
        userMapper.updateLastLoginAt(user.getId(), now);

        // 隐藏密码字段
        user.setPassword("******");

        return new LoginResponse(token, DEFAULT_EXPIRES_SECONDS, user);
    }

    @Override
    public User getUserByToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            return null;
        }
        UserToken ut = userTokenMapper.selectByToken(token.trim());
        if (ut == null) {
            return null;
        }
        if (ut.getExpireAt() != null && ut.getExpireAt().isBefore(LocalDateTime.now())) {
            // 过期自动清理
            userTokenMapper.deleteByToken(token);
            return null;
        }
        User user = userMapper.selectById(ut.getUserId());
        if (user != null) {
            user.setPassword("******");
        }
        return user;
    }

    @Override
    public void logout(String token) {
        if (token == null || token.trim().isEmpty()) {
            return;
        }
        userTokenMapper.deleteByToken(token.trim());
    }
}
