package cn.iocoder.txgy.module.system.api.permission;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.system.api.permission.dto.PermissionEnsureRoleReqDTO;
import cn.iocoder.txgy.module.system.controller.admin.permission.vo.role.RoleSaveReqVO;
import cn.iocoder.txgy.module.system.enums.permission.RoleTypeEnum;
import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.txgy.module.system.service.permission.PermissionService;
import cn.iocoder.txgy.module.system.service.permission.RoleService;
import cn.iocoder.txgy.module.system.dal.dataobject.permission.RoleDO;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@RestController // 提供 RESTful API 接口，给 Feign 调用
@Validated
@Primary // 由于 PermissionCommonApi 的存在，必须声明为 @Primary Bean
public class PermissionApiImpl implements PermissionApi {

    @Resource
    private PermissionService permissionService;
    @Resource
    private RoleService roleService;

    @Override
    public CommonResult<Set<Long>> getUserRoleIdListByRoleIds(Collection<Long> roleIds) {
        return success(permissionService.getUserRoleIdListByRoleId(roleIds));
    }

    @Override
    public CommonResult<Boolean> hasAnyPermissions(Long userId, String... permissions) {
        return success(permissionService.hasAnyPermissions(userId, permissions));
    }

    @Override
    public CommonResult<Boolean> hasAnyRoles(Long userId, String... roles) {
        return success(permissionService.hasAnyRoles(userId, roles));
    }

    @Override
    public CommonResult<DeptDataPermissionRespDTO> getDeptDataPermission(Long userId) {
        return success(permissionService.getDeptDataPermission(userId));
    }

    @Override
    public CommonResult<Boolean> assignRoleByCode(Long userId, String roleCode) {
        // 角色不存在（例如该租户还没建「员工」角色）时静默跳过，不影响主流程
        RoleDO role = roleService.getRoleByCode(roleCode);
        if (role == null || role.getId() == null) {
            return success(false);
        }
        permissionService.assignUserRole(userId, Set.of(role.getId()));
        return success(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CommonResult<Boolean> ensureRoleAndGrant(PermissionEnsureRoleReqDTO request) {
        RoleDO role = roleService.getRoleByCode(request.roleCode());
        if (role == null) {
            RoleSaveReqVO createReq = new RoleSaveReqVO();
            createReq.setName(request.roleName());
            createReq.setCode(request.roleCode());
            createReq.setSort(90);
            createReq.setStatus(CommonStatusEnum.ENABLE.getStatus());
            createReq.setRemark("由业务模块自动建立，可在系统管理中调整权限");
            Long roleId = roleService.createRole(createReq, RoleTypeEnum.CUSTOM.getType());
            role = roleService.getRole(roleId);
        }

        Set<Long> menuIds = new HashSet<>(permissionService.getRoleMenuListByRoleId(Set.of(role.getId())));
        menuIds.addAll(request.menuIds());
        permissionService.assignRoleMenu(role.getId(), menuIds);

        Set<Long> roleIds = new HashSet<>(permissionService.getUserRoleIdListByUserId(request.userId()));
        roleIds.add(role.getId());
        permissionService.assignUserRole(request.userId(), roleIds);
        return success(true);
    }
    @Override
    public CommonResult<Boolean> revokeRoleByCode(Long userId, String roleCode) {
        // 角色不存在时无需回收
        RoleDO role = roleService.getRoleByCode(roleCode);
        if (role == null || role.getId() == null) {
            return success(true);
        }
        // 仅从用户现有角色中移除该角色，其余角色保持不动，避免误删 HR 等配置好的角色
        Set<Long> currentRoleIds = permissionService.getUserRoleIdListByUserId(userId);
        if (CollUtil.isEmpty(currentRoleIds) || !currentRoleIds.contains(role.getId())) {
            return success(true);
        }
        Set<Long> remainRoleIds = new HashSet<>(currentRoleIds);
        remainRoleIds.remove(role.getId());
        permissionService.assignUserRole(userId, remainRoleIds);
        return success(true);
    }

}
