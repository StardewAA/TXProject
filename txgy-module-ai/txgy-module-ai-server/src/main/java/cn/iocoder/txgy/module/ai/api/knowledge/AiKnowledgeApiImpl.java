package cn.iocoder.txgy.module.ai.api.knowledge;

import cn.iocoder.txgy.framework.common.pojo.CommonResult;
import cn.iocoder.txgy.framework.common.enums.CommonStatusEnum;
import cn.iocoder.txgy.module.ai.dal.dataobject.knowledge.AiKnowledgeDO;
import cn.iocoder.txgy.module.ai.service.knowledge.AiKnowledgeSegmentService;
import cn.iocoder.txgy.module.ai.service.knowledge.AiKnowledgeService;
import cn.iocoder.txgy.module.ai.service.knowledge.bo.AiKnowledgeSegmentSearchReqBO;
import cn.iocoder.txgy.module.ai.service.knowledge.bo.AiKnowledgeSegmentSearchRespBO;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static cn.iocoder.txgy.framework.common.pojo.CommonResult.success;

/**
 * AI 知识库 API 实现
 *
 * <p>把 AI 模块已有的「分段向量检索」能力开放给业务模块，供其做 RAG。
 * 本身不引入新的检索逻辑，只是加了「跨该租户全部启用知识库」的聚合与排序。
 */
@RestController
@Validated
@Slf4j
public class AiKnowledgeApiImpl implements AiKnowledgeApi {

    @Resource
    private AiKnowledgeService knowledgeService;
    @Resource
    private AiKnowledgeSegmentService knowledgeSegmentService;

    @Override
    public CommonResult<List<AiKnowledgeSimpleDTO>> getEnabledKnowledgeList() {
        List<AiKnowledgeDO> list =
                knowledgeService.getKnowledgeSimpleListByStatus(CommonStatusEnum.ENABLE.getStatus());
        List<AiKnowledgeSimpleDTO> result = new ArrayList<>(list.size());
        for (AiKnowledgeDO item : list) {
            AiKnowledgeSimpleDTO dto = new AiKnowledgeSimpleDTO();
            dto.setId(item.getId());
            dto.setName(item.getName());
            result.add(dto);
        }
        return success(result);
    }

    @Override
    public CommonResult<List<AiKnowledgeSegmentDTO>> searchKnowledge(String content, Integer topK) {
        List<AiKnowledgeSimpleDTO> knowledgeList = getEnabledKnowledgeList().getData();
        if (knowledgeList == null || knowledgeList.isEmpty()) {
            return success(new ArrayList<>());
        }
        // 逐个知识库召回后合并、按相似度倒序。
        // 单个知识库检索失败（如向量索引未构建、模型不可用）不应影响其余知识库，
        // 因此这里吞掉异常并记录日志，保证「能答的照样答」。
        List<AiKnowledgeSegmentDTO> merged = new ArrayList<>();
        for (AiKnowledgeSimpleDTO knowledge : knowledgeList) {
            try {
                List<AiKnowledgeSegmentSearchRespBO> segments =
                        knowledgeSegmentService.searchKnowledgeSegment(new AiKnowledgeSegmentSearchReqBO()
                                .setKnowledgeId(knowledge.getId())
                                .setContent(content)
                                .setTopK(topK));
                if (segments == null) {
                    continue;
                }
                for (AiKnowledgeSegmentSearchRespBO segment : segments) {
                    AiKnowledgeSegmentDTO dto = new AiKnowledgeSegmentDTO();
                    dto.setId(segment.getId());
                    dto.setDocumentId(segment.getDocumentId());
                    dto.setKnowledgeId(segment.getKnowledgeId());
                    dto.setContent(segment.getContent());
                    dto.setScore(segment.getScore());
                    merged.add(dto);
                }
            } catch (Exception ex) {
                log.warn("[searchKnowledge][知识库({}) 检索失败，已跳过：{}]",
                        knowledge.getId(), ex.getMessage());
            }
        }
        merged.sort(Comparator.comparing(AiKnowledgeSegmentDTO::getScore,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return success(merged);
    }

}
