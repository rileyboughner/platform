// Style Dictionary v4 config. The platform/file definitions live in
// config.base.json (plain JSON); this file layers in the custom format
// functions, since JSON alone can't carry function references.
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";

import nixAttrset from "./formats/nix-attrset.js";
import nixJson from "./formats/nix-json.js";
import composeTheme from "./formats/compose-theme.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const base = JSON.parse(
  readFileSync(join(__dirname, "config.base.json"), "utf8")
);

export default {
  ...base,
  hooks: {
    formats: {
      "nix/attrset": nixAttrset,
      "nix/json": nixJson,
      "compose/theme": composeTheme,
    },
  },
};
