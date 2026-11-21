package com.breckneck.debtbook.debt.details

import com.breckneck.deptbook.domain.util.MoneyDisplay
import java.math.BigDecimal
import com.breckneck.deptbook.domain.util.compareTo

import android.content.Context
import android.content.Intent
import android.content.Intent.*
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.transition.Fade
import android.transition.TransitionManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.os.bundleOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import androidx.recyclerview.widget.RecyclerView
import com.breckneck.debtbook.R
import com.breckneck.debtbook.core.ui.components.ConfirmationBottomSheet
import com.breckneck.debtbook.core.ui.theme.DebtBookTheme
import com.breckneck.debtbook.debt.details.adapter.DebtDetailsAdapter
import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.usecase.Debt.ExportDebtDataInExcelUseCase
import com.breckneck.deptbook.domain.util.DebtOrderAttribute
import com.breckneck.deptbook.domain.util.Filter
import com.breckneck.deptbook.domain.util.ROTATE_DEGREE_DEBT_IMAGE_VIEW_BY_DECREASE
import com.breckneck.deptbook.domain.util.ROTATE_DEGREE_DEBT_IMAGE_VIEW_BY_INCREASE
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.chip.ChipGroup
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import com.breckneck.deptbook.domain.util.LedgerDate


@AndroidEntryPoint
class DebtDetailsFragment : Fragment() {

    private val TAG = "DebtDetailsFragment"

    private enum class DebtSheet { ACTIONS, REMOVE }

    interface OnButtonClickListener {
        fun addNewDebtFragment(idHuman: Int, currency: String, name: String)

        fun editDebt(
            debtDomain: DebtDomain, currency: String, name: String,
            hasRepayments: Boolean = false, canRemind: Boolean = true
        )

        fun deleteHuman()

        fun onBackButtonClick()

        fun onTickVibration()
    }

    var buttonClickListener: OnButtonClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        buttonClickListener = context as OnButtonClickListener
    }

    private val vm by viewModels<DebtDetailsViewModel>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?) =
        ComposeView(requireContext()).apply {
            id = R.id.debtDetailsSheets
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                DebtBookTheme {
                    DebtDetailsScreen(vm, requireActivity() as OnButtonClickListener, ::showSortDialog,
                        ::showDeleteHumanDialog, { showShareDialog(vm.humanName.value, vm.humanCurrency) })
                    DebtSheets()
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (vm.isSortDialogShown.value == true) showSortDialog()
        if (vm.isHumanDeleteDialogShown.value == true) showDeleteHumanDialog()
        if (vm.isShareDialogShown.value == true) showShareDialog(vm.humanName.value, vm.humanCurrency)
        vm.humanDeleted.observe(viewLifecycleOwner) { if (it) buttonClickListener?.deleteHuman() }
        vm.operationFailed.observe(viewLifecycleOwner) { if (it) {
            Toast.makeText(requireContext(), R.string.history_change_failed, Toast.LENGTH_LONG).show()
            vm.dismissOperationError()
        } }
        vm.visibleDebtRows.observe(viewLifecycleOwner) {
            val requested = arguments?.getInt("openDebtId", -1) ?: -1
            if (requested > 0 && vm.originalDebt(requested) != null) {
                arguments?.remove("openDebtId")
                vm.selectDebtAction(requested)
            }
        }
    }

    private fun showDeleteHumanDialog() {
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(R.layout.dialog_are_you_sure)
        vm.onHumanDeleteDialogOpen()

        val personName = vm.humanName.value
        dialog.findViewById<TextView>(R.id.dialogTitle)!!.text = if (personName.isNullOrBlank()) {
            getString(R.string.person_delete_action)
        } else {
            getString(R.string.person_delete_title, personName)
        }
        dialog.findViewById<TextView>(R.id.dialogMessage)!!.setText(R.string.person_delete_message)

        val confirm = dialog.findViewById<Button>(R.id.okButton)!!
        confirm.setText(R.string.delete)
        confirm.setOnClickListener {
            vm.deleteHuman()
            dialog.dismiss()
        }

        val cancel = dialog.findViewById<Button>(R.id.cancelButton)!!
        cancel.setText(R.string.cancel)
        cancel.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            vm.onHumanDeleteDialogClose()
        }
        dialog.setOnCancelListener {
            vm.onHumanDeleteDialogClose()
        }

        dialog.show()
    }

    @androidx.compose.runtime.Composable
    private fun DebtSheets() {
        val repaymentId by vm.repaymentDebtId.observeAsState()
        val debtId by vm.debtActionId.observeAsState()
        val history by vm.history.observeAsState()
        val selected = repaymentId ?: debtId
        val debt = history?.firstOrNull { it.id == selected }
        if (debt != null && repaymentId != null) {
            RepaymentSheet(vm, debt)
        } else if (debt != null) key(debt.id) {
            var sheet by rememberSaveable { mutableStateOf(DebtSheet.ACTIONS) }
            val rest = vm.remaining(debt)
            val currency = vm.humanCurrency.orEmpty()
            val hasRepayments = vm.hasRepayments(debt.id)
            val close = { vm.selectDebtAction(null) }
            when (sheet) {
                DebtSheet.ACTIONS -> DebtActionsSheet(
                    debt, debt.repaymentOfId?.let(vm::originalDebt), currency, rest, hasRepayments,
                    onDismiss = close,
                    onRepay = { close(); vm.openRepayment(debt) },
                    onEdit = {
                        close()
                        buttonClickListener?.editDebt(
                            debt, currency, vm.humanName.value.orEmpty(), hasRepayments,
                            canRemind = !hasRepayments || rest.signum() > 0
                        )
                    },
                    onRemove = { sheet = DebtSheet.REMOVE }
                )
                DebtSheet.REMOVE -> {
                    val repayment = debt.repaymentOfId != null
                    val action = stringResource(if (repayment) R.string.repayment_cancel else R.string.delete)
                    ConfirmationBottomSheet(
                        title = action,
                        message = if (repayment) stringResource(R.string.repayment_cancel_confirm,
                            MoneyDisplay.formatExact(debt.sum.abs(), currency), currency)
                        else stringResource(R.string.debt_delete_question),
                        confirmText = action,
                        dismissText = stringResource(R.string.cancel),
                        onConfirm = { close(); vm.deleteDebt(debt) },
                        onDismiss = { sheet = DebtSheet.ACTIONS }
                    )
                }
            }
        }
    }

    private fun showShareDialog(humanName: String?, currency: String?) {
        val dialog = BottomSheetDialog(requireContext())
        dialog.setContentView(R.layout.dialog_share)
        vm.onShareDialogOpen()

        dialog.findViewById<Button>(R.id.deleteButton)!!.setOnClickListener {
            dialog.dismiss()
            DebtStatementPreviewDialog().show(childFragmentManager, "debtStatementPreview")
        }

        dialog.findViewById<Button>(R.id.editButton)!!.setOnClickListener {
            var rootFolder = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
                    .toString()
            )
            rootFolder = File(rootFolder, "DebtBookFiles")
            val sheetTitles = arrayOf(
                getString(R.string.date),
                "${getString(R.string.sum)} ($currency)",
                getString(R.string.comment)
            )
            val excelFile = ExportDebtDataInExcelUseCase().execute(
                debtList = vm.resultedDebtList.value!!.map(::withHistoryDescription),
                sheetName = "${humanName}_debts",
                rootFolder = rootFolder,
                sheetTitles = sheetTitles
            )
            if (excelFile.exists())
                Toast.makeText(
                    requireContext(),
                    R.string.excel_folder_toast_hint,
                    Toast.LENGTH_SHORT
                ).show()
            val intent = Intent(ACTION_SEND)
            val uriPath: Uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.provider",
                excelFile
            )
            intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(uriPath, "application/vnd.ms-excel");
            intent.putExtra(EXTRA_STREAM, uriPath)
            startActivity(createChooser(intent, humanName))
        }

        dialog.findViewById<Button>(R.id.cancelButton)!!.setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnDismissListener {
            vm.onShareDialogClose()
        }
        dialog.setOnCancelListener {
            vm.onShareDialogClose()
        }

        dialog.show()
    }

    private fun withHistoryDescription(debt: DebtDomain): DebtDomain {
        val details = if (debt.repaymentOfId != null) {
            debt.repaymentOfId?.let(vm::originalDebt)?.let { requireContext().originalDebtLabel(it, vm.humanCurrency.orEmpty()) }
        } else {
            getString(R.string.debt_remaining, MoneyDisplay.formatExact(vm.remaining(debt), vm.humanCurrency.orEmpty()), vm.humanCurrency)
        }
        val description = listOfNotNull(getString(debt.historyTitle()), details, debt.info?.takeIf { it.isNotBlank() })
        return debt.copy(info = description.joinToString("; "))
    }

    private fun showSortDialog() {
        val orderDialog = BottomSheetDialog(requireContext())
        orderDialog.setContentView(R.layout.dialog_sort)
        orderDialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
        vm.onSortDialogOpen()

        var sortByIncrease = vm.debtOrder.value!!.second
        val sortImageView = orderDialog.findViewById<ImageView>(R.id.sortImageView)
        if (sortByIncrease)
            sortImageView!!.rotationY = ROTATE_DEGREE_DEBT_IMAGE_VIEW_BY_INCREASE
        else
            sortImageView!!.rotationY = ROTATE_DEGREE_DEBT_IMAGE_VIEW_BY_DECREASE

        var orderAttribute = vm.debtOrder.value!!.first
        val orderCreationDateRadioButton =
            orderDialog.findViewById<RadioButton>(R.id.orderCreationDateRadioButton)
        val orderDateRadioButton = orderDialog.findViewById<RadioButton>(R.id.orderDateRadioButton)
        val orderSumRadioButton = orderDialog.findViewById<RadioButton>(R.id.orderSumRadioButton)
        when (orderAttribute) {
            DebtOrderAttribute.CreationDate ->
                orderCreationDateRadioButton!!.isChecked = true

            DebtOrderAttribute.Date ->
                orderDateRadioButton!!.isChecked = true

            DebtOrderAttribute.Sum ->
                orderSumRadioButton!!.isChecked = true
        }

        orderDialog.findViewById<CardView>(R.id.sortButtonCard)!!.setOnClickListener {
            buttonClickListener!!.onTickVibration()
            if (sortImageView.rotationY == ROTATE_DEGREE_DEBT_IMAGE_VIEW_BY_INCREASE) {
                sortImageView.rotationY = ROTATE_DEGREE_DEBT_IMAGE_VIEW_BY_DECREASE
                sortByIncrease = false
            } else if (sortImageView.rotationY == ROTATE_DEGREE_DEBT_IMAGE_VIEW_BY_DECREASE) {
                sortImageView.rotationY = ROTATE_DEGREE_DEBT_IMAGE_VIEW_BY_INCREASE
                sortByIncrease = true
            }
        }

        var debtFilter = vm.debtFilter.value!!
        when (debtFilter) {
            Filter.ALL -> orderDialog.findViewById<RadioButton>(R.id.showAllRadioButton)!!.isChecked =
                true

            Filter.NEGATIVE -> orderDialog.findViewById<RadioButton>(R.id.showNegativeRadioButton)!!.isChecked =
                true

            Filter.POSITIVE -> orderDialog.findViewById<RadioButton>(R.id.showPositiveRadioButton)!!.isChecked =
                true
        }

        val rememberChoiceCheckBox = orderDialog.findViewById<CheckBox>(R.id.rememberChoiceCheckBox)
        orderDialog.findViewById<Button>(R.id.confirmButton)!!.setOnClickListener {

            when (orderDialog.findViewById<RadioGroup>(R.id.filterRadioGroup)!!.checkedRadioButtonId) {
                R.id.showAllRadioButton -> debtFilter = Filter.ALL
                R.id.showPositiveRadioButton -> debtFilter = Filter.POSITIVE
                R.id.showNegativeRadioButton -> debtFilter = Filter.NEGATIVE
            }

            when (orderDialog.findViewById<RadioGroup>(R.id.orderRadioGroup)!!.checkedRadioButtonId) {
                R.id.orderDateRadioButton -> {
                    orderAttribute = DebtOrderAttribute.Date
                }

                R.id.orderCreationDateRadioButton -> {
                    orderAttribute = DebtOrderAttribute.CreationDate
                }

                R.id.orderSumRadioButton -> {
                    orderAttribute = DebtOrderAttribute.Sum
                }
            }

            val order = Pair(orderAttribute, sortByIncrease)
            vm.onSetDebtSort(filter = debtFilter, order = order)

            if (rememberChoiceCheckBox!!.isChecked) {
                vm.saveDebtOrder(Pair(orderAttribute, sortByIncrease))
            }

            orderDialog.dismiss()
        }

        orderDialog.findViewById<Button>(R.id.cancelButton)!!.setOnClickListener {
            orderDialog.dismiss()
        }

        orderDialog.setOnDismissListener {
            vm.onOrderDialogClose()
        }

        orderDialog.setOnCancelListener {
            vm.onOrderDialogClose()
        }

        orderDialog.show()
    }

    private val dayChanged = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { vm.retry() }
    }

    override fun onResume() {
        super.onResume()
        vm.retry()
        ContextCompat.registerReceiver(requireContext(), dayChanged, android.content.IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED); addAction(Intent.ACTION_TIMEZONE_CHANGED); addAction(Intent.ACTION_TIME_CHANGED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        requireContext().unregisterReceiver(dayChanged)
        super.onPause()
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }
}
