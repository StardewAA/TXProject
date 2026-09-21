// @vitest-environment node
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const { z } = createRequire(new URL('../../../../../../packages/@core/ui-kit/form-ui/package.json', import.meta.url))('zod');

function load<TExports>(file: URL, dependencies: Record<string, unknown>): TExports {
  const source = readFileSync(file, 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
    reportDiagnostics: true,
  });
  expect(compiled.diagnostics).toEqual([]);
  const exports = {};
  new Function('exports', 'require', compiled.outputText)(exports, (name: string) => {
    if (!(name in dependencies)) throw new Error(`Unexpected dependency: ${name}`);
    return dependencies[name];
  });
  return exports as TExports;
}

const schema = load<{
  useFormSchema: () => any[];
  useGridColumns: () => any[];
}>(new URL('./data.ts', import.meta.url), {
  'vue': { h() {}, markRaw: (value: unknown) => value },
  '@vben/constants': { CommonStatusEnum: { ENABLE: 0 }, DICT_TYPE: {} },
  '@vben/hooks': { getDictOptions: () => [] },
  '@vben/utils': {},
  'ant-design-vue': {},
  '#/adapter/form': { z },
  '#/api/member/group': {},
  '#/api/member/level': {},
  '#/api/member/tag': {},
  '#/components/area': {},
  '#/utils': { getRangePickerDefaultProps: () => ({}) },
});

describe('member login-name compatibility', () => {
  it('allows editing a login-name account without an identity mobile', () => {
    const mobile = schema.useFormSchema().find((field: any) => field.fieldName === 'mobile');
    const values = { loginName: 'buyer_01', mobile: null };
    expect(mobile.dependencies.disabled(values)).toBe(true);
    const rule = mobile.dependencies.rules(values);
    expect(rule.safeParse(null).success).toBe(true);
    expect(rule.safeParse(undefined).success).toBe(true);
  });

  it('preserves required and editable mobile for legacy accounts', () => {
    const mobile = schema.useFormSchema().find((field: any) => field.fieldName === 'mobile');
    expect(mobile.dependencies.disabled({ loginName: null })).toBe(false);
    expect(mobile.dependencies.rules({ loginName: null })).toBe('required');
  });

  it('exposes account name and unverified contact as readonly details in each view', () => {
    const form = schema.useFormSchema(), columns = schema.useGridColumns();
    const detail = readFileSync(new URL('./detail/modules/basic-info.vue', import.meta.url), 'utf8');
    for (const field of ['loginName', 'contactMobile']) {
      expect(form.find((item: any) => item.fieldName === field).componentProps.disabled).toBe(true);
      expect(columns.some((item: any) => item.field === field)).toBe(true);
      expect(detail).toContain(`field: '${field}'`);
    }
  });

  it('excludes readonly identity metadata from updates without mutating the loaded member', () => {
    const calls: unknown[] = [];
    const api = load<{ updateUser: (data: Record<string, unknown>) => unknown }>(new URL('../../../api/member/user/index.ts', import.meta.url), {
      '#/api/request': { requestClient: { put: (path: string, data: unknown) => calls.push({ path, data }) } },
    });
    const member = { id: 7, loginName: 'buyer_01', contactMobile: '18800000000', mobile: null, status: 1 };
    api.updateUser(member);
    expect(calls).toEqual([{ path: '/member/user/update', data: { id: 7, mobile: null, status: 1 } }]);
    expect(member.loginName).toBe('buyer_01'); expect(member.contactMobile).toBe('18800000000');
  });
});
