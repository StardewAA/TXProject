package cn.iocoder.txgy.module.ai.api.knowledge;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.module.ai.enums.ApiConstants;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

import static cn.iocoder.txgy.module.ai.enums.ApiConstants.PREFIX;

/**
 * AI 知识库 API
 *
 * <p>供其他业务模块（如 WMS 自研 AI 客服）在回答不了「结构化数据查询」时，
 * 退一步到知识库做检索增强（RAG）：把公司制度、产品资料等非结构化文档检索出来，
 * 交给大模型照着资料回答，而不是凭空编造。
 *
 * <p>检索范围是该租户下**全部启用中的知识库**，业务模块无需关心知识库编号，
 * 也就不必在自身配置里维护一份「用哪个知识库」的映射。
 */
@FeignClient(name = ApiConstants.NAME)
@Tag(name = "RPC 服务 - AI 知识库")
public interface AiKnowledgeApi {

    String PREFIX_RPC = PREFIX + "/knowledge";

    /**
     * 当前租户启用中的知识库列表（供业务模块做可用性判断与提示）
     */
    @GetMapping(PREFIX_RPC + "/list-enabled")
    @Operation(summary = "获取启用中的知识库列表")
    CommonResult<List<AiKnowledgeSimpleDTO>> getEnabledKnowledgeList();

    /**
     * 在全部启用中的知识库内做向量检索
     *
     * @param content 检索内容（通常是用户原话）
     * @param topK    每个知识库最多返回的分段数；为空时用知识库自身配置
     * @return 命中的分段，按相似度倒序；无命中返回空列表
     */
    @GetMapping(PREFIX_RPC + "/search")
    @Operation(summary = "跨知识库检索分段")
    CommonResult<List<AiKnowledgeSegmentDTO>> searchKnowledge(
            @Parameter(description = "检索内容", required = true)
            @RequestParam("content") String content,
            @Parameter(description = "每个知识库最多返回的分段数", example = "5")
            @RequestParam(value = "topK", required = false) Integer topK);

}
