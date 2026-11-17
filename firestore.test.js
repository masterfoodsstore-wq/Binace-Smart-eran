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
    isVerified: false,
    firstDepositStatus: "none",
    firstDepositAmount: 0,
    demoBalance: 0,
    demoRewardBalance: 0,
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

test("Canonical 10-task bootstrap & verification gate + atomic dailyTaskCompletions claim prevents duplicates", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
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

  // Unverified user CANNOT self-verify or start a daily task
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      updatedAt: serverTimestamp(),
    })
  );
  const utId = `${ALICE_UID}_task_01_daily_login_2026-11-14`;
  await assertFails(
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

  // Admin verifies Alice's account
  await assertSucceeds(
    adminDb.collection("users").doc(ALICE_UID).update({
      isVerified: true,
      firstDepositStatus: "approved",
      verifiedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Now verified user can start task
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
    demoRewardBalance: 20,
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
    demoRewardBalance: 40,
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

  // Deposit below PKR 1,000 minimum MUST fail
  const lowDepBatch = bobDb.batch();
  lowDepBatch.set(bobDb.collection("deposits").doc("dep_low"), {
    depositId: "dep_low",
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
    reference: "TXN-LOW-500",
    transactionReference: "TXN-LOW-500",
    refKey: "TXN-LOW-500",
    status: "PENDING",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  lowDepBatch.set(bobDb.collection("depositReferences").doc("TXN-LOW-500"), {
    refKey: "TXN-LOW-500",
    transactionReference: "TXN-LOW-500",
    depositId: "dep_low",
    userUid: BOB_UID,
    createdAt: serverTimestamp(),
  });
  await assertFails(lowDepBatch.commit());

  // Bob submits pending simulated deposit >= 1,000 (4,000 PKR) + atomic depositReferences + firstDepositStatus=pending
  const depBatch = bobDb.batch();
  depBatch.set(bobDb.collection("deposits").doc("dep_1"), {
    depositId: "dep_1",
    uid: BOB_UID,
    userUid: BOB_UID,
    username: "bobuser",
    fullName: "bobuser Full",
    userEmail: "bobuser@example.com",
    userPhone: "+923001234567",
    amount: 4000,
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
  depBatch.update(bobDb.collection("users").doc(BOB_UID), {
    firstDepositStatus: "pending",
    firstDepositAmount: 4000,
    updatedAt: serverTimestamp(),
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
    amount: 1000,
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

  // Admin approves Bob's deposit -> auto-verifies Bob, credits 4000 PKR, & credits PKR 100 commission to Alice
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
    isVerified: true,
    firstDepositStatus: "approved",
    verifiedAt: serverTimestamp(),
    realBalance: 4000,
    demoBalance: 4000,
    updatedAt: serverTimestamp(),
  });
  adminBatch.update(adminDb.collection("wallets").doc(BOB_UID), {
    balance: 4000,
    depositBalance: 4000,
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
    demoRewardBalance: 100,
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

  // Withdrawal below PKR 3,500 minimum MUST fail even for verified Bob with 4,000 balance
  await assertFails(
    bobDb.collection("withdrawals").doc("wd_low").set({
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

  // Verified Bob with 4,000 balance CAN submit a withdrawal request of 3,500 PKR
  await assertSucceeds(
    bobDb.collection("withdrawals").doc("wd_1").set({
      uid: BOB_UID,
      username: "bobuser",
      amount: 3500,
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

test("Live Customer Support: RBAC multi-agent isolation, tickets, text/image messages, read receipts, typing, and FCM devices", async () => {
  const AGENT_UID = "agent_uid_777";
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const agentDb = testEnv.authenticatedContext(AGENT_UID).firestore();
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();

  // Unauthenticated access denied
  await assertFails(unauthDb.collection("supportAgents").get());
  await assertFails(unauthDb.collection("supportTickets").get());
  await assertFails(unauthDb.collection("userDevices").doc(ALICE_UID).get());

  // Non-admin user cannot promote themselves to supportAgent
  await assertFails(
    aliceDb.collection("supportAgents").doc(ALICE_UID).set({
      uid: ALICE_UID,
      displayName: "Rogue Agent",
      role: "support_agent",
      isActive: true,
      isOnline: true,
      lastActiveAt: serverTimestamp(),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Admin registers a verified support agent (non-admin account)
  await assertSucceeds(
    adminDb.collection("supportAgents").doc(AGENT_UID).set({
      uid: AGENT_UID,
      displayName: "Sara Support",
      role: "support_agent",
      isActive: true,
      isOnline: false,
      lastActiveAt: serverTimestamp(),
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Agent can toggle their own online status (only allowed keys)
  await assertSucceeds(
    agentDb.collection("supportAgents").doc(AGENT_UID).update({
      isOnline: true,
      lastActiveAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Agent cannot escalate role or rename themselves
  await assertFails(
    agentDb.collection("supportAgents").doc(AGENT_UID).update({
      role: "admin",
      updatedAt: serverTimestamp(),
    })
  );

  // Signed-in users can read online support agents list (no PII stored here)
  await assertSucceeds(aliceDb.collection("supportAgents").get());

  // Alice creates a valid support ticket (cannot spoof assignedAgentUid or status != open)
  const ticketId = "SUP-ALICE001";
  await assertFails(
    aliceDb.collection("supportTickets").doc(ticketId).set({
      ticketId,
      userUid: ALICE_UID,
      username: "alice",
      userName: "Alice Full",
      category: "deposits",
      subject: "Deposit receipt review",
      status: "resolved",
      isOfflineTicket: false,
      assignedAgentUid: AGENT_UID,
      assignedAgentName: "Sara Support",
      lastMessageText: "Help please",
      lastMessageSenderRole: "user",
      lastMessageAt: serverTimestamp(),
      userIsTyping: false,
      agentIsTyping: false,
      unreadByUserCount: 0,
      unreadByAgentCount: 1,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  await assertSucceeds(
    aliceDb.collection("supportTickets").doc(ticketId).set({
      ticketId,
      userUid: ALICE_UID,
      username: "alice",
      userName: "Alice Full",
      category: "deposits",
      subject: "Deposit receipt review",
      status: "open",
      isOfflineTicket: false,
      assignedAgentUid: "",
      assignedAgentName: "",
      lastMessageText: "Please check my EasyPaisa receipt.",
      lastMessageSenderRole: "user",
      lastMessageAt: serverTimestamp(),
      userIsTyping: false,
      agentIsTyping: false,
      unreadByUserCount: 0,
      unreadByAgentCount: 1,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Alice sends initial user text message
  const msg1Id = "MSG-ALICE001";
  await assertSucceeds(
    aliceDb
      .collection("supportTickets")
      .doc(ticketId)
      .collection("messages")
      .doc(msg1Id)
      .set({
        messageId: msg1Id,
        ticketId,
        ticketOwnerUid: ALICE_UID,
        senderUid: ALICE_UID,
        senderName: "Alice Full",
        senderRole: "user",
        type: "text",
        text: "Please check my EasyPaisa receipt.",
        readByUser: true,
        readByAgent: false,
        createdAt: serverTimestamp(),
      })
  );

  // Alice cannot spoof an agent message
  await assertFails(
    aliceDb
      .collection("supportTickets")
      .doc(ticketId)
      .collection("messages")
      .doc("MSG-SPOOF01")
      .set({
        messageId: "MSG-SPOOF01",
        ticketId,
        ticketOwnerUid: ALICE_UID,
        senderUid: ALICE_UID,
        senderName: "Fake Agent",
        senderRole: "agent",
        type: "text",
        text: "Approved!",
        readByUser: false,
        readByAgent: true,
        createdAt: serverTimestamp(),
      })
  );

  // Bob CANNOT read or list Alice's tickets or messages
  await assertFails(bobDb.collection("supportTickets").doc(ticketId).get());
  await assertFails(bobDb.collection("supportTickets").where("userUid", "==", ALICE_UID).get());
  await assertFails(
    bobDb.collection("supportTickets").doc(ticketId).collection("messages").doc(msg1Id).get()
  );
  await assertFails(
    bobDb.collection("supportTickets").doc(ticketId).collection("messages").where("ticketOwnerUid", "==", ALICE_UID).get()
  );

  // Alice CAN query her own tickets and messages (matching Kotlin query filters)
  await assertSucceeds(aliceDb.collection("supportTickets").where("userUid", "==", ALICE_UID).get());
  await assertSucceeds(
    aliceDb
      .collection("supportTickets")
      .doc(ticketId)
      .collection("messages")
      .where("ticketOwnerUid", "==", ALICE_UID)
      .get()
  );

  // Assigned/authorized support agent CAN list tickets, assign to self, update status/typing, reply with image, and mark read
  await assertSucceeds(agentDb.collection("supportTickets").get());
  await assertSucceeds(
    agentDb.collection("supportTickets").doc(ticketId).collection("messages").get()
  );
  await assertSucceeds(
    agentDb.collection("supportTickets").doc(ticketId).update({
      assignedAgentUid: AGENT_UID,
      assignedAgentName: "Sara Support",
      status: "pending",
      agentIsTyping: true,
      agentTypingName: "Sara Support",
      updatedAt: serverTimestamp(),
    })
  );

  // Agent marks user's message as read and sends an image reply
  await assertSucceeds(
    agentDb
      .collection("supportTickets")
      .doc(ticketId)
      .collection("messages")
      .doc(msg1Id)
      .update({
        readByAgent: true,
        readAt: serverTimestamp(),
      })
  );

  const msg2Id = "MSG-AGENT002";
  await assertSucceeds(
    agentDb
      .collection("supportTickets")
      .doc(ticketId)
      .collection("messages")
      .doc(msg2Id)
      .set({
        messageId: msg2Id,
        ticketId,
        ticketOwnerUid: ALICE_UID,
        senderUid: AGENT_UID,
        senderName: "Sara Support",
        senderRole: "agent",
        type: "image",
        text: "Here is your verification confirmation.",
        imageUrl: "https://firebasestorage.googleapis.com/v0/b/test/o/confirm.jpg",
        imageStoragePath: `support_attachments/${AGENT_UID}/${ticketId}/${msg2Id}.jpg`,
        readByUser: false,
        readByAgent: true,
        createdAt: serverTimestamp(),
      })
  );

  // Agent resolves ticket
  await assertSucceeds(
    agentDb.collection("supportTickets").doc(ticketId).update({
      status: "resolved",
      agentIsTyping: false,
      lastMessageText: "Here is your verification confirmation.",
      lastMessageSenderRole: "agent",
      lastMessageAt: serverTimestamp(),
      unreadByUserCount: 1,
      unreadByAgentCount: 0,
      resolvedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );

  // Alice marks agent reply read and reopens ticket if needed
  await assertSucceeds(
    aliceDb
      .collection("supportTickets")
      .doc(ticketId)
      .collection("messages")
      .doc(msg2Id)
      .update({
        readByUser: true,
        readAt: serverTimestamp(),
      })
  );
  await assertSucceeds(
    aliceDb.collection("supportTickets").doc(ticketId).update({
      unreadByUserCount: 0,
      userLastReadAt: serverTimestamp(),
      status: "open",
      updatedAt: serverTimestamp(),
    })
  );

  // Alice cannot alter assignedAgentUid on her own ticket
  await assertFails(
    aliceDb.collection("supportTickets").doc(ticketId).update({
      assignedAgentUid: BOB_UID,
      updatedAt: serverTimestamp(),
    })
  );

  // FCM device token registration is strictly owner-only
  await assertSucceeds(
    aliceDb.collection("userDevices").doc(ALICE_UID).set({
      uid: ALICE_UID,
      fcmToken: "fcm_test_token_1234567890_abcdef",
      platform: "android",
      notificationsEnabled: true,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(bobDb.collection("userDevices").doc(ALICE_UID).get());
  await assertFails(adminDb.collection("userDevices").doc(ALICE_UID).get());
});

test("Live Support Atomic Batch Create: simultaneous batch create of ticket + first message succeeds", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ticketId = "SUP-BATCH01";
  const msgId = "MSG-BATCH01";
  const batch = aliceDb.batch();
  batch.set(aliceDb.collection("supportTickets").doc(ticketId), {
    ticketId,
    userUid: ALICE_UID,
    username: "alice",
    userName: "Alice Full",
    category: "payments",
    subject: "Batch ticket creation test",
    status: "open",
    isOfflineTicket: false,
    assignedAgentUid: "",
    assignedAgentName: "",
    lastMessageText: "Hello live support",
    lastMessageSenderRole: "user",
    lastMessageAt: serverTimestamp(),
    userIsTyping: false,
    agentIsTyping: false,
    unreadByUserCount: 0,
    unreadByAgentCount: 1,
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp(),
  });
  batch.set(
    aliceDb.collection("supportTickets").doc(ticketId).collection("messages").doc(msgId),
    {
      messageId: msgId,
      ticketId,
      ticketOwnerUid: ALICE_UID,
      senderUid: ALICE_UID,
      senderName: "Alice Full",
      senderRole: "user",
      type: "text",
      text: "Hello live support",
      readByUser: true,
      readByAgent: false,
      createdAt: serverTimestamp(),
    }
  );
  await assertSucceeds(batch.commit());
});

test("VIP Membership: admin manages configs & overrides, users initialized bronze only, anti-tampering & immutable history", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  const goldConfig = {
    levelKey: "gold",
    tierName: "Gold",
    tierOrder: 3,
    minVerifiedDepositsPkr: 5000,
    minCompletedTasks: 25,
    minQualifiedReferrals: 2,
    taskBonusPercent: 10,
    prioritySupportLabel: "Gold Fast-Track Support",
    benefits: ["Gold VIP crest", "Fast-track Live Support"],
    isActive: true,
    updatedAt: serverTimestamp(),
  };

  // Non-admin cannot create VIP config
  await assertFails(aliceDb.collection("vipConfigs").doc("gold").set(goldConfig));
  await assertSucceeds(adminDb.collection("vipConfigs").doc("gold").set(goldConfig));
  await assertSucceeds(aliceDb.collection("vipConfigs").get());

  // User cannot create pre-elevated membership
  await assertFails(
    aliceDb.collection("vipMemberships").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice",
      currentLevel: "diamond",
      currentTierName: "Diamond",
      tierOrder: 5,
      verifiedDepositTotal: 99999,
      completedTasksTotal: 999,
      qualifiedReferralsCount: 50,
      progressPercent: 100,
      nextLevelKey: "",
      assignedBy: "system_auto",
      updatedAt: serverTimestamp(),
    })
  );

  // User CAN initialize default Bronze zero-progress membership
  await assertSucceeds(
    aliceDb.collection("vipMemberships").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice",
      currentLevel: "bronze",
      currentTierName: "Bronze",
      tierOrder: 1,
      verifiedDepositTotal: 0,
      completedTasksTotal: 0,
      qualifiedReferralsCount: 0,
      progressPercent: 0,
      nextLevelKey: "silver",
      assignedBy: "system_auto",
      updatedAt: serverTimestamp(),
    })
  );

  // User cannot update or elevate own VIP membership; Bob cannot read Alice's VIP membership
  await assertFails(
    aliceDb.collection("vipMemberships").doc(ALICE_UID).update({
      currentLevel: "gold",
      tierOrder: 3,
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(bobDb.collection("vipMemberships").doc(ALICE_UID).get());

  // Admin can override Alice's VIP membership and append immutable VIP history
  await assertSucceeds(
    adminDb.collection("vipMemberships").doc(ALICE_UID).update({
      currentLevel: "gold",
      currentTierName: "Gold",
      tierOrder: 3,
      assignedBy: "admin_override",
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    adminDb.collection("vipHistory").doc("VIPH-001").set({
      historyId: "VIPH-001",
      uid: ALICE_UID,
      previousLevel: "bronze",
      newLevel: "gold",
      reason: "Admin verified VIP promotion",
      source: "admin",
      changedBy: ADMIN_EMAIL,
      createdAt: serverTimestamp(),
    })
  );
  await assertSucceeds(aliceDb.collection("vipHistory").where("uid", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("vipHistory").where("uid", "==", ALICE_UID).get());
  await assertFails(adminDb.collection("vipHistory").doc("VIPH-001").delete());
});

test("Global Leaderboard: zero-score init, anti-cheat blocking self-score inflation, admin prizes", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Cheat attempt by user fails
  await assertFails(
    aliceDb.collection("leaderboardEntries").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      vipLevel: "bronze",
      isVerified: false,
      dailyPoints: 5000,
      dailyDateKey: "2026-11-17",
      weeklyPoints: 5000,
      weekKey: "2026-W47",
      monthlyPoints: 5000,
      monthKey: "2026-11",
      allTimeTaskPoints: 5000,
      completedTasksCount: 50,
      qualifiedReferralsCount: 10,
      verifiedActivityScore: 99999,
      updatedAt: serverTimestamp(),
    })
  );

  // Zero-score initialization succeeds & signed-in users can view rankings
  await assertSucceeds(
    aliceDb.collection("leaderboardEntries").doc(ALICE_UID).set({
      uid: ALICE_UID,
      username: "alice",
      displayName: "Alice",
      vipLevel: "bronze",
      isVerified: false,
      dailyPoints: 0,
      dailyDateKey: "2026-11-17",
      weeklyPoints: 0,
      weekKey: "2026-W47",
      monthlyPoints: 0,
      monthKey: "2026-11",
      allTimeTaskPoints: 0,
      completedTasksCount: 0,
      qualifiedReferralsCount: 0,
      verifiedActivityScore: 0,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(bobDb.collection("leaderboardEntries").get());

  // User cannot inflate score via update
  await assertFails(
    aliceDb.collection("leaderboardEntries").doc(ALICE_UID).update({
      verifiedActivityScore: 1000,
      updatedAt: serverTimestamp(),
    })
  );

  // Admin writes promotional prize config
  const prizeDoc = {
    prizeId: "PRIZE-WEEKLY-TOP1",
    periodType: "weekly",
    rankFrom: 1,
    rankTo: 3,
    title: "Weekly Verified Champion Pool",
    rewardDescription: "Promotional community bonus for top verified performers (not guaranteed returns).",
    rewardAmountPkr: 1500,
    isActive: true,
    updatedAt: serverTimestamp(),
  };
  await assertFails(aliceDb.collection("leaderboardPrizes").doc("PRIZE-WEEKLY-TOP1").set(prizeDoc));
  await assertSucceeds(adminDb.collection("leaderboardPrizes").doc("PRIZE-WEEKLY-TOP1").set(prizeDoc));
  await assertSucceeds(aliceDb.collection("leaderboardPrizes").get());
});

test("Notification Center & Announcements: read-only user receipt marking, admin broadcast & scheduled privacy", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // Only admin creates direct notifications
  const notifDoc = {
    notificationId: "NTF-001",
    uid: ALICE_UID,
    title: "VIP Milestone",
    body: "You are 2 tasks away from Silver VIP.",
    category: "vip",
    isRead: false,
    referenceId: "",
    createdAt: serverTimestamp(),
  };
  await assertFails(aliceDb.collection("notifications").doc("NTF-001").set(notifDoc));
  await assertSucceeds(adminDb.collection("notifications").doc("NTF-001").set(notifDoc));

  // Alice reads & marks own notification read; cannot tamper body; Bob cannot read Alice's notifications
  await assertSucceeds(aliceDb.collection("notifications").where("uid", "==", ALICE_UID).get());
  await assertFails(bobDb.collection("notifications").doc("NTF-001").get());
  await assertFails(
    aliceDb.collection("notifications").doc("NTF-001").update({
      body: "PKR 999999 bonus",
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(
    aliceDb.collection("notifications").doc("NTF-001").update({
      isRead: true,
      readAt: serverTimestamp(),
    })
  );

  // Scheduled announcements hidden from regular users until published
  await assertSucceeds(
    adminDb.collection("announcements").doc("ANN-SCHED1").set({
      announcementId: "ANN-SCHED1",
      title: "Upcoming Maintenance",
      body: "Scheduled window tonight.",
      category: "maintenance",
      status: "scheduled",
      scheduledFor: serverTimestamp(),
      sendPushNotification: true,
      pushDispatched: false,
      createdByUid: ADMIN_UID,
      createdByEmail: ADMIN_EMAIL,
      createdAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertFails(aliceDb.collection("announcements").doc("ANN-SCHED1").get());
  await assertSucceeds(
    adminDb.collection("announcements").doc("ANN-SCHED1").update({
      status: "published",
      publishedAt: serverTimestamp(),
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(aliceDb.collection("announcements").where("status", "==", "published").get());
  await assertSucceeds(
    aliceDb.collection("announcementReads").doc(`${ALICE_UID}_ANN-SCHED1`).set({
      uid: ALICE_UID,
      announcementId: "ANN-SCHED1",
      readAt: serverTimestamp(),
    })
  );
});

test("Account Security: strict owner-only totpSecret isolation, device session management, immutable events, OTP server-only", async () => {
  const adminDb = testEnv
    .authenticatedContext(ADMIN_UID, { email: ADMIN_EMAIL, email_verified: true })
    .firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  // SecurityProfile with TOTP secret is strictly owner-only (even admin cannot read totpSecret)
  await assertSucceeds(
    aliceDb.collection("securityProfiles").doc(ALICE_UID).set({
      uid: ALICE_UID,
      totpEnabled: true,
      totpSecret: "JBSWY3DPEHPK3PXP",
      biometricEnabled: true,
      otpVerified: true,
      verifiedWithdrawMethod: "EasyPaisa",
      verifiedWithdrawAccount: "03001234567",
      verifiedWithdrawHolderName: "Alice Full",
      withdrawalDetailVerified: true,
      suspiciousAlertActive: false,
      updatedAt: serverTimestamp(),
    })
  );
  await assertSucceeds(aliceDb.collection("securityProfiles").doc(ALICE_UID).get());
  await assertFails(bobDb.collection("securityProfiles").doc(ALICE_UID).get());
  await assertFails(adminDb.collection("securityProfiles").doc(ALICE_UID).get());

  // Device session registration + suspicious flag / remote revocation
  const sessionId = `${ALICE_UID}_DEV-001`;
  await assertSucceeds(
    aliceDb.collection("securityDevices").doc(sessionId).set({
      sessionId,
      uid: ALICE_UID,
      deviceId: "DEV-001",
      deviceName: "Pixel 8",
      osVersion: "15",
      appVersion: "1.0.2",
      timezoneId: "Asia/Karachi",
      isTrusted: true,
      isSuspicious: false,
      status: "active",
      firstSeenAt: serverTimestamp(),
      lastActiveAt: serverTimestamp(),
    })
  );
  await assertFails(bobDb.collection("securityDevices").doc(sessionId).get());
  await assertSucceeds(
    adminDb.collection("securityDevices").doc(sessionId).update({
      status: "revoked",
      revokedAt: serverTimestamp(),
      revokedBy: ADMIN_EMAIL,
    })
  );

  // Security Events are immutable
  await assertSucceeds(
    aliceDb.collection("securityEvents").doc("SEC-001").set({
      eventId: "SEC-001",
      uid: ALICE_UID,
      username: "alice",
      eventType: "TOTP_ENABLED",
      title: "2FA Enabled",
      details: "Authenticator app 2FA activated.",
      severity: "info",
      actorUid: ALICE_UID,
      createdAt: serverTimestamp(),
    })
  );
  await assertFails(aliceDb.collection("securityEvents").doc("SEC-001").delete());
  await assertFails(adminDb.collection("securityEvents").doc("SEC-001").update({ details: "tampered" }));
  await assertSucceeds(adminDb.collection("securityEvents").get());

  // Security OTP collection is completely inaccessible from client SDKs (Cloud Functions Admin SDK only)
  await assertFails(aliceDb.collection("securityOtps").doc("OTP-001").get());
  await assertFails(adminDb.collection("securityOtps").doc("OTP-001").set({ uid: ALICE_UID }));
});


