import { describe, expect, test } from "bun:test";
import {
  commercialAmount,
  compareExactDecimals,
  formatExactMoney,
  isCommercialAmount,
  subtractExactDecimals,
} from "./exact-decimal";

describe("exact commercial decimals", () => {
  test("accepts the backend precision boundary without numeric coercion", () => {
    expect(isCommercialAmount("999999999999999.9999")).toBe(true);
    expect(isCommercialAmount("1000000000000000.0000")).toBe(false);
    expect(isCommercialAmount("1.00000")).toBe(false);
    expect(isCommercialAmount("-1")).toBe(false);
    expect(commercialAmount(" 123456789012345.6789 ")).toBe("123456789012345.6789");
  });

  test("orders exact values beyond JavaScript's safe integer range", () => {
    expect(compareExactDecimals("999999999999998.9999", "999999999999999.0000")).toBeLessThan(0);
    expect(compareExactDecimals("12.3400", "12.34")).toBe(0);
  });

  test("subtracts exact values without IEEE-754 coercion", () => {
    expect(subtractExactDecimals("123456789012345.6789", "0.6789", "5.0000")).toBe("123456789012340.0000");
    expect(() => subtractExactDecimals("1.0000", "1.0001")).toThrow();
  });

  test("formats all four decimals instead of truncating through Number", () => {
    const formatted = formatExactMoney("123456789012345.6789", "MAD");
    expect(formatted).toContain("6789");
    expect(formatted).toContain("123");
  });
});
