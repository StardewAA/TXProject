package cn.iocoder.txgy.module.mes.dal.mysql.pro.workorder;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.mes.controller.admin.pro.project.vo.MesProProjectStatisticsRespVO;
import cn.iocoder.txgy.module.mes.controller.admin.pro.workorder.vo.MesProWorkOrderPageReqVO;
import cn.iocoder.txgy.module.mes.dal.dataobject.pro.workorder.MesProWorkOrderDO;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;

/**
 * MES 生产工单 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface MesProWorkOrderMapper extends BaseMapperX<MesProWorkOrderDO> {

    default PageResult<MesProWorkOrderDO> selectPage(MesProWorkOrderPageReqVO reqVO) {
        LambdaQueryWrapperX<MesProWorkOrderDO> query = new LambdaQueryWrapperX<MesProWorkOrderDO>()
                .likeIfPresent(MesProWorkOrderDO::getCode, reqVO.getCode())
                .likeIfPresent(MesProWorkOrderDO::getName, reqVO.getName())
                .eqIfPresent(MesProWorkOrderDO::getType, reqVO.getType())
                .likeIfPresent(MesProWorkOrderDO::getOrderSourceCode, reqVO.getOrderSourceCode())
                .eqIfPresent(MesProWorkOrderDO::getProjectId, reqVO.getProjectId())
                .eqIfPresent(MesProWorkOrderDO::getProductId, reqVO.getProductId())
                .eqIfPresent(MesProWorkOrderDO::getClientId, reqVO.getClientId())
                .eqIfPresent(MesProWorkOrderDO::getStatus, reqVO.getStatus())
                .betweenIfPresent(MesProWorkOrderDO::getRequestDate, reqVO.getRequestDate());
        // 只查未挂接项目的工单（用于项目管理-关联工单弹窗）
        if (Boolean.TRUE.equals(reqVO.getNoProject())) {
            query.isNull(MesProWorkOrderDO::getProjectId);
        }
        return selectPage(reqVO, query.orderByDesc(MesProWorkOrderDO::getId));
    }

    default MesProWorkOrderDO selectByCode(String code) {
        return selectOne(MesProWorkOrderDO::getCode, code);
    }

    default void updateProducedQuantity(Long id, BigDecimal incrQuantityProduced) {
        update(null, new LambdaUpdateWrapper<MesProWorkOrderDO>()
                .eq(MesProWorkOrderDO::getId, id)
                .setSql("quantity_produced = IFNULL(quantity_produced, 0) + " + incrQuantityProduced));
    }

    default Long selectCountByVendorId(Long vendorId) {
        return selectCount(MesProWorkOrderDO::getVendorId, vendorId);
    }

    /**
     * 按项目统计生产工单数与生产进度（用于项目管理）
     *
     * 工单状态：0 草稿、1 已确认、2 已完成、3 已取消，参见 {@link MesProWorkOrderStatusEnum}
     *
     * @param projectIds 项目编号数组
     * @return 各项目的工单统计
     */
    @Select("<script>" +
            "SELECT project_id AS projectId, COUNT(*) AS workOrderTotal, " +
            "SUM(status = 0) AS prepareCount, SUM(status = 1) AS confirmedCount, " +
            "SUM(status = 2) AS finishedCount, SUM(status = 3) AS canceledCount, " +
            "IFNULL(SUM(quantity), 0) AS quantityTotal, " +
            "IFNULL(SUM(quantity_produced), 0) AS quantityProducedTotal " +
            "FROM mes_pro_work_order WHERE deleted = 0 AND project_id IN " +
            "<foreach collection='projectIds' item='projectId' open='(' separator=',' close=')'>#{projectId}</foreach> " +
            "GROUP BY project_id" +
            "</script>")
    List<MesProProjectStatisticsRespVO> selectProjectStatistics(
            @Param("projectIds") Collection<Long> projectIds);

}
