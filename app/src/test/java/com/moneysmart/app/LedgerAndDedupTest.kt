package com.moneysmart.app

import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.repository.LedgerCalculations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerAndDedupTest {

    @Test
    fun accountBalancesReflectExpenseIncomeTransferAndIgnoreDeleted() {
        val bank = AccountEntity(id = 1L, name = "Bank", type = AccountType.BANK, currency = "USD", initialBalance = 1000.0)
        val wallet = AccountEntity(id = 2L, name = "Wallet", type = AccountType.E_WALLET, currency = "USD", initialBalance = 50.0)

        val txs = listOf(
            TransactionEntity(id = 1, type = TransactionType.INCOME, amount = 500.0, currency = "USD", baseAmount = 500.0, accountId = 1L),
            TransactionEntity(id = 2, type = TransactionType.EXPENSE, amount = 120.0, currency = "USD", baseAmount = 120.0, accountId = 1L),
            TransactionEntity(id = 3, type = TransactionType.TRANSFER, amount = 200.0, currency = "USD", baseAmount = 200.0, accountId = 1L, toAccountId = 2L),
            TransactionEntity(id = 4, type = TransactionType.EXPENSE, amount = 30.0, currency = "USD", baseAmount = 30.0, accountId = 2L),
            TransactionEntity(id = 5, type = TransactionType.EXPENSE, amount = 999.0, currency = "USD", baseAmount = 999.0, accountId = 1L, isDeleted = true)
        )

        // Bank: 1000 + 500 - 120 - 200 = 1180.0
        assertEquals(1180.0, LedgerCalculations.computeAccountBalance(bank, txs, "USD"), 0.001)
        // Wallet: 50 + 200 - 30 = 220.0
        assertEquals(220.0, LedgerCalculations.computeAccountBalance(wallet, txs, "USD"), 0.001)
    }

    @Test
    fun multiCurrencyRateConversionComputesAccurateBaseAmount() {
        // Suppose base is USD and API reports 1 USD = 0.80 GBP
        val rateToBase = LedgerCalculations.exchangeRateToBaseFromApiRate(
            txCurrency = "GBP",
            baseCurrency = "USD",
            apiRateBaseToTx = 0.80
        )
        assertEquals(1.25, rateToBase, 0.0001)
        val baseAmount = LedgerCalculations.computeBaseAmount(80.0, rateToBase)
        assertEquals(100.0, baseAmount, 0.01)
    }

    @Test
    fun deduplicationMatchesSameAmountAndCurrencyWithinTwoMinutes() {
        val baseTime = 1_700_000_000_000L
        val existing = listOf(
            TransactionEntity(
                id = 10L,
                type = TransactionType.EXPENSE,
                amount = 19.99,
                currency = "USD",
                baseAmount = 19.99,
                accountId = 1L,
                timestamp = baseTime
            ),
            TransactionEntity(
                id = 11L,
                type = TransactionType.EXPENSE,
                amount = 45.00,
                currency = "USD",
                baseAmount = 45.00,
                accountId = 1L,
                timestamp = baseTime,
                isDeleted = true
            )
        )

        val match = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 19.99,
            currency = "usd",
            timestamp = baseTime + 60_000L
        )
        assertNotNull(match)
        assertEquals(10L, match!!.id)

        val outsideWindow = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 19.99,
            currency = "USD",
            timestamp = baseTime + 180_000L
        )
        assertNull(outsideWindow)

        val differentCurrency = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 19.99,
            currency = "EUR",
            timestamp = baseTime + 5_000L
        )
        assertNull(differentCurrency)

        val deletedNotMatched = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 45.00,
            currency = "USD",
            timestamp = baseTime + 1_000L
        )
        assertNull(deletedNotMatched)
    }

    @Test
    fun crossAppMerchantDeduplicationCatchesDelayedBankAlertAfterWalletTap() {
        val tapTime = 1_700_000_000_000L
        val existing = listOf(
            TransactionEntity(
                id = 20L,
                type = TransactionType.EXPENSE,
                amount = 6.40,
                currency = "SGD",
                baseAmount = 6.40,
                accountId = 2L,
                merchant = "PLAYMADE - JURONG POINT",
                timestamp = tapTime,
                rawNotificationId = 100L
            )
        )
        val sourcePackages = mapOf(20L to "com.samsung.android.spay")

        // OCBC alert arrives 1 minute later with truncated merchant name
        val ocbc1MinLater = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 60_000L,
            type = TransactionType.EXPENSE,
            merchant = "PLAYMADE - J...",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.ocbc.mobile" }
        )
        assertNotNull(ocbc1MinLater)
        assertEquals(20L, ocbc1MinLater!!.id)

        // OCBC alert arrives 6 minutes later (outside 2-min window, inside 15-min cross-app merchant window)
        val ocbc6MinLater = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 360_000L,
            type = TransactionType.EXPENSE,
            merchant = "PLAYMADE - J...",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.ocbc.mobile" }
        )
        assertNotNull(ocbc6MinLater)
        assertEquals(20L, ocbc6MinLater!!.id)

        // Same app 6 minutes later -> keep as separate transaction (e.g., buying a second drink)
        val sameAppLater = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 360_000L,
            type = TransactionType.EXPENSE,
            merchant = "PLAYMADE - JURONG POINT",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.samsung.android.spay" }
        )
        assertNull(sameAppLater)

        // Different merchant from another app 6 minutes later -> keep as separate transaction
        val differentMerchant = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 360_000L,
            type = TransactionType.EXPENSE,
            merchant = "7-ELEVEN",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.ocbc.mobile" }
        )
        assertNull(differentMerchant)

        // Different transaction type (e.g., INCOME refund/repayment) 30 seconds later -> keep separate
        val differentType = LedgerCalculations.findDuplicateTransaction(
            existing = existing,
            amount = 6.40,
            currency = "SGD",
            timestamp = tapTime + 30_000L,
            type = TransactionType.INCOME,
            merchant = "PLAYMADE - JURONG POINT",
            isDifferentSourceApp = { tx -> sourcePackages[tx.id] != "com.ocbc.mobile" }
        )
        assertNull(differentType)
    }

    @Test
    fun standaloneReimbursementsOffsetExpensesAndExcludeFromIncomeWithoutDoubleCountingSplits() {
        val transactions = listOf(
            // Salary = SGD 1000
            TransactionEntity(id = 1L, type = TransactionType.INCOME, amount = 1000.0, currency = "SGD", baseAmount = 1000.0, accountId = 1L),
            // Covered dinner for family = SGD 50
            TransactionEntity(id = 2L, type = TransactionType.EXPENSE, amount = 50.0, currency = "SGD", baseAmount = 50.0, accountId = 1L),
            // Transfer back SGD 50 marked as Reimbursement (unlinked to split bill)
            TransactionEntity(id = 3L, type = TransactionType.INCOME, amount = 50.0, currency = "SGD", baseAmount = 50.0, accountId = 1L, isReimbursement = true, splitBillId = null),
            // Groceries SGD 30, Bank Cashback SGD 2.50 marked as Reimbursement
            TransactionEntity(id = 4L, type = TransactionType.EXPENSE, amount = 30.0, currency = "SGD", baseAmount = 30.0, accountId = 1L),
            TransactionEntity(id = 5L, type = TransactionType.INCOME, amount = 2.50, currency = "SGD", baseAmount = 2.50, accountId = 1L, isReimbursement = true, splitBillId = null),
            // Split bill dinner SGD 60 where friend repaid SGD 30 (already reflected in reimbursedBaseAmount)
            TransactionEntity(id = 6L, type = TransactionType.EXPENSE, amount = 60.0, currency = "SGD", baseAmount = 60.0, reimbursedAmount = 30.0, reimbursedBaseAmount = 30.0, accountId = 1L, splitBillId = 10L),
            TransactionEntity(id = 7L, type = TransactionType.INCOME, amount = 30.0, currency = "SGD", baseAmount = 30.0, accountId = 1L, isReimbursement = true, splitBillId = 10L)
        )

        val totalIncome = transactions.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }
        val totalExpenses = LedgerCalculations.effectiveTotalExpensesBase(transactions)

        // Only real Salary (1000.0) counts as income; reimbursements must not inflate income
        assertEquals(1000.0, totalIncome, 0.001)
        // Net expenses: (50 - 50) + (30 - 2.50) + (60 - 30) = 57.50 (split repayment #7 not subtracted twice)
        assertEquals(57.50, totalExpenses, 0.001)
    }

    @Test
    fun lexicalAccountGroundingRejectsUnmentionedWalletsAndSameBankCard() {
        val ocbc = AccountEntity(id = 1L, name = "OCBC Bank", type = AccountType.BANK, currency = "SGD")
        val mari = AccountEntity(id = 2L, name = "MariBank", type = AccountType.BANK, currency = "SGD")
        val youTrip = AccountEntity(id = 3L, name = "YouTrip", type = AccountType.E_WALLET, currency = "SGD")
        val ocbcCard = AccountEntity(
            id = 4L,
            name = "OCBC 365 Card",
            type = AccountType.CREDIT_CARD,
            currency = "SGD",
            cardLast4 = "0624"
        )
        val all = listOf(ocbc, mari, youTrip, ocbcCard)

        assertFalse(
            LedgerCalculations.isAccountMentionedInNotification(
                account = youTrip,
                appName = "OCBC Digital",
                packageName = "com.ocbc.mobile",
                title = "FAST Transfer",
                text = "You transferred SGD 232.00 to account ending 8888.",
                allAccounts = all,
                excludeAccount = ocbc
            )
        )
        assertFalse(
            LedgerCalculations.isAccountMentionedInNotification(
                account = ocbcCard,
                appName = "OCBC Digital",
                packageName = "com.ocbc.mobile",
                title = "FAST Transfer",
                text = "You transferred SGD 232.00 to account ending 8888.",
                allAccounts = all,
                excludeAccount = ocbc
            )
        )
        assertNull(
            LedgerCalculations.findMentionedAccount(
                accounts = all,
                appName = "OCBC Digital",
                packageName = "com.ocbc.mobile",
                title = "FAST Transfer",
                text = "You transferred SGD 232.00 to account ending 8888.",
                excludeAccount = ocbc
            )
        )

        val matchedDest = LedgerCalculations.findMentionedAccount(
            accounts = all,
            appName = "OCBC Digital",
            packageName = "com.ocbc.mobile",
            title = "FAST Transfer",
            text = "You transferred SGD 232.00 to MariBank Savings.",
            excludeAccount = ocbc
        )
        assertEquals(mari.id, matchedDest?.id)

        val matchedCardPayment = LedgerCalculations.findMentionedAccount(
            accounts = listOf(ocbc, ocbcCard),
            appName = "OCBC Digital",
            packageName = "com.ocbc.mobile",
            title = "Bill Payment",
            text = "Paid SGD 500.00 to Credit Card.",
            excludeAccount = ocbc
        )
        assertEquals(ocbcCard.id, matchedCardPayment?.id)
    }

    @Test
    fun transferPairingUnifiesOutgoingAndIncomingOwnBankAlertsAcrossOrders() {
        val ocbc = AccountEntity(id = 1L, name = "OCBC Bank", type = AccountType.BANK, currency = "SGD")
        val mari = AccountEntity(id = 2L, name = "MariBank", type = AccountType.BANK, currency = "SGD")
        val youTrip = AccountEntity(id = 3L, name = "YouTrip", type = AccountType.E_WALLET, currency = "SGD")
        val accountsById = listOf(ocbc, mari, youTrip).associateBy { it.id }
        val t0 = 1_700_000_000_000L

        // 1. Outgoing TRANSFER(OCBC -> null) + incoming INCOME(MariBank) -> TRANSFER(OCBC -> MariBank)
        val outgoingUnresolved = TransactionEntity(
            id = 101L,
            type = TransactionType.TRANSFER,
            amount = 232.0,
            currency = "SGD",
            baseAmount = 232.0,
            accountId = ocbc.id,
            toAccountId = null,
            merchant = "OCBC",
            timestamp = t0,
            needsReview = true
        )
        val match1 = LedgerCalculations.findTransferCounterpart(
            existingCandidates = listOf(outgoingUnresolved to "OCBC FAST transfer SGD 232.00"),
            incomingType = TransactionType.INCOME,
            incomingAccountId = mari.id,
            incomingToAccountId = null,
            incomingAmount = 232.0,
            incomingCurrency = "SGD",
            incomingTimestamp = t0 + 30_000L,
            incomingText = "MariBank You received SGD 232.00",
            incomingMerchant = "MariBank",
            incomingNote = "Incoming transfer",
            accountsById = accountsById
        )
        assertNotNull(match1)
        assertEquals(ocbc.id, match1!!.sourceAccountId)
        assertEquals(mari.id, match1.destAccountId)
        assertTrue(match1.shouldUpdateExisting)

        // 2. Outgoing TRANSFER(OCBC -> YouTrip [unverified]) + incoming INCOME(MariBank) -> corrects to OCBC -> MariBank
        val outgoingMisrouted = TransactionEntity(
            id = 102L,
            type = TransactionType.TRANSFER,
            amount = 232.0,
            currency = "SGD",
            baseAmount = 232.0,
            accountId = ocbc.id,
            toAccountId = youTrip.id,
            merchant = "OCBC",
            timestamp = t0
        )
        val match2 = LedgerCalculations.findTransferCounterpart(
            existingCandidates = listOf(outgoingMisrouted to "OCBC You transferred SGD 232.00 via FAST"),
            incomingType = TransactionType.INCOME,
            incomingAccountId = mari.id,
            incomingToAccountId = null,
            incomingAmount = 232.0,
            incomingCurrency = "SGD",
            incomingTimestamp = t0 + 20_000L,
            incomingText = "MariBank You received SGD 232.00",
            incomingMerchant = "MariBank",
            incomingNote = "Incoming transfer",
            accountsById = accountsById
        )
        assertNotNull(match2)
        assertEquals(ocbc.id, match2!!.sourceAccountId)
        assertEquals(mari.id, match2.destAccountId)
        assertTrue(match2.shouldUpdateExisting)

        // 3. Outgoing EXPENSE with transfer phrasing + incoming INCOME(MariBank) -> TRANSFER(OCBC -> MariBank)
        val outgoingExpense = TransactionEntity(
            id = 103L,
            type = TransactionType.EXPENSE,
            amount = 232.0,
            currency = "SGD",
            baseAmount = 232.0,
            accountId = ocbc.id,
            merchant = "FAST Transfer",
            timestamp = t0
        )
        val match3 = LedgerCalculations.findTransferCounterpart(
            existingCandidates = listOf(outgoingExpense to "OCBC You transferred SGD 232.00 via FAST"),
            incomingType = TransactionType.INCOME,
            incomingAccountId = mari.id,
            incomingToAccountId = null,
            incomingAmount = 232.0,
            incomingCurrency = "SGD",
            incomingTimestamp = t0 + 15_000L,
            incomingText = "MariBank You received SGD 232.00",
            incomingMerchant = "MariBank",
            incomingNote = "FAST deposit",
            accountsById = accountsById
        )
        assertNotNull(match3)
        assertEquals(ocbc.id, match3!!.sourceAccountId)
        assertEquals(mari.id, match3.destAccountId)
        assertTrue(match3.shouldUpdateExisting)

        // 4. Reverse order: incoming INCOME(MariBank) first, then outgoing TRANSFER(OCBC -> null) or EXPENSE(PayNow)
        val incomingFirst = TransactionEntity(
            id = 104L,
            type = TransactionType.INCOME,
            amount = 232.0,
            currency = "SGD",
            baseAmount = 232.0,
            accountId = mari.id,
            merchant = "MariBank",
            timestamp = t0,
            isReimbursement = true
        )
        val match4a = LedgerCalculations.findTransferCounterpart(
            existingCandidates = listOf(incomingFirst to "MariBank You received SGD 232.00"),
            incomingType = TransactionType.TRANSFER,
            incomingAccountId = ocbc.id,
            incomingToAccountId = null,
            incomingAmount = 232.0,
            incomingCurrency = "SGD",
            incomingTimestamp = t0 + 10_000L,
            incomingText = "OCBC You transferred SGD 232.00",
            incomingMerchant = "OCBC",
            incomingNote = "FAST transfer",
            accountsById = accountsById
        )
        assertNotNull(match4a)
        assertEquals(ocbc.id, match4a!!.sourceAccountId)
        assertEquals(mari.id, match4a.destAccountId)
        assertTrue(match4a.shouldUpdateExisting)

        val match4b = LedgerCalculations.findTransferCounterpart(
            existingCandidates = listOf(incomingFirst to "MariBank You received SGD 232.00"),
            incomingType = TransactionType.EXPENSE,
            incomingAccountId = ocbc.id,
            incomingToAccountId = null,
            incomingAmount = 232.0,
            incomingCurrency = "SGD",
            incomingTimestamp = t0 + 10_000L,
            incomingText = "OCBC PayNow transfer SGD 232.00",
            incomingMerchant = "PayNow",
            incomingNote = "PayNow transfer",
            accountsById = accountsById
        )
        assertNotNull(match4b)
        assertEquals(ocbc.id, match4b!!.sourceAccountId)
        assertEquals(mari.id, match4b.destAccountId)
        assertTrue(match4b.shouldUpdateExisting)

        // 5. Existing TRANSFER(OCBC -> null) + incoming TRANSFER(OCBC -> MariBank) -> enriches destination
        val match5 = LedgerCalculations.findTransferCounterpart(
            existingCandidates = listOf(outgoingUnresolved to "OCBC FAST transfer SGD 232.00"),
            incomingType = TransactionType.TRANSFER,
            incomingAccountId = ocbc.id,
            incomingToAccountId = mari.id,
            incomingAmount = 232.0,
            incomingCurrency = "SGD",
            incomingTimestamp = t0 + 5_000L,
            incomingText = "OCBC transferred SGD 232.00 to MariBank",
            incomingMerchant = "OCBC",
            incomingNote = "Transfer to MariBank",
            accountsById = accountsById
        )
        assertNotNull(match5)
        assertEquals(ocbc.id, match5!!.sourceAccountId)
        assertEquals(mari.id, match5.destAccountId)
        assertTrue(match5.shouldUpdateExisting)
    }

    @Test
    fun transferPairingDoesNotFalseMatchRetailExpensesSameAccountOrOutsideWindow() {
        val ocbc = AccountEntity(id = 1L, name = "OCBC Bank", type = AccountType.BANK, currency = "SGD")
        val mari = AccountEntity(id = 2L, name = "MariBank", type = AccountType.BANK, currency = "SGD")
        val accountsById = listOf(ocbc, mari).associateBy { it.id }
        val t0 = 1_700_000_000_000L

        // Retail dining charge on OCBC + friend PayNow repayment on MariBank 1 min later -> keep separate!
        val diningExpense = TransactionEntity(
            id = 201L,
            type = TransactionType.EXPENSE,
            amount = 50.0,
            currency = "SGD",
            baseAmount = 50.0,
            accountId = ocbc.id,
            merchant = "HAIDILAO",
            timestamp = t0
        )
        assertNull(
            LedgerCalculations.findTransferCounterpart(
                existingCandidates = listOf(diningExpense to "OCBC SGD 50.00 was charged at HAIDILAO"),
                incomingType = TransactionType.INCOME,
                incomingAccountId = mari.id,
                incomingToAccountId = null,
                incomingAmount = 50.0,
                incomingCurrency = "SGD",
                incomingTimestamp = t0 + 60_000L,
                incomingText = "MariBank Received SGD 50.00 from Alex",
                incomingMerchant = "Alex",
                incomingNote = "Dinner share",
                accountsById = accountsById
            )
        )

        // Same-account transfer/refund -> never pair with self
        val ocbcTransfer = TransactionEntity(
            id = 202L,
            type = TransactionType.TRANSFER,
            amount = 232.0,
            currency = "SGD",
            baseAmount = 232.0,
            accountId = ocbc.id,
            toAccountId = null,
            merchant = "OCBC",
            timestamp = t0
        )
        assertNull(
            LedgerCalculations.findTransferCounterpart(
                existingCandidates = listOf(ocbcTransfer to "OCBC FAST transfer SGD 232.00"),
                incomingType = TransactionType.INCOME,
                incomingAccountId = ocbc.id,
                incomingToAccountId = null,
                incomingAmount = 232.0,
                incomingCurrency = "SGD",
                incomingTimestamp = t0 + 30_000L,
                incomingText = "OCBC Refund SGD 232.00",
                incomingMerchant = "OCBC",
                incomingNote = "Refund",
                accountsById = accountsById
            )
        )

        // Outside 5-minute window -> do not pair
        assertNull(
            LedgerCalculations.findTransferCounterpart(
                existingCandidates = listOf(ocbcTransfer to "OCBC FAST transfer SGD 232.00"),
                incomingType = TransactionType.INCOME,
                incomingAccountId = mari.id,
                incomingToAccountId = null,
                incomingAmount = 232.0,
                incomingCurrency = "SGD",
                incomingTimestamp = t0 + 360_000L,
                incomingText = "MariBank You received SGD 232.00",
                incomingMerchant = "MariBank",
                incomingNote = "Deposit",
                accountsById = accountsById
            )
        )
    }
}
