package cn.iocoder.txgy.module.bidding.controller.admin.personnel;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificateStatusChangeReqVO;
import cn.iocoder.txgy.module.bidding.service.personnel.PersonnelCertificateService;
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

/**
 * 员工证书管理接口（登记 / 检索 / 状态流转 / 到期清单 / 细分统计）。
 *
 * <p>规范出处：用户业务口径"员工证书信息的集中管理、分类检索和动态更新 + 到期自动提醒"；
 * 《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（证书要素）；
 * 06 文档 §二-1/§二-3（安许证 A/B/C 三类人员动态核查按细分计数、特种设备作业 G1/G2/G3）；
 * 《招投标模块开发规范》§4.2/§4.4/§4.5/§4.7。
 * <p>为什么这样写：证书状态直接决定"能不能投标"，因此状态流转单独成接口、单独权限点；到期清单与预警
 * 扫描共用同一"可用状态 + valid_to 窗口"口径，避免页面显示与告警口径打架。
 * <p>业务目标：证书一处登记，页面可筛、到期有提醒、安许证人员配备能自查。
 */
@Tag(name = "管理后台 - 员工证书")
@RestController
@RequestMapping("/bidding/personnel-certificate")
@Validated
public class PersonnelCertificateController {

    @Resource
    private PersonnelCertificateService personnelCertificateService;

    @PostMapping("/create")
    @Operation(summary = "登记员工证书（同人同类型同编号唯一）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:create')")
    @Idempotent
    public CommonResult<Long> createCertificate(@Valid @RequestBody PersonnelCertificateSaveReqVO reqVO) {
        return CommonResult.success(personnelCertificateService.createCertificate(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新员工证书（乐观锁）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:update')")
    @Idempotent
    public CommonResult<Boolean> updateCertificate(@Valid @RequestBody PersonnelCertificateSaveReqVO reqVO) {
        personnelCertificateService.updateCertificate(reqVO);
        return CommonResult.success(true);
    }

    @PutMapping("/change-status")
    @Operation(summary = "员工证书状态流转（吊销/注销/恢复；临近到期由预警自动置位）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:update')")
    @Idempotent
    public CommonResult<Boolean> changeStatus(@Valid @RequestBody PersonnelCertificateStatusChangeReqVO reqVO) {
        personnelCertificateService.changeStatus(reqVO.getId(), reqVO.getTargetStatus(), reqVO.getVersion(),
                reqVO.getReason());
        return CommonResult.success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除员工证书（软删，保留历史）")
    @Parameter(name = "id", description = "证书主键", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:delete')")
    @Idempotent
    public CommonResult<Boolean> deleteCertificate(@RequestParam("id") Long id) {
        personnelCertificateService.deleteCertificate(id);
        return CommonResult.success(true);
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询员工证书（支持到期窗口筛选）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    public CommonResult<PageResult<PersonnelCertRespVO>> pageCertificate(
            @Valid PersonnelCertificatePageReqVO reqVO) {
        return CommonResult.success(personnelCertificateService.pageCertificate(reqVO));
    }

    @GetMapping("/list-by-person")
    @Operation(summary = "获得某员工的全部在册证书")
    @Parameter(name = "personId", description = "员工档案主键", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    public CommonResult<List<PersonnelCertRespVO>> listByPerson(@RequestParam("personId") Long personId) {
        return CommonResult.success(personnelCertificateService.listByPerson(personId));
    }

    @GetMapping("/list-expiring")
    @Operation(summary = "获得证书到期清单（today ~ today+days）")
    @Parameter(name = "entId", description = "归属企业（可空=全部）")
    @Parameter(name = "days", description = "到期窗口天数，默认 30")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    public CommonResult<List<PersonnelCertRespVO>> listExpiring(@RequestParam(value = "entId", required = false) Long entId,
                                                               @RequestParam(value = "days", defaultValue = "30") int days) {
        return CommonResult.success(personnelCertificateService.listExpiring(entId, days));
    }

    @GetMapping("/count-by-sub-type")
    @Operation(summary = "按证书细分统计可用持证人数（安许证 A/B/C 三类人员配备自查）")
    @Parameter(name = "entId", description = "归属企业", required = true)
    @Parameter(name = "certType", description = "证书类型 code", required = true, example = "SAFETY_ASSESS_CERT")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    public CommonResult<Map<String, Integer>> countBySubType(@RequestParam("entId") Long entId,
                                                            @RequestParam("certType") String certType) {
        return CommonResult.success(personnelCertificateService.countUsableBySubType(entId, certType));
    }
}
