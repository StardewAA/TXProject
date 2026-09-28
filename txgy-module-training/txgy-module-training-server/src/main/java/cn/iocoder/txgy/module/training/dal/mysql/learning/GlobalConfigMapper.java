package cn.iocoder.txgy.module.training.dal.mysql.learning;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.learning.GlobalConfigDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 学员学习中心 - 全局配置 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface GlobalConfigMapper extends BaseMapperX<GlobalConfigDO> {

    default GlobalConfigDO selectByKey(String configKey) {
        return selectOne(new LambdaQueryWrapperX<GlobalConfigDO>()
                .eq(GlobalConfigDO::getConfigKey, configKey)
                .last("limit 1"));
    }

    default List<GlobalConfigDO> selectListByKeys(List<String> configKeys) {
        return selectList(new LambdaQueryWrapperX<GlobalConfigDO>()
                .in(GlobalConfigDO::getConfigKey, configKeys));
    }

}
