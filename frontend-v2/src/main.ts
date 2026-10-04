import { createApp } from "vue";
import "@fontsource-variable/inter";
import "./styles.css";
import App from "./App.vue";
import { router } from "./router";
import { restoreSession } from "./data/session";

await restoreSession();
createApp(App).use(router).mount("#app");
