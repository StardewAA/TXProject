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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

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

    @Resource
    private NoticeExternalMapper noticeExternalMapper;
    @Resource
    private NoticeExternalAttachmentMapper noticeExternalAttachmentMapper;

    @Override
    public Long ingest(ExternalNoticeIngestDTO dto) {
        // 主列必填校验（采集产出不齐不落库，宁缺毋滥）
        if (!StringUtils.hasText(dto.getOrigUrl()) || !StringUtils.hasText(dto.getTitle())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.EXTERNAL_NOTICE_INGEST_INVALID);
        }
        validatePayload(dto);
        String urlHash = StringUtils.hasText(dto.getOrigUrlHash())
                ? dto.getOrigUrlHash() : sha256Hex(dto.getOrigUrl());
        dto.setOrigUrlHash(urlHash);
        // origUrlHash 唯一去重：同 URL 重复产出幂等返回既有主档（重复消费靠 urlHash 幂等，设计 §三）
        NoticeExternalDO existing = noticeExternalMapper.selectOne(new LambdaQueryWrapper<NoticeExternalDO>()
                .eq(NoticeExternalDO::getOrigUrlHash, urlHash).last("LIMIT 1"));
        if (existing != null) {
            log.info("[ingest][urlHash={}] 主档已存在 noticeId={}，幂等跳过", urlHash, existing.getId());
            return existing.getId();
        }

        NoticeExternalDO notice = new NoticeExternalDO();
        notice.setSourceCode(dto.getSourceCode());
        notice.setSourceName(dto.getSourceName());
        notice.setTitle(dto.getTitle());
        notice.setOrigUrl(dto.getOrigUrl());
        notice.setOrigUrlHash(urlHash);
        notice.setContentHash(dto.getContentHash());
        notice.setContentType(dto.getContentType());
        notice.setContentHtml("HTML".equals(dto.getContentType()) ? dto.getContentHtml() : null);
        // PDF 红线 8：file_id+sha256+size 三要素齐存（缺 sha256 不入 pdf 展示形态）
        if ("PDF".equals(dto.getContentType())) {
            notice.setPdfFileId(dto.getPdfFileId());
            notice.setPdfSha256(dto.getPdfSha256());
            notice.setPdfSize(dto.getPdfSize());
        }
        notice.setNoticeTypeRaw(dto.getNoticeTypeRaw());
        notice.setLegalFlag(dto.getLegalFlag());
        notice.setIndustryFlag(dto.getIndustryFlag());
        notice.setProjectCode(dto.getProjectCode());
        notice.setTenderProjectCode(dto.getTenderProjectCode());
        notice.setBidSectionCodes(dto.getBidSectionCodes());
        notice.setProjectName(dto.getProjectName());
        notice.setIssuerName(dto.getIssuerName());
        notice.setPublishTime(dto.getPublishTime() != null ? dto.getPublishTime() : LocalDateTime.now());
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
        noticeExternalMapper.insert(notice);

        // 附件子表 + 主档 ids JSON（batch 不依赖 MP 批量扩展）
        if (dto.getAttachments() != null && !dto.getAttachments().isEmpty()) {
            List<Long> ids = new ArrayList<>();
            for (ExternalNoticeIngestDTO.Attachment a : dto.getAttachments()) {
                NoticeExternalAttachmentDO row = new NoticeExternalAttachmentDO();
                row.setNoticeId(notice.getId());
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
            notice.setAttachmentIdsJson(buildIdsJson(ids));
            noticeExternalMapper.updateById(notice);
        }
        log.info("[ingest][noticeId={} urlHash={}] 外部公告落库（NEW 待审核）", notice.getId(), urlHash);
        return notice.getId();
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
