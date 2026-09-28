package cn.iocoder.txgy.module.bidding.service.bidagent;

// [规范依据]《招投标模块开发规范》§4.2
// [业务目标] 切片治理：编辑/拆分/合并/批量删除，人工修改落「edited」标记并即时同步向量库；
//           向量库不可用时只落台账，不阻断编辑，由前端提示「向量待重建」。
// [为什么] 切片表 bidding_bid_agent_chunk 是检索链（向量 + BM25）共用的唯一切片台账，
//           治理动作必须同表落库 + 同源更新向量，否则"改了不生效"或"两处不一致"。

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.context.TenantContextHolder;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentChunkDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentChunkMapper;
import cn.iocoder.txgy.module.bidding.service.bidagent.index.BidIndexService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_SEGMENT_CONTENT_EMPTY;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_SEGMENT_MERGE_REQUIRED;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_SEGMENT_NOT_EXISTS;

@Service
public class BidAgentSegmentServiceImpl implements BidAgentSegmentService {

    @Resource
    private BidAgentChunkMapper chunkMapper;
    @Resource
    private BidIndexService indexService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void edit(Long id, String content) {
        BidAgentChunkDO chunk = load(id);
        String trimmed = StrUtil.nullToEmpty(content).trim();
        if (trimmed.isEmpty()) {
            throw exception(BID_AGENT_SEGMENT_CONTENT_EMPTY);
        }
        chunk.setContent(trimmed);
        chunk.setEdited(1);
        chunkMapper.updateById(chunk);
        indexService.reindexChunk(TenantContextHolder.getTenantId(), chunk);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void split(Long id) {
        BidAgentChunkDO chunk = load(id);
        String[] parts = splitContent(chunk.getContent());
        if (parts == null) {
            throw exception(BID_AGENT_SEGMENT_CONTENT_EMPTY);
        }
        // 前半：保留原切片行；后半：新建切片行，溯源块号加 -2 后缀区分
        chunk.setContent(parts[0]);
        chunk.setEdited(1);
        chunkMapper.updateById(chunk);
        indexService.reindexChunk(TenantContextHolder.getTenantId(), chunk);

        BidAgentChunkDO second = new BidAgentChunkDO();
        second.setTenantId(chunk.getTenantId());
        second.setProjectId(chunk.getProjectId());
        second.setDocType(chunk.getDocType());
        second.setBidSectionCode(chunk.getBidSectionCode());
        second.setSourceDocName(chunk.getSourceDocName());
        second.setChapterPath(chunk.getChapterPath());
        second.setPageNo(chunk.getPageNo());
        second.setSourceBlockIds(StrUtil.isNotBlank(chunk.getSourceBlockIds())
                ? chunk.getSourceBlockIds() + "-2" : null);
        second.setContent(parts[1]);
        second.setEdited(1);
        chunkMapper.insert(second);
        indexService.reindexChunk(TenantContextHolder.getTenantId(), second);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void merge(List<Long> ids) {
        if (ids == null || ids.size() < 2) {
            throw exception(BID_AGENT_SEGMENT_MERGE_REQUIRED);
        }
        List<BidAgentChunkDO> chunks = chunkMapper.selectListByIds(ids);
        if (chunks.size() != ids.size()) {
            throw exception(BID_AGENT_SEGMENT_NOT_EXISTS);
        }
        // 同一来源文档才可合并，且按 id 升序拼接保持原文顺序
        chunks.sort(Comparator.comparing(BidAgentChunkDO::getId));
        String sourceDocName = chunks.get(0).getSourceDocName();
        BidAgentChunkDO base = chunks.get(0);
        // 相邻性校验：所选切片必须来自同一来源文档，且在该文档按 id 排序的切片序列中位置连续，
        // 不允许「任意同源切片拼接」（接口口径为「相邻切片拼接为一条」）
        List<Long> allIds = chunkMapper.selectListBySourceDocName(sourceDocName).stream()
                .sorted(Comparator.comparing(BidAgentChunkDO::getId))
                .map(BidAgentChunkDO::getId)
                .toList();
        Set<Long> selected = new HashSet<>(ids);
        List<Integer> positions = new ArrayList<>();
        for (int i = 0; i < allIds.size(); i++) {
            if (selected.contains(allIds.get(i))) {
                positions.add(i);
            }
        }
        if (positions.size() != chunks.size()) {
            throw exception(BID_AGENT_SEGMENT_MERGE_REQUIRED);
        }
        for (int i = 1; i < positions.size(); i++) {
            if (positions.get(i) != positions.get(i - 1) + 1) {
                throw exception(BID_AGENT_SEGMENT_MERGE_REQUIRED);
            }
        }
        StringBuilder merged = new StringBuilder();
        List<BidAgentChunkDO> toDelete = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            BidAgentChunkDO chunk = chunks.get(i);
            if (!StrUtil.equals(sourceDocName, chunk.getSourceDocName())) {
                throw exception(BID_AGENT_SEGMENT_MERGE_REQUIRED);
            }
            if (i > 0) {
                merged.append('\n');
                toDelete.add(chunk);
            }
            merged.append(StrUtil.nullToEmpty(chunk.getContent()));
        }
        base.setContent(merged.toString());
        base.setEdited(1);
        chunkMapper.updateById(base);
        indexService.reindexChunk(TenantContextHolder.getTenantId(), base);
        indexService.deleteChunks(TenantContextHolder.getTenantId(), toDelete);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        List<BidAgentChunkDO> chunks = chunkMapper.selectListByIds(ids);
        if (chunks.isEmpty()) {
            return;
        }
        indexService.deleteChunks(TenantContextHolder.getTenantId(), chunks);
    }

    private BidAgentChunkDO load(Long id) {
        BidAgentChunkDO chunk = chunkMapper.selectById(id);
        if (chunk == null) {
            throw exception(BID_AGENT_SEGMENT_NOT_EXISTS);
        }
        return chunk;
    }

    /** 把正文一分为二：优先按段落中点切，单段则按字符中点（尽量切在句号处）；无法切成非空两半返回 null */
    private static String[] splitContent(String content) {
        String text = StrUtil.nullToEmpty(content).trim();
        if (text.isEmpty()) {
            return null;
        }
        String[] paragraphs = text.split("\\n+");
        if (paragraphs.length >= 2) {
            int mid = paragraphs.length / 2;
            String first = String.join("\n", Arrays.copyOfRange(paragraphs, 0, mid)).trim();
            String second = String.join("\n", Arrays.copyOfRange(paragraphs, mid, paragraphs.length)).trim();
            if (!first.isEmpty() && !second.isEmpty()) {
                return new String[]{first, second};
            }
        }
        int half = text.length() / 2;
        int cut = text.indexOf('。', half);
        if (cut <= 0 || cut >= text.length() - 1) {
            cut = half;
        }
        String first = text.substring(0, cut + 1).trim();
        String second = text.substring(cut + 1).trim();
        if (first.isEmpty() || second.isEmpty()) {
            return null;
        }
        return new String[]{first, second};
    }
}
