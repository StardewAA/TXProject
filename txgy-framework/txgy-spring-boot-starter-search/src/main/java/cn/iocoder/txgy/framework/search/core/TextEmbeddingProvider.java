package cn.iocoder.txgy.framework.search.core;

/**
 * 文本向量模型扩展点。CMS 只依赖此契约，可接本地模型或 AI 模块 RPC。
 * modelId 和 dimensions 变化时必须重建对应 Redis 向量索引。
 */
public interface TextEmbeddingProvider {

    String modelId();

    int dimensions();

    float[] embed(String text);

}
