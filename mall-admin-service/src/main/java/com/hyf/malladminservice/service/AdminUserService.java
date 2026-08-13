package com.hyf.malladminservice.service;

import com.hyf.malladminservice.entity.AdminUser;
import com.hyf.mallcommon.core.page.PageQuery;
import com.hyf.mallcommon.core.page.PageResult;

public interface AdminUserService {

    public PageResult<AdminUser> listUsers(PageQuery query, String keyword, Integer status, String memberLevel);
    public AdminUser getUser(Long id);
    public void updateStatus(Long id, Integer status);
    public void updateMemberLevel(Long id, String memberLevel);

}
