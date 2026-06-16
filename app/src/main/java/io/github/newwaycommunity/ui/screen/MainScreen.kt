package io.github.newwaycommunity.ui.screen

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import io.github.newwaycommunity.util.UpdateUtil
import io.github.newwaycommunity.viewmodel.MainViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

fun Modifier.shimmerModifier(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "Shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslate"
    )

    val shimmerColors = listOf(
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.2f),
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
    )

    val brush = Brush.linearGradient(
        colors = shimmerColors,
        start = Offset(10f, 10f),
        end = Offset(translateAnim, translateAnim)
    )

    background(brush)
}

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
    val isUserAdmin by viewModel.isAdmin.collectAsState(initial = false)

    var dropdownExpanded by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    var visibleItemsCount by remember { mutableStateOf(8) }

    val selectedThemeMode by viewModel.selectedThemeMode.collectAsState()
    val monetEnabled by viewModel.monetEnabled.collectAsState()

    val sharedPreferences = remember { context.getSharedPreferences("nwc_settings", Context.MODE_PRIVATE) }
    var isMusicPlaying by remember { mutableStateOf(sharedPreferences.getBoolean("music_enabled", false)) }
    
    var showUpdateDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var isDownloadFinished by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0.0f) }
    
    var serverVersionName by remember { mutableStateOf("") }
    var serverChangelog by remember { mutableStateOf("") }
    var updateUrl by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val currentVersionCode = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        } catch (_: Exception) { 1 }

        withContext(Dispatchers.IO) {
            try {
                val url = URL("https://newwaycommunity.github.io/nwc-app/update.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                
                if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                    val jsonString = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(jsonString)
                    val serverVersionCode = jsonObject.optInt("versionCode", 0)
                    
                    if (serverVersionCode > currentVersionCode) {
                        serverVersionName = jsonObject.optString("version").ifBlank { "Erro" }
                        
                        val changelogObj = jsonObject.optJSONObject("changelog")
                        val systemLanguage = Locale.getDefault().language
                        
                        if (changelogObj != null) {
                            serverChangelog = if (systemLanguage == "pt") {
                                changelogObj.optString("pt").ifBlank { "Não foi possível carregar as informações." }
                            } else {
                                changelogObj.optString("en").ifBlank { "Information could not be loaded." }
                            }
                        } else {
                            serverChangelog = if (systemLanguage == "pt") {
                                "Não foi possível carregar as informações."
                            } else {
                                "Information could not be loaded."
                            }
                        }
                        
                        val sourcesArray = jsonObject.optJSONArray("downloadSources")
                        val downloadLink = if (sourcesArray != null && sourcesArray.length() > 0) {
                            sourcesArray.optJSONObject(0).optString("url", "")
                        } else { "" }
                        
                        if (downloadLink.isNotEmpty()) {
                            updateUrl = downloadLink
                            showUpdateDialog = true
                        }
                    }
                } else {
                    serverVersionName = "Erro"
                    serverChangelog = if (Locale.getDefault().language == "pt") {
                        "Não foi possível carregar as informações."
                    } else {
                        "Information could not be loaded."
                    }
                }
            } catch (_: Exception) {
                serverVersionName = "Erro"
                serverChangelog = if (Locale.getDefault().language == "pt") {
                    "Não foi Psychological carregar as informações."
                } else {
                    "Information could not be loaded."
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, isMusicPlaying) {
        val observer = LifecycleEventObserver { _, event ->
            try {
                when (event) {
                    Lifecycle.Event.ON_RESUME -> {
                        if (isMusicPlaying && !mediaPlayer.isPlaying) {
                            mediaPlayer.start()
                        }
                    }
                    Lifecycle.Event.ON_PAUSE -> {
                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.pause()
                        }
                    }
                    else -> {}
                }
            } catch (_: Exception) {}
        }
        
        lifecycleOwner.lifecycle.addObserver(observer)
        
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    LaunchedEffect(currentSection, searchQuery, selectedSubCategory) {
        visibleItemsCount = 8
        if (games.isNotEmpty()) {
            gridState.scrollToItem(0)
        }
    }

    val view = LocalView.current
    val systemInDark = isSystemInDarkTheme()
    
    val isCalculatedDark = when (selectedThemeMode) {
        0 -> false
        2 -> true
        else -> systemInDark
    }
    
    SideEffect {
        val window = (context as? Activity)?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isCalculatedDark
        }
    }

    val subCategories = remember(rawGames) {
        val list = mutableListOf("Todas")
        rawGames.forEach { game ->
            if (game.category.isNotBlank() && !list.contains(game.category)) {
                list.add(game.category)
            }
        }
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

    if (showUpdateDialog) {
        AlertDialog(
            onDismissRequest = { if (!isDownloading && !isDownloadFinished) showUpdateDialog = false },
            title = { 
                Text(
                    text = "Atualização Disponível", 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 20.sp
                ) 
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Versão: $serverVersionName", 
                        fontWeight = FontWeight.SemiBold, 
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Changelog: $serverChangelog", 
                        fontSize = 14.sp, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    if (isDownloading || isDownloadFinished) {
                        Spacer(modifier = Modifier.height(12.dp))
                        if (isDownloadFinished) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            LinearProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isDownloading && !isDownloadFinished,
                    onClick = {
                        isDownloading = true
                        scope.launch {
                            val file = UpdateUtil.downloadApk(context, updateUrl) { progress ->
                                downloadProgress = progress
                            }
                            isDownloading = false
                            if (file != null) {
                                isDownloadFinished = true
                                UpdateUtil.installApk(context, file)
                                showUpdateDialog = false
                                isDownloadFinished = false
                            }
                        }
                    }
                ) {
                    Text("Atualizar")
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isDownloading && !isDownloadFinished, 
                    onClick = { showUpdateDialog = false }
                ) {
                    Text("Depois")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(300.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Start + WindowInsetsSides.Top + WindowInsetsSides.Bottom)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 24.dp)
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "New Way Community",
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    menuItems.forEach { (route, label, iconRes) ->
                        NavigationDrawerItem(
                            label = { Text(text = label, fontSize = 14.sp, fontWeight = FontWeight.Medium) },
                            icon = { Icon(painter = painterResource(id = iconRes), contentDescription = label) },
                            selected = currentSection == route,
                            onClick = {
                                viewModel.loadSection(route)
                                scope.launch { drawerState.close() }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                unselectedContainerColor = Color.Transparent
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Tema", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            modifier = Modifier.height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(2.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                val modes = listOf(
                                    R.drawable.light_mode_24px, 
                                    R.drawable.brightness_auto_24px, 
                                    R.drawable.dark_mode_24px
                                )
                                modes.forEachIndexed { index, iconRes ->
                                    val isSelected = selectedThemeMode == index
                                    Surface(
                                        shape = CircleShape,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clickable { viewModel.setThemeMode(index) }
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                painter = painterResource(id = iconRes),
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 4.dp)
                                .clickable { viewModel.setMonetEnabled(!monetEnabled) },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Cores dinâmicas", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Switch(
                                checked = monetEnabled, 
                                onCheckedChange = { viewModel.setMonetEnabled(it) },
                                modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
                            )
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
                    title = {
                        Text(
                            text = menuItems.find { it.first == currentSection }?.second ?: "NWC",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(
                                painter = painterResource(id = R.drawable.menu_24px),
                                contentDescription = "Menu"
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            isMusicPlaying = !isMusicPlaying
                            sharedPreferences.edit().putBoolean("music_enabled", isMusicPlaying).apply()
                            try {
                                if (isMusicPlaying) {
                                    mediaPlayer.start()
                                } else {
                                    mediaPlayer.pause()
                                }
                            } catch (_: Exception) {}
                        }) {
                            Icon(
                                painter = painterResource(
                                    id = if (isMusicPlaying) R.drawable.music_note_24px else R.drawable.music_off_24px
                                ),
                                contentDescription = "Música"
                            )
                        }

                        IconButton(onClick = {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.gg/W5DUnEUgtj")))
                            } catch (_: Exception) {}
                        }) {
                            Icon(
                                painter = painterResource(id = R.drawable.discord_24px),
                                contentDescription = "Discord"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            floatingActionButton = {
                if (isUserAdmin) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingActionButton(
                            onClick = { /* Temizleme */ },
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.delete_sweep_24px),
                                contentDescription = "Limpar"
                            )
                        }

                        FloatingActionButton(
                            onClick = { /* Ekleme */ },
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.add_24px),
                                contentDescription = "Adicionar"
                            )
                        }
                    }
                }
            }
        ) { scaffoldPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(scaffoldPadding)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .background(MaterialTheme.colorScheme.background)
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { focusManager.clearFocus() })
                    }
            ) {
                if (isOnline && !isLoading) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            placeholder = { Text("Pesquisar...", fontSize = 14.sp) }, 
                            leadingIcon = { 
                                Icon(
                                    painter = painterResource(id = R.drawable.search_24px),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                ) 
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = { focusManager.clearFocus() }
                            ),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )

                        Box(modifier = Modifier.width(140.dp)) {
                            OutlinedCard(
                                onClick = { dropdownExpanded = true },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(56.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(start = 12.dp, end = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = selectedSubCategory,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    
                                    val arrowIcon = if (dropdownExpanded) R.drawable.arrow_drop_up_24px else R.drawable.arrow_drop_down_24px
                                    Icon(
                                        painter = painterResource(id = arrowIcon),
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            DropdownMenu(
                                expanded = dropdownExpanded,
                                onDismissRequest = { dropdownExpanded = false },
                                modifier = Modifier
                                    .width(140.dp)
                                    .heightIn(max = 280.dp)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            ) {
                                subCategories.forEach { category ->
                                    DropdownMenuItem(
                                        text = { 
                                            Text(
                                                text = category, 
                                                fontSize = 13.sp,
                                                fontWeight = if (selectedSubCategory == category) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            ) 
                                        },
                                        onClick = {
                                            viewModel.setSubCategory(category)
                                            dropdownExpanded = false
                                        },
                                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (!isOnline) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.wifi_off_24px),
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Sem conexão",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Verifique sua internet.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    } else if (isLoading && games.isEmpty()) {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(8) {
                                ShimmerGameCardItem()
                            }
                        }
                    } else if (games.isEmpty()) {
                        Text(
                            text = "Nenhum item encontrado.",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(32.dp),
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    } else {
                        val limitedGames = games.take(visibleItemsCount)

                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            horizontalArrangement = Arrangement.spacedBy(18.dp)
                        ) {
                            items(limitedGames, key = { it.id }) { game ->
                                GameCard(
                                    game = game,
                                    isAdminMode = isUserAdmin,
                                    onLinkClick = { url ->
                                        try {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            context.startActivity(intent)
                                        } catch (_: Exception) {}
                                    }
                                )
                            }

                            if (games.size > visibleItemsCount) {
                                item(span = { GridItemSpan(maxCurrentLineSpan) }) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 16.dp, bottom = 28.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Button(
                                            onClick = { visibleItemsCount += 8 },
                                            modifier = Modifier
                                                .wrapContentWidth()
                                                .height(48.dp),
                                            shape = RoundedCornerShape(99.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = MaterialTheme.colorScheme.primary,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            contentPadding = PaddingValues(horizontal = 32.dp)
                                        ) {
                                            Text(
                                                text = "Mostrar Mais",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold
                                            )
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

@Composable
fun ShimmerGameCardItem() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
                    .shimmerModifier()
            )
            Column(modifier = Modifier.padding(18.dp)) {
                Box(modifier = Modifier.width(80.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerModifier())
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerModifier())
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerModifier())
                Spacer(modifier = Modifier.height(6.dp))
                Box(modifier = Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerModifier())
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GameCard(game: Game, isAdminMode: Boolean, onLinkClick: (String) -> Unit) {
    val cardBorder = if (game.pinned) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(165.dp)
            ) {
                if (!game.banner.isNullOrEmpty()) {
                    AsyncImage(
                        model = game.banner,
                        contentDescription = game.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            )
                    )
                }

                if (isAdminMode) {
                    userIsAdminRow()
                }

                if (game.pinned) {
                    gamePinnedSurface()
                }
            }

            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text(
                        text = game.category.uppercase(),
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }

                Text(
                    text = game.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                if (game.desc.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = game.desc.replace("\n", " ").replace("\r", " "),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 18.sp
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.placeholderMetaDataVisibility(game.createdAt != 0L)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.calendar_today_24px),
                        contentDescription = null,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(game.createdAt)),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }

                if (game.linkObjects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        game.linkObjects.forEach { link ->
                            key(link.url) {
                                FilledTonalButton(
                                    onClick = { onLinkClick(link.url) },
                                    shape = CircleShape,
                                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(
                                        text = link.label,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BoxScope.gamePinnedSurface() {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(32.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.keep_24px),
                contentDescription = "Fixar",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun BoxScope.userIsAdminRow() {
    Row(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilledIconButton(
            onClick = { /* Düzenleme */ },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.edit_24px),
                contentDescription = "Editar",
                modifier = Modifier.size(16.dp)
            )
        }

        FilledIconButton(
            onClick = { /* Silme */ },
            modifier = Modifier.size(32.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.delete_24px),
                contentDescription = "Deletar",
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

fun Modifier.placeholderMetaDataVisibility(visible: Boolean): Modifier = if (visible) this else Modifier.size(0.dp)
