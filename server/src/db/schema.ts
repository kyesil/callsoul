import { boolean, integer, pgTable, primaryKey, text, timestamp } from "drizzle-orm/pg-core";

/** Per-number summary, so a lookup is a single primary-key read. */
export const numbers = pgTable("numbers", {
  number: text().primaryKey(),
  spamVotes: integer("spam_votes").notNull().default(0),
  safeVotes: integer("safe_votes").notNull().default(0),
  category: text(),
  callerName: text("caller_name"),
  updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
}).enableRLS();

/** One row per device and number, so a device can change its vote but not repeat it. */
export const reports = pgTable(
  "reports",
  {
    number: text()
      .notNull()
      .references(() => numbers.number, { onDelete: "cascade" }),
    deviceHash: text("device_hash").notNull(),
    isSpam: boolean("is_spam").notNull(),
    category: text(),
    callerName: text("caller_name"),
    updatedAt: timestamp("updated_at", { withTimezone: true }).notNull().defaultNow(),
  },
  (t) => [primaryKey({ columns: [t.number, t.deviceHash] })],
).enableRLS();
