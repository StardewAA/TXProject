package cn.iocoder.txgy.module.system.api.permission;

import cn.iocoder.txgy.framework.common.biz.system.permission.PermissionCommonApi;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.system.enums.ApiConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

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

}
