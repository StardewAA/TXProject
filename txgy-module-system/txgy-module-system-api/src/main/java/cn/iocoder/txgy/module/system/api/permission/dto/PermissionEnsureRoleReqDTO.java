package cn.iocoder.txgy.module.system.api.permission.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/** 创建或复用角色，并把角色追加给用户。 */
public record PermissionEnsureRoleReqDTO(
        @NotNull Long userId,
        @NotBlank String roleCode,
        @NotBlank String roleName,
        @NotEmpty Set<Long> menuIds) {
}
