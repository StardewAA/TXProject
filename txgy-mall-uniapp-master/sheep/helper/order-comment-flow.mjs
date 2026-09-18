// Shared by the H5 page and isolated tests. Existing backend status rules remain authoritative.
export function createOrderCommentFlow(state, { readOrder, sendComment, isCurrent }) {
  const succeeded = new Set(); let alive = true, generation = 0;
  const current = (request) => alive && isCurrent() && request === generation;
  function reconcile(order) {
    if (!order || String(order.id) !== String(state.id) || !Array.isArray(order.items)) throw new Error('订单信息不一致，请从订单详情重新进入');
    if (order.status === 30 && (typeof order.commentStatus !== 'boolean' || order.items.some(item => typeof item.commentStatus !== 'boolean'))) throw new Error('评价状态不完整，请刷新核验后再试');
    const drafts = new Map(state.commentList.map(comment => [String(comment.orderItemId), comment]));
    const items = order.status === 30 && order.commentStatus === false ? order.items.filter(item => item.commentStatus === false && !succeeded.has(String(item.id))) : [];
    state.orderInfo = { ...order, items };
    state.commentList = items.map(item => drafts.get(String(item.id)) || { anonymous: false, orderItemId: String(item.id), descriptionScores: 5, benefitScores: 5, content: '', picUrls: [] });
    state.needsRefresh = false;
  }
  async function load() {
    if (!alive || !isCurrent() || state.submitting || state.loading) return;
    const request = ++generation; state.loading = true; state.error = '';
    try { const order = await readOrder(state.id); if (current(request)) reconcile(order); }
    catch (error) { if (current(request)) { state.needsRefresh = true; state.error = error?.message || '订单读取失败，请刷新核验后再评价'; } }
    finally { if (current(request)) state.loading = false; }
  }
  async function submit() {
    if (!alive || !isCurrent() || state.submitting || state.loading || state.needsRefresh || !state.commentList.length) return false;
    if (state.commentList.some(comment => !String(comment.content || '').trim() || String(comment.content).trim().length > 120 || ![comment.descriptionScores, comment.benefitScores].every(score => Number.isInteger(score) && score >= 1 && score <= 5))) {
      state.error = '请填写每件待评价商品的内容（1—120 字），并选择 1—5 星评分'; return false;
    }
    const request = ++generation; state.submitting = true; state.error = '';
    try {
      const order = await readOrder(state.id); if (!current(request)) return false; reconcile(order);
      if (order.status !== 30) { state.error = '订单状态已变化，当前不能评价；没有提交新的评价。'; return false; }
      for (const comment of [...state.commentList]) {
        if (!current(request)) return false;
        await sendComment({ ...comment, content: comment.content.trim(), picUrls: [...comment.picUrls] });
        if (!current(request)) return false;
        succeeded.add(String(comment.orderItemId));
        state.commentList = state.commentList.filter(value => String(value.orderItemId) !== String(comment.orderItemId));
        state.orderInfo.items = state.orderInfo.items.filter(item => String(item.id) !== String(comment.orderItemId));
      }
      return current(request) && state.commentList.length === 0;
    } catch (error) {
      if (!current(request)) return false;
      const message = error?.message || '评价提交失败'; state.needsRefresh = true;
      try {
        const order = await readOrder(state.id); if (!current(request)) return false; reconcile(order);
        state.error = `${message}。已重新核验：已成功项目不会重发，剩余草稿已保留，请确认后重试。`;
      } catch {
        if (current(request)) state.error = `${message}。暂时无法核验提交结果，已暂停再次提交；请刷新核验，勿重复发送。`;
      }
      return false;
    } finally { if (current(request)) state.submitting = false; }
  }
  function dispose() { alive = false; generation++; state.commentList = []; state.orderInfo = { items: [] }; state.loading = false; state.submitting = false; }
  return { load, submit, dispose };
}
