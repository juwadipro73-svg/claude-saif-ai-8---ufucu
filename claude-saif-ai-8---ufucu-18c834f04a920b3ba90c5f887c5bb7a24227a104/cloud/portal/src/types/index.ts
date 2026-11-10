export interface FileItem {
  id: string;
  name: string;
  size: number;
  sizeFormatted?: string;
  mimeType: string;
  category: 'image' | 'video' | 'audio' | 'pdf' | 'code' | 'archive' | 'document' | 'other';
  data: string;
  hash: string;
}

export interface VaultData {
  vaultId: string;
  createdAt: number;
  expiresAt: number | null;
  securityMode: 'onetime' | 'rotating' | 'protected';
  burnOnDownload: boolean;
  isBurned: boolean;
  burnedAt?: number;
  downloadCount: number;
  senderApp: string;
  rotatingSecret: string;
  currentDynamicToken?: string;
  remainingSeconds?: number;
  files: FileItem[];
}

export interface UniversalApiConfig {
  activeProvider: 'gemini' | 'openai' | 'anthropic' | 'custom';
  geminiApiKey: string;
  geminiModel: string;
  openaiApiKey: string;
  openaiModel: string;
  anthropicApiKey: string;
  anthropicModel: string;
  customEndpoint: string;
  customApiKey: string;
  customModel: string;
  adDurationSeconds: number;
  autoDownloadOnAdFinish: boolean;
  activeSponsor: 'saif_cloud' | 'cybershield' | 'neural_studio';
  storageBackend: 'local_memory' | 'cloudflare_r2' | 'aws_s3';
}

export interface FirebaseAppRecord {
  appName: string;
  driveFileId: string;
  status: string;
  timestamp?: number;
}

