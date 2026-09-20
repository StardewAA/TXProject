package cn.iocoder.txgy.module.hrm.controller.admin.employee;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.certificate.HrmEmployeeCertificateRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.certificate.HrmEmployeeCertificateSaveReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateLedgerPageReqVO;
import cn.iocoder.txgy.module.hrm.controller.admin.certificate.vo.HrmCertificateLedgerRespVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.certificate.HrmCertificateTypeDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.experience.HrmEmployeeCertificateDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateExpireStatusEnum;
import cn.iocoder.txgy.module.hrm.enums.certificate.HrmCertificateVerifyStatusEnum;
import cn.iocoder.txgy.module.hrm.service.certificate.HrmCertificateTypeService;
import cn.iocoder.txgy.module.hrm.service.employee.experience.HrmEmployeeCertificateService;
import cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService;
import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
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
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 员工证书")
@RestController
@RequestMapping("/hrm/employee/certificate")
@Validated
public class HrmEmployeeCertificateController {

    @Resource
    private HrmEmployeeCertificateService certificateService;
    @Resource
    private HrmCertificateTypeService certificateTypeService;
    @Resource
    private HrmEmployeeService employeeService;

    @GetMapping("/list")
    @Operation(summary = "获得员工证书列表")
    @Parameter(name = "employeeId", description = "员工编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:query')")
    public CommonResult<List<HrmEmployeeCertificateRespVO>> getCertificateList(
            @RequestParam("employeeId") Long employeeId) {
        List<HrmEmployeeCertificateDO> certificates = certificateService.getCertificateListByEmployeeId(employeeId);
        Map<Long, HrmCertificateTypeDO> typeMap = certificateTypeService.getCertificateTypeMap();
        return success(BeanUtils.toBean(certificates, HrmEmployeeCertificateRespVO.class, vo -> {
            HrmCertificateTypeDO type = typeMap.get(vo.getTypeId());
            if (type != null) {
                vo.setTypeName(type.getName());
                vo.setSpecialOperation(type.getSpecialOperation());
                vo.setMandatory(type.getMandatory());
                fillExpireStatus(vo, vo.getEndTime(), type.getWarnDays());
            } else {
                fillExpireStatus(vo, vo.getEndTime(), 90);
            }
            HrmCertificateVerifyStatusEnum verify = HrmCertificateVerifyStatusEnum.valueOf(vo.getVerifyStatus());
            vo.setVerifyStatusName(verify != null ? verify.getName() : null);
        }));
    }

    @PostMapping("/create")
    @Operation(summary = "创建员工证书")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Long> createCertificate(
            @Valid @RequestBody HrmEmployeeCertificateSaveReqVO reqVO) {
        return success(certificateService.createCertificate(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新员工证书")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Boolean> updateCertificate(
            @Valid @RequestBody HrmEmployeeCertificateSaveReqVO reqVO) {
        certificateService.updateCertificate(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除员工证书")
    @Parameter(name = "id", description = "证书编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:delete')")
    public CommonResult<Boolean> deleteCertificate(@RequestParam("id") Long id) {
        certificateService.deleteCertificate(id);
        return success(true);
    }

    // ==================== 证照台账（跨员工） ====================

    @GetMapping("/ledger/page")
    @Operation(summary = "获得证照台账分页")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:ledger:query')")
    public CommonResult<PageResult<HrmCertificateLedgerRespVO>> getLedgerPage(
            @Valid HrmCertificateLedgerPageReqVO pageReqVO) {
        PageResult<HrmEmployeeCertificateDO> page = certificateService.getCertificateLedgerPage(pageReqVO);
        return success(new PageResult<>(buildLedgerRespVOList(page.getList()), page.getTotal()));
    }

    @GetMapping("/ledger/expire-warning")
    @Operation(summary = "获得证照到期预警清单（临期 + 已过期）")
    @Parameter(name = "warnDays", description = "临期判定天数，默认 90", example = "90")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:ledger:query')")
    public CommonResult<List<HrmCertificateLedgerRespVO>> getExpireWarningList(
            @RequestParam(value = "warnDays", required = false) Integer warnDays) {
        return success(buildLedgerRespVOList(certificateService.getExpireWarningList(warnDays)));
    }

    @PutMapping("/ledger/verify")
    @Operation(summary = "证照核验（已核验必须已上传证件扫描件）")
    @Parameter(name = "id", description = "证照编号", required = true, example = "1")
    @Parameter(name = "verifyStatus", description = "核验状态：1 已核验，2 核验不通过", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:ledger:verify')")
    public CommonResult<Boolean> verifyCertificate(@RequestParam("id") Long id,
                                                   @RequestParam("verifyStatus") Integer verifyStatus) {
        certificateService.verifyCertificate(id, verifyStatus);
        return success(true);
    }

    @GetMapping("/ledger/export-excel")
    @Operation(summary = "导出证照台账")
    @PreAuthorize("@ss.hasPermission('hrm:certificate:ledger:export')")
    public void exportLedgerExcel(@Valid HrmCertificateLedgerPageReqVO exportReqVO,
                                  HttpServletResponse response) throws IOException {
        exportReqVO.setPageSize(PageParam.PAGE_SIZE_NONE);
        List<HrmCertificateLedgerRespVO> list =
                buildLedgerRespVOList(certificateService.getCertificateLedgerList(exportReqVO));
        ExcelUtils.write(response, "证照台账.xls", "数据", HrmCertificateLedgerRespVO.class, list);
    }

    /**
     * 组装台账响应：回填员工、证照类型、到期状态
     */
    private List<HrmCertificateLedgerRespVO> buildLedgerRespVOList(List<HrmEmployeeCertificateDO> list) {
        if (cn.hutool.core.collection.CollUtil.isEmpty(list)) {
            return Collections.emptyList();
        }
        Map<Long, HrmCertificateTypeDO> typeMap = certificateTypeService.getCertificateTypeMap();
        Map<Long, HrmEmployeeDO> employeeMap = employeeService.getEmployeeMap(
                list.stream().map(HrmEmployeeCertificateDO::getEmployeeId).collect(Collectors.toSet()));
        return BeanUtils.toBean(list, HrmCertificateLedgerRespVO.class, vo -> {
            HrmCertificateTypeDO type = typeMap.get(vo.getTypeId());
            if (type != null) {
                vo.setTypeName(type.getName());
                vo.setSpecialOperation(type.getSpecialOperation());
                vo.setMandatory(type.getMandatory());
            }
            HrmEmployeeDO employee = employeeMap.get(vo.getEmployeeId());
            if (employee != null) {
                vo.setEmployeeName(employee.getName());
                vo.setJobNumber(employee.getJobNumber());
                vo.setMobile(employee.getMobile());
            }
            fillExpire(vo, type);
            HrmCertificateVerifyStatusEnum verify = HrmCertificateVerifyStatusEnum.valueOf(vo.getVerifyStatus());
            vo.setVerifyStatusName(verify != null ? verify.getName() : null);
        });
    }

    /**
     * 计算到期状态：已过期 / 临期（按类型预警天数）/ 有效
     */
    private void fillExpire(HrmCertificateLedgerRespVO vo, HrmCertificateTypeDO type) {
        LocalDateTime endTime = vo.getEndTime();
        if (endTime == null) {
            return;
        }
        int warnDays = type != null && type.getWarnDays() != null ? type.getWarnDays() : 90;
        long remainDays = Duration.between(LocalDateTime.now(), endTime).toDays();
        vo.setRemainDays(remainDays);
        if (remainDays < 0) {
            vo.setExpireStatus(HrmCertificateExpireStatusEnum.EXPIRED.getStatus());
            vo.setExpireStatusName(HrmCertificateExpireStatusEnum.EXPIRED.getName());
        } else if (remainDays <= warnDays) {
            vo.setExpireStatus(HrmCertificateExpireStatusEnum.EXPIRING.getStatus());
            vo.setExpireStatusName(HrmCertificateExpireStatusEnum.EXPIRING.getName());
        } else {
            vo.setExpireStatus(HrmCertificateExpireStatusEnum.VALID.getStatus());
            vo.setExpireStatusName(HrmCertificateExpireStatusEnum.VALID.getName());
        }
    }

    /**
     * 员工证照列表用的到期状态回填（字段在员工证照 VO 上）
     */
    private void fillExpireStatus(HrmEmployeeCertificateRespVO vo, LocalDateTime endTime, Integer warnDays) {
        if (endTime == null) {
            return;
        }
        int days = warnDays == null ? 90 : warnDays;
        long remainDays = Duration.between(LocalDateTime.now(), endTime).toDays();
        vo.setRemainDays(remainDays);
        if (remainDays < 0) {
            vo.setExpireStatus(HrmCertificateExpireStatusEnum.EXPIRED.getStatus());
            vo.setExpireStatusName(HrmCertificateExpireStatusEnum.EXPIRED.getName());
        } else if (remainDays <= days) {
            vo.setExpireStatus(HrmCertificateExpireStatusEnum.EXPIRING.getStatus());
            vo.setExpireStatusName(HrmCertificateExpireStatusEnum.EXPIRING.getName());
        } else {
            vo.setExpireStatus(HrmCertificateExpireStatusEnum.VALID.getStatus());
            vo.setExpireStatusName(HrmCertificateExpireStatusEnum.VALID.getName());
        }
    }

}
