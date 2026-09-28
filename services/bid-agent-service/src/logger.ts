export type LogLevel = 'debug' | 'info' | 'warn' | 'error';

const LEVEL_ORDER: Record<LogLevel, number> = { debug: 10, info: 20, warn: 30, error: 40 };

const LEVEL_FROM_TEXT = new Map<string, LogLevel>([
  ['debug', 'debug'],
  ['info', 'info'],
  ['warn', 'warn'],
  ['error', 'error'],
]);

export function parseLogLevel(value: string | undefined): LogLevel {
  return (value && LEVEL_FROM_TEXT.get(value.toLowerCase())) || 'info';
}

export class Logger {
  readonly level: LogLevel;

  private readonly base: Record<string, unknown>;

  constructor(level: LogLevel = 'info', base: Record<string, unknown> = {}) {
    this.level = level;
    this.base = base;
  }

  child(fields: Record<string, unknown>): Logger {
    return new Logger(this.level, { ...this.base, ...fields });
  }

  withLevel(level: LogLevel): Logger {
    return new Logger(level, this.base);
  }

  debug(message: string, fields?: Record<string, unknown>): void {
    this.write('debug', message, fields);
  }

  info(message: string, fields?: Record<string, unknown>): void {
    this.write('info', message, fields);
  }

  warn(message: string, fields?: Record<string, unknown>): void {
    this.write('warn', message, fields);
  }

  error(message: string, fields?: Record<string, unknown>): void {
    this.write('error', message, fields);
  }

  private write(level: LogLevel, message: string, fields?: Record<string, unknown>): void {
    if (LEVEL_ORDER[level] < LEVEL_ORDER[this.level]) return;
    const line = JSON.stringify({
      ts: new Date().toISOString(),
      level,
      msg: message,
      ...this.base,
      ...(fields ?? {}),
    });
    process.stdout.write(`${line}\n`);
  }
}
