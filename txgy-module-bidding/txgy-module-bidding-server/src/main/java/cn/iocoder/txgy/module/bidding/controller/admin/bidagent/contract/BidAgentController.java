package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.contract;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.idempotent.core.annotation.Idempotent;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.*;
import cn.iocoder.txgy.module.bidding.service.bidagent.draft.BidDraftService;
import cn.iocoder.txgy.module.bidding.service.bidagent.fact.BidFactService;
import cn.iocoder.txgy.module.bidding.service.bidagent.project.BidProjectService;
import cn.iocoder.txgy.module.bidding.service.bidagent.retrieval.BidRetrievalService;
import cn.iocoder.txgy.module.bidding.service.bidagent.shadow.BidTaskShadowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;
import static cn.iocoder.txgy.framework.common.exception.enums.GlobalErrorCodeConstants.BAD_REQUEST;

/**
 * 投标 Agent 契约接口（架构文档 §3.1：Agent → Java）。
 *
 * 说明：这些接口由 Node 侧 bid-agent-service 调用，URL 经框架自动加上 /admin-api 前缀，
 * 即 /admin-api/bid/agent/**。租户编号由请求体/查询参数显式传递（Agent 不做租户鉴权，由 Java 侧兜底）。
 */
@Tag(name = "管理后台 - 投标 Agent 契约")
@RestController
@RequestMapping("/bidding/bid-agent/contract")
@Validated
// 契约端由 Node 侧机对机调用，不携带 tenant-id 请求头；租户由请求参数/请求体显式传入，
// 服务层再用 TenantUtils.execute(tenantId, ...) 开启租户过滤，故此处需跳过租户校验过滤器。
@TenantIgnore
public class BidAgentController {

    @Resource
    private BidRetrievalService retrievalService;
    @Resource
    private BidProjectService projectService;
    @Resource
    private BidFactService factService;
    @Resource
    private BidDraftService draftService;
    @Resource
    private BidTaskShadowService shadowService;
    @Resource
    private cn.iocoder.txgy.module.bidding.service.bidagent.pricing.BidPricingService pricingService;

    @PostMapping("/retrieve")
    @Operation(summary = "投标检索（向量+BM25+必检+阈值+溯源）")
    @PermitAll // 机对机契约端：免登（鉴权由 BidAgentKeyFilter 校验 X-Agent-Key）
    @Idempotent // 写方法幂等（默认解析器＝方法名+参数 MD5，1s 内同参重复请求拦截）
    public CommonResult<BidRetrieveRespDTO> retrieve(@Valid @RequestBody BidRetrieveReqDTO reqVO) {
        return success(retrievalService.retrieve(reqVO));
    }

    @GetMapping("/context/{projectId}")
    @Operation(summary = "获取项目上下文（事实卡+招标解析+全局事实）")
    @PermitAll
    public CommonResult<BidContextRespDTO> getContext(@PathVariable("projectId") Long projectId,
                                                      @RequestParam(value = "tenantId", required = false) Long tenantId) {
        return success(projectService.getContext(projectId, tenantId));
    }

    @GetMapping("/facts")
    @Operation(summary = "查询企业事实（COPY_FACT 通道数据源）")
    @PermitAll
    public CommonResult<List<BidFactRespDTO>> getFacts(@RequestParam(value = "tenantId", required = false) Long tenantId,
                                                       @RequestParam(value = "types", required = false) String types) {
        List<String> typeList = StrUtil.isBlank(types) ? List.of() : List.of(types.split(","));
        return success(factService.getFacts(tenantId, typeList));
    }

    @GetMapping("/pricing")
    @Operation(summary = "查询报价数据（COPY_PRICING 通道数据源）")
    @PermitAll
    public CommonResult<BidPricingRespDTO> getPricing(@RequestParam("projectId") Long projectId,
                                                      @RequestParam(value = "tenantId", required = false) Long tenantId) {
        return success(pricingService.getPricing(projectId, tenantId));
    }

    @PostMapping("/draft/chapter")
    @Operation(summary = "章节草稿回写（按 projectId+nodeId+version 幂等）")
    @PermitAll
    @Idempotent
    public CommonResult<Long> saveChapter(@Valid @RequestBody BidChapterDraftReqDTO reqVO) {
        return success(draftService.saveChapter(reqVO));
    }

    @PostMapping("/task-status")
    @Operation(summary = "Agent 任务影子状态上报")
    @PermitAll
    @Idempotent
    public CommonResult<Boolean> reportTaskStatus(@Valid @RequestBody BidTaskShadowReqDTO reqVO) {
        shadowService.report(reqVO);
        return success(true);
    }

    // ================= 智能体平台（2026-09-16 新增） =================

    @Resource
    private cn.iocoder.txgy.module.bidding.service.bidagent.BidAgentRuntimeService runtimeService;
    @Resource
    private cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidProjectMapper bidProjectMapper;

    @GetMapping("/agent-runtime")
    @PermitAll // 执行侧（Node 编排）在任务开始时拉取运行配置：模型/提示词/技能/工具白名单/MCP
    @Operation(summary = "运行配置下发（模型 + 提示词 + 技能 + 工具白名单 + MCP；api-key 仅内网传输）")
    public CommonResult<java.util.Map<String, Object>> agentRuntime(
            @RequestParam(value = "taskType", required = false) String taskType,
            @RequestParam(value = "tenantId", required = false) Long tenantId) {
        return success(runtimeService.buildRuntime(taskType, tenantId));
    }

    @PostMapping("/project/facts")
    @PermitAll
    @Idempotent
    @Operation(summary = "回写项目事实卡与招标解析结果（parse 阶段产出）")
    public CommonResult<Boolean> saveProjectFacts(@RequestBody java.util.Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        Long tenantId = toLong(body.get("tenantId"));
        if (projectId == null) {
            throw exception(
                    cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_PROJECT_NOT_EXISTS);
        }
        // tenantId 必填：缺失时不再静默落 0L（防止把租户数据写进平台行）
        if (tenantId == null) {
            throw exception(BAD_REQUEST, "tenantId 不能为空");
        }
        cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidProjectDO project = bidProjectMapper.selectById(projectId);
        if (project == null) {
            project = new cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidProjectDO();
            project.setId(projectId);
            project.setName("项目-" + projectId);
            project.setStatus(0);
            project.setTenantId(tenantId);
        }
        if (body.get("factsJson") != null) {
            project.setFactsJson(String.valueOf(body.get("factsJson")));
        }
        if (body.get("tenderParseJson") != null) {
            project.setTenderParseJson(String.valueOf(body.get("tenderParseJson")));
        }
        if (project.getCreateTime() == null && project.getId() != null && bidProjectMapper.selectById(projectId) == null) {
            bidProjectMapper.insert(project);
        } else {
            bidProjectMapper.updateById(project);
        }
        return success(true);
    }

    @PostMapping("/outline")
    @PermitAll
    @Idempotent
    @Operation(summary = "回写标书章节树（outline 阶段产出）")
    public CommonResult<Boolean> saveOutline(@RequestBody java.util.Map<String, Object> body) {
        Long projectId = toLong(body.get("projectId"));
        if (projectId == null) {
            throw cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception(
                    cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_PROJECT_NOT_EXISTS);
        }
        // tenantId 必填：缺失时不再静默落 0L（防止把租户数据写进平台行）
        if (toLong(body.get("tenantId")) == null) {
            throw exception(BAD_REQUEST, "tenantId 不能为空");
        }
        cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidProjectDO exist = bidProjectMapper.selectById(projectId);
        cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidProjectDO project = exist == null
                ? new cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidProjectDO() : exist;
        if (exist == null) {
            project.setId(projectId);
            project.setName("项目-" + projectId);
            project.setStatus(0);
            Long tenantId = toLong(body.get("tenantId"));
            project.setTenantId(tenantId == null ? 0L : tenantId);
        }
        project.setOutlineJson(body.get("outlineJson") == null ? null : String.valueOf(body.get("outlineJson")));
        if (exist == null) {
            bidProjectMapper.insert(project);
        } else {
            bidProjectMapper.updateById(project);
        }
        return success(true);
    }

    private static Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Long.valueOf(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
