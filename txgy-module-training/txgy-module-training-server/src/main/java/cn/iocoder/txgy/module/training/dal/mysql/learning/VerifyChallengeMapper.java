package cn.iocoder.txgy.module.training.dal.mysql.learning;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.VerifyChallengeDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 学习防挂机验证挑战 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface VerifyChallengeMapper extends BaseMapperX<VerifyChallengeDO> {

    default List<VerifyChallengeDO> selectListBySessionId(Long sessionId) {
        return selectList(new LambdaQueryWrapperX<VerifyChallengeDO>()
                .eq(VerifyChallengeDO::getSessionId, sessionId)
                .orderByAsc(VerifyChallengeDO::getId));
    }

}
