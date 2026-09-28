package cn.iocoder.txgy.module.hrm.controller.admin.company;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.company.vo.HrmCompanyProfileRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.company.vo.HrmCompanyProfileSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.service.company.HrmCompanyProfileService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 公司信息（用工主体）")
@RestController
@RequestMapping("/hrm/company/profile")
@Validated
public class HrmCompanyProfileController {

    @Resource
    private HrmCompanyProfileService companyProfileService;

    @GetMapping("/list")
    @Operation(summary = "获得公司信息列表")
    @PreAuthorize("@ss.hasPermission('hrm:company-profile:query')")
    public CommonResult<List<HrmCompanyProfileRespVO>> getProfileList() {
        List<HrmCompanyProfileDO> list = companyProfileService.getProfileList();
        return success(BeanUtils.toBean(list, HrmCompanyProfileRespVO.class));
    }

    @GetMapping("/get-default")
    @Operation(summary = "获得默认用工主体")
    @PreAuthorize("@ss.hasPermission('hrm:company-profile:query')")
    public CommonResult<HrmCompanyProfileRespVO> getDefaultProfile() {
        return success(BeanUtils.toBean(companyProfileService.getDefaultProfile(), HrmCompanyProfileRespVO.class));
    }

    @PostMapping("/save")
    @Operation(summary = "保存公司信息（新增或更新）")
    @PreAuthorize("@ss.hasPermission('hrm:company-profile:update')")
    public CommonResult<Long> saveProfile(@Valid @RequestBody HrmCompanyProfileSaveReqVO reqVO) {
        return success(companyProfileService.saveProfile(reqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除公司信息")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:company-profile:update')")
    public CommonResult<Boolean> deleteProfile(@RequestParam("id") Long id) {
        companyProfileService.deleteProfile(id);
        return success(true);
    }

}
