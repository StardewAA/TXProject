package cn.iocoder.txgy.module.bidding.dal.mysql.qualification;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationApplyRecordDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 资质业务申报流 Mapper（办结回写主档走服务层事务编排；本 Mapper 提供基础 CRUD/条件查询）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-7（状态机 + 乐观锁）。
 */
@Mapper
public interface QualificationApplyRecordMapper extends BaseMapperX<QualificationApplyRecordDO> {
}
