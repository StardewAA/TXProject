package cn.iocoder.txgy.module.bidding.controller.admin.bidagent.contract;

// [规范依据]《标书Agent_最终架构设计》§3.1（Agent → Java 只读契约）
// [业务目标] 给招投标侧的「知识库管理 → 切片与内容」提供只读片段台账
// [为什么] 切片是召回质量的第一现场，专员必须能按材料查看切成了什么；
//          该端点只读、幂等，不涉及向量改写，故不新增写动作

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import cn.iocoder.txgy.module.bidding.service.bidagent.index.BidIndexService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.annotation.security.PermitAll;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

@Tag(name = "管理后台 - 投标检索片段（只读）")
@RestController
@RequestMapping("/bidding/bid-agent/contract/segment")
@Validated
// 契约端由 Node 侧机对机调用，不携带 tenant-id 请求头，故此处需跳过租户校验过滤器。
@TenantIgnore
public class BidSegmentQueryController {

    @Resource
    private BidIndexService indexService;

    @GetMapping("/page")
    @Operation(summary = "按来源文档名分页查询切片台账（与向量库内容一致）")
    @PermitAll // 机对机契约端：免登（鉴权由 BidAgentKeyFilter 校验 X-Agent-Key）
    public CommonResult<Map<String, Object>> page(@RequestParam("sourceDocName") String sourceDocName,
                                                  @RequestParam(value = "tenantId", required = false) Long tenantId,
                                                  @RequestParam(value = "pageNo", required = false, defaultValue = "1") Integer pageNo,
                                                  @RequestParam(value = "pageSize", required = false, defaultValue = "20") Integer pageSize) {
        // 租户优先取显式传参（Agent 机对机调用不带 tenant-id 头），其次取上下文
        Long effectiveTenantId = tenantId != null ? tenantId : TenantContextHolder.getTenantId();
        return success(indexService.pageSegments(effectiveTenantId, sourceDocName, pageNo, pageSize));
    }
}
