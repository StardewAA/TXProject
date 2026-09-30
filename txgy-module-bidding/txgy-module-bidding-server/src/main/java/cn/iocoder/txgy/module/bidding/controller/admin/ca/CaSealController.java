package cn.iocoder.txgy.module.bidding.controller.admin.ca;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealCheckRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.CaSealStatsRespVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.SealCallbackReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.ca.vo.SealDocumentRespVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ca.CaSealDO;
import cn.iocoder.txgy.module.bidding.service.ca.CaSealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * 管理后台 - bidding CA 文件电子签章。
 *
 * <p>能力：对上传文件做 CA 签章（SM2 真实签名 + PDF 可见红章 / 非 PDF 分离式签名信息文件），
 * 并把签名/证书/指纹<b>直接落库 CA 签章台账（bidding_ca_seal，每次签章一条）</b>；
 * 台账支持分页/详情/验签（真实 SM2 验签回写 verifyStatus）。
 * 测试配置（bidding.ca.sdk.enabled=false，默认）本地统一测试账号真实签名；真实配置（=true）接入官方 H5/共享组件（待注册）。
 *
 * <p><b>权限口径裁定（2026-09-21，回应契约《投标文件CA签章递交衔接》A6）</b>：
 * 本控制器采取「<b>签章通用、递交受控</b>」——签章动作（document/batch/callback/check）走
 * {@code isAuthenticated()}，即<b>登录即可签</b>；台账治理类动作（page/get/stats/verify）走
 * {@code bidding:ca:query|verify} 业务权限码。
 * <p>理由：签章是<b>通用能力</b>（附件、营业执照、资质证据、审查报告等任意"需要签名的文件位置"都可挂，
 * 见 30 号文档 §七的六处入口），若绑死某个业务权限码，会让"能上传却签不了章"；
 * 而<b>递交投标文件</b>才是受控业务动作（须企业资格门 + {@code bidding:process-track:upload}），
 * 因此两者口径<b>本就应当不同</b>，契约 A6 提的"不一致"实为设计意图。
 * <p>越权风险由"按登录人/租户隔离"承担：签章产物落 {@code bidding_ca_seal}(tenant_id)，
 * 台账读取须权限码；签章本身不改业务状态、不产生对外效力。
 */
@Tag(name = "管理后台 - bidding CA 文件签章")
@RestController
@RequestMapping("/bidding/ca/seal")
@Validated
public class CaSealController {

    @Resource
    private CaSealService caSealService;

    @PostMapping("/document")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "文件签章（生产签章 document 与已签章文件，直接落库台账）",
            description = "上传待签章文件（multipart/form-data，字段 file）；返回 SM2 签名值+证书+SM3 指纹+已签章文件；businessCode 为投标流程跟踪 id 时同步落库。")
    public CommonResult<SealDocumentRespVO> sealDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "businessCode", required = false) String businessCode,
            @RequestParam(value = "callbackUrl", required = false) String callbackUrl) {
        return success(caSealService.sealDocument(file, businessCode, callbackUrl));
    }

    @PostMapping("/batch")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "批量文件签章（每页盖章，统一落库台账）",
            description = "一次上传多份文件（multipart/form-data，多个 files 字段；如投标文件多册），每份逐页盖红色电子公章、"
                    + "真实 SM2 签名并各自落库 CA 签章台账；批内整体一个事务（全成或全滚）。businessCode 为投标流程跟踪 id 时同步落库。")
    public CommonResult<List<SealDocumentRespVO>> batch(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "businessCode", required = false) String businessCode,
            @RequestParam(value = "callbackUrl", required = false) String callbackUrl) {
        return success(caSealService.sealDocumentBatch(files, businessCode, callbackUrl));
    }

    @PostMapping("/callback")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "文件签章完成回调（本地/官方 H5 统一收口）",
            description = "携 SM2 签名值/证书/指纹确认签章完成；本地测试会在入库前验签（不匹配拒绝）；businessCode 为投标流程跟踪 id 时回写留痕；按 documentId 回写台账验签状态 PASS。")
    public CommonResult<Boolean> callback(@Valid @RequestBody SealCallbackReqVO reqVO) {
        return success(caSealService.saveCallback(reqVO));
    }

    @GetMapping("/page")
    @PreAuthorize("@ss.hasPermission('bidding:ca:query')")
    @Operation(summary = "CA 签章台账分页（直接入库记录；按文件名/签章单位/验签状态过滤）")
    public CommonResult<PageResult<CaSealRespVO>> page(@Valid CaSealPageReqVO reqVO) {
        PageResult<CaSealDO> page = caSealService.page(reqVO);
        return success(BeanUtils.toBean(page, CaSealRespVO.class));
    }

    @GetMapping("/check")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "上传后检测文件是否已 CA 认证（未认证提示前端）",
            description = "按原文件 fileId（必传）匹配签章台账，sha256 兜底（重传场景）；返回 caAuthed + 最近签章记录摘要。"
                    + "前端：上传回调后调本接口，caAuthed=false 时提示「该文件未 CA 认证」并询问是否使用 CA 认证功能。")
    public CommonResult<CaSealCheckRespVO> check(@RequestParam("fileId") Long fileId,
                                                  @RequestParam(value = "sha256", required = false) String sha256) {
        return success(caSealService.checkSealAuth(fileId, sha256));
    }

    @GetMapping("/stats")
    @PreAuthorize("@ss.hasPermission('bidding:ca:query')")
    @Operation(summary = "CA 签章统计（演示仪表卡：总数/PASS/FAIL/未验签）",
            description = "统计直接入库的签章台账：total 总数、pass/fail/unverified 验签状态计数；演示页顶部四卡。")
    public CommonResult<CaSealStatsRespVO> stats() {
        return success(caSealService.stats());
    }

    @PostMapping("/demo-seed")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "生成演示签章数据（真实 SM2 签名 + PDF 红章，直接落库台账）",
            description = "演示用途：一键生成 count（默认5，上限20）份带真实签章与红色公章的演示 PDF 并直接落库，"
                    + "验签必然 PASS；演示页打开即有华丽数据，点开后可见红章 PDF 并可验签。")
    public CommonResult<List<CaSealRespVO>> demoSeed(@RequestParam(value = "count", defaultValue = "5") Integer count) {
        return success(caSealService.demoSeed(count));
    }

    @PostMapping("/demo-tamper")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "生成演示「被篡改」签章数据（验签必 FAIL，展示检出能力）",
            description = "演示用途：生成 count（默认2，上限20）份「文件被篡改」样例——台账指纹换成被替换内容的指纹，"
                    + "原签名失效，验签必 FAIL（红标）；配合验签展示「篡改检出」，演示后可用 demo-clear 清理。")
    public CommonResult<List<CaSealRespVO>> demoTamper(@RequestParam(value = "count", defaultValue = "2") Integer count) {
        return success(caSealService.demoTamper(count));
    }

    @PostMapping("/demo-clear")
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "清空演示签章数据（含篡改样例），还原台账",
            description = "演示用途：删除 documentName 以「演示签章文件-」「演示篡改文件-」开头的记录，返回删除条数。")
    public CommonResult<Long> demoClear() {
        return success(caSealService.demoClear());
    }

    @GetMapping("/get")
    @PreAuthorize("@ss.hasPermission('bidding:ca:query')")
    @Operation(summary = "CA 签章记录详情（签章证据链）")
    public CommonResult<CaSealRespVO> get(@RequestParam("id") Long id) {
        return success(BeanUtils.toBean(caSealService.get(id), CaSealRespVO.class));
    }

    @PostMapping("/verify")
    @PreAuthorize("@ss.hasPermission('bidding:ca:verify')")
    @Idempotent
    @Operation(summary = "验签（真实 SM2 验签：对台账记录的 SM3 指纹验签名）",
            description = "按 id 取台账记录，用统一测试账号公钥对「文件 SM3 指纹 + SM2 签名值」真实验签，通过/不通过回写 verifyStatus（PASS/FAIL）与验签时间。")
    public CommonResult<CaSealRespVO> verify(@RequestParam("id") Long id) {
        return success(caSealService.verifySeal(id));
    }
}
