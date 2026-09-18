package cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.chat;

import cn.iocoder.txgy.framework.tenant.core.db.TenantBaseDO;
import com.baomidou.mybatisplus.annotation.KeySequence;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** HRM 招聘沟通消息。 */
@TableName("hrm_recruit_chat_message")
@KeySequence("hrm_recruit_chat_message_seq")
@Data
@EqualsAndHashCode(callSuper = true)
public class HrmRecruitChatMessageDO extends TenantBaseDO {
    @TableId
    private Long id;
    private Long conversationId;
    private Long senderUserId;
    /** 1 文本，2 简历 */
    private Integer contentType;
    private String content;
    private LocalDateTime sendTime;
}
