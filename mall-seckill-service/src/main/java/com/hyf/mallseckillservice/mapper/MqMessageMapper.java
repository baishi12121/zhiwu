package com.hyf.mallseckillservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallseckillservice.entity.MqMessageDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 本地消息表 Mapper。
 *
 * <p>负责维护秒杀异步链路状态机，状态更新必须保持单调，避免 MQ ACK 和消费者提交时序竞争。</p>
 */
@Mapper
public interface MqMessageMapper extends BaseMapper<MqMessageDO> {

    /**
     * 状态单调前进更新：低状态可以变成高状态，高状态不能被旧事件覆盖。
     */
    int updateStatusByMessageId(@Param("messageId") String messageId, @Param("status") int status);

    /**
     * 设置下一次重投时间和已重试次数，防止刚发送的消息被定时任务立即扫到。
     */
    int markRetry(@Param("messageId") String messageId,
                  @Param("retryCount") int retryCount,
                  @Param("nextRetryTime") LocalDateTime nextRetryTime);

    /**
     * 仅把发送失败状态重新打开为待发送，用于用户重复入口自愈。
     */
    int resetFailedToSending(@Param("messageId") String messageId);

    /**
     * 扫描已到重试时间的待发送消息，交给定时任务重新投递 MQ。
     */
    List<MqMessageDO> selectPendingSendForRetry(@Param("limit") int limit);

    int countPendingSendDue();

    List<MqMessageDO> selectSentWithoutOrder(@Param("limit") int limit);
}
