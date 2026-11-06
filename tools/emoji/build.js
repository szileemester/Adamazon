// Builds the icon picker's emoji catalogue: every fully-qualified emoji (no skin-tone variants),
// in Unicode order, with its Hungarian name and keywords from CLDR.
//
//   npm install && npm run build
//
// Output: app/src/main/assets/emoji_hu.json – [{ e, g, n, k }], where g is the group slug,
// n the Hungarian name and k the search keywords joined by "|".

import { readFileSync, writeFileSync } from "node:fs";

const load = (path) => JSON.parse(readFileSync(new URL(path, import.meta.url), "utf8"));

const byEmoji = load("./node_modules/unicode-emoji-json/data-by-emoji.json");
const ordered = load("./node_modules/unicode-emoji-json/data-ordered-emoji.json");
const annotations = {
  ...load("./node_modules/cldr-annotations-derived-full/annotationsDerived/hu/annotations.json").annotationsDerived.annotations,
  ...load("./node_modules/cldr-annotations-full/annotations/hu/annotations.json").annotations.annotations,
};

const GROUPS = {
  "Smileys & Emotion": "smileys",
  "People & Body": "people",
  "Animals & Nature": "nature",
  "Food & Drink": "food",
  "Travel & Places": "travel",
  "Activities": "activities",
  "Objects": "objects",
  "Symbols": "symbols",
  "Flags": "flags",
};

// CLDR keys usually leave out the emoji variation selector.
const lookup = (emoji) => annotations[emoji] ?? annotations[emoji.replaceAll("️", "")];

const catalog = [];
let unnamed = 0;
for (const emoji of ordered) {
  const info = byEmoji[emoji];
  const group = info && GROUPS[info.group];
  if (!group) continue;
  const note = lookup(emoji);
  if (!note) {
    unnamed++;
    continue;
  }
  const name = (note.tts ?? note.default)[0];
  const keywords = [...new Set(note.default ?? [])].filter((k) => k !== name);
  catalog.push({ e: emoji, g: group, n: name, k: keywords.join("|") });
}

const out = new URL("../../app/src/main/assets/emoji_hu.json", import.meta.url);
writeFileSync(out, JSON.stringify(catalog));
console.log(`${catalog.length} emoji written, ${unnamed} skipped without a Hungarian name`);
