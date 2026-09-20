package cn.iocoder.txgy.module.hrm.controller.admin.certificate;

import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateTypePageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateTypeRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateTypeSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.mysql.employee.experience.HrmEmployeeCertificateMapper;
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

import java.util.Collections;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * HRM 证照类型 Controller
 *
 * <p>证照类型是上岗卡控的依据：标记了「特种作业 / 必持 / 预警天数」的类型，
 * 后续项目要求里一旦引用，缺证或过期的员工就不允许进场。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 证照类型")
@RestController
@RequestMapping("/hrm/certificate-type")
@Validated
public class HrmCertificateTypeController {

    @Resource
    private HrmCertificateTypeService certificateTypeService;
    @Resource
    private HrmEmployeeCertificateMapper employeeCertificateMapper;

    @PostMapping("/create")
    @Operation(summary = "创建证照类型")
    @PreAuthorize("@ss.hasPermission('hrm:certificate-type:create')")
    public CommonResult<Long> createCertificateType(@Valid @RequestBody HrmCertificateTypeSaveReqVO reqVO) {
        return success(certificateTypeService.createCertificateType(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新证照类型")
    @PreAuthorize("@ss.hasPermission('hrm:certificate-type:update')")
    public CommonResult<Boolean> updateCertificateType(@Valid @RequestBody HrmCertificateTypeSaveReqVO reqVO) {
        certificateTypeService.updateCertificateType(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除证照类型")
    @Parameter(name = "id", description = "编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:certificate-type:delete')")
    public CommonResult<Boolean> deleteCertificateType(@RequestParam("id") Long id) {
        certificateTypeService.deleteCertificateType(id);
        return success(true);
    }

    @GetMapping("/get")
    @Operation(summary = "获得证照类型")
    @PreAuthorize("@ss.hasPermission('hrm:certificate-type:query')")
    public CommonResult<HrmCertificateTypeRespVO> getCertificateType(@RequestParam("id") Long id) {
        List<HrmCertificateTypeRespVO> list = buildRespVOList(
                Collections.singletonList(certificateTypeService.validateCertificateTypeExists(id)));
        return success(CollUtil.isEmpty(list) ? null : list.get(0));
    }

    @GetMapping("/list")
    @Operation(summary = "获得证照类型列表")
    @PreAuthorize("@ss.hasPermission('hrm:certificate-type:query')")
    public CommonResult<List<HrmCertificateTypeRespVO>> getCertificateTypeList() {
        return success(buildRespVOList(certificateTypeService.getCertificateTypeList()));
    }

    @GetMapping("/simple-list")
    @Operation(summary = "获得证照类型精简列表（下拉用）")
    public CommonResult<List<HrmCertificateTypeRespVO>> getCertificateTypeSimpleList() {
        return success(buildRespVOList(certificateTypeService.getCertificateTypeList()));
    }

    @GetMapping("/page")
    @Operation(summary = "获得证照类型分页")
    @PreAuthorize("@ss.hasPermission('hrm:certificate-type:query')")
    public CommonResult<PageResult<HrmCertificateTypeRespVO>> getCertificateTypePage(
            @Valid HrmCertificateTypePageReqVO pageReqVO) {
        PageResult<HrmCertificateTypeDO> page = certificateTypeService.getCertificateTypePage(pageReqVO);
        return success(new PageResult<>(buildRespVOList(page.getList()), page.getTotal()));
    }

    private List<HrmCertificateTypeRespVO> buildRespVOList(List<HrmCertificateTypeDO> list) {
        if (CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        return BeanUtils.toBean(list, HrmCertificateTypeRespVO.class, vo ->
                vo.setCertificateCount(employeeCertificateMapper.selectCountByTypeId(vo.getId())));
    }

}
