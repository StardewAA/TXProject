package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit;

import cn.hutool.core.util.NumberUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.*;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.post.HrmRecruitPostPageReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitApplicationDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.application.HrmRecruitOfferDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitCandidateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.candidate.HrmRecruitInterviewDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.post.HrmRecruitPostTypeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.application.HrmRecruitOfferMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.candidate.HrmRecruitInterviewMapper;
import cn.iocoder.txgy.module.hrm.enums.recruit.application.HrmRecruitApplicationStatusEnum;
import cn.iocoder.txgy.module.hrm.service.recruit.application.HrmRecruitApplicationService;
import cn.iocoder.txgy.module.hrm.service.recruit.candidate.HrmRecruitCandidateService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostService;
import cn.iocoder.txgy.module.hrm.service.recruit.post.HrmRecruitPostTypeService;
import cn.iocoder.txgy.module.hrm.service.recruit.resume.HrmRecruitResumeService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.system.api.user.AdminUserApi;
import cn.iocoder.txgy.module.system.api.user.dto.AdminUserRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum.PUBLISHED;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;

@Tag(name = "员工端 - HRM 招聘应聘")
@RestController
@RequestMapping("/hrm/portal/recruit")
@Validated
@Slf4j
public class HrmPortalRecruitController {
    @Resource private HrmRecruitPostService postService;
    @Resource private HrmRecruitPostTypeService postTypeService;
    @Resource private HrmRecruitApplicationService applicationService;
    @Resource private HrmRecruitCandidateService candidateService;
    @Resource private HrmRecruitResumeService resumeService;
    @Resource private HrmEmployeeService employeeService;
    @Resource private AdminUserApi adminUserApi;
    @Resource private HrmRecruitInterviewMapper interviewMapper;
    @Resource private HrmRecruitOfferMapper offerMapper;

    @GetMapping("/post/page")
    @Operation(summary = "查询已发布招聘职位")
    @PermitAll
    @TenantIgnore
    public CommonResult<PageResult<HrmPortalRecruitPostRespVO>> postPage(HrmRecruitPostPageReqVO reqVO) {
        reqVO.setStatus(PUBLISHED.getStatus());
        PageResult<HrmRecruitPostDO> page = postService.getPortalRecruitPostPage(reqVO);
        return success(new PageResult<>(convertList(page.getList(), this::buildPost), page.getTotal()));
    }

    @GetMapping("/post-type/list")
    @Operation(summary = "查询可用招聘职位类型")
    @PermitAll
    @TenantIgnore
    public CommonResult<List<HrmPortalRecruitPostTypeRespVO>> postTypeList() {
        return success(TenantUtils.executeIgnore(() -> convertList(postTypeService.getRecruitPostTypeList(0),
                item -> BeanUtils.toBean(item, HrmPortalRecruitPostTypeRespVO.class))));
    }

    @GetMapping("/post/get")
    @Operation(summary = "查询已发布招聘职位详情")
    @PermitAll
    @TenantIgnore
    public CommonResult<HrmPortalRecruitPostRespVO> postGet(@RequestParam("id") Long id) {
        return success(buildPost(postService.getPortalRecruitPostPublished(id)));
    }

    @PostMapping("/application/create")
    @Operation(summary = "在线投递招聘应聘")
    public CommonResult<Long> applicationCreate(@Valid @RequestBody HrmPortalRecruitApplyReqVO reqVO) {
        return success(applicationService.createPortalApplication(getLoginUserId(), reqVO));
    }

    @GetMapping("/application/page")
    @Operation(summary = "查询我的招聘应聘")
    public CommonResult<PageResult<HrmPortalRecruitApplicationRespVO>> applicationPage(
            HrmPortalRecruitApplicationPageReqVO reqVO) {
        PageResult<HrmRecruitApplicationDO> page = applicationService
                .getPortalApplicationPage(getLoginUserId(), reqVO);
        return success(new PageResult<>(convertList(page.getList(), this::buildApplication), page.getTotal()));
    }

    @GetMapping("/application/get")
    @Operation(summary = "查询我的招聘应聘详情")
    public CommonResult<HrmPortalRecruitApplicationRespVO> applicationGet(@RequestParam("id") Long id) {
        return success(buildApplication(applicationService.getPortalApplication(getLoginUserId(), id)));
    }

    @PutMapping("/application/withdraw")
    @Operation(summary = "撤回我的招聘应聘")
    public CommonResult<Boolean> applicationWithdraw(@Valid @RequestBody HrmPortalRecruitWithdrawReqVO reqVO) {
        applicationService.withdrawPortalApplication(getLoginUserId(), reqVO.getId(), reqVO.getReason());
        return success(true);
    }

    private HrmPortalRecruitPostRespVO buildPost(HrmRecruitPostDO post) {
        if (post == null) return null;
        HrmPortalRecruitPostRespVO vo = BeanUtils.toBean(post, HrmPortalRecruitPostRespVO.class);
        vo.setAreaName(AreaUtils.format(post.getAreaId()));
        HrmRecruitPostTypeDO type = post.getPostTypeId() == null ? null
                : TenantUtils.executeIgnore(() -> postTypeService.getRecruitPostType(post.getPostTypeId()));
        vo.setPostTypeName(type == null ? null : type.getName());
        fillOwnerUser(vo, getPostOwnerUser(post));
        return vo;
    }

    private HrmPortalRecruitApplicationRespVO buildApplication(HrmRecruitApplicationDO application) {
        HrmPortalRecruitApplicationRespVO vo = BeanUtils.toBean(application, HrmPortalRecruitApplicationRespVO.class);
        HrmRecruitPostDO post = TenantUtils.executeIgnore(() -> postService.getRecruitPost(application.getPostId()));
        HrmRecruitCandidateDO candidate = TenantUtils.executeIgnore(
                () -> candidateService.getRecruitCandidate(application.getCandidateId()));
        vo.setPostName(post == null ? null : post.getPostName());
        fillOwnerUser(vo, getPostOwnerUser(post));
        vo.setCandidateName(candidate == null ? null : candidate.getName());
        vo.setMobileMasked(maskMobile(candidate == null ? null : candidate.getMobile()));
        vo.setEmail(candidate == null ? null : candidate.getEmail());
        HrmRecruitApplicationStatusEnum status = HrmRecruitApplicationStatusEnum.valueOfStatus(application.getStatus());
        vo.setStatusName(status == null ? null : status.getName());
        if (application.getResumeId() != null) {
            var resume = TenantUtils.executeIgnore(
                    () -> resumeService.getAuthorizedRecruitResume(application.getResumeId()));
            vo.setResumeFileName(resume.getFileName()).setResumeFileUrl(resume.getFileUrl());
        }
        List<HrmRecruitInterviewDO> interviews = TenantUtils.executeIgnore(
                () -> interviewMapper.selectListByApplicationId(application.getId()));
        vo.setInterviews(convertList(interviews, item -> BeanUtils.toBean(item, HrmPortalRecruitApplicationRespVO.Interview.class)));
        HrmRecruitOfferDO offer = TenantUtils.executeIgnore(
                () -> offerMapper.selectByApplicationId(application.getId()));
        vo.setOffer(offer == null ? null : BeanUtils.toBean(offer, HrmPortalRecruitApplicationRespVO.Offer.class));
        return vo;
    }

    /**
     * 查询岗位咨询联系人对应的系统用户。
     *
     * <p>优先使用岗位配置的招聘负责人；历史岗位可能没有配置负责人，此时退回到岗位创建人，
     * 以保证求职者仍能联系发布该岗位的 HR。公开岗位接口需要跨租户读取职位，故所有用户查询
     * 均必须使用岗位所属租户，且负责人档案或系统用户服务异常时不能影响职位列表/详情的正常浏览。</p>
     */
    private AdminUserRespDTO getPostOwnerUser(HrmRecruitPostDO post) {
        if (post == null || post.getTenantId() == null) {
            return null;
        }
        Long tenantId = post.getTenantId();
        if (post.getOwnerEmployeeId() != null) {
            try {
                HrmEmployeeDO owner = TenantUtils.execute(tenantId,
                        () -> employeeService.getEmployee(post.getOwnerEmployeeId()));
                if (owner != null && owner.getUserId() != null) {
                    AdminUserRespDTO ownerUser = TenantUtils.execute(tenantId,
                            () -> adminUserApi.getUserIgnoreTenant(owner.getUserId()).getCheckedData());
                    if (ownerUser != null) {
                        return ownerUser;
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("[getPostOwnerUser][postId({}) tenantId({}) ownerEmployeeId({}) 查询招聘负责人失败，"
                                + "尝试回退到岗位创建人]",
                        post.getId(), tenantId, post.getOwnerEmployeeId(), ex);
            }
        }

        Long creatorUserId = NumberUtil.isNumber(post.getCreator())
                ? NumberUtil.parseLong(post.getCreator()) : null;
        if (creatorUserId == null) {
            return null;
        }
        try {
            return TenantUtils.execute(tenantId,
                    () -> adminUserApi.getUserIgnoreTenant(creatorUserId).getCheckedData());
        } catch (RuntimeException ex) {
            log.warn("[getPostOwnerUser][postId({}) tenantId({}) creator({}) 查询岗位创建人失败，"
                            + "跳过 IM 联系人信息]",
                    post.getId(), tenantId, post.getCreator(), ex);
            return null;
        }
    }

    private void fillOwnerUser(HrmPortalRecruitPostRespVO vo, AdminUserRespDTO ownerUser) {
        if (ownerUser == null) {
            return;
        }
        vo.setOwnerUserId(ownerUser.getId());
        vo.setOwnerUserName(ownerUser.getNickname());
        vo.setOwnerUserAvatar(ownerUser.getAvatar());
    }

    private void fillOwnerUser(HrmPortalRecruitApplicationRespVO vo, AdminUserRespDTO ownerUser) {
        if (ownerUser == null) {
            return;
        }
        vo.setOwnerUserId(ownerUser.getId());
        vo.setOwnerUserName(ownerUser.getNickname());
        vo.setOwnerUserAvatar(ownerUser.getAvatar());
    }

    private String maskMobile(String mobile) {
        if (mobile == null || mobile.length() < 7) return mobile;
        return mobile.substring(0, 3) + "****" + mobile.substring(mobile.length() - 4);
    }
}
