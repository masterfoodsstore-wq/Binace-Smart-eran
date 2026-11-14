const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");
const serverTimestamp = () => new Date(Date.now() - 1000);

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_001";
const BOB_UID = "bob_uid_002";
const ADMIN_UID = "admin_uid_999";
const ADMIN_EMAIL = "masterfoodsstore@gmail.com";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function validUserDoc(uid, username, referralCode, extra = {}) {
  return {
    uid,
    fullName: `${username} Full`,
    username,
    email: `${username}@example.com`,
    phone: "+923001234567",
    referralCode,
    walletId: `BSE-WLT-${uid.slice(-4)}`,
    realBalance: 0,
    realRewardBalance: 0,
    todayTaskEarnings: 0,
    todayTaskDate: "2026-11-14",
    totalEarnedTasks: 0,
    completedTasksCount: 0,
    totalReferrals: 0,
    totalReferralCommission: 0,
    accountStatus: "active",
    role: "user",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    ...extra,
  };
}

function validWalletDoc(uid) {
  return {
    uid,
    walletId: `BSE-WLT-${uid.slice(-4)}`,
    balance: 0,
    depositBalance: 0,
    rewardBalance: 0,
    todayTaskEarnings: 0,
    todayTaskDate: "2026-11-14",
    totalEarnedTasks: 0,
    totalReferralCommission: 0,
    currency: "PKR",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  };
}

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

test("Unauthenticated user: cannot read users, wallets, tasks, deposits, or settings", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("wallets").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("tasks").get());
  await assertFails(unauthDb.collection("deposits").get());
  await assertFails(unauthDb.collection("settings").doc("global").get());
});

test("User registration & wallet isolation: Alice creates profile + wallet, cannot read Bob's or tamper balances", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserDoc(ALICE_UID, "alice", "ALICE1001"))
  );
  await assertSucceeds(
    aliceDb.collection("wallets").doc(ALICE_UID).set(validWalletDoc(ALICE_UID))
  );
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set(validUserDoc(BOB_UID, "bobuser", "BOB1002"))
  );
  await assertSucceeds(
    bobDb.collection("wallets").doc(BOB_UID).set(validWalletDoc(BOB_UID))
  );

  // Cross-user PII & wallet blocked
  await assertFails(aliceDb.collection("users").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("users").get());
  await assertFails(aliceDb.collection("wallets").doc(BOB_UID).get());
  await assertFails(aliceDb.collection("wallets").get());

  // Direct balance or role tampering blocked
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      realBalance: 99999,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("wallets").doc(ALICE_UID).update({
      balance: 99999,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      role: "admin",
      updatedAt: serverTimestamp(),
    })
  );
});

test("Email spoofing test: unverified admin email cannot access admin collections or arbitrary task write", async () => {
  const spoofDb = testEnv
    .authenticatedContext("spoof_uid", { email: ADMIN_EMAIL, email_verified: false })
    .firestore();

  await assertFails(
    spoofDb.collection("tasks").doc("custom_spoof_task").set({
      title: "Spoof Task",
      description: "Check in daily",
      category: "daily_login",
      rewardPoints: 999,
      dailyLimit: 1,
      durationSeconds: 0,
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(spoofDb.collection("users").get());
  await assertFails(spoofDb.collection("adminLogs").get());
});

test("Canonical 10-task bootstrap & atomic dailyTaskCompletions + wallets claim prevents duplicates", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserDoc(ALICE_UID, "alice", "ALICE1001"))
  );
  await assertSucceeds(
    aliceDb.collection("wallets").doc(ALICE_UID).set(validWalletDoc(ALICE_UID))
  );

  // Canonical 20 PKR default task bootstrap allowed for signed-in users
  await assertSucceeds(
    aliceDb.collection("tasks").doc("task_01_daily_login").set({
      title: "Daily login check-in",
      description: "Check in daily for PKR 20 reward",
      category: "daily_login",
      rewardPoints: 20,
      dailyLimit: 1,
      durationSeconds: 0,
      isActive: true,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Non-canonical reward or ID blocked for non-admin
  await assertFails(
    aliceDb.collection("tasks").doc("task_01_daily_login").update({
      rewardPoints: 500,
      updatedAt: serverTimestamp(),
    })
  );

  // Start task
  const utId = `${ALICE_UID}_task_01_daily_login_2026-11-14`;
  await assertSucceeds(
    aliceDb.collection("dailyTaskCompletions").doc(utId).set({
      uid: ALICE_UID,
      taskId: "task_01_daily_login",
      taskTitle: "Daily login check-in",
      dateKey: "2026-11-14",
      status: "in_progress",
      rewardPoints: 20,
      startedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Atomic batch claim succeeds for exact reward (20) across dailyTaskCompletions, users, wallets, and transactions
  const batch = aliceDb.batch();
  batch.update(aliceDb.collection("dailyTaskCompletions").doc(utId), {
    status: "claimed",
    completedAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  batch.set(aliceDb.collection("transactions").doc(`tx_${utId}`), {
    uid: ALICE_UID,
    type: "task_reward",
    amount: 20,
    status: "completed",
    description: "Daily login check-in",
    referenceId: utId,
    createdAt: serverTimestamp(),
  });
  batch.update(aliceDb.collection("users").doc(ALICE_UID), {
    realRewardBalance: 20,
    todayTaskEarnings: 20,
    todayTaskDate: "2026-11-14",
    totalEarnedTasks: 20,
    completedTasksCount: 1,
    lastClaimedUserTaskId: utId,
    updatedAt: serverTimestamp(),
  });
  batch.update(aliceDb.collection("wallets").doc(ALICE_UID), {
    balance: 20,
    rewardBalance: 20,
    todayTaskEarnings: 20,
    todayTaskDate: "2026-11-14",
    totalEarnedTasks: 20,
    lastClaimedTaskCompletionId: utId,
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(batch.commit());

  // Duplicate claim on same day fails!
  const dupBatch = aliceDb.batch();
  dupBatch.update(aliceDb.collection("users").doc(ALICE_UID), {
    realRewardBalance: 40,
    todayTaskEarnings: 40,
    todayTaskDate: "2026-11-14",
    totalEarnedTasks: 40,
    completedTasksCount: 2,
    lastClaimedUserTaskId: utId,
    updatedAt: serverTimestamp(),
  });
  await assertFails(dupBatch.commit());
});

test("Deposit with paymentMethod, withdrawal, and PKR 100 referral commission flow security boundaries", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Alice signs up + wallet + referral code
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validUserDoc(ALICE_UID, "alice", "ALICE1001"))
  );
  await assertSucceeds(
    aliceDb.collection("wallets").doc(ALICE_UID).set(validWalletDoc(ALICE_UID))
  );
  await assertSucceeds(
    aliceDb.collection("referralCodes").doc("ALICE1001").set({
      code: "ALICE1001",
      uid: ALICE_UID,
      username: "alice",
      createdAt: serverTimestamp(),
    })
  );

  // Self-referral fails
  await assertFails(
    aliceDb.collection("referrals").doc(ALICE_UID).set({
      referrerUid: ALICE_UID,
      referredUid: ALICE_UID,
      referredUsername: "alice",
      referralCode: "ALICE1001",
      registrationDate: serverTimestamp(),
      firstDepositVerified: false,
      status: "registered",
      commissionAmount: 0,
      commissionStatus: "none",
      updatedAt: serverTimestamp(),
    })
  );

  // Bob registers with Alice's referral code
  await assertSucceeds(
    bobDb.collection("users").doc(BOB_UID).set(
      validUserDoc(BOB_UID, "bobuser", "BOB1002", {
        referredBy: "ALICE1001",
        referredByUid: ALICE_UID,
      })
    )
  );
  await assertSucceeds(
    bobDb.collection("wallets").doc(BOB_UID).set(validWalletDoc(BOB_UID))
  );
  await assertSucceeds(
    bobDb.collection("referrals").doc(BOB_UID).set({
      referrerUid: ALICE_UID,
      referredUid: BOB_UID,
      referredUsername: "bobuser",
      referralCode: "ALICE1001",
      registrationDate: serverTimestamp(),
      firstDepositVerified: false,
      status: "registered",
      commissionAmount: 0,
      commissionStatus: "none",
      updatedAt: serverTimestamp(),
    })
  );

  // Deposit without paymentMethod fails
  await assertFails(
    bobDb.collection("deposits").doc("dep_bad").set({
      depositId: "dep_bad",
      uid: BOB_UID,
      userUid: BOB_UID,
      username: "bobuser",
      amount: 500,
      reference: "TX-7788",
      transactionReference: "TX-7788",
      refKey: "TX-7788",
      status: "PENDING",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Deposit without depositReferences atomic batch entry fails
  await assertFails(
    bobDb.collection("deposits").doc("dep_no_ref_guard").set({
      depositId: "dep_no_ref_guard",
      uid: BOB_UID,
      userUid: BOB_UID,
      username: "bobuser",
      fullName: "bobuser Full",
      userEmail: "bobuser@example.com",
      userPhone: "+923001234567",
      amount: 500,
      paymentMethod: "EasyPaisa",
      paymentAccountName: "Binace Merchant",
      paymentAccountNumber: "03001112233",
      reference: "TXN-NO-GUARD",
      transactionReference: "TXN-NO-GUARD",
      refKey: "TXN-NO-GUARD",
      status: "PENDING",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Bob submits pending deposit with paymentMethod + atomic depositReferences guard
  const depBatch = bobDb.batch();
  depBatch.set(bobDb.collection("deposits").doc("dep_1"), {
    depositId: "dep_1",
    uid: BOB_UID,
    userUid: BOB_UID,
    username: "bobuser",
    fullName: "bobuser Full",
    userEmail: "bobuser@example.com",
    userPhone: "+923001234567",
    amount: 500,
    paymentMethod: "EasyPaisa",
    paymentAccountName: "Binace Merchant",
    paymentAccountNumber: "03001112233",
    reference: "TXN-REF-7788",
    transactionReference: "TXN-REF-7788",
    refKey: "TXN-REF-7788",
    receiptStoragePath: `deposit_receipts/${BOB_UID}/dep_1/receipt.jpg`,
    receiptUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/receipt.jpg",
    status: "PENDING",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  depBatch.set(bobDb.collection("depositReferences").doc("TXN-REF-7788"), {
    refKey: "TXN-REF-7788",
    transactionReference: "TXN-REF-7788",
    depositId: "dep_1",
    userUid: BOB_UID,
    createdAt: serverTimestamp(),
  });
  await assertSucceeds(depBatch.commit());

  // Duplicate transaction reference by another user (or same user) MUST fail!
  const dupRefBatch = aliceDb.batch();
  dupRefBatch.set(aliceDb.collection("deposits").doc("dep_dup"), {
    depositId: "dep_dup",
    uid: ALICE_UID,
    userUid: ALICE_UID,
    username: "alice",
    fullName: "alice Full",
    userEmail: "alice@example.com",
    userPhone: "+923001234567",
    amount: 500,
    paymentMethod: "EasyPaisa",
    paymentAccountName: "Binace Merchant",
    paymentAccountNumber: "03001112233",
    reference: "txn-ref-7788",
    transactionReference: "txn-ref-7788",
    refKey: "TXN-REF-7788",
    status: "PENDING",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  dupRefBatch.set(aliceDb.collection("depositReferences").doc("TXN-REF-7788"), {
    refKey: "TXN-REF-7788",
    transactionReference: "txn-ref-7788",
    depositId: "dep_dup",
    userUid: ALICE_UID,
    createdAt: serverTimestamp(),
  });
  await assertFails(dupRefBatch.commit());

  // RefKey spoofing mismatch against transactionReference fails
  const spoofRefBatch = aliceDb.batch();
  spoofRefBatch.set(aliceDb.collection("deposits").doc("dep_spoof_ref"), {
    depositId: "dep_spoof_ref",
    uid: ALICE_UID,
    userUid: ALICE_UID,
    username: "alice",
    amount: 500,
    paymentMethod: "EasyPaisa",
    reference: "TXN-REF-7788",
    transactionReference: "TXN-REF-7788",
    refKey: "WRONG_KEY",
    status: "PENDING",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  spoofRefBatch.set(aliceDb.collection("depositReferences").doc("WRONG_KEY"), {
    refKey: "WRONG_KEY",
    transactionReference: "TXN-REF-7788",
    depositId: "dep_spoof_ref",
    userUid: ALICE_UID,
    createdAt: serverTimestamp(),
  });
  await assertFails(spoofRefBatch.commit());

  // Bob CANNOT approve own deposit
  await assertFails(
    bobDb.collection("deposits").doc("dep_1").update({
      status: "APPROVED",
      updatedAt: serverTimestamp(),
    })
  );

  // Bob CANNOT request withdrawal exceeding balance (0)
  await assertFails(
    bobDb.collection("withdrawals").doc("wd_over").set({
      uid: BOB_UID,
      username: "bobuser",
      amount: 100,
      paymentMethod: "Easypaisa",
      accountReference: "03001234567",
      status: "pending",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Alice CANNOT generate her own commission
  await assertFails(
    aliceDb.collection("commissions").doc(`comm_${BOB_UID}`).set({
      referrerUid: ALICE_UID,
      referredUid: BOB_UID,
      referredUsername: "bobuser",
      amount: 100,
      currency: "PKR",
      status: "approved",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Admin approves Bob's deposit & credits PKR 100 commission to Alice
  const adminBatch = adminDb.batch();
  adminBatch.update(adminDb.collection("deposits").doc("dep_1"), {
    status: "APPROVED",
    adminNote: "Verified receipt and TID",
    reviewedBy: ADMIN_EMAIL,
    reviewedAt: serverTimestamp(),
    approvedAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  adminBatch.update(adminDb.collection("users").doc(BOB_UID), {
    realBalance: 500,
    updatedAt: serverTimestamp(),
  });
  adminBatch.update(adminDb.collection("wallets").doc(BOB_UID), {
    balance: 500,
    depositBalance: 500,
    updatedAt: serverTimestamp(),
  });
  adminBatch.update(adminDb.collection("referrals").doc(BOB_UID), {
    firstDepositVerified: true,
    status: "rewarded",
    commissionAmount: 100,
    commissionStatus: "approved",
    updatedAt: serverTimestamp(),
  });
  adminBatch.set(adminDb.collection("commissions").doc(`comm_${BOB_UID}`), {
    referrerUid: ALICE_UID,
    referredUid: BOB_UID,
    referredUsername: "bobuser",
    amount: 100,
    currency: "PKR",
    status: "approved",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
    approvedAt: serverTimestamp(),
  });
  adminBatch.update(adminDb.collection("users").doc(ALICE_UID), {
    realRewardBalance: 100,
    totalReferrals: 1,
    totalReferralCommission: 100,
    updatedAt: serverTimestamp(),
  });
  adminBatch.update(adminDb.collection("wallets").doc(ALICE_UID), {
    balance: 100,
    rewardBalance: 100,
    totalReferralCommission: 100,
    updatedAt: serverTimestamp(),
  });
  await assertSucceeds(adminBatch.commit());

  // Duplicate approval on already APPROVED deposit MUST fail!
  await assertFails(
    adminDb.collection("deposits").doc("dep_1").update({
      status: "APPROVED",
      adminNote: "Double approval attempt",
      updatedAt: serverTimestamp(),
    })
  );

  // Now Bob has 500 balance and can submit a withdrawal request of 200
  await assertSucceeds(
    bobDb.collection("withdrawals").doc("wd_1").set({
      uid: BOB_UID,
      username: "bobuser",
      amount: 200,
      paymentMethod: "JazzCash",
      accountReference: "03009876543",
      status: "pending",
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Query alignment tests: owner filtered queries succeed, unfiltered fail
  await assertSucceeds(bobDb.collection("deposits").where("uid", "==", BOB_UID).get());
  await assertFails(bobDb.collection("deposits").get());
  await assertSucceeds(aliceDb.collection("referrals").where("referrerUid", "==", ALICE_UID).get());
  await assertFails(aliceDb.collection("referrals").get());
  await assertFails(bobDb.collection("depositReferences").get());
});

test("Payment Account Settings: signed-in read, admin-only create/update/delete, non-admin write blocked", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  const easyPaisaDoc = {
    providerKey: "easypaisa",
    providerName: "EasyPaisa",
    bankName: "",
    accountHolderName: "Binace Merchant",
    accountNumber: "03451234567",
    iban: "",
    instructions: "Send deposit and upload receipt screenshot.",
    qrCodeData: "",
    isEnabled: true,
    updatedBy: ADMIN_EMAIL,
    updatedAt: serverTimestamp(),
  };

  // Unauthenticated cannot read or write paymentAccounts
  await assertFails(unauthDb.collection("paymentAccounts").doc("easypaisa").get());
  await assertFails(unauthDb.collection("paymentAccounts").get());

  // Non-admin user cannot create or update paymentAccounts
  await assertFails(aliceDb.collection("paymentAccounts").doc("easypaisa").set(easyPaisaDoc));

  // Admin creates EasyPaisa payment account
  await assertSucceeds(adminDb.collection("paymentAccounts").doc("easypaisa").set(easyPaisaDoc));

  // Admin updates EasyPaisa payment account
  await assertSucceeds(
    adminDb.collection("paymentAccounts").doc("easypaisa").update({
      accountHolderName: "Binace Updated Name",
      accountNumber: "03459998877",
      updatedAt: serverTimestamp(),
    })
  );

  // Non-admin user cannot update existing paymentAccount
  await assertFails(
    aliceDb.collection("paymentAccounts").doc("easypaisa").update({
      accountNumber: "03000000000",
      updatedAt: serverTimestamp(),
    })
  );

  // Signed-in user CAN read single & list paymentAccounts
  await assertSucceeds(aliceDb.collection("paymentAccounts").doc("easypaisa").get());
  await assertSucceeds(aliceDb.collection("paymentAccounts").get());
});

test("Global Settings: signed-in read, admin-only write with optional GitHub release metadata", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  const baseSettings = {
    referralCommission: 100,
    defaultTaskReward: 20,
    maxDailyReward: 200,
    currency: "PKR",
    dailyTaskLimit: 10,
    minWithdrawal: 100,
    minDeposit: 100,
    updatedAt: serverTimestamp(),
  };

  // Non-admin blocked from writing settings
  await assertFails(aliceDb.collection("settings").doc("global").set(baseSettings));

  // Admin writes legacy base settings cleanly
  await assertSucceeds(adminDb.collection("settings").doc("global").set(baseSettings));

  // Admin updates settings with GitHub release & APK URL metadata
  await assertSucceeds(
    adminDb.collection("settings").doc("global").set({
      ...baseSettings,
      githubRepoSlug: "acme/binace-smart-earn",
      latestVersionName: "1.0.2",
      latestVersionCode: 2,
      apkDownloadUrl: "https://github.com/acme/binace-smart-earn/releases/latest/download/binace-smart-earn-latest.apk",
      releaseNotes: "Production signed APK release.",
    })
  );

  // Signed-in user can read global settings
  await assertSucceeds(aliceDb.collection("settings").doc("global").get());
});

