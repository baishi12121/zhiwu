package com.hyf.malluserservice.controller;

import com.hyf.mallcommon.core.result.Result;
import com.hyf.malluserservice.dto.request.AddressSaveRequest;
import com.hyf.malluserservice.dto.response.AddressResponse;
import com.hyf.malluserservice.service.AddressService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 收货地址控制器。
 *
 * @author hyf
 */
@Slf4j
@RestController
@RequestMapping("/user/addresses")
@RequiredArgsConstructor
public class AddressController {

    private final AddressService addressService;

    /**
     * 创建收货地址。
     */
    @PostMapping
    public Result<AddressResponse> create(@RequestBody AddressSaveRequest req) {
        return Result.success(addressService.createAddress(req));
    }

    /**
     * 获取当前用户的所有收货地址。
     */
    @GetMapping
    public Result<List<AddressResponse>> list() {
        return Result.success(addressService.listAddresses());
    }

    /**
     * 获取地址详情。
     */
    @GetMapping("/{id}")
    public Result<AddressResponse> getById(@PathVariable Long id) {
        return Result.success(addressService.getAddress(id));
    }

    /**
     * 修改收货地址。
     */
    @PutMapping("/{id}")
    public Result<AddressResponse> update(@PathVariable Long id, @RequestBody AddressSaveRequest req) {
        return Result.success(addressService.updateAddress(id, req));
    }

    /**
     * 删除收货地址。
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        addressService.deleteAddress(id);
        return Result.success(null);
    }
}
