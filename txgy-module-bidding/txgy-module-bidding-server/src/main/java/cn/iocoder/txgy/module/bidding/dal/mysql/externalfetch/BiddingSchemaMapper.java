package cn.iocoder.txgy.module.bidding.dal.mysql.externalfetch;

// 规范依据：《招投标模块开发规范》§4.2（dal/mysql 层只做数据访问）；用户反馈 2026-09-11：
//   「Table 'ruoyi-vue-pro.bidding_crawl_download_gate' doesn't exist …… 所有任务都卡在这里反复重试」——
//   缺表属于"部署期问题"，必须在启动时就说清楚，而不是让业务在运行期反复失败。

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 模块库表存在性查询（供启动自检使用，只读 information_schema，不碰业务数据）。
 *
 * <p>用途：启动时一次性盘点本模块业务表是否齐备；缺失时由 {@code BiddingSchemaHealthCheck}
 * 打印"该执行哪个 SQL"并（爬虫关键表缺失时）停掉爬虫调度。
 *
 * <p>为什么查 information_schema 而不是 {@code SELECT 1 FROM 表 LIMIT 1}：缺表时后者会抛
 * BadSqlGrammarException，要靠解析异常文本判断缺哪张；前者一次查询即可拿到齐备清单，
 * 且能一次性列出**所有**缺失表（而不是只报第一张）。
 */
// 注：@Mapper 必须显式标注——框架 @MapperScan 是 annotationClass=Mapper.class 的注解扫描。
@Mapper
public interface BiddingSchemaMapper {

    /**
     * 查询给定表名中"当前库已存在"的部分。
     *
     * @param names 待检查的表名（小写，与建表语句一致）
     * @return 已存在的表名（小写）
     */
    @Select("<script>SELECT LOWER(table_name) FROM information_schema.tables "
            + "WHERE table_schema = DATABASE() AND LOWER(table_name) IN "
            + "<foreach collection='names' item='n' open='(' separator=',' close=')'>#{n}</foreach>"
            + "</script>")
    List<String> selectExistingTables(@Param("names") Collection<String> names);

}
