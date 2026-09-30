package cn.iocoder.txgy.module.bidding.controller.admin.personnel;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelAlertPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelAlertRespVO;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.service.personnel.PersonnelAlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

/**
 * 员工证件到期预警运行接口（手动触发扫描 + 查法定预警下限）。
 *
 * <p>规范出处：用户业务口径"在人员资质证书、安全生产证、身份证有效期到期前自动提醒"；
 * 《电子证件全流程管理_数据库设计.md》v3.3 §九（扫描幂等，重跑安全）；06 文档 §二-5（法定预警下限，供前端提示）。
 * <p>为什么这样写：扫描是幂等动作（幂等键含触发日期，可安全重复触发），因此手动端点与定时任务共用同一
 * 服务方法；法定下限做成只读查询，便于前端在配置预警天数时即时提示下限，避免把窗口调小到错过法定办理期。
 * <p>业务目标：定时任务缺失或补跑时可直接触发，同日重跑不会重复推送。
 */
@Tag(name = "管理后台 - 员工证件到期预警运行")
@RestController
@RequestMapping("/bidding/personnel-alert-run")
@Validated
public class PersonnelAlertRunController {

    @Resource
    private PersonnelAlertService personnelAlertService;

    @GetMapping("/alert/page")
    @Operation(summary = "员工预警清单分页（决策③：员工证书/复审/身份证三类；含 version 供处理时 CAS）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    public CommonResult<PageResult<PersonnelAlertRespVO>> getAlertPage(@Valid PersonnelAlertPageReqVO reqVO) {
        return CommonResult.success(personnelAlertService.getAlertPage(reqVO));
    }

    @PostMapping("/scan")
    @Operation(summary = "手动触发员工证书/复审/身份证到期扫描（同日幂等）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:alert-run')")
    @Idempotent
    public CommonResult<Integer> scan() {
        return CommonResult.success(personnelAlertService.scanPersonnelExpiries(LocalDate.now()));
    }

    @GetMapping("/legal-floor-days")
    @Operation(summary = "获得法定预警下限（配置预警天数时不得低于该值）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    public CommonResult<Map<String, Integer>> legalFloorDays() {
        return CommonResult.success(personnelAlertService.legalFloorDays());
    }
}
