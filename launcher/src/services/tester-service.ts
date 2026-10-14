import type {
  BugVote,
  NeedsTestingResponse,
  PendingKind,
  ReviewVote,
  SubmitTestVoteResponse,
} from "../types/tester";

// Salami: Die NoRisk-Testerwarteschlange gibt es nicht – keine Backend-Aufrufe, kein Fenster.
const unavailable = <T>(): Promise<T> =>
  Promise.reject(new Error("Not available in Salami Client"));

export const fetchTesterQueueCount = (): Promise<{ count: number }> =>
  Promise.resolve({ count: 0 });

export const fetchTesterQueue = (): Promise<NeedsTestingResponse> => unavailable();

export const openTesterWindow = (): Promise<void> => unavailable();

export interface SubmitTesterVoteArgs {
  issueId: string;
  kind: PendingKind;
  vote: BugVote | ReviewVote;
  description?: string;
}

export const submitTesterVote = (
  _args: SubmitTesterVoteArgs,
): Promise<SubmitTestVoteResponse> => unavailable();
