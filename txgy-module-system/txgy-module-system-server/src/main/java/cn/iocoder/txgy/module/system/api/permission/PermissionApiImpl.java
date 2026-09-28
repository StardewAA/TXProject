package cn.iocoder.txgy.module.system.api.permission;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.biz.system.permission.dto.DeptDataPermissionRespDTO;
import cn.iocoder.txgy.module.system.service.permission.PermissionService;
import cn.iocoder.txgy.module.system.service.permission.RoleService;
import cn.iocoder.txgy.module.system.dal.dataobject.permission.RoleDO;
import org.springframework.context.annotation.Primary;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import java.util.Collection;
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

}
