package cn.iocoder.txgy.module.mes.dal.mysql.pro.feedback;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.feedback.vo.MesProFeedbackPageReqVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectFeedbackStatisticsRespVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.feedback.MesProFeedbackDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * MES 生产报工 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProFeedbackMapper extends BaseMapperX<MesProFeedbackDO> {

    /**
     * 按项目聚合报工统计（报工单数、状态分布、报工总量、合格/不合格数量与合格率）
     *
     * 报工状态：0 草稿、2 审核中、3 待检验、4 已完成、5 已取消，参见 MesProFeedbackStatusEnum
     *
     * @param projectIds 项目编号数组
     * @return 各项目的报工统计
     */
    @Select("<script>" +
            "SELECT w.project_id AS projectId, COUNT(*) AS feedbackTotal, " +
            "SUM(f.status = 0) AS feedbackPrepareCount, SUM(f.status = 2) AS feedbackApprovingCount, " +
            "SUM(f.status = 4) AS feedbackFinishedCount, " +
            "IFNULL(SUM(f.feedback_quantity), 0) AS feedbackQuantityTotal, " +
            "IFNULL(SUM(f.qualified_quantity), 0) AS qualifiedQuantityTotal, " +
            "IFNULL(SUM(f.unqualified_quantity), 0) AS unqualifiedQuantityTotal, " +
            "ROUND(IFNULL(SUM(f.qualified_quantity), 0) / NULLIF(SUM(f.qualified_quantity) + SUM(f.unqualified_quantity), 0) * 100, 1) AS qualifyRate " +
            "FROM mes_pro_feedback f " +
            "INNER JOIN mes_pro_work_order w ON w.id = f.work_order_id AND w.deleted = 0 " +
            "WHERE f.deleted = 0 AND w.project_id IN " +
            "<foreach collection='projectIds' item='projectId' open='(' separator=',' close=')'>#{projectId}</foreach> " +
            "GROUP BY w.project_id" +
            "</script>")
    List<MesProProjectFeedbackStatisticsRespVO> selectFeedbackStatisticsByProjectIds(
            @Param("projectIds") Collection<Long> projectIds);

    default PageResult<MesProFeedbackDO> selectPage(MesProFeedbackPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MesProFeedbackDO>()
                .likeIfPresent(MesProFeedbackDO::getCode, reqVO.getCode())
                .eqIfPresent(MesProFeedbackDO::getType, reqVO.getType())
                .eqIfPresent(MesProFeedbackDO::getWorkOrderId, reqVO.getWorkOrderId())
                .eqIfPresent(MesProFeedbackDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MesProFeedbackDO::getFeedbackTime, reqVO.getFeedbackTime())
                .eqIfPresent(MesProFeedbackDO::getItemId, reqVO.getItemId())
                .eqIfPresent(MesProFeedbackDO::getFeedbackUserId, reqVO.getFeedbackUserId())
                .eqIfPresent(MesProFeedbackDO::getCreator, reqVO.getCreator())
                .orderByDesc(MesProFeedbackDO::getId));
    }

}
