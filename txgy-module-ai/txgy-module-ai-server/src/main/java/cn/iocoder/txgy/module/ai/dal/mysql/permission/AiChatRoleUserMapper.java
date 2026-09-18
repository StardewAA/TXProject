package cn.iocoder.txgy.module.ai.dal.mysql.permission;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.ai.dal.dataobject.permission.AiChatRoleUserDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * AI 聊天角色 - 用户关联 Mapper
 *
 * @author 天信管业
 */
@Mapper
public interface AiChatRoleUserMapper extends BaseMapperX<AiChatRoleUserDO> {

    /**
     * 获得指定用户绑定的全部角色关联记录
     *
     * @param userId 用户编号
     * @return 关联记录列表
     */
    default List<AiChatRoleUserDO> selectListByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<AiChatRoleUserDO>()
                .eq(AiChatRoleUserDO::getUserId, userId)
                .orderByAsc(AiChatRoleUserDO::getId));
    }

    /**
     * 获得指定角色关联的全部用户
     *
     * @param roleId 角色编号
     * @return 关联记录列表
     */
    default List<AiChatRoleUserDO> selectListByRoleId(Long roleId) {
        return selectList(new LambdaQueryWrapperX<AiChatRoleUserDO>()
                .eq(AiChatRoleUserDO::getRoleId, roleId)
                .orderByAsc(AiChatRoleUserDO::getId));
    }

    /**
     * 批量获得多个用户绑定的角色关联记录
     *
     * @param userIds 用户编号集合
     * @return 关联记录列表
     */
    default List<AiChatRoleUserDO> selectListByUserIds(Collection<Long> userIds) {
        return selectList(new LambdaQueryWrapperX<AiChatRoleUserDO>()
                .in(AiChatRoleUserDO::getUserId, userIds)
                .orderByAsc(AiChatRoleUserDO::getId));
    }

    /**
     * 判断指定用户是否已绑定该角色
     *
     * @param roleId 角色编号
     * @param userId 用户编号
     * @return 已绑定返回关联记录，否则 null
     */
    default AiChatRoleUserDO selectByRoleIdAndUserId(Long roleId, Long userId) {
        return selectOne(new LambdaQueryWrapperX<AiChatRoleUserDO>()
                .eq(AiChatRoleUserDO::getRoleId, roleId)
                .eq(AiChatRoleUserDO::getUserId, userId));
    }

    /**
     * 物理删除指定角色的全部用户关联
     *
     * @param roleId 角色编号
     */
    default void deleteByRoleId(Long roleId) {
        delete(new LambdaQueryWrapperX<AiChatRoleUserDO>()
                .eq(AiChatRoleUserDO::getRoleId, roleId));
    }

}
