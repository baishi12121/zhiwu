package com.hyf.malluserservice.service.impl;


import com.hyf.malluserservice.service.AddressService;
import com.hyf.malluserservice.service.CartService;
import com.hyf.malluserservice.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hyf.mallcommon.core.exception.BizException;
import com.hyf.mallcommon.core.result.ResultCode;
import com.hyf.mallcommon.security.context.SecurityContextHolder;
import com.hyf.malluserservice.dto.request.AddressSaveRequest;
import com.hyf.malluserservice.dto.response.AddressResponse;
import com.hyf.malluserservice.entity.UserAddress;
import com.hyf.malluserservice.mapper.UserAddressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 收货地址服务。
 *
 * @author hyf
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AddressServiceImpl implements AddressService {

    private final UserAddressMapper addressMapper;

    /**
     * 创建收货地址。
     *
     * @param req 地址信息
     * @return 创建后的地址
     */
    @Transactional
    public AddressResponse createAddress(AddressSaveRequest req) {
        Long userId = getCurrentUserId();
        UserAddress addr = toEntity(req, userId);
        addressMapper.insert(addr);

        // 如果设为默认，取消其他默认地址
        if (isDefaultTrue(addr.getIsDefault())) {
            clearDefaultExcept(userId, addr.getId());
        }

        log.info("[address] 创建地址成功: userId={}, addressId={}", userId, addr.getId());
        return toResponse(addr);
    }

    /**
     * 获取当前用户的所有收货地址。
     *
     * @return 地址列表
     */
    public List<AddressResponse> listAddresses() {
        Long userId = getCurrentUserId();
        List<UserAddress> addresses = addressMapper.selectList(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .orderByDesc(UserAddress::getIsDefault)
                        .orderByDesc(UserAddress::getUpdateTime)
        );
        return addresses.stream().map(AddressServiceImpl::toResponse).toList();
    }

    /**
     * 获取地址详情。
     *
     * @param id 地址 ID
     * @return 地址详情
     */
    public AddressResponse getAddress(Long id) {
        UserAddress addr = getOwnAddress(id);
        return toResponse(addr);
    }

    /**
     * 更新收货地址。
     *
     * @param id  地址 ID
     * @param req 更新内容
     * @return 更新后的地址
     */
    @Transactional
    public AddressResponse updateAddress(Long id, AddressSaveRequest req) {
        UserAddress addr = getOwnAddress(id);

        if (req.getReceiver() != null) addr.setReceiver(req.getReceiver());
        if (req.getContact() != null) addr.setContact(req.getContact());
        if (req.getProvinceCode() != null) addr.setProvinceCode(req.getProvinceCode());
        if (req.getCityCode() != null) addr.setCityCode(req.getCityCode());
        if (req.getCountyCode() != null) addr.setCountyCode(req.getCountyCode());
        if (req.getFullLocation() != null) addr.setFullLocation(req.getFullLocation());
        if (req.getAddress() != null) addr.setAddress(req.getAddress());
        if (req.getPostalCode() != null) addr.setPostalCode(req.getPostalCode());
        if (req.getAddressTags() != null) addr.setAddressTags(req.getAddressTags());
        if (req.getIsDefault() != null) {
            addr.setIsDefault(req.getIsDefault());
            if (isDefaultTrue(req.getIsDefault())) {
                clearDefaultExcept(addr.getUserId(), addr.getId());
            }
        }

        addressMapper.updateById(addr);
        log.info("[address] 更新地址成功: userId={}, addressId={}", addr.getUserId(), addr.getId());
        return toResponse(addr);
    }

    /**
     * 删除收货地址。
     *
     * @param id 地址 ID
     */
    @Transactional
    public void deleteAddress(Long id) {
        UserAddress addr = getOwnAddress(id);
        addressMapper.deleteById(id);
        log.info("[address] 删除地址成功: userId={}, addressId={}", addr.getUserId(), id);
    }

    // ---------- 内部辅助方法 ----------

    /**
     * 获取属于当前用户的地址，否则抛异常。
     */
    private UserAddress getOwnAddress(Long id) {
        Long userId = getCurrentUserId();
        UserAddress addr = addressMapper.selectById(id);
        if (addr == null) {
            throw new BizException(ResultCode.NOT_FOUND, "地址不存在");
        }
        if (!addr.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FORBIDDEN, "无权操作该地址");
        }
        return addr;
    }

    /**
     * 清除该用户的其他默认地址（将 is_default 置为 0）。
     */
    private void clearDefaultExcept(Long userId, Long exceptId) {
        List<UserAddress> defaults = addressMapper.selectList(
                new LambdaQueryWrapper<UserAddress>()
                        .eq(UserAddress::getUserId, userId)
                        .eq(UserAddress::getIsDefault, 1)
                        .ne(UserAddress::getId, exceptId)
        );
        for (UserAddress addr : defaults) {
            addr.setIsDefault(0);
            addressMapper.updateById(addr);
        }
    }

    private Long getCurrentUserId() {
        Long userId = SecurityContextHolder.getUserId();
        if (userId == null) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    private static boolean isDefaultTrue(Integer val) {
        return val != null && val == 1;
    }

    // ---------- 实体 ↔ 响应 转换 ----------

    static UserAddress toEntity(AddressSaveRequest req, Long userId) {
        UserAddress addr = new UserAddress();
        addr.setUserId(userId);
        addr.setReceiver(req.getReceiver());
        addr.setContact(req.getContact());
        addr.setProvinceCode(req.getProvinceCode());
        addr.setCityCode(req.getCityCode());
        addr.setCountyCode(req.getCountyCode());
        addr.setFullLocation(req.getFullLocation());
        addr.setAddress(req.getAddress());
        addr.setPostalCode(req.getPostalCode());
        addr.setAddressTags(req.getAddressTags());
        addr.setIsDefault(req.getIsDefault() != null ? req.getIsDefault() : 0);
        return addr;
    }

    static AddressResponse toResponse(UserAddress addr) {
        return AddressResponse.builder()
                .id(String.valueOf(addr.getId()))
                .receiver(addr.getReceiver())
                .contact(addr.getContact())
                .provinceCode(addr.getProvinceCode())
                .cityCode(addr.getCityCode())
                .countyCode(addr.getCountyCode())
                .fullLocation(addr.getFullLocation())
                .address(addr.getAddress())
                .postalCode(addr.getPostalCode())
                .addressTags(addr.getAddressTags())
                .isDefault(addr.getIsDefault())
                .build();
    }
}
