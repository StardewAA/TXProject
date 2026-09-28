package cn.iocoder.txgy.module.bidding.dal.mysql.subject;
// [依据] 模块开发规范 §3.4（Mapper 层规约：继承 BaseMapperX，条件用 LambdaQueryWrapperX 风格由 Service 组装）

import cn.iocoder.txgy.module.bidding.dal.dataobject.subject.SubjectAuthDO;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SubjectAuthMapper extends BaseMapperX<SubjectAuthDO> {
}
