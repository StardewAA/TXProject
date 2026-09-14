package cn.iocoder.txgy.module.bidding.controller.admin.notice;
// 规范依据：《招投标模块开发规范》§4.1~4.5、五章五；01/05（BID-ENT-NOTICE-01/02、GATHER-02、HALL-INFO-01/SEARCH-01）

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeDeadlineRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePublicSyncReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticePublicSyncRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeSaveReqVO;
import cn.iocoder.txgy.module.bidding.service.notice.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 公告/公示（bidding notice）。
 *
 * <p>管理端：分页/详情/新建/修改/发布/撤下/删除（本租户隔离）；
 * 大厅公开只读（列表/详情）：跨租户、仅已发布且非内部的社会公开公告，
 * 关键词全文命中走检索组件（见 NoticePageReqVO 说明）。
 *
 * <p>权威接口编号对齐（见 05-接口设计文档 §3）：
 * BID-ENT-NOTICE-01 公告查询≈ GET /get（管理端全集）与 /public/detail（社会公开子集）；
 * BID-ENT-WIN-01 公示/结果推送≈ POST /publish（幂等，对外推送口径后续接 GATHER-02）；
 * BID-HALL-INFO-01 公开公告/公示查询≈ GET /public/list；
 * BID-HALL-SEARCH-01 全文检索≈ GET /public/list?keyword=（高亮片段待检索组件增强后补）。
 * 公开边界只外发社会公开字段（01/05 规则 1 + 红线 2）。
 */
@Tag(name = "管理后台 - bidding 公告/公示")
@RestController
@RequestMapping("/bidding/notice")
@Validated
public class NoticeController {

    @Resource
    private NoticeService noticeService;

    // ========== 1. 公告列表查询（管理端 + 大厅公开） ==========

    @GetMapping("/page")
    @Operation(summary = "公告/公示分页（管理端）")
    @PreAuthorize("@ss.hasPermission('bidding:notice:query')")
    public CommonResult<PageResult<NoticeRespVO>> page(@Valid NoticePageReqVO reqVO) {
        return success(noticeService.getNoticePage(reqVO));
    }

    @GetMapping("/public/list")
    @Operation(summary = "公告/公示分页（大厅公开只读）")
    @PermitAll
    public CommonResult<PageResult<NoticeRespVO>> publicList(@Valid NoticePageReqVO reqVO) {
        return success(noticeService.getPublicNoticePage(reqVO));
    }

    // ========== 2. 公告详情（PDF 版式 + 结构化两种形态） ==========

    @GetMapping("/get")
    @Operation(summary = "公告/公示详情（管理端）")
    @PreAuthorize("@ss.hasPermission('bidding:notice:query')")
    public CommonResult<NoticeDetailRespVO> get(@RequestParam("id") Long id) {
        return success(noticeService.getNoticeDetail(id));
    }

    @GetMapping("/public/detail")
    @Operation(summary = "公告/公示详情（大厅公开只读）")
    @PermitAll
    public CommonResult<NoticeDetailRespVO> publicDetail(@RequestParam("id") Long id) {
        return success(noticeService.getPublicNoticeDetail(id));
    }

    // ========== 3. 新建公告 ==========

    @PostMapping("/create")
    @Operation(summary = "新建公告/公示（草稿入库）")
    @PreAuthorize("@ss.hasPermission('bidding:notice:create')")
    @Idempotent
    public CommonResult<Long> create(@Valid @RequestBody NoticeSaveReqVO reqVO) {
        return success(noticeService.createNotice(reqVO));
    }

    // ========== 4. 公告管理（发布/修改/删除；internalFlag 区分企业内部公告） ==========

    @PutMapping("/update")
    @Operation(summary = "修改公告/公示（正文变更追加新版本）")
    @PreAuthorize("@ss.hasPermission('bidding:notice:update')")
    @Idempotent
    public CommonResult<Boolean> update(@Valid @RequestBody NoticeSaveReqVO reqVO) {
        noticeService.updateNotice(reqVO);
        return success(true);
    }

    @PostMapping("/publish")
    @Operation(summary = "发布公告/公示")
    @PreAuthorize("@ss.hasPermission('bidding:notice:publish')")
    @Idempotent
    public CommonResult<Boolean> publish(@RequestParam("id") Long id) {
        noticeService.publishNotice(id);
        return success(true);
    }

    @GetMapping("/meta/types")
    @PreAuthorize("@ss.hasPermission('bidding:notice:query')")
    @Operation(summary = "公告类型字段模板元数据（按类型必填/可选 + 中文名，供新建/编辑动态表单）")
    public CommonResult<java.util.List<cn.iocoder.txgy.module.bidding.controller.admin.notice.vo.NoticeTypeMetaRespVO>> typeMeta() {
        return success(noticeService.listTypeMeta());
    }

    @PostMapping("/reindex")
    @PreAuthorize("@ss.hasPermission('bidding:notice:update')")
    @Idempotent
    @Operation(summary = "全量重建本租户公告 ES 索引（幂等 PUT 覆盖；补偿 es.enabled 启用前存量）")
    public CommonResult<String> reindex() {
        return success(noticeService.reindexIndex());
    }

    @PostMapping("/reindex-all")
    @PreAuthorize("@ss.hasPermission('bidding:notice:update')")
    @Idempotent
    @Operation(summary = "平台级全租户公告 ES 索引补偿（忽略租户遍历全部公告；仅平台租户可用，幂等可重跑）")
    public CommonResult<String> reindexAll() {
        // 仅平台租户（id=1）放行：本端点会索引全部租户公告，不允许租户互扫
        Long tenantId = cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId();
        if (tenantId == null || tenantId != 1L) {
            throw cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.FORBIDDEN);
        }
        return success(noticeService.reindexAllTenants());
    }

    @PostMapping("/unpublish")
    @Operation(summary = "撤下公告/公示")
    @PreAuthorize("@ss.hasPermission('bidding:notice:update')")
    @Idempotent
    public CommonResult<Boolean> unpublish(@RequestParam("id") Long id) {
        noticeService.unpublishNotice(id);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除公告/公示（逻辑删除）")
    @PreAuthorize("@ss.hasPermission('bidding:notice:delete')")
    @Idempotent
    public CommonResult<Boolean> delete(@RequestParam("id") Long id) {
        noticeService.deleteNotice(id);
        return success(true);
    }

    @GetMapping("/exchange/deadline")
    @Operation(summary = "截止时间查询（BID-ENT-NOTICE-02，模块内部只读）")
    @PreAuthorize("@ss.hasPermission('bidding:notice:query')")
    public CommonResult<NoticeDeadlineRespVO> deadline(@RequestParam("bidSectionCode") String bidSectionCode) {
        return success(noticeService.getBidDeadline(bidSectionCode));
    }

    @PostMapping("/exchange/gather-sync")
    @Operation(summary = "公开同步（BID-SUP-GATHER-02）：公告 → 归集 → 检索文档")
    @PreAuthorize("@ss.hasPermission('bidding:notice:update')")
    @Idempotent
    public CommonResult<NoticePublicSyncRespVO> gatherSync(@Valid @RequestBody NoticePublicSyncReqVO reqVO) {
        return success(noticeService.syncPublicDocument(reqVO));
    }

}
