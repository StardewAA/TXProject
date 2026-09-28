package cn.iocoder.txgy.module.bidding.dal.mysql.ruleconfig;

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.tenant.core.util.TenantUtils;
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
 *
 * <p><b>2026-09-17 修复（标书审查管理端改造中发现）</b>：三个查询都带显式的 tenant_id 参数，
 * 但多租户拦截器仍会再追加一条 `tenant_id = 当前租户`，两个条件相与后**平台默认行（tenant_id=0）恒为 0 行**。
 * 后果：/bidding/meta/dicts 只回"枚举兜底"（label 退化成 code，sort=999），
 * 运营改字典不生效、前端下拉显示英文 code，连 `?types=xxx` 都会因"该类型未在字典表中出现"而报 1060108008。
 * 因此三个方法改为经 {@link TenantUtils#executeIgnore} 关闭租户过滤——租户维度已由入参 tenantId 显式表达。
 */
@Mapper
public interface BiddingDictDataMapper extends BaseMapperX<BiddingDictDataDO> {

    /** 取某租户下指定类型的启用枚举（按 sort 升序）。 */
    default List<BiddingDictDataDO> selectEnabledByType(Long tenantId, String dictType) {
        return TenantUtils.executeIgnore(() -> selectList(new LambdaQueryWrapper<BiddingDictDataDO>()
                .eq(BiddingDictDataDO::getTenantId, tenantId)
                .eq(BiddingDictDataDO::getDictType, dictType)
                .eq(BiddingDictDataDO::getEnabled, true)
                .orderByAsc(BiddingDictDataDO::getSort)));
    }

    /**
     * 取某租户下**已配置**的字典类型清单（去重、按类型名升序）。
     *
     * <p>规范出处：契约 §1.1（字典读取）+ 用户硬约束"枚举值必须支持动态配置"。
     * <p>为什么这样写：类型是否"受支持"应以字典表里真实配置为准——cert_type / holder_type / review_result
     * 这类字典的取值是业务字面量集合，代码里没有枚举类，旧实现仅凭枚举白名单判定，导致种了数据也取不到。
     * <p>业务目标：新增一类字典只需插数据即可下发，不必为每种字典补一个枚举类；
     * 同时 `/meta/dicts` 不带 types 时也能把纯字典类型一并带出。
     */
    default List<String> selectEnabledTypes(Long tenantId) {
        return TenantUtils.executeIgnore(() -> selectList(new LambdaQueryWrapper<BiddingDictDataDO>()
                .select(BiddingDictDataDO::getDictType)
                .eq(BiddingDictDataDO::getTenantId, tenantId)
                .eq(BiddingDictDataDO::getEnabled, true)
                .groupBy(BiddingDictDataDO::getDictType)
                .orderByAsc(BiddingDictDataDO::getDictType)))
                .stream().map(BiddingDictDataDO::getDictType).distinct().toList();
    }

    /** 取某租户下多个类型的启用枚举（一次取回，避免前端按类型多次往返）。 */
    default List<BiddingDictDataDO> selectEnabledByTypes(Long tenantId, List<String> dictTypes) {
        return TenantUtils.executeIgnore(() -> selectList(new LambdaQueryWrapper<BiddingDictDataDO>()
                .eq(BiddingDictDataDO::getTenantId, tenantId)
                .in(BiddingDictDataDO::getDictType, dictTypes)
                .eq(BiddingDictDataDO::getEnabled, true)
                .orderByAsc(BiddingDictDataDO::getDictType)
                .orderByAsc(BiddingDictDataDO::getSort)));
    }
}
