package cn.iocoder.txgy.module.ai.dal.dataobject.permission;

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

/**
 * AI 聊天角色 - 用户关联 DO
 *
 * <p>决定「哪个用户可以使用哪个 AI 角色」，用户通过角色间接继承 MES 数据权限。
 * 一个用户可以绑定多个角色，一个角色也可以授权给多个用户。
 *
 * @author 天信管业
 */
@TableName("ai_chat_role_user")
@KeySequence("ai_chat_role_user_seq")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRoleUserDO extends BaseDO {

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
     * 用户编号
     *
     * 关联 AdminUserDO 的 id 字段
     */
    private Long userId;

}
