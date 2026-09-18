package cn.iocoder.txgy.module.infra.dal.mysql.worklog;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.infra.controller.admin.worklog.vo.WorkLogPageReqVO;
import cn.iocoder.txgy.module.infra.dal.dataobject.worklog.WorkLogDO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface WorkLogMapper extends BaseMapperX<WorkLogDO> {
    default PageResult<WorkLogDO> page(WorkLogPageReqVO req, Long tenantId, Long actorId) {
        var query = new LambdaQueryWrapperX<WorkLogDO>()
                .eq(WorkLogDO::getTenantId, tenantId)
                .eqIfPresent(WorkLogDO::getUserId, actorId)
                .leIfPresent(WorkLogDO::getId, req.getMaxId())
                .eqIfPresent(WorkLogDO::getModule, req.getModule())
                .eqIfPresent(WorkLogDO::getAction, req.getAction())
                .betweenIfPresent(WorkLogDO::getBeginTime, req.getBeginTime())
                .eq("SUCCESS".equals(req.getResultStatus()), WorkLogDO::getResultCode, 0)
                .ne("FAILURE".equals(req.getResultStatus()), WorkLogDO::getResultCode, 0);
        if (StrUtil.isNotBlank(req.getKeyword())) {
            String keyword = req.getKeyword();
            query.and(q -> q.like(WorkLogDO::getUserName, keyword)
                    .or().like(WorkLogDO::getBusinessKey, keyword)
                    .or().like(WorkLogDO::getSummary, keyword)
                    .or().like(WorkLogDO::getRequestParams, keyword));
        }
        query.orderByDesc(WorkLogDO::getBeginTime).orderByDesc(WorkLogDO::getId);
        return selectPage(req, query);
    }

    @Select("SELECT nickname FROM system_users WHERE id=#{userId} AND tenant_id=#{tenantId} LIMIT 1")
    String findUserName(@Param("userId") Long userId, @Param("tenantId") Long tenantId);

    @Select("SELECT COALESCE(MAX(id),0) FROM infra_work_log WHERE tenant_id=#{tenantId} AND deleted=0 AND (#{actorId,jdbcType=BIGINT} IS NULL OR user_id=#{actorId,jdbcType=BIGINT})")
    Long exportBoundary(@Param("tenantId") Long tenantId, @Param("actorId") Long actorId);
}
