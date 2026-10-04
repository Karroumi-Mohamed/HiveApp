<script setup lang="ts">
import { ref } from "vue";
import {
  appearance,
  resolvedAppearance,
  setAppearance,
  type Appearance,
} from "@/data/appearance";

const options: { value: Appearance; label: string }[] = [
  { value: "light", label: "Light" },
  { value: "dark", label: "Dark" },
  { value: "system", label: "System" },
];
const savedLocally = ref(true);
function choose(value: Appearance) {
  savedLocally.value = setAppearance(value);
}
</script>
<template>
  <section class="report-section">
    <fieldset class="appearance-settings">
      <legend>Appearance</legend>
      <p class="muted small">Saved for your profile in this browser.</p>
      <div class="appearance-options">
        <label
          v-for="option in options"
          :key="option.value"
          class="appearance-option"
        >
          <input
            type="radio"
            name="appearance"
            :value="option.value"
            :checked="appearance === option.value"
            @change="choose(option.value)"
          />
          <span>{{ option.label }}</span>
        </label>
      </div>
      <p v-if="appearance === 'system'" class="muted small">
        System is currently using {{ resolvedAppearance }} appearance.
      </p>
      <p v-if="!savedLocally" class="notice warning" role="status">
        Applied for this session. Browser storage is unavailable.
      </p>
    </fieldset>
  </section>
</template>
<style scoped>
.appearance-settings {
  border: 0;
  padding: 0;
  margin: 0;
}
legend {
  font-size: 16px;
  font-weight: 580;
  margin-bottom: 8px;
}
.appearance-options {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin: 20px 0 14px;
}
.appearance-option {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 48px;
  padding: 12px 18px;
  border: 1px solid var(--border);
  border-radius: 7px;
  cursor: pointer;
}
.appearance-option:has(input:checked) {
  border-color: var(--accent);
  background: var(--accent-soft);
}
.appearance-option input {
  width: 16px;
  height: 16px;
  margin: 0;
  accent-color: var(--accent);
}
</style>
