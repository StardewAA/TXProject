package cn.iocoder.txgy.module.bidding.controller.admin.subject;
// [依据] 《招投标模块开发规范》§4.4（权限注解/幂等/Swagger）；主体档案=企业租户自维护（T 轨，租户拦截器自动隔离）。
// 需求口径：档案维护/查看只要求“登录+权限”，不要求先授权；授权/完善仅约束“投标/发布”动作（见资格门）。

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.subject.vo.CurrentUserTypeRespVO;
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
import org.springframework.web.bind.annotation.DeleteMapping;
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
    public CommonResult<SubjectProfileRespVO> get(@RequestParam(value = "subjectType", required = false) String subjectType) {
        return success(subjectProfileService.getOwnProfile(subjectType));
    }

    @PostMapping("/save")
    @PreAuthorize("@ss.hasPermission('bidding:subject:update')")
    @Idempotent
    @Operation(summary = "保存本企业主体档案（新建/更新；USCC 按 GB32100-2015 强校验；实时核算完善度）")
    public CommonResult<Long> save(@Valid @RequestBody SubjectProfileSaveReqVO reqVO) {
        return success(subjectProfileService.saveOwnProfile(reqVO));
    }

    /**
     * 移除营业执照影像（契约《后端变更请求-主体档案影像移除与更换-2026-09-23》B1）。
     *
     * <p><b>为什么必须独立成一个动作</b>：{@code licenseFileId} 无法通过 {@code /save} 清空 ——
     * 前端空值被 {@code JSON.stringify} 丢字段、传空串又被 Jackson 转成 {@code null}，
     * 而 MyBatis-Plus {@code updateById} 默认忽略 null 字段，UPDATE 里根本没有这一列 →
     * **接口返回成功但数据没变**（实测确认）。"清空字段"必须由专用端点表达。
     *
     * <p><b>为什么用 DELETE 而不是 POST /clear</b>：语义直白（删除一个子资源）；
     * 且 REST 风格下前端已有的 {@code requestClient.delete} 可直接调用。
     *
     * <p><b>权限</b>：与 save 同为 {@code bidding:subject:update} —— 能改档案就应能移除其中一项影像，
     * 不额外增设权限码（避免前端再多一次菜单授权）。
     *
     * <p><b>幂等</b>：标 {@code @Idempotent}；未建档/本就无影像时同样返回 true
     * （调用方语义是"确保没有影像"，不是"必须删掉一条"）。
     */
    @DeleteMapping("/license")
    @PreAuthorize("@ss.hasPermission('bidding:subject:update')")
    @Idempotent
    @Operation(summary = "移除营业执照影像（显式清空，杜绝 save 传 null 清不掉的语义缺口）")
    public CommonResult<Boolean> clearLicense() {
        return success(subjectProfileService.clearOwnLicenseImage());
    }

    @GetMapping("/eligibility")
    @PreAuthorize("@ss.hasPermission('bidding:subject:query')")
    @Operation(summary = "本企业主体资格状态（投标人是否可投标/招标人是否可发布：授权+完善双条件）")
    public CommonResult<SubjectEligibilityRespVO> eligibility(@RequestParam(value = "subjectType", required = false) String subjectType) {
        return success(subjectEligibilityService.resolveEligibility(getTenantId(), subjectType));
    }

    /**
     * 当前登录用户的类型（个人 / 企业员工）—— 门户分流提示用。
     *
     * <p><b>为什么是"仅登录"而不是权限码</b>：本端点**恰恰是给"没有权限码的账号"用的**
     * ——个人用户（保留租户 0）与未配角色的员工都需要靠它知道自己为何被拦。
     * 若加权限码，最需要它的用户反而调不到（与 BidReviewPortalController 同因同理）。
     * 数据无敏感性：只回当前登录人自己的租户类型与档案状态。
     */
    @GetMapping("/current-user-type")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "当前登录用户类型（个人/企业员工；契约 3.1 方案 A，仅登录即可）",
            description = "供门户前端分流提示：个人用户（租户 0）→「本功能未对个人用户开放」；"
                    + "企业员工 →「请联系贵公司对企业档案进行完善」。")
    public CommonResult<CurrentUserTypeRespVO> currentUserType() {
        Long tenantId = getTenantId();
        boolean personal = tenantId == null || tenantId == 0L;
        CurrentUserTypeRespVO resp = new CurrentUserTypeRespVO();
        resp.setTenantId(tenantId);
        resp.setPersonal(personal);
        resp.setUserType(personal ? "PERSONAL" : "ENTERPRISE");
        // 个人用户没有企业档案概念，恒 false；企业租户查本租户唯一档案
        resp.setSubjectProfileBuilt(!personal && subjectProfileService.getOwnSubjectId() != null);
        return success(resp);
    }
}
