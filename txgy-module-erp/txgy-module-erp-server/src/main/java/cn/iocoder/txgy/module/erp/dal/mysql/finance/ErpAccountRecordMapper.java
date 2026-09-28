package cn.iocoder.txgy.module.erp.dal.mysql.finance;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.erp.dal.dataobject.finance.ErpAccountRecordDO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ErpAccountRecordMapper extends BaseMapperX<ErpAccountRecordDO> {

    default ErpAccountRecordDO selectByBiz(String bizType, Long bizId) {
        return selectOne(ErpAccountRecordDO::getBizType, bizType,
                ErpAccountRecordDO::getBizId, bizId);
    }

    default List<ErpAccountRecordDO> selectListByAccountId(Long accountId) {
        return selectList(new LambdaQueryWrapperX<ErpAccountRecordDO>()
                .eq(ErpAccountRecordDO::getAccountId, accountId)
                .orderByDesc(ErpAccountRecordDO::getId)
                .last("LIMIT 200"));
    }

}
