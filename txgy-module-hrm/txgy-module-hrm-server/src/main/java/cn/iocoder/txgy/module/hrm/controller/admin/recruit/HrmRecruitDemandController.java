package cn.iocoder.txgy.module.hrm.controller.admin.recruit;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand.HrmRecruitDemandPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand.HrmRecruitDemandRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.demand.HrmRecruitDemandSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.recruit.demand.HrmRecruitDemandDO;
import cn.iocoder.txgy.module.hrm.enums.recruit.demand.HrmRecruitDemandStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.recruit.demand.HrmRecruitDemandUrgencyEnum;
import cn.iocoder.txgy.module.hrm.service.company.HrmCompanyProfileService;
import cn.iocoder.txgy.module.hrm.service.recruit.demand.HrmRecruitDemandService;
import cn.iocoder.txgy.module.system.api.dept.DeptApi;
import cn.iocoder.txgy.module.system.api.dept.dto.DeptRespDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 用工需求单：项目部 / 用工部门提出用人需求，HR 按需求招聘并回写到岗人数。
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 用工需求单")
@RestController
@RequestMapping("/hrm/recruit/demand")
@Validated
public class HrmRecruitDemandController {

    @Resource
    private HrmRecruitDemandService recruitDemandService;
    @Resource
    private DeptApi deptApi;
    @Resource
    private HrmCompanyProfileService companyProfileService;

    @PostMapping("/create")
    @Operation(summary = "创建用工需求单")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:demand:create')")
    public CommonResult<Long> createRecruitDemand(@Valid @RequestBody HrmRecruitDemandSaveReqVO createReqVO) {
        return success(recruitDemandService.createRecruitDemand(createReqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新用工需求单")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:demand:update')")
    public CommonResult<Boolean> updateRecruitDemand(@Valid @RequestBody HrmRecruitDemandSaveReqVO updateReqVO) {
        recruitDemandService.updateRecruitDemand(updateReqVO);
        return success(true);
    }

    @PutMapping("/update-status")
    @Operation(summary = "更新用工需求单状态")
    @Parameter(name = "id", description = "编号", required = true)
    @Parameter(name = "status", description = "状态", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:recruit:demand:update')")
    public CommonResult<Boolean> updateRecruitDemandStatus(@RequestParam("id") Long id,
                                                          @RequestParam("status") Integer status) {
        recruitDemandService.updateRecruitDemandStatus(id, status);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除用工需求单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:recruit:demand:delete')")
    public CommonResult<Boolean> deleteRecruitDemand(@RequestParam("id") Long id) {
        recruitDemandService.deleteRecruitDemand(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得用工需求单")
    @Parameter(name = "id", description = "编号", required = true)
    @PreAuthorize("@ss.hasPermission('hrm:recruit:demand:query')")
    public CommonResult<HrmRecruitDemandRespVO> getRecruitDemand(@RequestParam("id") Long id) {
        HrmRecruitDemandDO demand = recruitDemandService.getRecruitDemand(id);
        if (demand == null) {
            return success(null);
        }
        return success(buildRespVOList(List.of(demand)).get(0));
    }

    @GetMapping("/page")
    @Operation(summary = "获得用工需求单分页")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:demand:query')")
    public CommonResult<PageResult<HrmRecruitDemandRespVO>> getRecruitDemandPage(
            @Valid HrmRecruitDemandPageReqVO pageReqVO) {
        PageResult<HrmRecruitDemandDO> pageResult = recruitDemandService.getRecruitDemandPage(pageReqVO);
        return success(new PageResult<>(buildRespVOList(pageResult.getList()), pageResult.getTotal()));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得可招聘的用工需求单精简列表")
    public CommonResult<List<HrmRecruitDemandRespVO>> getRecruitingDemandSimpleList() {
        return success(buildRespVOList(recruitDemandService.getRecruitingDemandList()));
    }

    /**
     * 补充部门名称、用工主体名称、缺口人数、枚举名称
     */
    private List<HrmRecruitDemandRespVO> buildRespVOList(List<HrmRecruitDemandDO> list) {
        if (CollUtil.isEmpty(list)) {
            return List.of();
        }
        Set<Long> deptIds = list.stream().map(HrmRecruitDemandDO::getDeptId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, DeptRespDTO> deptMap = CollUtil.isEmpty(deptIds) ? Map.of() : deptApi.getDeptMap(deptIds);
        Map<Long, HrmCompanyProfileDO> companyMap = companyProfileService.getProfileList().stream()
                .collect(Collectors.toMap(HrmCompanyProfileDO::getId, item -> item, (a, b) -> a));
        return BeanUtils.toBean(list, HrmRecruitDemandRespVO.class, vo -> {
            DeptRespDTO dept = vo.getDeptId() == null ? null : deptMap.get(vo.getDeptId());
            if (dept != null) {
                vo.setDeptName(dept.getName());
            }
            HrmCompanyProfileDO company = vo.getCompanyProfileId() == null ? null
                    : companyMap.get(vo.getCompanyProfileId());
            if (company != null) {
                vo.setCompanyProfileName(company.getName());
            }
            int headcount = vo.getHeadcount() == null ? 0 : vo.getHeadcount();
            int onboard = vo.getOnboardCount() == null ? 0 : vo.getOnboardCount();
            vo.setGapCount(Math.max(headcount - onboard, 0));
            HrmRecruitDemandStatusEnum status = HrmRecruitDemandStatusEnum.valueOf(vo.getStatus());
            vo.setStatusName(status == null ? null : status.getName());
            HrmRecruitDemandUrgencyEnum urgency = HrmRecruitDemandUrgencyEnum.valueOf(vo.getUrgency());
            vo.setUrgencyName(urgency == null ? null : urgency.getName());
        });
    }

}
