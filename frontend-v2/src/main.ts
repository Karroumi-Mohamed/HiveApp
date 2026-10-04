import { createApp } from "vue";
import "@fontsource-variable/inter";
import "./styles.css";
import "./theme.css";
import App from "./App.vue";
import { router } from "./router";
import { restoreSession } from "./data/session";
import { initializeAppearance } from "./data/appearance";

initializeAppearance();
await restoreSession();
createApp(App).use(router).mount("#app");
