package com.hyf.malluserservice.mapper;

import com.hyf.malluserservice.entity.UserToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户登录会话 Mapper
 */
@Mapper
public interface UserTokenMapper {

    /**
     * 插入一条登录会话
     *
     * @param userToken 会话实体
     * @return 影响行数
     */
    int insert(UserToken userToken);

    /**
     * 根据 token 查询会话
     *
     * @param token 登录凭证
     * @return 会话实体，未找到返回 null
     */
    UserToken selectByToken(@Param("token") String token);

    /**
     * 根据 token 删除会话（用于退出登录）
     *
     * @param token 登录凭证
     * @return 影响行数
     */
    int deleteByToken(@Param("token") String token);

    /**
     * 根据 userId 删除该用户全部会话（强制下线）
     *
     * @param userId 用户ID
     * @return 影响行数
     */
    int deleteByUserId(@Param("userId") Long userId);
}
