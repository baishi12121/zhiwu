package com.hyf.malladminservice.service;

import com.hyf.malladminservice.dto.request.SeckillActivitySaveRequest;
import com.hyf.malladminservice.dto.request.SeckillItemSaveRequest;
import com.hyf.malladminservice.entity.SeckillActivity;
import com.hyf.malladminservice.entity.SeckillItem;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;
import java.util.List;

public interface AdminSeckillService {

    public PageResult<SeckillActivity> listActivities(PageQuery query, Integer enabled);
    public SeckillActivity getActivity(Long id);
    public Long createActivity(SeckillActivitySaveRequest req);
    public void updateActivity(Long id, SeckillActivitySaveRequest req);
    public void updateActivityEnabled(Long id, Integer enabled);
    public void deleteActivity(Long id);
    public List<SeckillItem> listItems(Long activityId);
    public Long addItem(Long activityId, SeckillItemSaveRequest req);
    public void updateItem(Long itemId, SeckillItemSaveRequest req);
    public void updateItemStatus(Long itemId, Integer status);
    public void deleteItem(Long itemId);

}
