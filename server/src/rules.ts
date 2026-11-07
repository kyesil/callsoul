export const CATEGORIES = ["telemarketing", "scam", "survey", "other"] as const;
export type Category = (typeof CATEGORIES)[number];
export type Verdict = "spam" | "safe" | "unknown";

const MIN_VOTES = 3;
const MIN_RATIO = 0.7;
const MAX_NAME_LENGTH = 60;

/** A single device can never decide a verdict. */
export function verdictOf(spamVotes: number, safeVotes: number): Verdict {
  const total = spamVotes + safeVotes;
  if (total === 0) return "unknown";
  if (spamVotes >= MIN_VOTES && spamVotes / total >= MIN_RATIO) return "spam";
  if (safeVotes >= MIN_VOTES && safeVotes / total >= MIN_RATIO) return "safe";
  return "unknown";
}

/** How a device's new vote moves the totals; `previous` is its earlier vote on the number, if any. */
export function voteDelta(previous: boolean | undefined, isSpam: boolean): { spam: number; safe: number } {
  return {
    spam: Number(isSpam) - Number(previous === true),
    safe: Number(!isSpam) - Number(previous === false),
  };
}

export function isE164(value: unknown): value is string {
  return typeof value === "string" && /^\+[1-9]\d{6,14}$/.test(value);
}

export function isCategory(value: unknown): value is Category {
  return CATEGORIES.includes(value as Category);
}

export function isDeviceId(value: unknown): value is string {
  return typeof value === "string" && /^[0-9a-f-]{32,36}$/i.test(value);
}

export function cleanName(value: unknown): string | null {
  if (typeof value !== "string") return null;
  const name = value.trim().replace(/\s+/g, " ");
  return name.length === 0 ? null : name.slice(0, MAX_NAME_LENGTH);
}
