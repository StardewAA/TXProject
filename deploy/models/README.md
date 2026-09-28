# 标书 Agent 私有化模型服务

对应《标书Agent_最终架构设计》第 6 章「部署拓扑与资源」。三个模型可共用一张 GPU（验证档 24GB），也可分卡。

## 组件与端口

| 组件 | 镜像 | 模型 | 端口 | 资源 |
|---|---|---|---|---|
| Qdrant | `qdrant/qdrant:latest` | — | 6333/6334 | 内存 4GB 起步 |
| TEI Embedding | `text-embeddings-inference:cpu-1.5` | `BAAI/bge-m3` | 8081 | CPU 可跑 / GPU 约 3GB |
| TEI Reranker | `text-embeddings-inference:cpu-1.5` | `BAAI/bge-reranker-v2-m3` | 8082 | 约 1.2GB |
| vLLM | `vllm/vllm-openai:latest` | `Qwen2.5-14B-Instruct-AWQ` | 8100 | 约 10GB 显存 |

## 启动

```bash
# 1) 启动
docker compose -f deploy/models/docker-compose.yml up -d

# 2) 验证 embedding（TEI）
curl http://127.0.0.1:8081/embed -H 'Content-Type: application/json' \
  -d '{"inputs":"投标人须具备安全生产许可证"}'

# 3) 验证 rerank（TEI）
curl http://127.0.0.1:8082/rerank -H 'Content-Type: application/json' \
  -d '{"query":"安全生产许可证","texts":["投标人须具备安全生产许可证","本项目工期210天"]}'

# 4) 验证 vLLM（OpenAI 兼容）
curl http://127.0.0.1:8100/v1/chat/completions -H 'Content-Type: application/json' \
  -d '{"model":"Qwen2.5-14B-Instruct-AWQ","messages":[{"role":"user","content":"你好"}]}'
```

## 与 Java / Agent 侧对接

| 服务 | Java 侧配置（`txgy-server` 的 application.yaml） | Agent 侧配置（`bid-agent-service/.env`） |
|---|---|---|
| Embedding | `spring.ai.openai.embedding` 指向 `http://127.0.0.1:8081/v1` | — |
| Rerank | `txgy.bid.rerank.base-url=http://127.0.0.1:8082` | — |
| LLM | — | `LLM_BASE_URL=http://127.0.0.1:8100/v1` |
| Qdrant | `spring.ai.vectorstore.qdrant` host 127.0.0.1:6334 | — |

## 注意事项

- **内网镜像仓库**：TEI / vLLM / Qdrant 镜像体积较大，建议提前 `docker pull` 后 `docker save` 进内网镜像仓库（架构文档风险登记表）。
- **模型权重离线**：内网无法访问 HuggingFace 时，把权重放到 `./hf-models` 并改用 `--model-id /data/xxx` 本地路径。
- **rerank 镜像版本**：bge-reranker-v2-m3 需要 TEI 1.4+；若当前镜像不识别 rerank 类型，换 `text-embeddings-inference:1.5`（GPU）并在 command 加 `--revision` 指定。
- **多租户**：Qdrant 建议按租户独立 collection（见 Java 侧 `BidRetrievalService` 的 collection 命名）。
