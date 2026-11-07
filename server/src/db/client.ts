import { drizzle } from "drizzle-orm/postgres-js";
import postgres from "postgres";
import * as schema from "./schema.js";

const url = process.env.DATABASE_URL;
if (!url) throw new Error("DATABASE_URL is not set");

// The transaction pooler does not support prepared statements.
const client = postgres(url, { prepare: false, max: 1, idle_timeout: 20, connect_timeout: 10 });

export const db = drizzle({ client, schema });
