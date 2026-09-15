package cn.iocoder.txgy.module.bidding.controller.admin.qualification;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.AssetSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.PerformanceSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.StaffSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationAssetDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationPerformanceDO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationStaffConfigDO;
import cn.iocoder.txgy.module.bidding.service.qualification.QualificationElementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 资质四要素录入接口（15-3 人员 / 15-4 业绩 / 15-5 资产装备）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-3/15-4/15-5；开发规范 §4.4（bidding:qualification:element）。
 */
@Tag(name = "管理后台 - 资质要素台账")
@RestController
@RequestMapping("/bidding/qualification/element")
@Validated
public class QualificationElementController {

    @Resource
    private QualificationElementService elementService;

    @PostMapping("/staff")
    @Operation(summary = "录入资质人员")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:element')")
    @Idempotent
    public CommonResult<Long> staff(@Valid @RequestBody StaffSaveReqVO reqVO) {
        QualificationStaffConfigDO staff = new QualificationStaffConfigDO();
        staff.setEntId(reqVO.getEntId());
        staff.setQualId(reqVO.getQualId());
        staff.setStaffRole(reqVO.getStaffRole());
        staff.setPersonName(reqVO.getPersonName());
        staff.setMajor(reqVO.getMajor());
        staff.setRegNo(reqVO.getRegNo());
        staff.setTitleLevel(reqVO.getTitleLevel());
        staff.setIsFullTime(reqVO.getIsFullTime());
        staff.setRegValidTo(reqVO.getRegValidTo());
        staff.setBindedCertId(reqVO.getBindedCertId());
        return CommonResult.success(elementService.saveStaff(staff));
    }

    @PostMapping("/performance")
    @Operation(summary = "录入工程业绩")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:element')")
    @Idempotent
    public CommonResult<Long> performance(@Valid @RequestBody PerformanceSaveReqVO reqVO) {
        QualificationPerformanceDO perf = new QualificationPerformanceDO();
        perf.setEntId(reqVO.getEntId());
        perf.setQualId(reqVO.getQualId());
        perf.setProjectName(reqVO.getProjectName());
        perf.setProjectType(reqVO.getProjectType());
        perf.setRole(reqVO.getRole());
        perf.setContractAmount(reqVO.getContractAmount());
        perf.setCompletionDate(reqVO.getCompletionDate());
        perf.setQualityResult(reqVO.getQualityResult());
        perf.setOwnerName(reqVO.getOwnerName());
        perf.setIsPlatformVerified(reqVO.getIsPlatformVerified());
        perf.setEvidenceFileIds(reqVO.getEvidenceFileIds());
        return CommonResult.success(elementService.savePerformance(perf));
    }

    @PostMapping("/asset")
    @Operation(summary = "录入资产/技术装备")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:element')")
    @Idempotent
    public CommonResult<Long> asset(@Valid @RequestBody AssetSaveReqVO reqVO) {
        QualificationAssetDO asset = new QualificationAssetDO();
        asset.setEntId(reqVO.getEntId());
        asset.setAssetType(reqVO.getAssetType());
        asset.setName(reqVO.getName());
        asset.setSpec(reqVO.getSpec());
        asset.setQuantity(reqVO.getQuantity());
        asset.setValueAmount(reqVO.getValueAmount());
        asset.setStatYear(reqVO.getStatYear());
        asset.setEvidenceFileIds(reqVO.getEvidenceFileIds());
        return CommonResult.success(elementService.saveAsset(asset));
    }
}
