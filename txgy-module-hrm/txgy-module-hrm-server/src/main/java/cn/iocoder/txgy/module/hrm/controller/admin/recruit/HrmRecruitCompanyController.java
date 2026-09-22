package cn.iocoder.txgy.module.hrm.controller.admin.recruit;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.hrm.controller.admin.recruit.vo.company.HrmRecruitCompanyProfileVO;
import cn.iocoder.txgy.module.hrm.service.recruit.company.HrmRecruitCompanyProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 招聘企业")
@RestController
@RequestMapping("/hrm/recruit/company")
@Validated
public class HrmRecruitCompanyController {
    @Resource
    private HrmRecruitCompanyProfileService profileService;

    @GetMapping("/get")
    @Operation(summary = "获得当前租户招聘企业档案")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:post:query')")
    public CommonResult<HrmRecruitCompanyProfileVO> get() {
        return success(profileService.getProfile());
    }

    @PutMapping("/save")
    @Operation(summary = "保存当前租户招聘企业档案")
    @PreAuthorize("@ss.hasPermission('hrm:recruit:post:update')")
    public CommonResult<Boolean> save(@Valid @RequestBody HrmRecruitCompanyProfileVO reqVO) {
        profileService.saveProfile(reqVO);
        return success(true);
    }
}
