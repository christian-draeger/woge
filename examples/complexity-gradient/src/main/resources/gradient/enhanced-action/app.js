import { createWogePatchRuntime, installWogeActionForms } from "/assets/woge/index.js";

installWogeActionForms(document, createWogePatchRuntime(document));
