import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { Bytes, deleteDoc, deleteField, doc, getDoc, setDoc, updateDoc, writeBatch } from "firebase/firestore";

let env;

before(async () => {
  env = await initializeTestEnvironment({
    projectId: "demo-adamazon",
    firestore: {
      rules: readFileSync(new URL("../firestore.rules", import.meta.url), "utf8"),
      host: "127.0.0.1",
      port: 8080,
    },
  });
});

after(() => env.cleanup());

// Documents shaped exactly like the app writes them (see data/Repositories.kt, data/Mappers.kt).
const store = (extra = {}) => ({
  name: "Auchan", domain: "auchan.hu", colorArgb: 4294957272, sortIndex: 0,
  orderLocked: false, description: null, ...extra,
});
const item = (extra = {}) => ({
  name: "Paradicsom", quantity: "2 kg", categoryId: "zoldseg", emoji: "🍅", checked: false,
  checkedAt: null, createdAt: 1761900000000, createdBy: "anna@gmail.com", ...extra,
});
const bytes = (n) => Bytes.fromUint8Array(new Uint8Array(n));

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await setDoc(doc(db, "allowedUsers/anna@gmail.com"), { displayName: "Anna" });
    await setDoc(doc(db, "stores/s1"), store());
    await setDoc(doc(db, "stores/s1/items/i1"), item());
    await setDoc(doc(db, "categories/egyeb"), { name: "Egyéb", emoji: "🛒", sortIndex: 12 });
    await setDoc(doc(db, "cards/c1"), { name: "DM", storeId: "s1", sortIndex: 0 });
  });
});

const user = (email, verified = true) =>
  env.authenticatedContext(email, { email, email_verified: verified }).firestore();
const anna = () => user("anna@gmail.com");

describe("access", () => {
  it("allowed user reads and writes", async () => {
    const db = anna();
    await assertSucceeds(getDoc(doc(db, "stores/s1")));
    await assertSucceeds(setDoc(doc(db, "stores/s1/items/i2"), item({ name: "Tej" })));
  });

  it("matches the allow-list case-insensitively", async () => {
    await assertSucceeds(getDoc(doc(user("Anna@Gmail.com"), "stores/s1")));
  });

  it("can read only its own allow-list entry and never write it", async () => {
    const db = anna();
    await assertSucceeds(getDoc(doc(db, "allowedUsers/anna@gmail.com")));
    await assertFails(getDoc(doc(db, "allowedUsers/bela@gmail.com")));
    await assertFails(setDoc(doc(db, "allowedUsers/bela@gmail.com"), {}));
  });

  it("strangers, unverified and anonymous users are rejected", async () => {
    await assertFails(getDoc(doc(user("idegen@gmail.com"), "stores/s1")));
    await assertSucceeds(getDoc(doc(user("idegen@gmail.com"), "allowedUsers/idegen@gmail.com")));
    await assertFails(getDoc(doc(user("anna@gmail.com", false), "stores/s1")));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "stores/s1")));
  });

  it("unknown collections are closed", async () => {
    await assertFails(setDoc(doc(anna(), "other/x"), {}));
  });
});

describe("every write the app makes is accepted", () => {
  it("stores", async () => {
    const db = anna();
    await assertSucceeds(setDoc(doc(db, "stores/s2"), store({ description: "Nyitva 7-21" })));
    await assertSucceeds(updateDoc(doc(db, "stores/s1"), { name: "Auchan Óbuda", domain: null, colorArgb: 0, description: "x".repeat(255) }));
    await assertSucceeds(updateDoc(doc(db, "stores/s1"), { description: null }));
    await assertSucceeds(updateDoc(doc(db, "stores/s1"), { logo: bytes(20_000) }));
    await assertSucceeds(updateDoc(doc(db, "stores/s1"), { logo: deleteField() }));
    await assertSucceeds(updateDoc(doc(db, "stores/s1"), { categoryScores: { zoldseg: 0.1, egyeb: 1 }, orderLocked: true }));
    await assertSucceeds(updateDoc(doc(db, "stores/s1"), { session: { startedAt: 1, lastActivityAt: 2, sequence: ["zoldseg"] } }));
    await assertSucceeds(updateDoc(doc(db, "stores/s1"), { session: deleteField() }));
    const batch = writeBatch(db);
    batch.update(doc(db, "stores/s1"), { sortIndex: 3 });
    await assertSucceeds(batch.commit());
    await assertSucceeds(deleteDoc(doc(db, "stores/s1")));
  });

  it("items", async () => {
    const db = anna();
    await assertSucceeds(setDoc(doc(db, "stores/s1/items/i2"), item({ quantity: null, createdBy: null })));
    await assertSucceeds(updateDoc(doc(db, "stores/s1/items/i1"), { checked: true, checkedAt: 1761900000001 }));
    await assertSucceeds(updateDoc(doc(db, "stores/s1/items/i1"), { checked: false, checkedAt: null, quantity: "3 kg", createdAt: 2 }));
    await assertSucceeds(setDoc(doc(db, "stores/s1/items/i1"), item({ name: "https://my.linistry.com/" + "a".repeat(200) })));
    await assertSucceeds(deleteDoc(doc(db, "stores/s1/items/i1")));
  });

  it("categories, learned names, cards", async () => {
    const db = anna();
    await assertSucceeds(setDoc(doc(db, "categories/k1"), { name: "Baby ápolás/étel", emoji: "👶", sortIndex: 13 }));
    await assertSucceeds(updateDoc(doc(db, "categories/egyeb"), { sortIndex: 14 }));
    await assertSucceeds(deleteDoc(doc(db, "categories/k1")));
    await assertSucceeds(setDoc(doc(db, "learned/paradicsom"), { displayName: "Paradicsom", categoryId: "gyumolcs", emoji: "🍎" }));
    await assertSucceeds(setDoc(doc(db, "cards/c2"), {
      name: "Auchan kártya", storeId: null, sortIndex: 1, thumb: bytes(50_000),
      barcodeValue: "5998817311417", barcodeFormat: "EAN_13",
    }, { merge: true }));
    await assertSucceeds(setDoc(doc(db, "cards/c2/images/front"), { data: bytes(600_000) }));
    await assertSucceeds(deleteDoc(doc(db, "cards/c2/images/back")));
    await assertSucceeds(deleteDoc(doc(db, "cards/c2")));
  });
});

describe("bad data is rejected", () => {
  it("store description over 255 characters", async () => {
    await assertFails(updateDoc(doc(anna(), "stores/s1"), { description: "x".repeat(256) }));
  });

  it("empty or missing names", async () => {
    const db = anna();
    await assertFails(setDoc(doc(db, "stores/s1/items/i2"), item({ name: "" })));
    await assertFails(updateDoc(doc(db, "stores/s1"), { name: "" }));
    await assertFails(setDoc(doc(db, "categories/k1"), { emoji: "x", sortIndex: 1 }));
  });

  it("unknown fields", async () => {
    const db = anna();
    await assertFails(updateDoc(doc(db, "stores/s1"), { hacked: true }));
    await assertFails(setDoc(doc(db, "stores/s1/items/i2"), item({ price: 100 })));
  });

  it("wrong types", async () => {
    const db = anna();
    await assertFails(updateDoc(doc(db, "stores/s1/items/i1"), { checked: "yes" }));
    await assertFails(updateDoc(doc(db, "stores/s1"), { sortIndex: "first" }));
  });

  it("oversized images", async () => {
    const db = anna();
    await assertFails(setDoc(doc(db, "cards/c1/images/front"), { data: bytes(1_000_000) }));
    await assertFails(updateDoc(doc(db, "stores/s1"), { logo: bytes(300_000) }));
  });

  it("card image under an unknown name", async () => {
    await assertFails(setDoc(doc(anna(), "cards/c1/images/third"), { data: bytes(10) }));
  });

  it("the fallback category cannot be deleted", async () => {
    await assertFails(deleteDoc(doc(anna(), "categories/egyeb")));
  });
});
