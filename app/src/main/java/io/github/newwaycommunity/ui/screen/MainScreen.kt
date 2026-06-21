package io.github.newwaycommunity.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import coil.compose.AsyncImage
import io.github.newwaycommunity.R
import io.github.newwaycommunity.model.Game
import io.github.newwaycommunity.model.LinkObject
import io.github.newwaycommunity.util.UpdateUtil
import io.github.newwaycommunity.viewmodel.MainViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private data class LinkFieldState(val id: Int, var label: String = "", var url: String = "")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel, mediaPlayer: MediaPlayer) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val games by viewModel.games.collectAsState(initial = emptyList())
    val rawGames by viewModel.rawGames.collectAsState(initial = emptyList())
    val currentSection by viewModel.currentSection.collectAsState(initial = "games_android")
    val searchQuery by viewModel.searchQuery.collectAsState()
    val selectedSubCategory by viewModel.selectedSubCategory.collectAsState()
    val isOnline by viewModel.isOnline.collectAsState(initial = true)
    val isLoading by viewModel.isLoading.collectAsState(initial = true)

    var dropdownExpanded by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    var visibleItemsCount by remember { mutableStateOf(8) }

    val selectedThemeMode by viewModel.selectedThemeMode.collectAsState()
    val monetEnabled by viewModel.monetEnabled.collectAsState()

    val sharedPreferences = remember { context.getSharedPreferences("nwc_settings", Context.MODE_PRIVATE) }
    var isMusicPlaying by remember { mutableStateOf(sharedPreferences.getBoolean("music_enabled", false)) }
    var starsEnabled by remember { mutableStateOf(sharedPreferences.getBoolean("stars_enabled", true)) }
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    
    var serverVersionName by remember { mutableStateOf("") }
    var serverChangelog by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf("") }

    var showLoginDialog by remember { mutableStateOf(false) }
    var showFormDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var gameToDelete by remember { mutableStateOf<Game?>(null) }
    var selectedGameForEdit by remember { mutableStateOf<Game?>(null) }
    var isUserLoggedInSimulated by remember { mutableStateOf(false) }

    val isCalculatedDark = when (selectedThemeMode) {
        0 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    
    val view = LocalView.current
    SideEffect {
        val window = (context as? Activity)?.window
        if (window != null) {
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isCalculatedDark
        }
    }

    LaunchedEffect(Unit) {
        val currentVersionCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0)).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0).versionCode
            }
        } catch (_: Exception) { 1 }

        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://newwaycommunity.github.io/nwc-app/update.json")
                val connection = url.openConnection() as HttpURLConnection
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonObject = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                    if (jsonObject.optInt("versionCode", 0) > currentVersionCode) {
                        serverVersionName = jsonObject.optString("version")
                        serverChangelog = jsonObject.optJSONObject("changelog")?.optString(Locale.getDefault().language).orEmpty()
                        updateUrl = jsonObject.optJSONArray("downloadSources")?.optJSONObject(0)?.optString("url").orEmpty()
                        if (updateUrl.isNotEmpty()) showUpdateDialog = true
                    }
                }
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(lifecycleOwner, isMusicPlaying) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                if (event == Lifecycle.Event.ON_RESUME && isMusicPlaying && !mediaPlayer.isPlaying) mediaPlayer.start()
                if (event == Lifecycle.Event.ON_PAUSE && mediaPlayer.isPlaying) mediaPlayer.pause()
            } catch (_: Exception) {}
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    LaunchedEffect(currentSection, searchQuery, selectedSubCategory) {
        visibleItemsCount = 8
        if (games.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    val subCategories = remember(rawGames) {
        val list = mutableListOf("Todas")
        rawGames.forEach { if (it.category.isNotBlank() && !list.contains(it.category)) list.add(it.category) }
        list
    }

    val menuItems = remember {
        listOf(
            Triple("games_android", "Jogos Android", R.drawable.smartphone_24px),
            Triple("games_hl1", "Half Life 1", R.drawable.target_24px),
            Triple("games_hl2", "Half Life 2", R.drawable.target_24px),
            Triple("games_emulator", "Jogos de Emulador", R.drawable.sports_esports_24px),
            Triple("games_apps", "Apps Premium", R.drawable.apps_24px)
        )
    }

    if (showLoginDialog) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var emailError by remember { mutableStateOf(false) }
        var emailErrorText by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showLoginDialog = false },
            title = { Text("Acesso Administrativo", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    OutlinedTextField(
                        value = email,
                        onValueChange = { 
                            email = it
                            emailError = false
                            emailErrorText = ""
                        },
                        label = { Text("Email") },
                        singleLine = true,
                        isError = emailError,
                        supportingText = { if (emailError) Text(emailErrorText) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        label = { Text("Senha") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Go
                        ),
                        keyboardActions = KeyboardActions(
                            onGo = {
                                viewModel.signInAdmin(email, password) { success, error ->
                                    if (success) {
                                        showLoginDialog = false
                                        isUserLoggedInSimulated = true
                                        Toast.makeText(context, "Autenticado!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        emailError = true
                                        emailErrorText = error ?: "Erro ao entrar."
                                    }
                                }
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.signInAdmin(email, password) { success, error ->
                        if (success) {
                            showLoginDialog = false
                            isUserLoggedInSimulated = true
                            Toast.makeText(context, "Autenticado!", Toast.LENGTH_SHORT).show()
                        } else {
                            emailError = true
                            emailErrorText = error ?: "Erro ao entrar."
                        }
                    }
                }) { Text("Entrar") }
            },
            dismissButton = { TextButton(onClick = { showLoginDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showFormDialog) {
        var name by remember { mutableStateOf(selectedGameForEdit?.name ?: "") }
        var desc by remember { mutableStateOf(selectedGameForEdit?.desc ?: "") }
        var category by remember { mutableStateOf(selectedGameForEdit?.category ?: "") }
        var bannerUrl by remember { mutableStateOf(selectedGameForEdit?.banner ?: "") }
        var isPinned by remember { mutableStateOf(selectedGameForEdit?.pinned ?: false) }
        
        var nameError by remember { mutableStateOf(false) }

        val dynamicLinks = remember { mutableStateListOf<LinkFieldState>().apply {
            selectedGameForEdit?.linkObjects?.forEachIndexed { idx, obj ->
                add(LinkFieldState(idx, obj.label, obj.url))
            }
        }}
        var nextLinkId by remember { mutableStateOf(dynamicLinks.size) }

        AlertDialog(
            onDismissRequest = { showFormDialog = false },
            title = { Text(if (selectedGameForEdit != null) "Editar Item" else "Adicionar Item", fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Column {
                        OutlinedTextField(
                            value = name, 
                            onValueChange = { 
                                if (it.length <= 30) name = it 
                                nameError = false
                            }, 
                            label = { Text("Nome") }, 
                            singleLine = true, 
                            isError = nameError,
                            supportingText = { if (nameError) Text("Nome obrigatório.") },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(text = "${name.length} / 30", fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
                    }
                    Column {
                        OutlinedTextField(value = desc, onValueChange = { if (it.length <= 60) desc = it }, label = { Text("Descrição") }, modifier = Modifier.fillMaxWidth())
                        Text(text = "${desc.length} / 60", fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
                    }
                    Column {
                        OutlinedTextField(value = category, onValueChange = { if (it.length <= 20) category = it }, label = { Text("Categoria (Ex: Ação, RPG)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Text(text = "${category.length} / 20", fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
                    }
                    
                    Text("Links (Máx. 4)", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    dynamicLinks.forEach { link ->
                        Card(modifier = Modifier.fillMaxWidth(), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Column {
                                    OutlinedTextField(value = link.label, onValueChange = { if (it.length <= 20) { link.label = it; val idx = dynamicLinks.indexOf(link); if(idx != -1) dynamicLinks[idx] = link.copy(label = it) } }, label = { Text("Nome do link *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                    Text(text = "${link.label.length} / 20", fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 2.dp))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(value = link.url, onValueChange = { link.url = it; val idx = dynamicLinks.indexOf(link); if(idx != -1) dynamicLinks[idx] = link.copy(url = it) }, label = { Text("URL *") }, singleLine = true, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { dynamicLinks.remove(link) }) {
                                        Icon(painter = painterResource(id = R.drawable.delete_24px), contentDescription = "Remover Link", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    
                    TextButton(
                        onClick = { if (dynamicLinks.size < 4) dynamicLinks.add(LinkFieldState(nextLinkId++)) },
                        enabled = dynamicLinks.size < 4
                    ) {
                        Icon(painter = painterResource(id = R.drawable.add_24px), contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adicionar Link")
                    }

                    OutlinedTextField(
                        value = bannerUrl,
                        onValueChange = { bannerUrl = it },
                        label = { Text("URL do Banner (Imgur)") },
                        leadingIcon = { Icon(painter = painterResource(id = R.drawable.image_24px), contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = isPinned, onCheckedChange = { isPinned = it })
                        Text("Fixar no topo", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }
                    
                    val gameToSave = Game(
                        id = selectedGameForEdit?.id ?: "",
                        name = name.trim(),
                        desc = desc.trim(),
                        category = if (category.isBlank()) "Geral" else category.trim(),
                        banner = bannerUrl.trim().ifBlank { null },
                        pinned = isPinned,
                        createdAt = selectedGameForEdit?.createdAt ?: System.currentTimeMillis(),
                        linkObjects = dynamicLinks.filter { it.label.isNotBlank() && it.url.isNotBlank() }.map { LinkObject(it.label.trim(), it.url.trim()) }
                    )
                    
                    viewModel.saveGame(gameToSave) { success ->
                        if (success) {
                            Toast.makeText(context, "Salvo no Firebase!", Toast.LENGTH_SHORT).show()
                            showFormDialog = false
                        } else {
                            Toast.makeText(context, "Erro ao salvar!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }) { Text("Salvar") }
            },
            dismissButton = { TextButton(onClick = { showFormDialog = false }) { Text("Cancelar") } }
        )
    }

    if (showDeleteAllDialog) {
        val sectionName = menuItems.find { it.first == currentSection }?.second ?: "NWC"
        var confirmText by remember { mutableStateOf("") }
        val isConfirmValid = confirmText.trim() == sectionName

        AlertDialog(
            onDismissRequest = { 
                showDeleteAllDialog = false
                confirmText = ""
            },
            icon = {
                Icon(
                    painter = painterResource(id = R.drawable.delete_sweep_24px),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { 
                Text(
                    text = "Apagar $sectionName", 
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                ) 
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Esta ação é irreversível. Para confirmar, digite o nome da seção exatamente como mostrado abaixo:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = sectionName,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                            fontSize = 16.sp
                        )
                    }

                    OutlinedTextField(
                        value = confirmText,
                        onValueChange = { confirmText = it },
                        placeholder = { Text(sectionName) },
                        singleLine = true,
                        isError = confirmText.isNotBlank() && !isConfirmValid,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = isConfirmValid,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                        disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    ),
                    onClick = {
                        viewModel.deleteAllGames { success ->
                            if (success) {
                                Toast.makeText(context, "Seção limpa com sucesso!", Toast.LENGTH_SHORT).show()
                                showDeleteAllDialog = false
                                confirmText = ""
                            } else {
                                Toast.makeText(context, "Erro ao apagar!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { 
                    Text("Deletar esta seção") 
                }
            },
            dismissButton = { 
                TextButton(
                    onClick = { 
                        showDeleteAllDialog = false
                        confirmText = ""
                    }
                ) { 
                    Text("Cancelar") 
                } 
            }
        )
    }

    if (gameToDelete != null) {
        AlertDialog(
            onDismissRequest = { gameToDelete = null },
            title = { Text("Deletar jogo?", fontWeight = FontWeight.Bold) },
            text = { Text("Tem certeza que deseja deletar '${gameToDelete?.name}'? Esta ação é irreversível.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        viewModel.deleteGame(gameToDelete!!.id) { success ->
                            if (success) Toast.makeText(context, "Deletado!", Toast.LENGTH_SHORT).show()
                            gameToDelete = null
                        }
                    }
                ) { Text("Deletar") }
            },
            dismissButton = { TextButton(onClick = { gameToDelete = null }) { Text("Cancelar") } }
        )
    }

    if (showUpdateDialog) {
        var isDownloading by remember { mutableStateOf(false) }
        var progress by remember { mutableStateOf(0f) }

        AlertDialog(
            onDismissRequest = { if (!isDownloading) showUpdateDialog = false },
            title = { Text("Nova Atualização disponível!", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Versão: $serverVersionName")
                    if (serverChangelog.isNotEmpty()) {
                        Text("Novidades:", fontWeight = FontWeight.Bold)
                        Text(serverChangelog)
                    }
                    if (isDownloading) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Text(
                            text = "Baixando: ${(progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isDownloading,
                    onClick = {
                        isDownloading = true
                        scope.launch {
                            val apkFile = UpdateUtil.downloadApk(context, updateUrl) { prg -> progress = prg }
                            isDownloading = false
                            if (apkFile != null) {
                                UpdateUtil.installApk(context, apkFile)
                                showUpdateDialog = false
                            } else {
                                Toast.makeText(context, "Erro no download!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) { Text("Atualizar") }
            },
            dismissButton = {
                if (!isDownloading) {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("Mais tarde") }
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isCalculatedDark && starsEnabled) { StarsEffectComponent() }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    modifier = Modifier.width(300.dp),
                    drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom)
                ) {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("New Way Community", modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp), fontSize = 20.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(12.dp))
                        menuItems.forEach { (route, label, iconRes) ->
                            NavigationDrawerItem(
                                label = { Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                                icon = { Icon(painterResource(iconRes), contentDescription = label) },
                                selected = currentSection == route,
                                onClick = { viewModel.loadSection(route); scope.launch { drawerState.close() } },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Tema", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = Modifier.height(38.dp)) {
                                Row(modifier = Modifier.padding(2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    val modes = listOf(R.drawable.light_mode_24px, R.drawable.brightness_auto_24px, R.drawable.dark_mode_24px)
                                    modes.forEachIndexed { index, iconRes ->
                                        val isSelected = selectedThemeMode == index
                                        Surface(shape = CircleShape, color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent, modifier = Modifier.size(34.dp).clickable { viewModel.setThemeMode(index) }) {
                                            Box(contentAlignment = Alignment.Center) { Icon(painter = painterResource(id = iconRes), contentDescription = null, modifier = Modifier.size(18.dp), tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant) }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).clickable { starsEnabled = !starsEnabled; sharedPreferences.edit().putBoolean("stars_enabled", starsEnabled).apply() }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Efeito de estrelas", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Switch(checked = starsEnabled, onCheckedChange = { starsEnabled = it; sharedPreferences.edit().putBoolean("stars_enabled", it).apply() }, modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f))
                        }

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).clickable { viewModel.setMonetEnabled(!monetEnabled) }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Cores dinâmicas", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Switch(checked = monetEnabled, onCheckedChange = { viewModel.setMonetEnabled(it) }, modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f))
                            }
                        }
                    }
                }
            }
        ) {
            Scaffold(
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                        title = { Text(menuItems.find { it.first == currentSection }?.second ?: "NWC", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                        navigationIcon = { IconButton(onClick = { scope.launch { drawerState.open() } }) { Icon(painterResource(R.drawable.menu_24px), "Menu") } },
                        actions = {
                            IconButton(onClick = { isMusicPlaying = !isMusicPlaying; sharedPreferences.edit().putBoolean("music_enabled", isMusicPlaying).apply(); try { if (isMusicPlaying) mediaPlayer.start() else mediaPlayer.pause() } catch (_: Exception) {} }) { Icon(painterResource(if (isMusicPlaying) R.drawable.music_note_24px else R.drawable.music_off_24px), "Música") }
                            IconButton(onClick = { try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/W5DUnEUgtj"))) } catch (_: Exception) {} }) { Icon(painterResource(R.drawable.discord_24px), "Discord") }
                            
                            IconButton(onClick = {
                                if (isUserLoggedInSimulated) {
                                    isUserLoggedInSimulated = false
                                    Toast.makeText(context, "Log-out efetuado!", Toast.LENGTH_SHORT).show()
                                } else {
                                    showLoginDialog = true
                                }
                            }) {
                                Icon(
                                    painter = painterResource(id = if (isUserLoggedInSimulated) R.drawable.logout_24px else R.drawable.admin_panel_settings_24px),
                                    contentDescription = "Admin"
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.75f), titleContentColor = MaterialTheme.colorScheme.onSurface)
                    )
                },
                floatingActionButton = {
                    if (isUserLoggedInSimulated) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FloatingActionButton(onClick = { showDeleteAllDialog = true }, containerColor = MaterialTheme.colorScheme.errorContainer) { Icon(painterResource(R.drawable.delete_sweep_24px), "Limpar") }
                            FloatingActionButton(onClick = { selectedGameForEdit = null; showFormDialog = true }, containerColor = MaterialTheme.colorScheme.primaryContainer) { Icon(painterResource(R.drawable.add_24px), "Adicionar") }
                        }
                    }
                }
            ) { scaffoldPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)).pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }) {
                    if (isOnline && !isLoading) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                                label = { Text("Pesquisar...") },
                                leadingIcon = { Icon(painterResource(R.drawable.search_24px), null) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { focusManager.clearFocus() })
                            )
                            
                            ExposedDropdownMenuBox(
                                expanded = dropdownExpanded,
                                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                                modifier = Modifier.width(140.dp).fillMaxHeight()
                            ) {
                                OutlinedTextField(
                                    value = selectedSubCategory,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Filtro") },
                                    trailingIcon = { 
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) 
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxSize()
                                        .pointerInput(Unit) { detectTapGestures(onTap = { dropdownExpanded = !dropdownExpanded }) }
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = dropdownExpanded,
                                    onDismissRequest = { dropdownExpanded = false },
                                    modifier = Modifier.heightIn(max = 250.dp)
                                ) {
                                    subCategories.forEach { category ->
                                        DropdownMenuItem(
                                            text = { Text(category, fontSize = 14.sp) },
                                            onClick = { 
                                                viewModel.setSubCategory(category)
                                                dropdownExpanded = false 
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (!isOnline) {
                            Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(painter = painterResource(id = R.drawable.wifi_off_24px), contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(text = "Sem conexão", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(text = "Verifique sua internet.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            }
                        } else if (isLoading && games.isEmpty()) {
                            LazyVerticalGrid(columns = GridCells.Adaptive(320.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) { items(8) { ShimmerGameCardItem() } }
                        } else if (games.isEmpty()) {
                            Text(text = "Nenhum item encontrado.", modifier = Modifier.align(Alignment.Center).padding(32.dp), fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        } else {
                            val displayGames = if (isUserLoggedInSimulated) {
                                games
                            } else {
                                games.filter { !it.name.equals("NEW WAY COMMUNITY APP", ignoreCase = true) }
                            }
                            
                            val limitedGames = displayGames.take(visibleItemsCount)
                            LazyVerticalGrid(state = gridState, columns = GridCells.Adaptive(320.dp), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                                items(limitedGames, key = { it.id }) { game ->
                                    GameCard(
                                        game = game, 
                                        isAdminMode = isUserLoggedInSimulated,
                                        onLinkClick = { url -> context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }, 
                                        onEditClick = { selectedGameForEdit = game; showFormDialog = true }, 
                                        onDeleteClick = { gameToDelete = game }
                                    )
                                }
                                if (displayGames.size > visibleItemsCount) {
                                    item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                                            Button(onClick = { visibleItemsCount += 8 }) { Text("Mostrar Mais") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
