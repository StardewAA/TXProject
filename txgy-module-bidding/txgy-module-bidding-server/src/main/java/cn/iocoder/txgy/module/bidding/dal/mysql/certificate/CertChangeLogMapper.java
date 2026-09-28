package cn.iocoder.txgy.module.bidding.dal.mysql.certificate;

import cn.iocoder.txgy.framework.common.pojo.PageResult;
import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.framework.mybatis.core.query.LambdaQueryWrapperX;
import cn.iocoder.txgy.module.bidding.controller.admin.certificate.vo.CertChangeLogPageReqVO;
import cn.iocoder.txgy.module.bidding.dal.dataobject.certificate.CertChangeLogDO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 证照变更审计日志 Mapper（只追加；哈希链追加由 CertHashChain 驱动）。
 *
 * <p>规范出处：《电子证件全流程管理_数据库设计.md》v3.3 §18.2 变更记录只追加审计；
 * 设计遵循/针对：全量快照存 prev/next 字段 + 链式 batch_id 支撑哈希链回验（GB/T 38540 事件存证思路）。
 */
@Mapper
public interface CertChangeLogMapper extends BaseMapperX<CertChangeLogDO> {

    /**
     * 取该证照最近一条日志的 record_hash（哈希链前驱；无则 null → 链首 ROOT）。
     * <p>出处：v3.3 §13 哈希链（prev_hash 链接）＋v3.4 §18.3-1；按 id 倒序取最新一条。
     */
    default String selectLastRecordHash(Long certId) {
        CertChangeLogDO last = selectOne(new LambdaQueryWrapper<CertChangeLogDO>()
                .eq(CertChangeLogDO::getCertId, certId)
                .orderByDesc(CertChangeLogDO::getId)
                .last("LIMIT 1"));
        return last == null ? null : last.getRecordHash();
    }

    /**
     * 变更留痕分页（按 certId 必查，时间倒序：最新变更在前，契合"查看历史"的阅读顺序）。
     *
     * <p>排序为什么用 id 倒序而不是 create_time：同一次操作会写入多行（字段级留痕），
     * create_time 精度到秒会**并列**，排序不稳定；id 是雪花号，同批内天然保序。
     */
    default PageResult<CertChangeLogDO> selectPage(CertChangeLogPageReqVO reqVO) {
        return selectPage(reqVO, new LambdaQueryWrapperX<CertChangeLogDO>()
                .eq(CertChangeLogDO::getCertId, reqVO.getCertId())
                .eqIfPresent(CertChangeLogDO::getChangeType, reqVO.getChangeType())
                .eqIfPresent(CertChangeLogDO::getFieldName, reqVO.getFieldName())
                .eqIfPresent(CertChangeLogDO::getBatchId, reqVO.getBatchId())
                .orderByDesc(CertChangeLogDO::getId));
    }
}
