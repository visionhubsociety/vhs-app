package io.github.newwaycommunity.ui.screen

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.newwaycommunity.R
import io.github.newwaycommunity.model.Game

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

    val brush = Brush.linearGradient(
        colors = listOf(
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
        ),
        start = Offset(10f, 10f),
        end = Offset(translateAnim, translateAnim)
    )

    background(brush)
}

@Composable
fun ShimmerGameCardItem() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f))
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).shimmerModifier())
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
fun GameCard(
    game: Game,
    isAdminMode: Boolean,
    onLinkClick: (String) -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val cardBorder = if (game.pinned) {
        BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = cardBorder,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.3f)) // DEĞİŞİKLİK: elevation parametresi silindi, buglı çizgi temizlendi.
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                if (!game.banner.isNullOrEmpty()) {
                    AsyncImage(model = game.banner, contentDescription = game.name, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(Brush.linearGradient(colors = listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.tertiaryContainer))))
                }
                if (isAdminMode) {
                    Row(modifier = Modifier.align(Alignment.TopStart).padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilledIconButton(onClick = onEditClick, modifier = Modifier.size(32.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)) { Icon(painter = painterResource(id = R.drawable.edit_24px), contentDescription = "Editar", modifier = Modifier.size(16.dp)) }
                        FilledIconButton(onClick = onDeleteClick, modifier = Modifier.size(32.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)) { Icon(painter = painterResource(id = R.drawable.delete_24px), contentDescription = "Deletar", modifier = Modifier.size(16.dp)) }
                    }
                }
                if (game.pinned) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(32.dp)) { Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(painter = painterResource(id = R.drawable.keep_24px), contentDescription = "Fixar", modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary) } }
                }
            }
            Column(modifier = Modifier.padding(18.dp)) {
                Surface(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), shape = RoundedCornerShape(999.dp), modifier = Modifier.padding(bottom = 8.dp)) { Text(text = game.category.uppercase(), color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)) }
                Text(text = game.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (game.desc.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(text = game.desc.replace("\n", " ").replace("\r", " "), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = if (game.createdAt != 0L) Modifier else Modifier.size(0.dp)) {
                    Icon(painter = painterResource(id = R.drawable.calendar_today_24px), contentDescription = null, modifier = Modifier.size(13.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    Text(text = java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(game.createdAt)), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
                if (game.linkObjects.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    FlowRow(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        game.linkObjects.forEach { link -> key(link.url) { FilledTonalButton(onClick = { onLinkClick(link.url) }, shape = CircleShape, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) { Text(text = link.label, fontSize = 13.sp, fontWeight = FontWeight.Bold) } } }
                    }
                }
            }
        }
    }
}
