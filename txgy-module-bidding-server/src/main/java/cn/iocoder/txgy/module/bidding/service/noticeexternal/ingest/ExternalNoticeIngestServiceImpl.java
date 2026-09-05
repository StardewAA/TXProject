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
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 采集落库实现（status=NEW + audit=UNREVIEWED 进审核队列；展示等审核通过）。
 */
@Slf4j
@Service
public class ExternalNoticeIngestServiceImpl implements ExternalNoticeIngestService {

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
