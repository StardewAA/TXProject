import type { TaskHandler, TaskType } from '../orchestrator/types.js';
import { draftTask } from './draftTask.js';
import { generateTask } from './generateTask.js';
import { outlineTask } from './outlineTask.js';
import { parseTask } from './parseTask.js';

export const TASK_HANDLERS: Record<TaskType, TaskHandler> = {
  parse: parseTask,
  outline: outlineTask,
  draft: draftTask,
  generate: generateTask,
};

export function getTaskHandler(type: TaskType): TaskHandler {
  const handler = TASK_HANDLERS[type];
  if (!handler) throw new Error(`未知任务类型：${type}`);
  return handler;
}
