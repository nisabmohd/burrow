package app.burrow.money.domain

/*
 * Account balance = opening balance + its transactions (docs/DATA.md § Money). SQL sums each
 * transaction type per account; this adds them up with the right signs. Pure, unit-tested.
 * Expenses and transfers out lower the account; income and transfers in raise it.
 */
data class TypeSum(val accountId: Long, val type: String, val total: Long)

fun balances(opening: Map<Long, Long>, outSums: List<TypeSum>, transfersIn: Map<Long, Long>): Map<Long, Long> =
    opening.mapValues { (id, start) ->
        val own = outSums.filter { it.accountId == id }.sumOf { s ->
            when (s.type) {
                "INCOME" -> s.total
                "EXPENSE", "TRANSFER" -> -s.total
                else -> 0L
            }
        }
        start + own + (transfersIn[id] ?: 0L)
    }
