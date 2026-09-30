package cn.iocoder.txgy.module.bidding.framework.bidagent;

// [规范依据]《招投标模块开发规范》§4.2
// [业务目标] 把百炼云端 embedding 适配成 Spring AI 的 EmbeddingModel，供 Qdrant VectorStore 使用
// [为什么] 自研适配比引 spring-ai-alibaba starter 更可控：不需要它的自动配置（平台已证明 2.0.0-M1.1 与
//          Spring AI 2.0.0 不兼容而整体排除），同时能精确控制维度（决定 Qdrant 集合维度）

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 百炼 EmbeddingModel 适配器：把向量化请求转给 {@link DashScopeEmbeddingClient}。
 *
 * <p>本 Bean **常驻注册**（不受 ai-enabled 影响）：Qdrant VectorStore 自动配置需要一个唯一的 EmbeddingModel，
 * 若按 ai-enabled 条件注册，则 ai-enabled=false 时容器无法构造 vectorStore 而启动失败。
 * "是否真的用检索"由 {@code bidding.bid-agent.ai-enabled} 在 service 层判断（false 时入库跳过、检索返回 hit=false），
 * 而"缺 key 时能否启动"由调用期错误码 {@code 1_060_111_070} 兜底——两者互不干扰。
 */
@Component
@Slf4j
public class DashScopeEmbeddingModel implements EmbeddingModel {

    private final DashScopeEmbeddingClient client;

    public DashScopeEmbeddingModel(DashScopeEmbeddingClient client) {
        this.client = client;
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions() == null ? List.of() : request.getInstructions();
        List<float[]> vectors = client.embed(instructions);
        List<Embedding> embeddings = new ArrayList<>(vectors.size());
        for (int index = 0; index < vectors.size(); index++) {
            embeddings.add(new Embedding(vectors.get(index), index));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return client.embedOne(document.getText());
    }

    @Override
    public int dimensions() {
        return client.dimensions();
    }
}
