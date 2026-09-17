package cn.iocoder.txgy.module.bidding.controller.admin.personnel;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelLaborContractRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelLaborContractSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelSocialSecurityRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelSocialSecuritySaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelTrainingRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelTrainingSaveReqVO;
import cn.iocoder.txgy.module.bidding.service.personnel.PersonnelRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 员工履历登记接口（培训记录 / 社保缴纳 / 劳动合同）。
 *
 * <p>规范出处：用户业务口径"记录员工基本信息、资质证书、培训记录等"与"核验社保缴纳单位、劳动合同主体
 * 是否与申报企业一致"；《电子证件全流程管理_数据库设计.md》v3.3 表 15-3（社保/合同=挂靠核查证据）；
 * 《劳动合同法》第十条、《社会保险法》第五十八条；《招投标模块开发规范》§4.2/§4.4/§4.5/§4.7。
 * <p>为什么这样写：社保与劳动合同是"人证合一"的第三方证据，登记时即做业务键唯一（同人同月同单位 /
 * 同人同合同号），确保月度核对不会出现两条互相矛盾的记录；登记接口带 @Idempotent 防重复提交。
 * <p>业务目标：三类证据集中登记、按月可核对，为"人证合一、防挂证"提供数据基础。
 */
@Tag(name = "管理后台 - 员工履历（培训/社保/劳动合同）")
@RestController
@RequestMapping("/bidding/personnel-record")
@Validated
public class PersonnelRecordController {

    @Resource
    private PersonnelRecordService personnelRecordService;

    @PostMapping("/training/create")
    @Operation(summary = "登记培训记录（继续教育学时可累计）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:record:create')")
    @Idempotent
    public CommonResult<Long> createTraining(@Valid @RequestBody PersonnelTrainingSaveReqVO reqVO) {
        return CommonResult.success(personnelRecordService.createTraining(reqVO));
    }

    @GetMapping("/training/list")
    @Operation(summary = "获得某员工培训履历")
    @Parameter(name = "personId", description = "员工档案主键", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:personnel:record:query')")
    public CommonResult<List<PersonnelTrainingRespVO>> listTraining(@RequestParam("personId") Long personId) {
        return CommonResult.success(personnelRecordService.listTraining(personId).stream().map(r -> {
            PersonnelTrainingRespVO vo = new PersonnelTrainingRespVO();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).toList());
    }

    @PostMapping("/social-security/create")
    @Operation(summary = "登记社保缴纳记录（同人同月同单位唯一）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:record:create')")
    @Idempotent
    public CommonResult<Long> createSocialSecurity(@Valid @RequestBody PersonnelSocialSecuritySaveReqVO reqVO) {
        return CommonResult.success(personnelRecordService.createSocialSecurity(reqVO));
    }

    @GetMapping("/social-security/list")
    @Operation(summary = "获得某员工社保缴纳履历")
    @Parameter(name = "personId", description = "员工档案主键", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:personnel:record:query')")
    public CommonResult<List<PersonnelSocialSecurityRespVO>> listSocialSecurity(
            @RequestParam("personId") Long personId) {
        return CommonResult.success(personnelRecordService.listSocialSecurity(personId).stream().map(r -> {
            PersonnelSocialSecurityRespVO vo = new PersonnelSocialSecurityRespVO();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).toList());
    }

    @PostMapping("/labor-contract/create")
    @Operation(summary = "登记劳动合同（同人同编号唯一）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:record:create')")
    @Idempotent
    public CommonResult<Long> createLaborContract(@Valid @RequestBody PersonnelLaborContractSaveReqVO reqVO) {
        return CommonResult.success(personnelRecordService.createLaborContract(reqVO));
    }

    @GetMapping("/labor-contract/list")
    @Operation(summary = "获得某员工劳动合同列表")
    @Parameter(name = "personId", description = "员工档案主键", required = true)
    @PreAuthorize("@ss.hasPermission('bidding:personnel:record:query')")
    public CommonResult<List<PersonnelLaborContractRespVO>> listLaborContract(
            @RequestParam("personId") Long personId) {
        return CommonResult.success(personnelRecordService.listLaborContract(personId).stream().map(r -> {
            PersonnelLaborContractRespVO vo = new PersonnelLaborContractRespVO();
            BeanUtils.copyProperties(r, vo);
            return vo;
        }).toList());
    }
}
