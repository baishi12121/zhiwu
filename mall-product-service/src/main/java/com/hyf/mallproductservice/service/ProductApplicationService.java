package com.hyf.mallproductservice.service;

import com.hyf.mallproductservice.entity.SkuVO;
import com.hyf.mallproductservice.entity.SpecVO;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import java.util.List;
import java.util.Map;

public interface ProductApplicationService {

    public PageResult<Map<String, Object>> getProductList(PageQuery query, Long categoryId, String keyword, String sort);
    public Map<String, Object> getProductDetail(Long id);
    public List<SkuVO> getSkuVOs(Long productId);
    public List<SpecVO> getSpecVOs(Long productId);
    public Map<String, Object> getStock(Long productId);

}
