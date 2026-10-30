import { readFileSync } from "node:fs";
import { after, before, beforeEach, describe, it } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import { doc, getDoc, setDoc } from "firebase/firestore";

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

beforeEach(async () => {
  await env.clearFirestore();
  await env.withSecurityRulesDisabled(async (ctx) => {
    await setDoc(doc(ctx.firestore(), "allowedUsers/anna@gmail.com"), { displayName: "Anna" });
    await setDoc(doc(ctx.firestore(), "stores/s1"), { name: "Auchan" });
  });
});

const user = (email, verified = true) =>
  env.authenticatedContext(email, { email, email_verified: verified }).firestore();

describe("allowed user", () => {
  it("reads and writes stores, items, cards", async () => {
    const db = user("anna@gmail.com");
    await assertSucceeds(getDoc(doc(db, "stores/s1")));
    await assertSucceeds(setDoc(doc(db, "stores/s1/items/i1"), { name: "Tej" }));
    await assertSucceeds(setDoc(doc(db, "cards/c1/images/front"), { data: "x" }));
    await assertSucceeds(setDoc(doc(db, "learned/tej"), { categoryId: "tejtermek" }));
  });

  it("matches the allow-list case-insensitively", async () => {
    await assertSucceeds(getDoc(doc(user("Anna@Gmail.com"), "stores/s1")));
  });

  it("can read only its own allow-list entry and never write it", async () => {
    const db = user("anna@gmail.com");
    await assertSucceeds(getDoc(doc(db, "allowedUsers/anna@gmail.com")));
    await assertFails(getDoc(doc(db, "allowedUsers/bela@gmail.com")));
    await assertFails(setDoc(doc(db, "allowedUsers/bela@gmail.com"), {}));
  });

  it("cannot use unknown collections", async () => {
    await assertFails(setDoc(doc(user("anna@gmail.com"), "other/x"), {}));
  });
});

describe("not allowed", () => {
  it("signed-in stranger sees nothing but can check its own entry", async () => {
    const db = user("idegen@gmail.com");
    await assertFails(getDoc(doc(db, "stores/s1")));
    await assertFails(setDoc(doc(db, "stores/s1/items/i1"), { name: "Tej" }));
    await assertSucceeds(getDoc(doc(db, "allowedUsers/idegen@gmail.com")));
  });

  it("unverified e-mail is rejected even if listed", async () => {
    await assertFails(getDoc(doc(user("anna@gmail.com", false), "stores/s1")));
  });

  it("anonymous user is rejected", async () => {
    await assertFails(getDoc(doc(env.unauthenticatedContext().firestore(), "stores/s1")));
  });
});
