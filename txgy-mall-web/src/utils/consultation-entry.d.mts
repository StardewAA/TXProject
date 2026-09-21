export interface ConsultationResource { type: 'PRODUCT' | 'ORDER' | 'PLATFORM'; id?: unknown }
export interface ConsultationTarget { scope: 'SHOP' | 'PLATFORM'; shopId: number; resourceType?: 'PRODUCT' | 'ORDER'; resourceId?: number }
export function consultationId(value: unknown): string | null
export function createConsultationAction(options: {
  resource: () => ConsultationResource;
  readProduct: (id: string) => Promise<{ id?: unknown; shopId?: unknown } | null>;
  readOrder: (id: string) => Promise<{ id?: unknown; shopId?: unknown } | null>;
  current: () => boolean;
  confirmPlatform: () => Promise<boolean>;
  navigate: (target: ConsultationTarget) => Promise<unknown> | unknown;
  update: (state: { busy?: boolean; error?: string }) => void;
}): () => Promise<boolean>
