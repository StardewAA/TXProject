package cn.iocoder.txgy.module.bidding.dal.mysql.qualification;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationComplianceReviewDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 达标核查（只追加）Mapper——评审留痕只 insert/select，不支持改删（防抵赖，15-6）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-6（只追加 · 防抵赖）。
 */
@Mapper
public interface QualificationComplianceReviewMapper extends BaseMapperX<QualificationComplianceReviewDO> {
}
