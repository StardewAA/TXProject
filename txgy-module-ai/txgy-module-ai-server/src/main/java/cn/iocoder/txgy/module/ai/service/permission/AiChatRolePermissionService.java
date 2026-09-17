package cn.iocoder.txgy.module.ai.service.permission;

import cn.iocoder.txgy.module.ai.controller.admin.permission.vo.AiChatRolePermissionSaveReqVO;
import cn.iocoder.txgy.module.ai.dal.dataobject.permission.AiChatRolePermissionDO;
import cn.iocoder.txgy.module.ai.service.permission.bo.AiPermissionContextBO;

import java.util.List;

/**
 * AI 聊天角色权限配置 Service 接口
 *
 * @author 天信管业
 */
public interface AiChatRolePermissionService {

    /**
     * 保存角色的权限配置（业务域权限 + 绑定用户），整体覆盖
     *
     * @param saveReqVO 权限配置
     */
    void updateRolePermission(AiChatRolePermissionSaveReqVO saveReqVO);

    /**
     * 获得角色的权限配置列表
     *
     * @param roleId 角色编号
     * @return 权限配置列表
     */
    List<AiChatRolePermissionDO> getRolePermissionList(Long roleId);

    /**
     * 获得角色绑定的用户编号列表
     *
     * @param roleId 角色编号
     * @return 用户编号列表
     */
    List<Long> getRoleUserIds(Long roleId);

    /**
     * 获得用户绑定的角色编号列表
     *
     * @param userId 用户编号
     * @return 角色编号列表
     */
    List<Long> getUserRoleIds(Long userId);

    /**
     * 获得当前用户的权限上下文（供 AI 对话链路使用）
     *
     * @param userId 用户编号
     * @return 权限上下文
     */
    AiPermissionContextBO getPermissionContext(Long userId);

}
