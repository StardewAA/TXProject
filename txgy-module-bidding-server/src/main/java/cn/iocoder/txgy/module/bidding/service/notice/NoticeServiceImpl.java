package cn.iocoder.txgy.module.bidding.service.notice;
// 规范依据：《招投标模块开发规范》§4.4/4.5/4.8、五章三/五；VO数据规范必填；红线2/8；05

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
import cn.iocoder.txgy.module.bidding.enums.NoticeTypeEnum;
import cn.iocoder.txgy.module.bidding.service.notice.search.NoticeEsDoc;
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
            fanoutInternalVersionChange("UPDATED", notice.getId(), notice.getTitle(),
                    notice.getPublisherName(), notice.getProjectName(), notice.getProjectCode(), appendedVersion);
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
    @Transactional(rollbackFor = Exception.class)
    public void publishNotice(Long id) {
        subjectEligibilityService.assertTendererIssuanceAllowed(); // 招标人发布门：DRAFT→PUBLISHED 前复核主体资格
        NoticeDO notice = getNotice(id);
        transit(notice, NoticeStatusEnum.PUBLISHED);
        if (notice.getPublishTime() == null) {
            notice.setPublishTime(LocalDateTime.now());
        }
        noticeMapper.updateById(notice);
        afterCommit(() -> indexDoc(id));
        // 关注扇出：发布/重新发布成功 → 同租户关注者收到 NOTICE_PUBLISHED（版本=当前最新正文版本；重发同版本不重复提醒，见事件键含版本号）
        int version = latestContentVersion(id);
        fanoutInternalVersionChange("PUBLISHED", id, notice.getTitle(),
                notice.getPublisherName(), notice.getProjectName(), notice.getProjectCode(), version);
    }

    /** 内部公告关注扇出（失败仅记日志，不阻断公告本身的结果；见 FollowService.handleInternalNoticeChanged）。 */
    private void fanoutInternalVersionChange(String action, Long noticeId, String title, String publisherName,
                                             String projectName, String projectCode, int version) {
        try {
            if (followService != null && noticeId != null) {
                followService.handleInternalNoticeChanged(action, noticeId, title, publisherName,
                        projectName, projectCode, version);
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
                vo.setClarifyDeadline(strOrNull(payload.get("BID_DOC_REFER_END_TIME")));
                // 变更公告以 BID_END_TIME 覆盖递交截止
                String submit = NoticeTypeEnum.CHANGE_NOTICE.name().equals(notice.getNoticeType())
                        ? strOrNull(payload.get("BID_END_TIME"))
                        : strOrNull(payload.get("SUBMIT_TIMESTAMP"));
                vo.setSubmitDeadline(submit);
                vo.setBidOpenTime(strOrNull(payload.get("BID_OPEN_TIME")));
                return vo;
            }
        }
        throw ServiceExceptionUtil.exception(ErrorCodeConstants.NOTICE_DEADLINE_NOT_FOUND);
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

    // ========== 内部实现 ==========

    private PageResult<NoticeRespVO> queryPage(NoticePageReqVO reqVO, boolean publicOnly) {
        LambdaQueryWrapper<NoticeDO> wrapper = new LambdaQueryWrapper<NoticeDO>()
                .eq(StringUtils.hasText(reqVO.getNoticeType()), NoticeDO::getNoticeType, reqVO.getNoticeType())
                .eq(StringUtils.hasText(reqVO.getStatus()), NoticeDO::getStatus, reqVO.getStatus())
                .eq(reqVO.getInternalFlag() != null, NoticeDO::getInternalFlag, reqVO.getInternalFlag())
                .eq(StringUtils.hasText(reqVO.getPublicLevel()), NoticeDO::getPublicLevel, reqVO.getPublicLevel())
                .eq(StringUtils.hasText(reqVO.getProjectCode()), NoticeDO::getProjectCode, reqVO.getProjectCode())
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
            throw new cn.iocoder.txgy.framework.common.exception.ServiceException(
                    new cn.iocoder.txgy.framework.common.exception.ErrorCode(
                            ErrorCodeConstants.NOTICE_PAYLOAD_INVALID.getCode(),
                            "公告必填字段缺失（依据VO数据规范 必填=是）：" + String.join("、", missing)));
        }
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
        doc.setTitle(notice.getTitle());
        doc.setProjectCode(notice.getProjectCode());
        doc.setProjectName(notice.getProjectName());
        doc.setPublishTime(notice.getPublishTime() == null ? null : notice.getPublishTime()
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli());
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
        return "BN" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
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
