package cn.iocoder.txgy.module.bidding.service.biznum;

import cn.iocoder.txgy.framework.common.exception.util.ServiceExceptionUtil;
import cn.iocoder.txgy.module.bidding.dal.mysql.biznum.BiddingBizSequenceMapper;
import cn.iocoder.txgy.module.bidding.enums.ErrorCodeConstants;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * {@link BiddingBizNoGenerator} 实现。
 *
 * <p>独立式（generate，17 位）：前缀(1) + 区划(6) + 年度(4) + 36 进制序列(6)；
 * 层级式（generateChild，20/23 位）：父编号 + 36 进制序列(3)，编号自带"项目→招标项目→标段"追溯链。
 *
 * <p>序列原子递增：bidding_biz_sequence 按 (biz_type, scope_key) 联合主键分桶，
 * INSERT ... ON DUPLICATE KEY UPDATE + LAST_INSERT_ID 单语句自增，多实例并发不重号。
 * 独立式 scope_key = 年度_区划；层级式 scope_key = 父编号（17/20 位，≤32 列宽）。
 * <p>为什么序列用 36 进制：附录B.3.2 序列号段"0-9、a-z 的组合"；
 * 独立式 6 位 36^6≈21.7 亿、层级式 3 位 36^3≈4.66 万，逐年/按父分桶后远用不完。
 */
@Service
public class BiddingBizNoGeneratorImpl implements BiddingBizNoGenerator {

    /** 默认行政区划：陕西省级代码（无区划上下文时的兜底） */
    public static final String DEFAULT_REGION = "610000";
    /** 独立式序列最大值：36^6 - 1 */
    public static final long MAX_SEQ = 36L * 36 * 36 * 36 * 36 * 36 - 1;
    /** 层级式序列最大值：36^3 - 1（父编号下 46655 个足够） */
    public static final long MAX_CHILD_SEQ = 36L * 36 * 36 - 1;

    private static final DateTimeFormatter YEAR = DateTimeFormatter.ofPattern("yyyy");

    @Resource
    private BiddingBizSequenceMapper biddingBizSequenceMapper;

    @Override
    public String generate(BiddingBizNoTypeEnum type, String regionCode) {
        if (type.isChild()) {
            throw ServiceExceptionUtil.invalidParamException("编号类型 {0} 为层级式，请调用 generateChild(parentCode)", type.name());
        }
        String region = normalizeRegion(regionCode);
        String year = LocalDate.now().format(YEAR);
        Long seq = nextSeq(type, year + "_" + region);
        return buildNo(type.getPrefix(), region, year, seq);
    }

    @Override
    public String generateChild(BiddingBizNoTypeEnum type, String parentCode) {
        if (!type.isChild()) {
            throw ServiceExceptionUtil.invalidParamException("编号类型 {0} 为独立式，请调用 generate(regionCode)", type.name());
        }
        String parent = parentCode == null ? "" : parentCode.trim();
        if (parent.isEmpty()) {
            throw ServiceExceptionUtil.invalidParamException("层级编号（{0}）缺少父编号（parentCode）", type.getLabel());
        }
        Long seq = nextSeq(type, parent);
        return buildChildNo(parent, seq);
    }

    // ==================== 纯函数（单测覆盖，不依赖 DB） ====================

    /** 区划归一化：空/非法 → 默认省级；合法 6 位数字原样返回。 */
    static String normalizeRegion(String regionCode) {
        if (regionCode == null) {
            return DEFAULT_REGION;
        }
        String t = regionCode.trim();
        return t.matches("\\d{6}") ? t : DEFAULT_REGION;
    }

    /** 拼接 17 位独立编号：前缀 + 区划(6) + 年度(4) + 6 位 36 进制序列（左补 0）。 */
    static String buildNo(String prefix, String region, String year, long seq) {
        if (seq < 1 || seq > MAX_SEQ) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BIZ_NO_SEQ_EXCEEDED, Long.toString(seq));
        }
        String s = Long.toString(seq, 36);
        StringBuilder sb = new StringBuilder(17);
        sb.append(prefix).append(region).append(year);
        for (int i = s.length(); i < 6; i++) {
            sb.append('0');
        }
        sb.append(s);
        return sb.toString();
    }

    /** 拼接层级编号：父编号 + 3 位 36 进制序列（左补 0）；总长 = 父编号长 + 3。 */
    static String buildChildNo(String parentCode, long seq) {
        if (seq < 1 || seq > MAX_CHILD_SEQ) {
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BIZ_NO_SEQ_EXCEEDED, Long.toString(seq));
        }
        String s = Long.toString(seq, 36);
        StringBuilder sb = new StringBuilder(parentCode.length() + 3);
        sb.append(parentCode);
        for (int i = s.length(); i < 3; i++) {
            sb.append('0');
        }
        sb.append(s);
        return sb.toString();
    }

    // ==================== DB 递增 ====================

    private Long nextSeq(BiddingBizNoTypeEnum type, String scopeKey) {
        biddingBizSequenceMapper.upsertAndIncrement(type.name(), scopeKey);
        Long seq = biddingBizSequenceMapper.selectLastInsertId();
        if (seq == null || seq < 1) {
            // 理论上刚 upsert 过不会发生；防御性兜底
            throw ServiceExceptionUtil.exception(ErrorCodeConstants.BIZ_NO_SEQ_FAILED);
        }
        return seq;
    }
}
