import { privacyPage } from "../src/privacy-page.js";

/** The privacy policy Google Play links to, also at /privacy. */
export const GET = () =>
  new Response(privacyPage, { headers: { "content-type": "text/html; charset=utf-8" } });
