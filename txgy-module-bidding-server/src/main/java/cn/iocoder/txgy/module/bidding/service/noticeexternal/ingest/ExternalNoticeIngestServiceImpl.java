package cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest;
// 规范依据：设计文档 v0.6 §五.1/§七/§15.1（主列稳定+meta_json 兜底、附件子表、origUrlHash 唯一幂等、NEW 待审核）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalAttachmentDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.noticeexternal.NoticeExternalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalAttachmentMapper;
import cn.iocoder.txgy.module.bidding.dal.mysql.noticeexternal.NoticeExternalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalAuditStatusEnum;
import cn.iocoder.txgy.module.bidding.enums.NoticeExternalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.NoticePublishTimeResolver;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 采集落库实现（status=NEW + audit=UNREVIEWED 进审核队列；展示等审核通过）。
 * <p>安全评审加固：入库前校验 URL 协议白名单（http/https）、metaJson 合法性、字段长度上限（防 XSS 载体/污染/DoS），
 * 详见 objective(3)；HTML 原文仍“原样留档”（权威要求），展示侧由 HtmlSanitizer 净化。
 */
@Slf4j
@Service
public class ExternalNoticeIngestServiceImpl implements ExternalNoticeIngestService {

    /** 长度上限（字符，对齐 DDL 与防 DoS；超限拒绝入库 106_013） */
    private static final int MAX_TITLE = 512;
    private static final int MAX_ORIG_URL = 1024;
    private static final int MAX_HTML = 8 * 1024 * 1024;
    private static final int MAX_META_JSON = 1024 * 1024;
    private static final int MAX_EXTRA_RAW = 4 * 1024 * 1024;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** 联调清单 R11：把 DTO 上整串为 "NULL"（忽略大小写）的可空 String 字段归一为 null；
     *  常见于采集侧 ORM/拼接把空值写成字串 "NULL"，直接落库会污染展示/筛选与清洗口径。 */
    private static void normalizeNullStringFields(ExternalNoticeIngestDTO dto) {
        for (Field field : ExternalNoticeIngestDTO.class.getDeclaredFields()) {
            if (field.getType() == String.class) {
                try {
                    field.setAccessible(true);
                    Object value = field.get(dto);
                    if (value != null && "NULL".equalsIgnoreCase((String) value)) {
                        field.set(dto, null);
                    }
                } catch (IllegalAccessException ignore) {
                    // 不可写字段跳过（不影响主流程）
                }
            }
        }
    }

    @Resource
    private NoticeExternalMapper noticeExternalMapper;
    @Resource
    private NoticeExternalAttachmentMapper noticeExternalAttachmentMapper;
    @Resource
    private cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch.CrawlSourceMapper crawlSourceMapper;

    /**
     * 采集落库（同 URL 幂等 + 内容变更/终态重投语义，设计 §三/§12.2）。
     *
     * <p>同 URL 再次产出（urlHash 幂等判定）按“既有记录状态 × 内容是否变化”分支：
     * <ul>
     *   <li>NEW 待审 / ACTIVE 展示中 且内容未变 → 幂等返回既有主档（重复消费安全，不打扰）；</li>
     *   <li>REJECTED（驳回）→ 重新排队进审核（重置 NEW+UNREVIEWED，覆盖为最新内容）；</li>
     *   <li>ACTIVE 展示中 且内容变化（源站更新）→ 覆盖内容并打回 NEW+UNREVIEWED 重新审核
     *       （防源站投毒新内容直接公开展示；审核通过即恢复展示）；</li>
     *   <li>TAKEN_DOWN 已下线 且内容变化 → 同“源站更新”重新排队；内容未变则保持下线不复活。</li>
     * </ul>
     * 内容变化以 content_hash 内容指纹判定（采集侧未给 hash 时由本类按正文关键字段计算）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long ingest(ExternalNoticeIngestDTO dto) {
        // 联调清单 R11：采集/历史数据常见 ORM 拼接把 null 写成字串 "NULL"（污染展示/检索/清洗口径）→ 写入前统一归一
        normalizeNullStringFields(dto);
        // sourceName 快照兜底（P2-3）：上游未传时按 sourceCode 取源配置当前名（存量 NULL 由 sql/2026-09-10-source-name-backfill.sql 回填）
        if (!StringUtils.hasText(dto.getSourceName()) && StringUtils.hasText(dto.getSourceCode())) {
            cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO src = crawlSourceMapper.selectOne(
                    new LambdaQueryWrapper<cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO>()
                            .eq(cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlSourceDO::getSourceCode, dto.getSourceCode())
                            .last("LIMIT 1"));
            if (src != null) {
                dto.setSourceName(src.getSourceName());
            }
        }
        // 主列必填校验（采集产出不齐不落库，宁缺毋滥）
        if (!StringUtils.hasText(dto.getOrigUrl()) || !StringUtils.hasText(dto.getTitle())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_INVALID);
        }
        validatePayload(dto);
        String urlHash = StringUtils.hasText(dto.getOrigUrlHash())
                ? dto.getOrigUrlHash() : sha256Hex(dto.getOrigUrl());
        dto.setOrigUrlHash(urlHash);
        String contentHash = StringUtils.hasText(dto.getContentHash())
                ? dto.getContentHash() : computeContentHash(dto);
        dto.setContentHash(contentHash);
        // origUrlHash 唯一去重：同 URL 既有记录按状态×内容变化分支处理（设计 §三/§12.2）
        NoticeExternalDO existing = selectByUrlHash(urlHash);
        if (existing != null) {
            return reuseOrResubmit(existing, dto, urlHash);
        }
        try {
            NoticeExternalDO notice = buildNotice(dto, urlHash, contentHash);
            noticeExternalMapper.insert(notice);
            String idsJson = insertAttachments(notice.getId(), dto);
            if (idsJson != null) {
                notice.setAttachmentIdsJson(idsJson);
                noticeExternalMapper.updateById(notice);
            }
            log.info("[ingest][noticeId={} urlHash={}] 外部公告落库（NEW 待审核）", notice.getId(), urlHash);
            return notice.getId();
        } catch (DuplicateKeyException e) {
            // 并发同 URL 双投：撞 uk_external_orig_url_hash → 回读既有行走幂等/重投分支（不抛裸异常）
            NoticeExternalDO raced = selectByUrlHash(urlHash);
            if (raced == null) {
                throw e;
            }
            return reuseOrResubmit(raced, dto, urlHash);
        }
    }

    /**
     * 同 URL 既有记录：幂等返回或覆盖重置为 NEW+UNREVIEWED。
     */
    private Long reuseOrResubmit(NoticeExternalDO existing, ExternalNoticeIngestDTO dto, String urlHash) {
        String newHash = dto.getContentHash();
        boolean same = Objects.equals(newHash, existing.getContentHash());
        boolean active = NoticeExternalStatusEnum.ACTIVE.getStatus().equals(existing.getStatus());
        boolean unReviewed = NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus().equals(existing.getAuditStatus());
        boolean rejected = NoticeExternalAuditStatusEnum.REJECTED.getStatus().equals(existing.getAuditStatus());
        if ((active || (NoticeExternalStatusEnum.NEW.getStatus().equals(existing.getStatus()) && unReviewed))
                && same) {
            // 展示中/待审中 且内容未变：幂等跳过（重复消费靠 urlHash 幂等，设计 §三）
            log.info("[ingest][urlHash={}] 主档已存在 noticeId={} 且内容未变，幂等返回", urlHash, existing.getId());
            return existing.getId();
        }
        if (NoticeExternalStatusEnum.INACTIVE.getStatus().equals(existing.getStatus()) && same && !rejected) {
            // 已下线（TAKEN_DOWN）且内容未变：不复活（恢复需源站更新内容重新采集）
            log.info("[ingest][urlHash={}] 主档已下线 noticeId={} 且内容未变，保持下线不复活", urlHash, existing.getId());
            return existing.getId();
        }
        // 变化了（含 ACTIVE 源站更新、TAKEN_DOWN 更新）或 REJECTED 重投 → 覆盖为最新内容并重新排队审核
        overwriteNotice(existing, dto, urlHash);
        rebuildAttachments(existing.getId(), dto);
        log.info("[ingest][urlHash={}] 同 URL 重投：覆盖内容并重置待审核 noticeId={}（status=NEW+UNREVIEWED）",
                urlHash, existing.getId());
        return existing.getId();
    }

    /** 覆盖主档 payload（保留 id/审计痕迹列；状态重置 NEW+UNREVIEWED，内容指纹同步）。
     *  2026-09-09 审查 A6/S3/C2 修复：覆盖=新内容，必须统一清场——正文结构化(A6)、AI 产物(C2)、
     *  行业判定 flag/confidence 配对(C2)、HTML/PDF 形态残留(S3) 全部清空，防前台/ES 渲染旧产物。 */
    private void overwriteNotice(NoticeExternalDO existing, ExternalNoticeIngestDTO dto, String urlHash) {
        NoticeExternalDO fresh = buildNotice(dto, urlHash, dto.getContentHash());
        // 用 UpdateWrapper 显式更新（含 null 清场列；避免 MP 默认忽略 null 导致旧值残留）
        com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<NoticeExternalDO> uw =
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
        uw.eq("id", existing.getId())
          .set("source_code", fresh.getSourceCode())
          .set("source_name", fresh.getSourceName())
          .set("title", fresh.getTitle())
          .set("orig_url", fresh.getOrigUrl())
          .set("content_hash", fresh.getContentHash())
          .set("content_type", fresh.getContentType())
          .set("content_html", fresh.getContentHtml())
          .set("pdf_file_id", fresh.getPdfFileId())
          .set("pdf_sha256", fresh.getPdfSha256())
          .set("pdf_size", fresh.getPdfSize())
          .set("pdf_download_url", fresh.getPdfDownloadUrl())
          .set("notice_type_raw", fresh.getNoticeTypeRaw())
          .set("legal_flag", fresh.getLegalFlag())
          .set("industry_flag", fresh.getIndustryFlag())
          .set("industry_confidence", fresh.getIndustryConfidence())
          .set("project_code", fresh.getProjectCode())
          .set("tender_project_code", fresh.getTenderProjectCode())
          .set("bid_section_codes", fresh.getBidSectionCodes())
          .set("project_name", fresh.getProjectName())
          .set("issuer_name", fresh.getIssuerName())
          .set("effective_from", fresh.getEffectiveFrom())
          .set("effective_to", fresh.getEffectiveTo())
          .set("parser_code", fresh.getParserCode())
          .set("meta_json", fresh.getMetaJson())
          .set("extra_raw", fresh.getExtraRaw())
          .set("status", NoticeExternalStatusEnum.NEW.getStatus())
          .set("audit_status", NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus())
          .set("fetch_count", (existing.getFetchCount() == null ? 0 : existing.getFetchCount()) + 1)
          .set("last_fetch_time", LocalDateTime.now())
          // ---- 统一清场：新内容待重新结构化/AI/判定/抽取（审查 A6/S3/C2） ----
          .set("extraction_status", "NONE")
          .set("extraction_text", null)
          .set("extraction_tables_json", null)
          .set("extraction_at", null)
          .set("ai_extraction_status", "NONE")
          .set("ai_meta_json", null)
          .set("ai_extract_at", null)
          .set("body_parser_code", null)
          .set("body_blocks_status", "NONE")
          .set("body_blocks_json", null)
          .set("body_blocks_at", null);
        // 2026-09-10：发布时间部分更新语义——采集侧解析到真实时间才覆盖（防把既有真实值/AI 回填值打回 null）
        if (fresh.getPublishTime() != null) {
            uw.set("publish_time", fresh.getPublishTime());
        }
        noticeExternalMapper.update(null, uw);
        // 同步内存对象（供后续附件重建等使用）
        existing.setContentType(fresh.getContentType());
        existing.setContentHtml(fresh.getContentHtml());
        existing.setPdfDownloadUrl(fresh.getPdfDownloadUrl());
        existing.setStatus(NoticeExternalStatusEnum.NEW.getStatus());
        existing.setAuditStatus(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus());
        existing.setFetchCount((existing.getFetchCount() == null ? 0 : existing.getFetchCount()) + 1);
        existing.setLastFetchTime(LocalDateTime.now());
    }

    /** 重建附件子表（先删后插，事务内保证主档/子表一致）并回填主档 ids JSON。 */
    private void rebuildAttachments(Long noticeId, ExternalNoticeIngestDTO dto) {
        if (dto.getAttachments() == null || dto.getAttachments().isEmpty()) {
            // 无附件：保留既有附件与 ids JSON（内容更新通常不改采购文件附件，避免孤儿行/无谓写）
            return;
        }
        noticeExternalAttachmentMapper.delete(new LambdaQueryWrapper<NoticeExternalAttachmentDO>()
                .eq(NoticeExternalAttachmentDO::getNoticeId, noticeId));
        NoticeExternalDO main = noticeExternalMapper.selectById(noticeId);
        String idsJson = insertAttachments(noticeId, dto);
        main.setAttachmentIdsJson(idsJson);
        noticeExternalMapper.updateById(main);
    }

    /** 内容指纹：正文关键字段变化即视为“新版本”（采集侧未提供 content_hash 时兜底计算）。 */
    private String computeContentHash(ExternalNoticeIngestDTO dto) {
        StringBuilder sb = new StringBuilder();
        appendFp(sb, dto.getTitle());
        appendFp(sb, dto.getContentType());
        appendFp(sb, "HTML".equals(dto.getContentType()) ? dto.getContentHtml() :
                dto.getPdfFileId() + ":" + dto.getPdfSha256() + ":" + dto.getPdfSize());
        appendFp(sb, dto.getIssuerName());
        appendFp(sb, dto.getNoticeTypeRaw());
        appendFp(sb, dto.getProjectCode());
        appendFp(sb, dto.getTenderProjectCode());
        appendFp(sb, dto.getBidSectionCodes());
        // 不含 publishTime：采集侧若填抓取快照时间会使同内容重采误判为新版本（无谓重审）；实质内容变化由上述正文字段捕获
        return sha256Hex(sb.toString());
    }

    private void appendFp(StringBuilder sb, String part) {
        sb.append(part == null ? "<null>" : part).append('\u0001');
    }

    private NoticeExternalDO selectByUrlHash(String urlHash) {
        return noticeExternalMapper.selectOne(new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getOrigUrlHash, urlHash).last("LIMIT 1"));
    }

    /** 由 DTO 构建主档（status=NEW + audit=UNREVIEWED 进审核队列）。 */
    private NoticeExternalDO buildNotice(ExternalNoticeIngestDTO dto, String urlHash, String contentHash) {
        NoticeExternalDO notice = new NoticeExternalDO();
        notice.setSourceCode(dto.getSourceCode());
        notice.setSourceName(dto.getSourceName());
        notice.setTitle(dto.getTitle());
        notice.setOrigUrl(dto.getOrigUrl());
        notice.setOrigUrlHash(urlHash);
        notice.setContentHash(contentHash);
        notice.setContentType(dto.getContentType());
        notice.setContentHtml("HTML".equals(dto.getContentType()) ? dto.getContentHtml() : null);
        // PDF 红线 8：file_id+sha256+size 三要素齐存（缺 sha256 不入 pdf 展示形态）
        if ("PDF".equals(dto.getContentType())) {
            notice.setPdfFileId(dto.getPdfFileId());
            notice.setPdfSha256(dto.getPdfSha256());
            notice.setPdfSize(dto.getPdfSize());
            notice.setPdfDownloadUrl(dto.getPdfDownloadUrl());
        }
        notice.setNoticeTypeRaw(dto.getNoticeTypeRaw());
        notice.setLegalFlag(dto.getLegalFlag());
        notice.setIndustryFlag(dto.getIndustryFlag());
        notice.setProjectCode(dto.getProjectCode());
        notice.setTenderProjectCode(dto.getTenderProjectCode());
        notice.setBidSectionCodes(dto.getBidSectionCodes());
        notice.setProjectName(dto.getProjectName());
        // 项目维度兜底增强（2026-09-09，依据：存量 ACTIVE 20 条 project_name 覆盖 0、project_code 仅 4 条，
        // 无项目维度则“按项目关注/聚合”闭环断链）：采集结构化字段缺失时，从标题+正文推导编号/项目名（只补空、不覆盖源数据）
        if (!StringUtils.hasText(dto.getProjectCode()) || !StringUtils.hasText(dto.getProjectName())) {
            NoticeProjectFieldExtractor.ProjectFields pf = NoticeProjectFieldExtractor.extract(dto.getTitle(),
                    "HTML".equals(dto.getContentType()) ? dto.getContentHtml() : null);
            if (pf != null) {
                if (!StringUtils.hasText(dto.getProjectCode()) && pf.projectCode() != null) {
                    notice.setProjectCode(pf.projectCode());
                }
                if (!StringUtils.hasText(dto.getProjectName()) && pf.projectName() != null) {
                    notice.setProjectName(pf.projectName());
                }
            }
        }
        notice.setIssuerName(dto.getIssuerName());
        // BR-5 发布时间回填：采集侧未给或给了异常早时间（0/1970 等历史脏值）→ 尽力从原文解析发布日期；仍无命中才取抓取时间
        notice.setPublishTime(resolvePublishTime(dto));
        notice.setEffectiveFrom(dto.getEffectiveFrom());
        notice.setEffectiveTo(dto.getEffectiveTo());
        notice.setParserCode(dto.getParserCode());
        notice.setStatus(NoticeExternalStatusEnum.NEW.getStatus());
        notice.setAuditStatus(NoticeExternalAuditStatusEnum.UNREVIEWED.getStatus());
        notice.setPublicScope("SOCIAL"); // 默认社会公开（01 公开级别对齐；非公开源由源配置决定）
        notice.setFetchCount(1);
        notice.setLastFetchTime(LocalDateTime.now());
        notice.setMetaJson(dto.getMetaJson());
        notice.setExtraRaw(dto.getExtraRaw());
        notice.setExtractionStatus("NONE");
        return notice;
    }


    /**
     * 存量回填项目维度（2026-09-09 补，依据同 buildNotice 增强）：只扫 project_name 或 project_code 为空的
     * HTML 形态主档（PDF 抽取链 P2 未含），从标题+正文推导并回填；幂等（只补空），重复执行安全。
     *
     * @return 发生回填的记录数
     */
    public int backfillProjectFieldsFromContent() {
        return backfillProjectFieldsFromContent(false);
    }

    /**
     * 存量回填（forceName=true 时项目名即使非空也按新规则重写，用于修复首轮优先级缺陷产生的污染值，
     * 见 NoticeProjectFieldExtractor 顺序注释；幂等：重复执行结果一致）。扫全量 HTML 形态主档（PDF 抽取链 P2 未含）。
     *
     * @return 发生回填/重写的记录数
     */
    public int backfillProjectFieldsFromContent(boolean forceName) {
        List<NoticeExternalDO> rows;
        if (forceName) {
            // 强制重写模式：全量扫 HTML 主档，用最新规则重算项目名（幂等修复）
            rows = noticeExternalMapper.selectList(new LambdaQueryWrapper<NoticeExternalDO>()
                    .isNotNull(NoticeExternalDO::getContentHtml));
        } else {
            rows = noticeExternalMapper.selectList(new LambdaQueryWrapper<NoticeExternalDO>()
                    .and(w -> w.isNull(NoticeExternalDO::getProjectName).or().eq(NoticeExternalDO::getProjectName, ""))
                    .or(q -> q.isNull(NoticeExternalDO::getProjectCode).or().eq(NoticeExternalDO::getProjectCode, ""))
                    .isNotNull(NoticeExternalDO::getContentHtml));
        }
        int changed = 0;
        for (NoticeExternalDO row : rows) {
            NoticeProjectFieldExtractor.ProjectFields pf = NoticeProjectFieldExtractor.extract(row.getTitle(), row.getContentHtml());
            if (pf == null || (pf.projectCode() == null && pf.projectName() == null)) {
                continue;
            }
            boolean dirty = false;
            if (forceName && pf.projectName() != null && !pf.projectName().equals(row.getProjectName())) {
                row.setProjectName(pf.projectName());
                dirty = true;
            } else if (!forceName && !StringUtils.hasText(row.getProjectName()) && pf.projectName() != null) {
                row.setProjectName(pf.projectName());
                dirty = true;
            }
            if (!StringUtils.hasText(row.getProjectCode()) && pf.projectCode() != null) {
                row.setProjectCode(pf.projectCode());
                dirty = true;
            }
            if (dirty) {
                noticeExternalMapper.updateById(row);
                changed++;
                log.info("[backfillProject][noticeId={}] 回填 projectCode={} projectName={}",
                        row.getId(), row.getProjectCode(), row.getProjectName());
            }
        }
        log.info("[backfillProject] 扫描 {} 条，回填 {} 条", rows.size(), changed);
        return changed;
    }

    /** 发布日期（BR-5 修订 2026-09-10）：采集侧给定且年份正常（≥2000）直接用；否则尽力从原文解析；
     *  仍无命中返回 null——不再用抓取时间冒充发布时间（宁缺毋假，沉底可接受；真实时间可由审核侧 AI 抽取回填 publish_time）。 */
    private LocalDateTime resolvePublishTime(ExternalNoticeIngestDTO dto) {
        LocalDateTime publish = dto.getPublishTime();
        if (publish != null && publish.getYear() >= 2000) {
            return publish;
        }
        LocalDateTime parsed = NoticePublishTimeResolver.resolveFromText(dto.getContentHtml(), dto.getTitle());
        return parsed != null && parsed.getYear() >= 2000 ? parsed : null;
    }

    /** 附件子表落库，返回 ids JSON（无附件返回 null）；主档 attachment_ids_json 由调用方回填。 */
    private String insertAttachments(Long noticeId, ExternalNoticeIngestDTO dto) {
        if (dto.getAttachments() == null || dto.getAttachments().isEmpty()) {
            return null;
        }
        List<Long> ids = new ArrayList<>();
        for (ExternalNoticeIngestDTO.Attachment a : dto.getAttachments()) {
            NoticeExternalAttachmentDO row = new NoticeExternalAttachmentDO();
            row.setNoticeId(noticeId);
            row.setFileId(a.getFileId());
            row.setSha256(a.getSha256());
            row.setSize(a.getSize());
            row.setName(a.getName());
            row.setExt(a.getExt());
            row.setOrigDownloadUrl(a.getOrigDownloadUrl());
            row.setVirusStatus("PENDING");
            noticeExternalAttachmentMapper.insert(row);
            ids.add(row.getId());
        }
        return buildIdsJson(ids);
    }

    /**
     * 内容安全校验（安全评审意见 1/3/5 + 红线：URL 协议/JSON 合法性/长度上限）。
     */
    private void validatePayload(ExternalNoticeIngestDTO dto) {
        if (!isHttpUrl(dto.getOrigUrl())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID);
        }
        if (dto.getTitle() != null && dto.getTitle().length() > MAX_TITLE) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID);
        }
        if (dto.getOrigUrl().length() > MAX_ORIG_URL) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID);
        }
        if (dto.getContentHtml() != null && dto.getContentHtml().length() > MAX_HTML) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID);
        }
        if (!isValidJsonOrNull(dto.getMetaJson(), MAX_META_JSON)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID);
        }
        if (dto.getExtraRaw() != null && dto.getExtraRaw().length() > MAX_EXTRA_RAW) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_PAYLOAD_INVALID);
        }
    }

    /** 协议白名单：仅 http/https（拒绝 javascript:/data: 等伪协议进库，防链接型 XSS） */
    private static boolean isHttpUrl(String url) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            return scheme != null && ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /** metaJson 必须为合法 JSON 且不超长；空值放行 */
    private static boolean isValidJsonOrNull(String json, int maxChars) {
        if (json == null || json.isBlank()) {
            return true;
        }
        if (json.length() > maxChars) {
            return false;
        }
        try {
            OBJECT_MAPPER.readTree(json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private String buildIdsJson(List<Long> ids) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(ids.get(i));
        }
        return sb.append(']').toString();
    }

    /** SHA-256 hex（URL 去重指纹） */
    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 不可用", e);
        }
    }

}
