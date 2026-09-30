package cn.iocoder.txgy.module.cms.service.rag;

import cn.iocoder.txgy.module.cms.enums.CmsSearchScopeEnum;

import java.util.regex.Pattern;

/** 轻量级检索路由：法规特征明显时禁用语义泛化，强制走 LAW/BM25。 */
public final class CmsSearchRouter {

    private static final Pattern LEGAL_QUERY = Pattern.compile(
            "(?i)(GB(?:/T|T)?\\s*\\d+|第[一二三四五六七八九十百千万零〇0-9]+条|根据.{0,30}(法|条例|办法|规定)|"
                    + "中华人民共和国.{0,30}法|国标|标准号|文号)");
    private static final Pattern FINANCE_POLICY_QUERY = Pattern.compile(
            "(惠企|补贴|补助|贴息|融资支持|担保支持|税费减免|专项资金|申报条件|申报截止|奖励资金|金融政策)");

    private CmsSearchRouter() {
    }

    public static CmsSearchScopeEnum resolve(CmsSearchScopeEnum requested, String query) {
        CmsSearchScopeEnum scope = requested == null ? CmsSearchScopeEnum.AUTO : requested;
        if (scope != CmsSearchScopeEnum.AUTO) {
            return scope;
        }
        String value = query == null ? "" : query.strip();
        if (LEGAL_QUERY.matcher(value).find()) return CmsSearchScopeEnum.LAW;
        if (FINANCE_POLICY_QUERY.matcher(value).find()) return CmsSearchScopeEnum.FINANCE_POLICY;
        return CmsSearchScopeEnum.ALL;
    }
}
