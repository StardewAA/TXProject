package cn.iocoder.txgy.module.bidding.service.noticeexternal.sanitize;
// 规范依据：设计文档 v0.6 §九（XSS/SSRF：HTML 消毒或 sandbox）；《招投标模块开发规范》红线；安全评审意见 1（存储型 XSS：输出白名单净化）

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HTML 白名单净化器（jsoup，classpath 由 starter-web 传递提供）。
 *
 * <p>用途：外部公告 content_html 为“原文原样留档”（权威要求，入库不裁剪）；
 * 展示链路（公开详情）必须净化后再交给前端渲染 —— 删除 script/iframe/object/表单/样式
 * 及 on* 事件属性，href/src 仅放行 http/https(/mailto)。
 * <p>白名单宽度（联调清单 R7/R12① “白名单结构化净化”，前端据此做公文排版）：
 * <b>保留</b>：p/br/div/h1~h6/table/thead/tbody/tfoot/tr/th/td/caption/col/colgroup/
 * ul/ol/li/a/img/blockquote/hr/strong/b/em/i/u/sub/sup/small/q/pre/cite/code；
 * <b>剥除</b>：script/iframe/object/embed/form/input/button/textarea/select/option/
 * style/link/meta/base/frame/frameset；全部 on* 事件属性与 style 属性；class 不入内联
 * （样式由前端组件 class 控制，防外站 class 名污染）；href 仅 http/https/mailto，
 * img src 仅 http/https（data:/javascript: 一律剔除，防盗链/伪协议注入）。
 * <p>注意：净化不是唯一防线 —— 前端仍需按可信内容渲染 + CSP；本组件处理“后端兜底白名单”。
 */
public final class HtmlSanitizer {

    private static final Safelist DISPLAY_SAFELIST;

    static {
        Safelist list = Safelist.relaxed()
                // 高危/富交互标签一律剔除
                .removeTags("script", "iframe", "object", "embed", "form", "input", "button",
                        "textarea", "select", "option", "style", "link", "meta", "base", "frame", "frameset")
                // 属性级兜底：杜绝 on* 事件与 javascript:/data: 伪协议（Safelist.relaxed 已不含，这里显式约束协议）
                .removeAttributes(":all", "onclick", "onload", "onerror", "onmouseover", "onfocus", "onblur",
                        "onchange", "onsubmit", "onkeydown", "onkeyup", "style")
                .addProtocols("a", "href", "http", "https", "mailto")
                .addProtocols("img", "src", "http", "https")
                // 审查 S5（2026-09-09）：relaxed 自带 ftp: 等协议，addProtocols 是追加非替换——显式剔除避免外链 ftp 直出
                .removeProtocols("a", "href", "ftp")
                .removeProtocols("img", "src", "ftp")
                .addProtocols("blockquote", "cite", "http", "https");
        DISPLAY_SAFELIST = list;
    }

    private HtmlSanitizer() {
    }

    /**
     * 净化一段外部 HTML 为“可安全展示子集”。
     *
     * @return 净化后 HTML；入参为 null/空白时原样返回（无内容可净化）
     */
    public static String sanitize(String html) {
        if (html == null || html.isBlank()) {
            return html;
        }
        return Jsoup.clean(html, DISPLAY_SAFELIST);
    }

}
