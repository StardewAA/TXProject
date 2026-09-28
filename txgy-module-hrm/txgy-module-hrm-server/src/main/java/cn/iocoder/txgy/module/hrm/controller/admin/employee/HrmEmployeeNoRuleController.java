package cn.iocoder.txgy.module.hrm.controller.admin.employee;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeNoRuleRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.employee.HrmEmployeeNoRuleSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeNoRuleDO;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeNoService;
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

/**
 * HRM 工号规则 Controller
 *
 * <p>工号 = 租户缩写 + 可选分隔符 + 可选年份 + 顺序号（左补零），租户内独立计数、永不复用。
 * 以前只能改数据库，这里给实施/运维一个页面：改缩写、位数、是否按年重置、是否允许人工填工号。</p>
 *
 * @author OPENLAB BS
 */
@Tag(name = "管理后台 - HRM 工号规则")
@RestController
@RequestMapping("/hrm/employee/no-rule")
@Validated
public class HrmEmployeeNoRuleController {

    @Resource
    private HrmEmployeeNoService employeeNoService;

    @GetMapping("/list")
    @Operation(summary = "获得工号规则列表")
    @PreAuthorize("@ss.hasPermission('hrm:employee-no-rule:query')")
    public CommonResult<List<HrmEmployeeNoRuleRespVO>> getRuleList() {
        List<HrmEmployeeNoRuleDO> list = employeeNoService.getRuleList();
        return success(BeanUtils.toBean(list, HrmEmployeeNoRuleRespVO.class,
                rule -> rule.setSampleJobNumber(buildSample(rule))));
    }

    @GetMapping("/get")
    @Operation(summary = "获得工号规则")
    @Parameter(name = "id", description = "规则编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:employee-no-rule:query')")
    public CommonResult<HrmEmployeeNoRuleRespVO> getRule(@RequestParam("id") Long id) {
        HrmEmployeeNoRuleDO rule = employeeNoService.getRule(id);
        HrmEmployeeNoRuleRespVO respVO = BeanUtils.toBean(rule, HrmEmployeeNoRuleRespVO.class);
        if (respVO != null) {
            respVO.setSampleJobNumber(buildSample(respVO));
        }
        return success(respVO);
    }

    @PostMapping("/save")
    @Operation(summary = "保存工号规则（无编号新增、有编号修改）", description = "同一租户只保留一条启用规则，启用本条会自动停用其它规则")
    @PreAuthorize("@ss.hasPermission('hrm:employee-no-rule:update')")
    public CommonResult<Long> saveRule(@Valid @RequestBody HrmEmployeeNoRuleSaveReqVO reqVO) {
        return success(employeeNoService.saveRule(reqVO));
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除工号规则", description = "启用中的规则不允许删除")
    @Parameter(name = "id", description = "规则编号", required = true, example = "1")
    @PreAuthorize("@ss.hasPermission('hrm:employee-no-rule:update')")
    public CommonResult<Boolean> deleteRule(@RequestParam("id") Long id) {
        employeeNoService.deleteRule(id);
        return success(true);
    }

    @GetMapping("/preview")
    @Operation(summary = "预览下一个工号", description = "按当前启用规则预览下一个工号（不占用序号）")
    @PreAuthorize("@ss.hasPermission('hrm:employee-no-rule:query')")
    public CommonResult<String> previewNextJobNumber() {
        return success(employeeNoService.previewNextJobNumber());
    }

    @PostMapping("/preview-sample")
    @Operation(summary = "按给定配置预览工号样例", description = "用于配置界面即时预览，不落库")
    @PreAuthorize("@ss.hasPermission('hrm:employee-no-rule:query')")
    public CommonResult<String> previewSampleJobNumber(@RequestBody HrmEmployeeNoRuleSaveReqVO reqVO) {
        return success(employeeNoService.previewSampleJobNumber(reqVO));
    }

    private String buildSample(HrmEmployeeNoRuleRespVO rule) {
        return employeeNoService.previewSampleJobNumber(
                new HrmEmployeeNoRuleSaveReqVO()
                        .setTenantAbbr(rule.getTenantAbbr())
                        .setSerialLength(rule.getSerialLength() == null ? 6 : rule.getSerialLength())
                        .setPrefixSeparator(rule.getPrefixSeparator())
                        .setResetPeriod(rule.getResetPeriod()));
    }

}
