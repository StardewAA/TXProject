package cn.iocoder.txgy.module.report.dal.mysql.dataset;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.report.controller.admin.dataset.vo.ReportDatasetPageReqVO;
import cn.iocoder.txgy.module.report.dal.dataobject.dataset.ReportDatasetDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 报表数据集 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface ReportDatasetMapper extends BaseMapperX<ReportDatasetDO> {

    default PageResult<ReportDatasetDO> selectPage(ReportDatasetPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ReportDatasetDO>()
                .likeIfPresent(ReportDatasetDO::getCode, reqVO.getCode())
                .likeIfPresent(ReportDatasetDO::getName, reqVO.getName())
                .eqIfPresent(ReportDatasetDO::getCategory, reqVO.getCategory())
                .eqIfPresent(ReportDatasetDO::getStatus, reqVO.getStatus())
                .orderByDesc(ReportDatasetDO::getId));
    }

    /**
     * 分页查询「指定租户集合」的数据集
     *
     * 用于管理页同时展示本租户数据集与平台级数据集（tenant_id = 0）。
     * 需在 TenantUtils.executeIgnore 中调用，避免租户插件二次过滤。
     */
    default PageResult<ReportDatasetDO> selectPageByTenantIds(ReportDatasetPageReqVO reqVO, List<Long> tenantIds) {
        return selectPage(reqVO, new LambdaQueryWrapperX<ReportDatasetDO>()
                .in(ReportDatasetDO::getTenantId, tenantIds)
                .likeIfPresent(ReportDatasetDO::getCode, reqVO.getCode())
                .likeIfPresent(ReportDatasetDO::getName, reqVO.getName())
                .eqIfPresent(ReportDatasetDO::getCategory, reqVO.getCategory())
                .eqIfPresent(ReportDatasetDO::getStatus, reqVO.getStatus())
                .orderByDesc(ReportDatasetDO::getId));
    }

    default ReportDatasetDO selectByCode(String code) {
        return selectOne(ReportDatasetDO::getCode, code);
    }

}
