package com.hyf.malluserservice.controller;

import com.hyf.mallcommon.core.result.Result;
import com.hyf.malluserservice.dto.request.CartAddRequest;
import com.hyf.malluserservice.dto.request.CartSelectedRequest;
import com.hyf.malluserservice.dto.request.CartUpdateRequest;
import com.hyf.malluserservice.dto.response.CartItemResponse;
import com.hyf.malluserservice.service.CartService;
import jakarta.validation.Valid;
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
 * 购物车控制器。
 *
 * <p>路径前缀 {@code /cart}，由网关 {@code /cart/**} 路由到 mall-user-service。
 * 所有接口需登录鉴权（网关白名单不含 {@code /cart/**}）。
 *
 * <p>接口清单：
 * <pre>
 * POST   /cart                  加车
 * GET    /cart                  购物车列表
 * PUT    /cart/{skuId}          改数量
 * PUT    /cart/{skuId}/selected 选中/取消（单品）
 * GET    /cart/selected         已选列表（下单用）
 * PUT    /cart/selected         全选/取消全选
 * DELETE /cart/{skuId}          删除单项
 * DELETE /cart                  清空
 * </pre>
 *
 * @author hyf
 */
@Slf4j
@RestController
@RequestMapping("/cart")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    /**
     * 加入购物车。
     */
    @PostMapping
    public Result<Void> add(@RequestBody @Valid CartAddRequest req) {
        cartService.addCart(req);
        return Result.success();
    }

    /**
     * 购物车列表。
     */
    @GetMapping
    public Result<List<CartItemResponse>> list() {
        return Result.success(cartService.listCart());
    }

    /**
     * 修改数量。
     */
    @PutMapping("/{skuId}")
    public Result<Void> updateCount(@PathVariable Long skuId,
                                    @RequestBody @Valid CartUpdateRequest req) {
        cartService.updateCount(skuId, req);
        return Result.success();
    }

    /**
     * 选中/取消选中（单品）。
     */
    @PutMapping("/{skuId}/selected")
    public Result<Void> updateSelected(@PathVariable Long skuId,
                                       @RequestBody CartSelectedRequest req) {
        cartService.updateSelected(skuId, req);
        return Result.success();
    }

    /**
     * 已选购物车列表（下单用）。
     *
     * <p>Spring MVC 优先匹配字面量路径 {@code /selected}，不会被 {@code /{skuId}} 捕获。
     */
    @GetMapping("/selected")
    public Result<List<CartItemResponse>> listSelected() {
        return Result.success(cartService.listSelected());
    }

    /**
     * 全选/取消全选。
     */
    @PutMapping("/selected")
    public Result<Void> selectAll(@RequestBody CartSelectedRequest req) {
        cartService.selectAll(req);
        return Result.success();
    }

    /**
     * 删除单项。
     */
    @DeleteMapping("/{skuId}")
    public Result<Void> deleteItem(@PathVariable Long skuId) {
        cartService.deleteItem(skuId);
        return Result.success();
    }

    /**
     * 清空购物车。
     */
    @DeleteMapping
    public Result<Void> clearCart() {
        cartService.clearCart();
        return Result.success();
    }
}
