package coop.macambira.leitedia

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.time.LocalDate
import java.util.Locale

object SummaryExporter {
    fun share(context: Context, user: UserProfile, entries: List<MilkEntry>, start: LocalDate, end: LocalDate) {
        require(!end.isBefore(start)) { "O fim do período deve ser igual ou posterior ao início." }
        require(!end.isAfter(start.plusDays(365))) { "O período máximo do resumo é de 366 dias." }
        val filtered = entries.filter { entry ->
            runCatching { LocalDate.parse(entry.entryDate) }.getOrNull()?.let { !it.isBefore(start) && !it.isAfter(end) } == true
        }
        fun csv(value: String) = "\"${value.replace("\"", "\"\"")}\""
        fun number(value: Double) = String.format(Locale.US, "%.2f", value)
        val rows = filtered.groupBy { it.entryDate to it.supplier }.toSortedMap(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })
        val content = buildString {
            appendLine("Data,Produtor,Manha_L,Tarde_L,Total_L,Observacoes")
            rows.forEach { (key, values) ->
                val morning = values.filter { it.shift.startsWith("Manh", ignoreCase = true) }.sumOf { it.liters }
                val afternoon = values.filter { it.shift.startsWith("Tarde", ignoreCase = true) }.sumOf { it.liters }
                val notes = values.map { it.notes }.filter { it.isNotBlank() }.distinct().joinToString("; ")
                appendLine(listOf(csv(key.first), csv(key.second), number(morning), number(afternoon), number(morning + afternoon), csv(notes)).joinToString(","))
            }
        }
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(directory, "LeiteDia_Resumo_${user.loginId}_${start}_${end}.csv").apply { writeText(content, Charsets.UTF_8) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Compartilhar resumo do período"))
    }
}
