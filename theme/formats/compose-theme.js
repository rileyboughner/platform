// Custom Style Dictionary format: a Kotlin object exposing the palette as
// Jetpack Compose Color values.
// Registered via theme/config.js -> hooks.formats["compose/theme"].
export default function composeTheme({ dictionary }) {
  const toPropName = (name) =>
    name.replace(/_(.)/g, (_, c) => c.toUpperCase());

  const props = dictionary.allTokens.map((token) => {
    // token.value is already an 8-digit AARRGGBB hex string here (the
    // "android" transformGroup's color/hex8 transform ran first).
    let hex = token.value.replace("#", "").toUpperCase();
    if (hex.length === 6) hex = "FF" + hex;
    return `    val ${toPropName(token.name)} = Color(0x${hex})`;
  });

  return [
    "// GENERATED FILE. Do not hand-edit.",
    "// Source: theme/tokens.json, built by Style Dictionary (see theme/config.js).",
    "",
    "package platform.theme",
    "",
    "import androidx.compose.ui.graphics.Color",
    "",
    "object PlatformTheme {",
    ...props,
    "}",
    "",
  ].join("\n");
}
