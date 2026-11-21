package com.breckneck.deptbook.domain.usecase.Debt

import com.breckneck.deptbook.domain.model.DebtDomain
import com.breckneck.deptbook.domain.util.MoneyDisplay
import org.apache.poi.hssf.usermodel.HSSFWorkbook
import org.apache.poi.ss.util.WorkbookUtil
import java.io.OutputStream

class ExportDebtDataInExcelUseCase {
    fun execute(debtList: List<DebtDomain>, sheetName: String, currency: String, titles: List<String>, output: OutputStream) {
        HSSFWorkbook().use { workbook ->
            val sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(sheetName))
            val header = sheet.createRow(0)
            titles.forEachIndexed { index, title ->
                header.createCell(index).setCellValue(title)
                sheet.setColumnWidth(index, 30 * 256)
            }
            debtList.forEachIndexed { index, debt ->
                val row = sheet.createRow(index + 1)
                row.createCell(0).setCellValue(debt.date)
                val sign = if (debt.sum.signum() > 0) "+" else ""
                row.createCell(1).setCellValue(sign + MoneyDisplay.formatExact(debt.sum, currency))
                row.createCell(2).setCellValue(debt.info.orEmpty())
            }
            workbook.write(output)
        }
    }
}
