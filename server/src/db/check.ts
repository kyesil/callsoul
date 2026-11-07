import postgres from "postgres";

const url = process.env.DATABASE_URL;
if (!url) throw new Error("DATABASE_URL is not set");

const sql = postgres(url, { max: 1, prepare: false, connect_timeout: 15 });

const started = performance.now();
const tables = await sql`select tablename from pg_tables where schemaname = 'public' order by 1`;
const connectMs = Math.round(performance.now() - started);

const samples: number[] = [];
for (let i = 0; i < 5; i++) {
  const t = performance.now();
  await sql`select 1`;
  samples.push(Math.round(performance.now() - t));
}

console.log(`public tables: ${tables.map((t) => t.tablename).join(", ") || "(none)"}`);
console.log(`first query incl. connect: ${connectMs} ms`);
console.log(`warm query: ${samples.join(", ")} ms`);

await sql.end();
