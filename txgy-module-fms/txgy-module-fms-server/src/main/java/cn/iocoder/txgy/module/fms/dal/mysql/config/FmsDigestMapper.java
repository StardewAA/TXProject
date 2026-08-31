package cn.iocoder.txgy.module.fms.dal.mysql.config;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.fms.dal.dataobject.config.FmsDigestDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * FMS 常用摘要 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface FmsDigestMapper extends BaseMapperX<FmsDigestDO> {

    default List<FmsDigestDO> selectListByAccountSetId(Long accountSetId) {
        return selectList(new LambdaQueryWrapperX<FmsDigestDO>()
                .eq(FmsDigestDO::getAccountSetId, accountSetId)
                .orderByDesc(FmsDigestDO::getUpdateTime)
                .orderByDesc(FmsDigestDO::getId));
    }

}
