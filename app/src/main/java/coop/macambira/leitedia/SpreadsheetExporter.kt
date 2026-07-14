package coop.macambira.leitedia

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object SpreadsheetExporter {
    private val br = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    fun share(context: Context, user: UserProfile, entries: List<MilkEntry>, start: LocalDate) {
        val days = (0L..7L).map(start::plusDays)
        val end = days.last()
        val filtered = entries.filter {
            runCatching { LocalDate.parse(it.entryDate) }.getOrNull()?.let { date ->
                !date.isBefore(start) && !date.isAfter(end)
            } == true
        }
        val suppliers = filtered.map { it.supplier }.distinct().sorted()
        fun volume(supplier: String, date: LocalDate, shift: String) = filtered
            .filter { it.supplier == supplier && it.entryDate == date.toString() && it.shift.lowercase().startsWith(shift) }
            .sumOf { it.liters }
        fun n(value: Double) = if (value == 0.0) "" else String.format(Locale.US, "%.2f", value)
        fun x(value: String) = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

        val producerRows = (0 until maxOf(12, suppliers.size)).joinToString("") { index ->
            val supplier = suppliers.getOrNull(index)
            if (supplier == null) "<tr><td></td>${days.joinToString("") { "<td></td><td></td>" }}<td></td></tr>"
            else {
                val values = days.flatMap { listOf(volume(supplier, it, "manh"), volume(supplier, it, "tarde")) }
                "<tr><td class='left'>${x(supplier)}</td>${values.joinToString("") { "<td>${n(it)}</td>" }}<td>${n(values.sum())}</td></tr>"
            }
        }
        val dailyRows = days.joinToString("") { day ->
            val dayEntries = filtered.filter { it.entryDate == day.toString() }
            val morning = dayEntries.filter { it.shift.lowercase().startsWith("manh") }.sumOf { it.liters }
            val afternoon = dayEntries.filter { it.shift.lowercase().startsWith("tarde") }.sumOf { it.liters }
            val notes = dayEntries.map { it.notes }.filter { it.isNotBlank() }.distinct().joinToString("; ")
            "<tr><td>${day.format(br)}</td><td>${n(morning)}</td><td></td><td>${n(afternoon)}</td><td></td><td></td><td></td><td></td><td class='left'>${x(notes)}</td></tr>"
        }
        val html = """<html xmlns:x='urn:schemas-microsoft-com:office:excel'><head><meta charset='UTF-8'><style>
            body{font-family:Arial;color:#102945}.brand{text-align:center;color:#078d42;font-size:24px;font-weight:bold}.title{text-align:center;font-weight:bold}.meta{text-align:center;margin:8px}table{border-collapse:collapse;width:100%}th,td{border:1px solid #111;text-align:center;padding:4px;font-size:10px}.left{text-align:left}th{font-weight:bold}.daily{margin-top:8px}
            </style></head><body><div class='brand'>COOPANEMA</div><div class='title'>PLANILHA DE ACOMPANHAMENTO DE 8 DIAS DE ENTREGA DE LEITE</div><div class='meta'>PERÍODO: ${start.format(br)} a ${end.format(br)} | NÚCLEO: Macambira | RESPONSÁVEL: ${x(user.fullName)}</div>
            <table><tr><th></th>${days.joinToString("") { "<th colspan='2'>${it.format(DateTimeFormatter.ofPattern("dd/MM"))}</th>" }}<th>TOTAL</th></tr><tr><th>PRODUTOR</th>${days.joinToString("") { "<th>MANHÃ</th><th>TARDE</th>" }}<th></th></tr>$producerRows</table>
            <table class='daily'><tr><th>DATA</th><th>ENTRADA 1ª ORDENHA</th><th>1ª MEDIÇÃO DO TANQUE</th><th>ENTRADA 2ª ORDENHA</th><th>2ª MEDIÇÃO DO TANQUE</th><th>COLETA EMPRESA</th><th>HORAS</th><th>SALDO + OU -</th><th>OBSERVAÇÕES E SALDO ANTERIOR</th></tr>$dailyRows</table>
            <p><b>LEITE DO PRODUTOR:</b> __________ &nbsp; <b>CONTROLE DE COLETA:</b> __________ &nbsp; <b>SALDO POSITIVO:</b> __________</p><p><b>CONTROLE DE TANQUE:</b> __________ &nbsp; <b>EMPRESA:</b> __________ &nbsp; <b>SALDO NEGATIVO:</b> __________</p></body></html>"""
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "LeiteDia_${user.loginId}_${start}_${end}.xls")
        file.writeText(html, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartilhar planilha"))
    }
}
