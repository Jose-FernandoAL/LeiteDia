package coop.macambira.leitedia

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coop.macambira.leitedia.ui.theme.LeiteDiaTheme
import java.security.SecureRandom
import java.time.LocalDate
import java.io.IOException
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LeiteDiaTheme { Scaffold { LeiteDiaApp(Modifier.padding(it)) } } }
    }
}

@Composable
private fun LeiteDiaApp(modifier: Modifier) {
    val client = remember { SupabaseClient() }
    val appContext = LocalContext.current.applicationContext
    val offline = remember { OfflineStore(appContext) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var license by remember { mutableStateOf<LicenseStatus?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (profile == null) LoginScreen(modifier, loading, error) { id, password ->
        loading = true; error = null
        Thread { runCatching { client.login(id, password) to client.getLicenseStatus() }.onSuccess { profile = it.first; license = it.second }.onFailure { error = it.message }; loading = false }.start()
    } else if (license?.active == false) TrialExpiredScreen(modifier, license!!, { client.logout(); profile = null; license = null })
    else if (profile!!.role == "admin") AdminScreen(
        modifier, profile!!,
        loadUsers = { done -> Thread { val r = runCatching { client.listUsers() }; done(r.getOrNull(), r.exceptionOrNull()?.message) }.start() },
        loadEntries = { id, done -> Thread { val r = runCatching { client.listEntries(id) }; done(r.getOrNull(), r.exceptionOrNull()?.message) }.start() },
        createUser = { id, name, pass, done -> Thread { done(runCatching { client.createUser(id, name, pass) }.exceptionOrNull()?.message) }.start() },
        updateUser = { id, login, name, active, pass, done -> Thread { done(runCatching { client.updateUser(id, login, name, active, pass) }.exceptionOrNull()?.message) }.start() },
        onLogout = { client.logout(); profile = null }
    ) else MilkEntryScreen(
        modifier, profile!!,
        save = { input, done -> Thread {
            val failure = runCatching { client.saveMilkEntry(profile!!, input) }.exceptionOrNull()
            if (failure == null) done(null)
            else if (failure.isNetworkFailure()) { offline.queue(profile!!.id, input); done("OFFLINE_SAVED") }
            else done(failure.message)
        }.start() },
        loadProducers = { done -> Thread {
            val r = runCatching { client.listProducers() }
            if (r.isSuccess) { offline.cacheProducers(profile!!.id, r.getOrThrow()); done(r.getOrThrow(), null) }
            else if (r.exceptionOrNull()?.isNetworkFailure() == true) done(offline.cachedProducers(profile!!.id), "Modo offline: usando produtores salvos neste celular.")
            else done(null, r.exceptionOrNull()?.message)
        }.start() },
        createProducer = { name, document, phone, community, done -> Thread { done(runCatching { client.createProducer(name, document, phone, community) }.exceptionOrNull()?.message) }.start() },
        updateProducer = { id, name, document, phone, community, active, done -> Thread { done(runCatching { client.updateProducer(id, name, document, phone, community, active) }.exceptionOrNull()?.message) }.start() },
        pendingCount = { offline.pendingCount(profile!!.id) },
        syncPending = { done -> Thread {
            var sent = 0
            var failure: Throwable? = null
            for (pending in offline.pending(profile!!.id)) {
                val result = runCatching { client.saveMilkEntry(profile!!, pending.input) }
                if (result.isSuccess) { offline.remove(pending.input.clientEntryId); sent++ }
                else { failure = result.exceptionOrNull(); break }
            }
            done(offline.pendingCount(profile!!.id), sent, failure?.takeUnless { it.isNetworkFailure() }?.message)
        }.start() },
        loadEntries = { done -> Thread { val r = runCatching { client.listEntries(profile!!.id) }; done(r.getOrNull(), r.exceptionOrNull()?.message) }.start() },
        logout = { client.logout(); profile = null }
    )
}

private fun Throwable.isNetworkFailure(): Boolean = generateSequence(this as Throwable?) { it.cause }
    .any { it is IOException }

@Composable
private fun AdminScreen(
    modifier: Modifier, admin: UserProfile,
    loadUsers: (((List<UserProfile>?, String?) -> Unit) -> Unit),
    loadEntries: (String, (List<MilkEntry>?, String?) -> Unit) -> Unit,
    createUser: (String, String, String, (String?) -> Unit) -> Unit,
    updateUser: (String, String, String, Boolean, String?, (String?) -> Unit) -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var users by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    var selected by remember { mutableStateOf<UserProfile?>(null) }
    var entries by remember { mutableStateOf<List<MilkEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreate by remember { mutableStateOf(false) }
    var showEdit by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }; var login by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var active by remember { mutableStateOf(true) }; var saving by remember { mutableStateOf(false) }
    var credentials by remember { mutableStateOf<String?>(null) }
    var startDate by remember { mutableStateOf(LocalDate.now().minusDays(7).toString()) }

    fun refreshUsers() { loading = true; loadUsers { value, message -> users = value.orEmpty(); error = message; loading = false } }
    fun openUser(user: UserProfile) { selected = user; loading = true; loadEntries(user.id) { value, message -> entries = value.orEmpty(); error = message; loading = false } }
    LaunchedEffect(Unit) { refreshUsers() }

    Column(modifier.fillMaxSize().padding(18.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column { Text("Administração", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(admin.fullName) }
            TextButton(onClick = onLogout) { Text("Sair") }
        }
        Spacer(Modifier.height(12.dp))
        if (selected == null) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Text("Usuários da cooperativa", fontWeight = FontWeight.Bold)
                TextButton(onClick = { refreshUsers() }) { Text("Atualizar") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                users.forEach { user -> ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable { openUser(user) }) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) { Column { Text(user.fullName, fontWeight = FontWeight.Bold); Text("ID: ${user.loginId}") }; Text(if (!user.active) "Desativado" else if (user.role == "admin") "Administrador" else "Ativo", color = if (user.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
                } }
            }
            credentials?.let { Text(it, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)) }
            Button(onClick = { val r = SecureRandom(); name = ""; login = "USR%06d".format(r.nextInt(1_000_000)); password = "L%07d".format(r.nextInt(10_000_000)); showCreate = true }, Modifier.fillMaxWidth()) { Text("Cadastrar novo usuário") }
        } else {
            val user = selected!!
            TextButton(onClick = { selected = null; entries = emptyList(); error = null }) { Text("← Voltar aos usuários") }
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column { Text(user.fullName, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Text("ID: ${user.loginId} • ${if (user.active) "Ativo" else "Desativado"}") }
                TextButton(onClick = { name = user.fullName; login = user.loginId; password = ""; active = user.active; showEdit = true }) { Text("Gerenciar") }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(16.dp), Arrangement.SpaceBetween) { Text("${entries.size} registros"); Text("Total: %.2f L".format(entries.sumOf { it.liters }), fontWeight = FontWeight.Bold) } }
            Spacer(Modifier.height(8.dp))
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { entries.forEach { entry -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.SpaceBetween) { Column { Text(entry.entryDate, fontWeight = FontWeight.Bold); Text("${entry.supplier} • ${entry.shift}") }; Text("%.2f L".format(entry.liters), fontWeight = FontWeight.Bold) }
            } }; if (!loading && entries.isEmpty()) Text("Nenhuma entrada registrada.") }
            OutlinedTextField(startDate, { startDate = it }, label = { Text("Início do período (AAAA-MM-DD)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            val parsed = runCatching { LocalDate.parse(startDate) }.getOrNull()
            Text("A planilha incluirá 8 dias${parsed?.let { ": ${it} até ${it.plusDays(7)}" } ?: "."}")
            Button(onClick = { parsed?.let { SpreadsheetExporter.share(context, user, entries, it) } ?: run { error = "Informe uma data válida no formato AAAA-MM-DD." } }, enabled = parsed != null, modifier = Modifier.fillMaxWidth()) { Text("Exportar planilha de 8 dias") }
        }
    }

    if (showCreate) UserDialog("Novo usuário", name, login, password, true, { name = it }, { login = it.uppercase() }, { password = it }, {}, saving, {
        saving = true; createUser(login, name, password) { message -> saving = false; if (message == null) { credentials = "Criado: $login | Senha: $password"; showCreate = false; refreshUsers() } else error = message }
    }, { showCreate = false })
    if (showEdit && selected != null) UserDialog("Gerenciar usuário", name, login, password, active, { name = it }, { login = it.uppercase() }, { password = it }, { active = it }, saving, {
        saving = true; updateUser(selected!!.id, login, name, active, password.ifBlank { null }) { message -> saving = false; if (message == null) { val updated = selected!!.copy(loginId = login, fullName = name, active = active); selected = updated; showEdit = false; refreshUsers() } else error = message }
    }, { showEdit = false }, editing = true)
}

@Composable
private fun ProducerDialog(editing: Boolean, name: String, document: String, phone: String, community: String, active: Boolean,
    setName: (String) -> Unit, setDocument: (String) -> Unit, setPhone: (String) -> Unit, setCommunity: (String) -> Unit,
    setActive: (Boolean) -> Unit, saving: Boolean, save: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = { if (!saving) dismiss() }, title = { Text(if (editing) "Gerenciar produtor" else "Novo produtor") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) {
            OutlinedTextField(name, setName, label = { Text("Nome completo *") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(6.dp))
            OutlinedTextField(document, setDocument, label = { Text("CPF ou CNPJ") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(6.dp))
            OutlinedTextField(phone, setPhone, label = { Text("Telefone") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(6.dp))
            OutlinedTextField(community, setCommunity, label = { Text("Comunidade ou endereço") }, modifier = Modifier.fillMaxWidth())
            if (editing) Row(verticalAlignment = Alignment.CenterVertically) { Switch(active, setActive); Spacer(Modifier.width(8.dp)); Text(if (active) "Produtor ativo" else "Produtor desativado") }
        } }, confirmButton = { Button(onClick = save, enabled = !saving && name.trim().length >= 3) { Text(if (saving) "Salvando..." else "Salvar") } },
        dismissButton = { TextButton(onClick = dismiss, enabled = !saving) { Text("Cancelar") } })
}

@Composable
private fun UserDialog(title: String, name: String, login: String, password: String, active: Boolean, setName: (String) -> Unit, setLogin: (String) -> Unit, setPassword: (String) -> Unit, setActive: (Boolean) -> Unit, saving: Boolean, save: () -> Unit, dismiss: () -> Unit, editing: Boolean = false) {
    AlertDialog(onDismissRequest = { if (!saving) dismiss() }, title = { Text(title) }, text = { Column {
        OutlinedTextField(name, setName, label = { Text("Nome completo") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(6.dp))
        OutlinedTextField(login, setLogin, label = { Text("ID de acesso") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(6.dp))
        OutlinedTextField(password, setPassword, label = { Text(if (editing) "Nova senha (opcional)" else "Senha inicial") }, modifier = Modifier.fillMaxWidth())
        if (editing) Row(verticalAlignment = Alignment.CenterVertically) { Switch(active, setActive); Spacer(Modifier.width(8.dp)); Text(if (active) "Conta ativa" else "Conta desativada") }
    } }, confirmButton = { Button(onClick = save, enabled = !saving && name.length >= 3 && login.length >= 3 && (editing || password.length >= 6) && (password.isBlank() || password.length >= 6)) { Text(if (saving) "Salvando..." else "Salvar") } }, dismissButton = { TextButton(onClick = dismiss, enabled = !saving) { Text("Cancelar") } })
}

@Composable
private fun LoginScreen(modifier: Modifier, loading: Boolean, error: String?, login: (String, String) -> Unit) {
    val context = LocalContext.current
    var id by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("LeiteDia", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold); Text("Cooperativa Macambira", style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(28.dp))
        OutlinedTextField(id, { id = it }, label = { Text("ID do usuário") }, modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Senha") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), singleLine = true); Spacer(Modifier.height(18.dp))
        Button(onClick = { login(id, password) }, enabled = !loading && id.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (loading) "Conectando..." else "Entrar") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = {
            val text = Uri.encode("Olá! Gostaria de solicitar um acesso de teste de 7 dias ao LeiteDia para minha cooperativa.")
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/5587999190815?text=$text")))
        }, modifier = Modifier.fillMaxWidth()) { Text("Solicitar acesso de teste") }
        Spacer(Modifier.height(12.dp)); Text("Versão 1.6 • teste gratuito", modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun TrialExpiredScreen(modifier: Modifier, license: LicenseStatus, logout: () -> Unit) {
    val context = LocalContext.current
    val message = "Olá! Testei o LeiteDia e gostaria de negociar a liberação da versão completa para minha cooperativa."
    val whatsapp = "https://wa.me/5587999190815?text=${Uri.encode(message)}"
    Column(modifier.fillMaxSize().padding(28.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Período de teste encerrado", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("Seu período gratuito de 7 dias terminou. Para continuar usando o LeiteDia, exportar planilhas e liberar todos os recursos, negocie os valores pelo WhatsApp.")
        if (license.trialEndsAt.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text("Teste encerrado em: ${license.trialEndsAt.take(10)}") }
        Spacer(Modifier.height(24.dp))
        Button(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(whatsapp))) }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Negociar versão completa") }
        TextButton(onClick = logout) { Text("Sair da conta") }
    }
}

@Composable
private fun MilkEntryScreen(modifier: Modifier, profile: UserProfile, save: (MilkEntryInput, (String?) -> Unit) -> Unit,
    loadProducers: ((List<Producer>?, String?) -> Unit) -> Unit,
    createProducer: (String, String, String, String, (String?) -> Unit) -> Unit,
    updateProducer: (Long, String, String, String, String, Boolean, (String?) -> Unit) -> Unit,
    pendingCount: () -> Int,
    syncPending: ((Int, Int, String?) -> Unit) -> Unit,
    loadEntries: ((List<MilkEntry>?, String?) -> Unit) -> Unit, logout: () -> Unit) {
    val context = LocalContext.current
    var producers by remember { mutableStateOf<List<Producer>>(emptyList()) }; var selectedProducer by remember { mutableStateOf<Producer?>(null) }; var producerMenu by remember { mutableStateOf(false) }
    var liters by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }; var shift by remember { mutableStateOf("Manhã") }; var status by remember { mutableStateOf<String?>(null) }; var saving by remember { mutableStateOf(false) }
    var viewMode by remember { mutableIntStateOf(0) }; var entries by remember { mutableStateOf<List<MilkEntry>>(emptyList()) }; var historyLoading by remember { mutableStateOf(false) }; var startDate by remember { mutableStateOf(LocalDate.now().minusDays(7).toString()) }
    var pending by remember { mutableIntStateOf(pendingCount()) }; var syncing by remember { mutableStateOf(false) }
    var editingProducer by remember { mutableStateOf<Producer?>(null) }; var showProducerDialog by remember { mutableStateOf(false) }
    var producerName by remember { mutableStateOf("") }; var producerDocument by remember { mutableStateOf("") }; var producerPhone by remember { mutableStateOf("") }; var producerCommunity by remember { mutableStateOf("") }; var producerActive by remember { mutableStateOf(true) }
    fun refreshHistory() { historyLoading = true; loadEntries { value, message -> entries = value.orEmpty(); status = message; historyLoading = false } }
    fun refreshProducers() { loadProducers { value, message -> producers = value.orEmpty(); selectedProducer = selectedProducer?.takeIf { selected -> value.orEmpty().any { it.id == selected.id && it.active } } ?: value?.firstOrNull { it.active }; status = message } }
    fun synchronize(showMessage: Boolean = false) { if (syncing) return; syncing = true; syncPending { remaining, sent, message -> pending = remaining; syncing = false; if (showMessage) status = message ?: if (sent > 0) "$sent lançamento(s) sincronizado(s)." else if (remaining == 0) "Tudo sincronizado." else "Sem conexão. Os dados continuam salvos no celular." } }
    LaunchedEffect(Unit) {
        refreshProducers()
        while (true) { synchronize(false); delay(30_000) }
    }
    Column(modifier.fillMaxSize().padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Column { Text(listOf("Entrada de leite", "Meu histórico", "Meus produtores")[viewMode], style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold); Text(profile.fullName) }; TextButton(onClick = logout) { Text("Sair") } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(viewMode == 0, { viewMode = 0; refreshProducers() }, { Text("Entrada") }, modifier = Modifier.weight(1f))
            FilterChip(viewMode == 1, { viewMode = 1; refreshHistory() }, { Text("Histórico") }, modifier = Modifier.weight(1f))
            FilterChip(viewMode == 2, { viewMode = 2; refreshProducers() }, { Text("Produtores") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        if (pending > 0 || syncing) ElevatedCard(Modifier.fillMaxWidth().padding(bottom = 8.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(if (syncing) "Sincronizando..." else "$pending lançamento(s) pendente(s)", fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { synchronize(true) }, enabled = !syncing) { Text("Sincronizar") }
        } }
        if (viewMode == 0) Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box {
                OutlinedButton(onClick = { producerMenu = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) { Text(selectedProducer?.name ?: "Selecionar produtor") }
                DropdownMenu(expanded = producerMenu, onDismissRequest = { producerMenu = false }) {
                    producers.filter { it.active }.forEach { producer -> DropdownMenuItem(text = { Column { Text(producer.name); if (producer.community.isNotBlank()) Text(producer.community, style = MaterialTheme.typography.bodySmall) } }, onClick = { selectedProducer = producer; producerMenu = false }) }
                }
            }
            if (producers.none { it.active }) Text("Você ainda não possui produtor ativo. Cadastre um na aba Produtores.", color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp)); OutlinedTextField(liters, { liters = it }, label = { Text("Litros") }, modifier = Modifier.fillMaxWidth()); Spacer(Modifier.height(8.dp))
            Text("Turno", fontWeight = FontWeight.SemiBold); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Manhã", "Tarde").forEach { FilterChip(shift == it, { shift = it }, { Text(it) }, modifier = Modifier.weight(1f)) } }
            OutlinedTextField(notes, { notes = it }, label = { Text("Observações") }, modifier = Modifier.fillMaxWidth(), minLines = 3); Spacer(Modifier.height(14.dp))
            Button(onClick = { val producer = selectedProducer ?: return@Button; saving = true; status = null; save(MilkEntryInput(producer.id, producer.name, liters.replace(',', '.').toDouble(), shift, notes.trim())) { result -> saving = false; if (result == "OFFLINE_SAVED") { pending = pendingCount(); status = "Sem internet: lançamento salvo no celular e aguardando sincronização."; liters = ""; notes = "" } else { status = result ?: "Entrada registrada com sucesso."; if (result == null) { liters = ""; notes = "" } } } }, enabled = !saving && selectedProducer != null && liters.replace(',', '.').toDoubleOrNull()?.let { it > 0 } == true, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(if (saving) "Salvando..." else "Salvar entrada") }
            status?.let { Text(it, color = if (it.contains("sucesso") || it.contains("salvo no celular") || it.contains("sincronizado") || it.contains("Tudo sincronizado")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) }
        } else if (viewMode == 1) Column(Modifier.fillMaxSize()) {
            if (historyLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
            ElevatedCard(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.SpaceBetween) { Text("${entries.size} registros"); Text("%.2f L".format(entries.sumOf { it.liters }), fontWeight = FontWeight.Bold) } }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { entries.forEach { entry -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(Modifier.fillMaxWidth().padding(12.dp), Arrangement.SpaceBetween) { Column { Text(entry.entryDate, fontWeight = FontWeight.Bold); Text("${entry.supplier} • ${entry.shift}") }; Text("%.2f L".format(entry.liters), fontWeight = FontWeight.Bold) } } }; if (!historyLoading && entries.isEmpty()) Text("Você ainda não possui registros.") }
            OutlinedTextField(startDate, { startDate = it }, label = { Text("Início da planilha (AAAA-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            val parsed = runCatching { LocalDate.parse(startDate) }.getOrNull()
            Button(onClick = { parsed?.let { SpreadsheetExporter.share(context, profile, entries, it) } }, enabled = parsed != null && entries.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Compartilhar minha planilha de 8 dias") }
        } else Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                producers.forEach { producer -> ElevatedCard(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable {
                    editingProducer = producer; producerName = producer.name; producerDocument = producer.document; producerPhone = producer.phone; producerCommunity = producer.community; producerActive = producer.active; showProducerDialog = true
                }) { Row(Modifier.fillMaxWidth().padding(14.dp), Arrangement.SpaceBetween) { Column { Text(producer.name, fontWeight = FontWeight.Bold); Text(listOf(producer.community, producer.phone).filter { it.isNotBlank() }.joinToString(" • ").ifBlank { "Sem contato informado" }) }; Text(if (producer.active) "Ativo" else "Desativado", color = if (producer.active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) } } }
                if (producers.isEmpty()) Text("Cadastre seu primeiro produtor para começar os lançamentos.")
            }
            Button(onClick = { editingProducer = null; producerName = ""; producerDocument = ""; producerPhone = ""; producerCommunity = ""; producerActive = true; showProducerDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Cadastrar produtor") }
        }
    }
    if (showProducerDialog) ProducerDialog(editingProducer != null, producerName, producerDocument, producerPhone, producerCommunity, producerActive,
        { producerName = it }, { producerDocument = it }, { producerPhone = it }, { producerCommunity = it }, { producerActive = it }, saving,
        save = { saving = true; val done: (String?) -> Unit = { message -> saving = false; if (message == null) { showProducerDialog = false; refreshProducers() } else status = message }; editingProducer?.let { updateProducer(it.id, producerName, producerDocument, producerPhone, producerCommunity, producerActive, done) } ?: createProducer(producerName, producerDocument, producerPhone, producerCommunity, done) },
        dismiss = { showProducerDialog = false })
}
