package cn.iocoder.txgy.module.hrm.job.recruit;

import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.hrm.service.recruit.chat.HrmRecruitChatService;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/** 招聘沟通等待回复超时处理 Job。 */
@Component
public class HrmRecruitChatTimeoutJob {

    @Resource
    private HrmRecruitChatService chatService;

    @XxlJob("hrmRecruitChatTimeoutJob")
    @TenantJob
    public String execute() {
        int expiredCount = chatService.expireWaitingConversations(LocalDateTime.now());
        return String.format("HRM 招聘沟通超时处理：失败 %s 条", expiredCount);
    }
}
