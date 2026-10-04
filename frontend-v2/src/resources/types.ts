// These descriptors are UI metadata. All operations use the domain API clients.
export type RecordData = Record<string, any>;
export type Choice = { value: string; label: string; record?: RecordData };
export type Field = {
  key: string;
  label: string;
  type?:
    | "text"
    | "textarea"
    | "number"
    | "decimal"
    | "select"
    | "checkbox"
    | "datetime"
    | "choices"
    | "choice"
    | "array"
    | "password"
    | "permissions";
  required?: boolean;
  hidden?: boolean;
  contextKeys?: string[];
  emptyLink?: () => { label: string; to: string } | undefined;
  onSelect?: (data: RecordData, choice: Choice) => void;
  min?: number;
  max?: number;
  options?: Choice[];
  resolve?: (ids: string[]) => Promise<Choice[]>;
  load?: (
    search: string,
    page: number,
    data: RecordData,
  ) => Promise<{ options: Choice[]; totalPages: number }>;
  fields?: Field[];
  defaults?: RecordData;
  show?: (data: RecordData) => boolean;
  full?: boolean;
  help?: string;
};
export type Column = {
  key: string;
  label: string;
  format?: "status" | "date" | "money" | "boolean";
  currencyKey?: string;
  link?: (row: RecordData) => string | undefined;
};
export type Action = {
  key: string;
  label: string;
  permission: string;
  fields?: Field[];
  defaults?: (data: RecordData) => RecordData;
  visible?: (data: RecordData) => boolean;
  preview?: (data: RecordData, input: RecordData) => Promise<unknown>;
  execute: (
    data: RecordData,
    input: RecordData,
    preview: RecordData | undefined,
  ) => Promise<unknown>;
  destination?: (result: RecordData, data: RecordData) => string | undefined;
  destructive?: boolean;
  reason?: boolean;
  readOnly?: boolean;
  paginated?: boolean;
};
export type Section = {
  group?: { key: string; label: string };
  key: string;
  label: string;
  permission: string;
  load: (data: RecordData, page: number) => Promise<unknown>;
  columns?: Column[];
  actions?: (row: RecordData, data: RecordData) => Action[];
};
export type Resource = {
  key: string;
  title: string;
  singular: string;
  base: string;
  listPermission: string;
  listPermissions?: string[];
  readPermission: string;
  list: (query: RecordData) => Promise<unknown>;
  detail: (id: string) => Promise<unknown>;
  alternateDetail?: {
    permission: string;
    load: (id: string) => Promise<unknown>;
  }[];
  normalize?: (data: RecordData) => RecordData;
  columns: Column[];
  facts?: Column[];
  detailKeys?: string[];
  listOnly?: boolean;
  searchable?: boolean;
  filters?: { key: string; label: string; values: string[] }[];
  bulkActions?: Action[];
  listActions?: Action[];
  createPermission?: string;
  createPermissions?: string[];
  creationPermission?: (input: RecordData) => string;
  createRequirements?: string[];
  editPermission?: string;
  fields?: Field[];
  defaults?: RecordData;
  editable?: (data: RecordData) => boolean;
  save?: (
    id: string | undefined,
    input: RecordData,
    data: RecordData | undefined,
  ) => Promise<unknown>;
  saveDestination?: (result: RecordData) => string;
  actions?: Action[];
  sections?: Section[];
  sortable?: { key: string; label: string }[];
  links?: (data: RecordData) => { label: string; to: string }[];
  formGroups?: { label: string; keys: string[] }[];
};
export function get(data: RecordData | undefined, path: string) {
  return path.split(".").reduce((value, key) => value?.[key], data as any);
}
export function set(data: RecordData, path: string, value: unknown) {
  const keys = path.split(".");
  let parent = data;
  for (const key of keys.slice(0, -1)) parent = parent[key] ??= {};
  parent[keys[keys.length - 1]!] = value;
}
export function rows(data: any): RecordData[] {
  return Array.isArray(data)
    ? data
    : Array.isArray(data?.content)
      ? data.content
      : [];
}
export function normalizeInput(input: RecordData, fields: Field[]): RecordData {
  const output: RecordData = {};
  for (const field of fields) {
    if (field.show && !field.show(input)) continue;
    let value = get(input, field.key);
    if (field.type === "datetime")
      value = value ? new Date(value).toISOString() : null;
    if (field.type === "number")
      value = value === "" || value == null ? null : Number(value);
    if (field.type === "array")
      value = (value || []).map((row: RecordData) =>
        normalizeInput(row, field.fields || []),
      );
    if (value === "" && !field.required) value = null;
    set(output, field.key, value);
  }
  return output;
}

export function validateFields(
  input: RecordData,
  fields: Field[],
  prefix = "",
): string | undefined {
  for (const field of fields) {
    if (field.show && !field.show(input)) continue;
    const value = get(input, field.key);
    const name = prefix + field.label;
    if (
      field.required &&
      (value == null ||
        value === "" ||
        (field.type === "checkbox" && !value) ||
        (Array.isArray(value) && !value.length))
    )
      return name + " is required.";
    if (field.type === "array" && Array.isArray(value))
      for (const [i, row] of value.entries()) {
        const issue = validateFields(
          row,
          field.fields || [],
          name + " " + (i + 1) + " · ",
        );
        if (issue) return issue;
      }
  }
  return undefined;
}
