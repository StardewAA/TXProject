package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.module.cms.dto.law.CmsLawAnswerReqDTO;
import cn.iocoder.txgy.module.cms.dto.law.CmsLawSearchRespDTO;

import java.util.List;

/** 只基于召回原文生成回答的大模型扩展点。 */
public interface CmsAnswerGenerator {

    String modelId();

    String generate(String question, List<CmsLawSearchRespDTO> contexts);

    default String generate(String question, List<CmsLawSearchRespDTO> contexts,
                            List<CmsLawAnswerReqDTO.HistoryItem> history) {
        return generate(question, contexts);
    }

}
