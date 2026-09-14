package cn.iocoder.txgy.module.bidding.dal.dataobject.externalfetch;
// 规范依据：设计文档 v0.6 §15.1（G 轨：平台级聚合无租户语义）；《招投标模块开发规范》五章

import cn.iocoder.txgy.framework.mybatis.core.dataobject.BaseDO;
import cn.iocoder.txgy.framework.tenant.core.aop.TenantIgnore;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 信息获取源配置（获取方式：CRAWLER 爬虫 / API 接口直取；白名单源：URL/频控/通道/策略）（G 轨）。表：bidding_fetch_source。
 */
@TenantIgnore // G 轨：表无 tenant_id 列，忽略租户拦截器（平台级数据，见 DO 注释）
@TableName("bidding_fetch_source")
@Data
@EqualsAndHashCode(callSuper = true)
public class CrawlSourceDO extends BaseDO {

    @TableId(type = IdType.ASSIGN_ID) // 雪花主键：表 id 无自增（见 DDL），显式注解压过全局 AUTO 策略

    private Long id;                      // 主键（雪花 ID）
    private String sourceCode;            // 源 code（唯一）
    private String sourceName;            // 源名称
    private String baseUrl;               // 站点域名/入口
    private String allowedHosts;          // 额外放行域名（半角逗号分隔：附件 CDN/文件服务器等不在 base_url 域下的溯源域名；白名单=base_url 域名 ∪ 本列）
    private String entryUrlsJson;         // 入口/栏目 URL 列表（JSON 数组）
    private String channelCode;           // 下载通道适配器 code（通用或平台专属）
    private String fetchType;            // 获取方式：CRAWLER=爬虫(列表HTML)/API=接口直取(JSON)
    private String apiConfigJson;        // API 源配置 JSON（fetch_type=API 时：method/url/headers/params/page/rowsPath/fieldMapping）
    private String parserCodeDefault;     // 默认解析/策略码（API 源=字段映射/AI 策略码；URL 未单独审批时用）
    private Integer fetchIntervalSeconds; // 抓取间隔（秒，频控）
    private Boolean robotsOk;             // 是否允许按 robots 抓取
    private Boolean enabled;              // 启用开关
    private String remark;                // 备注
}
