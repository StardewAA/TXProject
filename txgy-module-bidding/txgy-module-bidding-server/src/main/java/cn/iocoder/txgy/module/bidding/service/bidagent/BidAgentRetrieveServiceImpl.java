package cn.iocoder.txgy.module.bidding.service.bidagent;

// [规范依据]《招投标模块开发规范》§4.2
// [业务目标] 召回验证 + 精准度测试：把"知识库好不好用"变成可复算的数字（用例命中率与未命中清单）
// [为什么] 2026-09-16 口径：召回不再绕 Node 底座（底座并未提供 /bid/agent/retrieve），改为直接调用
//          BidRetrievalService（百炼云端 embedding + Qdrant 向量库 + 可选云端 rerank）；
//          让专员能区分两类失败：资料根本没入库（未命中）还是切片/口径不对（命中了但块号不对）

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidRetrieveReqDTO;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidRetrieveRespDTO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentEvalVO;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentRetrieveVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentEvalCaseDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentEvalCaseMapper;
import cn.iocoder.txgy.module.bidding.service.bidagent.retrieval.BidRetrievalService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_EVAL_CASE_EMPTY;

@Service
@Slf4j
public class BidAgentRetrieveServiceImpl implements BidAgentRetrieveService {

    @Resource
    private BidRetrievalService retrievalService;
    @Resource
    private BidAgentEvalCaseMapper evalCaseMapper;

    @Override
    public BidAgentRetrieveVO.RespVO retrieve(BidAgentRetrieveVO.ReqVO reqVO) {
        // 后台召回验证用严格口径：云端不可用时抛出可读原因，避免专员把"配置没弄好"误判成"资料没入库"
        long start = System.nanoTime();
        BidAgentRetrieveVO.RespVO resp = toRespVO(retrievalService.retrieveStrict(toReqDTO(reqVO)));
        resp.setElapsedMs((System.nanoTime() - start) / 1_000_000);
        return resp;
    }

    @Override
    public BidAgentEvalVO.RunRespVO runEval(BidAgentEvalVO.RunReqVO reqVO) {
        List<BidAgentEvalCaseDO> cases = new ArrayList<>();
        if (reqVO != null && reqVO.getCaseIds() != null && !reqVO.getCaseIds().isEmpty()) {
            cases.addAll(evalCaseMapper.selectListByIds(reqVO.getCaseIds()));
        } else {
            cases.addAll(evalCaseMapper.selectEnabledList());
        }
        if (cases.isEmpty()) {
            throw exception(BID_AGENT_EVAL_CASE_EMPTY);
        }

        List<BidAgentEvalVO.CaseResultVO> results = new ArrayList<>();
        int hitCount = 0;
        int evaluatedCount = 0;
        int unlabeledCount = 0;
        int scoredCaseCount = 0;
        double scoreSum = 0;
        long latencySum = 0;
        long maxLatency = 0;
        Map<String, Integer> channelCounts = new LinkedHashMap<>();

        for (BidAgentEvalCaseDO evalCase : cases) {
            int topK = reqVO != null && reqVO.getTopK() != null ? reqVO.getTopK()
                    : (evalCase.getTopK() == null ? 10 : evalCase.getTopK());
            boolean evaluated = hasExpectation(evalCase);
            if (evaluated) {
                evaluatedCount++;
            } else {
                unlabeledCount++;
            }

            BidAgentRetrieveVO.ReqVO req = new BidAgentRetrieveVO.ReqVO();
            req.setQuery(evalCase.getQuery());
            req.setDocType(evalCase.getDocType());
            req.setBidSectionCode(evalCase.getBidSectionCode());
            req.setTopK(topK);

            BidAgentRetrieveVO.RespVO resp;
            long elapsedMs;
            try {
                resp = retrieve(req);
                elapsedMs = resp.getElapsedMs() == null ? 0 : resp.getElapsedMs();
            } catch (Exception ex) {
                log.warn("[runEval][用例执行失败，记为未命中] caseId={} msg={}", evalCase.getId(), ex.getMessage());
                channelCounts.merge("none", 1, Integer::sum);
                BidAgentEvalVO.CaseResultVO failed = new BidAgentEvalVO.CaseResultVO();
                failed.setCaseId(evalCase.getId());
                failed.setName(evalCase.getName());
                failed.setQuery(evalCase.getQuery());
                failed.setEvaluated(evaluated);
                failed.setHit(false);
                failed.setTopK(topK);
                failed.setMatchedBy("none");
                failed.setMissReason("用例执行异常：" + StrUtil.maxLength(ex.getMessage(), 200));
                results.add(failed);
                continue;
            }

            List<String> matchedBlockIds = new ArrayList<>();
            StringBuilder text = new StringBuilder();
            Double bestScore = null;
            String matchedBy = "none";
            if (resp.getChunks() != null) {
                for (BidAgentRetrieveVO.Chunk chunk : resp.getChunks()) {
                    if (chunk.getSourceBlockIds() != null) {
                        matchedBlockIds.addAll(chunk.getSourceBlockIds());
                    }
                    if (StrUtil.isNotBlank(chunk.getContent())) {
                        text.append(chunk.getContent()).append('\n');
                    }
                    if (chunk.getScore() != null && (bestScore == null || chunk.getScore() > bestScore)) {
                        bestScore = chunk.getScore();
                    }
                    if ("none".equals(matchedBy) && StrUtil.isNotBlank(chunk.getMatchedBy())) {
                        matchedBy = chunk.getMatchedBy();
                    }
                }
            }
            channelCounts.merge(matchedBy, 1, Integer::sum);

            long latencyMs = elapsedMs;
            latencySum += latencyMs;
            maxLatency = Math.max(maxLatency, latencyMs);

            if (evaluated && bestScore != null) {
                scoreSum += bestScore;
                scoredCaseCount++;
            }

            boolean hit = evaluated && judgeHit(evalCase, matchedBlockIds, text.toString());
            if (hit) {
                hitCount++;
            }

            BidAgentEvalVO.CaseResultVO result = new BidAgentEvalVO.CaseResultVO();
            result.setCaseId(evalCase.getId());
            result.setName(evalCase.getName());
            result.setQuery(evalCase.getQuery());
            result.setEvaluated(evaluated);
            result.setHit(hit);
            result.setTopK(topK);
            result.setMatchedBy(matchedBy);
            result.setMatchedBlockIds(matchedBlockIds);
            result.setMissReason(!evaluated ? "未配置期望标准，未计入用例命中率"
                    : (hit ? null : missReason(evalCase, resp, matchedBlockIds)));
            result.setScore(bestScore);
            result.setLatencyMs(elapsedMs);
            results.add(result);
        }

        BidAgentEvalVO.RunRespVO resp = new BidAgentEvalVO.RunRespVO();
        resp.setTotalCount(cases.size());
        resp.setHitCount(hitCount);
        Double caseHitRate = evaluatedCount == 0 ? null
                : Math.round(hitCount * 10000.0 / evaluatedCount) / 10000.0;
        resp.setCaseHitRate(caseHitRate);
        // 兼容旧前端字段；口径已统一为“已标注用例命中率”，不再是信息检索意义的 Recall@K。
        resp.setRecall(caseHitRate);
        resp.setEvaluatedCount(evaluatedCount);
        resp.setUnlabeledCount(unlabeledCount);
        resp.setScoredCaseCount(scoredCaseCount);
        resp.setUnscoredCaseCount(evaluatedCount - scoredCaseCount);
        resp.setScoreAvailable(scoredCaseCount > 0);
        resp.setScoreType(resolveScoreType(channelCounts, scoredCaseCount));
        resp.setChannelCounts(channelCounts);
        resp.setTopK(reqVO != null ? reqVO.getTopK() : null);
        resp.setAvgScore(scoredCaseCount == 0 ? null
                : Math.round(scoreSum / scoredCaseCount * 10000.0) / 10000.0);
        resp.setAvgLatencyMs(cases.isEmpty() ? null : latencySum / cases.size());
        resp.setMaxLatencyMs(maxLatency);
        resp.setCases(results);
        return resp;
    }

    /** 只有配置期望关键词或期望溯源块号的用例才参与命中率计算。 */
    private static boolean hasExpectation(BidAgentEvalCaseDO evalCase) {
        return StrUtil.isNotBlank(evalCase.getExpectBlockIds()) || StrUtil.isNotBlank(evalCase.getExpectKeywords());
    }

    /** 根据实际命中通道判断本批次分数口径。 */
    private static String resolveScoreType(Map<String, Integer> channelCounts, int scoredCaseCount) {
        if (scoredCaseCount <= 0) {
            return channelCounts.containsKey("bm25") ? "bm25" : "none";
        }
        if (channelCounts.containsKey("rerank")) {
            return "rerank";
        }
        if (channelCounts.containsKey("vector") && channelCounts.containsKey("bm25")) {
            return "mixed";
        }
        if (channelCounts.containsKey("vector")) {
            return "vector";
        }
        return channelCounts.containsKey("bm25") ? "bm25" : "none";
    }
    /** A 侧检索入参 → 契约 DTO：租户取上下文，角色/标段走 filters（与 Node 侧契约同口径） */
    private BidRetrieveReqDTO toReqDTO(BidAgentRetrieveVO.ReqVO reqVO) {
        BidRetrieveReqDTO dto = new BidRetrieveReqDTO();
        dto.setTenantId(TenantContextHolder.getTenantId());
        dto.setQuery(reqVO.getQuery());
        dto.setTopK(reqVO.getTopK());
        dto.setMinScore(reqVO.getMinScore());
        dto.setMode(reqVO.getMode());
        dto.setMandatory(reqVO.getMandatory());
        if (StrUtil.isNotBlank(reqVO.getDocType()) || StrUtil.isNotBlank(reqVO.getBidSectionCode())
                || CollUtil.isNotEmpty(reqVO.getBidSectionCodes())) {
            BidRetrieveReqDTO.Filters filters = new BidRetrieveReqDTO.Filters();
            filters.setDocType(reqVO.getDocType());
            filters.setBidSectionCode(reqVO.getBidSectionCode());
            filters.setBidSectionCodes(reqVO.getBidSectionCodes());
            dto.setFilters(filters);
        }
        return dto;
    }

    private BidAgentRetrieveVO.RespVO toRespVO(BidRetrieveRespDTO dto) {
        BidAgentRetrieveVO.RespVO resp = new BidAgentRetrieveVO.RespVO();
        resp.setHit(dto.getHit());
        List<BidAgentRetrieveVO.Chunk> chunks = new ArrayList<>();
        if (dto.getChunks() != null) {
            for (BidRetrieveRespDTO.Chunk source : dto.getChunks()) {
                BidAgentRetrieveVO.Chunk chunk = new BidAgentRetrieveVO.Chunk();
                chunk.setChunkId(source.getChunkId());
                chunk.setContent(source.getContent());
                chunk.setSourceDocName(source.getSourceDocName());
                chunk.setBidSectionCode(source.getBidSectionCode());
                chunk.setChapterPath(source.getChapterPath());
                chunk.setPageNo(source.getPageNo());
                chunk.setScore(source.getScore());
                chunk.setMatchedBy(source.getMatchedBy());
                chunk.setSourceBlockIds(source.getSourceBlockIds());
                chunks.add(chunk);
            }
        }
        resp.setChunks(chunks);
        return resp;
    }

    /** 命中判定：优先比溯源块号，其次比关键词（任一口径满足即算命中） */
    private boolean judgeHit(BidAgentEvalCaseDO evalCase, List<String> matchedBlockIds, String text) {
        if (StrUtil.isNotBlank(evalCase.getExpectBlockIds())) {
            for (String expect : evalCase.getExpectBlockIds().split(",")) {
                for (String actual : matchedBlockIds) {
                    if (actual.contains(expect.trim())) {
                        return true;
                    }
                }
            }
            return false;
        }
        if (StrUtil.isBlank(evalCase.getExpectKeywords())) {
            return !matchedBlockIds.isEmpty();
        }
        for (String keyword : evalCase.getExpectKeywords().split(",")) {
            if (StrUtil.isNotBlank(keyword) && text.contains(keyword.trim())) {
                return true;
            }
        }
        return false;
    }

    private String missReason(BidAgentEvalCaseDO evalCase, BidAgentRetrieveVO.RespVO resp, List<String> matchedBlockIds) {
        if (matchedBlockIds.isEmpty()) {
            return "未命中任何片段：请先在「资料库」确认该角色/标段资料已入库（入库状态=已入库），再用「召回验证」换问法复测";
        }
        if (StrUtil.isNotBlank(evalCase.getExpectBlockIds())) {
            return "命中了片段但溯源块号不符：多为切片过大/口径不一致，建议调整资料版本后重建索引";
        }
        return "命中了片段但未包含期望关键词：检索词与原文表述差异较大，建议在用例里改用原文用词";
    }
}
