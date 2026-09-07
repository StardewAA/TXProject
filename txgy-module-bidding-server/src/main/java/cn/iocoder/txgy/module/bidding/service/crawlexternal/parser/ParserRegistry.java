package cn.iocoder.txgy.module.bidding.service.crawlexternal.parser;
// [依据] 与 ChannelRegistry 同构：构造注入自动收集全部 NoticeParser Bean，按 parserCode 路由；
//       重复 code 启动即失败；未知 code 明确报错（防配置错乱静默走错解析器）。

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析器注册表：按 parser_code 路由（多策略可插拔）。
 */
@Component
public class ParserRegistry {

    private final Map<String, NoticeParser> parsers = new HashMap<>();

    private final List<NoticeParser> parserList;

    public ParserRegistry(List<NoticeParser> parserList) {
        this.parserList = parserList == null ? List.of() : parserList;
    }

    @PostConstruct
    public void init() {
        for (NoticeParser parser : parserList) {
            NoticeParser old = parsers.put(parser.getParserCode(), parser);
            if (old != null) {
                throw new IllegalStateException("解析器 code 重复注册: " + parser.getParserCode());
            }
        }
    }

    /** 是否已注册（采集源配置保存时校验 parser_code_default 用） */
    public boolean has(String parserCode) {
        return parserCode != null && parsers.containsKey(parserCode);
    }

    /** 取解析器；未知 code 明确报错（抛出 IllegalArgumentException，由服务层转业务码 041） */
    public NoticeParser get(String parserCode) {
        NoticeParser parser = parsers.get(parserCode);
        if (parser == null) {
            throw new IllegalArgumentException("未知解析器 parserCode=" + parserCode
                    + "，可用: " + parsers.keySet());
        }
        return parser;
    }

    /** 全部解析器（按 code 排序，管理界面清单） */
    public List<NoticeParser> all() {
        List<NoticeParser> list = new ArrayList<>(parsers.values());
        list.sort(Comparator.comparing(NoticeParser::getParserCode));
        return list;
    }

}
