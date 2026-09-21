package cn.iocoder.txgy.module.member.dal.mysql.point;

import cn.iocoder.txgy.framework.common.pojo.PageParam;
import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.member.controller.admin.point.vo.recrod.MemberPointRecordPageReqVO;
import cn.iocoder.txgy.module.member.controller.app.point.vo.AppMemberPointRecordPageReqVO;
import cn.iocoder.txgy.module.member.dal.dataobject.point.MemberPointRecordDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Set;

/**
 * 用户积分记录 Mapper
 *
 * @author QingX
 */
@Mapper
public interface MemberPointRecordMapper extends BaseMapperX<MemberPointRecordDO> {

    /**
     * 会员行锁之后查询持久化业务事实，使用当前读避免 REPEATABLE READ 的旧快照漏掉并发已提交流水。
     * 故意包含逻辑删除记录：隐藏流水不代表积分没有到账；历史重复记录保留供人工对账。
     */
    @Select("SELECT id, user_id, biz_id, biz_type, point, total_point, tenant_id FROM member_point_record "
            + "WHERE tenant_id = #{tenantId} AND user_id = #{userId} "
            + "AND biz_type = #{bizType} AND biz_id = #{bizId} FOR UPDATE")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    List<MemberPointRecordDO> selectBusinessRecordsForUpdate(@Param("tenantId") Long tenantId,
            @Param("userId") Long userId, @Param("bizType") Integer bizType, @Param("bizId") String bizId);

    default PageResult<MemberPointRecordDO> selectPage(MemberPointRecordPageReqVO reqVO, Set<Long> userIds) {
        return selectPage(reqVO, new LambdaQueryWrapperX<MemberPointRecordDO>()
                .inIfPresent(MemberPointRecordDO::getUserId, userIds)
                .eqIfPresent(MemberPointRecordDO::getUserId, reqVO.getUserId())
                .eqIfPresent(MemberPointRecordDO::getBizType, reqVO.getBizType())
                .likeIfPresent(MemberPointRecordDO::getTitle, reqVO.getTitle())
                .orderByDesc(MemberPointRecordDO::getId));
    }

    default PageResult<MemberPointRecordDO> selectPage(Long userId, AppMemberPointRecordPageReqVO pageReqVO) {
        return selectPage(pageReqVO, new LambdaQueryWrapperX<MemberPointRecordDO>()
                .eq(MemberPointRecordDO::getUserId, userId)
                .betweenIfPresent(MemberPointRecordDO::getCreateTime, pageReqVO.getCreateTime())
                .gt(Boolean.TRUE.equals(pageReqVO.getAddStatus()),
                        MemberPointRecordDO::getPoint, 0)
                .lt(Boolean.FALSE.equals(pageReqVO.getAddStatus()),
                        MemberPointRecordDO::getPoint, 0)
                .orderByDesc(MemberPointRecordDO::getId));
    }

}
