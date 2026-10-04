<script setup lang="ts">
import { computed } from "vue";
import { label } from "@/lib/format";
const props = defineProps<{ status: string; text?: string }>();
const tone = computed(() =>
  /^(ACTIVE|COMPLETED|SETTLED|SETTLED_ZERO|SUCCEEDED|SUCCESS|APPLIED|UP|CONFIGURED|PUBLIC|PUBLISHED)$/.test(
    props.status,
  )
    ? "good"
    : /ERROR|FAILED|CONFLICT|SUSPENDED|DEGRADED|UNAVAILABLE/.test(props.status)
      ? "bad"
      : /PAST_DUE|OPEN|ATTENTION|AWAITING/.test(props.status)
        ? "warn"
        : /RUNNING|QUEUED|SCHEDULED|TRIALING|READY|PENDING/.test(props.status)
          ? "info"
          : "neutral",
);
</script>
<template>
  <span class="badge" :class="tone"
    ><span class="status-dot"></span>{{ text || label(status) }}</span
  >
</template>
