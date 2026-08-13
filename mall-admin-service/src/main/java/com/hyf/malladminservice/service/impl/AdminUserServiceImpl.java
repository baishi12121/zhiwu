package com.hyf.malladminservice.service.impl;


import com.hyf.malladminservice.service.AdminAuthService;
import com.hyf.malladminservice.service.AdminBannerService;
import com.hyf.malladminservice.service.AdminProductService;
import com.hyf.malladminservice.service.AdminSalesService;
import com.hyf.malladminservice.service.AdminSeckillService;
import com.hyf.malladminservice.service.AdminUserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hyf.malladminservice.entity.AdminUser;
import com.hyf.malladminservice.mapper.AdminUserMapper;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import com.hyf.mallcommon.core.result.ResultCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 用户管理业务逻辑。
 *
 * <p>支持用户列表（多条件筛选）、详情、启停、调整会员等级。
 * 出于安全考虑，本服务不提供用户密码重置——密码仍由 auth-service 维护。
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdminUserServiceImpl implements AdminUserService {

    /** 合法的会员等级枚举值 */
    private static final Set<String> VALID_LEVELS = Set.of("NORMAL", "SILVER", "GOLD", "DIAMOND");

    private final AdminUserMapper adminUserMapper;

    /**
     * 用户分页查询，支持按手机号 / 昵称 / 状态 / 会员等级筛选。
     */
    public PageResult<AdminUser> listUsers(PageQuery query, String keyword, Integer status, String memberLevel) {
        LambdaQueryWrapper<AdminUser> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // 同时模糊匹配昵称与手机号
            wrapper.and(w -> w.like(AdminUser::getNickname, keyword)
                    .or().like(AdminUser::getMobile, keyword));
        }
        if (status != null) {
            wrapper.eq(AdminUser::getStatus, status);
        }
        if (StringUtils.hasText(memberLevel)) {
            wrapper.eq(AdminUser::getMemberLevel, memberLevel);
        }
        wrapper.orderByDesc(AdminUser::getCreateTime);

        IPage<AdminUser> page = new Page<>(query.getPage(), query.getPageSize());
        IPage<AdminUser> result = adminUserMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords(), result.getTotal(), query.getPage(), query.getPageSize());
    }

    /**
     * 用户详情。
     *
     * @param id 用户 ID
     * @return 用户实体
     * @throws BizException 用户不存在
     */
    public AdminUser getUser(Long id) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        return user;
    }

    /**
     * 用户启停。
     *
     * @param id     用户 ID
     * @param status 0 禁用 1 正常
     */
    @Transactional
    public void updateStatus(Long id, Integer status) {
        AdminUser exist = adminUserMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        if (exist.getIsAdmin() != null && exist.getIsAdmin() == 1) {
            throw new BizException(ResultCode.BAD_REQUEST, "不能禁用管理员账号");
        }
        exist.setStatus(status);
        adminUserMapper.updateById(exist);
        log.info("[admin-user] 用户启停: id={}, status={}", id, status);
    }

    /**
     * 调整会员等级。
     *
     * @param id          用户 ID
     * @param memberLevel NORMAL / SILVER / GOLD / DIAMOND
     */
    @Transactional
    public void updateMemberLevel(Long id, String memberLevel) {
        if (!VALID_LEVELS.contains(memberLevel)) {
            throw new BizException(ResultCode.BAD_REQUEST, "非法会员等级: " + memberLevel);
        }
        AdminUser exist = adminUserMapper.selectById(id);
        if (exist == null) {
            throw new BizException(ResultCode.NOT_FOUND, "用户不存在");
        }
        exist.setMemberLevel(memberLevel);
        adminUserMapper.updateById(exist);
        log.info("[admin-user] 调整会员等级: id={}, level={}", id, memberLevel);
    }
}
