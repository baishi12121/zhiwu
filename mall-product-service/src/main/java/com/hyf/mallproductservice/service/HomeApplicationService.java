package com.hyf.mallproductservice.service;

import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import java.util.List;
import java.util.Map;

public interface HomeApplicationService {

    public List<Map<String, Object>> getBanners(Integer distributionSite);
    public List<Map<String, Object>> getCategoryMutli();
    public List<Map<String, Object>> getHotMutli();
    public List<Map<String, Object>> getHomeSeckill();
    public PageResult<Map<String, Object>> getGuessLike(PageQuery query);
    public Map<String, Object> getHotActivity(String activityKey, PageQuery query, String subType);

}
