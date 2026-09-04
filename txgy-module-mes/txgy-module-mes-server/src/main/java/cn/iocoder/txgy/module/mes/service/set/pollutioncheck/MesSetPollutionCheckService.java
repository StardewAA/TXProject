package cn.iocoder.txgy.module.mes.service.set.pollutioncheck;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckAiRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckReviewReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckSaveReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesSetPollutionCheckDO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesPollutionCheckLogDO;
import jakarta.validation.Valid;

import java.util.List;

/**
 * MES 安全环保检测-污染判定记录 Service 接口
 *
 * @author OPENLAB BS
 */
public interface MesSetPollutionCheckService {

    /**
     * 创建污染判定记录（AI 初筛自动回填，落为待复核）
     *
     * @param createReqVO 创建信息
     * @return 编号
     */
    Long createPollutionCheck(@Valid MesSetPollutionCheckSaveReqVO createReqVO);

    /**
     * 更新污染判定记录（仅待复核；重新 AI 初筛）
     *
     * @param updateReqVO 更新信息
     */
    void updatePollutionCheck(@Valid MesSetPollutionCheckSaveReqVO updateReqVO);

    /**
     * 删除污染判定记录（仅待复核）
     *
     * @param id 编号
     */
    void deletePollutionCheck(Long id);

    /**
     * 人工复核（终态二值：CLEAN/POLLUTED，收口 AI 的不确定）
     *
     * @param reviewReqVO 复核信息
     */
    void reviewPollutionCheck(@Valid MesSetPollutionCheckReviewReqVO reviewReqVO);

    /**
     * AI 初筛预览（不落库，供录入界面提前展示建议）
     *
     * @param reqVO 基础信息
     * @return AI 建议
     */
    MesSetPollutionCheckAiRespVO prescreenPollutionCheck(@Valid MesSetPollutionCheckSaveReqVO reqVO);

    /**
     * 获得污染判定记录
     *
     * @param id 编号
     * @return 污染判定记录
     */
    MesSetPollutionCheckDO getPollutionCheck(Long id);

    /**
     * 获得污染判定记录分页
     *
     * @param pageReqVO 分页查询
     * @return 污染判定记录分页
     */
    PageResult<MesSetPollutionCheckDO> getPollutionCheckPage(MesSetPollutionCheckPageReqVO pageReqVO);

    /**
     * 获得某条污染判定记录的完整履历（创建→改单→复核，时间正序；AI/人工结果快照只增不改）
     *
     * @param checkId 判定记录ID
     * @return 履历
     */
    List<MesPollutionCheckLogDO> getPollutionCheckLogList(Long checkId);

}
