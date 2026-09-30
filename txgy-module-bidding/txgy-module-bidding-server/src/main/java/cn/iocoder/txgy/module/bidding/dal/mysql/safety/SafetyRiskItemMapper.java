package cn.iocoder.txgy.module.bidding.dal.mysql.safety;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.safety.SafetyRiskItemDO;
import cn.iocoder.txgy.module.bidding.enums.SafetyRiskItemStatusEnum;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 安许风险条目工单 Mapper（契约 Q18/Q19）。
 *
 * <p>为什么这样写：① 生成工单要去重——同 permit 下未闭环（PENDING/ASSIGNED/HANDLING）工单存在时不在扫描时重复生成；
 * ② 处理时清点"是否有未闭环工单"，供重新扫描口径（Q20）判断。
 */
@Mapper
public interface SafetyRiskItemMapper extends BaseMapperX<SafetyRiskItemDO> {

    /** 未闭环工单（PENDING/ASSIGNED/HANDLING），供扫描生成去重与大盘统计。 */
    default List<SafetyRiskItemDO> selectOpenByPermit(Long permitId) {
        return selectList(new LambdaQueryWrapper<SafetyRiskItemDO>()
                .eq(SafetyRiskItemDO::getPermitId, permitId)
                .in(SafetyRiskItemDO::getStatus, SafetyRiskItemStatusEnum.OPEN_STATES));
    }

    /** 某企业未闭环工单（风险复现提示用）。 */
    default List<SafetyRiskItemDO> selectOpenByEnt(Long entId) {
        return selectList(new LambdaQueryWrapper<SafetyRiskItemDO>()
                .eq(SafetyRiskItemDO::getEntId, entId)
                .in(SafetyRiskItemDO::getStatus, SafetyRiskItemStatusEnum.OPEN_STATES));
    }
}
