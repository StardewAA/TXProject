package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal;
// 规范依据：设计文档 v0.6 §7/§15.1（大厅公开只读：SOCIAL+ACTIVE、三形态、原网址必带）；05 §3（HALL 口径）

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeBodyStructureRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalAiExtractRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManagePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalManageRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalRespVO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.NoticeExternalService;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.AiExtractSnapshot;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ai.NoticeExternalAiExtractService;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.body.NoticeBodyStructureService;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.body.NoticeBodyStructureSnapshot;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestDTO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.ingest.ExternalNoticeIngestService;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.url.ExternalRedirectService;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 外部公告（大厅公开只读）。
 *
 * <p>展示接口（前端由其它人员对接）：
 * GET /bidding/notice-external/page    公开分页（SOCIAL+ACTIVE，只出主列，publishTime 倒序；keyword 走 ES 全文检索）；
 * GET /bidding/notice-external/detail  公开详情（HTML 原样/PDF JS 预览+origUrl/附件下载列表）。
 *
 * <p>关键词全文检索：走检索组件（ES 索引 bidding-notice-external，见 EsExternalNoticeIndexNotifier/EsExternalNoticeSearchEngine），
 * 不在本接口开放业务表 LIKE（规范 4.6）。
 */
@Tag(name = "管理后台 - bidding 外部公告（大厅公开）")
@RestController
@RequestMapping("/bidding/notice-external")
@Validated
public class NoticeExternalController {

    @Resource
    private NoticeExternalService noticeExternalService;
    @Resource
    private ExternalRedirectService externalRedirectService;
    @Resource
    private ExternalNoticeIngestService externalNoticeIngestService;
    @Resource
    private NoticeExternalAiExtractService noticeExternalAiExtractService;
    @Resource
    private NoticeBodyStructureService noticeBodyStructureService;

    @GetMapping("/page")
    @PermitAll
    @TenantIgnore // 大厅公开：G 轨全局数据，无租户概念（免租户头直接可访）
    @Operation(summary = "外部公告公开分页（SOCIAL+ACTIVE）")
    public CommonResult<PageResult<NoticeExternalRespVO>> page(@Valid NoticeExternalPageReqVO reqVO) {
        return success(noticeExternalService.getPage(reqVO));
    }

    @GetMapping("/manage/page")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Operation(summary = "外部公告管理分页（审核/运营列表：全状态/审核态，行内含抽取/AI/结构化状态，供行内操作选中）")
    public CommonResult<PageResult<NoticeExternalManageRespVO>> managePage(@Valid NoticeExternalManagePageReqVO reqVO) {
        return success(noticeExternalService.getManagePage(reqVO));
    }

    @GetMapping("/go")
    @PermitAll
    @TenantIgnore // 大厅公开：原网址中转白名单校验，无租户概念
    @Operation(summary = "外链中转（仅白名单采集源域名下的 http(s)，防开放重定向；不直达外站请前端改用本端点）")
    public void go(@RequestParam("url") String url, HttpServletResponse response) throws IOException {
        String safe = externalRedirectService.resolve(url);
        response.setStatus(HttpServletResponse.SC_FOUND);
        response.setHeader("Location", safe);
        // 中转页不缓存、不携带来源引用（防反向钓鱼/窗口劫持，配合前端 rel 属性）
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }

    @GetMapping("/detail")
    @PermitAll
    @TenantIgnore // 大厅公开：G 轨全局数据，无租户概念（免租户头直接可访）
    @Operation(summary = "外部公告公开详情（三形态）")
    public CommonResult<NoticeExternalDetailRespVO> detail(@RequestParam("id") Long id) {
        return success(noticeExternalService.getDetail(id));
    }

    @GetMapping("/manage/get")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Operation(summary = "外部公告管理详情（BR-7 审核预览：全状态；含净化正文/结构化块/PDF/附件病毒/抽取摘要）")
    public CommonResult<NoticeExternalManageDetailRespVO> manageGet(@RequestParam("id") Long id) {
        return success(noticeExternalService.getManageDetail(id));
    }

    // 管理端：审核通过（NEW→ACTIVE，随后触发关注扇出+ES 索引事件预留）
    @PostMapping("/approve")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Idempotent
    @Operation(summary = "审核通过外部公告（转 ACTIVE，触发关注提醒+索引事件）")
    public CommonResult<Boolean> approve(@RequestParam("id") Long id) {
        noticeExternalService.approveExternal(id);
        return success(Boolean.TRUE);
    }

    @PostMapping("/reject")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Idempotent
    @Operation(summary = "驳回外部公告（审核不通过）")
    public CommonResult<Boolean> reject(@RequestParam("id") Long id) {
        noticeExternalService.rejectExternal(id);
        return success(Boolean.TRUE);
    }

    @PostMapping("/take-down")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Idempotent
    @Operation(summary = "下线外部公告（ACTIVE→INACTIVE，展示随即拒绝）")
    public CommonResult<Boolean> takeDown(@RequestParam("id") Long id) {
        noticeExternalService.takeDownExternal(id);
        return success(Boolean.TRUE);
    }

    /**
     * 采集回传入库（外部采集服务 → 平台）。
     *
     * <p>语义对齐设计文档 v0.6 §二/§四/§15.4：采集侧完成下载+解析后回传，落 NEW+UNREVIEWED 待审核；
     * 同 URL 幂等 / 内容变更重投 / 驳回重采 由 ingest 内部处理。
     * 鉴权：本期走管理端权限（服务账号 HMAC 接入为 P2，见设计 §六）。
     */
    @PostMapping("/ingest")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Idempotent
    @Operation(summary = "采集回传入库（落 NEW 待审核；同 URL 幂等/内容变更覆盖）", description = "返回外部公告 id")
    public CommonResult<Long> ingest(@Valid @RequestBody ExternalNoticeIngestDTO dto) {
        return success(externalNoticeIngestService.ingest(dto));
    }

    /**
     * AI 结构化抽取 + 防腐保温行业判定：人工触发（PDF/HTML → 结构化字段落 ai_meta_json，
     * industryFlag/置信度回写权威字段；扫描件全文转写回写后可被关键词检索命中；需 bidding.ai.enabled=true）
     */
    @PostMapping("/ai-extract")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Idempotent
    @Operation(summary = "AI 结构化抽取与防腐保温行业判定（管理端人工触发）")
    public CommonResult<NoticeExternalAiExtractRespVO> aiExtract(@RequestParam("id") Long id) {
        AiExtractSnapshot snapshot = noticeExternalAiExtractService.extract(id);
        NoticeExternalAiExtractRespVO respVO = new NoticeExternalAiExtractRespVO();
        respVO.setId(snapshot.getId());
        respVO.setAiExtractionStatus(snapshot.getAiExtractionStatus());
        respVO.setMetaFieldCount(snapshot.getMetaFieldCount());
        respVO.setFullTextLength(snapshot.getFullTextLength());
        respVO.setIndustryFlag(snapshot.getIndustryFlag());
        respVO.setIndustryConfidence(snapshot.getIndustryConfidence());
        return success(respVO);
    }

    /**
     * 正文结构化（策略链，人工触发）：常规 HTML 结构解析（HTML_STRUCTURE）；
     * useAi=true 时强制 AI 兜底排版（AI_FALLBACK，把拍平/脏正文恢复为结构化块，需 bidding.ai.enabled=true）。
     */
    @PostMapping("/body-structure")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:update')")
    @Idempotent
    @Operation(summary = "正文结构化（管理端人工触发：常规 HTML 解析 / AI 兜底排版）")
    public CommonResult<NoticeBodyStructureRespVO> bodyStructure(@RequestParam("id") Long id,
                                                                 @RequestParam(value = "ai", defaultValue = "false") Boolean ai) {
        NoticeBodyStructureSnapshot snapshot = noticeBodyStructureService.structure(id, Boolean.TRUE.equals(ai));
        NoticeBodyStructureRespVO respVO = new NoticeBodyStructureRespVO();
        respVO.setId(snapshot.getId());
        respVO.setBodyBlocksStatus(snapshot.getBodyBlocksStatus());
        respVO.setBodyParserCode(snapshot.getBodyParserCode());
        respVO.setBlockCount(snapshot.getBlockCount());
        respVO.setTableCount(snapshot.getTableCount());
        respVO.setAiRecommended(snapshot.getAiRecommended());
        return success(respVO);
    }

}
