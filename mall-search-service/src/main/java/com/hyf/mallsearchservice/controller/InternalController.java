package com.hyf.mallsearchservice.controller;

import com.hyf.mallcommon.core.result.Result;
import com.hyf.mallsearchservice.service.SyncService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 搜索服务内部接口 — 全量重建.
 *
 * <p>路径 {@code /search/internal/**},经网关需 token(运维/管理员触发);
 * 后续 Phase B 增量同步也挂在此 controller。
 *
 * @author hyf
 */
@RestController
@RequestMapping("/search/internal")
public class InternalController {

    @Autowired
    private SyncService syncService;

    /**
     * 全量重建:删索引→建索引→扫表 bulk index.
     * <p>幂等:可重复调用,每次都重建.
     */
    @PostMapping("/reindex")
    public Result<Map<String, Object>> reindex() {
        long count = syncService.fullSync();
        return Result.success(Map.of("synced", count));
    }
}
