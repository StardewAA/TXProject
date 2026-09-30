package cn.iocoder.txgy.module.bidding.service.prequal;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyPageReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalApplyReqVO;
import cn.iocoder.txgy.module.bidding.controller.admin.prequal.vo.PrequalReviewReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.prequal.PrequalApplyDO;

import java.util.List;

/**
 * 资格预审申请服务（投标方提交 + 资格审查委员会审查）。
 *
 * <p>设计依据：《电子招标投标全流程说明书》§6；《实施条例》第19条
 * （未通过资格预审的申请人不具有投标资格；通过者<3家应重新招标）。
 */
public interface PrequalApplyService {

    /**
     * 提交资格预审申请（投标方；申请截止前可提交，同一主体幂等）。
     *
     * @param reqVO 申请入参
     * @return 申请ID
     */
    Long apply(PrequalApplyReqVO reqVO);

    /**
     * 审查资格预审申请（资格审查委员会；通过/不通过）。
     *
     * @param reqVO 审查入参
     */
    void review(PrequalReviewReqVO reqVO);

    /**
     * 按资格预审查申请列表（资格审查用）。
     *
     * @param prequalId 资格预审ID
     * @return 申请列表
     */
    List<PrequalApplyDO> listByPrequal(Long prequalId);

    /**
     * 申请分页。
     *
     * @param reqVO 分页入参
     * @return 分页结果
     */
    PageResult<PrequalApplyDO> page(PrequalApplyPageReqVO reqVO);
}
