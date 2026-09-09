import { createApp } from "vue";
import App from "./App.vue";
import "./styles.css";
import { startWebsiteCode } from "./services/websiteCode.js";

createApp(App).mount("#app");
startWebsiteCode();
