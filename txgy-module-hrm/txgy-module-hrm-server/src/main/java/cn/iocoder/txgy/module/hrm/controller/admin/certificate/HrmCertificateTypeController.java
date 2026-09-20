package cn.iocoder.txgy.module.hrm.controller.admin.certificate;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypeRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.type.HrmCertificateTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.service.certificate.HrmCertificateTypeService;
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

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 证件类型")
@RestController
@RequestMapping("/hrm/certificate-type")
@Validated
public class HrmCertificateTypeController {

    @Resource
    private HrmCertificateTypeService certificateTypeService;

    @PostMapping("/create")
    @Operation(summary = "创建证件类型")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:create')")
    public CommonResult<Long> createCertificateType(@Valid @RequestBody HrmCertificateTypeSaveReqVO reqVO) {
        return success(certificateTypeService.createCertificateType(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新证件类型")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:update')")
    public CommonResult<Boolean> updateCertificateType(@Valid @RequestBody HrmCertificateTypeSaveReqVO reqVO) {
        certificateTypeService.updateCertificateType(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除证件类型")
    @Parameter(name = "id", description = "证件类型编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:delete')")
    public CommonResult<Boolean> deleteCertificateType(@RequestParam("id") Long id) {
        certificateTypeService.deleteCertificateType(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得证件类型")
    @Parameter(name = "id", description = "证件类型编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:query')")
    public CommonResult<HrmCertificateTypeRespVO> getCertificateType(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(certificateTypeService.getCertificateType(id),
                HrmCertificateTypeRespVO.class));
    }

    @GetMapping("/list")
    @Operation(summary = "获得证件类型列表", description = "用于证件台账、续证弹窗的下拉选项")
    @Parameter(name = "status", description = "状态，参见 common_status", example = "0")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:query')")
    public CommonResult<List<HrmCertificateTypeRespVO>> getCertificateTypeList(
            @RequestParam(value = "status", required = false) Integer status) {
        List<HrmCertificateTypeDO> list = certificateTypeService.getCertificateTypeList(status);
        return success(BeanUtils.toBean(list, HrmCertificateTypeRespVO.class));
    }

    @GetMapping("/page")
    @Operation(summary = "获得证件类型分页")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:query')")
    public CommonResult<PageResult<HrmCertificateTypeRespVO>> getCertificateTypePage(
            @Validated HrmCertificateTypePageReqVO pageReqVO) {
        PageResult<HrmCertificateTypeDO> pageResult = certificateTypeService.getCertificateTypePage(pageReqVO);
        return success(BeanUtils.toBean(pageResult, HrmCertificateTypeRespVO.class));
    }

}
