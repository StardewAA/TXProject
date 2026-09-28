package cn.iocoder.txgy.module.system.api.permission;

import cn.iocoder.txgy.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.system.api.permission.dto.PermissionEnsureRoleReqDTO;
import cn.iocoder.txgy.module.system.enums.ApiConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import jakarta.validation.Valid;
import java.util.Collection;
import java.util.Set;

@FeignClient(name = ApiConstants.NAME) // TODO 芋艿：fallbackFactory =
@Tag(name = "RPC 服务 - 权限")
public interface PermissionApi extends PermissionCommonApi {

    String PREFIX = ApiConstants.PREFIX + "/permission";

    @GetMapping(PREFIX + "/user-role-id-list-by-role-id")
    @Operation(summary = "获得拥有多个角色的用户编号集合")
    @Parameter(name = "roleIds", description = "角色编号集合", example = "1,2", required = true)
    CommonResult<Set<Long>> getUserRoleIdListByRoleIds(@RequestParam("roleIds") Collection<Long> roleIds);

    /**
     * 按角色标识给用户分配角色（用于 HRM 员工自动开户时默认分配「员工」角色）
     *
     * @param userId   用户编号
     * @param roleCode 角色标识，例如 employee_role_2020
     * @return 是否分配成功；角色不存在时返回 false，不抛异常
     */
    @PostMapping(PREFIX + "/assign-role-by-code")
    @Operation(summary = "按角色标识给用户分配角色")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "1024")
    @Parameter(name = "roleCode", description = "角色标识", required = true, example = "employee_role_2020")
    CommonResult<Boolean> assignRoleByCode(@RequestParam("userId") Long userId,
                                           @RequestParam("roleCode") String roleCode);

    /**
     * 创建或复用可在“系统管理 - 角色管理”中维护的自定义角色，追加权限并分配给用户。
     * 已有角色和已有菜单权限均会保留。
     */
    @PostMapping(PREFIX + "/ensure-role-and-grant")
    @Operation(summary = "确保角色存在并追加分配给用户")
    CommonResult<Boolean> ensureRoleAndGrant(@Valid @RequestBody PermissionEnsureRoleReqDTO request);
    /**
     * 按角色标识回收用户身上的某个角色（用于 HRM 员工离职时回收「员工」角色）
     *
     * <p>只移除指定角色，用户的其他角色保持不变；角色不存在或用户本来就没有该角色时直接返回 true。</p>
     *
     * @param userId   用户编号
     * @param roleCode 角色标识，例如 employee_role_2020
     * @return 是否处理成功
     */
    @PostMapping(PREFIX + "/revoke-role-by-code")
    @Operation(summary = "按角色标识回收用户角色")
    @Parameter(name = "userId", description = "用户编号", required = true, example = "1024")
    @Parameter(name = "roleCode", description = "角色标识", required = true, example = "employee_role_2020")
    CommonResult<Boolean> revokeRoleByCode(@RequestParam("userId") Long userId,
                                           @RequestParam("roleCode") String roleCode);

}
