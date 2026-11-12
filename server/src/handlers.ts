import { createHash } from "node:crypto";
import { and, count, desc, eq, isNotNull, sql } from "drizzle-orm";
import { db } from "./db/client.js";
import { numbers, reports } from "./db/schema.js";
import { isCategory, isDeviceId, isE164, verdictOf, voteDelta } from "./rules.js";

const json = (body: unknown, status = 200) => Response.json(body, { status });
const invalid = (field: string) => json({ error: `invalid ${field}` }, 400);

async function readBody(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const body = await request.json();
    return typeof body === "object" && body !== null ? (body as Record<string, unknown>) : null;
  } catch {
    return null;
  }
}

function hashDevice(deviceId: string): string {
  const salt = process.env.DEVICE_HASH_SALT;
  if (!salt) throw new Error("DEVICE_HASH_SALT is not set");
  return createHash("sha256").update(salt).update(deviceId).digest("hex");
}

export async function health(): Promise<Response> {
  await db.execute(sql`select 1`);
  return json({ ok: true });
}

/** POST keeps the number out of URLs, which end up in access logs. */
export async function lookup(request: Request): Promise<Response> {
  const body = await readBody(request);
  if (!body || !isE164(body.number)) return invalid("number");

  const [row] = await db.select().from(numbers).where(eq(numbers.number, body.number));
  return json({
    verdict: verdictOf(row?.spamVotes ?? 0, row?.safeVotes ?? 0),
    spamVotes: row?.spamVotes ?? 0,
    safeVotes: row?.safeVotes ?? 0,
    category: row?.category ?? null,
    // Names typed by users are no longer collected or shown to others; the field stays for older apps.
    callerName: null,
  });
}

export async function report(request: Request): Promise<Response> {
  const body = await readBody(request);
  if (!body || !isE164(body.number)) return invalid("number");
  if (!isDeviceId(body.deviceId)) return invalid("deviceId");
  if (typeof body.isSpam !== "boolean") return invalid("isSpam");
  if (body.category != null && !isCategory(body.category)) return invalid("category");

  const number = body.number;
  const isSpam = body.isSpam;
  const category = isSpam && isCategory(body.category) ? body.category : null;
  // A caller name sent by an older app is ignored: the app now keeps it as a label on the phone.
  const callerName = null;
  const deviceHash = hashDevice(body.deviceId);
  const updatedAt = new Date();

  await db.transaction(async (tx) => {
    await tx.insert(numbers).values({ number }).onConflictDoNothing();
    await tx.select({ number: numbers.number }).from(numbers).where(eq(numbers.number, number)).for("update");

    const [previous] = await tx
      .select({ isSpam: reports.isSpam })
      .from(reports)
      .where(and(eq(reports.number, number), eq(reports.deviceHash, deviceHash)));

    await tx
      .insert(reports)
      .values({ number, deviceHash, isSpam, category, callerName, updatedAt })
      .onConflictDoUpdate({
        target: [reports.number, reports.deviceHash],
        set: { isSpam, category, callerName, updatedAt },
      });

    // Totals also hold imported votes that have no report rows, so a recount would erase them.
    const delta = voteDelta(previous?.isSpam, isSpam);

    const [topCategory] = await tx
      .select({ value: reports.category })
      .from(reports)
      .where(and(eq(reports.number, number), isNotNull(reports.category)))
      .groupBy(reports.category)
      .orderBy(desc(count()))
      .limit(1);

    await tx
      .update(numbers)
      .set({
        spamVotes: sql`${numbers.spamVotes} + ${delta.spam}`,
        safeVotes: sql`${numbers.safeVotes} + ${delta.safe}`,
        category: topCategory?.value ?? null,
        callerName: null,
        updatedAt,
      })
      .where(eq(numbers.number, number));
  });

  return json({ ok: true });
}
