// Custom Style Dictionary format: plain JSON of color tokens, for Nix's
// builtins.fromJSON / lib.importJSON, as an alternative to theme.nix.
// Registered via theme/config.js -> hooks.formats["nix/json"].
export default function nixJson({ dictionary }) {
  const out = {};
  dictionary.allTokens.forEach((token) => {
    out[token.name] = token.value;
  });
  return JSON.stringify(out, null, 2) + "\n";
}
