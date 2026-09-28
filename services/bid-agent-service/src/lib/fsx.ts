import fs from 'node:fs/promises';
import path from 'node:path';

export async function ensureDir(dir: string): Promise<void> {
  await fs.mkdir(dir, { recursive: true });
}

export async function exists(target: string): Promise<boolean> {
  try {
    await fs.access(target);
    return true;
  } catch {
    return false;
  }
}

/** 原子写入：先写临时文件再 rename，避免进程被杀产生半截文件；失败时清理临时文件 */
export async function writeAtomic(file: string, content: string): Promise<void> {
  await ensureDir(path.dirname(file));
  const tmp = `${file}.${process.pid}.${Date.now()}.tmp`;
  try {
    await fs.writeFile(tmp, content, 'utf8');
    await fs.rename(tmp, file);
  } catch (error) {
    await fs.unlink(tmp).catch(() => undefined);
    throw error;
  }
}

/** 原子写入二进制（docx 等） */
export async function writeBytesAtomic(file: string, data: Uint8Array): Promise<void> {
  await ensureDir(path.dirname(file));
  const tmp = `${file}.${process.pid}.${Date.now()}.tmp`;
  try {
    await fs.writeFile(tmp, data);
    await fs.rename(tmp, file);
  } catch (error) {
    await fs.unlink(tmp).catch(() => undefined);
    throw error;
  }
}

export async function writeJsonAtomic(file: string, data: unknown): Promise<void> {
  await writeAtomic(file, `${JSON.stringify(data, null, 2)}\n`);
}

export async function readJsonOrDefault<T>(file: string, fallback: T): Promise<T> {
  try {
    const text = await fs.readFile(file, 'utf8');
    return JSON.parse(text) as T;
  } catch {
    return fallback;
  }
}

export async function appendLine(file: string, line: string): Promise<void> {
  await ensureDir(path.dirname(file));
  await fs.appendFile(file, `${line}\n`, 'utf8');
}

export async function listFiles(dir: string, baseDir: string = dir): Promise<string[]> {
  const out: string[] = [];
  let entries;
  try {
    entries = await fs.readdir(dir, { withFileTypes: true });
  } catch {
    return out;
  }
  for (const entry of entries) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...(await listFiles(full, baseDir)));
    } else {
      out.push(path.relative(baseDir, full).split(path.sep).join('/'));
    }
  }
  return out;
}

export function randomId(prefix: string): string {
  return `${prefix}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 8)}`;
}
