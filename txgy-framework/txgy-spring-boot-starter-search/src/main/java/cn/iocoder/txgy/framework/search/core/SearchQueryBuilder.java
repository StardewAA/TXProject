package cn.iocoder.txgy.framework.search.core;

/**
 * 查询构造契约。实现时必须对用户输入转义，限定过滤字段白名单，
 * 并将服务端提供的租户条件与其他条件以 AND 组合。
 */
public interface SearchQueryBuilder {

    String build(SearchRequest request);

}
