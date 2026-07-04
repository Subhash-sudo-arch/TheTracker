package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.viewmodel.Achievement
import com.example.viewmodel.TrackingViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

data class ConfettiParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    val size: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    viewModel: TrackingViewModel,
    modifier: Modifier = Modifier
) {
    val achievements by viewModel.achievements.collectAsState()
    val unlockedCount = achievements.count { it.isUnlocked }

    // Confetti particles state
    val particles = remember { mutableStateListOf<ConfettiParticle>() }
    val scope = rememberCoroutineScope()

    fun triggerConfetti() {
        scope.launch {
            particles.clear()
            val colors = listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Magenta, Color.Cyan, Color(0xFFFF9800))
            for (i in 0..70) {
                particles.add(
                    ConfettiParticle(
                        id = i,
                        x = Random.nextFloat() * 800f + 100f,
                        y = 1000f,
                        vx = (Random.nextFloat() - 0.5f) * 15f,
                        vy = -Random.nextFloat() * 25f - 15f,
                        color = colors.random(),
                        size = Random.nextFloat() * 12f + 8f
                    )
                )
            }

            // Confetti physics loop
            var ticks = 0
            while (ticks < 120 && particles.isNotEmpty()) {
                delay(16)
                for (i in particles.indices) {
                    val p = particles[i]
                    p.x += p.vx
                    p.y += p.vy
                    p.vy += 0.8f // gravity
                    p.vx *= 0.98f // air resistance
                }
                // Force recompose
                val temp = particles.toList()
                particles.clear()
                particles.addAll(temp.filter { it.y < 2500f })
                ticks++
            }
            particles.clear()
        }
    }

    // Automatically trigger a small celebration when an achievement unlocks
    var previousUnlockedCount by remember { mutableStateOf(-1) }
    LaunchedEffect(unlockedCount) {
        if (previousUnlockedCount != -1 && unlockedCount > previousUnlockedCount) {
            triggerConfetti()
        }
        previousUnlockedCount = unlockedCount
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Achievements", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            modifier = modifier
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Summary Progress Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { triggerConfetti() },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .background(Color(0xFFFFD700), shape = CircleShape), // Gold Award Circle
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.EmojiEvents,
                                    contentDescription = "Gold Cup",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = "Unlocked Badges",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "$unlockedCount of ${achievements.size} completed",
                                    fontWeight = FontWeight.ExtraBold,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = "Tap here or any badge to celebrate! 🎉",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "Badges List",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                items(achievements) { ach ->
                    AchievementCard(achievement = ach, onClick = {
                        if (ach.isUnlocked) {
                            triggerConfetti()
                        }
                    })
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }

        // Particle Overlay Canvas
        if (particles.isNotEmpty()) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                for (p in particles) {
                    drawRect(
                        color = p.color,
                        topLeft = Offset(p.x, p.y),
                        size = androidx.compose.ui.geometry.Size(p.size, p.size)
                    )
                }
            }
        }
    }
}

@Composable
fun AchievementCard(
    achievement: Achievement,
    onClick: () -> Unit
) {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                scope.launch {
                    scale.animateTo(0.95f, animationSpec = spring())
                    scale.animateTo(1f, animationSpec = spring())
                }
                onClick()
            }
            .background(Color.Transparent),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) {
                MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
            } else {
                MaterialTheme.colorScheme.surfaceColorAtElevation(0.5.dp)
            }
        ),
        border = if (achievement.isUnlocked) {
            CardDefaults.outlinedCardBorder().copy(
                width = 1.5.dp,
                brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFFFD700)) // Gold border
            )
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Emoji circle
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(
                        if (achievement.isUnlocked) {
                            Color(0xFFFFD700).copy(alpha = 0.15f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = achievement.emoji,
                    fontSize = 24.sp,
                    color = if (achievement.isUnlocked) Color.Unspecified else Color.Gray
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (achievement.isUnlocked) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        }
                    )
                    if (achievement.isUnlocked) {
                        Text(
                            text = "Unlocked 🏆",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFFE6B800)
                        )
                    }
                }
                Text(
                    text = achievement.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { achievement.progress },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp),
                        strokeCap = StrokeCap.Round,
                        color = if (achievement.isUnlocked) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = achievement.progressText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
