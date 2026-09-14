package cn.iocoder.txgy.module.bidding.dal.mysql.qualification;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.qualification.QualificationSafetyPermitDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDate;
import java.util.List;

/**
 * 安全生产许可证 Mapper（permit_no UK 判重 + QUAL_EXPIRY 扫描候选）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 表 15-8（UK(tenant, permit_no)）。
 */
@Mapper
public interface QualificationSafetyPermitMapper extends BaseMapperX<QualificationSafetyPermitDO> {

    default QualificationSafetyPermitDO selectByPermitNo(String permitNo) {
        return selectOne(new LambdaQueryWrapper<QualificationSafetyPermitDO>()
                .eq(QualificationSafetyPermitDO::getPermitNo, permitNo)
                .last("LIMIT 1"));
    }

    /** 未到期但进入预警窗口的安许（QUAL_EXPIRY 扫描源二）。 */
    default List<QualificationSafetyPermitDO> selectExpiringCandidates(LocalDate today, int maxDays) {
        return selectList(new LambdaQueryWrapper<QualificationSafetyPermitDO>()
                .gt(QualificationSafetyPermitDO::getValidTo, today)
                .le(QualificationSafetyPermitDO::getValidTo, today.plusDays(maxDays)));
    }
}
