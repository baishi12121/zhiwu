package com.hyf.malluserservice.service.impl;


import com.hyf.malluserservice.service.AddressService;
import com.hyf.malluserservice.service.CartService;
import com.hyf.malluserservice.service.UserService;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallcommon.security.context.SecurityContextHolder;
import com.hyf.malluserservice.dto.request.ProfileUpdateRequest;
import com.hyf.malluserservice.dto.response.ProfileResponse;
import com.hyf.malluserservice.entity.User;
import com.hyf.malluserservice.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 用户资料服务。
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * 获取当前登录用户的资料。
     *
     * @return 用户资料
     */
    public ProfileResponse getProfile() {
        Long userId = getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return toProfileResponse(user);
    }

    /**
     * 更新当前登录用户的资料。
     *
     * @param req 更新请求（仅更新非 null 字段）
     * @return 更新后的用户资料
     */
    @Transactional
    public ProfileResponse updateProfile(ProfileUpdateRequest req) {
        Long userId = getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }

        boolean changed = false;

        if (req.getNickname() != null) {
            user.setNickname(req.getNickname());
            changed = true;
        }
        if (req.getGender() != null) {
            user.setGender(parseGender(req.getGender()));
            changed = true;
        }
        if (req.getBirthday() != null) {
            user.setBirthday(LocalDate.parse(req.getBirthday(), DATE_FMT));
            changed = true;
        }
        if (req.getProfession() != null) {
            user.setProfession(req.getProfession());
            changed = true;
        }
        if (req.getProvinceCode() != null) {
            user.setProvinceCode(req.getProvinceCode());
            changed = true;
        }
        if (req.getCityCode() != null) {
            user.setCityCode(req.getCityCode());
            changed = true;
        }
        if (req.getCountyCode() != null) {
            user.setCountyCode(req.getCountyCode());
            changed = true;
        }

        if (changed) {
            userMapper.updateById(user);
            log.info("[user] 更新资料成功: userId={}", userId);
        }

        return toProfileResponse(user);
    }

    /**
     * 更新用户头像 URL。
     *
     * @param avatarUrl 头像 URL
     * @return 更新后的用户资料
     */
    @Transactional
    public ProfileResponse updateAvatar(String avatarUrl) {
        Long userId = getCurrentUserId();
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        user.setAvatar(avatarUrl);
        userMapper.updateById(user);
        log.info("[user] 更新头像成功: userId={}, avatar={}", userId, avatarUrl);
        return toProfileResponse(user);
    }

    /**
     * 从 SecurityContextHolder 获取当前登录用户 ID。
     */
    private Long getCurrentUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /**
     * 将 User 实体转为 ProfileResponse。
     */
    private ProfileResponse toProfileResponse(User user) {
        return ProfileResponse.builder()
                .id(user.getId())
                .account(user.getAccount())
                .nickname(user.getNickname())
                .avatar(user.getAvatar())
                .gender(genderToString(user.getGender()))
                .birthday(user.getBirthday() != null ? user.getBirthday().format(DATE_FMT) : null)
                .fullLocation(null) // TODO: 待 region 表反查实现
                .provinceCode(user.getProvinceCode())
                .cityCode(user.getCityCode())
                .countyCode(user.getCountyCode())
                .profession(user.getProfession())
                .build();
    }

    /**
     * 性别 int → 字符串映射。
     */
    private static String genderToString(Integer gender) {
        return switch (gender == null ? 0 : gender) {
            case 1 -> "男";
            case 2 -> "女";
            default -> null;
        };
    }

    /**
     * 性别字符串 → int 映射。
     */
    private static int parseGender(String gender) {
        return switch (gender) {
            case "男" -> 1;
            case "女" -> 2;
            default -> 0;
        };
    }
}
