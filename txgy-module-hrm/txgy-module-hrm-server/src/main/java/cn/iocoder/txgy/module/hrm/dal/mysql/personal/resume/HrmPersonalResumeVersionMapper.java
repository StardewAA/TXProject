package cn.iocoder.txgy.module.hrm.dal.mysql.personal.resume;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.hrm.dal.dataobject.personal.resume.HrmPersonalResumeVersionDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface HrmPersonalResumeVersionMapper extends BaseMapperX<HrmPersonalResumeVersionDO> {

    default List<HrmPersonalResumeVersionDO> selectListByUserId(Long userId, Long sourceResumeId) {
        return selectList(new LambdaQueryWrapperX<HrmPersonalResumeVersionDO>()
                .eq(HrmPersonalResumeVersionDO::getUserId, userId)
                .eqIfPresent(HrmPersonalResumeVersionDO::getSourceResumeId, sourceResumeId)
                .orderByDesc(HrmPersonalResumeVersionDO::getVersionNo)
                .orderByDesc(HrmPersonalResumeVersionDO::getId));
    }

    default HrmPersonalResumeVersionDO selectByIdAndUserId(Long id, Long userId) {
        return selectOne(new LambdaQueryWrapperX<HrmPersonalResumeVersionDO>()
                .eq(HrmPersonalResumeVersionDO::getId, id)
                .eq(HrmPersonalResumeVersionDO::getUserId, userId));
    }

    /** 幂等查询：同一用户同一幂等键只应存在一个版本 */
    default HrmPersonalResumeVersionDO selectByUserIdAndIdempotencyKey(Long userId, String idempotencyKey) {
        return selectOne(new LambdaQueryWrapperX<HrmPersonalResumeVersionDO>()
                .eq(HrmPersonalResumeVersionDO::getUserId, userId)
                .eq(HrmPersonalResumeVersionDO::getIdempotencyKey, idempotencyKey));
    }

    /** 该用户当前的最大版本号，用于分配下一个版本号 */
    default Integer selectMaxVersionNoByUserId(Long userId) {
        return selectList(new LambdaQueryWrapperX<HrmPersonalResumeVersionDO>()
                .eq(HrmPersonalResumeVersionDO::getUserId, userId)
                .orderByDesc(HrmPersonalResumeVersionDO::getVersionNo))
                .stream().findFirst().map(HrmPersonalResumeVersionDO::getVersionNo).orElse(null);
    }

    default List<HrmPersonalResumeVersionDO> selectListByIdsAndUserId(List<Long> ids, Long userId) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return selectList(new LambdaQueryWrapperX<HrmPersonalResumeVersionDO>()
                .in(HrmPersonalResumeVersionDO::getId, ids)
                .eq(HrmPersonalResumeVersionDO::getUserId, userId));
    }
}
