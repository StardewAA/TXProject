package cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch;

// 规范依据：用户口径 2026-09-10「文件完整性与状态监测」。

import cn.iocoder.txgy.framework.mybatis.core.mapper.BaseMapperX;
import cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch.CrawlDownloadAuditDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 原文下载审计 Mapper（只追加；监控查询走 BaseMapperX 的 selectPage/selectList）。
 *
 * <p>注：@Mapper 必须显式标注——框架 @MapperScan 是 annotationClass=Mapper.class 的注解扫描
 * （见 {@code TxgyMybatisAutoConfiguration}），漏标不报编译错，只会在启动时"找不到 Mapper bean"。
 */
@Mapper
public interface CrawlDownloadAuditMapper extends BaseMapperX<CrawlDownloadAuditDO> {
}
