// Custom Style Dictionary format: Nix attrset of color tokens.
// Registered via theme/config.js -> hooks.formats["nix/attrset"].
export default function nixAttrset({ dictionary }) {
  const lines = dictionary.allTokens.map(
    (token) => `    ${token.name} = "${token.value}";`
  );

  return [
    "# GENERATED FILE. Do not hand-edit.",
    "# Source: theme/tokens.json, built by Style Dictionary (see theme/config.js).",
    "# Import into a home-manager module, e.g.:",
    "#   theme = import ./theme.nix;",
    "{",
    ...lines,
    "}",
    "",
  ].join("\n");
}
