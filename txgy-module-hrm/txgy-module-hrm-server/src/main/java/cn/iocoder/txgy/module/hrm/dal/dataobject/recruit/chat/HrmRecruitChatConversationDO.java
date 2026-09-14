package cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** HRM 招聘沟通会话。 */
@TableName("hrm_recruit_chat_conversation")
@KeySequence("hrm_recruit_chat_conversation_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitChatConversationDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long postId;
    private Long hrUserId;
    private Long candidateUserId;
    /** 正式投递后绑定的候选人编号。 */
    private Long candidateId;
    private String lastContent;
    private Long lastSenderUserId;
    private LocalDateTime lastTime;
    private Integer candidateUnread;
    private Integer hrUnread;
    /** 会话状态：0 待发起，1 等待回复，2 招聘方已回复，3 沟通失败 */
    private Integer status;
    /** 投递状态：待投递、已投递；招聘方回复前为空。 */
    private String applicationStatus;
    /** 正式应聘记录编号。 */
    private Long applicationId;
    private LocalDateTime hrReplyTime;
    private LocalDateTime endTime;
    private String endReason;
}
