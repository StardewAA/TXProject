// @vitest-environment node
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';

import { describe, expect, it, vi } from 'vitest';

const ts = createRequire(
  new URL('../../../../../../../package.json', import.meta.url),
)('typescript');

function createModalApi() {
  const api = { open: vi.fn(), setData: vi.fn() };
  api.setData.mockReturnValue(api);
  return api;
}

function loadWarehouseComponentScript(
  source: string,
  modalApis: Array<ReturnType<typeof createModalApi>>,
) {
  const dependencies: Record<string, unknown> = {
    '@vben/common-ui': {
      DocAlert: {},
      Page: {},
      useVbenModal: () => [{}, modalApis.shift()],
    },
    '@vben/utils': { downloadFileFromBlobPart: vi.fn() },
    'ant-design-vue': {
      message: { loading: vi.fn(), success: vi.fn() },
    },
    '#/adapter/vxe-table': {
      ACTION_ICON: {},
      TableAction: {},
      useVbenVxeGrid: () => [{}, { formApi: {}, query: vi.fn() }],
    },
    '#/api/wms/md/warehouse': {
      deleteWarehouse: vi.fn(),
      exportWarehouse: vi.fn(),
      getWarehousePage: vi.fn(),
    },
    '#/locales': { $t: vi.fn() },
    './data': { useGridColumns: vi.fn(), useGridFormSchema: vi.fn() },
    './modules/detail.vue': { default: {} },
    './modules/form.vue': { default: {} },
  };
  const compiled = ts.transpileModule(`${source}\nexport { handleDetail };`, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
    },
  });
  const exports: Record<string, unknown> = {};
  new Function('exports', 'require', 'defineOptions', compiled.outputText)(
    exports,
    (name: string) => dependencies[name],
    vi.fn(),
  );
  return exports as {
    handleDetail: (row: { id: number }) => void;
  };
}

function getDuplicateImplementationDiagnostics(source: string) {
  const fileName = 'warehouse-index.ts';
  const compilerOptions = {
    module: ts.ModuleKind.ESNext,
    noEmit: true,
    noLib: true,
    target: ts.ScriptTarget.ES2022,
  };
  const host = ts.createCompilerHost(compilerOptions);
  host.fileExists = (name) => name === fileName;
  host.readFile = (name) => (name === fileName ? source : undefined);
  host.getSourceFile = (name, languageVersion) =>
    name === fileName
      ? ts.createSourceFile(name, source, languageVersion, true)
      : undefined;

  const program = ts.createProgram([fileName], compilerOptions, host);
  return ts
    .getPreEmitDiagnostics(program)
    .filter((diagnostic) => diagnostic.code === 2393);
}

describe('warehouse detail action', () => {
  const component = readFileSync(new URL('./index.vue', import.meta.url), 'utf8');
  const script = component.match(/<script[^>]*>([\s\S]*?)<\/script>/)?.[1] ?? '';

  it('opens the dedicated detail modal with the selected warehouse id', () => {
    const formModalApi = createModalApi();
    const detailModalApi = createModalApi();
    const modalApis = [formModalApi, detailModalApi];
    const loaded = loadWarehouseComponentScript(script, modalApis);

    loaded.handleDetail({ id: 73 });

    expect(detailModalApi.setData).toHaveBeenCalledWith({ id: 73 });
    expect(detailModalApi.open).toHaveBeenCalledOnce();
    expect(formModalApi.setData).not.toHaveBeenCalled();
  });

  it('keeps a single detail handler so clicking details can mount the detail modal', () => {
    expect(getDuplicateImplementationDiagnostics(script)).toEqual([]);
  });
});
