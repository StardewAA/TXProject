package cn.iocoder.txgy.module.bidding.service.bidagent;

// [规范依据]《招投标模块开发规范》§4.2（业务在 service）
// [业务目标] 切片管理（素材质量治理核心）：人工编辑/拆分/合并/批量删除切片，
//           并把人工修改标记 + 向量同步闭环起来，让"改了切片"可生效、可巡检。
// [为什么] 切片是召回质量的第一现场；此前只有只读"看切片"，专员无法修正劣质切片，
//           只能整份重建索引，人工修正无法沉淀。这里把切片治理做成一等能力。

import java.util.List;

public interface BidAgentSegmentService {

    /**
     * 编辑切片正文：更新台账并置「人工修改」标记，再同步向量库。
     *
     * <p>向量库不可用时仅更新台账（不抛异常），由调用方提示「向量待重建」。
     */
    void edit(Long id, String content);

    /**
     * 拆分切片：按段落一分为二，两半都置「人工修改」标记并同步向量库。
     */
    void split(Long id);

    /**
     * 合并切片：把若干相邻切片正文拼接为一条（保留首条 id 与溯源），其余删除。
     */
    void merge(List<Long> ids);

    /**
     * 批量删除切片：连同向量库对应点一并清理。
     */
    void delete(List<Long> ids);
}
