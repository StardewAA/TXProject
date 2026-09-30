package cn.iocoder.txgy.module.bpm.api.event;

import cn.iocoder.txgy.module.bpm.framework.flowable.core.util.BpmHttpRequestUtils;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;

/**
 * 将 HRM 的 Offer 审批流程终态回写到 HRM 微服务。
 *
 * <p>BPM 与 HRM 为独立微服务，流程状态事件无法通过 Spring 事件跨进程传递，
 * 因此这里按项目既有做法（参考 WMS / ERP）通过 HTTP 回调业务服务。</p>
 *
 * @author OPENLAB BS
 */
@Component
@Slf4j
public class HrmOfferApprovalStatusListener extends BpmProcessInstanceStatusEventListener {

    private static final String PROCESS_KEY = "hrm_offer_approval";

    @Resource
    private RestTemplate loadBalancedRestTemplate;
    @Resource
    private HistoryService historyService;

    @Override
    protected String getProcessDefinitionKey() {
        return PROCESS_KEY;
    }

    @Override
    protected void onEvent(BpmProcessInstanceStatusEvent event) {
        // 把最后一个已办任务的负责人作为「最终审批人」带上，供 HRM 留痕（查询失败不影响回调）
        try {
            List<HistoricTaskInstance> tasks = historyService.createHistoricTaskInstanceQuery()
                    .processInstanceId(event.getId()).list();
            if (CollUtil.isNotEmpty(tasks)) {
                // 取最后一个有负责人的任务（兼容历史记录尚未写入结束时间的情况）
                for (HistoricTaskInstance task : tasks) {
                    if (task.getAssignee() != null) {
                        event.setApproverUserId(NumberUtil.parseLong(task.getAssignee()));
                    }
                }
            }
        } catch (Throwable ex) {
            log.warn("[onEvent][流程({})查询最终审批人失败，忽略]", event.getId(), ex);
        }
        BpmHttpRequestUtils.executeBpmHttpRequest(event,
                "http://hrm-server/rpc-api/hrm/recruit/offer/update-approval-status",
                loadBalancedRestTemplate);
    }

}
