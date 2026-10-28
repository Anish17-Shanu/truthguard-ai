export type Role = "USER" | "ADMIN";

export type SessionUser = {
  userId: string;
  fullName: string;
  email: string;
  role: Role;
  createdAt?: string;
};

export type AuthPayload = {
  expiresIn: number;
  userId: string;
  fullName: string;
  email: string;
  role: Role;
};

export type ClassificationLabel = "real" | "fake" | "propaganda" | "uncertain";

export type Analysis = {
  analysisId: string;
  sourceType: string;
  inputSnippet: string | null;
  label: ClassificationLabel;
  confidence: number;
  biasScore: number;
  emotion: string;
  language: string;
  highlightedText: string[];
  suspiciousPhrases: string[];
  modelVersion: string;
  classifierMode: "transformer" | "heuristic";
  createdAt: string;
};

export type Paged<T> = {
  items: T[];
  total: number;
  page: number;
  size: number;
};

export type UserStats = {
  totalScans: number;
  fakeCount: number;
  realCount: number;
  propagandaCount: number;
  trendsByDate: Record<string, number>;
};

export type AdminStats = UserStats & {
  users: number;
  feedbackCount: number;
  scansToday: number;
  activeModelVersion: string;
};

export type FeedbackItem = {
  id: string;
  analysisId: string;
  inputSnippet: string | null;
  predictedLabel: string;
  confidence: number;
  correct: boolean;
  correctedLabel: string | null;
  comment: string | null;
  modelVersion: string | null;
  createdAt: string;
};

export type RetrainResult = {
  status: string;
  newModelVersion?: string | null;
  message?: string | null;
};

export type ApiErrorBody = {
  code?: string;
  message?: string;
  requestId?: string;
  details?: Record<string, string>;
};
