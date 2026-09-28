package cn.iocoder.txgy.module.bidding.service.report;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.report.vo.ReportSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.report.ReportDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.report.ReportMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 定标书面报告实现。
 *
 * <p>业务目标：记录招标人向行政监督部门提交的书面报告（招标投标法第47条）；
 * 定标时间以中标结果公告（WIN_RESULT_PUBLICITY）发布时间为锚点，
 * 台账以 reportOverdue 标记「定标后超 15 日未提交」（义务逾期提醒，不阻断）。
 * 定标时间缺失时容错跳过（外部采集/缺公告不误拦）。
 */
@Service
@Slf4j
public class ReportServiceImpl implements ReportService {

    /** 招标投标法第47条：确定中标人之日起 15 日内提交书面报告。 */
    private static final long REPORT_PERIOD_DAYS_LIMIT = 15L;

    @Resource
    private ReportMapper reportMapper;
    @Resource
    private NoticeMapper noticeMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(ReportSaveReqVO reqVO) {
        // 定标时间锚点：中标结果公告（WIN_RESULT_PUBLICITY）发布时间；缺失容错跳过
        LocalDateTime awardTime = null;
        NoticeDO resultNotice = noticeMapper.selectOne(new LambdaQueryWrapper<NoticeDO>()
                .eq(NoticeDO::getNoticeType, NoticeTypeEnum.WIN_RESULT_PUBLICITY.name())
                .eq(NoticeDO::getProjectCode, reqVO.getTenderProjectCode())
                .isNotNull(NoticeDO::getPublishTime)
                .orderByDesc(NoticeDO::getPublishTime)
                .last("LIMIT 1"));
        if (resultNotice != null) {
            awardTime = resultNotice.getPublishTime();
        } else {
            log.warn("[report][submit] 招标项目 {} 未定位到中标结果公告发布时间（定标时间），跳过 15 日时限校验", reqVO.getTenderProjectCode());
        }
        // 超期提交告警（招标投标法第47条）：定标后超 15 日才提交 → warn（不阻断，报告必须提交）
        if (awardTime != null && ChronoUnit.DAYS.between(awardTime, LocalDateTime.now()) > REPORT_PERIOD_DAYS_LIMIT) {
            log.warn("[report][submit] 定标书面报告超期提交（招标投标法第47条：定标后15日内提交）：project={}，定标={}",
                    reqVO.getTenderProjectCode(), awardTime);
        }
        ReportDO report = new ReportDO();
        report.setTenderProjectCode(reqVO.getTenderProjectCode());
        report.setBidSectionCode(reqVO.getBidSectionCode());
        report.setReportCode(reqVO.getReportCode());
        report.setReportName(reqVO.getReportName());
        report.setFileId(reqVO.getFileId());
        report.setSubmitTime(LocalDateTime.now());
        report.setAwardTime(awardTime);
        report.setStatus("SUBMITTED");
        report.setRemark(reqVO.getRemark());
        reportMapper.insert(report);
        return report.getId();
    }

    @Override
    public ReportDO get(Long id) {
        ReportDO report = reportMapper.selectById(id);
        if (report == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.REPORT_NOT_EXISTS, id);
        }
        fillReportOverdue(report);
        return report;
    }

    @Override
    public PageResult<ReportDO> page(ReportPageReqVO reqVO) {
        PageResult<ReportDO> page = reportMapper.selectPage(reqVO);
        page.getList().forEach(this::fillReportOverdue);
        return page;
    }

    /** 计算「是否超期未提交」（招标投标法第47条）：未提交且定标后超 15 日 → true。 */
    private void fillReportOverdue(ReportDO report) {
        if (report.getSubmitTime() != null) {
            report.setReportOverdue(false);
            return;
        }
        report.setReportOverdue(report.getAwardTime() != null
                && ChronoUnit.DAYS.between(report.getAwardTime(), LocalDateTime.now()) > REPORT_PERIOD_DAYS_LIMIT);
    }
}
