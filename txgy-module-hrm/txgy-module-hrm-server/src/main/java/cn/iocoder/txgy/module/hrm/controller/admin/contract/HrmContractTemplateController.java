package cn.iocoder.txgy.module.hrm.controller.admin.contract;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractTemplateRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractTemplateSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.company.HrmCompanyProfileDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractTemplateDO;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmTemplateCategoryEnum;
import cn.iocoder.txgy.module.hrm.service.company.HrmCompanyProfileService;
import cn.iocoder.txgy.module.hrm.service.contract.HrmContractTemplateService;
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
import java.util.Objects;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 合同模板")
@RestController
@RequestMapping("/hrm/contract/template")
@Validated
public class HrmContractTemplateController {

    @Resource
    private HrmContractTemplateService contractTemplateService;
    @Resource
    private HrmCompanyProfileService companyProfileService;

    @GetMapping("/list")
    @Operation(summary = "获得模板列表", description = "可按分类筛选，用于模板管理页按分类分组展示")
    @Parameter(name = "category", description = "模板分类：1 劳动合同，2 个人电子档案，3 培训协议；不传返回全部",
            example = "1")
    @PreAuthorize("@ss.hasAnyPermissions('hrm:contract:template:query', 'hrm:employee:query')")
    public CommonResult<List<HrmContractTemplateRespVO>> getTemplateList(
            @RequestParam(value = "category", required = false) Integer category) {
        List<HrmContractTemplateDO> list = category == null
                ? contractTemplateService.getTemplateList()
                : contractTemplateService.getTemplateListByCategory(category);
        return success(buildRespList(list));
    }

    @GetMapping("/get")
    @Operation(summary = "获得合同模板")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasAnyPermissions('hrm:contract:template:query', 'hrm:employee:query')")
    public CommonResult<HrmContractTemplateRespVO> getTemplate(@RequestParam("id") Long id) {
        List<HrmContractTemplateRespVO> list = buildRespList(
                List.of(contractTemplateService.validateTemplateExists(id)));
        return success(list.get(0));
    }

    @PostMapping("/save")
    @Operation(summary = "保存合同模板（新增或更新）")
    @PreAuthorize("@ss.hasAnyPermissions('hrm:contract:template:create', 'hrm:employee:update')")
    public CommonResult<Long> saveTemplate(@Valid @RequestBody HrmContractTemplateSaveReqVO reqVO) {
        return success(contractTemplateService.saveTemplate(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新合同模板（模板文件变更时自动升版本）")
    @PreAuthorize("@ss.hasAnyPermissions('hrm:contract:template:update', 'hrm:employee:update')")
    public CommonResult<Boolean> updateTemplate(@Valid @RequestBody HrmContractTemplateSaveReqVO reqVO) {
        contractTemplateService.saveTemplate(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除合同模板")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasAnyPermissions('hrm:contract:template:delete', 'hrm:employee:update')")
    public CommonResult<Boolean> deleteTemplate(@RequestParam("id") Long id) {
        contractTemplateService.deleteTemplate(id);
        return success(true);
    }

    @GetMapping("/download-url")
    @Operation(summary = "获得合同模板文件下载地址")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasAnyPermissions('hrm:contract:template:download', 'hrm:employee:query')")
    public CommonResult<String> getTemplateDownloadUrl(@RequestParam("id") Long id) {
        return success(contractTemplateService.validateTemplateExists(id).getFileUrl());
    }

    /**
     * 补齐适用用工主体名称，便于模板列表直接展示
     */
    private List<HrmContractTemplateRespVO> buildRespList(List<HrmContractTemplateDO> templates) {
        List<HrmContractTemplateRespVO> list = BeanUtils.toBean(templates, HrmContractTemplateRespVO.class);
        Map<Long, String> employerNameMap = companyProfileService.getProfileList().stream()
                .collect(Collectors.toMap(HrmCompanyProfileDO::getId, HrmCompanyProfileDO::getName, (a, b) -> a));
        list.forEach(item -> {
            if (item.getEmployerId() != null) {
                item.setEmployerName(employerNameMap.get(item.getEmployerId()));
            }
            if (Objects.isNull(item.getEmployerName())) {
                item.setEmployerName("不限");
            }
            HrmTemplateCategoryEnum category = HrmTemplateCategoryEnum.valueOf(item.getCategory());
            item.setCategoryName(category == null ? null : category.getName());
        });
        return list;
    }

}
