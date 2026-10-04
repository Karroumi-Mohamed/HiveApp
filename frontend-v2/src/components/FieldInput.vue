<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from "vue";
import type { Field, RecordData, Choice } from "@/resources/types";
import { get, set } from "@/resources/types";
import { errorMessage } from "@/lib/format";
import RolePermissionsEditor from "./settings/RolePermissionsEditor.vue";
const props = defineProps<{
  field: Field;
  data: RecordData;
  prefix?: string;
  context?: RecordData;
}>();
const emptyLink = computed(() => props.field.emptyLink?.());
const id = computed(() =>
  ("field-" + (props.prefix || "") + props.field.key).replaceAll(".", "-"),
);
const value = computed({
  get: () => get(props.data, props.field.key),
  set: (v) => set(props.data, props.field.key, v),
});
const picker = ref<HTMLDetailsElement>(),
  resolved = ref<Choice[]>([]);
const selectedOptions = computed(() => {
  const all = [...resolved.value, ...options.value];
  const ids = Array.isArray(value.value)
    ? value.value
    : value.value
      ? [String(value.value)]
      : [];
  return ids.map(
    (id: string) =>
      all.find((o) => o.value === id) || {
        value: id,
        label: "Selected record",
      },
  );
});
const options = ref<Choice[]>(props.field.options || []),
  search = ref(""),
  page = ref(0),
  total = ref(1),
  loading = ref(false),
  error = ref("");
let version = 0,
  timer: ReturnType<typeof setTimeout>;
async function load() {
  if (!props.field.load) return;
  const request = ++version;
  loading.value = true;
  error.value = "";
  try {
    const result = await props.field.load(search.value, page.value, {
      ...props.context,
      ...props.data,
    });
    if (request !== version) return;
    options.value = result.options;
    total.value = result.totalPages;
  } catch (e) {
    if (request === version) error.value = errorMessage(e);
  } finally {
    if (request === version) loading.value = false;
  }
}
watch(
  () => value.value,
  async (v) => {
    if (!props.field.resolve) return;
    try {
      resolved.value = await props.field.resolve(
        Array.isArray(v) ? v : v ? [String(v)] : [],
      );
    } catch {
      resolved.value = [];
    }
  },
  { immediate: true, deep: true },
);
watch(
  () => props.field,
  () => {
    search.value = "";
    page.value = 0;
    options.value = props.field.options || [];
    void load();
  },
  { immediate: true },
);
watch(
  () =>
    JSON.stringify(
      props.field.contextKeys?.map((k) =>
        get({ ...props.context, ...props.data }, k),
      ),
    ),
  (next, old) => {
    if (!old || next === old) return;
    value.value = null;
    page.value = 0;
    void load();
  },
);
watch(search, () => {
  clearTimeout(timer);
  timer = setTimeout(() => {
    page.value = 0;
    void load();
  }, 250);
});
watch(page, () => void load());
onUnmounted(() => {
  clearTimeout(timer);
  version++;
});
function selected(choice: string) {
  return Array.isArray(value.value) && value.value.includes(choice);
}
function choose(choice: string) {
  value.value = choice;
  const option = options.value.find((x) => x.value === choice);
  if (option) props.field.onSelect?.(props.data, option);
  if (picker.value) picker.value.open = false;
}
function toggle(choice: string) {
  const existing = Array.isArray(value.value) ? value.value : [];
  value.value = selected(choice)
    ? existing.filter((x: string) => x !== choice)
    : [...existing, choice];
}
function add() {
  value.value = [
    ...(value.value || []),
    structuredClone(props.field.defaults || {}),
  ];
}
function remove(index: number) {
  value.value = (value.value || []).filter(
    (_: unknown, i: number) => i !== index,
  );
}
function dateValue(v: unknown) {
  if (!v) return "";
  const date = new Date(String(v));
  if (!Number.isFinite(date.getTime())) return String(v);
  return new Date(date.getTime() - date.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
}
</script>
<template>
  <div
    v-if="!field.hidden && (!field.show || field.show(data))"
    class="field"
    :class="{
      'full-width':
        field.full || field.type === 'array' || field.type === 'choices',
    }"
  >
    <label :id="id + '-label'" :for="id"
      >{{ field.label
      }}<span v-if="field.required" class="required-mark"> *</span></label
    >
    <RolePermissionsEditor
      v-if="field.type === 'permissions'"
      :id="id"
      :labelled-by="id + '-label'"
      :max-selections="field.max"
      :known-permissions="data._permissionDetails"
      v-model="value"
    />
    <textarea
      v-else-if="field.type === 'textarea'"
      :id="id"
      v-model="value"
      :required="field.required"
      rows="3"
    />
    <input
      v-else-if="field.type === 'checkbox'"
      :id="id"
      type="checkbox"
      :checked="!!value"
      :required="field.required"
      @change="value = ($event.target as HTMLInputElement).checked"
    />
    <template v-else-if="field.type === 'array'"
      ><div
        v-for="(row, index) in value || []"
        :key="index"
        class="array-editor"
      >
        <div class="array-editor-header">
          <strong>{{ field.label }} {{ Number(index) + 1 }}</strong
          ><button
            type="button"
            class="text-link"
            @click="remove(Number(index))"
          >
            Remove
          </button>
        </div>
        <div class="editor-grid">
          <FieldInput
            v-for="nested in field.fields"
            :key="nested.key + nested.label"
            :field="nested"
            :data="row"
            :context="{ ...context, ...data }"
            :prefix="id + '-' + index"
          />
        </div>
      </div>
      <button type="button" class="button small" @click="add">
        Add {{ field.label.toLowerCase() }}
      </button></template
    >
    <template v-else-if="field.type === 'choices' || field.type === 'choice'">
      <details ref="picker" class="record-picker">
        <summary :id="id" :aria-labelledby="id + '-label ' + id + '-selection'">
          <span :id="id + '-selection'">
            {{
              selectedOptions.length
                ? field.type === "choices"
                  ? selectedOptions.length + " selected"
                  : selectedOptions[0]?.label
                : "Select " + field.label.toLowerCase()
            }}
          </span>
        </summary>
        <div class="picker-options">
          <input
            v-if="field.load"
            v-model="search"
            class="choice-search"
            type="search"
            :aria-label="'Search ' + field.label"
            placeholder="Search"
          />
          <p v-if="error" class="notice error" role="alert">
            {{ error }}
            <button type="button" class="text-link" @click="load">Retry</button>
          </p>
          <span v-if="loading" class="muted small">Loading…</span>
          <div class="choice-list">
            <label v-for="option in options" :key="option.value"
              ><input
                :type="field.type === 'choice' ? 'radio' : 'checkbox'"
                :name="id"
                :checked="
                  field.type === 'choice'
                    ? value === option.value
                    : selected(option.value)
                "
                @change="
                  field.type === 'choice'
                    ? choose(option.value)
                    : toggle(option.value)
                "
              />{{ option.label }}</label
            >
            <p v-if="!options.length && !loading && !error" class="muted small">
              No matches
            </p>
            <RouterLink
              v-if="
                !options.length && !loading && !error && !search && emptyLink
              "
              class="text-link"
              :to="emptyLink.to"
              >{{ emptyLink.label }}</RouterLink
            >
          </div>
          <p
            v-if="field.type === 'choices' && value?.length"
            class="muted small"
          >
            {{ value.length }} selected
          </p>
          <div v-if="total > 1" class="row">
            <button
              type="button"
              class="button small"
              :disabled="page === 0 || loading"
              @click="page--"
            >
              Previous</button
            ><span class="small">Page {{ page + 1 }} of {{ total }}</span
            ><button
              type="button"
              class="button small"
              :disabled="page + 1 >= total || loading"
              @click="page++"
            >
              Next
            </button>
          </div>
        </div>
      </details>
      <div
        v-if="field.type === 'choices' && selectedOptions.length"
        class="selected-records"
      >
        <button
          v-for="option in selectedOptions.slice(0, 3)"
          type="button"
          class="selected-record"
          @click="toggle(option.value)"
        >
          {{ option.label }} ×
        </button>
        <details v-if="selectedOptions.length > 3">
          <summary class="text-link">
            {{ selectedOptions.length - 3 }} more
          </summary>
          <div class="selected-records">
            <button
              v-for="option in selectedOptions.slice(3)"
              :key="option.value"
              type="button"
              class="selected-record"
              @click="toggle(option.value)"
            >
              {{ option.label }} ×
            </button>
          </div>
        </details>
      </div>
    </template>
    <select
      v-else-if="field.type === 'select'"
      :id="id"
      v-model="value"
      :required="field.required"
    >
      <option v-if="!field.required" :value="null">None</option>
      <option
        v-for="option in options"
        :key="option.value"
        :value="option.value"
      >
        {{ option.label }}
      </option>
    </select>
    <input
      v-else-if="field.type === 'datetime'"
      :id="id"
      type="datetime-local"
      :value="dateValue(value)"
      :required="field.required"
      @input="value = ($event.target as HTMLInputElement).value"
    />
    <input
      v-else
      :id="id"
      v-model="value"
      :type="
        field.type === 'number'
          ? 'number'
          : field.type === 'password'
            ? 'password'
            : 'text'
      "
      :inputmode="field.type === 'decimal' ? 'decimal' : undefined"
      :pattern="field.type === 'decimal' ? '[0-9]+([.][0-9]{1,4})?' : undefined"
      :step="field.type === 'number' ? '1' : undefined"
      :min="field.min"
      :max="field.max"
      :required="field.required"
    />
    <small v-if="field.help" class="muted">{{ field.help }}</small>
  </div>
</template>
