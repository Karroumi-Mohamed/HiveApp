import { useEffect, useState } from "react";

/**
 * Holds a value back until it has stopped changing for `delayMs`.
 *
 * This is not interchangeable with useDeferredValue. That hook lowers the render
 * priority of an update but still settles on every value the user types, so a
 * query keyed on its result still fires once per keystroke. This one drops the
 * intermediate values entirely, so a seven-character search costs one request
 * instead of six.
 */
export function useDebouncedValue<T>(value: T, delayMs = 250): T {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs);
    return () => clearTimeout(timer);
  }, [value, delayMs]);

  return debounced;
}
