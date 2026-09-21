package cn.iocoder.txgy.module.hrm.job.recruit;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.job.TenantJob;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitInterviewDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.candidate.HrmRecruitInterviewMapper;
import cn.iocoder.txgy.module.hrm.enums.MessageTemplateConstants;
import cn.iocoder.txgy.module.hrm.enums.recruit.candidate.HrmRecruitInterviewResultEnum;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.system.api.mail.MailSendApi;
import cn.iocoder.txgy.module.system.api.mail.dto.MailSendToAddressesReqDTO;
import cn.iocoder.txgy.module.system.api.notify.NotifyMessageSendApi;
import cn.iocoder.txgy.module.system.api.notify.dto.NotifySendSingleToUserReqDTO;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * HRM 面试前提醒 Job
 *
 * <p>扫描"未来 N 小时内即将开始、且尚未出结果"的面试：
 * 给候选人发提醒邮件（候选人没有后台账号，站内信收不到）；给面试官发站内信。
 * 默认提前 24 小时（handler 参数可覆盖）。</p>
 *
 * <p>XXL-Job handler 名：hrmInterviewReminderJob</p>
 *
 * @author OPENLAB BS
 */
@Component
@Slf4j
public class HrmRecruitInterviewReminderJob {

    private static final int DEFAULT_REMIND_HOURS = 24;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Resource
    private HrmRecruitInterviewMapper recruitInterviewMapper;
    @Resource
    private HrmRecruitCandidateService recruitCandidateService;
    @Resource
    private HrmEmployeeService employeeService;
    @Resource
    private MailSendApi mailSendApi;
    @Resource
    private NotifyMessageSendApi notifyMessageSendApi;

    @XxlJob("hrmInterviewReminderJob")
    @TenantJob
    public String execute() {
        String param = XxlJobHelper.getJobParam();
        int remindHours = StrUtil.isBlank(param) ? DEFAULT_REMIND_HOURS : Integer.parseInt(param.trim());
        LocalDateTime now = LocalDateTime.now();
        List<HrmRecruitInterviewDO> interviews = recruitInterviewMapper
                .selectListByInterviewTimeBetween(new LocalDateTime[]{now, now.plusHours(remindHours)});
        int mailed = 0;
        int notified = 0;
        for (HrmRecruitInterviewDO interview : interviews) {
            // 只提醒"还没出结果"的面试
            if (!HrmRecruitInterviewResultEnum.UNFINISHED.getResult().equals(interview.getResult())) {
                continue;
            }
            HrmRecruitCandidateDO candidate = recruitCandidateService
                    .getRecruitCandidate(interview.getCandidateId());
            if (candidate == null) {
                continue;
            }
            String timeText = interview.getInterviewTime() == null ? "待定"
                    : interview.getInterviewTime().format(TIME_FORMATTER);
            // 1. 提醒候选人（邮件）
            if (StrUtil.isNotBlank(candidate.getEmail())) {
                try {
                    Map<String, Object> params = new HashMap<>(4);
                    params.put("candidateName", StrUtil.blankToDefault(candidate.getName(), "候选人"));
                    params.put("interviewTime", timeText);
                    params.put("interviewAddress", StrUtil.blankToDefault(interview.getAddress(), "另行通知"));
                    mailSendApi.sendSingleMailToAddresses(new MailSendToAddressesReqDTO()
                            .setToMails(Collections.singletonList(candidate.getEmail()))
                            .setTemplateCode(MessageTemplateConstants.RECRUIT_INTERVIEW_REMIND)
                            .setTemplateParams(params)).getCheckedData();
                    mailed++;
                } catch (Throwable ex) {
                    log.warn("[execute][面试({}) 提醒邮件发送失败]", interview.getId(), ex);
                }
            }
            // 2. 提醒面试官（站内信）
            Set<Long> employeeIds = new LinkedHashSet<>();
            if (interview.getInterviewEmployeeId() != null) {
                employeeIds.add(interview.getInterviewEmployeeId());
            }
            if (interview.getOtherInterviewEmployeeIds() != null) {
                employeeIds.addAll(interview.getOtherInterviewEmployeeIds());
            }
            if (employeeIds.isEmpty()) {
                continue;
            }
            Map<String, Object> notifyParams = new HashMap<>(4);
            notifyParams.put("candidateName", StrUtil.blankToDefault(candidate.getName(), "候选人"));
            notifyParams.put("stageNumber", interview.getStageNumber());
            notifyParams.put("interviewTime", timeText);
            employeeService.getEmployeeMap(employeeIds).values().stream()
                    .map(HrmEmployeeDO::getUserId)
                    .filter(java.util.Objects::nonNull)
                    .forEach(userId -> {
                        try {
                            notifyMessageSendApi.sendSingleMessageToAdmin(new NotifySendSingleToUserReqDTO()
                                    .setUserId(userId)
                                    .setTemplateCode(MessageTemplateConstants.RECRUIT_INTERVIEW_ARRANGED)
                                    .setTemplateParams(notifyParams));
                        } catch (Throwable ex) {
                            log.warn("[execute][面试({}) 面试官提醒发送失败，userId({})]",
                                    interview.getId(), userId, ex);
                        }
                    });
            notified++;
        }
        return String.format("HRM 面试提醒：未来 %s 小时内面试 %s 场，候选人邮件 %s 封，面试官提醒 %s 人",
                remindHours, interviews.size(), mailed, notified);
    }

}
