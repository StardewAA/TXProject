package cn.iocoder.txgy.module.bidding.controller.admin.personnel;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertImportRespVO;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertPageByPersonReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertPersonRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertAlertNotifyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertBatchRenewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertVerifyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertBatchRenewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.personnel.vo.PersonnelCertificateStatusChangeReqVO;
import cn.iocoder.txgy.module.bidding.service.personnel.PersonnelCertificateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService;
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
    @Resource
    private SubjectProfileService subjectProfileService;

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

    @GetMapping("/page-by-person")
    @Operation(summary = "按人维度分页查询员工证书（同一人的证书在同一页内完整；五页改造契约 Q9）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    public CommonResult<PageResult<PersonnelCertPersonRespVO>> pageByPerson(
            @Valid PersonnelCertPageByPersonReqVO reqVO) {
        return CommonResult.success(personnelCertificateService.pageByPerson(reqVO));
    }

    @GetMapping("/stats")
    @Operation(summary = "员工证书看板统计（持证员工/总数/临期/过期/未验真；五页改造契约 Q10）")
    @Parameter(name = "entId", description = "归属企业（可空=全部）")
    @Parameter(name = "deptName", description = "部门名称（可空=全部，模糊）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    public CommonResult<PersonnelCertStatsRespVO> getCertStats(
            @RequestParam(value = "entId", required = false) Long entId,
            @RequestParam(value = "deptName", required = false) String deptName) {
        return CommonResult.success(personnelCertificateService.getCertStats(entId, deptName));
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
    @Parameter(name = "entId", description = "归属企业（**可空**：不传=本租户主体档案）")
    @Parameter(name = "certType", description = "证书类型 code", required = true, example = "SAFETY_ASSESS_CERT")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:query')")
    public CommonResult<Map<String, Integer>> countBySubType(@RequestParam(value = "entId", required = false) Long entId,
                                                            @RequestParam("certType") String certType) {
        // 规范出处：《后端变更通知-给前端》§12："我是谁"类 id 由后端推导。
        // 缺省取本租户主体档案 id，与证照建档 org_id / 完备性自查用同一个入口，口径一致。
        Long subjectId = entId != null ? entId : subjectProfileService.getOwnSubjectId();
        return CommonResult.success(personnelCertificateService.countUsableBySubType(subjectId, certType));
    }

    // ===== 契约 e2e P2（2026-09-21）：员工证书 Excel 导入（模板下载 + 行级回执） =====

    @GetMapping("/import-template")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:import')")
    @Operation(summary = "下载员工证书导入模板（含示例行；证书类型/学历层次填 code）",
            description = "与证照/资质导入同构：模板自带一行示例（仅示例，不入库），降低填列成本。")
    public void certImportTemplate(HttpServletResponse response) throws IOException {
        PersonnelCertImportExcelVO demo = new PersonnelCertImportExcelVO();
        demo.setPersonId(1024L);
        demo.setCertType("REG_PRACTICE_CERT");
        demo.setCertName("示例：一级建造师注册证书");
        demo.setCertNo("610000000001");
        demo.setCertLevel("一级");
        demo.setMajor("建筑工程");
        demo.setIssuingAuthority("陕西省住房和城乡建设厅");
        demo.setIssueDate(java.time.LocalDate.of(2026, 1, 1));
        demo.setValidFrom(java.time.LocalDate.of(2026, 1, 1));
        demo.setValidTo(java.time.LocalDate.of(2029, 1, 1));
        demo.setRemark("示例行，导入前请删除或替换");
        ExcelUtils.write(response, "员工证书导入模板.xls", "员工证书", PersonnelCertImportExcelVO.class,
                java.util.Collections.singletonList(demo));
    }

    @PostMapping("/import")
    @Operation(summary = "员工证书 Excel 导入（P2：逐行登记，行级回执，单个失败不影响其余）",
            description = "列口径见模板；personId 为员工档案主键，entId 由后端按本租户主体档案推导。")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:import')")
    @Idempotent
    public CommonResult<PersonnelCertImportRespVO> importPersonnelCerts(
            // 同证照/资质导入：required=false + 手动校验，避免非 multipart 请求在参数解析阶段
            // 抛 MultipartException 变成裸 500（契约 e2e P0-4 同类问题）。
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.IMPORT_FILE_REQUIRED);
        }
        List<PersonnelCertImportExcelVO> rows = ExcelUtils.read(file, PersonnelCertImportExcelVO.class);
        return CommonResult.success(personnelCertificateService.importCertificates(rows,
                subjectProfileService.getOwnSubjectId()));
    }

    // ===== 五页改造缺接口契约 Q11/Q12/Q13（2026-09-28）：批量续期 / 批量到期提醒 / 证书级验真 =====
    @PostMapping("/batch-renew")
    @Operation(summary = "批量续期（Q11：items[] 或 certIds[]+newValidTo 两种模式；返回回执对象，单个失败不影响其余）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:renew')")
    @Idempotent
    public CommonResult<PersonnelCertBatchRenewRespVO> batchRenew(
            @Valid @RequestBody PersonnelCertBatchRenewReqVO reqVO) {
        return CommonResult.success(personnelCertificateService.batchRenew(reqVO,
                SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/alert/notify")
    @Operation(summary = "批量到期提醒（Q12：personIds[]/certIds[] 二选一，逐证派发通知，同日同证幂等）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:alert-run')")
    @Idempotent
    public CommonResult<Integer> notifyExpiring(@Valid @RequestBody PersonnelCertAlertNotifyReqVO reqVO) {
        return CommonResult.success(personnelCertificateService.notifyExpiring(reqVO,
                SecurityFrameworkUtils.getLoginUserId()));
    }

    @PostMapping("/verify")
    @Operation(summary = "证书级验真（Q13：复用证照域验真渠道，须已绑定证照台账；certId 兼容 JSON body 与查询参数）")
    @PreAuthorize("@ss.hasPermission('bidding:personnel:cert:update')")
    @Idempotent
    public CommonResult<Boolean> verifyPersonnelCert(
            @RequestBody(required = false) PersonnelCertVerifyReqVO reqVO,
            @RequestParam(value = "certId", required = false) Long certId,
            @RequestParam(value = "verifyChannel", required = false) String verifyChannel) {
        // 规范出处：五页改造契约 Q13 + 2026-09-28 前后端字段对账。
        // 为什么双形态：前端已发布代码（personnel/index.ts 的 verifyPersonnelCert）发 JSON body {certId}，
        // 后端原先只认 @RequestParam → 前端点"验真"必 400。这里 body 优先、query 兜底，两边都不改也能通。
        Long finalCertId = reqVO != null && reqVO.getCertId() != null ? reqVO.getCertId() : certId;
        String finalChannel = reqVO != null && reqVO.getVerifyChannel() != null
                ? reqVO.getVerifyChannel() : verifyChannel;
        if (finalCertId == null) {
            // 两个形态都没给主键：按参数错误返回（不是"证书不存在"，语义不同）
            throw cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST);
        }
        personnelCertificateService.verifyPersonnelCert(finalCertId, finalChannel,
                SecurityFrameworkUtils.getLoginUserId());
        return CommonResult.success(true);
    }
}
