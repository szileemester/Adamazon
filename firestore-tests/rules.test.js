import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  Bytes, collection, deleteDoc, deleteField, doc, getDoc, getDocs, query, setDoc, updateDoc, where, writeBatch,
} from "firebase/firestore";

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
    // Anna and Béla share g1 (which also holds the pre-group data to copy); Cili is in g2;
    // Dani is allowed but not yet in a group.
    await setDoc(doc(db, "groups/g1"), { name: "Otthon", legacyImport: true });
    await setDoc(doc(db, "groups/g2"), { name: "Kovács család" });
    await setDoc(doc(db, "allowedUsers/anna@gmail.com"), { displayName: "Anna", groupId: "g1" });
    await setDoc(doc(db, "allowedUsers/bela@gmail.com"), { displayName: "Béla", groupId: "g1" });
    await setDoc(doc(db, "allowedUsers/cili@gmail.com"), { displayName: "Cili", groupId: "g2" });
    await setDoc(doc(db, "allowedUsers/dani@gmail.com"), { displayName: "Dani" });
    await setDoc(doc(db, "groups/g1/stores/s1"), store());
    await setDoc(doc(db, "groups/g1/stores/s1/items/i1"), item());
    await setDoc(doc(db, "groups/g1/categories/egyeb"), { name: "Egyéb", emoji: "🛒", sortIndex: 12 });
    await setDoc(doc(db, "groups/g1/cards/c1"), { name: "DM", storeId: "s1", sortIndex: 0 });
    await setDoc(doc(db, "groups/g2/stores/s9"), store({ name: "Spar" }));
    await setDoc(doc(db, "stores/old1"), store());
    await setDoc(doc(db, "stores/old1/items/i1"), item());
    await setDoc(doc(db, "cards/oldc/images/front"), { data: bytes(10) });
  });
});

const user = (email, verified = true) =>
  env.authenticatedContext(email, { email, email_verified: verified }).firestore();
const anna = () => user("anna@gmail.com");
const cili = () => user("cili@gmail.com");

describe("access", () => {
  it("allowed user reads and writes", async () => {
    const db = anna();
    await assertSucceeds(getDoc(doc(db, "groups/g1/stores/s1")));
    await assertSucceeds(setDoc(doc(db, "groups/g1/stores/s1/items/i2"), item({ name: "Tej" })));
  });

  it("matches the allow-list case-insensitively", async () => {
    await assertSucceeds(getDoc(doc(user("Anna@Gmail.com"), "groups/g1/stores/s1")));
  });

  it("reads its own and its group's allow-list entries, never writes them", async () => {
    const db = anna();
    await assertSucceeds(getDoc(doc(db, "allowedUsers/anna@gmail.com")));
    await assertSucceeds(getDoc(doc(db, "allowedUsers/bela@gmail.com")));
    await assertFails(getDoc(doc(db, "allowedUsers/cili@gmail.com")));
    await assertFails(getDoc(doc(db, "allowedUsers/dani@gmail.com")));
    await assertFails(setDoc(doc(db, "allowedUsers/bela@gmail.com"), {}));
    await assertFails(updateDoc(doc(db, "allowedUsers/anna@gmail.com"), { groupId: "g2" }));
  });

  it("lists the members of its own group only", async () => {
    const db = anna();
    await assertSucceeds(getDocs(query(collection(db, "allowedUsers"), where("groupId", "==", "g1"))));
    await assertFails(getDocs(query(collection(db, "allowedUsers"), where("groupId", "==", "g2"))));
    await assertFails(getDocs(collection(db, "allowedUsers")));
  });

  it("strangers, unverified and anonymous users are rejected", async () => {
    await assertFails(getDoc(doc(user("idegen@gmail.com"), "groups/g1/stores/s1")));
    await assertSucceeds(getDoc(doc(user("idegen@gmail.com"), "allowedUsers/idegen@gmail.com")));
    await assertFails(getDoc(doc(user("anna@gmail.com", false), "groups/g1/stores/s1")));
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "groups/g1/stores/s1")));
  });

  it("strangers, unverified users and users without a group see no group", async () => {
    await assertFails(getDoc(doc(user("idegen@gmail.com"), "groups/g1/stores/s1")));
    await assertFails(getDoc(doc(user("anna@gmail.com", false), "groups/g1")));
    await assertFails(getDoc(doc(user("dani@gmail.com"), "groups/g1/stores/s1")));
    await assertSucceeds(getDoc(doc(user("dani@gmail.com"), "allowedUsers/dani@gmail.com")));
  });

  it("unknown collections are closed", async () => {
    await assertFails(setDoc(doc(anna(), "groups/g1/other/x"), {}));
  });
});

describe("groups", () => {
  it("a member reads its group and can only mark the legacy copy done", async () => {
    const db = anna();
    await assertSucceeds(getDoc(doc(db, "groups/g1")));
    await assertSucceeds(updateDoc(doc(db, "groups/g1"), { migratedAt: 1761900000000 }));
    await assertFails(updateDoc(doc(db, "groups/g1"), { name: "Enyém" }));
    await assertFails(updateDoc(doc(db, "groups/g1"), { migratedAt: "most" }));
    await assertFails(setDoc(doc(db, "groups/g3"), { name: "Új" }));
    await assertFails(deleteDoc(doc(db, "groups/g1")));
  });

  it("another group's data can be neither read nor written", async () => {
    const db = cili();
    await assertSucceeds(getDoc(doc(db, "groups/g2/stores/s9")));
    await assertFails(getDoc(doc(db, "groups/g1")));
    await assertFails(getDoc(doc(db, "groups/g1/stores/s1")));
    await assertFails(getDocs(collection(db, "groups/g1/stores")));
    await assertFails(getDoc(doc(db, "groups/g1/stores/s1/items/i1")));
    await assertFails(setDoc(doc(db, "groups/g1/stores/s1/items/i2"), item()));
    await assertFails(deleteDoc(doc(db, "groups/g1/stores/s1")));
    await assertFails(getDoc(doc(db, "groups/g1/cards/c1")));
  });

  it("pre-group data is readable only by the legacy group, and never writable", async () => {
    await assertSucceeds(getDocs(collection(anna(), "stores")));
    await assertSucceeds(getDoc(doc(anna(), "stores/old1/items/i1")));
    await assertSucceeds(getDoc(doc(anna(), "cards/oldc/images/front")));
    await assertFails(getDocs(collection(cili(), "stores")));
    await assertFails(setDoc(doc(anna(), "stores/old1/items/i2"), item()));
    await assertFails(updateDoc(doc(anna(), "stores/old1"), { name: "X" }));
    await assertFails(deleteDoc(doc(anna(), "stores/old1")));
  });
});

describe("every write the app makes is accepted", () => {
  it("stores", async () => {
    const db = anna();
    await assertSucceeds(setDoc(doc(db, "groups/g1/stores/s2"), store({ description: "Nyitva 7-21" })));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/stores/s1"), { name: "Auchan Óbuda", domain: null, colorArgb: 0, description: "x".repeat(255) }));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/stores/s1"), { description: null }));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/stores/s1"), { logo: bytes(20_000) }));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/stores/s1"), { logo: deleteField() }));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/stores/s1"), { categoryScores: { zoldseg: 0.1, egyeb: 1 }, orderLocked: true }));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/stores/s1"), { session: { startedAt: 1, lastActivityAt: 2, sequence: ["zoldseg"] } }));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/stores/s1"), { session: deleteField() }));
    const batch = writeBatch(db);
    batch.update(doc(db, "groups/g1/stores/s1"), { sortIndex: 3 });
    await assertSucceeds(batch.commit());
    await assertSucceeds(deleteDoc(doc(db, "groups/g1/stores/s1")));
  });

  it("items", async () => {
    const db = anna();
    await assertSucceeds(setDoc(doc(db, "groups/g1/stores/s1/items/i2"), item({ quantity: null, createdBy: null })));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/stores/s1/items/i1"), { checked: true, checkedAt: 1761900000001 }));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/stores/s1/items/i1"), { checked: false, checkedAt: null, quantity: "3 kg", createdAt: 2 }));
    await assertSucceeds(setDoc(doc(db, "groups/g1/stores/s1/items/i1"), item({ name: "https://my.linistry.com/" + "a".repeat(200) })));
    await assertSucceeds(deleteDoc(doc(db, "groups/g1/stores/s1/items/i1")));
  });

  it("categories, learned names, cards", async () => {
    const db = anna();
    await assertSucceeds(setDoc(doc(db, "groups/g1/categories/k1"), { name: "Baby ápolás/étel", emoji: "👶", sortIndex: 13 }));
    await assertSucceeds(updateDoc(doc(db, "groups/g1/categories/egyeb"), { sortIndex: 14 }));
    await assertSucceeds(deleteDoc(doc(db, "groups/g1/categories/k1")));
    await assertSucceeds(setDoc(doc(db, "groups/g1/learned/paradicsom"), { displayName: "Paradicsom", categoryId: "gyumolcs", emoji: "🍎" }));
    await assertSucceeds(setDoc(doc(db, "groups/g1/cards/c2"), {
      name: "Auchan kártya", storeId: null, sortIndex: 1, thumb: bytes(50_000),
      barcodeValue: "5998817311417", barcodeFormat: "EAN_13",
    }, { merge: true }));
    await assertSucceeds(setDoc(doc(db, "groups/g1/cards/c2/images/front"), { data: bytes(600_000) }));
    await assertSucceeds(deleteDoc(doc(db, "groups/g1/cards/c2/images/back")));
    await assertSucceeds(deleteDoc(doc(db, "groups/g1/cards/c2")));
  });
});

describe("bad data is rejected", () => {
  it("store description over 255 characters", async () => {
    await assertFails(updateDoc(doc(anna(), "groups/g1/stores/s1"), { description: "x".repeat(256) }));
  });

  it("empty or missing names", async () => {
    const db = anna();
    await assertFails(setDoc(doc(db, "groups/g1/stores/s1/items/i2"), item({ name: "" })));
    await assertFails(updateDoc(doc(db, "groups/g1/stores/s1"), { name: "" }));
    await assertFails(setDoc(doc(db, "groups/g1/categories/k1"), { emoji: "x", sortIndex: 1 }));
  });

  it("unknown fields", async () => {
    const db = anna();
    await assertFails(updateDoc(doc(db, "groups/g1/stores/s1"), { hacked: true }));
    await assertFails(setDoc(doc(db, "groups/g1/stores/s1/items/i2"), item({ price: 100 })));
  });

  it("wrong types", async () => {
    const db = anna();
    await assertFails(updateDoc(doc(db, "groups/g1/stores/s1/items/i1"), { checked: "yes" }));
    await assertFails(updateDoc(doc(db, "groups/g1/stores/s1"), { sortIndex: "first" }));
  });

  it("oversized images", async () => {
    const db = anna();
    await assertFails(setDoc(doc(db, "groups/g1/cards/c1/images/front"), { data: bytes(1_000_000) }));
    await assertFails(updateDoc(doc(db, "groups/g1/stores/s1"), { logo: bytes(300_000) }));
  });

  it("card image under an unknown name", async () => {
    await assertFails(setDoc(doc(anna(), "groups/g1/cards/c1/images/third"), { data: bytes(10) }));
  });

  it("the fallback category cannot be deleted", async () => {
    await assertFails(deleteDoc(doc(anna(), "groups/g1/categories/egyeb")));
  });
});
