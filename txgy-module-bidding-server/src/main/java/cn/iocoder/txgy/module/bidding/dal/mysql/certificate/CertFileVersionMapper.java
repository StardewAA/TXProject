package cn.iocoder.txgy.module.bidding.dal.mysql.certificate;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertFileVersionDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 证照文件版本 Mapper。
 */
@Mapper
public interface CertFileVersionMapper extends BaseMapperX<CertFileVersionDO> {
}
