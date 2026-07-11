package com.hyf.mallauthservice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hyf.mallauthservice.entity.UserAuth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户登录凭证 Mapper。
 *
 * @author hyf
 */
@Mapper
public interface UserAuthMapper extends BaseMapper<UserAuth> {

    /**
     * 按认证类型 + 标识查询凭证。
     *
     * @param identityType 认证类型（USERNAME / PHONE / WECHAT）
     * @param identifier   认证标识
     * @return 凭证实体，未找到返回 null
     */
    @Select("SELECT * FROM user_auth WHERE identity_type = #{identityType} AND identifier = #{identifier}")
    UserAuth selectByIdentity(@Param("identityType") String identityType,
                              @Param("identifier") String identifier);

    /**
     * 按用户 ID 和认证类型查询。
     *
     * @param userId       用户 ID
     * @param identityType 认证类型
     * @return 凭证实体，未找到返回 null
     */
    @Select("SELECT * FROM user_auth WHERE user_id = #{userId} AND identity_type = #{identityType}")
    UserAuth selectByUserIdAndType(@Param("userId") Long userId,
                                   @Param("identityType") String identityType);
}
