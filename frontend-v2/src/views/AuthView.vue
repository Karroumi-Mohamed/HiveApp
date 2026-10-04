<script setup lang="ts">
import { computed, ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { adminApi } from "@/api/admin-api";
import { session, acceptSession, signOut } from "@/data/session";
import { errorMessage } from "@/lib/format";
const route = useRoute(),
  router = useRouter(),
  kind = computed(() => String(route.meta.auth)),
  email = ref(""),
  password = ref(""),
  confirmation = ref(""),
  busy = ref(false),
  error = ref(""),
  done = ref(false);
const token = computed(() => String(route.query.token || ""));
const title = computed(() =>
  kind.value === "activation"
    ? "Activate access"
    : kind.value === "verification"
      ? "Verify email"
      : kind.value === "initial"
        ? "Set your password"
        : token.value
          ? "Reset password"
          : "Password reset",
);
async function submit() {
  busy.value = true;
  error.value = "";
  try {
    if (kind.value === "verification") {
      if (!token.value) throw Error("Verification link is missing.");
      await adminApi.completeEmailVerification(token.value);
    } else if (kind.value === "reset" && !token.value)
      await adminApi.requestPasswordReset(email.value);
    else {
      if (password.value !== confirmation.value)
        throw Error("Passwords do not match.");
      if (kind.value === "initial") {
        if (!session.initialAccessToken)
          throw Error("Sign in with your temporary password first.");
        await acceptSession(
          await adminApi.changeInitialPassword(
            session.initialAccessToken,
            password.value,
          ),
        );
        await router.push("/overview");
        return;
      }
      if (!token.value) throw Error("Access link is missing.");
      if (kind.value === "activation")
        await adminApi.completeActivation(token.value, password.value);
      else await adminApi.completePasswordReset(token.value, password.value);
    }
    done.value = true;
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
async function cancel() {
  await signOut();
  await router.push("/login");
}
</script>
<template>
  <main class="auth-page">
    <form class="auth-form" @submit.prevent="submit">
      <img src="/hive.svg" alt="Hive" />
      <h1>{{ title }}</h1>
      <p v-if="done">
        {{
          kind === "reset" && !token
            ? "If the address has an account, a reset link will be sent."
            : "Completed. You can sign in."
        }}
      </p>
      <template v-else
        ><label v-if="kind === 'reset' && !token" class="field"
          >Email<input
            v-model="email"
            type="email"
            required
            autocomplete="username" /></label
        ><template v-else-if="kind !== 'verification'"
          ><label class="field"
            >New password<input
              v-model="password"
              type="password"
              required
              minlength="8"
              maxlength="128"
              autocomplete="new-password" /></label
          ><label class="field"
            >Confirm password<input
              v-model="confirmation"
              type="password"
              required
              autocomplete="new-password" /></label
        ></template>
        <p v-if="error" class="notice error" role="alert">{{ error }}</p>
        <button class="button primary" :disabled="busy">
          {{
            busy
              ? "Processing…"
              : kind === "verification"
                ? "Verify email"
                : kind === "reset" && !token
                  ? "Send reset link"
                  : "Save password"
          }}
        </button></template
      ><button
        v-if="kind === 'initial'"
        type="button"
        class="text-link"
        @click="cancel"
      >
        Sign out</button
      ><RouterLink v-else class="text-link" to="/login">Sign in</RouterLink>
    </form>
  </main>
</template>
