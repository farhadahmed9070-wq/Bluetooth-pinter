const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_printer_owner";
const BOB_UID = "bob_other_user";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function validTemplatePayload(uid) {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    name: "Cafe Standard 58mm",
    businessName: "Blue Roast Coffee",
    businessSubtitle: "42 Harbor St • (555) 204-8891",
    headerNote: "Welcome!",
    footerNote: "Thank you for visiting!",
    paperWidthMm: 58,
    currencySymbol: "$",
    showDate: true,
    showLogoHeader: true,
    defaultItems: [
      { name: "Flat White", quantity: 1, unitPriceCents: 450 },
      { name: "Butter Croissant", quantity: 2, unitPriceCents: 375 },
    ],
    createdAt: now,
    updatedAt: now,
  };
}

function validOrderPayload(uid, status = "printed") {
  const now = new Date(Date.now() - 1000);
  return {
    userId: uid,
    orderNumber: "ORD-1001",
    orderType: "receipt",
    title: "Blue Roast Coffee",
    businessName: "Blue Roast Coffee",
    businessSubtitle: "42 Harbor St",
    footerNote: "Thank you!",
    textContent: "",
    items: [{ name: "Flat White", quantity: 1, unitPriceCents: 450 }],
    totalCents: 450,
    currencySymbol: "$",
    paperWidthMm: 58,
    printerName: "MTP-II Thermal",
    printerAddress: "00:11:22:33:44:55",
    status,
    createdAt: now,
    updatedAt: now,
  };
}

test("Unauthenticated users cannot read or write templates or orders", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("templates").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("templates").doc("tpl_1").set(validTemplatePayload(ALICE_UID))
  );
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("orders").get());
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("orders").doc("ord_1").set(validOrderPayload(ALICE_UID))
  );
});

test("Owner can create, list, update, and delete receipt templates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const tplCol = aliceDb.collection("users").doc(ALICE_UID).collection("templates");

  await assertSucceeds(tplCol.doc("tpl_1").set(validTemplatePayload(ALICE_UID)));
  await assertSucceeds(tplCol.orderBy("updatedAt", "desc").get());
  await assertSucceeds(
    tplCol.doc("tpl_1").update({
      name: "Cafe Standard 80mm",
      paperWidthMm: 80,
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject ghost field & @DocumentId 'id' injection
  await assertFails(
    tplCol.doc("tpl_2").set({
      ...validTemplatePayload(ALICE_UID),
      id: "tpl_2",
    })
  );
  await assertFails(
    tplCol.doc("tpl_1").update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  // Reject invalid paperWidthMm
  await assertFails(
    tplCol.doc("tpl_1").update({
      paperWidthMm: 100,
      updatedAt: new Date(Date.now() - 500),
    })
  );
  // Reject immutable createdAt mutation
  await assertFails(
    tplCol.doc("tpl_1").update({
      createdAt: new Date(Date.now() - 200),
      updatedAt: new Date(Date.now() - 200),
    })
  );

  await assertSucceeds(tplCol.doc("tpl_1").delete());
});

test("Owner can create, transition status, reprint, and list orders", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ordCol = aliceDb.collection("users").doc(ALICE_UID).collection("orders");

  await assertSucceeds(ordCol.doc("ord_queued").set(validOrderPayload(ALICE_UID, "queued")));
  await assertSucceeds(ordCol.orderBy("createdAt", "desc").get());

  // Transition queued -> printed
  await assertSucceeds(
    ordCol.doc("ord_queued").update({
      status: "printed",
      printerName: "RONGTA RPP02N",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reprint already printed order (status must stay 'printed')
  await assertSucceeds(
    ordCol.doc("ord_queued").update({
      printerName: "RONGTA RPP02N",
      printerAddress: "AA:BB:CC:DD:EE:FF",
      updatedAt: new Date(Date.now() - 200),
    })
  );

  // Terminal state lock: cannot revert printed -> queued
  await assertFails(
    ordCol.doc("ord_queued").update({
      status: "queued",
      updatedAt: new Date(Date.now() - 100),
    })
  );

  await assertSucceeds(ordCol.doc("ord_queued").delete());
});

test("Cross-user isolation blocks Bob from accessing Alice's templates or orders", async () => {
  await testEnv.withSecurityRulesDisabled(async (ctx) => {
    const db = ctx.firestore();
    await db.collection("users").doc(ALICE_UID).collection("templates").doc("tpl_alice").set(validTemplatePayload(ALICE_UID));
    await db.collection("users").doc(ALICE_UID).collection("orders").doc("ord_alice").set(validOrderPayload(ALICE_UID));
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("templates").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("templates").doc("tpl_alice").get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("templates").doc("tpl_alice").update({
      name: "Hijacked",
      updatedAt: new Date(Date.now() - 500),
    })
  );
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("orders").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("orders").doc("ord_alice").delete());
});
