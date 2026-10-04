import type { ExactDecimal } from "@/api/contracts";

const COMMERCIAL_AMOUNT = /^\d{1,15}(?:\.\d{1,4})?$/;

/** The backend accepts non-negative commercial amounts with 15 integer and 4 fractional digits. */
export function isCommercialAmount(value: string): value is ExactDecimal {
  return COMMERCIAL_AMOUNT.test(value.trim());
}

export function commercialAmount(value: string): ExactDecimal {
  return value.trim();
}

/** Compare two non-negative exact decimals without converting either value to IEEE-754. */
export function compareExactDecimals(
  left: ExactDecimal,
  right: ExactDecimal,
): number {
  const [leftIntegerRaw = "0", leftFraction = ""] = left.split(".");
  const [rightIntegerRaw = "0", rightFraction = ""] = right.split(".");
  const leftInteger = leftIntegerRaw.replace(/^0+(?=\d)/, "");
  const rightInteger = rightIntegerRaw.replace(/^0+(?=\d)/, "");
  if (leftInteger.length !== rightInteger.length)
    return leftInteger.length < rightInteger.length ? -1 : 1;
  const integerOrder = leftInteger.localeCompare(rightInteger);
  if (integerOrder !== 0) return integerOrder;
  return leftFraction
    .padEnd(4, "0")
    .localeCompare(rightFraction.padEnd(4, "0"));
}

/** Subtract non-negative exact decimals at the backend's four-decimal scale. */
export function subtractExactDecimals(
  left: ExactDecimal,
  ...subtractors: ExactDecimal[]
): ExactDecimal {
  const scaled = (value: ExactDecimal) => {
    const [integer = "0", fraction = ""] = value.split(".");
    return BigInt(integer) * 10_000n + BigInt(fraction.padEnd(4, "0"));
  };
  const result = subtractors.reduce(
    (current, value) => current - scaled(value),
    scaled(left),
  );
  if (result < 0n)
    throw new RangeError(
      "Exact decimal subtraction cannot produce a negative commercial amount",
    );
  const integer = result / 10_000n;
  const fraction = (result % 10_000n).toString().padStart(4, "0");
  return `${integer}.${fraction}`;
}

/** Sum non-negative exact decimals at the backend's four-decimal commercial scale. */
export function sumExactDecimals(values: ExactDecimal[]): ExactDecimal {
  const total = values.reduce((sum, value) => {
    const [integer = "0", fraction = ""] = value.split(".");
    return sum + BigInt(integer) * 10_000n + BigInt(fraction.padEnd(4, "0"));
  }, 0n);
  const integer = total / 10_000n;
  const fraction = (total % 10_000n).toString().padStart(4, "0");
  return `${integer}.${fraction}`;
}

/** Intl accepts decimal strings exactly even though TypeScript's older declaration omits them. */
export function formatExactMoney(
  amount: ExactDecimal,
  currency: string,
): string {
  const formatter = new Intl.NumberFormat("fr-MA", {
    style: "currency",
    currency,
    maximumFractionDigits: 4,
  });
  const exactFormat = formatter.format as unknown as (value: string) => string;
  return exactFormat(amount);
}

/** Separate visual amount/unit styling without converting the exact decimal to a number. */
export function formatExactMoneyParts(
  amount: ExactDecimal,
  currency: string,
): Intl.NumberFormatPart[] {
  const formatter = new Intl.NumberFormat("fr-MA", {
    style: "currency",
    currency,
    maximumFractionDigits: 4,
  });
  const exactParts = formatter.formatToParts.bind(formatter) as unknown as (
    value: string,
  ) => Intl.NumberFormatPart[];
  return exactParts(amount);
}
