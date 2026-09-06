package cn.iocoder.txgy.module.bidding.service.noticeexternal;
// 规范依据：设计文档 v0.6 §7/§15.1/§15.3（SOCIAL+ACTIVE 过滤、ES 检索事件预留、不开放业务 LIKE）；
// 《招投标模块开发规范》§4.4、红线 8；01（公开边界）

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
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
import cn.iocoder.txgy.module.bidding.service.noticefollow.FollowService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        return resp;
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
        // PDF 形态：file_id+sha+size（前端 JS 预览），下载地址待 FileApi 签名能力接入后回填
        resp.setPdfFileId(notice.getPdfFileId());
        resp.setPdfSha256(notice.getPdfSha256());
        resp.setPdfSize(notice.getPdfSize());
        resp.setExtractionStatus(notice.getExtractionStatus());
        resp.setNoticeTypeRaw(notice.getNoticeTypeRaw());
        resp.setLegalFlag(notice.getLegalFlag());
        resp.setIndustryFlag(notice.getIndustryFlag());
        resp.setBodyParserCode(notice.getBodyParserCode());
        resp.setBodyBlocksStatus(notice.getBodyBlocksStatus());
        resp.setBodyBlocksJson(notice.getBodyBlocksJson());
        resp.setProjectName(notice.getProjectName());
        resp.setBidSectionCodes(notice.getBidSectionCodes());
        resp.setIssuerName(notice.getIssuerName());
        resp.setPublishTime(notice.getPublishTime());
        resp.setEffectiveFrom(notice.getEffectiveFrom());
        resp.setEffectiveTo(notice.getEffectiveTo());
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

}
