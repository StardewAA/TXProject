package cn.iocoder.txgy.module.bidding.service.flow;

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.flow.vo.FlowTimerRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.flow.FlowTimerDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.flow.FlowTimerMapper;
import cn.iocoder.txgy.module.bidding.enums.flow.FlowTimerStatusEnum;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class FlowTimerServiceImpl implements FlowTimerService {

    /** 规范时间格式（《VO数据对象规范》日期时间型 yyyyMMddHHmmss） */
    private static final DateTimeFormatter SPEC_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private FlowTimerMapper flowTimerMapper;

    @Override
    public void registerForTenderNotice(String tenderProjectCode, String docGetStartTime,
                                        String bidDocReferEndTime, String bidEndTime) {
        if (!StringUtils.hasText(tenderProjectCode)) {
            return;
        }
        LocalDateTime start = parseSpecTime(docGetStartTime);
        // 变更公告以 BID_END_TIME 覆盖递交截止（与 getBidDeadline 同一口径）
        LocalDateTime end = parseSpecTime(bidEndTime != null ? bidEndTime : bidDocReferEndTime);
        register("TENDER_PROJECT", tenderProjectCode, "BIDDING_STARTED", start, "招标公告登记自动注册");
        register("TENDER_PROJECT", tenderProjectCode, "BIDDING_ENDED", end, "招标公告登记自动注册");
    }

    @Override
    public List<FlowTimerRespVO> listTimers(String bizType, String bizCode) {
        return BeanUtils.toBean(flowTimerMapper.selectListByBiz(bizType, bizCode), FlowTimerRespVO.class);
    }

    /** 解析规范 yyyyMMddHHmmss → LocalDateTime；空/非法返回 null（不注册对应事件）。 */
    private LocalDateTime parseSpecTime(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), SPEC_TIME);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 幂等注册：同一实体同一事件存在 WAITING 记录则更新时间（重跑/改时间安全）；
     * 已 DONE/SKIPPED 的历史记录不动，若再次配置更晚时间则新开一条 WAITING（到点执行时状态不匹配会 SKIP，无副作用）。
     */
    private void register(String bizType, String bizCode, String event, LocalDateTime triggerTime, String remark) {
        if (triggerTime == null) {
            return;
        }
        FlowTimerDO timer = flowTimerMapper.selectWaiting(bizType, bizCode, event);
        if (timer != null) {
            timer.setTriggerTime(triggerTime);
            timer.setRemark(remark);
            flowTimerMapper.updateById(timer);
            return;
        }
        FlowTimerDO created = new FlowTimerDO();
        created.setBizType(bizType);
        created.setBizCode(bizCode);
        created.setEvent(event);
        created.setTriggerTime(triggerTime);
        created.setStatus(FlowTimerStatusEnum.WAITING.name());
        created.setRemark(remark);
        flowTimerMapper.insert(created);
    }
}
