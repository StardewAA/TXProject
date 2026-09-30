package cn.iocoder.txgy.module.bidding.framework.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（配置放 framework/*）
// [业务目标] 标书智能体接入配置：检索模型统一走阿里云百炼云端（embedding + rerank），向量库复用 Qdrant 容器；
//           机对机密钥仍用于「Node → Java 契约端」的 X-Agent-Key 校验
// [为什么] 2026-09-16 口径：不再自建本地模型（TEI），key/模型/配额由云端统一持有；
//           旧的"底座透传"配置（enabled/base-url/index-path/retrieve-path/timeout-seconds）随链路一并删除

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "bidding.bid-agent")
@Data
public class BidAgentProperties {

    /** 机对机密钥（契约端 X-Agent-Key）；为空表示开发态不校验 */
    private String agentKey = "";

    /**
     * 门户标书工坊（个人/平台租户 tenant=0）使用的目标企业租户。
     * 设置后，租户 0 访问标书 Agent 管理端接口时，整条链路（材料入库/生成任务台账/知识库检索/企业事实/产物下载）
     * 挂到该企业租户（如西安欧鹏），避免个人租户下取不到企业资料而空转；
     * 为空则沿用当前租户（企业账号 tenant>0 不受影响）。契约端 /contract（Node 底座机对机）不拦截。
     */
    private Long portalEnterpriseTenantId;

    /**
     * 是否启用检索能力（云端 embedding + Qdrant 向量库）。
     * false 时 /retrieve 返回 hit=false（转人工）、入库跳过并写明原因，便于无 key 环境先跑 CRUD。
     */
    private Boolean aiEnabled = false;

    /**
     * 文件下载 host 重写（容器化部署用）。
     * 材料入库要下载文件 url（宿主返回的 host 是 127.0.0.1:48080），bidding 容器内访问不到，
     * 需把 url 的 host 重写为网关容器名（如 txgy-gateway:48080）。空=不重写（宿主 JVM 直跑场景）。
     */
    private String fileDownloadHost = "";

    /** 云端 Embedding（阿里云百炼，OpenAI 兼容端点） */
    private Embedding embedding = new Embedding();

    /** 云端 Rerank（阿里云百炼 text-rerank） */
    private Rerank rerank = new Rerank();

    /** 执行侧底座（Node bid-agent-service）接入参数 */
    private Orchestrator orchestrator = new Orchestrator();

    /** 向量检索参数 */
    private Retrieve retrieve = new Retrieve();

    /** 入库文本清洗参数（索引阶段：去噪 / 结构归一 / 编码统一） */
    private Index index = new Index();

    @Data
    public static class Embedding {

        /** 百炼 base-url（缺省公共域名；专属网关可覆盖） */
        private String baseUrl = "https://dashscope.aliyuncs.com";

        /** 百炼 key（建议用环境变量 DASHSCOPE_API_KEY 注入）；为空＝调用时报"云端检索不可用" */
        private String apiKey = "";

        /** 向量模型（text-embedding-v3；账号未开通时改 text-embedding-v4 / text-embedding-v2） */
        private String model = "text-embedding-v3";

        /** 向量维度：必须与 Qdrant 集合维度一致（text-embedding-v3 默认 1024） */
        private Integer dimensions = 1024;

        /** 单次批量条数（百炼 text-embedding-v3 单次上限 10） */
        private Integer batchSize = 10;

        /** 单次调用超时（秒） */
        private Integer timeoutSeconds = 60;
    }

    @Data
    public static class Rerank {

        /** 是否启用重排；关闭则仅用向量分数 + 阈值 */
        private Boolean enabled = true;

        /** 百炼 base-url（与 embedding 同域） */
        private String baseUrl = "https://dashscope.aliyuncs.com";

        /** 重排模型（gte-rerank-v2；账号未开通时改 gte-rerank） */
        private String model = "gte-rerank-v2";

        /** 交给百炼的候选上限（空＝不传 top_n，由应用层取 topK） */
        private Integer topN;

        /** 单次调用超时（秒） */
        private Integer timeoutSeconds = 60;
    }

    @Data
    public static class Orchestrator {

        /** 底座地址（Node bid-agent-service，默认本地 8199） */
        private String baseUrl = "http://127.0.0.1:8199";

        /** 单次调用超时（秒） */
        private Integer timeoutSeconds = 30;
    }

    @Data
    public static class Retrieve {

        /** 默认返回条数 */
        private Integer topK = 8;

        /** 相似度阈值，低于此值的片段不返回 */
        private Double minScore = 0.35d;

        /** 向量检索放大系数（供 rerank 重排） */
        private Integer candidateFactor = 4;

        /**
         * 关键词通道（BM25 / MySQL FULLTEXT + ngram）开关。
         * false 时行为与"仅向量 + rerank"完全一致，用于回滚与 A/B 对比。
         */
        private Boolean bm25Enabled = true;

        /** 关键词通道粗召回条数（与向量候选一起做 RRF 融合） */
        private Integer bm25TopK = 8;

        /** RRF 融合常数 k：score = Σ 1/(k + rank)；越大越平滑，60 为常用默认 */
        private Integer rrfK = 60;

        /**
         * 标段过滤是否放行"未绑标段"的材料（FACT 企业资料 / TEMPLATE 技术母本可跨标段复用）。
         *
         * <p>true（默认）：标段隔离只约束 TENDER 招标文件，企业资料仍召回——多标段项目里
         * 资质/业绩这类证据只入库一次，若按标段一并过滤会出现"资料在库里、就是搜不到"。
         * false：严格按标段隔离，未绑标段的材料不再返回。
         */
        private Boolean sectionIncludeUnbound = true;

        /**
         * 未指定标段时是否按标段均衡占额。
         *
         * <p>多标段语料里各标段的条款高度相似（同一份招标文件拆成 N 个标段），
         * 纯按分数取前 topK 会被单个标段霸榜；均衡占额让每个标段都有代表，
         * 额度没用满（其他标段没有更多候选）时再按原顺序补足，不减少返回条数。
         */
        private Boolean sectionBalanceEnabled = true;
    }

    @Data
    public static class Index {

        /**
         * 入库前是否清洗文本（去噪 / 结构归一 / 编码统一，见 BidTextCleaner）。
         *
         * <p>false 时原文直入，用于回滚与 A/B 对比（与检索侧 bm25-enabled 同样只做开关，不做静默降级）。
         */
        private Boolean cleanEnabled = true;

        /** 切分窗口（字符），默认 500 */
        private Integer chunkSize = 500;

        /**
         * 相邻片段重叠字符数，默认 50（窗口的 10%）。
         *
         * <p>答案常落在切分点两侧：一条合同条款被切成两段时，两个片段各持半句，
         * 向量与关键词都可能只命中半截。重叠让跨边界内容至少在一个片段里是完整的。
         */
        private Integer chunkOverlap = 50;
    }
}
