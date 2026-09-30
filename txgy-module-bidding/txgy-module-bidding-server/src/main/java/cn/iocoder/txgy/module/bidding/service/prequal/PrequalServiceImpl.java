package cn.iocoder.txgy.module.bidding.service.prequal;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalSaveReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalDO;
import cn.iocoder.txgy.module.bidding.dal.mysql.prequal.PrequalMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import cn.iocoder.txgy.module.bidding.enums.prequal.PrequalStatusEnum;
import cn.iocoder.txgy.module.bidding.service.biznum.BiddingBizNoGenerator;
import cn.iocoder.txgy.module.bidding.service.biznum.BiddingBizNoTypeEnum;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

/**
 * 资格预审实现（招标方发起）。
 *
 * <p>申请期校验（条例第17条）：自资格预审文件停止发售（applyStartTime）至申请截止
 * （applyDeadline）不得少于5日；发布时强校验，不足则拒绝。
 */
@Service
@Slf4j
public class PrequalServiceImpl implements PrequalService {

    /** 资格预审申请期最短天数（《实施条例》第17条：自文件停止发售起不得少于5日） */
    private static final long MIN_APPLY_PERIOD_DAYS = 5L;

    @Resource
    private PrequalMapper prequalMapper;
    @Resource
    private BiddingBizNoGenerator biddingBizNoGenerator;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long save(PrequalSaveReqVO reqVO) {
        if (reqVO.getId() != null) {
            PrequalDO exist = requirePrequal(reqVO.getId());
            // 已出结果/已关闭不可改
            if (PrequalStatusEnum.RESULTED.name().equals(exist.getStatus())
                    || PrequalStatusEnum.CLOSED.name().equals(exist.getStatus())) {
                throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_STATUS_INVALID, exist.getStatus());
            }
            exist.setTenderProjectCode(reqVO.getTenderProjectCode());
            exist.setBidSectionCode(reqVO.getBidSectionCode());
            exist.setTitle(reqVO.getTitle());
            exist.setNoticeCode(reqVO.getNoticeCode());
            exist.setFileId(reqVO.getFileId());
            exist.setApplyStartTime(reqVO.getApplyStartTime());
            exist.setApplyDeadline(reqVO.getApplyDeadline());
            exist.setRemark(reqVO.getRemark());
            prequalMapper.updateById(exist);
            return exist.getId();
        }
        PrequalDO prequal = new PrequalDO();
        prequal.setPrequalCode(biddingBizNoGenerator.generate(BiddingBizNoTypeEnum.PREQUAL, null));
        prequal.setTenderProjectCode(reqVO.getTenderProjectCode());
        prequal.setBidSectionCode(reqVO.getBidSectionCode());
        prequal.setTitle(reqVO.getTitle());
        prequal.setNoticeCode(reqVO.getNoticeCode());
        prequal.setFileId(reqVO.getFileId());
        prequal.setApplyStartTime(reqVO.getApplyStartTime());
        prequal.setApplyDeadline(reqVO.getApplyDeadline());
        prequal.setStatus(PrequalStatusEnum.DRAFT.name());
        prequal.setRemark(reqVO.getRemark());
        prequalMapper.insert(prequal);
        return prequal.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publish(Long id) {
        PrequalDO prequal = requirePrequal(id);
        if (!PrequalStatusEnum.DRAFT.name().equals(prequal.getStatus())) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_STATUS_INVALID, prequal.getStatus());
        }
        validateApplyPeriod(prequal);
        prequal.setStatus(PrequalStatusEnum.PUBLISHED.name());
        prequalMapper.updateById(prequal);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        PrequalDO prequal = requirePrequal(id);
        if (PrequalStatusEnum.CLOSED.name().equals(prequal.getStatus())) {
            return;
        }
        prequal.setStatus(PrequalStatusEnum.CLOSED.name());
        prequalMapper.updateById(prequal);
    }

    @Override
    public PrequalDO get(Long id) {
        return requirePrequal(id);
    }

    @Override
    public PrequalDO getByCode(String prequalCode) {
        PrequalDO prequal = prequalMapper.selectByCode(prequalCode);
        if (prequal == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_NOT_EXISTS, prequalCode);
        }
        return prequal;
    }

    @Override
    public PageResult<PrequalDO> page(PrequalPageReqVO reqVO) {
        return prequalMapper.selectPage(reqVO);
    }

    /** 校验申请期≥5日（条例第17条；仅两个时间都设置时校验）。 */
    private void validateApplyPeriod(PrequalDO prequal) {
        if (prequal.getApplyStartTime() == null || prequal.getApplyDeadline() == null) {
            return;
        }
        long days = Duration.between(prequal.getApplyStartTime(), prequal.getApplyDeadline()).toDays();
        if (days < MIN_APPLY_PERIOD_DAYS) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_APPLY_PERIOD_TOO_SHORT);
        }
    }

    private PrequalDO requirePrequal(Long id) {
        PrequalDO prequal = prequalMapper.selectById(id);
        if (prequal == null) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.PREQUAL_NOT_EXISTS, id);
        }
        return prequal;
    }
}
