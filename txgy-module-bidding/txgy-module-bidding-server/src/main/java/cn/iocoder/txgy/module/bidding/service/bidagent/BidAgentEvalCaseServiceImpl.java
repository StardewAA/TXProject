package cn.iocoder.txgy.module.bidding.service.bidagent;

// [规范依据]《招投标模块开发规范》§4.2
// [业务目标] golden set 的增删改查：A 负责存储，标注数据由指标负责人提供
// [为什么] 评测用例必须落库而不是贴在页面上：换人/换天都能跑出同一份 Recall 数字

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.common.util.object.BeanUtils;
import cn.iocoder.txgy.module.bidding.controller.admin.bidagent.vo.BidAgentEvalVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.bidagent.BidAgentEvalCaseDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.bidagent.BidAgentEvalCaseMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants.BID_AGENT_EVAL_CASE_NOT_EXISTS;

@Service
public class BidAgentEvalCaseServiceImpl implements BidAgentEvalCaseService {

    @Resource
    private BidAgentEvalCaseMapper evalCaseMapper;

    @Override
    public PageResult<BidAgentEvalVO.CaseRespVO> page(BidAgentEvalVO.CasePageReqVO reqVO) {
        return BeanUtils.toBean(evalCaseMapper.selectPage(reqVO), BidAgentEvalVO.CaseRespVO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(BidAgentEvalVO.CaseSaveReqVO reqVO) {
        BidAgentEvalCaseDO evalCase = BeanUtils.toBean(reqVO, BidAgentEvalCaseDO.class);
        if (evalCase.getTopK() == null) {
            evalCase.setTopK(10);
        }
        if (evalCase.getEnabled() == null) {
            evalCase.setEnabled(1);
        }
        if (reqVO.getId() == null) {
            evalCaseMapper.insert(evalCase);
        } else {
            if (evalCaseMapper.selectById(reqVO.getId()) == null) {
                throw exception(BID_AGENT_EVAL_CASE_NOT_EXISTS);
            }
            evalCaseMapper.updateById(evalCase);
        }
        return evalCase.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (evalCaseMapper.selectById(id) == null) {
            throw exception(BID_AGENT_EVAL_CASE_NOT_EXISTS);
        }
        evalCaseMapper.deleteById(id);
    }
}
