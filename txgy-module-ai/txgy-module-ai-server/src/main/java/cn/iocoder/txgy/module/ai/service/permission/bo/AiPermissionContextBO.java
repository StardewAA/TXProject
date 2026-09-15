package cn.iocoder.txgy.module.ai.service.permission.bo;

import cn.iocoder.txgy.module.ai.dal.dataobject.permission.AiChatRolePermissionDO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI 权限上下文 BO
 *
 * <p>作用：一次 AI 对话中「当前用户到底能看什么」的完整快照。
 * 它有两个用途：
 * 1. 交给权限校验层做拦截判断（后端规则校验，不依赖大模型）
 * 2. 渲染成 SystemMessage 注入给大模型，让模型知道自己能答什么
 *
 * @author 天信管业
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPermissionContextBO {

    /**
     * 用户编号
     */
    private Long userId;

    /**
     * 租户编号（多租户隔离，确保 AI 只能查本租户数据）
     */
    private Long tenantId;

    /**
     * 用户绑定的 AI 角色编号集合
     */
    private List<Long> roleIds;

    /**
     * 用户绑定的 AI 角色名称集合（用于 Prompt 展示）
     */
    private List<String> roleNames;

    /**
     * 用户拥有的业务域权限：key = 业务域编码，value = 权限配置
     */
    private Map<String, AiChatRolePermissionDO> permissionMap;

    /**
     * 允许查询的业务域名称集合（已过滤 canQuery = true）
     */
    private Set<String> allowedBizNames;

    /**
     * 允许访问敏感数据的业务域名称集合（已过滤 canSensitive = true）
     */
    private Set<String> sensitiveBizNames;

    /**
     * 数据范围：ALL=全部 / DEPT=本部门 / SELF=仅本人相关（取最宽松的一个）
     */
    private String dataScope;

    /**
     * 是否拥有任意权限（false 时直接拒绝所有业务数据查询）
     */
    private Boolean hasAnyPermission;
}
