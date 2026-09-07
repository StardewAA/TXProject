package cn.iocoder.txgy.module.bidding.controller.admin.subject;
// [依据] 《招投标模块开发规范》§4.4；管理端“显示已授权投标人并支持管理用户”（需求）：
//   授权列表行含完善度（授权≠完善）；经办用户绑定面向该主体企业（租户）下的账号。

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectAuthSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectEligibilityRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserBindReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectUserRespVO;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectAuthService;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectEligibilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
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
 * 管理后台 - 已授权主体（投标人/招标人）管理（平台侧：授权/启停/经办用户/资格查询）。
 */
@Tag(name = "管理后台 - bidding 已授权主体（投标人/招标人）")
@RestController
@RequestMapping("/bidding/subject-auth")
@Validated
public class SubjectAuthController {

    @Resource
    private SubjectAuthService subjectAuthService;
    @Resource
    private SubjectEligibilityService subjectEligibilityService;

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:subject-auth:query')")
    @Operation(summary = "已授权主体分页（含档案完善度：识别“已授权未完善”）")
    public CommonResult<PageResult<SubjectAuthRespVO>> page(@Valid SubjectAuthPageReqVO reqVO) {
        return success(subjectAuthService.getAuthPage(reqVO));
    }

    @PostMapping("/authorize")
    @PreAuthorize("@ss.hasPermission('bidding:subject-auth:update')")
    @Idempotent
    @Operation(summary = "授权企业主体（投标人/招标人；同主体重复 ENABLED 报错，停用历史可复用启用）")
    public CommonResult<Long> authorize(@Valid @RequestBody SubjectAuthSaveReqVO reqVO) {
        return success(subjectAuthService.authorize(reqVO));
    }

    @PutMapping("/status")
    @PreAuthorize("@ss.hasPermission('bidding:subject-auth:update')")
    @Idempotent
    @Operation(summary = "授权状态流转（DISABLED=停用/撤销授权留痕；ENABLED=重新启用）")
    public CommonResult<Boolean> updateStatus(@RequestParam("id") Long id,
                                              @RequestParam("authStatus") String authStatus) {
        subjectAuthService.updateAuthStatus(id, authStatus);
        return success(Boolean.TRUE);
    }

    @GetMapping("/eligibility")
    @PreAuthorize("@ss.hasPermission('bidding:subject-auth:query')")
    @Operation(summary = "指定企业主体资格状态（平台侧：任意租户；投标/发布双条件检查与原因）")
    public CommonResult<SubjectEligibilityRespVO> eligibility(@RequestParam("tenantId") Long tenantId,
                                                              @RequestParam("subjectType") String subjectType) {
        return success(subjectEligibilityService.resolveEligibility(tenantId, subjectType));
    }

    // ========== 经办用户管理（“支持管理用户”） ==========

    @GetMapping("/user/page")
    @PreAuthorize("@ss.hasPermission('bidding:subject-auth:query')")
    @Operation(summary = "主体经办用户分页")
    public CommonResult<PageResult<SubjectUserRespVO>> userPage(@Valid SubjectUserPageReqVO reqVO) {
        return success(subjectAuthService.getBindUserPage(reqVO));
    }

    @PostMapping("/user/bind")
    @PreAuthorize("@ss.hasPermission('bidding:subject-auth:update')")
    @Idempotent
    @Operation(summary = "绑定主体经办用户（该主体企业租户下的 AdminUser；昵称快照）")
    public CommonResult<Long> bindUser(@Valid @RequestBody SubjectUserBindReqVO reqVO) {
        return success(subjectAuthService.bindUser(reqVO));
    }

    @DeleteMapping("/user/unbind")
    @PreAuthorize("@ss.hasPermission('bidding:subject-auth:update')")
    @Idempotent
    @Operation(summary = "解绑主体经办用户（逻辑删除）")
    public CommonResult<Boolean> unbindUser(@RequestParam("id") Long id) {
        subjectAuthService.unbindUser(id);
        return success(Boolean.TRUE);
    }
}
