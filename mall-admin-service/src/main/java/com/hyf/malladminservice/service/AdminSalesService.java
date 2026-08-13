package com.hyf.malladminservice.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface AdminSalesService {

    public Map<String, Object> overview();
    public List<Map<String, Object>> productRanking(Integer limit);
    public List<Map<String, Object>> categoryDistribution();
    public List<Map<String, Object>> dailyTrend(LocalDate startDate, LocalDate endDate);

}
