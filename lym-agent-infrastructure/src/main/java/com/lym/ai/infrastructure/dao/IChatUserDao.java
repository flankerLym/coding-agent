package com.lym.ai.infrastructure.dao;

import com.lym.ai.infrastructure.dao.po.ChatUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * chat_user DAO
 *
 * @author lym
 */
@Mapper
public interface IChatUserDao {

    ChatUser queryByUsername(@Param("tenantId") String tenantId,
                             @Param("username") String username);

    ChatUser queryByUserId(@Param("userId") String userId);

    int updateLastLoginTime(@Param("userId") String userId);

}
