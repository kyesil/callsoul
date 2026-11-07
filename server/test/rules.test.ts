import { describe, expect, it } from "vitest";
import { cleanName, isCategory, isDeviceId, isE164, verdictOf, voteDelta } from "../src/rules.js";

describe("verdictOf", () => {
  it("does not let one device decide", () => {
    expect(verdictOf(1, 0)).toBe("unknown");
    expect(verdictOf(0, 1)).toBe("unknown");
  });

  it("needs enough votes and a clear majority", () => {
    expect(verdictOf(3, 0)).toBe("spam");
    expect(verdictOf(7, 3)).toBe("spam");
    expect(verdictOf(3, 3)).toBe("unknown");
    expect(verdictOf(1, 3)).toBe("safe");
    expect(verdictOf(0, 0)).toBe("unknown");
  });
});

describe("voteDelta", () => {
  it("adds a device's first vote", () => {
    expect(voteDelta(undefined, true)).toEqual({ spam: 1, safe: 0 });
    expect(voteDelta(undefined, false)).toEqual({ spam: 0, safe: 1 });
  });

  it("moves a changed vote to the other side", () => {
    expect(voteDelta(true, false)).toEqual({ spam: -1, safe: 1 });
    expect(voteDelta(false, true)).toEqual({ spam: 1, safe: -1 });
  });

  it("does not count a repeated vote", () => {
    expect(voteDelta(true, true)).toEqual({ spam: 0, safe: 0 });
    expect(voteDelta(false, false)).toEqual({ spam: 0, safe: 0 });
  });
});

describe("validation", () => {
  it("accepts only E.164 numbers", () => {
    expect(isE164("+905321234567")).toBe(true);
    expect(isE164("05321234567")).toBe(false);
    expect(isE164("+0123456789")).toBe(false);
    expect(isE164(905321234567)).toBe(false);
  });

  it("checks categories and device ids", () => {
    expect(isCategory("scam")).toBe(true);
    expect(isCategory("x")).toBe(false);
    expect(isDeviceId("3f2b8c1e-7a44-4d21-9b3c-0a1b2c3d4e5f")).toBe(true);
    expect(isDeviceId("abc")).toBe(false);
  });

  it("cleans names", () => {
    expect(cleanName("  Kargo   Firması ")).toBe("Kargo Firması");
    expect(cleanName("   ")).toBeNull();
    expect(cleanName(5)).toBeNull();
    expect(cleanName("a".repeat(100))?.length).toBe(60);
  });
});
