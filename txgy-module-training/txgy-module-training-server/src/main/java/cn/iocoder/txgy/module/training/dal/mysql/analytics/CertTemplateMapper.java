package cn.iocoder.txgy.module.training.dal.mysql.analytics;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.training.dal.dataobject.analytics.CertTemplateDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 培训证书模板 Mapper
 *
 * @author 云培训学院课题小组
 */
@Mapper
public interface CertTemplateMapper extends BaseMapperX<CertTemplateDO> {
}
