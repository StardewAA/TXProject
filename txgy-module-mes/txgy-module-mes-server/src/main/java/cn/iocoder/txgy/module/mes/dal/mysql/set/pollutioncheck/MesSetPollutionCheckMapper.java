package cn.iocoder.txgy.module.mes.dal.mysql.set.pollutioncheck;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.set.pollutioncheck.vo.MesSetPollutionCheckPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.set.pollutioncheck.MesSetPollutionCheckDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * MES 安全环保检测-污染判定记录 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesSetPollutionCheckMapper extends BaseMapperX<MesSetPollutionCheckDO> {

    default PageResult<MesSetPollutionCheckDO> selectPage(MesSetPollutionCheckPageReqVO reqVO) {
        LambdaQueryWrapperX<MesSetPollutionCheckDO> query = new LambdaQueryWrapperX<MesSetPollutionCheckDO>()
                .eqIfPresent(MesSetPollutionCheckDO::getStage, reqVO.getStage())
                .eqIfPresent(MesSetPollutionCheckDO::getBizNo, reqVO.getBizNo())
                .eqIfPresent(MesSetPollutionCheckDO::getBatchNo, reqVO.getBatchNo())
                .eqIfPresent(MesSetPollutionCheckDO::getAiResult, reqVO.getAiResult())
                .orderByDesc(MesSetPollutionCheckDO::getId);
        // 物料名称模糊
        if (reqVO.getItemName() != null && !reqVO.getItemName().isEmpty()) {
            query.like(MesSetPollutionCheckDO::getItemName, reqVO.getItemName());
        }
        // 判定状态过滤：null=全部，true=已复核，false=待复核
        if (reqVO.getReviewed() != null) {
            if (reqVO.getReviewed()) {
                query.isNotNull(MesSetPollutionCheckDO::getReviewResult);
            } else {
                query.isNull(MesSetPollutionCheckDO::getReviewResult);
            }
        }
        return selectPage(reqVO, query);
    }

}
