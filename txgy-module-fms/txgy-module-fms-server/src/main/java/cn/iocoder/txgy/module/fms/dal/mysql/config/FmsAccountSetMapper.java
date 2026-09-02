package cn.iocoder.txgy.module.fms.dal.mysql.config;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.fms.dal.dataobject.config.FmsAccountSetDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * FMS 账套 Mapper
 *
 * @author OPENLAB BS
 */
@Mapper
public interface FmsAccountSetMapper extends BaseMapperX<FmsAccountSetDO> {

    default FmsAccountSetDO selectByCompanyCode(String companyCode) {
        return selectOne(FmsAccountSetDO::getCompanyCode, companyCode);
    }

    default FmsAccountSetDO selectByIdForUpdate(Long id) {
        return selectOneForUpdate(FmsAccountSetDO::getId, id);
    }

}
