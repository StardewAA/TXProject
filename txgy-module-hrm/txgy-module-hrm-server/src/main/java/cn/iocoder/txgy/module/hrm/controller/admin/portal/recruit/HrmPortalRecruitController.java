package cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.NumberUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.ip.core.utils.AreaUtils;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.portal.recruit.vo.*;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company.HrmRecruitCompanyProfileVO;
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
import cn.iocoder.txgy.module.hrm.service.recruit.company.HrmRecruitCompanyProfileService;
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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId;
import static cn.iocoder.txgy.module.hrm.enums.recruit.post.HrmRecruitPostStatusEnum.PUBLISHED;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertList;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertMap;
import static cn.iocoder.txgy.framework.common.util.collection.CollectionUtils.convertSet;

@Tag(name = "员工端 - HRM 招聘应聘")
@RestController
@RequestMapping("/hrm/portal/recruit")
@Validated
@Slf4j
public class HrmPortalRecruitController {
    @Resource private HrmRecruitPostService postService;
    @Resource private HrmRecruitPostTypeService postTypeService;
    @Resource private HrmRecruitCompanyProfileService companyProfileService;
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
        // 批量装配：职位类型与联系人一次性批量查询，避免按职位逐条查询（原实现为 N+1）
        return success(new PageResult<>(buildPosts(page.getList()), page.getTotal()));
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

    @GetMapping("/company-profile/get")
    @Operation(summary = "查询公开招聘企业档案")
    @PermitAll
    @TenantIgnore
    public CommonResult<HrmRecruitCompanyProfileVO> companyProfileGet(@RequestParam("tenantId") Long tenantId) {
        return success(TenantUtils.execute(tenantId, companyProfileService::getProfile));
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

    /**
     * 批量装配招聘职位列表。
     *
     * <p>门户职位列表一次可能返回上百条职位，若按条查询职位类型与岗位联系人会产生 N+1 查询。
     * 这里统一改为批量查询，输出字段与单条 {@link #buildPost(HrmRecruitPostDO)} 完全一致。</p>
     */
    private List<HrmPortalRecruitPostRespVO> buildPosts(List<HrmRecruitPostDO> posts) {
        if (CollUtil.isEmpty(posts)) {
            return Collections.emptyList();
        }
        // 1. 批量查询职位类型（与单条实现一致，忽略租户）
        Set<Long> postTypeIds = convertSet(posts, HrmRecruitPostDO::getPostTypeId);
        Map<Long, HrmRecruitPostTypeDO> postTypeMap = CollUtil.isEmpty(postTypeIds)
                ? Collections.emptyMap()
                : TenantUtils.executeIgnore(() -> postTypeService.getRecruitPostTypeMap(postTypeIds));
        // 2. 批量查询岗位联系人
        Map<Long, AdminUserRespDTO> ownerUserMap = getPostOwnerUserMap(posts);
        // 3. 装配，字段与单条 buildPost 保持一致
        return convertList(posts, post -> {
            if (post == null) {
                return null;
            }
            HrmPortalRecruitPostRespVO vo = BeanUtils.toBean(post, HrmPortalRecruitPostRespVO.class);
            vo.setAreaName(AreaUtils.format(post.getAreaId()));
            HrmRecruitPostTypeDO type = post.getPostTypeId() == null ? null : postTypeMap.get(post.getPostTypeId());
            vo.setPostTypeName(type == null ? null : type.getName());
            fillOwnerUser(vo, ownerUserMap.get(post.getId()));
            return vo;
        });
    }

    /**
     * 批量查询职位列表对应的岗位联系人（postId -&gt; 用户）。
     *
     * <p>取值优先级与 {@link #getPostOwnerUser(HrmRecruitPostDO)} 一致：优先取岗位发布 HR
     * （creator 为数字时），查不到再回退到招聘负责人员工绑定的系统用户。
     * 两条路径均改为批量查询，且任一职位查询失败都不影响整页浏览。</p>
     */
    private Map<Long, AdminUserRespDTO> getPostOwnerUserMap(List<HrmRecruitPostDO> posts) {
        Map<Long, Long> creatorUserIdByPost = new HashMap<>();
        Map<Long, Long> fallbackEmployeeIdByPost = new HashMap<>();
        Set<Long> userIds = new HashSet<>();
        for (HrmRecruitPostDO post : posts) {
            if (post == null || post.getTenantId() == null) {
                continue;
            }
            Long creatorUserId = NumberUtil.isNumber(post.getCreator())
                    ? NumberUtil.parseLong(post.getCreator()) : null;
            if (creatorUserId != null) {
                creatorUserIdByPost.put(post.getId(), creatorUserId);
                userIds.add(creatorUserId);
            } else if (post.getOwnerEmployeeId() != null) {
                fallbackEmployeeIdByPost.put(post.getId(), post.getOwnerEmployeeId());
            }
        }
        // 回退路径：按租户分组批量查询员工，再取员工绑定的系统用户
        Map<Long, Long> fallbackUserIdByPost = new HashMap<>();
        if (!fallbackEmployeeIdByPost.isEmpty()) {
            Map<Long, Set<Long>> employeeIdsByTenant = new HashMap<>();
            for (HrmRecruitPostDO post : posts) {
                Long employeeId = fallbackEmployeeIdByPost.get(post.getId());
                if (employeeId != null) {
                    employeeIdsByTenant.computeIfAbsent(post.getTenantId(), key -> new HashSet<>()).add(employeeId);
                }
            }
            employeeIdsByTenant.forEach((tenantId, employeeIds) -> {
                try {
                    Map<Long, HrmEmployeeDO> employeeMap = TenantUtils.execute(tenantId,
                            () -> employeeService.getEmployeeMap(employeeIds));
                    fallbackEmployeeIdByPost.forEach((postId, employeeId) -> {
                        HrmEmployeeDO employee = employeeMap.get(employeeId);
                        if (employee != null && employee.getUserId() != null) {
                            fallbackUserIdByPost.put(postId, employee.getUserId());
                            userIds.add(employee.getUserId());
                        }
                    });
                } catch (RuntimeException ex) {
                    log.warn("[getPostOwnerUserMap][tenantId({}) employeeIds({}) 查询招聘负责人失败，"
                            + "跳过 IM 联系人信息]", tenantId, employeeIds, ex);
                }
            });
        }
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        // 批量查询系统用户：用户表不含租户字段，跨租户批量查询不会串数据
        Map<Long, AdminUserRespDTO> userMap;
        try {
            userMap = convertMap(adminUserApi.getUserListIgnoreTenant(userIds).getCheckedData(),
                    AdminUserRespDTO::getId);
        } catch (RuntimeException ex) {
            log.warn("[getPostOwnerUserMap][userIds({}) 批量查询岗位联系人失败，跳过 IM 联系人信息]",
                    userIds, ex);
            return Collections.emptyMap();
        }
        Map<Long, AdminUserRespDTO> result = new HashMap<>();
        for (HrmRecruitPostDO post : posts) {
            if (post == null) {
                continue;
            }
            AdminUserRespDTO ownerUser = null;
            Long creatorUserId = creatorUserIdByPost.get(post.getId());
            if (creatorUserId != null) {
                ownerUser = userMap.get(creatorUserId);
            }
            if (ownerUser == null) {
                Long fallbackUserId = fallbackUserIdByPost.get(post.getId());
                if (fallbackUserId != null) {
                    ownerUser = userMap.get(fallbackUserId);
                }
            }
            if (ownerUser != null) {
                result.put(post.getId(), ownerUser);
            }
        }
        return result;
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
            // 简历附件授权失败不应让整页应聘记录查询失败；记录本身仍需正常展示。
            try {
                var resume = TenantUtils.executeIgnore(
                        () -> resumeService.getAuthorizedRecruitResume(application.getResumeId()));
                if (resume != null) {
                    vo.setResumeFileName(resume.getFileName()).setResumeFileUrl(resume.getFileUrl());
                }
            } catch (RuntimeException ex) {
                log.warn("[buildApplication][applicationId({}) resumeId({}) 简历授权失败，跳过附件信息]",
                        application.getId(), application.getResumeId(), ex);
            }
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
     * <p>优先使用岗位发布 HR；历史岗位可能没有可用的 HR 发布账号，此时退回到岗位配置的招聘负责人，
     * 以保证求职者仍能联系招聘方。公开岗位接口需要跨租户读取职位，故所有用户查询
     * 均必须使用岗位所属租户，且负责人档案或系统用户服务异常时不能影响职位列表/详情的正常浏览。</p>
     */
    private AdminUserRespDTO getPostOwnerUser(HrmRecruitPostDO post) {
        if (post == null || post.getTenantId() == null) {
            return null;
        }
        Long tenantId = post.getTenantId();
        Long creatorUserId = NumberUtil.isNumber(post.getCreator())
                ? NumberUtil.parseLong(post.getCreator()) : null;
        if (creatorUserId != null) {
            try {
                AdminUserRespDTO creator = TenantUtils.execute(tenantId,
                        () -> adminUserApi.getUserIgnoreTenant(creatorUserId).getCheckedData());
                if (creator != null) {
                    return creator;
                }
            } catch (RuntimeException ex) {
                log.warn("[getPostOwnerUser][postId({}) tenantId({}) creator({}) 查询岗位发布 HR 失败，"
                                + "尝试回退到招聘负责人]",
                        post.getId(), tenantId, post.getCreator(), ex);
            }
        }

        if (post.getOwnerEmployeeId() == null) {
            return null;
        }
        try {
            HrmEmployeeDO owner = TenantUtils.execute(tenantId,
                    () -> employeeService.getEmployee(post.getOwnerEmployeeId()));
            if (owner == null || owner.getUserId() == null) {
                return null;
            }
            return TenantUtils.execute(tenantId,
                    () -> adminUserApi.getUserIgnoreTenant(owner.getUserId()).getCheckedData());
        } catch (RuntimeException ex) {
            log.warn("[getPostOwnerUser][postId({}) tenantId({}) ownerEmployeeId({}) 查询招聘负责人失败，"
                            + "跳过 IM 联系人信息]",
                    post.getId(), tenantId, post.getOwnerEmployeeId(), ex);
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
