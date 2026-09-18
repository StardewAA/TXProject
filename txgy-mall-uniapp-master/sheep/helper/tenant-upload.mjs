/** Callback uploads share the same verified session contract as ordinary private requests. */
export function createTenantUploader({ prepare, isCurrent, upload, showLoading, hideLoading, subscribe,
  schedule = setTimeout, cancel = clearTimeout }) {
  let active = 0;
  return async (file, directory = '', expectedSnapshot, pageCurrent = () => true) => {
    const assertCurrent = (snapshot) => {
      if (!pageCurrent() || (expectedSnapshot && !isCurrent(expectedSnapshot)) || (snapshot && !isCurrent(snapshot))) {
        throw new Error('登录状态已变化或页面已离开，请重新选择文件');
      }
    };
    assertCurrent();
    const { snapshot, header } = await prepare();
    assertCurrent(snapshot);
    if (++active === 1) showLoading();
    try {
      return await new Promise((resolve, reject) => {
        let settled = false, task, timer, unsubscribe;
        const finish = (error, result) => {
          if (settled) return;
          settled = true;
          if (timer !== undefined) cancel(timer);
          unsubscribe?.();
          if (error) reject(error); else resolve(result);
        };
        const stop = (error) => { finish(error); task?.abort?.(); };
        try {
          unsubscribe = subscribe?.(() => {
            try { assertCurrent(snapshot); } catch (error) { stop(error); }
          });
          timer = schedule(() => stop(new Error('文件上传超时，请重试')), 65000);
          assertCurrent(snapshot);
          task = upload({ filePath: file, name: 'file', timeout: 60000,
            header: { Accept: '*/*', ...header }, formData: { directory },
            success(response) {
              try {
                assertCurrent(snapshot);
                if (Number(response.statusCode) < 200 || Number(response.statusCode) >= 300 || !response.statusCode) throw new Error('文件上传失败，请检查服务后重试');
                const result = typeof response.data === 'string' ? JSON.parse(response.data) : response.data;
                if (!result || result.code !== 0 || typeof result.data !== 'string' || !result.data.trim()) {
                  throw new Error(result?.msg || '文件上传未成功，请重试');
                }
                finish(null, result);
              } catch (error) {
                finish(error instanceof SyntaxError ? new Error('上传服务返回格式异常，请重试') : error);
              }
            },
            fail() { finish(new Error('文件上传失败或已取消，请重试')); },
            complete() { if (!settled) finish(new Error('上传未返回有效结果，请重试')); },
          });
        } catch (error) { finish(error); }
      });
    } finally { if (--active === 0) hideLoading(); }
  };
}
