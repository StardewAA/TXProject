package cn.iocoder.txgy.module.hrm.controller.admin.employment;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employment.vo.HrmEmploymentTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employment.vo.HrmEmploymentTypeRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employment.vo.HrmEmploymentTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractTemplateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employment.HrmEmploymentTypeDO;
import cn.iocoder.txgy.module.hrm.service.contract.HrmContractTemplateService;
import cn.iocoder.txgy.module.hrm.service.employment.HrmEmploymentTypeService;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * HRM 用工形式 Controller
 *
 * <p>「用工形式」是防腐保温行业用工的总开关：定了它，才自动决定签哪份合同、怎么算钱、要不要记工日、
 * 要不要人脸打卡、以及是否走自动转正。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 用工形式")
@RestController
@RequestMapping("/hrm/employment-type")
@Validated
public class HrmEmploymentTypeController {

    @Resource
    private HrmEmploymentTypeService employmentTypeService;
    @Resource
    private HrmContractTemplateService contractTemplateService;

    @PostMapping("/create")
    @Operation(summary = "创建用工形式")
    @PreAuthorize("@ss.hasPermission('hrm:employment-type:create')")
    public CommonResult<Long> createEmploymentType(@Valid @RequestBody HrmEmploymentTypeSaveReqVO reqVO) {
        return success(employmentTypeService.createEmploymentType(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新用工形式")
    @PreAuthorize("@ss.hasPermission('hrm:employment-type:update')")
    public CommonResult<Boolean> updateEmploymentType(@Valid @RequestBody HrmEmploymentTypeSaveReqVO reqVO) {
        employmentTypeService.updateEmploymentType(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除用工形式")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:employment-type:delete')")
    public CommonResult<Boolean> deleteEmploymentType(@RequestParam("id") Long id) {
        employmentTypeService.deleteEmploymentType(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得用工形式")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:employment-type:query')")
    public CommonResult<HrmEmploymentTypeRespVO> getEmploymentType(@RequestParam("id") Long id) {
        HrmEmploymentTypeDO employmentType = employmentTypeService.getEmploymentType(id);
        return success(buildRespVO(employmentType));
    }

    @GetMapping("/list")
    @Operation(summary = "获得用工形式列表（含停用）")
    @PreAuthorize("@ss.hasPermission('hrm:employment-type:query')")
    public CommonResult<List<HrmEmploymentTypeRespVO>> getEmploymentTypeList() {
        return success(buildRespVOList(employmentTypeService.getEmploymentTypeList()));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得启用中的用工形式列表（下拉用）")
    @PreAuthorize("@ss.hasPermission('hrm:employment-type:query')")
    public CommonResult<List<HrmEmploymentTypeRespVO>> getEnabledEmploymentTypeList() {
        return success(buildRespVOList(employmentTypeService.getEnabledEmploymentTypeList()));
    }

    @GetMapping("/page")
    @Operation(summary = "获得用工形式分页")
    @PreAuthorize("@ss.hasPermission('hrm:employment-type:query')")
    public CommonResult<PageResult<HrmEmploymentTypeRespVO>> getEmploymentTypePage(
            @Valid HrmEmploymentTypePageReqVO pageReqVO) {
        PageResult<HrmEmploymentTypeDO> pageResult = employmentTypeService.getEmploymentTypePage(pageReqVO);
        return success(new PageResult<>(buildRespVOList(pageResult.getList()), pageResult.getTotal()));
    }

    private List<HrmEmploymentTypeRespVO> buildRespVOList(List<HrmEmploymentTypeDO> list) {
        if (CollUtil.isEmpty(list)) {
            return java.util.Collections.emptyList();
        }
        // 批量回填默认合同模板名称，避免逐条查库
        Map<Long, String> templateNameMap = contractTemplateService.getTemplateList().stream()
                .collect(Collectors.toMap(HrmContractTemplateDO::getId, HrmContractTemplateDO::getName,
                        (a, b) -> a));
        return list.stream().map(item -> buildRespVO(item, templateNameMap)).collect(Collectors.toList());
    }

    private HrmEmploymentTypeRespVO buildRespVO(HrmEmploymentTypeDO employmentType) {
        if (employmentType == null) {
            return null;
        }
        return buildRespVO(employmentType, null);
    }

    private HrmEmploymentTypeRespVO buildRespVO(HrmEmploymentTypeDO employmentType, Map<Long, String> templateNameMap) {
        HrmEmploymentTypeRespVO respVO = BeanUtils.toBean(employmentType, HrmEmploymentTypeRespVO.class);
        if (respVO != null && respVO.getContractTemplateId() != null) {
            if (templateNameMap != null) {
                respVO.setContractTemplateName(templateNameMap.get(respVO.getContractTemplateId()));
            } else {
                HrmContractTemplateDO template = contractTemplateService.getTemplateList().stream()
                        .filter(item -> item.getId().equals(respVO.getContractTemplateId()))
                        .findFirst().orElse(null);
                respVO.setContractTemplateName(template != null ? template.getName() : null);
            }
        }
        return respVO;
    }

}
