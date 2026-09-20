package cn.iocoder.txgy.module.ai.dal.dataobject.permission;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 聊天角色 - MES 业务权限配置 DO
 *
 * <p>一条记录 = 某个 AI 角色对某个 MES 业务域的授权情况。
 * 用户使用 AI 时，先找到其绑定的角色，再根据本表的配置决定能查什么。
 *
 * @author 天信管业
 */
@TableName("ai_chat_role_permission")
@KeySequence("ai_chat_role_permission_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRolePermissionDO extends BaseDO {

    /**
     * 编号
     */
    @TableId
    private Long id;

    /**
     * AI 聊天角色编号
     *
     * 关联 {@link cn.iocoder.txgy.module.ai.dal.dataobject.model.AiChatRoleDO#getId()}
     */
    private Long roleId;

    /**
     * MES 业务域编码
     *
     * 枚举 {@link cn.iocoder.txgy.module.ai.enums.permission.AiMesBizTypeEnum#getType()}
     */
    private String bizType;

    /**
     * 业务域名称（冗余，便于列表展示）
     */
    private String bizName;

    /**
     * 是否允许查询该业务域数据
     */
    private Boolean canQuery;

    /**
     * 是否允许访问敏感生产数据（成本、售价、客户联系方式、工艺配方等）
     */
    private Boolean canSensitive;

    /**
     * 可查询的单据类型列表（JSON 数组字符串），为空表示不限单据
     */
    private String billTypes;

    /**
     * 数据范围：ALL=全部 / DEPT=本部门 / SELF=仅本人相关
     */
    private String dataScope;

    /**
     * 备注
     */
    private String remark;

}
