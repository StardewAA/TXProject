package cn.iocoder.txgy.module.bidding.service.review;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;

/**
 * 纯 Mockito 单测的 MyBatis-Plus lambda 缓存初始化工具。
 *
 * <p>规范出处：MyBatis-Plus 3.5.x `TableInfoHelper`（lambda 列名解析依赖 TableInfo 缓存）。
 *
 * <p>为什么需要它：`LambdaQueryWrapper` / `LambdaUpdateWrapper` 用方法引用（如 `ReviewTaskDO::getEntId`）
 * 定位列名，解析时查的是 `TableInfoHelper` 的静态缓存；该缓存平时由 Spring 启动扫描 Mapper 时填充。
 * 单测直接 new Service（没有容器），缓存为空 ⇒ 一旦 wrapper 真正需要把方法引用翻译成列名
 * （`set(...)`、`getSqlSegment()`、`getParamNameValuePairs()` 等），就会抛
 * `MybatisPlusException: can not find lambda cache for this entity`。
 *
 * <p>关键差别（这也是先前踩坑的原因）：**只构造 wrapper 不触发列名解析就不会报错**，
 * 所以"能构造"不等于"能断言"；想断言 wrapper 内容就必须先调本工具。
 *
 * <p>业务目标：让单测能对"查询条件到底按哪一列、哪个值过滤"做断言 ——
 * 审查任务中心曾因过滤口径写成 tenant_id 而非企业主体 id，产生**不报错的空列表**缺陷，
 * 只有这类断言才拦得住。
 */
final class MybatisPlusLambdaCache {

    private MybatisPlusLambdaCache() {
    }

    /** 为给定实体注册 TableInfo，使 lambda 方法引用可被解析为列名。 */
    static void init(Class<?>... entities) {
        // MapperBuilderAssistant 需要一个 Configuration；resource 传空串即可（不落 Mapper 注册表）。
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new Configuration(), "");
        for (Class<?> entity : entities) {
            if (TableInfoHelper.getTableInfo(entity) == null) {
                TableInfoHelper.initTableInfo(assistant, entity);
            }
        }
    }
}
