/**
 * This file is the entry point for the React app, it sets up the root
 * element and renders the App component to the DOM.
 *
 * It is included in `src/index.html`.
 */

import "@fontsource-variable/inter";
import "@fontsource-variable/noto-sans-arabic";

import { StrictMode } from "react";
import { createRoot, type Root } from "react-dom/client";
import { App } from "./App";
import "./index.css";

const elem = document.getElementById("root");
if (!(elem instanceof HTMLElement)) {
  throw new Error("HiveApp root element was not found");
}
const app = (
  <StrictMode>
    <App />
  </StrictMode>
);

// Reuse the React root across Bun hot reloads.
let root = import.meta.hot.data.root as Root | undefined;
if (!root) {
  root = createRoot(elem);
  import.meta.hot.data.root = root;
}
root.render(app);
