package com.breckneck.debtbook.debt.main

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.breckneck.debtbook.R
import com.breckneck.debtbook.debt.DebtOrderSheet
import com.breckneck.deptbook.domain.util.HumanOrderAttribute

private val peopleOrders = listOf(
    (HumanOrderAttribute.Date to true) to R.string.people_order_newest,
    (HumanOrderAttribute.Date to false) to R.string.people_order_oldest,
    (HumanOrderAttribute.Sum to false) to R.string.people_order_highest_balance,
    (HumanOrderAttribute.Sum to true) to R.string.people_order_lowest_balance,
)

@Composable
internal fun DebtPeopleOrderSheet(
    order: Pair<HumanOrderAttribute, Boolean>,
    onDismiss: () -> Unit,
    onSelect: (Pair<HumanOrderAttribute, Boolean>, Boolean) -> Unit,
) = DebtOrderSheet(stringResource(R.string.people_order_title), order, peopleOrders, onDismiss, onSelect)
