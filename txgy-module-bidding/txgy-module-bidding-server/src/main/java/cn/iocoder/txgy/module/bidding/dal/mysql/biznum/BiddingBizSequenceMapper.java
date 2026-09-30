package cn.iocoder.txgy.module.bidding.dal.mysql.biznum;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 内部业务编号序列 Mapper（表 bidding_biz_sequence，G 轨全局）。
 *
 * <p>原子递增实现：INSERT ... ON DUPLICATE KEY UPDATE seq = LAST_INSERT_ID(seq + 1)，
 * 命中主键 (biz_type, scope_key) 时在同一语句内自增并写入 last_insert_id，
 * 随后立即 SELECT 取走。全程无应用侧读-改-写窗口，多实例并发不重号。
 * <p>纯自定义 SQL、不继承 BaseMapperX：本表无 DO/无租户拦截器诉求，且被调时需同一连接取 LAST_INSERT_ID，
 * 走 MP 会引入不必要的拼装；G 轨全局序列不参与租户过滤（内部编号全局唯一）。
 */
@Mapper
public interface BiddingBizSequenceMapper {

    /**
     * 原子递增：桶不存在 → 创建并置 1；已存在 → seq+1 并写入 last_insert_id。
     */
    @Insert("INSERT INTO bidding_biz_sequence (biz_type, scope_key, seq, create_time, update_time) "
            + "VALUES (#{bizType}, #{scopeKey}, LAST_INSERT_ID(1), NOW(), NOW()) "
            + "ON DUPLICATE KEY UPDATE seq = LAST_INSERT_ID(seq + 1), update_time = NOW()")
    void upsertAndIncrement(@Param("bizType") String bizType, @Param("scopeKey") String scopeKey);

    /** 取 last_insert_id（必须紧接 upsertAndIncrement，同一连接/事务内）。 */
    @Select("SELECT LAST_INSERT_ID()")
    Long selectLastInsertId();
}
