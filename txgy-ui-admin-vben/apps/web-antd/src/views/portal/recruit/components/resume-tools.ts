export type ResumeToolAction = 'ai' | 'edit' | 'upload' | 'version';

export interface ResumeToolRequest {
  action: ResumeToolAction;
}

export function createResumeToolRequest(action: ResumeToolAction): ResumeToolRequest {
  return { action };
}
