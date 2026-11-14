package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.data.model.AppConstants
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class BinaceSmartEarnRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun observeActiveTasks_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val taskRepo = TaskRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) { taskRepo.observeActiveTasks().first() }
            fail("Expected PERMISSION_DENIED when unauthenticated")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull("Expected FirebaseFirestoreException, got $e", fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    @Test
    fun userRegistration_crossUserIsolation_blocksReadingOtherProfileAndWallet() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)
        val aliceUid = signInTestUser("alice_$suffix@test.com")
        val userRepo = UserRepository(firestore, auth)
        val regResult = withTimeout(OP_TIMEOUT_MS) {
            userRepo.registerUser("Alice Khan", "alice$suffix", "alice_$suffix@test.com", "+923001112233", "")
        }
        assertTrue("Alice registration failed: ${regResult.exceptionOrNull()}", regResult.isSuccess)

        signInTestUser("bob_$suffix@test.com")
        val bobRepo = UserRepository(firestore, auth)
        try {
            withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeUserProfile(aliceUid).first() }
            fail("Expected PERMISSION_DENIED reading another user's profile")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull(fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
        try {
            withTimeout(FLOW_TIMEOUT_MS) { bobRepo.observeWallet(aliceUid).first() }
            fail("Expected PERMISSION_DENIED reading another user's wallet")
        } catch (e: Exception) {
            val fsEx = e.unwrapFirestoreException()
            assertNotNull(fsEx)
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx!!.code)
        }
    }

    @Test
    fun endToEnd_12StepUpgradeWorkflow_10Tasks200Pkr_WalletSync_Referral_Deposit_Withdrawal() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)

        // Step 1-2: Admin seeds 10 default daily tasks (20 PKR each) & PKR 100 commission settings
        val adminUid = signInTestUser(AppConstants.BOOTSTRAP_ADMIN_EMAIL, verifyEmail = true)
        val adminRepo = AdminRepository(firestore, auth)
        val seedRes = withTimeout(OP_TIMEOUT_MS) { adminRepo.seedDefaultsIfEmpty() }
        assertTrue("Admin seed failed: ${seedRes.exceptionOrNull()}", seedRes.isSuccess)
        assertTrue(withTimeout(OP_TIMEOUT_MS) { UserRepository(firestore, auth).isUserAdmin(adminUid) })

        // Step 1: Alice registers & wallets/{aliceUid} is automatically created
        val aliceUid = signInTestUser("alice_e2e_$suffix@test.com")
        val aliceUserRepo = UserRepository(firestore, auth)
        val aliceProfile = withTimeout(OP_TIMEOUT_MS) {
            aliceUserRepo.registerUser("Alice Referrer", "alice$suffix", "alice_e2e_$suffix@test.com", "+923001110000", "").getOrThrow()
        }
        val aliceInitialWallet = withTimeout(FLOW_TIMEOUT_MS) {
            aliceUserRepo.observeWallet(aliceUid).first { it != null }
        }!!
        assertEquals(aliceProfile.walletId, aliceInitialWallet.walletId)
        assertEquals(0.0, aliceInitialWallet.balance, 0.01)

        // Self-referral check
        val selfRefFail = withTimeout(OP_TIMEOUT_MS) {
            aliceUserRepo.registerUser("Alice Two", "alice2$suffix", "alice_e2e_$suffix@test.com", "+923001110000", aliceProfile.referralCode)
        }
        assertTrue("Self-referral must fail", selfRefFail.isFailure)

        // Step 3-4: Load all 10 active daily tasks and complete the 1st task (20 PKR)
        val taskRepo = TaskRepository(firestore, auth)
        val tasks = withTimeout(FLOW_TIMEOUT_MS) { taskRepo.observeActiveTasks().first { it.size == 10 } }
        assertEquals(10, tasks.size)

        val firstTask = tasks.first()
        assertEquals(20.0, firstTask.rewardPoints, 0.01)
        val startRes = withTimeout(OP_TIMEOUT_MS) { taskRepo.startTask(firstTask) }
        assertTrue("Start task failed: ${startRes.exceptionOrNull()}", startRes.isSuccess)

        val claimFirstRes = withTimeout(OP_TIMEOUT_MS) { taskRepo.claimTaskReward(firstTask, aliceProfile) }
        assertTrue("Claim 1st task reward failed: ${claimFirstRes.exceptionOrNull()}", claimFirstRes.isSuccess)

        var currentAlice = withTimeout(FLOW_TIMEOUT_MS) {
            aliceUserRepo.observeUserProfile(aliceUid).first { it != null && it.completedTasksCount == 1L }
        }!!
        assertEquals(20.0, currentAlice.realRewardBalance, 0.01)
        assertEquals(20.0, currentAlice.todayTaskEarnings, 0.01)

        // Step 6: Verify duplicate completion of 1st task is blocked
        val dupClaim = withTimeout(OP_TIMEOUT_MS) { taskRepo.claimTaskReward(firstTask, currentAlice) }
        assertTrue("Duplicate task claim must fail", dupClaim.isFailure)

        // Step 5: Complete remaining 9 tasks -> verify total daily reward = 200 PKR across users & wallets
        for (task in tasks.drop(1)) {
            val res = withTimeout(OP_TIMEOUT_MS) { taskRepo.claimTaskReward(task, currentAlice) }
            assertTrue("Claiming ${task.id} failed: ${res.exceptionOrNull()}", res.isSuccess)
            val expectedCount = currentAlice.completedTasksCount + 1L
            currentAlice = withTimeout(FLOW_TIMEOUT_MS) {
                aliceUserRepo.observeUserProfile(aliceUid).first { it != null && it.completedTasksCount == expectedCount }
            }!!
        }
        assertEquals(10L, currentAlice.completedTasksCount)
        assertEquals(200.0, currentAlice.todayTaskEarnings, 0.01)
        assertEquals(200.0, currentAlice.realRewardBalance, 0.01)

        val aliceWalletAfter10 = withTimeout(FLOW_TIMEOUT_MS) {
            aliceUserRepo.observeWallet(aliceUid).first { it != null && it.balance >= 200.0 }
        }!!
        assertEquals(200.0, aliceWalletAfter10.balance, 0.01)
        assertEquals(200.0, aliceWalletAfter10.todayTaskEarnings, 0.01)

        // Step 7: Bob registers using Alice's referral code
        val bobUid = signInTestUser("bob_e2e_$suffix@test.com")
        val bobUserRepo = UserRepository(firestore, auth)
        val bobProfile = withTimeout(OP_TIMEOUT_MS) {
            bobUserRepo.registerUser("Bob Referred", "bob$suffix", "bob_e2e_$suffix@test.com", "+923002220000", aliceProfile.referralCode).getOrThrow()
        }
        assertEquals(aliceProfile.referralCode, bobProfile.referredBy)

        // Step 8: Bob submits a deposit request with paymentMethod = "Easypaisa" (PKR 500)
        val bobWalletRepo = WalletRepository(firestore, auth)
        val overWithdraw = withTimeout(OP_TIMEOUT_MS) {
            bobWalletRepo.submitWithdrawalRequest(bobProfile, 100.0, "Easypaisa", "03001234567", emptyList())
        }
        assertTrue("Withdrawal exceeding 0 balance must fail", overWithdraw.isFailure)

        val preDepositId = bobWalletRepo.newDepositId()
        val depId = withTimeout(OP_TIMEOUT_MS) {
            bobWalletRepo.submitDepositRequest(
                userProfile = bobProfile,
                amount = 500.0,
                paymentMethod = "EasyPaisa",
                reference = "TX-REF-$suffix",
                receiptBase64 = null,
                depositId = preDepositId,
                paymentAccountName = "Binace Smart Pay",
                paymentAccountNumber = "03450001122",
                receiptStoragePath = "deposit_receipts/$bobUid/$preDepositId/receipt.jpg",
                receiptUrl = "https://firebasestorage.googleapis.com/v0/b/test/o/receipt.jpg"
            ).getOrThrow()
        }
        assertEquals(preDepositId, depId)

        // Step 9: Admin approves Bob's deposit -> credits Bob PKR 500 (users + wallets) AND triggers PKR 100 commission for Alice
        signInTestUser(AppConstants.BOOTSTRAP_ADMIN_EMAIL, verifyEmail = true)
        val deposits = withTimeout(FLOW_TIMEOUT_MS) {
            adminRepo.observeAllDeposits().first { list -> list.any { it.id == depId } }
        }
        val bobDeposit = deposits.first { it.id == depId }
        assertEquals("EasyPaisa", bobDeposit.paymentMethod)
        assertEquals("PENDING", bobDeposit.status)
        assertEquals("deposit_receipts/$bobUid/$preDepositId/receipt.jpg", bobDeposit.receiptStoragePath)
        val approveDepRes = withTimeout(OP_TIMEOUT_MS) {
            adminRepo.approveDeposit(
                deposit = bobDeposit,
                adminNote = "Verified receipt & TID",
                autoApproveReferralCommission = true
            )
        }
        assertTrue("Admin deposit approval failed: ${approveDepRes.exceptionOrNull()}", approveDepRes.isSuccess)

        // Duplicate approval on already APPROVED deposit must fail
        val dupApprove = withTimeout(OP_TIMEOUT_MS) {
            adminRepo.approveDeposit(bobDeposit, adminNote = "Retry")
        }
        assertTrue("Duplicate deposit approval must fail", dupApprove.isFailure)

        // Verify Alice received PKR 100 referral commission (200 + 100 = 300 PKR in users + wallets)
        signInTestUser("alice_e2e_$suffix@test.com")
        val aliceAfterComm = withTimeout(FLOW_TIMEOUT_MS) {
            aliceUserRepo.observeUserProfile(aliceUid).first { it != null && it.totalReferralCommission >= 100.0 }
        }!!
        assertEquals(100.0, aliceAfterComm.totalReferralCommission, 0.01)
        assertEquals(1L, aliceAfterComm.totalReferrals)
        assertEquals(300.0, aliceAfterComm.realRewardBalance, 0.01)
        val aliceWalletFinal = withTimeout(FLOW_TIMEOUT_MS) {
            aliceUserRepo.observeWallet(aliceUid).first { it != null && it.balance >= 300.0 }
        }!!
        assertEquals(300.0, aliceWalletFinal.balance, 0.01)

        // Step 10: Bob now has PKR 500 balance and submits a withdrawal request of PKR 200
        signInTestUser("bob_e2e_$suffix@test.com")
        val bobAfterDep = withTimeout(FLOW_TIMEOUT_MS) {
            bobUserRepo.observeUserProfile(bobUid).first { it != null && it.realBalance >= 500.0 }
        }!!
        val wdId = withTimeout(OP_TIMEOUT_MS) {
            bobWalletRepo.submitWithdrawalRequest(bobAfterDep, 200.0, "JazzCash", "03009998877", emptyList()).getOrThrow()
        }

        // Duplicate pending withdrawal blocked
        val pendingWds = withTimeout(FLOW_TIMEOUT_MS) {
            bobWalletRepo.observeUserWithdrawals(bobUid).first { it.any { w -> w.id == wdId } }
        }
        val dupWd = withTimeout(OP_TIMEOUT_MS) {
            bobWalletRepo.submitWithdrawalRequest(bobAfterDep, 100.0, "JazzCash", "03009998877", pendingWds)
        }
        assertTrue("Duplicate pending withdrawal must be blocked", dupWd.isFailure)

        // Step 11-12: Admin approves Bob's withdrawal -> Bob balance becomes PKR 300 in both users & wallets, plus real-time transactions
        signInTestUser(AppConstants.BOOTSTRAP_ADMIN_EMAIL, verifyEmail = true)
        val approveWdRes = withTimeout(OP_TIMEOUT_MS) { adminRepo.approveWithdrawal(pendingWds.first { it.id == wdId }) }
        assertTrue("Admin withdrawal approval failed: ${approveWdRes.exceptionOrNull()}", approveWdRes.isSuccess)
        val adminTxs = withTimeout(FLOW_TIMEOUT_MS) { adminRepo.observeAllTransactions().first { it.isNotEmpty() } }
        assertTrue("Admin transaction stream should contain task, deposit, commission, and withdrawal entries", adminTxs.size >= 4)

        signInTestUser("bob_e2e_$suffix@test.com")
        val bobFinal = withTimeout(FLOW_TIMEOUT_MS) {
            bobUserRepo.observeUserProfile(bobUid).first { it != null && it.realBalance == 300.0 }
        }!!
        assertEquals(300.0, bobFinal.realBalance, 0.01)
        val bobWalletFinal = withTimeout(FLOW_TIMEOUT_MS) {
            bobUserRepo.observeWallet(bobUid).first { it != null && it.balance == 300.0 }
        }!!
        assertEquals(300.0, bobWalletFinal.balance, 0.01)
    }

    @Test
    fun paymentAccounts_liveUpdates_duplicateRefBlocking_and_adminApprovalLifecycle() = runBlocking {
        val suffix = UUID.randomUUID().toString().take(6)

        // 1. Admin creates and updates EasyPaisa payment account settings
        signInTestUser(AppConstants.BOOTSTRAP_ADMIN_EMAIL, verifyEmail = true)
        val adminRepo = AdminRepository(firestore, auth)
        withTimeout(OP_TIMEOUT_MS) { adminRepo.seedDefaultsIfEmpty().getOrThrow() }

        val updatedEasyPaisa = com.example.data.model.PaymentAccountConfig(
            providerKey = "easypaisa",
            providerName = "EasyPaisa",
            accountHolderName = "Tariq Mahmood $suffix",
            accountNumber = "03457778899",
            instructions = "Send PKR to EasyPaisa and attach screenshot.",
            isEnabled = true
        )
        val saveAccountRes = withTimeout(OP_TIMEOUT_MS) { adminRepo.savePaymentAccount(updatedEasyPaisa) }
        assertTrue("Admin savePaymentAccount failed: ${saveAccountRes.exceptionOrNull()}", saveAccountRes.isSuccess)

        // 2. Non-admin registers, cannot edit paymentAccounts, and receives real-time updated account details
        val userUid = signInTestUser("wallet_user_$suffix@test.com")
        val userRepo = UserRepository(firestore, auth)
        val userProfile = withTimeout(OP_TIMEOUT_MS) {
            userRepo.registerUser("Zainab Ali", "zainab$suffix", "wallet_user_$suffix@test.com", "+923015556677", "").getOrThrow()
        }
        val userWalletRepo = WalletRepository(firestore, auth)

        val liveAccounts = withTimeout(FLOW_TIMEOUT_MS) {
            userWalletRepo.observePaymentAccounts().first { list ->
                list.any { it.providerKey == "easypaisa" && it.accountNumber == "03457778899" }
            }
        }
        val ep = liveAccounts.first { it.providerKey == "easypaisa" }
        assertEquals("Tariq Mahmood $suffix", ep.accountHolderName)

        // Non-admin write to paymentAccounts must fail
        val nonAdminWrite = withTimeout(OP_TIMEOUT_MS) {
            adminRepo.savePaymentAccount(ep.copy(accountNumber = "03000000000"))
        }
        assertTrue("Non-admin must be blocked from modifying payment accounts", nonAdminWrite.isFailure)

        // 3. Submit deposit with pre-generated depositId, receipt path/URL, and unique transaction ref
        val depId = userWalletRepo.newDepositId()
        val txRef = "EP-TID-$suffix"
        val depRes = withTimeout(OP_TIMEOUT_MS) {
            userWalletRepo.submitDepositRequest(
                userProfile = userProfile,
                amount = 1000.0,
                paymentMethod = ep.providerName,
                reference = txRef,
                receiptBase64 = null,
                depositId = depId,
                paymentAccountName = ep.accountHolderName,
                paymentAccountNumber = ep.accountNumber,
                receiptStoragePath = "deposit_receipts/$userUid/$depId/receipt.jpg",
                receiptUrl = "https://firebasestorage.googleapis.com/v0/b/test/o/ep_receipt.jpg"
            )
        }
        assertTrue("Deposit submission failed: ${depRes.exceptionOrNull()}", depRes.isSuccess)

        // Duplicate transaction reference (case-insensitive) must be blocked
        val dupRefRes = withTimeout(OP_TIMEOUT_MS) {
            userWalletRepo.submitDepositRequest(
                userProfile = userProfile,
                amount = 1000.0,
                paymentMethod = ep.providerName,
                reference = txRef.lowercase(),
                paymentAccountName = ep.accountHolderName,
                paymentAccountNumber = ep.accountNumber
            )
        }
        assertTrue("Duplicate transaction reference must be blocked", dupRefRes.isFailure)

        // Verify PENDING deposit in user stream
        val pendingDep = withTimeout(FLOW_TIMEOUT_MS) {
            userWalletRepo.observeUserDeposits(userUid).first { list ->
                list.any { it.effectiveDepositId == depId && it.status == "PENDING" }
            }
        }.first { it.effectiveDepositId == depId }
        assertEquals("Tariq Mahmood $suffix", pendingDep.paymentAccountName)
        assertEquals("03457778899", pendingDep.paymentAccountNumber)
        assertEquals("+923015556677", pendingDep.userPhone)

        // 4. Admin approves deposit with note -> user status becomes APPROVED, balance becomes 1000, tx logged
        signInTestUser(AppConstants.BOOTSTRAP_ADMIN_EMAIL, verifyEmail = true)
        val adminApproveRes = withTimeout(OP_TIMEOUT_MS) {
            adminRepo.approveDeposit(pendingDep, adminNote = "Receipt matched EasyPaisa statement")
        }
        assertTrue("Admin approveDeposit failed: ${adminApproveRes.exceptionOrNull()}", adminApproveRes.isSuccess)

        // 5. Submit a second deposit and verify Admin rejection leaves balance unchanged
        signInTestUser("wallet_user_$suffix@test.com")
        val updatedUser = withTimeout(FLOW_TIMEOUT_MS) {
            userRepo.observeUserProfile(userUid).first { it != null && it.realBalance == 1000.0 }
        }!!
        assertEquals(1000.0, updatedUser.realBalance, 0.01)

        val approvedDep = withTimeout(FLOW_TIMEOUT_MS) {
            userWalletRepo.observeUserDeposits(userUid).first { list ->
                list.any { it.effectiveDepositId == depId && it.status == "APPROVED" }
            }
        }.first { it.effectiveDepositId == depId }
        assertEquals("Receipt matched EasyPaisa statement", approvedDep.adminNote)
        assertEquals(AppConstants.BOOTSTRAP_ADMIN_EMAIL, approvedDep.reviewedBy)

        val rejDepId = withTimeout(OP_TIMEOUT_MS) {
            userWalletRepo.submitDepositRequest(
                userProfile = updatedUser,
                amount = 2500.0,
                paymentMethod = "JazzCash",
                reference = "JC-BAD-$suffix",
                paymentAccountName = ep.accountHolderName,
                paymentAccountNumber = ep.accountNumber
            ).getOrThrow()
        }
        val rejPending = withTimeout(FLOW_TIMEOUT_MS) {
            userWalletRepo.observeUserDeposits(userUid).first { list -> list.any { it.effectiveDepositId == rejDepId } }
        }.first { it.effectiveDepositId == rejDepId }

        signInTestUser(AppConstants.BOOTSTRAP_ADMIN_EMAIL, verifyEmail = true)
        val rejRes = withTimeout(OP_TIMEOUT_MS) {
            adminRepo.rejectDeposit(rejPending, "Blurred screenshot")
        }
        assertTrue("Admin rejectDeposit failed: ${rejRes.exceptionOrNull()}", rejRes.isSuccess)

        signInTestUser("wallet_user_$suffix@test.com")
        val rejectedDep = withTimeout(FLOW_TIMEOUT_MS) {
            userWalletRepo.observeUserDeposits(userUid).first { list ->
                list.any { it.effectiveDepositId == rejDepId && it.status == "REJECTED" }
            }
        }.first { it.effectiveDepositId == rejDepId }
        assertEquals("Blurred screenshot", rejectedDep.adminNote)

        val walletCheck = withTimeout(FLOW_TIMEOUT_MS) {
            userRepo.observeWallet(userUid).first { it != null }
        }!!
        assertEquals(1000.0, walletCheck.balance, 0.01)
    }

    private companion object {
        const val OP_TIMEOUT_MS = 10000L
        const val FLOW_TIMEOUT_MS = 8000L
    }
}
