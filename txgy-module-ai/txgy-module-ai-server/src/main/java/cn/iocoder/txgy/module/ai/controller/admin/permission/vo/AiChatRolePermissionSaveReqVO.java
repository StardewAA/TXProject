package cn.iocoder.txgy.module.ai.controller.admin.permission.vo;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * AI 聊天角色权限配置 - 保存 Request VO
 *
 * <p>一次提交 = 一个角色的完整权限配置（业务域权限列表 + 绑定用户列表），
 * 后端采用「先删后插」整体覆盖保存，保证与前端提交完全一致。
 *
 * @author 天信管业
 */
@Data
public class AiChatRolePermissionSaveReqVO {

    /**
     * AI 聊天角色编号
     */
    @NotNull(message = "角色编号不能为空")
    private Long roleId;

    /**
     * 业务域权限配置列表
     */
    @NotEmpty(message = "权限配置不能为空")
    @Valid
    private List<Permission> permissions;

    /**
     * 绑定的用户编号列表（可空，为空表示清空绑定）
     */
    private List<Long> userIds;

    /**
     * 单个业务域的权限配置
     */
    @Data
    public static class Permission {

        /**
         * MES 业务域编码，枚举 {@link cn.iocoder.txgy.module.ai.enums.permission.AiMesBizTypeEnum}
         */
        @NotNull(message = "业务域不能为空")
        private String bizType;

        /**
         * 是否允许查询该业务域数据
         */
        private Boolean canQuery;

        /**
         * 是否允许访问敏感生产数据
         */
        private Boolean canSensitive;

        /**
         * 可查询的单据类型列表，JSON 数组字符串，空表示不限
         */
        private String billTypes;

        /**
         * 数据范围：ALL / DEPT / SELF
         */
        private String dataScope;

        /**
         * 备注
         */
        private String remark;
    }

}
