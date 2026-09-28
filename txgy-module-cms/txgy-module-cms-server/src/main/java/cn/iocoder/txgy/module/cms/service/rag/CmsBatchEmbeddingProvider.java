package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.framework.search.core.TextEmbeddingProvider;

import java.util.List;

/** CMS 内部批量向量化能力，不修改公共搜索框架。 */
public interface CmsBatchEmbeddingProvider extends TextEmbeddingProvider {

    List<float[]> embedAll(List<String> texts);

    /** 实际用于向量化的模型；默认兼容旧实现。 */
    default String embeddingModelId() {
        return modelId();
    }

}
