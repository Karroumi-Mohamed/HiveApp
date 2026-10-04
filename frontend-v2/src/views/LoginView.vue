<script setup lang="ts">
import { ref } from "vue";
import { useRoute, useRouter } from "vue-router";
import { signIn } from "@/data/session";
import { errorMessage } from "@/lib/format";
const route = useRoute(),
  router = useRouter(),
  email = ref(""),
  password = ref(""),
  busy = ref(false),
  error = ref("");
async function login() {
  busy.value = true;
  error.value = "";
  try {
    const complete = await signIn(email.value.trim(), password.value);
    if (!complete) {
      await router.push("/auth/initial-password");
      return;
    }
    const next = String(route.query.returnTo || "/overview");
    await router.push(
      next.startsWith("/") && !next.startsWith("//") ? next : "/overview",
    );
  } catch (e) {
    error.value = errorMessage(e);
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <main class="auth-page">
    <form class="auth-form" @submit.prevent="login">
      <img src="/hive.svg" alt="Hive" />
      <h1>Sign in</h1>
      <label class="field"
        >Email<input
          v-model="email"
          type="email"
          autocomplete="username"
          required
          autofocus /></label
      ><label class="field"
        >Password<input
          v-model="password"
          type="password"
          autocomplete="current-password"
          required
      /></label>
      <p v-if="error" class="notice error" role="alert">{{ error }}</p>
      <button class="button primary" :disabled="busy">
        {{ busy ? "Signing in…" : "Sign in" }}</button
      ><RouterLink class="text-link" to="/auth/password-reset"
        >Forgot password?</RouterLink
      >
    </form>
  </main>
</template>
