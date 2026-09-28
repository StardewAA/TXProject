package cn.iocoder.txgy.module.bidding.service.bidagent;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentSkillVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentSkillDO;

import java.util.List;

/** 技能服务（轻量提示词增强包） */
public interface BidAgentSkillService {

    PageResult<BidAgentSkillVO.RespVO> page(BidAgentSkillVO.PageReqVO reqVO);

    BidAgentSkillVO.RespVO get(Long id);

    Long save(BidAgentSkillVO.SaveReqVO reqVO);

    void delete(Long id);

    /** 某任务类型可自动挂载的启用技能 */
    List<BidAgentSkillDO> listAutoAttachByTaskType(String taskType);

    /** 智能体当前挂载的技能（首次读取时按 taskType 自动挂载推荐技能） */
    List<BidAgentSkillDO> listByProfile(Long profileId, String taskType, boolean autoAttachIfEmpty);

    /** 覆盖设置智能体挂载的技能 */
    void setProfileSkills(Long profileId, List<Long> skillIds);
}
