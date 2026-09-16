package cn.iocoder.txgy.module.ai.service.permission;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.module.ai.controller.admin.permission.vo.AiChatRolePermissionSaveReqVO;
import cn.iocoder.txgy.module.ai.dal.dataobject.permission.AiChatRolePermissionDO;
import cn.iocoder.txgy.module.ai.dal.dataobject.permission.AiChatRoleUserDO;
import cn.iocoder.txgy.module.ai.dal.mysql.permission.AiChatRolePermissionMapper;
import cn.iocoder.txgy.module.ai.dal.mysql.permission.AiChatRoleUserMapper;
import cn.iocoder.txgy.module.ai.enums.permission.AiMesBizTypeEnum;
import cn.iocoder.txgy.module.ai.service.permission.bo.AiPermissionContextBO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 聊天角色权限配置 Service 实现类
 *
 * <p>负责两块数据的管理：
 * 1. ai_chat_role_permission：角色对 MES 业务域的授权（保存时整体覆盖）
 * 2. ai_chat_role_user：角色与用户的绑定关系
 *
 * @author 天信管业
 */
@Service
@Validated
@Slf4j
public class AiChatRolePermissionServiceImpl implements AiChatRolePermissionService {

    @Resource
    private AiChatRolePermissionMapper permissionMapper;
    @Resource
    private AiChatRoleUserMapper roleUserMapper;
    @Resource
    private AiPermissionGuard permissionGuard;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRolePermission(AiChatRolePermissionSaveReqVO saveReqVO) {
        Long roleId = saveReqVO.getRoleId();
        // 1. 覆盖保存业务域权限：先删后插，保证与前端提交完全一致
        permissionMapper.deleteByRoleId(roleId);
        if (CollUtil.isNotEmpty(saveReqVO.getPermissions())) {
            List<AiChatRolePermissionDO> permissions = saveReqVO.getPermissions().stream().map(item -> {
                AiMesBizTypeEnum biz = AiMesBizTypeEnum.getByType(item.getBizType());
                AiChatRolePermissionDO permission = new AiChatRolePermissionDO()
                        .setRoleId(roleId)
                        .setBizType(item.getBizType())
                        .setBizName(biz != null ? biz.getName() : item.getBizType())
                        .setCanQuery(Boolean.TRUE.equals(item.getCanQuery()))
                        .setCanSensitive(Boolean.TRUE.equals(item.getCanSensitive()))
                        .setBillTypes(item.getBillTypes())
                        .setDataScope(item.getDataScope())
                        .setRemark(item.getRemark());
                // 敏感权限必须以「允许查询」为前提，避免出现只开敏感不查数据的矛盾配置
                if (!Boolean.TRUE.equals(permission.getCanQuery())) {
                    permission.setCanSensitive(false);
                }
                return permission;
            }).collect(Collectors.toList());
            permissionMapper.insertBatch(permissions);
        }

        // 2. 覆盖保存用户绑定：先删后插
        roleUserMapper.deleteByRoleId(roleId);
        if (CollUtil.isNotEmpty(saveReqVO.getUserIds())) {
            List<AiChatRoleUserDO> binds = saveReqVO.getUserIds().stream()
                    .map(userId -> AiChatRoleUserDO.builder().roleId(roleId).userId(userId).build())
                    .collect(Collectors.toList());
            roleUserMapper.insertBatch(binds);
        }
        log.info("[updateRolePermission][角色({}) 权限配置已更新，业务域({})个，绑定用户({})个]",
                roleId, CollUtil.size(saveReqVO.getPermissions()), CollUtil.size(saveReqVO.getUserIds()));
    }

    @Override
    public List<AiChatRolePermissionDO> getRolePermissionList(Long roleId) {
        return permissionMapper.selectListByRoleId(roleId);
    }

    @Override
    public List<Long> getRoleUserIds(Long roleId) {
        return roleUserMapper.selectListByRoleId(roleId).stream()
                .map(AiChatRoleUserDO::getUserId).collect(Collectors.toList());
    }

    @Override
    public List<Long> getUserRoleIds(Long userId) {
        return roleUserMapper.selectListByUserId(userId).stream()
                .map(AiChatRoleUserDO::getRoleId).collect(Collectors.toList());
    }

    @Override
    public AiPermissionContextBO getPermissionContext(Long userId) {
        return permissionGuard.buildContext(userId);
    }

}
