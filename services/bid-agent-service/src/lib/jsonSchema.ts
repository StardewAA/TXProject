export type JsonSchemaType =
  | 'object'
  | 'array'
  | 'string'
  | 'number'
  | 'integer'
  | 'boolean'
  | 'null';

export interface JsonSchema {
  title?: string;
  description?: string;
  type?: JsonSchemaType | JsonSchemaType[];
  properties?: Record<string, JsonSchema>;
  required?: string[];
  additionalProperties?: boolean | JsonSchema;
  items?: JsonSchema;
  enum?: unknown[];
  const?: unknown;
  minimum?: number;
  maximum?: number;
  exclusiveMinimum?: number;
  exclusiveMaximum?: number;
  minLength?: number;
  maxLength?: number;
  pattern?: string;
  minItems?: number;
  maxItems?: number;
  nullable?: boolean;
  anyOf?: JsonSchema[];
  default?: unknown;
}

export interface ValidationIssue {
  path: string;
  message: string;
}

function valueType(value: unknown): JsonSchemaType {
  if (value === null) return 'null';
  if (Array.isArray(value)) return 'array';
  switch (typeof value) {
    case 'string':
      return 'string';
    case 'number':
      return Number.isInteger(value) ? 'integer' : 'number';
    case 'boolean':
      return 'boolean';
    case 'object':
      return 'object';
    default:
      return 'null';
  }
}

function typeMatches(schemaType: JsonSchemaType, value: unknown): boolean {
  const actual = valueType(value);
  if (schemaType === actual) return true;
  // number 允许整数
  if (schemaType === 'number' && actual === 'integer') return true;
  return false;
}

function checkType(schema: JsonSchema, value: unknown, path: string, issues: ValidationIssue[]): boolean {
  if (schema.type === undefined) return true;
  const expected = Array.isArray(schema.type) ? schema.type : [schema.type];
  if (expected.includes('null') && value === null) return true;
  if (expected.some((t) => typeMatches(t, value))) return true;
  issues.push({ path, message: `类型应为 ${expected.join('|')}，实际为 ${valueType(value)}` });
  return false;
}

function validateNode(schema: JsonSchema, value: unknown, path: string, issues: ValidationIssue[]): void {
  if (schema.anyOf && schema.anyOf.length > 0) {
    const branchIssues: ValidationIssue[] = [];
    const matched = schema.anyOf.some((sub) => {
      const local: ValidationIssue[] = [];
      validateNode(sub, value, path, local);
      if (local.length === 0) return true;
      branchIssues.push(...local);
      return false;
    });
    if (matched) return;
    if (value === null && schema.nullable === true) return;
    issues.push({ path, message: `不匹配 anyOf 任一分支：${branchIssues[0]?.message ?? '无'}` });
    return;
  }

  if (!checkType(schema, value, path, issues)) return;
  if (value === null) return;

  if (schema.const !== undefined && JSON.stringify(schema.const) !== JSON.stringify(value)) {
    issues.push({ path, message: `值必须等于 ${JSON.stringify(schema.const)}` });
  }
  if (schema.enum && !schema.enum.some((candidate) => JSON.stringify(candidate) === JSON.stringify(value))) {
    issues.push({ path, message: `值不在枚举内：${schema.enum.map((v) => JSON.stringify(v)).join(', ')}` });
  }

  if (typeof value === 'string') {
    if (schema.minLength !== undefined && value.length < schema.minLength) {
      issues.push({ path, message: `长度不能小于 ${schema.minLength}` });
    }
    if (schema.maxLength !== undefined && value.length > schema.maxLength) {
      issues.push({ path, message: `长度不能大于 ${schema.maxLength}` });
    }
    if (schema.pattern && !new RegExp(schema.pattern).test(value)) {
      issues.push({ path, message: `不匹配 pattern ${schema.pattern}` });
    }
  }

  if (typeof value === 'number') {
    if (schema.minimum !== undefined && value < schema.minimum) {
      issues.push({ path, message: `不能小于 ${schema.minimum}` });
    }
    if (schema.maximum !== undefined && value > schema.maximum) {
      issues.push({ path, message: `不能大于 ${schema.maximum}` });
    }
    if (schema.exclusiveMinimum !== undefined && value <= schema.exclusiveMinimum) {
      issues.push({ path, message: `必须大于 ${schema.exclusiveMinimum}` });
    }
    if (schema.exclusiveMaximum !== undefined && value >= schema.exclusiveMaximum) {
      issues.push({ path, message: `必须小于 ${schema.exclusiveMaximum}` });
    }
  }

  if (Array.isArray(value)) {
    if (schema.minItems !== undefined && value.length < schema.minItems) {
      issues.push({ path, message: `元素数量不能少于 ${schema.minItems}` });
    }
    if (schema.maxItems !== undefined && value.length > schema.maxItems) {
      issues.push({ path, message: `元素数量不能多于 ${schema.maxItems}` });
    }
    if (schema.items) {
      value.forEach((item, index) => validateNode(schema.items as JsonSchema, item, `${path}[${index}]`, issues));
    }
  }

  if (typeof value === 'object' && !Array.isArray(value)) {
    const record = value as Record<string, unknown>;
    for (const key of schema.required ?? []) {
      if (record[key] === undefined) {
        issues.push({ path: `${path}.${key}`, message: '缺少必填字段' });
      }
    }
    const properties = schema.properties ?? {};
    for (const [key, child] of Object.entries(record)) {
      const childSchema = properties[key];
      if (childSchema) {
        validateNode(childSchema, child, `${path}.${key}`, issues);
        continue;
      }
      if (schema.additionalProperties === false) {
        issues.push({ path: `${path}.${key}`, message: '存在未声明的字段' });
      } else if (typeof schema.additionalProperties === 'object' && schema.additionalProperties !== null) {
        validateNode(schema.additionalProperties as JsonSchema, child, `${path}.${key}`, issues);
      }
    }
  }
}

export function validateAgainstSchema(schema: JsonSchema, value: unknown): ValidationIssue[] {
  const issues: ValidationIssue[] = [];
  validateNode(schema, value, '$', issues);
  return issues;
}

export function isValid(schema: JsonSchema, value: unknown): boolean {
  return validateAgainstSchema(schema, value).length === 0;
}

export function formatIssues(issues: ValidationIssue[]): string {
  return issues.map((issue) => `${issue.path}: ${issue.message}`).join('; ');
}

/** 从 LLM 输出中稳健地抽出第一段 JSON（支持 ```json 代码围栏） */
export function extractJsonText(text: string): string | undefined {
  const trimmed = text.trim();
  const fenced = trimmed.match(/```(?:json)?\s*([\s\S]*?)```/i);
  const candidate = fenced ? fenced[1]!.trim() : trimmed;
  const start = candidate.search(/[[{]/);
  if (start < 0) return undefined;
  const open = candidate[start];
  const close = open === '{' ? '}' : ']';
  let depth = 0;
  let inString = false;
  let escaped = false;
  for (let i = start; i < candidate.length; i += 1) {
    const ch = candidate[i];
    if (inString) {
      if (escaped) escaped = false;
      else if (ch === '\\') escaped = true;
      else if (ch === '"') inString = false;
      continue;
    }
    if (ch === '"') {
      inString = true;
    } else if (ch === open) {
      depth += 1;
    } else if (ch === close) {
      depth -= 1;
      if (depth === 0) return candidate.slice(start, i + 1);
    }
  }
  return undefined;
}

export interface ParseResult<T> {
  ok: boolean;
  value?: T;
  error?: string;
  issues?: ValidationIssue[];
}

export function parseAndValidate<T = unknown>(schema: JsonSchema, text: string): ParseResult<T> {
  const jsonText = extractJsonText(text);
  if (!jsonText) {
    return { ok: false, error: '未能在输出中找到 JSON 内容' };
  }
  let parsed: unknown;
  try {
    parsed = JSON.parse(jsonText);
  } catch (error) {
    return { ok: false, error: `JSON 解析失败：${(error as Error).message}` };
  }
  const issues = validateAgainstSchema(schema, parsed);
  if (issues.length > 0) {
    return { ok: false, error: formatIssues(issues), issues };
  }
  return { ok: true, value: parsed as T };
}
