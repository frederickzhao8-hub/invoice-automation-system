export interface AiInsight {
  analysisType: string;
  generatedAt: string;
  narrative: string;
  keyFindings: string[];
  mostCommonBottleneckStage: string | null;
  recommendedActions: string[];
  grounding: Record<string, unknown>;
}

export interface AiOrderAnalysis {
  analysisType: string;
  generatedAt: string;
  orderId: number;
  orderNumber: string;
  healthStatus: string;
  delayStage: string | null;
  narrative: string;
  rootCauses: string[];
  recommendedActions: string[];
  grounding: Record<string, unknown>;
}
