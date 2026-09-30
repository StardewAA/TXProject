package cn.iocoder.txgy.module.bidding.controller.admin.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（Controller 只做校验/权限/包装）、§4.5（写接口 @Idempotent）
// [业务目标] 标书智能体·知识库管理：材料台账、切片查看、召回验证、精准度测试
// [为什么] 这四件事构成专员的调优闭环（管资料 → 验召回 → 定指标），故同址暴露，便于前端一页串起来

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentEvalVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentMaterialVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentRetrieveVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentSegmentVO;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentEvalCaseService;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentMaterialService;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentRetrieveService;
import cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentSegmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 标书智能体知识库")
@RestController
@RequestMapping("/bidding/bid-agent")
@Validated
public class BidAgentKnowledgeController {

    @Resource
    private BidAgentMaterialService materialService;
    @Resource
    private BidAgentRetrieveService retrieveService;
    @Resource
    private BidAgentEvalCaseService evalCaseService;
    @Resource
    private BidAgentSegmentService segmentService;

    // ================= 材料台账 =================

    @GetMapping("/material/page")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:query')")
    @Operation(summary = "材料分页（按角色/标段/入库状态过滤）")
    public CommonResult<PageResult<BidAgentMaterialVO.RespVO>> pageMaterial(@Valid BidAgentMaterialVO.PageReqVO reqVO) {
        return success(materialService.page(reqVO));
    }

    @GetMapping("/material/get")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:query')")
    @Operation(summary = "材料详情")
    public CommonResult<BidAgentMaterialVO.RespVO> getMaterial(@RequestParam("id") Long id) {
        return success(materialService.get(id));
    }

    @PostMapping("/material/save")
    // 保留 isAuthenticated 而非细粒度权限码：该接口同时服务前台「标书工坊」上传（同 task/generate 的门户口径），
    // 普通门户用户无后台权限码，收紧会导致前台上传失败；台账读写由材料自身的租户隔离保障
    @PreAuthorize("isAuthenticated()")
    @Idempotent
    @Operation(summary = "材料登记并入库（fileId + 角色 + 标段；TENDER 必须绑标段）")
    public CommonResult<Long> saveMaterial(@Valid @RequestBody BidAgentMaterialVO.SaveReqVO reqVO) {
        return success(materialService.register(reqVO));
    }

    @PostMapping("/material/delete")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:delete')")
    @Idempotent
    @Operation(summary = "材料删除（连同切片台账一并清理）")
    public CommonResult<Boolean> deleteMaterial(@RequestParam("id") Long id) {
        materialService.delete(id);
        return success(true);
    }

    @PostMapping("/material/reindex")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:update')")
    @Idempotent
    @Operation(summary = "重建索引（入库失败或资料更新后的修复入口）")
    public CommonResult<Boolean> reindexMaterial(@RequestParam("id") Long id) {
        materialService.reindex(id);
        return success(true);
    }

    @GetMapping("/segment/page")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:query')")
    @Operation(summary = "切片分页（读本地切片台账，与向量库写入同源）")
    public CommonResult<Map<String, Object>> pageSegment(@RequestParam("materialId") Long materialId,
                                                         @RequestParam(value = "pageNo", required = false) @Max(100000) Integer pageNo,
                                                         @RequestParam(value = "pageSize", required = false) @Max(200) Integer pageSize) {
        return success(materialService.pageSegment(materialId, pageNo, pageSize));
    }

    // ================= 切片管理（人工治理：编辑/拆分/合并/批量删除） =================

    @PostMapping("/segment/edit")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:update')")
    @Idempotent
    @Operation(summary = "切片编辑（更新正文并置人工修改标记，同步向量库）")
    public CommonResult<Boolean> editSegment(@Valid @RequestBody BidAgentSegmentVO.EditReqVO reqVO) {
        segmentService.edit(reqVO.getId(), reqVO.getContent());
        return success(true);
    }

    @PostMapping("/segment/split")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:update')")
    @Idempotent
    @Operation(summary = "切片拆分（按段落一分为二）")
    public CommonResult<Boolean> splitSegment(@RequestParam("id") Long id) {
        segmentService.split(id);
        return success(true);
    }

    @PostMapping("/segment/merge")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:update')")
    @Idempotent
    @Operation(summary = "切片合并（若干相邻切片拼接为一条）")
    public CommonResult<Boolean> mergeSegment(@Valid @RequestBody BidAgentSegmentVO.IdsReqVO reqVO) {
        segmentService.merge(reqVO.getIds());
        return success(true);
    }

    @PostMapping("/segment/delete")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:delete')")
    @Idempotent
    @Operation(summary = "切片批量删除（连同向量库对应点一并清理）")
    public CommonResult<Boolean> deleteSegment(@Valid @RequestBody BidAgentSegmentVO.IdsReqVO reqVO) {
        segmentService.delete(reqVO.getIds());
        return success(true);
    }

    // ================= 召回验证 =================

    @PostMapping("/retrieve")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:retrieve')")
    @Idempotent
    @Operation(summary = "召回验证（命中片段 + 命中方式 + 分数 + 溯源；hit=false 时前台必须转人工）")
    public CommonResult<BidAgentRetrieveVO.RespVO> retrieve(@Valid @RequestBody BidAgentRetrieveVO.ReqVO reqVO) {
        return success(retrieveService.retrieve(reqVO));
    }

    // ================= 精准度测试（golden set） =================

    @GetMapping("/eval/case/page")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:query')")
    @Operation(summary = "测试用例分页")
    public CommonResult<PageResult<BidAgentEvalVO.CaseRespVO>> pageEvalCase(@Valid BidAgentEvalVO.CasePageReqVO reqVO) {
        return success(evalCaseService.page(reqVO));
    }

    @PostMapping("/eval/case/save")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:create')")
    @Idempotent
    @Operation(summary = "测试用例新增/修改（标注数据由指标负责人提供）")
    public CommonResult<Long> saveEvalCase(@Valid @RequestBody BidAgentEvalVO.CaseSaveReqVO reqVO) {
        return success(evalCaseService.save(reqVO));
    }

    @PostMapping("/eval/case/delete")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:delete')")
    @Idempotent
    @Operation(summary = "测试用例删除")
    public CommonResult<Boolean> deleteEvalCase(@RequestParam("id") Long id) {
        evalCaseService.delete(id);
        return success(true);
    }

    @PostMapping("/eval/run")
    @PreAuthorize("@ss.hasPermission('bidding:bid-agent:eval')")
    @Idempotent
    @Operation(summary = "跑精准度测试，输出用例命中率与未命中清单")
    public CommonResult<BidAgentEvalVO.RunRespVO> runEval(@RequestBody BidAgentEvalVO.RunReqVO reqVO) {
        return success(retrieveService.runEval(reqVO));
    }
}
