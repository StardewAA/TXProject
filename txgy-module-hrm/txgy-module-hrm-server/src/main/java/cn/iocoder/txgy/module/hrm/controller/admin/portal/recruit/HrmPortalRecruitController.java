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
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.company.HrmRecruitCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.company.HrmRecruitCompanyProfileMapper;
import cn.iocoder.txgy.module.hrm.dal.mysql.recruit.post.HrmRecruitPostMapper;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

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
    private static final Set<String> STANDARD_ROOT_NAMES = Set.of(
            "施工技能类（一线操作岗）", "工程技术类", "项目管理类", "质量检验类", "生产制造类",
            "采购与供应链类", "市场营销类", "职能管理类", "国际业务类", "培训生/储备类");
    private static final Set<String> STANDARD_LEAF_NAMES = Set.of(
            "防腐工", "保温工", "喷砂工", "油漆工", "管工", "铆工", "焊工", "架子工", "电工", "瓦工",
            "钢筋工", "木工", "缝纫工", "普工/力工", "项目经理", "项目副经理", "技术负责人/工程师",
            "施工员", "施工队长/工长", "预算员/造价员", "安全员", "质检员", "试验/检测员", "资料员",
            "材料员/库管", "设备维修工", "机械设备管理员", "市场业务经理", "投标专员", "采购员",
            "财务会计", "行政人事专员", "防腐保温管理（校招管培方向）");

    @Resource private HrmRecruitPostService postService;
    @Resource private HrmRecruitPostTypeService postTypeService;
    @Resource private HrmRecruitCompanyProfileService companyProfileService;
    @Resource private HrmRecruitCompanyProfileMapper companyProfileMapper;
    @Resource private HrmRecruitPostMapper postMapper;
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
        return success(TenantUtils.executeIgnore(() -> buildStandardPostTypeCatalog(
                postTypeService.getRecruitPostTypeList(0))));
    }

    private List<HrmPortalRecruitPostTypeRespVO> buildStandardPostTypeCatalog(
            List<HrmRecruitPostTypeDO> postTypes) {
        Map<Long, HrmRecruitPostTypeDO> typeMap = new LinkedHashMap<>();
        postTypes.forEach(type -> typeMap.put(type.getId(), type));
        Map<String, HrmRecruitPostTypeDO> roots = new LinkedHashMap<>();
        postTypes.stream()
                .filter(type -> Objects.equals(type.getParentId(), 0L)
                        && STANDARD_ROOT_NAMES.contains(type.getName()))
                .sorted(Comparator.comparing(HrmRecruitPostTypeDO::getSort,
                                Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(HrmRecruitPostTypeDO::getId))
                .forEach(type -> roots.putIfAbsent(type.getName(), type));

        List<HrmPortalRecruitPostTypeRespVO> result = new ArrayList<>();
        for (HrmRecruitPostTypeDO root : roots.values()) {
            result.add(BeanUtils.toBean(root, HrmPortalRecruitPostTypeRespVO.class));
            Map<String, HrmRecruitPostTypeDO> leaves = new LinkedHashMap<>();
            postTypes.stream()
                    // 一级分类下的职位类型由 HRM 数据维护，门户不再限制旧的职位白名单。
                    .filter(type -> {
                        HrmRecruitPostTypeDO parent = typeMap.get(type.getParentId());
                        return parent != null && root.getName().equals(parent.getName());
                    })
                    .sorted(Comparator.comparing(HrmRecruitPostTypeDO::getSort,
                                    Comparator.nullsLast(Integer::compareTo))
                            .thenComparing(HrmRecruitPostTypeDO::getId))
                    .forEach(type -> leaves.putIfAbsent(type.getName(), type));
            leaves.values().forEach(leaf -> {
                HrmPortalRecruitPostTypeRespVO response =
                        BeanUtils.toBean(leaf, HrmPortalRecruitPostTypeRespVO.class);
                response.setParentId(root.getId());
                result.add(response);
            });
        }
        return result;
    }

    @GetMapping("/post/get")
    @Operation(summary = "查询已发布招聘职位详情")
    @PermitAll
    @TenantIgnore
    public CommonResult<HrmPortalRecruitPostRespVO> postGet(@RequestParam("id") Long id) {
        return success(buildPost(postService.getPortalRecruitPostPublished(id)));
    }

    @GetMapping("/company/list")
    @Operation(summary = "查询招聘门户企业目录")
    @PermitAll
    @TenantIgnore
    public CommonResult<List<HrmPortalRecruitCompanyRespVO>> companyList() {
        return success(TenantUtils.executeIgnore(() -> {
            Set<Long> tenantIds = new LinkedHashSet<>();
            companyProfileMapper.selectPortalTenantIds().stream()
                    .map(HrmRecruitCompanyProfileDO::getTenantId)
                    .filter(Objects::nonNull).forEach(tenantIds::add);
            tenantIds.addAll(postMapper.selectPortalHiringTenantIds());
            if (tenantIds.isEmpty()) {
                return List.of();
            }
            Map<Long, Long> postCounts = postMapper.selectPortalPostCountGroupByTenant(List.copyOf(tenantIds))
                    .stream().collect(Collectors.toMap(
                            row -> ((Number) row.get("tenant_id")).longValue(),
                            row -> ((Number) row.get("post_count")).longValue(), Long::sum));
            List<Long> portalTenantIds = List.copyOf(tenantIds);
            Map<Long, HrmRecruitCompanyProfileDO> profiles = companyProfileMapper
                    .selectPortalByTenantIds(portalTenantIds).stream()
                    .collect(Collectors.toMap(HrmRecruitCompanyProfileDO::getTenantId, profile -> profile));
            Map<Long, String> tenantNames = companyProfileMapper.selectPortalTenantNames(portalTenantIds).stream()
                    .collect(Collectors.toMap(
                            row -> ((Number) row.get("id")).longValue(),
                            row -> String.valueOf(row.get("name"))));
            return portalTenantIds.stream().map(tenantId -> {
                HrmRecruitCompanyProfileDO profile = profiles.get(tenantId);
                if (profile != null) {
                    return buildCompany(profile, postCounts.getOrDefault(tenantId, 0L));
                }
                String tenantName = tenantNames.get(tenantId);
                if (tenantName == null) {
                    return null;
                }
                HrmPortalRecruitCompanyRespVO company = new HrmPortalRecruitCompanyRespVO();
                company.setId(tenantId);
                company.setTenantId(tenantId);
                company.setName(tenantName);
                company.setPostCount(postCounts.getOrDefault(tenantId, 0L));
                return company;
            }).filter(Objects::nonNull).toList();
        }));
    }

    @GetMapping("/company/get")
    @Operation(summary = "查询招聘企业主页")
    @PermitAll
    @TenantIgnore
    public CommonResult<HrmPortalRecruitCompanyRespVO> companyGet(@RequestParam("tenantId") Long tenantId) {
        HrmRecruitCompanyProfileDO profile = companyProfileService.getPortalProfile(tenantId);
        return success(profile == null ? null : buildCompany(profile, 0L));
    }

    @GetMapping("/company/post/page")
    @Operation(summary = "查询企业在招职位")
    @PermitAll
    @TenantIgnore
    public CommonResult<PageResult<HrmPortalRecruitPostRespVO>> companyPostPage(
            @Valid HrmPortalRecruitCompanyPostPageReqVO reqVO) {
        PageResult<HrmRecruitPostDO> page = TenantUtils.executeIgnore(
                () -> postMapper.selectPortalPageByTenantId(reqVO.getTenantId(), reqVO));
        return success(new PageResult<>(convertList(page.getList(), this::buildPost), page.getTotal()));
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
        HrmRecruitPostTypeDO parentType = resolveLevel2Type(type);
        vo.setPostTypeParentId(parentType == null ? null : parentType.getId());
        vo.setPostTypeParentName(parentType == null ? null : parentType.getName());
        try {
            HrmRecruitCompanyProfileDO company = companyProfileService.getPortalProfile(post.getTenantId());
            if (company != null) {
                vo.setCompanyName(company.getName());
                vo.setCompanyShortName(company.getShortName());
                vo.setCompanyLogo(company.getLogo());
                vo.setCompanyIndustry(company.getIndustry());
                vo.setCompanyScale(company.getScale());
                vo.setCompanyFinancingStage(company.getFinancingStage());
            }
        } catch (RuntimeException ex) {
            log.warn("[buildPost][postId({}) tenantId({}) 招聘企业档案读取失败，跳过企业信息]",
                    post.getId(), post.getTenantId(), ex);
        }
        fillOwnerUser(vo, getPostOwnerUser(post));
        return vo;
    }

    /**
     * 沿 parentId 往上找到岗位所属的二级分类（根 → 二级 → 三级，最多三级）。
     *
     * 判定口径：父节点的 parentId 为 0/空，说明父节点是根，那么当前节点就是二级；
     * 岗位直接挂在根上时没有二级祖先，返回 null。
     *
     * 这里按岗位逐个查父级，与 buildPost 里已有的岗位类型、企业档案查询属同一层 N+1。
     * 门户一页最多百来条、每条至多两次 selectById，量级可接受；
     * 若日后成为瓶颈，可改为按页收集去重后批量取。
     */
    private HrmRecruitPostTypeDO resolveLevel2Type(HrmRecruitPostTypeDO type) {
        HrmRecruitPostTypeDO current = type;
        for (int depth = 0; current != null && depth < 5; depth++) {
            Long parentId = current.getParentId();
            if (parentId == null || parentId == 0L) {
                return null;
            }
            HrmRecruitPostTypeDO parent = TenantUtils.executeIgnore(
                    () -> postTypeService.getRecruitPostType(parentId));
            if (parent == null) {
                return null;
            }
            if (parent.getParentId() == null || parent.getParentId() == 0L) {
                return current;
            }
            current = parent;
        }
        return null;
    }

    private HrmPortalRecruitCompanyRespVO buildCompany(HrmRecruitCompanyProfileDO profile, Long postCount) {
        HrmPortalRecruitCompanyRespVO vo = BeanUtils.toBean(profile, HrmPortalRecruitCompanyRespVO.class);
        vo.setId(profile.getTenantId());
        vo.setAreaName(AreaUtils.format(profile.getAreaId()));
        vo.setPostCount(postCount);
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
