package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malladminservice.entity.AdminProduct;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品 SPU Mapper（管理后台用）。
 *
 * <p>列表/筛选/分页直接使用 MyBatis-Plus 的 {@link com.baomidou.mybatisplus.extension.service.IService}
 * 配合 LambdaQueryWrapper；详情通过 selectById + 子表 mapper 拼装。
 *
 * @author hyf
 */
@Mapper
public interface AdminProductMapper extends BaseMapper<AdminProduct> {
}
