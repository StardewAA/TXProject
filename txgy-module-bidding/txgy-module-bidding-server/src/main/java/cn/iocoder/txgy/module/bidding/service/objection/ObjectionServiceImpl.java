package cn.iocoder.txgy.module.bidding.service.objection;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeDeadlineRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionReplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.objection.vo.ObjectionSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.objection.ObjectionDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.objection.ObjectionMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.notice.NoticeService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * 投标异议实现。
 *
 * <p>业务目标：登记异议并校验提出时限（条例第22条——资格预审文件异议在申请截止 2 日前、
 * 招标文件异议在投标截止 10 日前提出，超期拒绝）；招标人收到后 3 日内答复，
 * 已答复不可重复答复；台账以 replyOverdue 标记答复超期。
 * 截止时间以已发布公告（招标/变更）的递交截止为锚点（NoticeService.getBidDeadline），缺失/解析失败容错跳过。
 */
@Service
@Slf4j
public class ObjectionServiceImpl implements ObjectionService {

    /** 条例第22条：资格预审文件异议须在申请截止 2 日前提出。 */
    private static final long PREQUAL_OBJECTION_DAYS = 2L;
    /** 条例第22条：招标文件异议须在投标截止 10 日前提出。 */
    private static final long TENDER_OBJECTION_DAYS = 10L;
    /** 条例第22条：招标人收到异议后 3 日内答复。 */
    private static final long REPLY_PERIOD_DAYS = 3L;

    private static final DateTimeFormatter SPEC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    @Resource
    private ObjectionMapper objectionMapper;
    @Resource
    private NoticeService noticeService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long propose(ObjectionSaveReqVO reqVO) {
        // 提出时限校验（条例第22条）：资格预审文件异议申请截止前 2 日 / 招标文件异议投标截止前 10 日
        long minDays = Objects.equals("PREQUAL_FILE", reqVO.getObjectionType())
                ? PREQUAL_OBJECTION_DAYS : TENDER_OBJECTION_DAYS;
        NoticeDeadlineRespVO deadline = noticeService.getBidDeadline(reqVO.getBidSectionCode());
        LocalDateTime submitDeadline = parseSpecTime(deadline == null ? null : deadline.getSubmitDeadline());
        if (submitDeadline != null) {
            long remaining = ChronoUnit.DAYS.between(LocalDateTime.now(), submitDeadline);
            if (remaining < minDays) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.OBJECTION_PROPOSE_TOO_LATE);
            }
        } else {
            log.warn("[objection][propose] 标段 {} 未定位到已发布公告的递交截止时间，跳过提出时限校验", reqVO.getBidSectionCode());
        }
        ObjectionDO objection = new ObjectionDO();
        objection.setTenderProjectCode(reqVO.getTenderProjectCode());
        objection.setBidSectionCode(reqVO.getBidSectionCode());
        objection.setObjectionType(reqVO.getObjectionType());
        objection.setSubjectId(reqVO.getSubjectId());
        objection.setContent(reqVO.getContent());
        objection.setProposeTime(LocalDateTime.now());
        objection.setStatus("PENDING");
        objection.setRemark(reqVO.getRemark());
        objectionMapper.insert(objection);
        return objection.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long reply(ObjectionReplyReqVO reqVO) {
        ObjectionDO objection = objectionMapper.selectById(reqVO.getObjectionId());
        if (objection == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OBJECTION_NOT_EXISTS, reqVO.getObjectionId());
        }
        if (objection.getReplyTime() != null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OBJECTION_ALREADY_REPLIED, reqVO.getObjectionId());
        }
        objection.setReplyTime(LocalDateTime.now());
        objection.setReplyContent(reqVO.getReplyContent());
        objection.setStatus("REPLIED");
        objectionMapper.updateById(objection);
        return objection.getId();
    }

    @Override
    public ObjectionDO get(Long id) {
        ObjectionDO objection = objectionMapper.selectById(id);
        if (objection == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.OBJECTION_NOT_EXISTS, id);
        }
        fillReplyOverdue(objection);
        return objection;
    }

    @Override
    public PageResult<ObjectionDO> page(ObjectionPageReqVO reqVO) {
        PageResult<ObjectionDO> page = objectionMapper.selectPage(reqVO);
        page.getList().forEach(this::fillReplyOverdue);
        return page;
    }

    /** 计算「答复是否超期」（条例第22条）：收到后 3 日内未答复 → true。 */
    private void fillReplyOverdue(ObjectionDO objection) {
        if (objection.getReplyTime() != null) {
            objection.setReplyOverdue(false);
            return;
        }
        objection.setReplyOverdue(objection.getProposeTime() != null
                && ChronoUnit.DAYS.between(objection.getProposeTime(), LocalDateTime.now()) > REPLY_PERIOD_DAYS);
    }

    /** 规范时间字符串（yyyyMMddHHmmss）→ LocalDateTime；缺失/解析失败返回 null（容错跳过校验）。 */
    private LocalDateTime parseSpecTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, SPEC_DATE_TIME);
        } catch (Exception e) {
            log.debug("[objection] 时间字段解析失败（跳过时限校验）：{}", text);
            return null;
        }
    }
}
