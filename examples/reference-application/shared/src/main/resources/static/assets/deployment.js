import { createWogePatchRuntime, installWogeActionForms } from "./woge/index.js";

const runtime = createWogePatchRuntime(document);
installWogeActionForms(document, runtime);
