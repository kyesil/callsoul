import { deletionPage } from "../src/privacy-page.js";

/** How to have your data deleted, linked from Google Play's data safety section; also at /delete-data. */
export const GET = () =>
  new Response(deletionPage, { headers: { "content-type": "text/html; charset=utf-8" } });
