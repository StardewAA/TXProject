package cn.iocoder.txgy.module.bidding.service.noticeexternal;
// 规范依据：设计文档 v0.6 §7/§15.1/§15.3（SOCIAL+ACTIVE 过滤、ES 检索事件预留、不开放业务 LIKE）；
// 《招投标模块开发规范》§4.4、红线 8；01（公开边界）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManagePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalAttachmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalAttachmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalAuditStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalContentTypeEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.indexer.ExternalNoticeIndexNotifier;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.sanitize.HtmlSanitizer;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.search.ExternalNoticeSearchEngine;
import cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.UrlHostGuard;
import cn.iocoder.txgy.module.bidding.service.noticefollow.FollowService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 外部公告（G 轨聚合主档）服务实现（对外只读展示切片）。
 */
@Slf4j
@Service
public class NoticeExternalServiceImpl implements NoticeExternalService {

    /** 公开范围常量：社会公开（01 公开级别对齐 PublicLevelEnum.SOCIAL） */
    private static final String PUBLIC_SCOPE_SOCIAL = "SOCIAL";

    /** 外部公告扇出源标识（关注/提醒侧 source） */
    private static final String SOURCE_EXTERNAL = "EXTERNAL";

    /** 关键词全文检索命中上限（列表/详情回主表过滤，命中集不无限放大 IN 条件） */
    private static final int KEYWORD_MATCH_LIMIT = 200;

    private static final ObjectMapper JSON = new ObjectMapper();

    @Resource
    private NoticeExternalMapper noticeExternalMapper;
    @Resource
    private NoticeExternalAttachmentMapper noticeExternalAttachmentMapper;
    @Resource
    private FollowService followService;
    @Resource
    private ExternalNoticeIndexNotifier externalNoticeIndexNotifier;
    @Resource
    private ExternalNoticeSearchEngine externalNoticeSearchEngine;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.externalfetch.fetch.HttpFetchClient httpFetchClient;
    @Resource
    private cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper crawlSourceMapper;
    @Resource
    private cn.iocoder.txgy.module.infra.api.file.FileApi fileApi;

    @Override
    public void approveExternal(Long id) {
        // 存在性先查（错误码区分），再条件更新防丢失更新：并发下 reject/approve 谁先提交谁生效（审核操作竞态安全）
        NoticeExternalDO pre = noticeExternalMapper.selectById(id);
        if (pre == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        // 幂等：已生效（ACTIVE+APPROVED）直接返回；非待审核态不重放（恢复入口=同 URL 重新采集进审核，见 ingest 重投语义）
        if (NoticeExternalStatusEnum.ACTIVE.getStatus().equals(pre.getStatus())
                && NoticeExternalAuditStatusEnum.APPROVED.getStatus().equals(pre.getAuditStatus())) {
            return;
        }
        NoticeExternalDO patch = new NoticeExternalDO();
        patch.setStatus(NoticeExternalStatusEnum.ACTIVE.getStatus());
        patch.setAuditStatus(NoticeExternalAuditStatusEnum.APPROVED.getStatus());
        int rows = noticeExternalMapper.update(patch, new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getId, id)
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.NEW.getStatus())
                .eq(NoticeExternalDO::getAuditStatus, NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus()));
        if (rows == 0) {
            // 条件不满足：已被并发置为其它状态 → 拒绝覆盖（读到的旧快照不参与写回）
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_STATE_INVALID);
        }
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        // 扇出关注动态 + ES 索引事件：均为旁路通知，失败不阻断审核。
        // 注意：设计 §7.6 的补偿扫描任务尚未实现——以下失败仅记日志，需人工复核后重放（幂等 PUT/扇出语义保证重放安全）
        try {
            followService.handleNoticeActivated(notice, SOURCE_EXTERNAL);
        } catch (Exception e) {
            log.error("[approveExternal][noticeId={}] 关注扇出失败（不影响审核；补偿扫描未实现，需人工复核重放，见设计 §7.6）", id, e);
        }
        try {
            externalNoticeIndexNotifier.indexNotice(notice);
        } catch (Exception e) {
            log.error("[approveExternal][noticeId={}] ES 索引事件失败（不影响审核；补偿扫描未实现，需人工复核重放，见设计 §7.6）", id, e);
        }
    }

    @Override
    public void rejectExternal(Long id) {
        NoticeExternalDO pre = noticeExternalMapper.selectById(id);
        if (pre == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        if (NoticeExternalAuditStatusEnum.REJECTED.getStatus().equals(pre.getAuditStatus())) {
            return; // 幂等：已驳回
        }
        NoticeExternalDO patch = new NoticeExternalDO();
        patch.setAuditStatus(NoticeExternalAuditStatusEnum.REJECTED.getStatus());
        int rows = noticeExternalMapper.update(patch, new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getId, id)
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.NEW.getStatus())
                .eq(NoticeExternalDO::getAuditStatus, NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus()));
        if (rows == 0) {
            // 并发竞态：该记录已被 approve/takeDown 等推进 → 拒绝旧快照覆盖
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_STATE_INVALID);
        }
    }

    @Override
    public void takeDownExternal(Long id) {
        NoticeExternalDO pre = noticeExternalMapper.selectById(id);
        if (pre == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        if (NoticeExternalStatusEnum.INACTIVE.getStatus().equals(pre.getStatus())) {
            // 已下线（TAKEN_DOWN）：幂等返回；恢复入口=同 URL 内容更新重新采集（见 ingest 重投语义）
            return;
        }
        NoticeExternalDO patch = new NoticeExternalDO();
        patch.setStatus(NoticeExternalStatusEnum.INACTIVE.getStatus());
        patch.setAuditStatus(NoticeExternalAuditStatusEnum.TAKEN_DOWN.getStatus());
        int rows = noticeExternalMapper.update(patch, new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getId, id)
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.ACTIVE.getStatus())
                .eq(NoticeExternalDO::getAuditStatus, NoticeExternalAuditStatusEnum.APPROVED.getStatus()));
        if (rows == 0) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_STATE_INVALID);
        }
    }

    @Override
    public PageResult<NoticeExternalRespVO> getPage(NoticeExternalPageReqVO reqVO) {
        LambdaQueryWrapper<NoticeExternalDO> wrapper = new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getPublicScope, PUBLIC_SCOPE_SOCIAL)
                .eq(NoticeExternalDO::getStatus, NoticeExternalStatusEnum.ACTIVE.getStatus());
        // 关键词全文：走 ES 检索引擎（bidding-notice-external），命中集约束 id，再叠结构化过滤与分页
        List<Long> matchedIds = null;
        if (StringUtils.hasText(reqVO.getKeyword())) {
            matchedIds = externalNoticeSearchEngine.matchNoticeIds(reqVO.getKeyword(), KEYWORD_MATCH_LIMIT);
            if (matchedIds.isEmpty()) {
                return PageResult.empty();
            }
            wrapper.in(NoticeExternalDO::getId, matchedIds);
        }
        if (StringUtils.hasText(reqVO.getTitle())) {
            // 标题精确匹配筛选（01 契约口径；标题/正文模糊全文一律走 keyword=ES——红线 4.6，禁止业务表 LIKE）
            wrapper.eq(NoticeExternalDO::getTitle, reqVO.getTitle());
        }
        if (StringUtils.hasText(reqVO.getSourceCode())) {
            wrapper.eq(NoticeExternalDO::getSourceCode, reqVO.getSourceCode());
        }
        if (StringUtils.hasText(reqVO.getContentType())) {
            wrapper.eq(NoticeExternalDO::getContentType, reqVO.getContentType());
        }
        if (StringUtils.hasText(reqVO.getIndustryFlag())) {
            wrapper.eq(NoticeExternalDO::getIndustryFlag, reqVO.getIndustryFlag());
        }
        if (StringUtils.hasText(reqVO.getNoticeTypeRaw())) {
            // 公告类型（外站原文，如“招标公告/中标候选人公示/成交结果公告”）；权威码维度后续按 notice_type_code 检索
            wrapper.eq(NoticeExternalDO::getNoticeTypeRaw, reqVO.getNoticeTypeRaw());
        }
        if (StringUtils.hasText(reqVO.getProjectCode())) {
            wrapper.eq(NoticeExternalDO::getProjectCode, reqVO.getProjectCode());
        }
        if (reqVO.getBeginTime() != null) {
            wrapper.ge(NoticeExternalDO::getPublishTime, reqVO.getBeginTime());
        }
        if (reqVO.getEndTime() != null) {
            wrapper.le(NoticeExternalDO::getPublishTime, reqVO.getEndTime());
        }
        wrapper.orderByDesc(NoticeExternalDO::getPublishTime);
        IPage<NoticeExternalDO> page = noticeExternalMapper.selectPage(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toRespVO).toList(), page.getTotal());
    }

    @Override
    public PageResult<NoticeExternalManageRespVO> getManagePage(NoticeExternalManagePageReqVO reqVO) {
        // 管理分页：不做 SOCIAL+ACTIVE 硬过滤（N1：公开 page 只出 ACTIVE+SOCIAL 无法支撑审核页；本方法出全状态/审核态行）
        LambdaQueryWrapper<NoticeExternalDO> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(reqVO.getStatus())) {
            wrapper.eq(NoticeExternalDO::getStatus, reqVO.getStatus());
        }
        if (StringUtils.hasText(reqVO.getAuditStatus())) {
            wrapper.eq(NoticeExternalDO::getAuditStatus, reqVO.getAuditStatus());
        }
        if (StringUtils.hasText(reqVO.getSourceCode())) {
            wrapper.eq(NoticeExternalDO::getSourceCode, reqVO.getSourceCode());
        }
        if (StringUtils.hasText(reqVO.getContentType())) {
            wrapper.eq(NoticeExternalDO::getContentType, reqVO.getContentType());
        }
        if (StringUtils.hasText(reqVO.getNoticeTypeRaw())) {
            wrapper.eq(NoticeExternalDO::getNoticeTypeRaw, reqVO.getNoticeTypeRaw());
        }
        if (StringUtils.hasText(reqVO.getIndustryFlag())) {
            wrapper.eq(NoticeExternalDO::getIndustryFlag, reqVO.getIndustryFlag());
        }
        if (StringUtils.hasText(reqVO.getProjectCode())) {
            wrapper.eq(NoticeExternalDO::getProjectCode, reqVO.getProjectCode());
        }
        if (reqVO.getBeginTime() != null) {
            wrapper.ge(NoticeExternalDO::getPublishTime, reqVO.getBeginTime());
        }
        if (reqVO.getEndTime() != null) {
            wrapper.le(NoticeExternalDO::getPublishTime, reqVO.getEndTime());
        }
        // 管理列表：同发布时间按下线先后排（NEW 在无发布时间的行前按创建时间），保证分页稳定（主键兜底序）
        wrapper.orderByDesc(NoticeExternalDO::getPublishTime).orderByDesc(NoticeExternalDO::getId);
        IPage<NoticeExternalDO> page = noticeExternalMapper.selectPage(
                Page.of(reqVO.getPageNo(), reqVO.getPageSize()), wrapper);
        return new PageResult<>(page.getRecords().stream().map(this::toManageRespVO).toList(), page.getTotal());
    }

    @Override
    public NoticeExternalManageDetailRespVO getManageDetail(Long id) {
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        NoticeExternalManageDetailRespVO resp = new NoticeExternalManageDetailRespVO();
        resp.setId(notice.getId());
        resp.setSourceCode(notice.getSourceCode());
        resp.setSourceName(notice.getSourceName());
        resp.setTitle(notice.getTitle());
        resp.setOrigUrl(notice.getOrigUrl());
        resp.setContentType(notice.getContentType());
        resp.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        resp.setLegalFlag(notice.getLegalFlag());
        resp.setIndustryFlag(notice.getIndustryFlag());
        resp.setIndustryConfidence(notice.getIndustryConfidence());
        resp.setStatus(notice.getStatus());
        resp.setAuditStatus(notice.getAuditStatus());
        resp.setPublicScope(notice.getPublicScope());
        boolean html = NoticeExternalContentTypeEnum.HTML.getType().equals(notice.getContentType());
        resp.setContentHtmlSafe(html ? HtmlSanitizer.sanitize(notice.getContentHtml()) : null);
        resp.setBodyBlocksStatus(notice.getBodyBlocksStatus());
        resp.setBodyParserCode(notice.getBodyParserCode());
        resp.setBodyBlocksJson(notice.getBodyBlocksJson());
        resp.setPdfFileId(notice.getPdfFileId());
        resp.setPdfSha256(notice.getPdfSha256());
        resp.setPdfSize(notice.getPdfSize());
        resp.setPdfDownloadUrl(notice.getPdfDownloadUrl());
        resp.setExtractionStatus(notice.getExtractionStatus());
        resp.setExtractionSummary(summarize(notice.getExtractionText()));
        resp.setAiExtractionStatus(notice.getAiExtractionStatus());
        resp.setAiMetaFieldCount(extractMetaFieldCount(notice.getAiMetaJson()));
        resp.setIssuerName(notice.getIssuerName());
        resp.setProjectKey(cleanNullable(notice.getProjectCode()));
        resp.setProjectName(notice.getProjectName());
        resp.setTenderProjectKey(cleanNullable(notice.getTenderProjectCode()));
        resp.setBidSectionKeys(NoticeTrackKeyUtils.splitSectionCodes(cleanNullable(notice.getBidSectionCodes())));
        resp.setBidSectionCodes(cleanNullable(notice.getBidSectionCodes()));
        resp.setPublishTime(notice.getPublishTime());
        resp.setEffectiveFrom(notice.getEffectiveFrom());
        resp.setEffectiveTo(notice.getEffectiveTo());
        resp.setFetchCount(notice.getFetchCount());
        resp.setLastFetchTime(notice.getLastFetchTime());
        resp.setCreateTime(notice.getCreateTime());
        resp.setUpdateTime(notice.getUpdateTime());
        // 附件（BR-7）：病毒 INFECTED 不下发下载链接（安全评审意见 1/2 同公开口径），virusStatus 照回供审核识别
        List<NoticeExternalAttachmentDO> attachments = noticeExternalAttachmentMapper
                .selectList(new LambdaQueryWrapper<NoticeExternalAttachmentDO>()
                        .eq(NoticeExternalAttachmentDO::getNoticeId, id)
                        .orderByAsc(NoticeExternalAttachmentDO::getId));
        List<NoticeExternalAttachmentDO> downloadable = attachments.stream()
                .filter(a -> !"INFECTED".equalsIgnoreCase(a.getVirusStatus()))
                .toList();
        if (!downloadable.isEmpty()) {
            resp.setAttachments(downloadable.stream().map(this::toManageAttachmentVO).toList());
        }
        return resp;
    }

    /** 抽取正文摘要：前 200 字符（无文本 null）。 */
    private String summarize(String extractionText) {
        if (!StringUtils.hasText(extractionText)) {
            return null;
        }
        String text = extractionText.trim();
        return text.length() > 200 ? text.substring(0, 200) + "…" : text;
    }

    /** ai_meta_json 中 metaFieldCount 读取（结构不符/缺失返回 null，不阻断预览）。 */
    private Integer extractMetaFieldCount(String aiMetaJson) {
        if (!StringUtils.hasText(aiMetaJson)) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(aiMetaJson).get("metaFieldCount");
            return node != null && node.isNumber() ? node.asInt() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private NoticeExternalManageDetailRespVO.Attachment toManageAttachmentVO(NoticeExternalAttachmentDO attachment) {
        NoticeExternalManageDetailRespVO.Attachment vo = new NoticeExternalManageDetailRespVO.Attachment();
        vo.setId(attachment.getId());
        vo.setName(attachment.getName());
        vo.setExt(attachment.getExt());
        vo.setFileId(attachment.getFileId());
        vo.setSha256(attachment.getSha256());
        vo.setSize(attachment.getSize());
        vo.setOrigDownloadUrl(attachment.getOrigDownloadUrl());
        vo.setVirusStatus(attachment.getVirusStatus());
        return vo;
    }

    private NoticeExternalManageRespVO toManageRespVO(NoticeExternalDO notice) {
        NoticeExternalManageRespVO resp = new NoticeExternalManageRespVO();
        resp.setId(notice.getId());
        resp.setSourceCode(notice.getSourceCode());
        resp.setSourceName(notice.getSourceName());
        resp.setTitle(notice.getTitle());
        resp.setOrigUrl(notice.getOrigUrl());
        resp.setContentType(notice.getContentType());
        resp.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        resp.setLegalFlag(notice.getLegalFlag());
        resp.setIndustryFlag(notice.getIndustryFlag());
        resp.setIndustryConfidence(notice.getIndustryConfidence());
        resp.setIssuerName(notice.getIssuerName());
        resp.setProjectName(notice.getProjectName());
        resp.setBidSectionCodes(notice.getBidSectionCodes());
        resp.setStatus(notice.getStatus());
        resp.setAuditStatus(notice.getAuditStatus());
        resp.setExtractionStatus(notice.getExtractionStatus());
        resp.setAiExtractionStatus(notice.getAiExtractionStatus());
        resp.setBodyBlocksStatus(notice.getBodyBlocksStatus());
        resp.setBodyParserCode(notice.getBodyParserCode());
        resp.setPublishTime(notice.getPublishTime());
        resp.setEffectiveFrom(notice.getEffectiveFrom());
        resp.setEffectiveTo(notice.getEffectiveTo());
        resp.setFetchCount(notice.getFetchCount());
        resp.setLastFetchTime(notice.getLastFetchTime());
        resp.setCreateTime(notice.getCreateTime());
        resp.setUpdateTime(notice.getUpdateTime());
        return resp;
    }

    @Override
    public NoticeExternalDetailRespVO getDetail(Long id) {
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        // 公开详情只放行 SOCIAL + ACTIVE（01 规则 1 + 红线 2 兜底）
        if (!Objects.equals(notice.getPublicScope(), PUBLIC_SCOPE_SOCIAL)
                || !NoticeExternalStatusEnum.ACTIVE.getStatus().equals(notice.getStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_TAKEN_DOWN);
        }
        NoticeExternalDetailRespVO resp = new NoticeExternalDetailRespVO();
        copyToDetail(notice, resp);
        // 附件下载列表（FileApi 签名 downloadUrl 待 infra 文件下载能力接入后回填，先给 file_id/溯源链接）
        // 安全评审意见 1/2：病毒状态=INFECTED 的附件一律不下发链接（扫描闭环由 FileApi 病毒扫描保证）
        List<NoticeExternalAttachmentDO> attachments = noticeExternalAttachmentMapper
                .selectList(new LambdaQueryWrapper<NoticeExternalAttachmentDO>()
                        .eq(NoticeExternalAttachmentDO::getNoticeId, id)
                        .orderByAsc(NoticeExternalAttachmentDO::getId));
        List<NoticeExternalAttachmentDO> downloadable = attachments.stream()
                .filter(a -> !"INFECTED".equalsIgnoreCase(a.getVirusStatus()))
                .toList();
        if (!downloadable.isEmpty()) {
            resp.setAttachments(downloadable.stream().map(this::toAttachmentVO).toList());
        }
        return resp;
    }

    private NoticeExternalRespVO toRespVO(NoticeExternalDO notice) {
        NoticeExternalRespVO resp = new NoticeExternalRespVO();
        resp.setId(notice.getId());
        resp.setSourceCode(notice.getSourceCode());
        resp.setSourceName(notice.getSourceName());
        resp.setTitle(notice.getTitle());
        resp.setOrigUrl(notice.getOrigUrl());
        resp.setContentType(notice.getContentType());
        resp.setLegalFlag(notice.getLegalFlag());
        resp.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        resp.setIndustryFlag(notice.getIndustryFlag());
        resp.setIssuerName(notice.getIssuerName());
        resp.setPublishTime(notice.getPublishTime());
        resp.setEffectiveFrom(notice.getEffectiveFrom());
        resp.setEffectiveTo(notice.getEffectiveTo());
        resp.setStatus(notice.getStatus());
        copyTrackKeys(notice, resp);
        return resp;
    }

    /** 跟踪维度键下发（BR-1）：project/tender 键 + 标段键列表；名称一期仅有 projectName 快照，其余可为 null。
     *  存量历史脏值字面 "NULL"（采集侧 ORM 拼接产物）一律按 null 下发，避免前端误当可关注键。 */
    private void copyTrackKeys(NoticeExternalDO notice, NoticeExternalRespVO resp) {
        resp.setProjectKey(cleanNullable(notice.getProjectCode()));
        resp.setProjectName(notice.getProjectName());
        resp.setTenderProjectKey(cleanNullable(notice.getTenderProjectCode()));
        resp.setTenderProjectName(null);
        resp.setBidSectionKeys(NoticeTrackKeyUtils.splitSectionCodes(cleanNullable(notice.getBidSectionCodes())));
        resp.setBidSectionNames(null);
        resp.setBidSectionCodes(cleanNullable(notice.getBidSectionCodes()));
    }

    /** 字面 "NULL"/空白 → null（归一采集侧脏值；见 ExternalNoticeIngestServiceImpl.normalizeNullStringFields 的存量场景）。 */
    private String cleanNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() || "NULL".equalsIgnoreCase(trimmed) ? null : trimmed;
    }

    private void copyToDetail(NoticeExternalDO notice, NoticeExternalDetailRespVO resp) {
        resp.setId(notice.getId());
        resp.setSourceCode(notice.getSourceCode());
        resp.setSourceName(notice.getSourceName());
        resp.setTitle(notice.getTitle());
        resp.setOrigUrl(notice.getOrigUrl());
        resp.setContentType(notice.getContentType());
        boolean html = NoticeExternalContentTypeEnum.HTML.getType().equals(notice.getContentType());
        // HTML 形态：留档原样仅存库（供抽取/审计）——公开匿名详情不下发原样，统一净化版渲染（安全评审意见 1：存储型 XSS 载荷不主动下发公众客户端）
        resp.setContentHtml(null);
        resp.setContentHtmlSafe(html ? HtmlSanitizer.sanitize(notice.getContentHtml()) : null);
        // PDF 形态：file_id+sha+size（前端 JS 预览）；pdfDownloadUrl=平台归档 URL（爬取 PDF 已上传文件服务），前端直接预览/下载
        resp.setPdfFileId(notice.getPdfFileId());
        resp.setPdfSha256(notice.getPdfSha256());
        resp.setPdfSize(notice.getPdfSize());
        resp.setPdfDownloadUrl(notice.getPdfDownloadUrl());
        resp.setExtractionStatus(notice.getExtractionStatus());
        resp.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        resp.setLegalFlag(notice.getLegalFlag());
        resp.setIndustryFlag(notice.getIndustryFlag());
        resp.setBodyParserCode(notice.getBodyParserCode());
        resp.setBodyBlocksStatus(notice.getBodyBlocksStatus());
        resp.setBodyBlocksJson(notice.getBodyBlocksJson());
        copyDetailTrackKeys(notice, resp);
        resp.setIssuerName(notice.getIssuerName());
        resp.setPublishTime(notice.getPublishTime());
        resp.setEffectiveFrom(notice.getEffectiveFrom());
        resp.setEffectiveTo(notice.getEffectiveTo());
    }

    /** 详情 VO 跟踪键（BR-1）：在既有 projectName/bidSectionCodes 快照字段外新增键列表下发（脏值 "NULL" 归一 null）。 */
    private void copyDetailTrackKeys(NoticeExternalDO notice, NoticeExternalDetailRespVO resp) {
        resp.setProjectKey(cleanNullable(notice.getProjectCode()));
        resp.setProjectName(notice.getProjectName());
        resp.setTenderProjectKey(cleanNullable(notice.getTenderProjectCode()));
        resp.setTenderProjectName(null);
        resp.setBidSectionKeys(NoticeTrackKeyUtils.splitSectionCodes(cleanNullable(notice.getBidSectionCodes())));
        resp.setBidSectionNames(null);
        resp.setBidSectionCodes(cleanNullable(notice.getBidSectionCodes()));
    }

    private NoticeExternalDetailRespVO.Attachment toAttachmentVO(NoticeExternalAttachmentDO attachment) {
        NoticeExternalDetailRespVO.Attachment vo = new NoticeExternalDetailRespVO.Attachment();
        vo.setId(attachment.getId());
        vo.setName(attachment.getName());
        vo.setExt(attachment.getExt());
        vo.setFileId(attachment.getFileId());
        vo.setSha256(attachment.getSha256());
        vo.setSize(attachment.getSize());
        vo.setOrigDownloadUrl(attachment.getOrigDownloadUrl());
        vo.setVirusStatus(attachment.getVirusStatus());
        // downloadUrl：FileApi 签名下载地址（TODO：接入 infra 文件下载后回填）
        return vo;
    }

    /** 存量 PDF 归档回填：见接口注释。单条失败记日志继续；meta_json 无外站 pdfDownloadUrl 的计 skipped。 */
    @Override
    public String backfillPdfDownloadUrls() {
        List<NoticeExternalDO> pdfs = noticeExternalMapper.selectList(new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getContentType, "PDF")
                .and(w -> w.isNull(NoticeExternalDO::getPdfDownloadUrl).or().eq(NoticeExternalDO::getPdfDownloadUrl, "")));
        int ok = 0;
        int failed = 0;
        int skipped = 0;
        for (NoticeExternalDO pdf : pdfs) {
            String externalUrl = null;
            try {
                externalUrl = pdfMetaPdfDownloadUrl(pdf.getMetaJson());
                if (!StringUtils.hasText(externalUrl)) {
                    skipped++;
                    log.warn("[backfillPdf][id={}] meta_json 无外站 pdfDownloadUrl，跳过", pdf.getId());
                    continue;
                }
                // SSRF 防护：重下载外站 PDF 前校验主机白名单（行 source_code → 源配置 base_url ∪ allowed_hosts）
                assertPdfDownloadAllowed(pdf, externalUrl);
                byte[] bytes = httpFetchClient.getBytes(externalUrl);
                if (bytes == null || bytes.length == 0) {
                    failed++;
                    log.warn("[backfillPdf][id={}] 重下载为空 {}", pdf.getId(), externalUrl);
                    continue;
                }
                String safeName = StringUtils.hasText(pdf.getTitle())
                        ? pdf.getTitle().replaceAll("[\\/:*?\"<>|]", "_") : "notice";
                if (safeName.length() > 80) {
                    safeName = safeName.substring(0, 80);
                }
                String url = fileApi.createFile(bytes, safeName + ".pdf", "bidding/notice-external", "application/pdf");
                if (!StringUtils.hasText(url)) {
                    failed++;
                    continue;
                }
                NoticeExternalDO patch = new NoticeExternalDO();
                patch.setPdfDownloadUrl(url);
                noticeExternalMapper.updateById(patch.setId(pdf.getId()));
                ok++;
                log.info("[backfillPdf][id={}] 回填 pdf_download_url={}", pdf.getId(), url);
            } catch (Exception e) {
                failed++;
                log.warn("[backfillPdf][id={}] 回填失败 url={} err={}", pdf.getId(), externalUrl, e.getMessage());
            }
        }
        return "total=" + pdfs.size() + ",ok=" + ok + ",failed=" + failed + ",skipped=" + skipped;
    }

    /** 重下载主机白名单校验：平台文件端点豁免；外站 URL 须在行 source_code 对应采集源白名单内（源缺失=拒绝，防脏数据外链 SSRF） */
    private void assertPdfDownloadAllowed(NoticeExternalDO pdf, String externalUrl) {
        if (UrlHostGuard.isInternalFileUrl(externalUrl)) {
            return; // 平台自产文件端点
        }
        cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO source = crawlSourceMapper.selectOne(
                new LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO>()
                        .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO::getSourceCode, pdf.getSourceCode())
                        .last("LIMIT 1"));
        if (source == null) {
            throw new IllegalStateException("PDF 行无对应采集源配置，拒绝重下载（SSRF 防护），id=" + pdf.getId());
        }
        if (!UrlHostGuard.isExternalAllowed(externalUrl, source.getBaseUrl(), source.getAllowedHosts())) {
            throw new IllegalStateException("PDF 直链不在采集源白名单（SSRF 防护），url=" + externalUrl);
        }
    }

    /** 读 meta_json 中的外站 pdfDownloadUrl（缺失/解析失败返回 null）。 */
    private String pdfMetaPdfDownloadUrl(String metaJson) {
        if (!StringUtils.hasText(metaJson)) {
            return null;
        }
        try {
            com.fasterxml.jackson.databind.JsonNode node = JSON.readTree(metaJson).get("pdfDownloadUrl");
            return node != null && node.isTextual() ? node.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }


    @Override
    public byte[] pdfContent(Long id) {
        NoticeExternalDO notice = noticeExternalMapper.selectById(id);
        if (notice == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_NOT_EXISTS);
        }
        if (!"PDF".equalsIgnoreCase(notice.getContentType()) || !StringUtils.hasText(notice.getPdfDownloadUrl())) {
            return null; // 非 PDF 或无归档：调用方按 404 处理
        }
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(notice.getPdfDownloadUrl()))
                    .timeout(java.time.Duration.ofSeconds(60))
                    .GET().build();
            java.net.http.HttpResponse<byte[]> resp = client.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null) {
                return resp.body();
            }
            log.warn("[pdfContent][id={}] 文件服务返回 HTTP {}", id, resp.statusCode());
            return null;
        } catch (Exception e) {
            log.warn("[pdfContent][id={}] PDF 获取失败: {}", id, e.getMessage());
            return null;
        }
    }

}
