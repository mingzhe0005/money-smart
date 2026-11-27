package com.moneysmart.app

import com.moneysmart.app.data.local.entity.AccountEntity
import com.moneysmart.app.data.local.entity.AccountType
import com.moneysmart.app.data.local.entity.TransactionEntity
import com.moneysmart.app.data.local.entity.TransactionType
import com.moneysmart.app.data.repository.LedgerCalculations
import org.junit.Assert.assertEquals
import org.junit.Test

class BillSplitterTest {

    @Test
    fun equalSplitHandlesOddCentsAccurately() {
        val (myShare, friendShares) = LedgerCalculations.computeEqualSplitShares(100.0, 2)
        // 100.00 / 3 = 33.33 + 1 cent remainder -> friends owe [33.34, 33.33], myShare = 33.33
        assertEquals(2, friendShares.size)
        val total = myShare + friendShares.sum()
        assertEquals(100.0, total, 0.0001)
    }

    @Test
    fun reimbursementsReduceEffectiveExpenseKeepZeroIncomeAndUpdateAccountBalances() {
        val ocbc = AccountEntity(id = 1L, name = "OCBC", type = AccountType.BANK, currency = "SGD", initialBalance = 500.0)
        val posb = AccountEntity(id = 2L, name = "POSB", type = AccountType.BANK, currency = "SGD", initialBalance = 100.0)
        val payLah = AccountEntity(id = 3L, name = "PayLah", type = AccountType.E_WALLET, currency = "SGD", initialBalance = 0.0)

        // Paid 120 dinner from OCBC; 2 friends repaid 40 each into POSB and PayLah
        val dinnerTx = TransactionEntity(
            id = 10L,
            type = TransactionType.EXPENSE,
            amount = 120.0,
            currency = "SGD",
            baseAmount = 120.0,
            accountId = 1L,
            reimbursedAmount = 80.0,
            reimbursedBaseAmount = 80.0,
            splitBillId = 1L
        )
        val repayPosb = TransactionEntity(
            id = 11L,
            type = TransactionType.INCOME,
            amount = 40.0,
            currency = "SGD",
            baseAmount = 40.0,
            accountId = 2L,
            isReimbursement = true,
            splitBillId = 1L
        )
        val repayPayLah = TransactionEntity(
            id = 12L,
            type = TransactionType.INCOME,
            amount = 40.0,
            currency = "SGD",
            baseAmount = 40.0,
            accountId = 3L,
            isReimbursement = true,
            splitBillId = 1L
        )
        val txs = listOf(dinnerTx, repayPosb, repayPayLah)

        // Real-world account balances
        assertEquals(380.0, LedgerCalculations.computeAccountBalance(ocbc, txs, "SGD"), 0.001)
        assertEquals(140.0, LedgerCalculations.computeAccountBalance(posb, txs, "SGD"), 0.001)
        assertEquals(40.0, LedgerCalculations.computeAccountBalance(payLah, txs, "SGD"), 0.001)

        // Effective expense = 120 - 80 = 40; Effective income = 0 (no fake income)
        assertEquals(40.0, txs.sumOf { LedgerCalculations.effectiveExpenseBaseAmount(it) }, 0.001)
        assertEquals(0.0, txs.sumOf { LedgerCalculations.effectiveIncomeBaseAmount(it) }, 0.001)
    }

    @Test
    fun linkingUnlinkedReimbursementToExpenseAvoidsDoubleCounting() {
        val unlinkedExpense = TransactionEntity(
            id = 20L,
            type = TransactionType.EXPENSE,
            amount = 20.0,
            currency = "SGD",
            baseAmount = 20.0,
            accountId = 1L
        )
        val unlinkedReimb = TransactionEntity(
            id = 21L,
            type = TransactionType.INCOME,
            amount = 8.0,
            currency = "SGD",
            baseAmount = 8.0,
            accountId = 2L,
            isReimbursement = true,
            splitBillId = null
        )

        assertEquals(
            12.0,
            LedgerCalculations.effectiveTotalExpensesBase(listOf(unlinkedExpense, unlinkedReimb)),
            0.001
        )

        val linkedExpense = unlinkedExpense.copy(
            reimbursedAmount = 8.0,
            reimbursedBaseAmount = 8.0,
            splitBillId = 1L
        )
        val linkedReimb = unlinkedReimb.copy(splitBillId = 1L)

        assertEquals(12.0, LedgerCalculations.effectiveExpenseBaseAmount(linkedExpense), 0.001)
        assertEquals(
            12.0,
            LedgerCalculations.effectiveTotalExpensesBase(listOf(linkedExpense, linkedReimb)),
            0.001
        )
    }

    @Test
    fun excessiveReimbursementCanLinkAcrossMultipleExpensesAndExcessOffsetsMonthlyExpenses() {
        val coffee = TransactionEntity(
            id = 30L,
            type = TransactionType.EXPENSE,
            amount = 10.0,
            currency = "SGD",
            baseAmount = 10.0,
            accountId = 1L
        )
        val dinner = TransactionEntity(
            id = 31L,
            type = TransactionType.EXPENSE,
            amount = 25.0,
            currency = "SGD",
            baseAmount = 25.0,
            accountId = 1L
        )
        val bigReimb = TransactionEntity(
            id = 32L,
            type = TransactionType.INCOME,
            amount = 30.0,
            currency = "SGD",
            baseAmount = 30.0,
            accountId = 2L,
            isReimbursement = true
        )

        // Step 1: Link $30 reimb to $10 coffee -> $10 allocated, $20 left unallocated
        val coffeeAfterStep1 = coffee.copy(reimbursedAmount = 10.0, reimbursedBaseAmount = 10.0, splitBillId = 1L)
        val reimbAfterStep1 = bigReimb.copy(reimbursedAmount = 10.0, reimbursedBaseAmount = 10.0, splitBillId = 1L)

        assertEquals(20.0, LedgerCalculations.remainingReimbursementAmount(reimbAfterStep1), 0.001)
        assertEquals(0.0, LedgerCalculations.effectiveExpenseBaseAmount(coffeeAfterStep1), 0.001)
        // Monthly total = ($0 coffee + $25 dinner) - $20 excess reimb = $5.00
        assertEquals(
            5.0,
            LedgerCalculations.effectiveTotalExpensesBase(listOf(coffeeAfterStep1, dinner, reimbAfterStep1)),
            0.001
        )

        // Step 2: Link remaining $20 from the SAME reimbursement to $25 dinner -> dinner net $5, reimb 30/30 used
        val dinnerAfterStep2 = dinner.copy(reimbursedAmount = 20.0, reimbursedBaseAmount = 20.0, splitBillId = 2L)
        val reimbAfterStep2 = reimbAfterStep1.copy(reimbursedAmount = 30.0, reimbursedBaseAmount = 30.0)

        assertEquals(0.0, LedgerCalculations.remainingReimbursementAmount(reimbAfterStep2), 0.001)
        assertEquals(5.0, LedgerCalculations.effectiveExpenseBaseAmount(dinnerAfterStep2), 0.001)
        // Monthly total stays $5.00 (no double counting!)
        assertEquals(
            5.0,
            LedgerCalculations.effectiveTotalExpensesBase(listOf(coffeeAfterStep1, dinnerAfterStep2, reimbAfterStep2)),
            0.001
        )

        // Legacy compatibility: old linked reimbursement with splitBillId != null and reimbursedAmount == 0.0
        val legacyLinked = TransactionEntity(
            id = 40L,
            type = TransactionType.INCOME,
            amount = 40.0,
            currency = "SGD",
            baseAmount = 40.0,
            accountId = 2L,
            isReimbursement = true,
            splitBillId = 9L,
            reimbursedAmount = 0.0,
            reimbursedBaseAmount = 0.0
        )
        assertEquals(0.0, LedgerCalculations.remainingReimbursementAmount(legacyLinked), 0.001)
        assertEquals(0.0, LedgerCalculations.unlinkedReimbursementBaseAmount(legacyLinked), 0.001)
    }
}
