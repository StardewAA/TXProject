package cn.iocoder.txgy.module.bidding.service.bidreview;

import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportPageRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidreview.vo.BidReviewReportRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewReportDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidreview.BidReviewTaskDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewReportMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidreview.BidReviewTaskMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;

/**
 * 智慧标书审查 · 报告实现。
 */
@Service
public class BidReviewReportServiceImpl implements BidReviewReportService {

    /** 报告页脚固定提示（内部参考件口径，方案 §12 第 3 条）。 */
    private static final String DISCLAIMER = "本报告由 AI 生成，需人工复核后使用；不作为对外正式文件。";

    // 钉住 bean 名：字段名 reportMapper 若走 @Resource 按名解析会命中 report 域的 ReportMapper（类型不符→启动失败）
    @Resource(name = "bidReviewReportMapper")
    private BidReviewReportMapper reportMapper;
    @Resource
    private BidReviewTaskService taskService;
    @Resource
    private BidReviewTaskMapper taskMapper;

    @Override
    public BidReviewReportRespVO getReportByTaskId(Long taskId, boolean checkOwner) {
        if (checkOwner) {
            // 归属校验（非本人抛 BID_REVIEW_TASK_NOT_OWNER）+ 切到任务所属租户：
            // 报告跟着任务的租户走，个人端提交到企业的任务同样读得到（2026-09-20）
            return taskService.executeInMyTaskTenant(taskId, () -> doGetReportByTaskId(taskId));
        }
        return doGetReportByTaskId(taskId);
    }

    private BidReviewReportRespVO doGetReportByTaskId(Long taskId) {
        BidReviewReportDO report = reportMapper.selectByTaskId(taskId);
        if (report == null) {
            throw exception(ErrorCodeConstants.BID_REVIEW_REPORT_NOT_READY);
        }
        BidReviewReportRespVO resp = BeanUtils.toBean(report, BidReviewReportRespVO.class);
        resp.setDisclaimer(DISCLAIMER);
        return resp;
    }

    @Override
    public String exportHtml(Long taskId, boolean checkOwner) {
        return getReportByTaskId(taskId, checkOwner).getHtmlContent();
    }

    @Override
    public PageResult<BidReviewReportPageRespVO> getReportPage(BidReviewReportPageReqVO reqVO) {
        PageResult<BidReviewReportDO> page = reportMapper.selectPage(reqVO);
        List<BidReviewReportPageRespVO> list = page.getList().stream()
                .map(report -> {
                    BidReviewReportPageRespVO resp = BeanUtils.toBean(report, BidReviewReportPageRespVO.class);
                    resp.setRejected("FAIL".equals(report.getVerdict()));
                    resp.setDocxReady(report.getDocxFileId() != null);
                    return resp;
                })
                .toList();
        fillTaskInfo(list);
        return new PageResult<>(list, page.getTotal());
    }

    /** 台账要显示"报告属于哪个任务"，一次批量查出任务编号与名称（不按行查）。 */
    private void fillTaskInfo(List<BidReviewReportPageRespVO> list) {
        List<Long> taskIds = list.stream()
                .map(BidReviewReportPageRespVO::getTaskId).filter(Objects::nonNull).distinct().toList();
        if (taskIds.isEmpty()) {
            return;
        }
        Map<Long, BidReviewTaskDO> taskMap = new HashMap<>();
        for (BidReviewTaskDO task : taskMapper.selectBatchIds(taskIds)) {
            taskMap.put(task.getId(), task);
        }
        list.forEach(resp -> {
            BidReviewTaskDO task = taskMap.get(resp.getTaskId());
            if (task != null) {
                resp.setTaskNo(task.getTaskNo());
                resp.setTaskName(task.getTaskName());
            }
        });
    }
}
