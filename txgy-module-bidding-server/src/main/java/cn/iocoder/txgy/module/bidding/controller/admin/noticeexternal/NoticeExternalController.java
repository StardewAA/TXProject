package cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal;
// 规范依据：设计文档 v0.6 §7/§15.1（大厅公开只读：SOCIAL+ACTIVE、三形态、原网址必带）；05 §3（HALL 口径）

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.noticeexternal.vo.NoticeExternalRespVO;
import cn.iocoder.txgy.module.bidding.service.noticeexternal.NoticeExternalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 外部公告（大厅公开只读）。
 *
 * <p>展示接口（前端由其它人员对接）：
 * GET /bidding/notice-external/page    公开分页（SOCIAL+ACTIVE，只出主列，publishTime 倒序）；
 * GET /bidding/notice-external/detail  公开详情（HTML 原样/PDF JS 预览+origUrl/附件下载列表）。
 *
 * <p>关键词全文检索：走检索组件（MQ 事件建 ES 索引预留，见设计文档 v0.6 §15.3），
 * 不在本接口开放业务表 LIKE。
 */
@Tag(name = "管理后台 - bidding 外部公告（大厅公开）")
@RestController
@RequestMapping("/bidding/notice-external")
@Validated
public class NoticeExternalController {

    @Resource
    private NoticeExternalService noticeExternalService;

    @GetMapping("/page")
    @PermitAll
    @Operation(summary = "外部公告公开分页（SOCIAL+ACTIVE）")
    public CommonResult<PageResult<NoticeExternalRespVO>> page(@Valid NoticeExternalPageReqVO reqVO) {
        return success(noticeExternalService.getPage(reqVO));
    }

    @GetMapping("/detail")
    @PermitAll
    @Operation(summary = "外部公告公开详情（三形态）")
    public CommonResult<NoticeExternalDetailRespVO> detail(@RequestParam("id") Long id) {
        return success(noticeExternalService.getDetail(id));
    }

    // 管理端（审核/下线/重抓/白名单审批）接口按设计文档 §6/§五 后续切片补齐，
    // 权限码统一申请 bidding:notice-external:*（占位，避免与公开只读混权限）
    @GetMapping("/admin-stub")
    @PreAuthorize("@ss.hasPermission('bidding:notice-external:query')")
    @Operation(summary = "外部公告管理端占位（后续切片）", hidden = true)
    public CommonResult<Boolean> adminStub() {
        return success(Boolean.TRUE);
    }

}
