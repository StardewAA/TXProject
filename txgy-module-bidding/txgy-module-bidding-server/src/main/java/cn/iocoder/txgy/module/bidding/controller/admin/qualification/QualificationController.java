package cn.iocoder.txgy.module.bidding.controller.admin.qualification;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.EnterpriseQualificationSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationExportVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationImportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertHandleReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStatusChangeReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAlertRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationAssetRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationElementPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPerformanceRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPermitPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPermitRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationReviewPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationReviewRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStaffRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApplyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationApproveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationDetailRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRejectReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRenewReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardOptionsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.qualification.vo.QualificationStandardRespVO;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateAlertService;
import cn.iocoder.txgy.module.bidding.service.qualification.EnterpriseQualificationService;
import cn.iocoder.txgy.module.bidding.service.subject.SubjectProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 企业资质主档/申报流管理接口（建档 + 延续申报 RENEW 全流程）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §十五 表 15-1/15-7（建档/延续申报/办结回写）；
 * 《招投标模块开发规范》§4.4（权限码 bidding:qualification:*，菜单脚本见 sql/history/2026-09-08-…）。
 */
@Tag(name = "管理后台 - 企业资质")
@RestController
@RequestMapping("/bidding/qualification")
@Validated
public class QualificationController {

    @Resource
    private EnterpriseQualificationService enterpriseQualificationService;

    @Resource
    private CertificateAlertService certificateAlertService;

    @Resource
    private SubjectProfileService subjectProfileService;

    @GetMapping("/page")
    @Operation(summary = "资质台账分页（按企业主体；支持序列/类别/等级/状态/临近到期/关键字筛选）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:query')")
    public CommonResult<PageResult<QualificationRespVO>> getQualificationPage(
            @Valid QualificationPageReqVO reqVO) {
        fillOwnSubjectId(reqVO);
        return CommonResult.success(enterpriseQualificationService.getQualificationPage(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "资质详情（含绑定证照摘要与最近一次达标核查）")
    @Parameter(name = "id", description = "资质主档 id", required = true, example = "9001")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:query')")
    public CommonResult<QualificationDetailRespVO> getQualification(@RequestParam("id") Long id) {
        return CommonResult.success(enterpriseQualificationService.getQualification(id));
    }

    @GetMapping("/stats")
    @Operation(summary = "资质看板统计（总数/正常/临期/过期/未关联人员；口径与 page 同源）")
    @Parameter(name = "entId", description = "企业主体 id（可选；不传时由后端按当前租户主体档案推导）", required = false, example = "1")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:query')")
    public CommonResult<QualificationStatsRespVO> getQualificationStats(
            @RequestParam(value = "entId", required = false) Long entId) {
        return CommonResult.success(enterpriseQualificationService.getQualificationStats(
                entId != null ? entId : requireOwnSubjectId()));
    }

    @GetMapping("/apply/page")
    @Operation(summary = "延续申报单分页（按企业主体；支持资质/申报类型/状态筛选，提交日期倒序）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:query')")
    public CommonResult<PageResult<QualificationApplyRespVO>> getApplyPage(
            @Valid QualificationApplyPageReqVO reqVO) {
        fillOwnSubjectId(reqVO);
        return CommonResult.success(enterpriseQualificationService.getApplyPage(reqVO));
    }

    private void fillOwnSubjectId(QualificationPageReqVO reqVO) {
        if (reqVO.getEntId() == null) {
            reqVO.setEntId(requireOwnSubjectId());
        }
    }

    private void fillOwnSubjectId(QualificationApplyPageReqVO reqVO) {
        if (reqVO.getEntId() == null) {
            reqVO.setEntId(requireOwnSubjectId());
        }
    }

    private void fillOwnSubjectId(QualificationAlertPageReqVO reqVO) {
        if (reqVO.getEntId() == null) {
            reqVO.setEntId(requireOwnSubjectId());
        }
    }

    /**
     * 取「本租户唯一企业主体档案」id；未建档时抛可操作错误。
     *
     * <p>为什么单独包一层：本租户还没建主体档案时，推导结果为空。
     * 若直接把它当 entId 往下传，Service 会抛 QUALIFICATION_QUERY_ENT_REQUIRED
     * 「企业主体不能为空（资质按企业主体查询）」——前端会误以为是自己漏传参数，
     * 实际是租户没建档（2026-09-21 实测租户 2019：profile 0 条 → 报该错）。
     * 这里把两种情形分开，未建档时明确指引「先去完善主体档案」。
     */
    private Long requireOwnSubjectId() {
        Long ownSubjectId = subjectProfileService.getOwnSubjectId();
        if (ownSubjectId == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.SUBJECT_PROFILE_NOT_BUILT);
        }
        return ownSubjectId;
    }

    @GetMapping("/standard/page")
    @Operation(summary = "资质标准分页（版本化标准库；支持序列/类别/等级/版本/仅现行筛选）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:standard')")
    public CommonResult<PageResult<QualificationStandardRespVO>> getStandardPage(
            @Valid QualificationStandardPageReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.getStandardPage(reqVO));
    }

    @GetMapping("/standard/options")
    @Operation(summary = "资质维度选项（序列→类别→等级级联；取自现行标准库，不固化字典）")
    @PreAuthorize("@ss.hasAnyPermissions('bidding:qualification:query', 'bidding:qualification:standard')")
    public CommonResult<QualificationStandardOptionsRespVO> getStandardOptions() {
        return CommonResult.success(enterpriseQualificationService.getStandardOptions());
    }

    @GetMapping("/element/staff/page")
    @Operation(summary = "要素台账 · 人员配置分页（出参不含身份证密文/盲索引）")
    @PreAuthorize("@ss.hasAnyPermissions('bidding:qualification:query', 'bidding:qualification:element')")
    public CommonResult<PageResult<QualificationStaffRespVO>> getStaffPage(
            @Valid QualificationElementPageReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.getStaffPage(reqVO));
    }

    @GetMapping("/element/performance/page")
    @Operation(summary = "要素台账 · 工程业绩分页（竣工日期倒序）")
    @PreAuthorize("@ss.hasAnyPermissions('bidding:qualification:query', 'bidding:qualification:element')")
    public CommonResult<PageResult<QualificationPerformanceRespVO>> getPerformancePage(
            @Valid QualificationElementPageReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.getPerformancePage(reqVO));
    }

    @GetMapping("/element/asset/page")
    @Operation(summary = "要素台账 · 资产/设备分页（统计年度倒序）")
    @PreAuthorize("@ss.hasAnyPermissions('bidding:qualification:query', 'bidding:qualification:element')")
    public CommonResult<PageResult<QualificationAssetRespVO>> getAssetPage(
            @Valid QualificationElementPageReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.getAssetPage(reqVO));
    }

    @GetMapping("/review/page")
    @Operation(summary = "达标核查历史分页（含依据标准版本与要素快照，可复算）")
    @PreAuthorize("@ss.hasAnyPermissions('bidding:qualification:query', 'bidding:qualification:review')")
    public CommonResult<PageResult<QualificationReviewRespVO>> getReviewPage(
            @Valid QualificationReviewPageReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.getReviewPage(reqVO));
    }

    @GetMapping("/permit/page")
    @Operation(summary = "安许证台账分页（含风险三件套与宽限登记）")
    @PreAuthorize("@ss.hasAnyPermissions('bidding:qualification:query', 'bidding:qualification:permit')")
    public CommonResult<PageResult<QualificationPermitRespVO>> getPermitPage(
            @Valid QualificationPermitPageReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.getPermitPage(reqVO));
    }

    @GetMapping("/permit/get")
    @Operation(summary = "安许证详情")
    @Parameter(name = "id", description = "安许证 id", required = true, example = "6001")
    @PreAuthorize("@ss.hasAnyPermissions('bidding:qualification:query', 'bidding:qualification:permit')")
    public CommonResult<QualificationPermitRespVO> getPermit(@RequestParam("id") Long id) {
        return CommonResult.success(enterpriseQualificationService.getPermit(id));
    }

    @GetMapping("/alert/page")
    @Operation(summary = "资质到期预警清单分页（QUAL_EXPIRY 三源；按 (refType,refId) 成对匹配企业）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:query')")
    public CommonResult<PageResult<QualificationAlertRespVO>> getAlertPage(
            @Valid QualificationAlertPageReqVO reqVO) {
        fillOwnSubjectId(reqVO);
        return CommonResult.success(enterpriseQualificationService.getAlertPage(reqVO));
    }

    /**
     * 处理到期预警（标记已处理 + 备注）。
     *
     * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§3.21（预警清单"可看、可跳转、**可闭环处理**"）；
     * v3.3 §十五（cert_alert_records.handled/handled_by/handled_at/handle_note）。
     *
     * <p>为什么补这个端点：服务层 {@code CertificateAlertService#markAlertHandled} 早已实现（含版本 CAS），
     * 但**全仓没有任何调用者**——前端能按 {@code handled} 过滤"已处理/未处理"，却没有任何接口能把预警置为已处理，
     * 即清单只增不减，"闭环处理"是空话。属于"能力有实现、没有出口"的断点。
     *
     * <p>为什么处理人取登录态：见 {@link QualificationAlertHandleReqVO}（前端传处理人=允许代人签字）。
     *
     * <p>权限说明（**2026-09-26 拍板，见《电子资质域-待决策与残留事项清单》④**）：
     * 使用独立码 {@code bidding:qualification:alert-handle}，与"补跑扫描"（{@code bidding:qualification:alert-run}）
     * 解耦 —— 岗位职责分离（审计要求"发现"与"核销"分权）：经办人可处理预警（销账），
     * 但不因此获得运维扫描权限；菜单行 60005215 已随 sql/master-credential-sync.sql 登记。
     *
     * <p>业务目标：预警处理完能销账，清单保持可用；处理留痕（谁、何时、为什么）可追溯。
     */
    @PostMapping("/alert/handle")
    @Idempotent
    @Operation(summary = "处理到期预警（标记已处理；版本 CAS 防覆盖他人备注）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:alert-handle')")
    public CommonResult<Boolean> handleAlert(@Valid @RequestBody QualificationAlertHandleReqVO reqVO) {
        certificateAlertService.markAlertHandled(reqVO.getAlertId(), reqVO.getVersion(),
                SecurityFrameworkUtils.getLoginUserId(), reqVO.getHandleNote());
        return CommonResult.success(true);
    }

    @PostMapping("/create")
    @Idempotent
    @Operation(summary = "建档（UK 判重；binded_cert_id 联动台账）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:create')")
    public CommonResult<Long> create(@Valid @RequestBody EnterpriseQualificationSaveReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.createQualification(reqVO));
    }

    @PutMapping("/update")
    @Idempotent
    @Operation(summary = "编辑资质主档（乐观锁 version 必填；UK 判重排除自身；状态流转不走本接口）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:create')")
    public CommonResult<Long> update(@Valid @RequestBody EnterpriseQualificationSaveReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.updateQualification(reqVO));
    }

    @PostMapping("/renew")
    @Idempotent
    @Operation(summary = "延续申报（提交 SUBMITTED；主档置 PENDING_RENEW）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:renew')")
    public CommonResult<Long> renew(@Valid @RequestBody QualificationRenewReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.submitRenewalApply(
                reqVO.getQualificationId(), reqVO.getVersion(), getLoginUserId(),
                reqVO.getDueDate(), reqVO.getRemark()));
    }

    @PostMapping("/approve")
    @Idempotent
    @Operation(summary = "延续审批通过（APPROVED→ISSUED；回写主档 VALID+new_valid_to）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:approve')")
    public CommonResult<Long> approve(@Valid @RequestBody QualificationApproveReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.approveRenewalApply(
                reqVO.getApplyId(), reqVO.getVersion(), reqVO.getResultNo(), reqVO.getNewCertNo(),
                reqVO.getNewValidTo(), getLoginUserId()));
    }

    @PostMapping("/reject")
    @Idempotent
    @Operation(summary = "延续审批退回（REJECTED；主档还原 VALID）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:reject')")
    public CommonResult<Long> reject(@Valid @RequestBody QualificationRejectReqVO reqVO) {
        return CommonResult.success(enterpriseQualificationService.rejectRenewalApply(
                reqVO.getApplyId(), reqVO.getVersion(), getLoginUserId(), reqVO.getRemark()));
    }

    // ===== 五页改造缺接口契约 Q6（2026-09-28）：资质导入/导出 =====
    @GetMapping("/import-template")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:import')")
    @Operation(summary = "下载资质导入模板（含示例行；序列/类别/等级/编号见列头说明）")
    public void importTemplate(HttpServletResponse response) throws IOException {
        QualificationImportExcelVO demo = new QualificationImportExcelVO();
        demo.setQualSerial("SHI_GONG_ZONG");
        demo.setQualCategory("建筑工程施工总承包");
        demo.setQualGrade("贰级");
        demo.setCertNo("DZ0000000001");
        demo.setIssuingAuthority("陕西省住房和城乡建设厅");
        ExcelUtils.write(response, "资质导入模板.xls", "资质", QualificationImportExcelVO.class,
                java.util.Collections.singletonList(demo));
    }

    // ===== 契约《企业资质状态机入口与删除能力-2026-10-01》B1/B2（2026-10-02 落地） =====
    @PostMapping("/status")
    @Operation(summary = "状态机显式入口（B1：LIMITED/REVOKED 只能经本接口产生；reason 必填留痕；乐观锁 CAS）")
    @PreAuthorize("@ss.hasAnyPermissions('bidding:qualification:status', 'bidding:qualification:create')")
    @Idempotent
    public CommonResult<Boolean> changeStatus(@Valid @RequestBody QualificationStatusChangeReqVO reqVO) {
        // 契约 §2：目标态仅 LIMITED | REVOKED（其余态由流程/扫描推导，不接受直填）
        String target = reqVO.getTargetStatus();
        if (!"LIMITED".equals(target) && !"REVOKED".equals(target)) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.QUALIFICATION_STATUS_TRANSITION_INVALID);
        }
        enterpriseQualificationService.changeStatus(reqVO.getId(), target, reqVO.getVersion(),
                reqVO.getReason(), getLoginUserId());
        return CommonResult.success(Boolean.TRUE);
    }

    @PostMapping("/import")
    @Operation(summary = "资质 Excel 导入（Q6：逐行建档，行级回执，单个失败不影响其余）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:import')")
    @Idempotent
    public CommonResult<QualificationImportRespVO> importQualification(
            // 契约 e2e P0-5：同证照导入（见 CertificateController#importCertificates 注释）——
            // required=false + 手动校验，避免非 multipart 请求在参数解析阶段抛 MultipartException 变裸 500。
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.IMPORT_FILE_REQUIRED);
        }
        java.util.List<QualificationImportExcelVO> rows = ExcelUtils.read(file,
                QualificationImportExcelVO.class);
        return CommonResult.success(enterpriseQualificationService.importQualifications(rows,
                getLoginUserId()));
    }

    // 路径兼容（2026-09-28 对账）：契约 Q6 写的是 /export，前端已发布代码调的是 /export-excel。
    // 两个都映射：旧路径不失效，前端也不必改（一条注解解决，不加新方法、不加权限点）。
    @GetMapping({"/export", "/export-excel"})
    @Operation(summary = "资质导出（Q6：范围=当前筛选，与 page 同过滤口径，xlsx；兼容 /export-excel）")
    @PreAuthorize("@ss.hasPermission('bidding:qualification:export')")
    public void exportQualification(@Valid QualificationPageReqVO reqVO, HttpServletResponse response)
            throws IOException {
        java.util.List<QualificationExportVO> list = enterpriseQualificationService.exportQualifications(reqVO);
        ExcelUtils.write(response, "企业资质.xls", "资质", QualificationExportVO.class, list);
    }

    /** 登录用户：平台 SecurityFrameworkUtils 兼容。 */
    private Long getLoginUserId() {
        return cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId();
    }
}
