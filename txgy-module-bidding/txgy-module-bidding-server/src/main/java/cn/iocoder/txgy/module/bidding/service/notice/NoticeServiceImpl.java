package cn.iocoder.txgy.module.bidding.service.notice;
// 规范依据：《招投标模块开发规范》§4.4/4.5/4.8、五章三/五；VO数据规范必填；红线2/8；05

import cn.iocoder.txgy.framework.common.exception.ServiceException;
import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeDeadlineRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePublicSyncReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePublicSyncRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeContentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeContentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.notice.NoticeMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.service.noticefollow.FollowService;
import cn.iocoder.txgy.module.bidding.enums.NoticeStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.flow.AnnouncementStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;
import cn.iocoder.txgy.module.bidding.service.flow.FlowTimerService;
import cn.iocoder.txgy.module.bidding.service.notice.search.NoticeEsDoc;
import cn.iocoder.txgy.module.bidding.service.biznum.BiddingBizNoGenerator;
import cn.iocoder.txgy.module.bidding.service.biznum.BiddingBizNoTypeEnum;
import cn.iocoder.txgy.module.bidding.service.notice.search.NoticeSearchEngine;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectEligibilityService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 公告/公示服务实现。
 *
 * <p>要点：权威字段全集按数据集 VO 规整入库（fieldsJson）；正文版本化只追加；
 * 状态机流转受 {@link NoticeStatusEnum} 约束；管理侧本租户隔离（拦截器自动拼 tenant_id），
 * 大厅公开读跨租户（executeIgnore）且只放行 PUBLISHED + 非内部 + 到时间 + SOCIAL；
 * 关键词全文走 {@link NoticeSearchEngine}（未启用即报错，禁止业务表 LIKE）。
 */
@Service
@Slf4j
public class NoticeServiceImpl implements NoticeService {

    private static final String CONTENT_TYPE_BODY = "BODY";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Resource
    private NoticeMapper noticeMapper;
    /** 招标人发布门（授权+完善；招标投标法第8/16条 —— 发布主体须为已授权且信息完善的招标人企业） */
    @Resource
    private SubjectEligibilityService subjectEligibilityService;
    @Resource
    private NoticeContentMapper noticeContentMapper;
    @Resource
    private NoticeSearchEngine noticeSearchEngine;
    /** 关注扇出（内部公告版本变化 → 同租户关注者 INTERNAL 动态；失败不阻断发布，见 handleInternalNoticeChanged 注释） */
    @Resource
    private FollowService followService;
    /** 内部编号生成器（公告内部编号按规则生成，非随机：B+区划+年度+序列，见 BiddingBizNoGenerator） */
    @Resource
    private BiddingBizNoGenerator biddingBizNoGenerator;
    /** 时间驱动调度注册（公告权威时间字段 → bidding_flow_timer，见 FlowTimerServiceImpl） */
    @Resource
    private FlowTimerService flowTimerService;

    // ========== 后台管理 ==========

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createNotice(NoticeSaveReqVO reqVO) {
        // [依据] 招标人发布门：发文主体须为已授权且信息完善的招标人（招标投标法第8/16条；实施条例第15条）
        //       外部采集/平台同步等无租户上下文操作不受影响（见 SubjectEligibilityServiceImpl）
        subjectEligibilityService.assertTendererIssuanceAllowed();
        NoticeTypeEnum type = requireType(reqVO.getNoticeType());
        NoticeDO notice = new NoticeDO();
        notice.setNoticeCode(StringUtils.hasText(reqVO.getNoticeCode()) ? reqVO.getNoticeCode() : generateNoticeCode());
        notice.setNoticeType(type.name());
        applyBaseCreate(notice, reqVO);
        notice.setStatus(NoticeStatusEnum.DRAFT.name());
        // 权威必填校验（VO数据规范 数据集“必填=是”）后规整入库
        LinkedHashMap<String, Object> canonical = NoticePayloadUtils.canonicalize(type, reqVO.getPayload());
        checkRequired(type, canonical);
        notice.setFieldsJson(writePayload(canonical));
        noticeMapper.insert(notice);
        // 时间驱动注册**不在此处**：草稿阶段不得注册时间轴任务。原因（2026-09-22 实测故障）：
        // 草稿公告若注册了 BIDDING_STARTED/BIDDING_ENDED，而公告始终不发布 → 招标项目永停在 PREPARING
        // → 调度器每秒重试"状态未就绪"、永不成功 → 事务风暴耗尽连接池、拖垮整个服务。
        // 正确时机=发布（见 publishNotice：只有 PUBLISHED 才对外生效，其时间字段才具备业务含义）。
        // 正文 v1
        if (StringUtils.hasText(reqVO.getContent())) {
            appendContent(notice.getId(), reqVO.getContent());
        }
        // 事务提交后同步检索文档（规范 4.6 最终一致）
        afterCommit(() -> indexDoc(notice.getId()));
        return notice.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateNotice(NoticeSaveReqVO reqVO) {
        subjectEligibilityService.assertTendererIssuanceAllowed(); // 招标人发布门（同 createNotice 注释）
        NoticeDO notice = getNotice(reqVO.getId());
        // 载荷：仅在显式提交 payload 时按权威必填重新规整（不提交则保持原快照）
        if (reqVO.getPayload() != null) {
            NoticeTypeEnum type = requireType(reqVO.getNoticeType() != null
                    ? reqVO.getNoticeType() : notice.getNoticeType());
            LinkedHashMap<String, Object> canonical = NoticePayloadUtils.canonicalize(type, reqVO.getPayload());
            checkRequired(type, canonical);
            notice.setFieldsJson(writePayload(canonical));
            // 时间驱动：仅**已发布**公告的载荷变更才重注册（幂等更新时间，见 FlowTimerServiceImpl）。
            // 草稿/已撤下公告改时间不注册——未对外生效的时间不构成时间轴承诺，注册了只会在到期时
            // 因"状态未就绪"被调度器每秒重试（2026-09-22 连接池耗尽故障的根因之一）。
            if (NoticeStatusEnum.PUBLISHED.name().equals(notice.getStatus())) {
                registerTimerFromPayload(type, canonical);
            }
        }
        if (reqVO.getNoticeType() != null) {
            notice.setNoticeType(requireType(reqVO.getNoticeType()).name());
        }
        applyBaseUpdate(notice, reqVO); // 部分更新：未提交字段保留原值（不得翻转 internalFlag/清空项目归属，见 05-业务核查 §2.5 实测修复）
        noticeMapper.updateById(notice);
        // 正文：有实质变化才追加新版本
        int appendedVersion = -1;
        if (reqVO.getContent() != null) {
            NoticeContentDO latest = latestContent(notice.getId());
            String oldContent = latest == null ? null : latest.getContent();
            if (NoticeContentUtils.shouldAppendVersion(oldContent, reqVO.getContent())) {
                appendedVersion = appendContent(notice.getId(), reqVO.getContent());
            }
        }
        afterCommit(() -> indexDoc(notice.getId()));
        // 关注扇出：原状态已发布且正文产生新版本 → 同租户关注者收到 NOTICE_UPDATED（业务口径：草稿期编辑不打扰订阅者）
        if (appendedVersion > 0 && NoticeStatusEnum.PUBLISHED.name().equals(notice.getStatus())) {
            LinkedHashMap<String, Object> payload = readPayload(notice.getFieldsJson());
            fanoutInternalVersionChange("UPDATED", notice.getId(), notice.getTitle(),
                    notice.getPublisherName(), notice.getProjectName(), notice.getProjectCode(),
                    strOrNull(payload.get("TENDER_PROJECT_CODE")),
                    splitSectionCodes(payload.get("BID_SECTION_CODES")), appendedVersion);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteNotice(Long id) {
        NoticeDO notice = getNotice(id);
        // 删除守卫（2026-09-09，对应 08-前端交付 §8 待补强项；业务依据）：
        // ① 已发布（PUBLISHED）对外生效中，直接删除会使大厅历史内容消失、关注订阅引用的公告 id 失效、审计留痕断链 → 必须先撤下再删；
        // ② 已归档（ARCHIVED）为只读留痕终态（状态机无出口），禁止删除 → 仅 草稿(DRAFT)/已撤下(UNPUBLISHED) 允许删除
        String status = notice.getStatus();
        if (!NoticeStatusEnum.DRAFT.name().equals(status)
                && !NoticeStatusEnum.UNPUBLISHED.name().equals(status)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_DELETE_STATUS_INVALID);
        }
        noticeMapper.deleteById(notice.getId());
        afterCommit(() -> noticeSearchEngine.remove(id));
    }

    @Override
    public String reindexIndex() {
        List<cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO> all = noticeMapper.selectList(
                new LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO>()
                        .orderByAsc(cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO::getId));
        int ok = 0;
        int failed = 0;
        for (cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO notice : all) {
            try {
                indexDoc(notice.getId());
                ok++;
            } catch (Exception e) {
                failed++;
                log.warn("[reindexIndex][noticeId={}] ES 索引失败: {}", notice.getId(), e.getMessage());
            }
        }
        log.info("[reindexIndex] 完成 total={} ok={} failed={}", all.size(), ok, failed);
        return "total=" + all.size() + ",ok=" + ok + ",failed=" + failed;
    }

    @Override
    public String reindexAllTenants() {
        // 平台级补偿：es.enabled=false 期间（DevDisabled/LogOnly 装配）入库/发布的公告从未建索引，标题/正文检索不到；
        // 忽略租户遍历全量重放（PUT 幂等，重复执行安全）；仅平台租户可调（Controller 校验 tenantId=1）
        int[] stat = new int[3]; // total/ok/failed
        TenantUtils.executeIgnore(() -> {
            List<cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO> all = noticeMapper.selectList(
                    new LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO>()
                            .orderByAsc(cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO::getId));
            stat[0] = all.size();
            for (cn.iocoder.txgy.module.bidding.dal.dataobject.notice.NoticeDO notice : all) {
                try {
                    indexDoc(notice.getId());
                    stat[1]++;
                } catch (Exception e) {
                    stat[2]++;
                    log.warn("[reindexAllTenants][noticeId={}] ES 索引失败: {}", notice.getId(), e.getMessage());
                }
            }
        });
        log.info("[reindexAllTenants] 完成 total={} ok={} failed={}", stat[0], stat[1], stat[2]);
        return "total=" + stat[0] + ",ok=" + stat[1] + ",failed=" + stat[2];
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishNotice(Long id) {
        subjectEligibilityService.assertTendererIssuanceAllowed(); // 招标人发布门：DRAFT→PUBLISHED 前复核主体资格
        NoticeDO notice = getNotice(id);
        validateIssuanceDeadline(notice); // 发售期/投标期发布拦截（§5.3 第1/2条；仅招标公告数据集，缺字段容错跳过）
        validateChangeDeadline(notice); // 澄清/修改 15 日顺延拦截（§4.3 第5条；仅变更公告数据集，缺字段容错跳过）
        validatePublicityPeriod(notice); // 公示期发布拦截（§10.3 第2条；仅候选人公示数据集，缺字段容错跳过）
        transit(notice, NoticeStatusEnum.PUBLISHED);
        if (notice.getPublishTime() == null) {
            notice.setPublishTime(LocalDateTime.now());
        }
        noticeMapper.updateById(notice);
        afterCommit(() -> indexDoc(id));
        // 时间驱动：**发布即注册**（唯一注册时机；草稿阶段不注册，见 createNotice 注释）。
        // 注册后状态必为 PUBLISHED，但招标项目状态机可能仍在 PREPARING（发布公告后才由
        // PUBLISH_TENDER_NOTICE 推进到 ANNOUNCED）——两者时间差由调度器的"状态未就绪"分支吸收，
        // 且该分支已加退避（见 TimeEventScheduler），不会形成高频重试。
        LinkedHashMap<String, Object> payload = readPayload(notice.getFieldsJson());
        registerTimerFromPayload(typeOfNotice(notice), payload);
        // 关注扇出：发布/重新发布成功 → 同租户关注者收到 NOTICE_PUBLISHED（版本=当前最新正文版本；重发同版本不重复提醒，见事件键含版本号）
        // 三级编号（2026-09-23）：项目编号取根字段，招标项目编号/标段（包）编号取权威数据集——
        // 门户「我的关注」的三级跟踪键就是这三种编号（notice-follow PROJECT/TENDER_PROJECT/SECTION）。
        int version = latestContentVersion(id);
        fanoutInternalVersionChange("PUBLISHED", id, notice.getTitle(),
                notice.getPublisherName(), notice.getProjectName(), notice.getProjectCode(),
                strOrNull(payload.get("TENDER_PROJECT_CODE")),
                splitSectionCodes(payload.get("BID_SECTION_CODES")), version);
    }

    /** 公告实体类型（注册时间轴用；类型串非法时返回 null，由注册方法自行跳过）。 */
    private NoticeTypeEnum typeOfNotice(NoticeDO notice) {
        return NoticeTypeEnum.of(notice.getNoticeType());
    }


    /** 规范 V2.0 公告时间字段格式（yyyyMMddHHmmss，14 位，VARCHAR(14)）。 */
    private static final DateTimeFormatter SPEC_DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 发售期/投标期发布拦截（说明书 §5.3 第 1/2 条，招标公告与资格预审公告数据集 TENDER_NOTICE）。
     *
     * <ul>
     *   <li>发售期（实施条例第 16 条）：DOC_GET_START_TIME → DOC_GET_END_TIME ≥ 5 日；</li>
     *   <li>投标期（招标投标法第 24 条）：仅招标公告（无 QUAL_TYPE），DOC_GET_START_TIME →
     *       BID_DOC_REFER_END_TIME ≥ 20 日；资格预审公告的申请期 ≥5 日已由 Prequal 域负责。</li>
     * </ul>
     * 时间字段缺失或解析失败时跳过校验（容错：外部采集/缺字段公告不误拦截，避免发布被卡死）。
     */
    private void validateIssuanceDeadline(NoticeDO notice) {
        if (!NoticeTypeEnum.TENDER_NOTICE.name().equals(notice.getNoticeType())) {
            return;
        }
        LinkedHashMap<String, Object> payload = readPayload(notice.getFieldsJson());
        LocalDateTime start = parseSpecTime(strOrNull(payload.get("DOC_GET_START_TIME")));
        if (start == null) {
            return; // 缺发售开始时间：跳过
        }
        // 发售期 ≥ 5 日
        LocalDateTime end = parseSpecTime(strOrNull(payload.get("DOC_GET_END_TIME")));
        if (end != null && ChronoUnit.DAYS.between(start, end) < 5) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_ISSUANCE_PERIOD_TOO_SHORT,
                    payload.get("DOC_GET_START_TIME"), payload.get("DOC_GET_END_TIME"));
        }
        // 投标期 ≥ 20 日（仅招标公告：QUAL_TYPE 为空；资格预审公告有评审办法 QUAL_TYPE，申请期校验归 Prequal 域）
        if (strOrNull(payload.get("QUAL_TYPE")) == null) {
            LocalDateTime referEnd = parseSpecTime(strOrNull(payload.get("BID_DOC_REFER_END_TIME")));
            if (referEnd != null && ChronoUnit.DAYS.between(start, referEnd) < 20) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_BID_PERIOD_TOO_SHORT,
                        payload.get("DOC_GET_START_TIME"), payload.get("BID_DOC_REFER_END_TIME"));
            }
        }
    }

    private LocalDateTime parseSpecTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text, SPEC_DATE_TIME);
        } catch (Exception e) {
            log.debug("[notice][deadline] 时间字段解析失败（跳过校验）：{}", text);
            return null;
        }
    }

    /**
     * 公示期发布拦截（说明书 §10.3 第 2 条，中标候选人公示数据集 WIN_CANDIDATE_PUBLICITY）：
     * PUBLICITY_START_TIME → PUBLICITY_END_TIME ≥ 3 日（实施条例第 54 条）；缺失/解析失败容错跳过。
     */
    private void validatePublicityPeriod(NoticeDO notice) {
        if (!NoticeTypeEnum.WIN_CANDIDATE_PUBLICITY.name().equals(notice.getNoticeType())) {
            return;
        }
        LinkedHashMap<String, Object> payload = readPayload(notice.getFieldsJson());
        LocalDateTime start = parseSpecTime(strOrNull(payload.get("PUBLICITY_START_TIME")));
        LocalDateTime end = parseSpecTime(strOrNull(payload.get("PUBLICITY_END_TIME")));
        if (start == null || end == null) {
            return; // 缺公示期字段：跳过
        }
        if (ChronoUnit.DAYS.between(start, end) < 3) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_PUBLICITY_PERIOD_TOO_SHORT,
                    payload.get("PUBLICITY_START_TIME"), payload.get("PUBLICITY_END_TIME"));
        }
    }

    /**
     * 澄清/修改通知时限顺延拦截（说明书 §4.3 第 5 条 / §4.4，变更公告数据集 CHANGE_NOTICE）：
     * 澄清/修改影响文件编制的，应在投标截止 15 日前（资格预审截止 3 日前）通知（实施条例第 21 条）；
     * 不足则该变更公告填报的投标截止时间（BID_END_TIME）不成立 → 拒绝发布，强制招标人顺延截止时间。
     * 资格预审/招标区分复用发售期约定：payload 有 QUAL_TYPE = 资格预审（3 日），否则招标（15 日）。
     * 缺字段/解析失败容错跳过。
     */
    private void validateChangeDeadline(NoticeDO notice) {
        if (!NoticeTypeEnum.CHANGE_NOTICE.name().equals(notice.getNoticeType())) {
            return;
        }
        LinkedHashMap<String, Object> payload = readPayload(notice.getFieldsJson());
        LocalDateTime send = parseSpecTime(strOrNull(payload.get("NOTICE_SEND_TIME")));
        LocalDateTime deadline = parseSpecTime(strOrNull(payload.get("BID_END_TIME")));
        if (send == null || deadline == null) {
            return; // 缺澄清发出/截止时间：跳过
        }
        boolean prequal = strOrNull(payload.get("QUAL_TYPE")) != null; // 资格预审变更：3 日；招标变更：15 日
        long minDays = prequal ? 3L : 15L;
        if (ChronoUnit.DAYS.between(send, deadline) < minDays) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_CHANGE_DEADLINE_TOO_SHORT,
                    minDays, payload.get("NOTICE_SEND_TIME"), payload.get("BID_END_TIME"));
        }
    }

    /** 内部公告关注扇出（失败仅记日志，不阻断公告本身的结果；见 FollowService.handleInternalNoticeChanged）。
     *  2026-09-23：入参由「一个项目编号」扩为**三级编号**（项目/招标项目/标段），
     *  与门户「我的关注」三级跟踪键一一对应；另两级来自权威数据集（见 splitSectionCodes）。 */
    private void fanoutInternalVersionChange(String action, Long noticeId, String title, String publisherName,
                                             String projectName, String projectCode, String tenderProjectCode,
                                             List<String> bidSectionCodes, int version) {
        try {
            if (followService != null && noticeId != null) {
                followService.handleInternalNoticeChanged(action, noticeId, title, publisherName,
                        projectName, projectCode, tenderProjectCode, bidSectionCodes, version);
            }
        } catch (Exception e) {
            log.error("[notice][internalFanout][action={}][noticeId={}] 关注扇出失败（不影响公告结果）", action, noticeId, e);
        }
    }

    private int latestContentVersion(Long noticeId) {
        NoticeContentDO latest = latestContent(noticeId);
        return latest == null ? 0 : latest.getVersion();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unpublishNotice(Long id) {
        NoticeDO notice = getNotice(id);
        transit(notice, NoticeStatusEnum.UNPUBLISHED);
        noticeMapper.updateById(notice);
        afterCommit(() -> indexDoc(id));
    }

    @Override
    public PageResult<NoticeRespVO> getNoticePage(NoticePageReqVO reqVO) {
        return queryPage(reqVO, false);
    }

    @Override
    public NoticeDetailRespVO getNoticeDetail(Long id) {
        return toDetail(getNotice(id));
    }

    // ========== 大厅公开只读（跨租户） ==========

    @Override
    public PageResult<NoticeRespVO> getPublicNoticePage(NoticePageReqVO reqVO) {
        return TenantUtils.executeIgnore(() -> queryPage(reqVO, true));
    }

    @Override
    public NoticeDetailRespVO getPublicNoticeDetail(Long id) {
        return TenantUtils.executeIgnore(() -> toDetail(getNotice(id), true));
    }

    @Override
    public NoticePublicSyncRespVO syncPublicDocument(NoticePublicSyncReqVO reqVO) {
        NoticeDO notice = noticeMapper.selectOne(new LambdaQueryWrapper<NoticeDO>()
                .eq(NoticeDO::getNoticeCode, reqVO.getBizNo())
                .orderByDesc(NoticeDO::getId)
                .last("LIMIT 1"));
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_NOT_EXISTS);
        }
        // 白名单校验（BID-SUP-GATHER-02 规则：仅社会公开字段，更正/撤销走发布-撤下）
        if (!NoticeStatusEnum.PUBLISHED.name().equals(notice.getStatus())
                || !Boolean.FALSE.equals(notice.getInternalFlag())
                || !"SOCIAL".equals(notice.getPublicLevel())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_GATHER_ONLY_PUBLIC);
        }
        indexDoc(notice.getId());
        NoticePublicSyncRespVO respVO = new NoticePublicSyncRespVO();
        respVO.setTaskNo("G" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        respVO.setStatus("SUCCESS");
        return respVO;
    }

    @Override
    public NoticeDeadlineRespVO getBidDeadline(String bidSectionCode) {
        List<NoticeDO> candidates = noticeMapper.selectList(new LambdaQueryWrapper<NoticeDO>()
                .in(NoticeDO::getNoticeType, NoticeTypeEnum.TENDER_NOTICE.name(),
                        NoticeTypeEnum.CHANGE_NOTICE.name())
                .eq(NoticeDO::getStatus, NoticeStatusEnum.PUBLISHED.name())
                .orderByDesc(NoticeDO::getPublishTime)
                .last("LIMIT 100"));
        for (NoticeDO notice : candidates) {
            LinkedHashMap<String, Object> payload = readPayload(notice.getFieldsJson());
            Object sectionCodes = payload.get("BID_SECTION_CODES");
            if (sectionCodes != null && matchesSection(sectionCodes.toString(), bidSectionCode)) {
                NoticeDeadlineRespVO vo = new NoticeDeadlineRespVO();
                vo.setBidSectionCode(bidSectionCode);
                vo.setNoticeCode(notice.getNoticeCode());
                vo.setNoticeType(notice.getNoticeType());
                vo.setFileIssueTime(strOrNull(payload.get("NOTICE_SEND_TIME")));
                // 2026-09-23 修正（对比 VO数据对象规范）：SUBMIT_TIMESTAMP=数据时间戳（数据上报时间），
                // 递交截止的正主是 BID_DOC_REFER_END_TIME（投标文件/资审申请文件递交截止时间，必填，社会公开）；
                // 变更公告以 BID_END_TIME（投标截止时间/申请截止时间）覆盖；澄清截止规范无专名，取 CLARIFY_TIME（答疑澄清时间）
                vo.setClarifyDeadline(strOrNull(payload.get("CLARIFY_TIME")));
                String submit = NoticeTypeEnum.CHANGE_NOTICE.name().equals(notice.getNoticeType())
                        ? strOrNull(payload.get("BID_END_TIME"))
                        : strOrNull(payload.get("BID_DOC_REFER_END_TIME"));
                vo.setSubmitDeadline(submit);
                vo.setBidOpenTime(strOrNull(payload.get("BID_OPEN_TIME")));
                return vo;
            }
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_DEADLINE_NOT_FOUND);
    }

    /** 从公告权威 payload 注册时间驱动调度（TENDER_PROJECT 维度；变更公告 BID_END_TIME 覆盖递交截止）。 */
    private void registerTimerFromPayload(NoticeTypeEnum type, LinkedHashMap<String, Object> canonical) {
        Object projectCode = canonical.get("TENDER_PROJECT_CODE");
        if (projectCode == null) {
            return;
        }
        String bidEndTime = NoticeTypeEnum.CHANGE_NOTICE.equals(type)
                ? strOrNull(canonical.get("BID_END_TIME")) : null;
        flowTimerService.registerForTenderNotice(
                projectCode.toString(),
                strOrNull(canonical.get("DOC_GET_START_TIME")),
                strOrNull(canonical.get("BID_DOC_REFER_END_TIME")),
                bidEndTime);
    }

    private boolean matchesSection(String sectionCodes, String bidSectionCode) {
        for (String code : sectionCodes.split("[,，、;；\\s]+")) {
            if (code.trim().equals(bidSectionCode)) {
                return true;
            }
        }
        return false;
    }

    private String strOrNull(Object value) {
        return value == null ? null : value.toString();
    }

    /** 数据集 BID_SECTION_CODES（可多个）→ 标段编号列表：分隔符口径与 {@link #matchesSection} 一致
     *  （半角/全角分号、顿号、逗号、空白），去重且保序；空值返回空列表（不过滤、不报错）。 */
    private List<String> splitSectionCodes(Object value) {
        List<String> codes = new ArrayList<>();
        String text = strOrNull(value);
        if (!StringUtils.hasText(text)) {
            return codes;
        }
        for (String part : text.split("[,，、;；\s]+")) {
            String code = part.trim();
            if (StringUtils.hasText(code) && !codes.contains(code)) {
                codes.add(code);
            }
        }
        return codes;
    }

    // ========== 内部实现 ==========

    private PageResult<NoticeRespVO> queryPage(NoticePageReqVO reqVO, boolean publicOnly) {
        LambdaQueryWrapper<NoticeDO> wrapper = new LambdaQueryWrapper<NoticeDO>()
                .eq(StringUtils.hasText(reqVO.getNoticeType()), NoticeDO::getNoticeType, reqVO.getNoticeType())
                .eq(StringUtils.hasText(reqVO.getStatus()), NoticeDO::getStatus, reqVO.getStatus())
                .eq(reqVO.getInternalFlag() != null, NoticeDO::getInternalFlag, reqVO.getInternalFlag())
                .eq(StringUtils.hasText(reqVO.getPublicLevel()), NoticeDO::getPublicLevel, reqVO.getPublicLevel())
                .eq(StringUtils.hasText(reqVO.getProjectCode()), NoticeDO::getProjectCode, reqVO.getProjectCode())
                // 三级编号（2026-09-23）：招标项目编号 / 标段（包）编号落在权威数据集 fields_json 里，
                // 零 DDL 用 JSON 提取做精确匹配；标段编号任一分量命中即算（分隔符口径同 matchesSection）。
                .apply(StringUtils.hasText(reqVO.getTenderProjectCode()),
                        "JSON_UNQUOTE(JSON_EXTRACT(fields_json, '$.TENDER_PROJECT_CODE')) = {0}",
                        reqVO.getTenderProjectCode())
                .apply(StringUtils.hasText(reqVO.getBidSectionCode()),
                        "FIND_IN_SET({0}, REPLACE(REPLACE(REPLACE(REPLACE("
                                + "JSON_UNQUOTE(JSON_EXTRACT(fields_json, '$.BID_SECTION_CODES')), ';', ','), '；', ','), '、', ','), ' ', ',')) > 0",
                        reqVO.getBidSectionCode())
                .ge(reqVO.getPublishTimeStart() != null, NoticeDO::getPublishTime, reqVO.getPublishTimeStart())
                .le(reqVO.getPublishTimeEnd() != null, NoticeDO::getPublishTime, reqVO.getPublishTimeEnd())
                .orderByDesc(NoticeDO::getPublishTime);
        if (publicOnly) {
            wrapper.eq(NoticeDO::getStatus, NoticeStatusEnum.PUBLISHED.name())
                    .eq(NoticeDO::getInternalFlag, false)
                    .eq(NoticeDO::getPublicLevel, "SOCIAL")
                    .and(w -> w.isNull(NoticeDO::getPublishTime).or().le(NoticeDO::getPublishTime, LocalDateTime.now()));
        }
        // 关键词：全文命中走检索组件，命中为空直接返回空页
        if (StringUtils.hasText(reqVO.getKeyword())) {
            List<Long> ids = noticeSearchEngine.matchIds(reqVO.getKeyword().trim(), 10_000);
            if (ids.isEmpty()) {
                return new PageResult<>(new ArrayList<>(), 0L);
            }
            wrapper.in(NoticeDO::getId, ids);
        }
        Page<NoticeDO> page = noticeMapper.selectPage(
                new Page<>(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(convertList(page.getRecords()), page.getTotal());
    }

    private NoticeDetailRespVO toDetail(NoticeDO notice) {
        return toDetail(notice, false);
    }

    private NoticeDetailRespVO toDetail(NoticeDO notice, boolean publicOnly) {
        if (publicOnly && !isPublicVisible(notice)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_NOT_EXISTS);
        }
        NoticeDetailRespVO vo = new NoticeDetailRespVO();
        fillCommon(vo, notice, latestContent(notice.getId()));
        LinkedHashMap<String, Object> full = readPayload(notice.getFieldsJson());
        // 公开边界裁剪：大厅只吐社会公开子集（权威规则，见 05 接口设计文档规则 1 / 红线 2）
        vo.setPayload(publicOnly
                ? NoticePayloadUtils.socialSubset(NoticeTypeEnum.of(notice.getNoticeType()), full)
                : full);
        return vo;
    }

    private boolean isPublicVisible(NoticeDO notice) {
        if (!NoticeStatusEnum.PUBLISHED.name().equals(notice.getStatus())
                || !Boolean.FALSE.equals(notice.getInternalFlag())
                || !"SOCIAL".equals(notice.getPublicLevel())) {
            return false;
        }
        return notice.getPublishTime() == null || !notice.getPublishTime().isAfter(LocalDateTime.now());
    }

    /** 新建（表单全量）：缺省字段落默认值（publicLevel 缺省 SOCIAL；internalFlag 缺省 false）。 */
    private void applyBaseCreate(NoticeDO notice, NoticeSaveReqVO reqVO) {
        notice.setTitle(reqVO.getTitle());
        notice.setProjectCode(reqVO.getProjectCode());
        notice.setProjectName(reqVO.getProjectName());
        notice.setPublisherName(reqVO.getPublisherName());
        notice.setPublishTime(reqVO.getPublishTime());
        notice.setInternalFlag(Boolean.TRUE.equals(reqVO.getInternalFlag()));
        notice.setPublicLevel(StringUtils.hasText(reqVO.getPublicLevel()) ? reqVO.getPublicLevel() : "SOCIAL");
        notice.setPdfFileId(reqVO.getPdfFileId());
        notice.setPdfSha256(reqVO.getPdfSha256());
        notice.setPdfSize(reqVO.getPdfSize());
    }

    /**
     * 更新（部分更新语义）：仅覆盖显式提交的字段；未提交字段保留库中原值。
     * 实测修复（2026-09-07）：此前无条件覆盖会把 internalFlag 翻回 false（企业内部公告泄到 SOCIAL 可见性）、
     * 清空 projectCode/projectName/publisherName 内存值（关注扇出 PROJECT 维度漏提醒、快照写空）。
     */
    private void applyBaseUpdate(NoticeDO notice, NoticeSaveReqVO reqVO) {
        notice.setTitle(reqVO.getTitle()); // VO @NotBlank：更新必传标题
        if (reqVO.getProjectCode() != null) {
            notice.setProjectCode(reqVO.getProjectCode());
        }
        if (reqVO.getProjectName() != null) {
            notice.setProjectName(reqVO.getProjectName());
        }
        if (reqVO.getPublisherName() != null) {
            notice.setPublisherName(reqVO.getPublisherName());
        }
        if (reqVO.getPublishTime() != null) {
            notice.setPublishTime(reqVO.getPublishTime());
        }
        if (reqVO.getInternalFlag() != null) {
            notice.setInternalFlag(Boolean.TRUE.equals(reqVO.getInternalFlag()));
        }
        if (StringUtils.hasText(reqVO.getPublicLevel())) {
            notice.setPublicLevel(reqVO.getPublicLevel());
        }
        if (reqVO.getPdfFileId() != null) {
            notice.setPdfFileId(reqVO.getPdfFileId());
        }
        if (reqVO.getPdfSha256() != null) {
            notice.setPdfSha256(reqVO.getPdfSha256());
        }
        if (reqVO.getPdfSize() != null) {
            notice.setPdfSize(reqVO.getPdfSize());
        }
    }

    private NoticeDO getNotice(Long id) {
        NoticeDO notice = id == null ? null : noticeMapper.selectById(id);
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_NOT_EXISTS);
        }
        return notice;
    }

    private void transit(NoticeDO notice, NoticeStatusEnum target) {
        NoticeStatusEnum current;
        try {
            current = NoticeStatusEnum.valueOf(notice.getStatus());
        } catch (Exception e) {
            current = NoticeStatusEnum.DRAFT;
        }
        if (!NoticeStatusEnum.canTransit(current, target)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_STATUS_TRANSITION_INVALID);
        }
        if (target == NoticeStatusEnum.PUBLISHED) {
            // 发布前提：正文存在 + PDF 原文件必须已保存（两种前端展示形态都基于它）
            if (latestContent(notice.getId()) == null) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_CONTENT_REQUIRED);
            }
            if (notice.getPdfFileId() == null) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_PDF_REQUIRED);
            }
        }
        notice.setStatus(target.name());
        // 公告新状态机（AnnouncementStatusEnum）同步维护：映射 DRAFT/DRAFT、PUBLISHED/PUBLISHED、UNPUBLISHED/WITHDRAWN、ARCHIVED/EXPIRED
        notice.setAnnouncementStatus(AnnouncementStatusEnum.fromNotice(target).name());
    }

    private NoticeTypeEnum requireType(String code) {
        NoticeTypeEnum type = NoticeTypeEnum.of(code);
        if (type == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_PAYLOAD_INVALID, "公告类型未登记：" + code);
        }
        return type;
    }

    private void checkRequired(NoticeTypeEnum type, LinkedHashMap<String, Object> canonical) {
        java.util.List<String> missing = NoticePayloadUtils.missingRequired(type, canonical);
        if (!missing.isEmpty()) {
            // 2026-09-10：缺失提示附中文名（code（中文名）），前端可按类型字段模板定位；格式向后兼容纯 code 断言
            StringBuilder sb = new StringBuilder("公告必填字段缺失（依据VO数据规范 必填=是，按类型 \"" + type.getLabel() + "\"）：");
            for (String code : missing) {
                String label = NoticePayloadUtils.labelOf(code);
                sb.append(code).append(label == null ? "" : "（" + label + "）").append("、");
            }
            sb.setLength(sb.length() - 1);
            throw new cn.iocoder.txgy.framework.common.exception.ServiceException(
                    new cn.iocoder.txgy.framework.common.exception.ErrorCode(
                            ErrorCodeConstants.NOTICE_PAYLOAD_INVALID.getCode(), sb.toString()));
        }
    }

    @Override
    public java.util.List<cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeTypeMetaRespVO> listTypeMeta() {
        java.util.List<cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeTypeMetaRespVO> result = new java.util.ArrayList<>();
        for (NoticeTypeEnum type : NoticeTypeEnum.values()) {
            cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeTypeMetaRespVO vo =
                    new cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeTypeMetaRespVO();
            vo.setType(type.name());
            vo.setName(type.getLabel());
            vo.setDataset(type.getDatasetClass() == null ? null : type.getDatasetClass().getSimpleName());
            java.util.Set<String> required = NoticePayloadUtils.requiredFieldNames(type);
            java.util.List<cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeTypeMetaRespVO.FieldMeta> fields =
                    new java.util.ArrayList<>();
            // 数据集 record 组件即规范字段全集（组件名=字段 code）；必填=是 的排前
            java.util.Set<String> ordered = new java.util.LinkedHashSet<>(required);
            java.lang.reflect.RecordComponent[] comps = type.getDatasetClass() == null
                    ? new java.lang.reflect.RecordComponent[0] : type.getDatasetClass().getRecordComponents();
            for (java.lang.reflect.RecordComponent c : comps) {
                ordered.add(c.getName());
            }
            for (String code : ordered) {
                fields.add(cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeTypeMetaRespVO.FieldMeta.of(
                        code, NoticePayloadUtils.labelOf(code), required.contains(code)));
            }
            vo.setFields(fields);
            result.add(vo);
        }
        return result;
    }

    // ========== 内容版本 ==========

    private NoticeContentDO latestContent(Long noticeId) {
        return noticeContentMapper.selectOne(new LambdaQueryWrapper<NoticeContentDO>()
                .eq(NoticeContentDO::getNoticeId, noticeId)
                .eq(NoticeContentDO::getContentType, CONTENT_TYPE_BODY)
                .orderByDesc(NoticeContentDO::getVersion)
                .last("LIMIT 1"));
    }

    private int appendContent(Long noticeId, String content) {
        NoticeContentDO latest = latestContent(noticeId);
        int nextVersion = latest == null ? 1 : latest.getVersion() + 1;
        NoticeContentDO contentDO = new NoticeContentDO();
        contentDO.setNoticeId(noticeId);
        contentDO.setContentType(CONTENT_TYPE_BODY);
        contentDO.setContent(content);
        contentDO.setSha256(sha256(content));
        contentDO.setVersion(nextVersion);
        noticeContentMapper.insert(contentDO);
        return nextVersion;
    }

    private List<NoticeRespVO> convertList(List<NoticeDO> notices) {
        List<NoticeRespVO> list = new ArrayList<>();
        if (notices.isEmpty()) {
            return list;
        }
        List<Long> ids = notices.stream().map(NoticeDO::getId).toList();
        Map<Long, String> previews = latestContentsByNoticeIds(ids);
        for (NoticeDO notice : notices) {
            NoticeRespVO vo = new NoticeRespVO();
            fillCommon(vo, notice, previews.get(notice.getId()));
            list.add(vo);
        }
        return list;
    }

    private void fillCommon(NoticeRespVO vo, NoticeDO notice, String content) {
        vo.setId(notice.getId());
        vo.setNoticeCode(notice.getNoticeCode());
        vo.setNoticeType(notice.getNoticeType());
        NoticeTypeEnum type = NoticeTypeEnum.of(notice.getNoticeType());
        vo.setNoticeTypeName(type == null ? notice.getNoticeType() : type.getLabel());
        vo.setTitle(notice.getTitle());
        vo.setProjectCode(notice.getProjectCode());
        vo.setProjectName(notice.getProjectName());
        vo.setPublisherName(notice.getPublisherName());
        vo.setPublishTime(notice.getPublishTime());
        vo.setStatus(notice.getStatus());
        vo.setAnnouncementStatus(notice.getAnnouncementStatus());
        vo.setInternalFlag(notice.getInternalFlag());
        vo.setPublicLevel(notice.getPublicLevel());
        if (content != null) {
            vo.setContentPreview(content.length() > 200 ? content.substring(0, 200) : content);
        }
    }

    private void fillCommon(NoticeDetailRespVO vo, NoticeDO notice, NoticeContentDO latest) {
        fillCommon((NoticeRespVO) vo, notice, latest == null ? null : latest.getContent());
        vo.setContent(latest == null ? null : latest.getContent());
        vo.setContentVersion(latest == null ? null : latest.getVersion());
        vo.setPdfFileId(notice.getPdfFileId());
        vo.setPdfSha256(notice.getPdfSha256());
        vo.setPdfSize(notice.getPdfSize());
    }

    private Map<Long, String> latestContentsByNoticeIds(List<Long> noticeIds) {
        Map<Long, String> result = new HashMap<>();
        if (noticeIds.isEmpty()) {
            return result;
        }
        List<NoticeContentDO> contents = noticeContentMapper.selectList(new LambdaQueryWrapper<NoticeContentDO>()
                .in(NoticeContentDO::getNoticeId, noticeIds)
                .eq(NoticeContentDO::getContentType, CONTENT_TYPE_BODY)
                .orderByDesc(NoticeContentDO::getVersion));
        for (NoticeContentDO content : contents) {
            result.putIfAbsent(content.getNoticeId(), content.getContent());
        }
        return result;
    }

    // ========== 检索文档同步（事务提交后） ==========

    private void indexDoc(Long noticeId) {
        NoticeDO notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            return;
        }
        NoticeEsDoc doc = new NoticeEsDoc();
        doc.setNoticeId(notice.getId());
        doc.setTenantId(notice.getTenantId());
        doc.setInternalFlag(notice.getInternalFlag());
        doc.setStatus(notice.getStatus());
        doc.setNoticeType(notice.getNoticeType());
        doc.setNoticeCode(notice.getNoticeCode());
        doc.setTitle(notice.getTitle());
        doc.setProjectCode(notice.getProjectCode());
        doc.setProjectName(notice.getProjectName());
        doc.setPublishTime(notice.getPublishTime() == null ? null
                : notice.getPublishTime().format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        NoticeContentDO latest = latestContent(noticeId);
        doc.setBody(latest == null ? "" : latest.getContent());
        noticeSearchEngine.index(doc);
    }

    private void afterCommit(Runnable runnable) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runnable.run();
                }
            });
        } else {
            runnable.run();
        }
    }

    // ========== 工具 ==========

    private String generateNoticeCode() {
        // 内部编号：B + 行政区划(6) + 年度(4) + 6位0-9a-z序列 = 17 位（附录B.3.2 序列规则）
        return biddingBizNoGenerator.generate(BiddingBizNoTypeEnum.NOTICE, null);
    }

    private String writePayload(Map<String, Object> payload) {
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_PAYLOAD_INVALID, e.getMessage());
        }
    }

    private LinkedHashMap<String, Object> readPayload(String json) {
        try {
            if (json == null) {
                return new LinkedHashMap<>();
            }
            return MAPPER.readValue(json, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (Exception e) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_PAYLOAD_INVALID, "载荷快照解析失败");
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(64);
            for (byte b : bytes) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
