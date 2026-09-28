package cn.iocoder.txgy.module.ai.controller.admin.permission;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.ai.controller.admin.permission.vo.AiChatRolePermissionSaveReqVO;
import cn.iocoder.txgy.module.ai.enums.permission.AiMesBizTypeEnum;
import cn.iocoder.txgy.module.ai.service.permission.AiChatRolePermissionService;
import cn.iocoder.txgy.module.ai.service.permission.bo.AiPermissionContextBO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;

/**
 * AI 聊天角色权限配置 Controller
 *
 * <p>提供三类接口：
 * 1. 控制台配置：查询/保存「角色 - 业务域权限 - 绑定用户」；
 * 2. 业务域字典：供前端权限配置页面渲染全部可配置的业务域；
 * 3. 对话侧查询：当前登录用户的权限上下文（前端对话页展示「我能查什么」）。
 *
 * @author 天信管业
 */
@Tag(name = "管理后台 - AI 聊天角色权限配置")
@RestController
@RequestMapping("/ai/chat-role-permission")
@Validated
public class AiChatRolePermissionController {

    @Resource
    private AiChatRolePermissionService rolePermissionService;

    @GetMapping("/get")
    @Operation(summary = "获得角色的权限配置")
    @Parameter(name = "roleId", description = "角色编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('ai:chat-role-permission:query')")
    public CommonResult<Map<String, Object>> getRolePermission(@RequestParam("roleId") Long roleId) {
        Map<String, Object> result = new HashMap<>();
        // 业务域权限配置
        result.put("permissions", rolePermissionService.getRolePermissionList(roleId));
        // 绑定的用户编号列表
        result.put("userIds", rolePermissionService.getRoleUserIds(roleId));
        return success(result);
    }

    @PutMapping("/update")
    @Operation(summary = "保存角色的权限配置", description = "整体覆盖：业务域权限 + 绑定用户")
    @PreAuthorize("@ss.hasPermission('ai:chat-role-permission:update')")
    public CommonResult<Boolean> updateRolePermission(@Valid @RequestBody AiChatRolePermissionSaveReqVO saveReqVO) {
        rolePermissionService.updateRolePermission(saveReqVO);
        return success(true);
    }

    @GetMapping("/biz-types")
    @Operation(summary = "获得全部 MES 业务域", description = "供权限配置页面渲染业务域清单")
    @PreAuthorize("@ss.hasPermission('ai:chat-role-permission:query')")
    public CommonResult<List<Map<String, Object>>> getBizTypes() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (AiMesBizTypeEnum biz : AiMesBizTypeEnum.values()) {
            Map<String, Object> item = new HashMap<>();
            item.put("type", biz.getType());
            item.put("name", biz.getName());
            item.put("toolName", biz.getToolName());
            list.add(item);
        }
        return success(list);
    }

    @GetMapping("/my")
    @Operation(summary = "获得当前用户的 AI 权限上下文", description = "AI 对话页展示「我能查什么」")
    public CommonResult<AiPermissionContextBO> getMyPermissionContext() {
        return success(rolePermissionService.getPermissionContext(getLoginUserId()));
    }

}
