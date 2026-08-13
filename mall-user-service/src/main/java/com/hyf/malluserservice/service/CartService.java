package com.hyf.malluserservice.service;

import com.hyf.malluserservice.dto.request.CartAddRequest;
import com.hyf.malluserservice.dto.request.CartSelectedRequest;
import com.hyf.malluserservice.dto.request.CartUpdateRequest;
import com.hyf.malluserservice.dto.response.CartItemResponse;
import java.util.List;

public interface CartService {

    public List<CartItemResponse> listCart();
    public List<CartItemResponse> listSelected();
    public void addCart(CartAddRequest req);
    public void updateCount(Long skuId, CartUpdateRequest req);
    public void updateSelected(Long skuId, CartSelectedRequest req);
    public void selectAll(CartSelectedRequest req);
    public void deleteItem(Long skuId);
    public void clearCart();

}
