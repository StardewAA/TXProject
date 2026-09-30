package cn.iocoder.txgy.module.hrm.controller.admin.employee;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.hutool.core.collection.CollUtil;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractRespVO;
import cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractSaveReqVO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.contract.HrmContractSignRecordDO;
import cn.iocoder.txgy.module.hrm.dal.dataobject.employee.employment.HrmEmployeeContractDO;
import cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignStatusEnum;
import cn.iocoder.txgy.module.hrm.service.contract.HrmContractSignRecordService;
import cn.iocoder.txgy.module.hrm.service.employee.employment.HrmEmployeeContractService;
import cn.iocoder.txgy.module.hrm.service.contract.HrmContractRenderService;
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
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - HRM 员工合同")
@RestController
@RequestMapping("/hrm/employee/contract")
@Validated
public class HrmEmployeeContractController {

    @Resource
    private HrmEmployeeContractService contractService;
    @Resource
    private HrmContractRenderService contractRenderService;
    @Resource
    private cn.iocoder.txgy.module.hrm.service.contract.HrmContractSignRecordService contractSignRecordService;
    @Resource
    private cn.iocoder.txgy.module.hrm.service.employee.info.HrmEmployeeService employeeService;

    @GetMapping("/expire-warning")
    @Operation(summary = "获得劳动合同到期预警清单", description = "在职员工 + 合同执行中 + 已开启到期提醒 + 未来 warnDays 天内到期")
    @Parameter(name = "warnDays", description = "提前预警天数", example = "30")
    @PreAuthorize("@ss.hasPermission('hrm:contract:expire:query')")
    public CommonResult<List<cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractExpireRespVO>>
            getExpireWarningList(@RequestParam(value = "warnDays", required = false, defaultValue = "30") Integer warnDays) {
        List<HrmEmployeeContractDO> contracts = contractService.getExpireWarningList(warnDays == null ? 30 : warnDays);
        if (CollUtil.isEmpty(contracts)) {
            return success(List.of());
        }
        Map<Long, cn.iocoder.txgy.module.hrm.dal.dataobject.employee.info.HrmEmployeeDO> employeeMap =
                employeeService.getEmployeeMap(contracts.stream().map(HrmEmployeeContractDO::getEmployeeId)
                        .filter(java.util.Objects::nonNull).collect(Collectors.toSet()));
        // 剩余天数按「自然日」口径计算：合同结束日期存的是当天 00:00，
        // 若直接用当前时刻做差，明天到期的合同会显示成 0 天，前端倒计时看起来不对。
        java.time.LocalDate today = java.time.LocalDate.now();
        List<cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractExpireRespVO> list =
                BeanUtils.toBean(contracts,
                        cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractExpireRespVO.class,
                        vo -> {
                            var employee = vo.getEmployeeId() == null ? null : employeeMap.get(vo.getEmployeeId());
                            if (employee != null) {
                                vo.setEmployeeName(employee.getName());
                                vo.setJobNumber(employee.getJobNumber());
                            }
                            long remainDays = vo.getEndTime() == null ? 0
                                    : java.time.temporal.ChronoUnit.DAYS.between(today, vo.getEndTime().toLocalDate());
                            vo.setRemainDays(remainDays);
                            if (remainDays <= 7) {
                                vo.setUrgency(3);
                                vo.setUrgencyName("紧急");
                            } else if (remainDays <= 30) {
                                vo.setUrgency(2);
                                vo.setUrgencyName("关注");
                            } else {
                                vo.setUrgency(1);
                                vo.setUrgencyName("一般");
                            }
                        });
        return success(list);
    }

    @GetMapping("/list")
    @Operation(summary = "获得员工合同列表")
    @Parameter(name = "employeeId", description = "员工编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:query')")
    public CommonResult<List<HrmEmployeeContractRespVO>> getContractList(
            @RequestParam("employeeId") Long employeeId) {
        List<HrmEmployeeContractDO> contracts = contractService.getContractListByEmployeeId(employeeId);
        List<HrmEmployeeContractRespVO> list = BeanUtils.toBean(contracts, HrmEmployeeContractRespVO.class);
        // 回填签署状态：合同台账要能一眼看出「待签 / 已签 / 已作废」
        if (CollUtil.isNotEmpty(list)) {
            List<Long> contractIds = contracts.stream()
                    .map(HrmEmployeeContractDO::getId).collect(Collectors.toList());
            // 记录按编号倒序返回，取第一条即为最新一条
            Map<Long, HrmContractSignRecordDO> signMap =
                    contractSignRecordService.getSignRecordListByContractIds(contractIds).stream()
                            .collect(Collectors.toMap(HrmContractSignRecordDO::getContractId,
                                    item -> item, (a, b) -> a));
            list.forEach(vo -> {
                HrmContractSignRecordDO sign = signMap.get(vo.getId());
                if (sign != null) {
                    vo.setSignStatus(sign.getStatus());
                    HrmContractSignStatusEnum statusEnum = HrmContractSignStatusEnum.valueOf(sign.getStatus());
                    vo.setSignStatusName(statusEnum == null ? null : statusEnum.getName());
                }
            });
        }
        return success(list);
    }

    @PostMapping("/create")
    @Operation(summary = "创建员工合同")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Long> createContract(@Valid @RequestBody HrmEmployeeContractSaveReqVO reqVO) {
        return success(contractService.createContract(reqVO));
    }

    @PostMapping("/generate")
    @Operation(summary = "生成待签合同（渲染归档 + 自动登记待签签署记录）")
    @Parameter(name = "id", description = "合同编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Long> generatePendingContract(@RequestParam("id") Long id) {
        return success(contractService.generatePendingContract(id));
    }

    @PostMapping("/renew")
    @Operation(summary = "合同续签（按原合同生成新周期合同 + 待签文件 + 待签签署记录）")
    @Parameter(name = "id", description = "原合同编号", required = true, example = "1024")
    @Parameter(name = "termYears", description = "续签年限，缺省沿用原合同期限")
    @Parameter(name = "templateId", description = "续签使用的合同模板编号，缺省沿用原合同模板")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Long> renewContract(@RequestParam("id") Long id,
                                            @RequestParam(value = "termYears", required = false) Integer termYears,
                                            @RequestParam(value = "templateId", required = false) Long templateId) {
        return success(contractService.renewContract(id, termYears, templateId));
    }

    @GetMapping("/file/list")
    @Operation(summary = "获得合同文件台账（系统生成稿 / 签署件）")
    @Parameter(name = "contractId", description = "合同编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:query')")
    public CommonResult<List<cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractFileRespVO>>
            getContractFileList(@RequestParam("contractId") Long contractId) {
        return success(BeanUtils.toBean(contractService.getContractFileList(contractId),
                cn.iocoder.txgy.module.hrm.controller.admin.employee.vo.contract.HrmEmployeeContractFileRespVO.class));
    }

    @GetMapping("/statistics/summary")
    @Operation(summary = "合同看板：待签署 / 已签署 / 30 天内到期")
    @PreAuthorize("@ss.hasPermission('hrm:employee:query')")
    public CommonResult<java.util.Map<String, Object>> getContractStatisticsSummary() {
        java.util.Map<String, Object> summary = new java.util.LinkedHashMap<>();
        // 待签署 / 已签署：按签署记录状态统计
        var pendingReq = new cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordPageReqVO();
        pendingReq.setPageNo(1);
        pendingReq.setPageSize(1);
        pendingReq.setStatus(cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignStatusEnum.PENDING.getStatus());
        var signedReq = new cn.iocoder.txgy.module.hrm.controller.admin.contract.vo.HrmContractSignRecordPageReqVO();
        signedReq.setPageNo(1);
        signedReq.setPageSize(1);
        signedReq.setStatus(cn.iocoder.txgy.module.hrm.enums.contract.HrmContractSignStatusEnum.SIGNED.getStatus());
        summary.put("pendingSignCount", contractSignRecordService.getSignRecordPage(pendingReq).getTotal());
        summary.put("signedCount", contractSignRecordService.getSignRecordPage(signedReq).getTotal());
        // 30 天内到期：复用到期预警清单（在职 + 执行中 + 已开启提醒）
        summary.put("expireIn30DaysCount", (long) contractService.getExpireWarningList(30).size());
        return success(summary);
    }

    @PutMapping("/update")
    @Operation(summary = "更新员工合同")
    @PreAuthorize("@ss.hasPermission('hrm:employee:update')")
    public CommonResult<Boolean> updateContract(@Valid @RequestBody HrmEmployeeContractSaveReqVO reqVO) {
        contractService.updateContract(reqVO);
        return success(true);
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除员工合同")
    @Parameter(name = "id", description = "合同编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:delete')")
    public CommonResult<Boolean> deleteContract(@RequestParam("id") Long id) {
        contractService.deleteContract(id);
        return success(true);
    }

    @GetMapping("/export")
    @Operation(summary = "导出合同文件（docx，按模板自动带入公司信息与员工信息）")
    @Parameter(name = "id", description = "合同编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:query')")
    public void exportContract(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        // 1. 渲染合同文件
        byte[] content = contractRenderService.renderDocx(id);
        String fileName = contractRenderService.buildFileName(id);

        // 2. 输出下载流
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
        response.flushBuffer();
    }

    @GetMapping("/preview")
    @Operation(summary = "在线预览合同文件（docx，浏览器就地打开而非直接下载）")
    @Parameter(name = "id", description = "合同编号", required = true, example = "1024")
    @PreAuthorize("@ss.hasPermission('hrm:employee:query')")
    public void previewContract(@RequestParam("id") Long id, HttpServletResponse response) throws IOException {
        // 与 /export 同一套渲染逻辑，仅把响应头改成 inline，便于 HR 先在线查看再决定是否下载
        byte[] content = contractRenderService.renderDocx(id);
        String fileName = contractRenderService.buildFileName(id);
        response.setContentType("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Disposition", "inline; filename*=UTF-8''"
                + URLEncoder.encode(fileName, StandardCharsets.UTF_8));
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
        response.flushBuffer();
    }

}
