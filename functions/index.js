const crypto = require("crypto");
const { onDocumentCreated, onDocumentWritten } = require("firebase-functions/v2/firestore");
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onSchedule } = require("firebase-functions/v2/scheduler");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore, FieldValue, Timestamp } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");

initializeApp();

const DATABASE_ID = "ai-studio-android-binacesm-06c4f3c1-efe0-4b45-b1d5-721359895f56";
const ADMIN_BOOTSTRAP_EMAIL = "masterfoodsstore@gmail.com";
const db = getFirestore(DATABASE_ID);
const messaging = getMessaging();

const DEFAULT_VIP_TIERS = [
  {
    levelKey: "bronze",
    tierName: "Bronze",
    tierOrder: 1,
    minVerifiedDepositsPkr: 0,
    minCompletedTasks: 0,
    minQualifiedReferrals: 0,
    taskBonusPercent: 0,
    prioritySupportLabel: "Standard Support Queue",
    benefits: [
      "Access to standard app dashboard & referral links",
      "Upgrade eligibility tracked in real time",
      "Community support & notification alerts"
    ],
    isActive: true,
  },
  {
    levelKey: "silver",
    tierName: "Silver",
    tierOrder: 2,
    minVerifiedDepositsPkr: 1000,
    minCompletedTasks: 5,
    minQualifiedReferrals: 0,
    taskBonusPercent: 5,
    prioritySupportLabel: "Silver Priority Queue",
    benefits: [
      "Verified account badge + Priority Queue tagging",
      "Silver tier profile crest on Global Leaderboard",
      "Eligible for promotional seasonal leaderboard drops"
    ],
    isActive: true,
  },
  {
    levelKey: "gold",
    tierName: "Gold",
    tierOrder: 3,
    minVerifiedDepositsPkr: 5000,
    minCompletedTasks: 25,
    minQualifiedReferrals: 2,
    taskBonusPercent: 10,
    prioritySupportLabel: "Gold Fast-Track Support",
    benefits: [
      "Gold VIP crest & featured leaderboard rank badge",
      "Fast-track Live Customer Support routing",
      "Eligible for Gold promotional leaderboard pools"
    ],
    isActive: true,
  },
  {
    levelKey: "platinum",
    tierName: "Platinum",
    tierOrder: 4,
    minVerifiedDepositsPkr: 15000,
    minCompletedTasks: 75,
    minQualifiedReferrals: 5,
    taskBonusPercent: 15,
    prioritySupportLabel: "Platinum Concierge Desk",
    benefits: [
      "Dedicated VIP Live Support routing & audit priority",
      "Platinum emblem across Global Leaderboard & Profile",
      "Extended promotional event access (subject to rules)"
    ],
    isActive: true,
  },
  {
    levelKey: "diamond",
    tierName: "Diamond",
    tierOrder: 5,
    minVerifiedDepositsPkr: 50000,
    minCompletedTasks: 150,
    minQualifiedReferrals: 10,
    taskBonusPercent: 25,
    prioritySupportLabel: "Diamond Executive Support",
    benefits: [
      "Highest verified VIP status & Diamond emblem",
      "Top executive support queue & enhanced security monitoring",
      "Priority eligibility for monthly community prize draws"
    ],
    isActive: true,
  },
];

function getDateKeys(now = new Date()) {
  const isoDay = now.toISOString().slice(0, 10); // YYYY-MM-DD
  const monthKey = isoDay.slice(0, 7); // YYYY-MM

  // ISO Week YYYY-Www
  const d = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
  const dayNum = d.getUTCDay() || 7;
  d.setUTCDate(d.getUTCDate() + 4 - dayNum);
  const yearStart = new Date(Date.UTC(d.getUTCFullYear(), 0, 1));
  const weekNo = Math.ceil(((d - yearStart) / 86400000 + 1) / 7);
  const weekKey = `${d.getUTCFullYear()}-W${String(weekNo).padStart(2, "0")}`;

  return { dailyDateKey: isoDay, weekKey, monthKey };
}

async function isAuthorizedAdmin(auth) {
  if (!auth || !auth.uid) return false;
  if (
    auth.token &&
    auth.token.email === ADMIN_BOOTSTRAP_EMAIL &&
    auth.token.email_verified === true
  ) {
    return true;
  }
  const adminSnap = await db.collection("admins").doc(auth.uid).get();
  return adminSnap.exists;
}

async function loadVipConfigs() {
  const snap = await db.collection("vipConfigs").get();
  if (snap.empty) {
    return DEFAULT_VIP_TIERS;
  }
  const items = snap.docs
    .map((doc) => doc.data())
    .filter((item) => item && item.isActive !== false)
    .sort((a, b) => (a.tierOrder || 0) - (b.tierOrder || 0));
  return items.length > 0 ? items : DEFAULT_VIP_TIERS;
}

async function sendPushToUser(uid, title, body, dataPayload = {}, channelId = "platform_alerts_channel") {
  try {
    const devSnap = await db.collection("userDevices").doc(uid).get();
    if (!devSnap.exists) return false;
    const device = devSnap.data();
    if (!device || !device.notificationsEnabled || !device.fcmToken) return false;
    await messaging.send({
      token: device.fcmToken,
      notification: { title, body },
      data: {
        title: String(title),
        body: String(body),
        ...Object.fromEntries(Object.entries(dataPayload).map(([k, v]) => [k, String(v ?? "")])),
      },
      android: {
        priority: "high",
        notification: { channelId },
      },
    });
    return true;
  } catch (err) {
    console.warn(`FCM send skipped for ${uid}:`, err.message);
    return false;
  }
}

async function createUserNotification(uid, title, body, category = "system", referenceId = "", sendPush = true) {
  const notificationId = `NTF-${crypto.randomBytes(6).toString("hex").toUpperCase()}`;
  const docData = {
    notificationId,
    uid,
    title: String(title).slice(0, 120),
    body: String(body).slice(0, 600),
    category,
    isRead: false,
    referenceId: String(referenceId || "").slice(0, 160),
    createdAt: FieldValue.serverTimestamp(),
  };
  await db.collection("notifications").doc(notificationId).set(docData);
  if (sendPush) {
    await sendPushToUser(uid, docData.title, docData.body, {
      notificationId,
      notification_category: category,
      referenceId: docData.referenceId,
    });
  }
  return notificationId;
}

function evaluateTierProgress(configs, verifiedDepositTotal, completedTasksTotal, qualifiedReferralsCount) {
  const sorted = [...configs].sort((a, b) => (a.tierOrder || 0) - (b.tierOrder || 0));
  let matched = sorted[0];
  for (const tier of sorted) {
    const meetsDep = verifiedDepositTotal >= (tier.minVerifiedDepositsPkr || 0);
    const meetsTasks = completedTasksTotal >= (tier.minCompletedTasks || 0);
    const meetsRefs = qualifiedReferralsCount >= (tier.minQualifiedReferrals || 0);
    if (meetsDep && meetsTasks && meetsRefs) {
      matched = tier;
    }
  }

  const nextTier = sorted.find((t) => (t.tierOrder || 0) > (matched.tierOrder || 0)) || null;
  let progressPercent = 100;
  if (nextTier) {
    const ratios = [];
    if ((nextTier.minVerifiedDepositsPkr || 0) > 0) {
      ratios.push(Math.min(1, verifiedDepositTotal / nextTier.minVerifiedDepositsPkr));
    }
    if ((nextTier.minCompletedTasks || 0) > 0) {
      ratios.push(Math.min(1, completedTasksTotal / nextTier.minCompletedTasks));
    }
    if ((nextTier.minQualifiedReferrals || 0) > 0) {
      ratios.push(Math.min(1, qualifiedReferralsCount / nextTier.minQualifiedReferrals));
    }
    const avg = ratios.length > 0 ? ratios.reduce((a, b) => a + b, 0) / ratios.length : 1;
    progressPercent = Math.max(0, Math.min(99, Math.round(avg * 100)));
  }

  return {
    matched,
    nextLevelKey: nextTier ? nextTier.levelKey : "",
    progressPercent,
  };
}

/**
 * Recomputes verified VIP level + verified Global Leaderboard entry strictly from server Firestore records.
 */
async function recalculateUserVipAndLeaderboard(uid) {
  const userSnap = await db.collection("users").doc(uid).get();
  if (!userSnap.exists) return null;
  const user = userSnap.data();
  if (!user || user.accountStatus === "suspended") return null;

  const [configs, depositsSnap, referralsSnap, completionsSnap, vipSnap] = await Promise.all([
    loadVipConfigs(),
    db.collection("deposits").where("uid", "==", uid).get(),
    db.collection("referrals").where("referrerUid", "==", uid).get(),
    db.collection("dailyTaskCompletions").where("uid", "==", uid).get(),
    db.collection("vipMemberships").doc(uid).get(),
  ]);

  let verifiedDepositTotal = 0;
  depositsSnap.forEach((doc) => {
    const d = doc.data();
    if (d && String(d.status || "").toLowerCase() === "approved") {
      verifiedDepositTotal += Number(d.amount || 0);
    }
  });

  let qualifiedReferralsCount = 0;
  referralsSnap.forEach((doc) => {
    const r = doc.data();
    if (r && r.firstDepositVerified === true) {
      qualifiedReferralsCount += 1;
    }
  });

  const { dailyDateKey, weekKey, monthKey } = getDateKeys();
  const weekPrefix = (() => {
    const now = new Date();
    const sevenDaysAgo = new Date(now.getTime() - 6 * 86400000);
    return sevenDaysAgo.toISOString().slice(0, 10);
  })();

  let dailyPoints = 0;
  let weeklyPoints = 0;
  let monthlyPoints = 0;
  let allTimeTaskPoints = 0;
  let claimedCount = 0;

  completionsSnap.forEach((doc) => {
    const c = doc.data();
    if (!c || c.status !== "claimed") return;
    const pts = Number(c.rewardPoints || 0);
    const dk = String(c.dateKey || "");
    claimedCount += 1;
    allTimeTaskPoints += pts;
    if (dk === dailyDateKey) {
      dailyPoints += pts;
    }
    if (dk >= weekPrefix && dk <= dailyDateKey) {
      weeklyPoints += pts;
    }
    if (dk.startsWith(monthKey)) {
      monthlyPoints += pts;
    }
  });

  const completedTasksTotal = Math.max(claimedCount, Number(user.completedTasksCount || 0));
  const evalResult = evaluateTierProgress(
    configs,
    verifiedDepositTotal,
    completedTasksTotal,
    qualifiedReferralsCount
  );

  const existingVip = vipSnap.exists ? vipSnap.data() : null;
  const isManualOverride =
    existingVip &&
    existingVip.assignedBy === "admin_override" &&
    Number(existingVip.tierOrder || 1) >= Number(evalResult.matched.tierOrder || 1);

  const finalTier = isManualOverride
    ? configs.find((t) => t.levelKey === existingVip.currentLevel) || evalResult.matched
    : evalResult.matched;

  const previousOrder = existingVip ? Number(existingVip.tierOrder || 1) : 1;
  const previousLevel = existingVip ? String(existingVip.currentLevel || "bronze") : "bronze";
  const newOrder = Number(finalTier.tierOrder || 1);

  const vipPayload = {
    uid,
    username: String(user.username || "user"),
    currentLevel: finalTier.levelKey,
    currentTierName: finalTier.tierName,
    tierOrder: newOrder,
    verifiedDepositTotal,
    completedTasksTotal,
    qualifiedReferralsCount,
    progressPercent: evalResult.progressPercent,
    nextLevelKey: evalResult.nextLevelKey,
    assignedBy: isManualOverride ? "admin_override" : "system_auto",
    updatedAt: FieldValue.serverTimestamp(),
  };
  await db.collection("vipMemberships").doc(uid).set(vipPayload);

  if (existingVip && newOrder > previousOrder && !isManualOverride) {
    const historyId = `VIPH-${crypto.randomBytes(6).toString("hex").toUpperCase()}`;
    await db.collection("vipHistory").doc(historyId).set({
      historyId,
      uid,
      previousLevel,
      newLevel: finalTier.levelKey,
      reason: `Qualified via ${completedTasksTotal} verified tasks, ${qualifiedReferralsCount} referrals, and PKR ${verifiedDepositTotal} verified deposits.`,
      source: "server_auto",
      changedBy: "system",
      createdAt: FieldValue.serverTimestamp(),
    });
    await createUserNotification(
      uid,
      `VIP Upgraded to ${finalTier.tierName}!`,
      `Your verified activity unlocked ${finalTier.tierName} VIP status (${finalTier.prioritySupportLabel}).`,
      "vip",
      historyId,
      true
    );
  }

  // Strictly non-PII public leaderboard record weighted by verified activity
  const verifiedActivityScore =
    (user.isVerified ? 50 : 0) +
    allTimeTaskPoints +
    qualifiedReferralsCount * 100 +
    newOrder * 25;

  const leaderboardPayload = {
    uid,
    username: String(user.username || "user"),
    displayName: String(user.fullName || user.username || "User").slice(0, 60),
    vipLevel: finalTier.levelKey,
    isVerified: Boolean(user.isVerified),
    dailyPoints,
    dailyDateKey,
    weeklyPoints,
    weekKey,
    monthlyPoints,
    monthKey,
    allTimeTaskPoints: Math.max(allTimeTaskPoints, Number(user.totalEarnedTasks || 0)),
    completedTasksCount: completedTasksTotal,
    qualifiedReferralsCount,
    verifiedActivityScore,
    updatedAt: FieldValue.serverTimestamp(),
  };
  await db.collection("leaderboardEntries").doc(uid).set(leaderboardPayload);

  return { vip: vipPayload, leaderboard: leaderboardPayload };
}

// ============================================================================
// LIVE SUPPORT TRIGGERS
// ============================================================================

exports.onSupportMessageCreated = onDocumentCreated(
  {
    database: DATABASE_ID,
    document: "supportTickets/{ticketId}/messages/{messageId}",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const msg = snap.data();
    const { ticketId } = event.params;
    const ticketSnap = await db.collection("supportTickets").doc(ticketId).get();
    if (!ticketSnap.exists) return;

    const ticket = ticketSnap.data();
    const bodyPreview =
      msg.type === "image" && (!msg.text || msg.text.trim().length === 0)
        ? "Sent an image attachment"
        : String(msg.text || "").slice(0, 140);

    if (msg.senderRole === "agent") {
      await createUserNotification(
        ticket.userUid,
        `Live Support • ${msg.senderName || "Support Agent"}`,
        bodyPreview,
        "support",
        ticketId,
        false
      );
      await sendPushToUser(
        ticket.userUid,
        `Live Support • ${msg.senderName || "Support Agent"}`,
        bodyPreview,
        { type: "support_reply", ticketId, category: String(ticket.category || "") },
        "support_updates_channel"
      );
    } else if (msg.senderRole === "user") {
      const targetUids = new Set();
      if (ticket.assignedAgentUid) {
        targetUids.add(ticket.assignedAgentUid);
      } else {
        const agentsSnap = await db
          .collection("supportAgents")
          .where("isActive", "==", true)
          .get();
        agentsSnap.forEach((doc) => {
          const data = doc.data();
          if (data && data.uid) targetUids.add(data.uid);
        });
      }

      for (const staffUid of targetUids) {
        if (staffUid === msg.senderUid) continue;
        await sendPushToUser(
          staffUid,
          `Support Ticket • @${ticket.username || "user"}`,
          `${ticket.category}: ${bodyPreview}`,
          { type: "support_user_message", ticketId, category: String(ticket.category || "") },
          "support_updates_channel"
        );
      }
    }
  }
);

exports.onSupportTicketCreated = onDocumentCreated(
  {
    database: DATABASE_ID,
    document: "supportTickets/{ticketId}",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;

    const ticket = snap.data();
    const { ticketId } = event.params;

    const agentsSnap = await db
      .collection("supportAgents")
      .where("isActive", "==", true)
      .get();

    for (const agentDoc of agentsSnap.docs) {
      const agent = agentDoc.data();
      if (!agent || !agent.uid || agent.uid === ticket.userUid) continue;
      await sendPushToUser(
        agent.uid,
        ticket.isOfflineTicket
          ? `New Offline Ticket • ${ticket.category}`
          : `New Live Support Request • ${ticket.category}`,
        `@${ticket.username}: ${String(ticket.subject || "").slice(0, 120)}`,
        {
          type: "support_new_ticket",
          ticketId,
          category: String(ticket.category || ""),
          isOfflineTicket: String(Boolean(ticket.isOfflineTicket)),
        },
        "support_updates_channel"
      );
    }

    await db.collection("supportTickets").doc(ticketId).set(
      { updatedAt: FieldValue.serverTimestamp() },
      { merge: true }
    );
  }
);

// ============================================================================
// 1 & 2. VIP MEMBERSHIP & GLOBAL LEADERBOARD TRIGGERS + CALLABLES
// ============================================================================

exports.onTaskCompletionClaimed = onDocumentWritten(
  {
    database: DATABASE_ID,
    document: "dailyTaskCompletions/{completionId}",
  },
  async (event) => {
    const after = event.data?.after?.data();
    if (!after || after.status !== "claimed" || !after.uid) return;
    await recalculateUserVipAndLeaderboard(after.uid);
  }
);

exports.onDepositReviewed = onDocumentWritten(
  {
    database: DATABASE_ID,
    document: "deposits/{depositId}",
  },
  async (event) => {
    const after = event.data?.after?.data();
    if (!after || !after.uid) return;
    const status = String(after.status || "").toLowerCase();
    if (status === "approved" || status === "rejected") {
      await recalculateUserVipAndLeaderboard(after.uid);
      if (status === "approved") {
        await createUserNotification(
          after.uid,
          `Deposit Approved • PKR ${Number(after.amount || 0).toFixed(0)}`,
          `Your deposit request ${after.depositId || event.params.depositId} was verified and credited.`,
          "wallet",
          after.depositId || event.params.depositId,
          true
        );
      }
    }
  }
);

exports.onReferralQualified = onDocumentWritten(
  {
    database: DATABASE_ID,
    document: "referrals/{referralId}",
  },
  async (event) => {
    const after = event.data?.after?.data();
    if (!after || !after.referrerUid) return;
    if (after.firstDepositVerified === true) {
      await recalculateUserVipAndLeaderboard(after.referrerUid);
    }
  }
);

exports.syncVipAndLeaderboard = onCall(async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  const res = await recalculateUserVipAndLeaderboard(request.auth.uid);
  if (!res) {
    throw new HttpsError("not-found", "Active user profile not found.");
  }
  return {
    ok: true,
    vipLevel: res.vip.currentLevel,
    tierOrder: res.vip.tierOrder,
    progressPercent: res.vip.progressPercent,
    verifiedActivityScore: res.leaderboard.verifiedActivityScore,
  };
});

exports.rebuildAllVipAndLeaderboard = onCall(async (request) => {
  const authorized = await isAuthorizedAdmin(request.auth);
  if (!authorized) {
    throw new HttpsError("permission-denied", "Admin privileges required.");
  }
  const usersSnap = await db.collection("users").get();
  let updatedCount = 0;
  for (const userDoc of usersSnap.docs) {
    const res = await recalculateUserVipAndLeaderboard(userDoc.id);
    if (res) updatedCount += 1;
  }
  return { ok: true, updatedCount };
});

// ============================================================================
// 3. NOTIFICATION CENTER & ADMIN ANNOUNCEMENTS (BROADCAST + SCHEDULED)
// ============================================================================

async function broadcastAnnouncement(announcementId, ann) {
  if (!ann || ann.pushDispatched === true) return;
  const devicesSnap = await db
    .collection("userDevices")
    .where("notificationsEnabled", "==", true)
    .get();

  const tokens = [];
  devicesSnap.forEach((doc) => {
    const d = doc.data();
    if (d && d.fcmToken) tokens.push(d.fcmToken);
  });

  if (ann.sendPushNotification !== false && tokens.length > 0) {
    const chunkSize = 450;
    for (let i = 0; i < tokens.length; i += chunkSize) {
      const chunk = tokens.slice(i, i + chunkSize);
      await messaging.sendEachForMulticast({
        tokens: chunk,
        notification: {
          title: String(ann.title || "Announcement"),
          body: String(ann.body || "").slice(0, 180),
        },
        data: {
          type: "announcement",
          announcementId,
          notification_category: String(ann.category || "announcement"),
        },
        android: {
          priority: "high",
          notification: { channelId: "platform_alerts_channel" },
        },
      });
    }
  }

  await db.collection("announcements").doc(announcementId).set(
    {
      status: "published",
      pushDispatched: true,
      publishedAt: ann.publishedAt || FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true }
  );
}

exports.onAnnouncementWritten = onDocumentWritten(
  {
    database: DATABASE_ID,
    document: "announcements/{announcementId}",
  },
  async (event) => {
    const after = event.data?.after?.data();
    if (!after) return;
    if (after.status === "published" && after.pushDispatched !== true) {
      await broadcastAnnouncement(event.params.announcementId, after);
    }
  }
);

exports.dispatchScheduledAnnouncements = onSchedule(
  {
    schedule: "every 5 minutes",
    region: "us-central1",
  },
  async () => {
    const now = Timestamp.now();
    const dueSnap = await db
      .collection("announcements")
      .where("status", "==", "scheduled")
      .get();

    for (const doc of dueSnap.docs) {
      const ann = doc.data();
      if (!ann || !ann.scheduledFor) continue;
      if (ann.scheduledFor.toMillis() <= now.toMillis()) {
        await broadcastAnnouncement(doc.id, ann);
      }
    }
  }
);

// ============================================================================
// 4. ACCOUNT SECURITY SYSTEM (OTP, SUSPICIOUS LOGIN, AUDIT LOGGING)
// ============================================================================

function hashOtpCode(code, salt) {
  return crypto.createHash("sha256").update(`${salt}:${code}`).digest("hex");
}

exports.requestOtpChallenge = onCall(async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  const uid = request.auth.uid;
  const purpose = String(request.data?.purpose || "account_security").slice(0, 50);

  // Rate limit: reject if an unverified challenge was issued in the last 45s
  const recentSnap = await db
    .collection("securityOtps")
    .where("uid", "==", uid)
    .orderBy("createdAt", "desc")
    .limit(1)
    .get();
  if (!recentSnap.empty) {
    const latest = recentSnap.docs[0].data();
    if (latest && latest.createdAt && Date.now() - latest.createdAt.toMillis() < 45_000) {
      throw new HttpsError("resource-exhausted", "Please wait 45 seconds before requesting another OTP.");
    }
  }

  const code = String(crypto.randomInt(100000, 1000000));
  const challengeId = `OTP-${crypto.randomBytes(6).toString("hex").toUpperCase()}`;
  const salt = crypto.randomBytes(8).toString("hex");
  const codeHash = hashOtpCode(code, salt);
  const expiresAtMs = Date.now() + 10 * 60 * 1000;

  await db.collection("securityOtps").doc(challengeId).set({
    challengeId,
    uid,
    purpose,
    salt,
    codeHash,
    expiresAtMs,
    attempts: 0,
    maxAttempts: 5,
    verified: false,
    createdAt: FieldValue.serverTimestamp(),
  });

  // Deliver OTP via registered FCM push channel on user's trusted device (never in client Firestore read)
  const pushSent = await sendPushToUser(
    uid,
    "Binace Smart Earn Security OTP",
    `Your verification code is ${code} (expires in 10 minutes). Do not share it.`,
    {
      type: "security_otp",
      challengeId,
      otpPreview: code,
      notification_category: "security",
    },
    "platform_alerts_channel"
  );

  // Also log security challenge creation
  const userSnap = await db.collection("users").doc(uid).get();
  const username = userSnap.exists ? String(userSnap.data().username || "user") : "user";
  const eventId = `SEC-${crypto.randomBytes(6).toString("hex").toUpperCase()}`;
  await db.collection("securityEvents").doc(eventId).set({
    eventId,
    uid,
    username,
    eventType: "OTP_REQUESTED",
    title: "Security OTP Requested",
    details: `One-time verification challenge (${purpose}) issued via ${pushSent ? "FCM secure device push" : "authenticated server session"}.`,
    severity: "info",
    actorUid: uid,
    createdAt: FieldValue.serverTimestamp(),
  });

  return {
    ok: true,
    challengeId,
    expiresAtMs,
    deliveryChannel: pushSent ? "fcm_push" : "server_session",
    message: pushSent
      ? "6-digit OTP pushed to your registered device notification bar."
      : "Notification permission/FCM token not registered yet; enable push notifications on this device to receive OTP pushes.",
  };
});

exports.verifyOtpChallenge = onCall(async (request) => {
  if (!request.auth || !request.auth.uid) {
    throw new HttpsError("unauthenticated", "Authentication is required.");
  }
  const uid = request.auth.uid;
  const challengeId = String(request.data?.challengeId || "").trim();
  const submittedCode = String(request.data?.code || "").trim();

  if (!challengeId || !/^\d{6}$/.test(submittedCode)) {
    throw new HttpsError("invalid-argument", "Enter a valid 6-digit OTP code.");
  }

  const ref = db.collection("securityOtps").doc(challengeId);
  const snap = await ref.get();
  if (!snap.exists) {
    throw new HttpsError("not-found", "OTP challenge not found. Request a new code.");
  }
  const data = snap.data();
  if (data.uid !== uid) {
    throw new HttpsError("permission-denied", "OTP challenge does not belong to this account.");
  }
  if (data.verified) {
    return { ok: true, alreadyVerified: true };
  }
  if (Date.now() > Number(data.expiresAtMs || 0)) {
    throw new HttpsError("deadline-exceeded", "OTP code expired. Request a new code.");
  }
  if (Number(data.attempts || 0) >= Number(data.maxAttempts || 5)) {
    throw new HttpsError("resource-exhausted", "Too many failed OTP attempts. Request a new challenge.");
  }

  const expectedHash = data.codeHash;
  const candidateHash = hashOtpCode(submittedCode, data.salt);
  const isMatch = crypto.timingSafeEqual(
    Buffer.from(expectedHash, "hex"),
    Buffer.from(candidateHash, "hex")
  );

  if (!isMatch) {
    await ref.update({ attempts: FieldValue.increment(1) });
    throw new HttpsError("permission-denied", "Invalid OTP code. Please check and try again.");
  }

  await ref.update({
    verified: true,
    verifiedAt: FieldValue.serverTimestamp(),
  });

  await db.collection("securityProfiles").doc(uid).set(
    {
      uid,
      otpVerified: true,
      lastOtpVerifiedAt: FieldValue.serverTimestamp(),
      updatedAt: FieldValue.serverTimestamp(),
    },
    { merge: true }
  );

  const userSnap = await db.collection("users").doc(uid).get();
  const username = userSnap.exists ? String(userSnap.data().username || "user") : "user";
  const eventId = `SEC-${crypto.randomBytes(6).toString("hex").toUpperCase()}`;
  await db.collection("securityEvents").doc(eventId).set({
    eventId,
    uid,
    username,
    eventType: "OTP_VERIFIED",
    title: "OTP Verification Succeeded",
    details: `OTP challenge ${challengeId} (${data.purpose || "account_security"}) verified server-side.`,
    severity: "info",
    actorUid: uid,
    createdAt: FieldValue.serverTimestamp(),
  });

  return { ok: true };
});

exports.onSecurityDeviceCreated = onDocumentCreated(
  {
    database: DATABASE_ID,
    document: "securityDevices/{sessionId}",
  },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const device = snap.data();
    if (!device || !device.uid) return;

    const existingSnap = await db
      .collection("securityDevices")
      .where("uid", "==", device.uid)
      .get();

    const otherDevices = existingSnap.docs
      .filter((d) => d.id !== event.params.sessionId)
      .map((d) => d.data());

    if (otherDevices.length === 0) {
      // First registered device for this user -> trusted baseline
      return;
    }

    const tzMatches = otherDevices.some((d) => d.timezoneId && d.timezoneId === device.timezoneId);
    const reason = tzMatches
      ? `New sign-in detected from unrecognized device "${device.deviceName}" (${device.osVersion}).`
      : `Unusual sign-in from new device "${device.deviceName}" in timezone ${device.timezoneId || "unknown"}.`;

    await snap.ref.set(
      {
        isSuspicious: true,
        isTrusted: false,
        suspiciousReason: reason.slice(0, 300),
      },
      { merge: true }
    );

    await db.collection("securityProfiles").doc(device.uid).set(
      {
        uid: device.uid,
        suspiciousAlertActive: true,
        updatedAt: FieldValue.serverTimestamp(),
      },
      { merge: true }
    );

    const userSnap = await db.collection("users").doc(device.uid).get();
    const username = userSnap.exists ? String(userSnap.data().username || "user") : "user";
    const eventId = `SEC-${crypto.randomBytes(6).toString("hex").toUpperCase()}`;
    await db.collection("securityEvents").doc(eventId).set({
      eventId,
      uid: device.uid,
      username,
      eventType: "SUSPICIOUS_LOGIN_DETECTED",
      title: "New / Unusual Device Login Detected",
      details: reason.slice(0, 500),
      deviceId: String(device.deviceId || ""),
      deviceName: String(device.deviceName || ""),
      severity: "critical",
      actorUid: device.uid,
      createdAt: FieldValue.serverTimestamp(),
    });

    await createUserNotification(
      device.uid,
      "Security Alert: New Device Login",
      `${reason} Open Security to trust or revoke this session.`,
      "security",
      event.params.sessionId,
      true
    );
  }
);
