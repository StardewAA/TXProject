package cn.iocoder.txgy.module.ai.dal.mysql.permission;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.ai.dal.dataobject.permission.AiChatRolePermissionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.Collection;
import java.util.List;

/**
 * AI 聊天角色权限配置 Mapper
 *
 * @author 天信管业
 */
@Mapper
public interface AiChatRolePermissionMapper extends BaseMapperX<AiChatRolePermissionDO> {

    /**
     * 获得指定角色的全部权限配置
     *
     * @param roleId 角色编号
     * @return 权限配置列表
     */
    default List<AiChatRolePermissionDO> selectListByRoleId(Long roleId) {
        return selectList(new LambdaQueryWrapperX<AiChatRolePermissionDO>()
                .eq(AiChatRolePermissionDO::getRoleId, roleId)
                .orderByAsc(AiChatRolePermissionDO::getId));
    }

    /**
     * 批量获得多个角色的权限配置
     *
     * @param roleIds 角色编号集合
     * @return 权限配置列表
     */
    default List<AiChatRolePermissionDO> selectListByRoleIds(Collection<Long> roleIds) {
        return selectList(new LambdaQueryWrapperX<AiChatRolePermissionDO>()
                .in(AiChatRolePermissionDO::getRoleId, roleIds)
                .orderByAsc(AiChatRolePermissionDO::getId));
    }

    /**
     * 获得指定角色 + 指定业务域的权限配置
     *
     * @param roleId  角色编号
     * @param bizType 业务域编码
     * @return 权限配置，不存在返回 null
     */
    default AiChatRolePermissionDO selectByRoleIdAndBizType(Long roleId, String bizType) {
        return selectOne(new LambdaQueryWrapperX<AiChatRolePermissionDO>()
                .eq(AiChatRolePermissionDO::getRoleId, roleId)
                .eq(AiChatRolePermissionDO::getBizType, bizType));
    }

    /**
     * 物理删除指定角色的全部权限配置（保存配置时先清后插，保证与前端提交一致）
     *
     * @param roleId 角色编号
     */
    default void deleteByRoleId(Long roleId) {
        delete(new LambdaQueryWrapperX<AiChatRolePermissionDO>()
                .eq(AiChatRolePermissionDO::getRoleId, roleId));
    }

}
