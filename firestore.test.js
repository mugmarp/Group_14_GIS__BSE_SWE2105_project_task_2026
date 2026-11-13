const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";
const CHARLIE_UID = "charlie_789";

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

test("Unauthenticated users cannot create groups", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("groups").doc("grp_1").set({
      name: "Family",
      inviteCode: "FAM-123",
      createdBy: "random",
      memberIds: ["random"],
      createdAt: new Date().toISOString()
    })
  );
});

test("Authenticated user can create a group if they are creator and member", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_1").set({
      name: "Alice Family",
      inviteCode: "ALC-123",
      createdBy: ALICE_UID,
      memberIds: [ALICE_UID],
      createdAt: new Date().toISOString()
    })
  );
});

test("Group member can publish their own location, but not another member's", async () => {
  // Setup group with Alice and Bob
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc("grp_1").set({
      name: "Team 14",
      inviteCode: "GRP-14",
      createdBy: ALICE_UID,
      memberIds: [ALICE_UID, BOB_UID]
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  // Bob publishes Bob's location -> SUCCESS
  await assertSucceeds(
    bobDb.collection("groups").doc("grp_1").collection("live_locations").doc(BOB_UID).set({
      userId: BOB_UID,
      displayName: "Bob",
      latitude: 0.3476,
      longitude: 32.5825,
      accuracy: 5.0,
      speed: 1.4,
      bearing: 90.0,
      altitude: 1190.0,
      batteryPercent: 85,
      isCharging: false,
      isSharingEnabled: true,
      lastUpdated: new Date().toISOString()
    })
  );

  // Bob tries to publish Alice's location -> FAILS
  await assertFails(
    bobDb.collection("groups").doc("grp_1").collection("live_locations").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Spoofed",
      latitude: 0.3476,
      longitude: 32.5825,
      lastUpdated: new Date().toISOString()
    })
  );
});

test("Non-member Charlie cannot read group live locations", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc("grp_1").set({
      name: "Secret Team",
      inviteCode: "SEC-99",
      createdBy: ALICE_UID,
      memberIds: [ALICE_UID, BOB_UID]
    });
    await context.firestore().collection("groups").doc("grp_1").collection("live_locations").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      latitude: 0.3476,
      longitude: 32.5825,
      lastUpdated: new Date().toISOString()
    });
  });

  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  await assertFails(
    charlieDb.collection("groups").doc("grp_1").collection("live_locations").doc(ALICE_UID).get()
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertSucceeds(
    bobDb.collection("groups").doc("grp_1").collection("live_locations").doc(ALICE_UID).get()
  );
});

test("Same user can publish multiple distinct deviceId docs in a group without overwriting", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("groups").doc("grp_multi").set({
      name: "Device Fleet",
      inviteCode: "DEV-01",
      createdBy: ALICE_UID,
      memberIds: [ALICE_UID]
    });
  });

  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_multi").collection("live_locations").doc(`${ALICE_UID}_phone1`).set({
      userId: ALICE_UID,
      deviceId: "phone1",
      displayName: "Pixel 8",
      latitude: 0.34,
      longitude: 32.58,
      lastUpdated: new Date().toISOString()
    })
  );
  await assertSucceeds(
    aliceDb.collection("groups").doc("grp_multi").collection("live_locations").doc(`${ALICE_UID}_tablet1`).set({
      userId: ALICE_UID,
      deviceId: "tablet1",
      displayName: "Galaxy Tab",
      latitude: 0.35,
      longitude: 32.59,
      lastUpdated: new Date().toISOString()
    })
  );
});

test("Personal devices collection allows owner R/W/ring and blocks other users", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_phone").set({
      userId: ALICE_UID,
      deviceId: "dev_phone",
      deviceName: "Work Phone",
      latitude: 0.33,
      longitude: 32.57,
      ringRequestedAt: 0,
      lastUpdated: new Date().toISOString()
    })
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_phone").update({
      ringRequestedAt: Date.now(),
      ringRequestedBy: "Tablet"
    })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_phone").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("devices").doc("dev_phone").update({
      ringRequestedAt: Date.now()
    })
  );
});
