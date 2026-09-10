package cn.iocoder.txgy.module.bidding.controller.admin.subject;
// [依据] 《招投标模块开发规范》§4.4（权限注解/幂等/Swagger）；主体档案=企业租户自维护（T 轨，租户拦截器自动隔离）。
// 需求口径：档案维护/查看只要求“登录+权限”，不要求先授权；授权/完善仅约束“投标/发布”动作（见资格门）。

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectEligibilityRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.SubjectProfileSaveReqVO;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectEligibilityService;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder.getTenantId;

/**
 * 管理后台 - 主体信息档案（企业租户自维护：投标人/招标人信息完善；仅登录+bidder/tenderer 档案权限即可使用，无需先授权）。
 */
@Tag(name = "管理后台 - bidding 主体信息档案")
@RestController
@RequestMapping("/bidding/subject/profile")
@Validated
public class SubjectProfileController {

    @Resource
    private SubjectProfileService subjectProfileService;
    @Resource
    private SubjectEligibilityService subjectEligibilityService;

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:subject:query')")
    @Operation(summary = "本企业主体档案详情（含完善度/缺失项；未建档返回 null）")
    public CommonResult<SubjectProfileRespVO> get(@RequestParam("subjectType") String subjectType) {
        return success(subjectProfileService.getOwnProfile(subjectType));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:subject:update')")
    @Idempotent
    @Operation(summary = "保存本企业主体档案（新建/更新；USCC 按 GB32100-2015 强校验；实时核算完善度）")
    public CommonResult<Long> save(@Valid @RequestBody SubjectProfileSaveReqVO reqVO) {
        return success(subjectProfileService.saveOwnProfile(reqVO));
    }

    @GetMapping("/eligibility")
    @PreAuthorize("@ss.hasPermission('bidding:subject:query')")
    @Operation(summary = "本企业主体资格状态（投标人是否可投标/招标人是否可发布：授权+完善双条件）")
    public CommonResult<SubjectEligibilityRespVO> eligibility(@RequestParam("subjectType") String subjectType) {
        return success(subjectEligibilityService.resolveEligibility(getTenantId(), subjectType));
    }
}
