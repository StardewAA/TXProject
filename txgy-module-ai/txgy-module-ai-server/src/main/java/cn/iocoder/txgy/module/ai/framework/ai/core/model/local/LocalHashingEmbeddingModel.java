package cn.iocoder.txgy.module.ai.framework.ai.core.model.local;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地哈希向量模型（Local Embedding Model）
 *
 * 不依赖任何外部 Embedding API，直接在本地将文本转换为稠密向量：
 * 1. 分词：CJK 单字 + CJK 二元组（bigram）+ ASCII 词元
 * 2. 加权：字符 1.0、二元组/词元 1.5，词频做亚线性压缩（1 + ln(tf)）
 * 3. 哈希：MurmurHash3 映射到固定维度，正负号区分冲突
 * 4. 归一化：L2 归一化，SimpleVectorStore 的余弦相似度等价于点积
 *
 * 特点：
 * - 完全离线、零成本、确定性强（同一文本向量恒定，无需持久化对齐）
 * - 对"严格按知识库内容回答"的字面匹配场景召回准确
 * - 适合企业内部知识库（生产工单、物料、库存等结构化数据问答）
 *
 * @author OPENLAB BS
 */
public class LocalHashingEmbeddingModel implements EmbeddingModel {

    /**
     * 向量维度：2 的幂，降低哈希冲突
     */
    public static final int DIMENSIONS = 1024;

    /**
     * 单字权重
     */
    private static final double WEIGHT_CHAR = 1.0;
    /**
     * 二元组 / ASCII 词元权重
     */
    private static final double WEIGHT_GRAM = 1.5;

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        List<String> instructions = request.getInstructions();
        List<Embedding> embeddings = new ArrayList<>(instructions.size());
        for (int i = 0; i < instructions.size(); i++) {
            float[] vector = embed(instructions.get(i));
            embeddings.add(new Embedding(vector, i));
        }
        return new EmbeddingResponse(embeddings);
    }

    @Override
    public float[] embed(Document document) {
        return embed(document.getText());
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }

    /**
     * 将文本向量化
     *
     * @param text 文本内容
     * @return L2 归一化后的稠密向量
     */
    public float[] embed(String text) {
        float[] vector = new float[DIMENSIONS];
        if (text == null || text.isEmpty()) {
            return vector;
        }
        // 1. 分词并统计词频
        Map<String, Integer> tf = tokenize(text);
        if (tf.isEmpty()) {
            return vector;
        }
        // 2. 哈希累加（亚线性词频 + 权重 + 正负号）
        for (Map.Entry<String, Integer> entry : tf.entrySet()) {
            String token = entry.getKey();
            double weight = token.length() >= 2 ? WEIGHT_GRAM : WEIGHT_CHAR;
            double value = weight * (1.0 + Math.log(entry.getValue()));
            long hash = murmur3(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            int index = (int) (Math.abs(hash) % DIMENSIONS);
            vector[index] += (hash % 2 == 0) ? value : -value;
        }
        // 3. L2 归一化
        double norm = 0.0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        if (norm > 0) {
            float scale = (float) (1.0 / Math.sqrt(norm));
            for (int i = 0; i < DIMENSIONS; i++) {
                vector[i] *= scale;
            }
        }
        return vector;
    }

    /**
     * 分词：CJK 单字、CJK 二元组、连续 ASCII 词元
     */
    private Map<String, Integer> tokenize(String text) {
        Map<String, Integer> tf = new HashMap<>();
        int length = text.length();
        StringBuilder asciiBuf = new StringBuilder();
        for (int i = 0; i < length; i++) {
            char current = text.charAt(i);
            // ASCII 词元
            if (current < 128) {
                if (Character.isLetterOrDigit(current)) {
                    asciiBuf.append(Character.toLowerCase(current));
                } else if (asciiBuf.length() > 0) {
                    addToken(tf, asciiBuf.toString());
                    asciiBuf.setLength(0);
                }
                continue;
            }
            if (asciiBuf.length() > 0) {
                addToken(tf, asciiBuf.toString());
                asciiBuf.setLength(0);
            }
            if (Character.isLetterOrDigit(current)) {
                // CJK 单字
                addToken(tf, String.valueOf(current));
                // CJK 二元组
                char next = i + 1 < length ? text.charAt(i + 1) : 0;
                if (next >= 128 && Character.isLetterOrDigit(next)) {
                    addToken(tf, new String(new char[]{current, next}));
                }
            }
        }
        if (asciiBuf.length() > 0) {
            addToken(tf, asciiBuf.toString());
        }
        return tf;
    }

    private void addToken(Map<String, Integer> tf, String token) {
        tf.merge(token, 1, Integer::sum);
    }

    /**
     * MurmurHash3 x86 32bit
     */
    private static long murmur3(byte[] data) {
        int h = 0x9747b28c;
        int c1 = 0xcc9e2d51;
        int c2 = 0x1b873593;
        int length = data.length;
        int roundedEnd = (length & 0xfffffffc);
        for (int i = 0; i < roundedEnd; i += 4) {
            int k = (data[i] & 0xff) | ((data[i + 1] & 0xff) << 8)
                    | ((data[i + 2] & 0xff) << 16) | (data[i + 3] << 24);
            k *= c1;
            k = Integer.rotateLeft(k, 15);
            k *= c2;
            h ^= k;
            h = Integer.rotateLeft(h, 13);
            h = h * 5 + 0xe6546b64;
        }
        int k = 0;
        for (int i = length & 3; i > 0; i--) {
            k ^= (data[roundedEnd + i - 1] & 0xff) << (i * 8);
        }
        if (length > 0) { // 避免 spot check：空串不参与
            k *= c1;
            k = Integer.rotateLeft(k, 15);
            k *= c2;
            h ^= k;
        }
        h ^= length;
        h ^= h >>> 16;
        h *= 0x85ebca6b;
        h ^= h >>> 13;
        h *= 0xc2b2ae35;
        h ^= h >>> 16;
        // 转为非负 long，避免 (int) 取模为负
        return h & 0xffffffffL;
    }

}
