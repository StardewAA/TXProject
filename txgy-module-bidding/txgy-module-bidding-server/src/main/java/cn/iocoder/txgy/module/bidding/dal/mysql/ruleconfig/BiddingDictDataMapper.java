package cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.ruleconfig.BiddingDictDataDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 业务字典 Mapper。
 *
 * <p>规范出处：契约《电子证照模块-前端接口契约-v1.0.md》§1.1（字典读取）；DDL bidding_dict_data。
 * <p>设计遵循/针对：查询命中 `uk_dict`/`idx_dict_type`；只取 enabled 行，停用的枚举不下发（前端立即不再展示），
 * 但历史数据仍保留其 code（不删行），避免旧数据变成"未知 code"。
 * <p>业务目标：字典读取走索引且只回启用项；前端下拉与标签始终与后台配置一致。
 */
@Mapper
public interface BiddingDictDataMapper extends BaseMapperX<BiddingDictDataDO> {

    /** 取某租户下指定类型的启用枚举（按 sort 升序）。 */
    default List<BiddingDictDataDO> selectEnabledByType(Long tenantId, String dictType) {
        return selectList(new LambdaQueryWrapper<BiddingDictDataDO>()
                .eq(BiddingDictDataDO::getTenantId, tenantId)
                .eq(BiddingDictDataDO::getDictType, dictType)
                .eq(BiddingDictDataDO::getEnabled, true)
                .orderByAsc(BiddingDictDataDO::getSort));
    }

    /** 取某租户下多个类型的启用枚举（一次取回，避免前端按类型多次往返）。 */
    default List<BiddingDictDataDO> selectEnabledByTypes(Long tenantId, List<String> dictTypes) {
        return selectList(new LambdaQueryWrapper<BiddingDictDataDO>()
                .eq(BiddingDictDataDO::getTenantId, tenantId)
                .in(BiddingDictDataDO::getDictType, dictTypes)
                .eq(BiddingDictDataDO::getEnabled, true)
                .orderByAsc(BiddingDictDataDO::getDictType)
                .orderByAsc(BiddingDictDataDO::getSort));
    }
}
