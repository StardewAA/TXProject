package cn.iocoder.txgy.module.bidding.service.bidagent.draft;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidChapterDraftDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidChapterDraftMapper;
import cn.iocoder.txgy.module.bidding.api.dto.bidagent.BidChapterDraftReqDTO;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 章节草稿服务实现：按 (projectId, nodeId, version) 幂等，重复调用只更新不重复插入。
 */
@Service
public class BidDraftServiceImpl implements BidDraftService {

    @Resource
    private BidChapterDraftMapper draftMapper;

    @Override
    public Long saveChapter(BidChapterDraftReqDTO reqDTO) {
        return TenantUtils.execute(reqDTO.getTenantId(), () -> {
            Integer version = ObjUtil.defaultIfNull(reqDTO.getVersion(), 1);
            BidChapterDraftDO existing = draftMapper.selectByProjectIdAndNodeIdAndVersion(
                    reqDTO.getProjectId(), reqDTO.getNodeId(), version);

            BidChapterDraftDO draft = existing != null ? existing : new BidChapterDraftDO();
            draft.setTenantId(reqDTO.getTenantId());
            draft.setProjectId(reqDTO.getProjectId());
            draft.setNodeId(reqDTO.getNodeId());
            draft.setContent(reqDTO.getContent());
            draft.setRefs(CollUtil.isEmpty(reqDTO.getRefs()) ? null : String.join(",", reqDTO.getRefs()));
            draft.setStatus(StrUtil.blankToDefault(reqDTO.getStatus(), "draft"));
            draft.setVersion(version);

            if (existing == null) {
                draftMapper.insert(draft);
            } else {
                draftMapper.updateById(draft);
            }
            return draft.getId();
        });
    }
}
