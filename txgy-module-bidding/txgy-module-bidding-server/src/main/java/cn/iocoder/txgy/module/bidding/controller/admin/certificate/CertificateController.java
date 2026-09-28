package cn.iocoder.txgy.module.bidding.controller.admin.certificate;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.excel.core.util.ExcelUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileCorrectReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileRegisterReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileVersionRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertFileVoidReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportExcelVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportFailureVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateImportRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertChangeLogPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertChangeLogRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertHashChainVerifyRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificatePageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateSaveReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateStatsReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertificateStatusChangeReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import cn.iocoder.txgy.module.bidding.enums.CertificateChangeSourceEnum;
import cn.iocoder.txgy.module.bidding.service.certificate.CertFileVersionService;
import cn.iocoder.txgy.module.bidding.service.certificate.CertificateService;

import java.io.IOException;
import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - 证照台账（bidding certificate）。
 *
 * <p>接口编号对齐（见 docs/_bak/05-接口设计文档.md 需求映射）：
 * 建档/台账查询服务于企业资质证照使用场景（AI 自查输入、投标资格审查取数）——
 * 管理端本租户分页/详情/建档/更新/状态流转；状态流转含吊销（REVOKE 日志，等保 2.0 三级哈希链，v3.3 §13）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §二/§十三＋§18；《招投标模块开发规范》§4.2
 * （Controller 只做参数校验/权限/文档）§4.4（写接口 @Idempotent）§4.5（乐观锁由服务层 CAS）。
 * 外部电子证照核验（GB/T 36906 对接）后续里程碑在 /external/ 下另立 Controller，本控制器只维护台账。
 */
@Tag(name = "管理后台 - bidding 证照台账")
@RestController
@RequestMapping("/bidding/certificate")
@Validated
public class CertificateController {

    @Resource
    private CertificateService certificateService;
    @Resource
    private CertFileVersionService certFileVersionService;
    @Resource
    private cn.iocoder.txgy.module.bidding.enums.certificate.CertDictHelper certDictHelper;

    @GetMapping("/page")
    @Operation(summary = "证照台账分页（管理端）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<PageResult<CertificateRespVO>> page(@Valid CertificatePageReqVO reqVO) {
        return success(certificateService.getCertificatePage(reqVO));
    }

    @GetMapping("/stats")
    @Operation(summary = "证照看板统计（按状态/验真/大类全量计数，支持状态多选；契约 Q3）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<CertificateStatsRespVO> stats(@Valid CertificateStatsReqVO reqVO) {
        // 规范出处：五页改造契约 Q3（2026-09-28）——GET /bidding/certificate/stats。
        // 为什么这样写：看板是"读"动作，权限复用查询码（不新增权限点/菜单），条件与列表页完全同口径；
        // 出参按状态全量计数（含暂扣/吊销/注销），把前端 5 次 pageSize=1 并发计数收敛为 1 次。
        return success(certificateService.getCertificateStats(reqVO));
    }

    @GetMapping("/get")
    @Operation(summary = "证照详情（管理端；含乐观锁版本供更新回传）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<CertificateRespVO> get(@RequestParam("id") Long id) {
        return success(certificateService.getCertificate(id));
    }

    @PostMapping("/create")
    @Operation(summary = "建档（证书编号租户内唯一；幂等）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:create')")
    @Idempotent
    public CommonResult<Long> create(@Validated(CertificateSaveReqVO.Create.class)
                                     @RequestBody CertificateSaveReqVO reqVO) {
        return success(certificateService.createCertificate(reqVO));
    }

    @PutMapping("/update")
    @Operation(summary = "更新证照字段（乐观锁 CAS；变更差异留痕）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:update')")
    @Idempotent
    public CommonResult<Boolean> update(@Validated(CertificateSaveReqVO.Update.class)
                                        @RequestBody CertificateSaveReqVO reqVO) {
        certificateService.updateCertificate(reqVO);
        return success(true);
    }

    /**
     * 变更留痕分页（契约：变更留痕读出口，2026-09-21 补）。
     *
     * <p>为什么单列一个读口：cert_change_logs 一直有写入（建档/更新/状态流转均留痕，含哈希链），
     * 但**从未有查询接口**——前端无法回答"这条证照改过什么"，而这是《参数与字典说明全表》
     * 里已标注的"⚠️ 有表无查询接口（待补）"。
     *
     * <p>权限复用 query（读动作，不新增权限点/菜单，与 stats 同处置）。
     */
    @GetMapping("/change-log/page")
    @Operation(summary = "证照变更留痕分页（字段级；含哈希链凭证）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<PageResult<CertChangeLogRespVO>> changeLogPage(@Valid CertChangeLogPageReqVO reqVO) {
        return success(certificateService.getChangeLogPage(reqVO));
    }

    /**
     * 哈希链回验：逐行重算 record_hash 并核对 prev_hash，断链即返回首处不一致的行。
     *
     * <p>权限复用 query（读动作，与 change-log/page、stats 同处置，不新增权限点/菜单）。
     */
    @GetMapping("/change-log/verify")
    @Operation(summary = "证照变更留痕哈希链回验（逐行重算；断链返回断链行 id 与期望/实际值）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<CertHashChainVerifyRespVO> verifyChangeLogChain(@RequestParam("certId") Long certId) {
        return success(certificateService.verifyChangeLogChain(certId));
    }

    @PostMapping("/file/register")
    @Operation(summary = "登记证照文件版本（FileApi 上传后只登记元数据；同证同哈希判重）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:file')")
    @Idempotent
    public CommonResult<Long> registerFile(@Valid @RequestBody CertFileRegisterReqVO reqVO) {
        return success(certFileVersionService.registerFile(reqVO));
    }

    @GetMapping("/file/list")
    @Operation(summary = "证照文件版本列表（只追加版本历史）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:query')")
    public CommonResult<List<CertFileVersionRespVO>> fileList(@RequestParam("certId") Long certId) {
        // 规范出处：契约 §3.2（13 个字段）；原先直接返回 DO，会把 tenantId/deleted/creator 等内部列一并外发。
        // 为什么这样写：出口统一收敛为 VO，契约与实现之间才有稳定边界（改表不再等于改接口）。
        // 传入字典解析器：fileCategoryName 需查 bidding_dict_data 才能得到中文名
        // （框架 DictFrameworkUtils 读的是 system_dict_data，与本模块字典表不是同一张表）
        return success(CertFileVersionRespVO.fromList(certFileVersionService.listFiles(certId), certDictHelper));
    }

    @PostMapping("/file/void")
    @Operation(summary = "作废证照文件版本（F2：软删留痕；当前版本被作废则该类别失去当前版本，需重新登记）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:file')")
    @Idempotent
    public CommonResult<Boolean> fileVoid(@Valid @RequestBody CertFileVoidReqVO reqVO) {
        certFileVersionService.voidVersion(reqVO.getFileVersionId(), reqVO.getReason());
        return success(true);
    }

    @PostMapping("/file/correct")
    @Operation(summary = "更正证照文件版本元数据（F2：备注 + 加注件 copy_* 四要素；至少传一个可更正字段）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:file')")
    @Idempotent
    public CommonResult<Boolean> fileCorrect(@Valid @RequestBody CertFileCorrectReqVO reqVO) {
        certFileVersionService.correctMetadata(reqVO);
        return success(true);
    }

    @PostMapping("/change-status")
    @Operation(summary = "台账状态流转（吊销/注销记 REVOKE，其余 STATUS_CHANGE；乐观锁 CAS）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:update')")
    @Idempotent
    public CommonResult<Boolean> changeStatus(@Valid @RequestBody CertificateStatusChangeReqVO reqVO) {
        // 操作来源/IP 当前取默认（MANUAL/空），对接登录上下文后由安全框架注入（后续里程碑）
        certificateService.changeStatus(reqVO.getCertificateId(), reqVO.getTargetStatus(), reqVO.getVersion(),
                reqVO.getReason(), CertificateChangeSourceEnum.MANUAL, null);
        return success(true);
    }
    // ===== 五页改造缺接口契约 Q1（2026-09-28）：证照 Excel 导入（模板下载 + 行级回执） =====
    @GetMapping("/import-template")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:import')")
    @Operation(summary = "下载证照导入模板（含示例行，证照大类 code 见列头说明）")
    public void importTemplate(HttpServletResponse response) throws IOException {
        // 模板自带一行示例，降低填列成本；仅示例，不入库。
        CertificateImportExcelVO demo = new CertificateImportExcelVO();
        demo.setCertName("示例：营业执照");
        demo.setCertNo("91XXXXXXXXXXXXXX");
        demo.setCertType("ADMIN_LICENSE");
        demo.setHolderName("陕西××建设集团有限公司");
        ExcelUtils.write(response, "证照导入模板.xls", "证照", CertificateImportExcelVO.class,
                java.util.Collections.singletonList(demo));
    }

    @PostMapping("/import")
    @Operation(summary = "证照 Excel 导入（Q1：逐行建档，行级回执，单个失败不影响其余）")
    @PreAuthorize("@ss.hasPermission('bidding:certificate:import')")
    @Idempotent
    public CommonResult<CertificateImportRespVO> importCertificates(
            // 契约 e2e P0-4：required=false + 手动校验，而非 required=true。
            // 为什么：非 multipart 请求时，Spring 在**参数解析阶段**就抛 MultipartException，
            // 进不了方法体 → 框架兜底成裸 500（调用方只看到"系统异常"，无法得知是缺文件）。
            // 改为可选后由方法体给出业务码，前端可提示"请选择文件"。
            @RequestParam(value = "file", required = false) MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.IMPORT_FILE_REQUIRED);
        }
        java.util.List<CertificateImportExcelVO> rows = ExcelUtils.read(file,
                CertificateImportExcelVO.class);
        return success(certificateService.importCertificates(rows,
                cn.iocoder.txgy.framework.security.core.util.SecurityFrameworkUtils.getLoginUserId()));
    }
}
