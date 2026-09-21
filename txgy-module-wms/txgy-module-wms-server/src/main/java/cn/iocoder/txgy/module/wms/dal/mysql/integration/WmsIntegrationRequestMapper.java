package cn.iocoder.txgy.module.wms.dal.mysql.integration;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.wms.dal.dataobject.integration.WmsIntegrationRequestDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WmsIntegrationRequestMapper extends BaseMapperX<WmsIntegrationRequestDO> {

    default WmsIntegrationRequestDO selectByRequestId(String requestId) {
        return selectOne(new LambdaQueryWrapperX<WmsIntegrationRequestDO>()
                .eq(WmsIntegrationRequestDO::getRequestId, requestId));
    }

    default WmsIntegrationRequestDO selectSourceRequest(String operationType, String aggregateNo) {
        return selectOne(new LambdaQueryWrapperX<WmsIntegrationRequestDO>()
                .eq(WmsIntegrationRequestDO::getOperationType, operationType)
                .eq(WmsIntegrationRequestDO::getAggregateNo, aggregateNo)
                .orderByAsc(WmsIntegrationRequestDO::getId)
                .last("LIMIT 1"));
    }

    /** 重复插入等待另一事务提交后，使用当前读，避免 MySQL REPEATABLE READ 的旧快照。 */
    default WmsIntegrationRequestDO selectByRequestIdCurrent(String requestId) {
        com.baomidou.mybatisplus.annotation.DbType type =
                cn.iocoder.txgy.framework.mybatis.core.util.JdbcUtils.getDbType();
        // MySQL 重复 INSERT 已持有共享锁，重放只读结果，无需升级独占锁。
        String lock = type == com.baomidou.mybatisplus.annotation.DbType.MYSQL
                || type == com.baomidou.mybatisplus.annotation.DbType.MARIADB
                ? "LOCK IN SHARE MODE" : "FOR UPDATE";
        return selectOne(new LambdaQueryWrapperX<WmsIntegrationRequestDO>()
                .eq(WmsIntegrationRequestDO::getRequestId, requestId)
                .last(lock));
    }
}
