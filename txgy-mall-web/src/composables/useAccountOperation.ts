import { onScopeDispose } from 'vue'
import { ElMessageBox } from 'element-plus'
import { getTenantContext, isCurrentTenantEpoch } from '@/utils/tenant-context'
import { StaleSessionError } from '@/api/http'

/** Bind deferred UI work to its original page and login generation, not a rotating access token. */
export function createAccountOperation(epoch = getTenantContext().epoch) {
  let alive = true
  const current = () => alive && isCurrentTenantEpoch(epoch)
  return {
    current,
    assert() { if (!current()) throw new StaleSessionError() },
    dispose() { alive = false },
  }
}

export function useAccountOperation() {
  const operation = createAccountOperation()
  onScopeDispose(operation.dispose)
  return operation
}

/** A modal's continuation must not issue a command as an account that logged in later. */
export function useAccountMessageBox() {
  const operation = useAccountOperation()
  return {
    async confirm(...args: Parameters<typeof ElMessageBox.confirm>) {
      operation.assert()
      const result = await ElMessageBox.confirm(...args)
      operation.assert()
      return result
    },
  }
}
