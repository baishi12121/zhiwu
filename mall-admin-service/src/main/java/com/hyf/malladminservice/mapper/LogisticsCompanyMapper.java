package com.hyf.malladminservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.malladminservice.entity.LogisticsCompany;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 快递公司 Mapper。
 */
@Mapper
public interface LogisticsCompanyMapper extends BaseMapper<LogisticsCompany> {

    @Select("SELECT id, name, code, tel, sort_order FROM logistics_company ORDER BY sort_order ASC, id ASC")
    List<LogisticsCompany> listAll();
}
