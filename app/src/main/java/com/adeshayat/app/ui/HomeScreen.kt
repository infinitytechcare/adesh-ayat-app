package com.adeshayat.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adeshayat.app.PlayerViewModel
import com.adeshayat.app.REPEAT_TIMES
import com.adeshayat.app.data.Verse
import com.adeshayat.app.data.bn
import com.adeshayat.app.data.toBengaliDigits
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val HEADER_ITEMS = 2 // হেডার + বিষয়ের চিপস

@Composable
fun HomeScreen(vm: PlayerViewModel) {
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.message) {
        val msg = vm.message
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.clearMessage()
        }
    }

    // যে আয়াত বাজছে সেটি স্ক্রিনে না থাকলে সেখানে স্ক্রল করে নিয়ে যায়
    val currentKey = vm.currentVerse?.key
    LaunchedEffect(currentKey) {
        if (currentKey == null) return@LaunchedEffect
        val idx = vm.visible.indexOfFirst { it.key == currentKey }
        if (idx < 0) return@LaunchedEffect
        val target = idx + HEADER_ITEMS
        val visibleIdx = listState.layoutInfo.visibleItemsInfo.map { it.index }
        val fullyShown = visibleIdx.size > 2 && target > visibleIdx.first() && target < visibleIdx.last()
        if (!fullyShown) listState.animateScrollToItem(target)
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = { PlayerBar(vm) },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 16.dp),
        ) {
            item(key = "header") { Header(vm) }
            item(key = "chips") { CategoryRow(vm) }
            itemsIndexed(vm.visible, key = { _, v -> v.key }) { index, verse ->
                val prevCat = vm.visible.getOrNull(index - 1)?.categoryId
                Column {
                    if (prevCat != verse.categoryId) {
                        SectionTitle(verse.categoryId.bn() + ". " + vm.data.categoryTitle(verse.categoryId))
                    }
                    VerseCard(index = index, verse = verse, vm = vm)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Header

@Composable
private fun Header(vm: PlayerViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
            .background(Brush.verticalGradient(listOf(Emerald900, Emerald700)))
    ) {
        IslamicPattern(Modifier.matchParentSize())
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
                fontFamily = Amiri,
                fontSize = 26.sp,
                color = GoldLight,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "আদেশ ও নিষেধের আয়াত",
                fontFamily = HindSiliguri,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = Color.White,
            )
            Text(
                text = "শায়খ মিশারী রাশিদ আল-আফাসির তেলাওয়াত",
                fontFamily = HindSiliguri,
                fontSize = 14.sp,
                color = GoldLight.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("${vm.data.verses.size.bn()} আয়াত")
                StatPill("${vm.data.categories.size.bn()} বিষয়")
                StatPill("${vm.downloaded.size.bn()} অফলাইনে")
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PillButton(
                    text = "সব প্লে করুন",
                    icon = { Icon(Icons.Rounded.PlayArrow, null, tint = Emerald900, modifier = Modifier.size(20.dp)) },
                    background = Gold,
                    textColor = Emerald900,
                    onClick = { vm.playAll() },
                )
                val bulk = vm.bulkProgress
                PillButton(
                    text = if (bulk == null) "সব অফলাইনে সেভ" else "${bulk.bn()}/${vm.data.verses.size.bn()} · থামান",
                    icon = {
                        if (bulk == null) {
                            Icon(Icons.Rounded.DownloadForOffline, null, tint = GoldLight, modifier = Modifier.size(20.dp))
                        } else {
                            CircularProgressIndicator(
                                color = GoldLight,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    background = Color.White.copy(alpha = 0.12f),
                    textColor = Color.White,
                    border = BorderStroke(1.dp, GoldLight.copy(alpha = 0.5f)),
                    onClick = { vm.toggleDownloadAll() },
                )
            }
        }
    }
}

@Composable
private fun StatPill(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 13.sp,
        fontFamily = HindSiliguri,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

@Composable
private fun PillButton(
    text: String,
    icon: @Composable () -> Unit,
    background: Color,
    textColor: Color,
    onClick: () -> Unit,
    border: BorderStroke? = null,
) {
    var mod = Modifier
        .clip(RoundedCornerShape(50))
        .background(background)
    if (border != null) mod = mod.border(border, RoundedCornerShape(50))
    Row(
        modifier = mod
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        icon()
        Text(text, color = textColor, fontFamily = HindSiliguri, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** হেডারের পেছনে হালকা সোনালি আট-কোণা তারার জ্যামিতিক নকশা */
@Composable
private fun IslamicPattern(modifier: Modifier) {
    Canvas(modifier) {
        val step = 64.dp.toPx()
        val r = step * 0.36f
        val color = Gold.copy(alpha = 0.10f)
        var row = 0
        var y = 0f
        while (y < size.height + step) {
            var x = if (row % 2 == 0) 0f else step / 2
            while (x < size.width + step) {
                drawStar(Offset(x, y), r, r * 0.72f, color, Stroke(width = 1.2.dp.toPx()))
                x += step
            }
            y += step * 0.75f
            row++
        }
    }
}

private fun starPath(center: Offset, outer: Float, inner: Float): Path {
    val path = Path()
    for (k in 0 until 16) {
        val angle = -PI / 2 + k * PI / 8
        val rad = if (k % 2 == 0) outer else inner
        val px = center.x + (rad * cos(angle)).toFloat()
        val py = center.y + (rad * sin(angle)).toFloat()
        if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    return path
}

private fun DrawScope.drawStar(center: Offset, outer: Float, inner: Float, color: Color, stroke: Stroke? = null) {
    val path = starPath(center, outer, inner)
    if (stroke != null) drawPath(path, color, style = stroke) else drawPath(path, color)
}

// ---------------------------------------------------------------- Categories

@Composable
private fun CategoryRow(vm: PlayerViewModel) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            CategoryChip("সব আয়াত", selected = vm.selectedCategory == null) { vm.selectCategory(null) }
        }
        items(vm.data.categories, key = { it.id }) { c ->
            CategoryChip("${c.id.bn()}. ${c.title}", selected = vm.selectedCategory == c.id) {
                vm.selectCategory(c.id)
            }
        }
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Emerald700 else Color.White, label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else Emerald800, label = "chipFg")
    Text(
        text = text,
        color = fg,
        fontFamily = HindSiliguri,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, if (selected) Emerald700 else Emerald700.copy(alpha = 0.2f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SectionTitle(text: String) {
    Row(
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(width = 4.dp, height = 18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Gold)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            fontFamily = HindSiliguri,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Emerald800,
        )
    }
}

// ---------------------------------------------------------------- Verse card

@Composable
private fun VerseCard(index: Int, verse: Verse, vm: PlayerViewModel) {
    val isCurrent = vm.currentVerse?.key == verse.key
    val shape = RoundedCornerShape(20.dp)
    val borderColor by animateColorAsState(if (isCurrent) Gold else Color.Transparent, label = "cardBorder")

    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .shadow(if (isCurrent) 10.dp else 2.dp, shape, ambientColor = Emerald900, spotColor = Emerald900)
            .clip(shape)
            .background(if (isCurrent) Color(0xFFFFFCF2) else Color.White)
            .border(2.dp, borderColor, shape)
            .clickable { if (isCurrent) vm.togglePlay() else vm.playVerse(verse) }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NumberBadge(index + 1)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "সূরা ${verse.surahName}",
                    fontFamily = HindSiliguri,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Emerald800,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "আয়াত ${verse.surah.bn()}:${verse.ayah.bn()}",
                    fontFamily = HindSiliguri,
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
            StatusIndicator(isCurrent, verse, vm)
            Spacer(Modifier.width(10.dp))
            val playingThis = isCurrent && vm.isPlaying
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) Gold else Emerald50)
                    .clickable { if (isCurrent) vm.togglePlay() else vm.playVerse(verse) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (playingThis) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = if (playingThis) "বিরতি" else "প্লে",
                    tint = if (isCurrent) Emerald900 else Emerald700,
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            text = verse.arabic,
            fontFamily = Amiri,
            fontSize = 25.sp,
            lineHeight = 48.sp,
            color = Ink,
            textAlign = TextAlign.Right,
            style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Rtl),
            modifier = Modifier.fillMaxWidth(),
        )

        AnimatedVisibility(visible = isCurrent && vm.repeatFive) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 1..REPEAT_TIMES) {
                    Box(
                        Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(if (i <= vm.playCount) Gold else Gold.copy(alpha = 0.25f))
                    )
                }
                Text(
                    text = "পুনরাবৃত্তি ${vm.playCount.bn()}/${REPEAT_TIMES.bn()}",
                    fontFamily = HindSiliguri,
                    fontSize = 12.sp,
                    color = Emerald700,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun StatusIndicator(isCurrent: Boolean, verse: Verse, vm: PlayerViewModel) {
    when {
        isCurrent && vm.isLoading -> CircularProgressIndicator(
            color = Gold,
            strokeWidth = 2.dp,
            modifier = Modifier.size(20.dp),
        )
        isCurrent && vm.isPlaying -> EqualizerBars()
        verse.key in vm.downloaded -> Icon(
            Icons.Rounded.DownloadDone,
            contentDescription = "ডাউনলোড করা আছে",
            tint = Emerald600,
            modifier = Modifier.size(20.dp),
        )
        else -> Icon(
            Icons.Rounded.CloudDownload,
            contentDescription = "অনলাইন",
            tint = Muted.copy(alpha = 0.5f),
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun NumberBadge(number: Int) {
    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(size.width / 2, size.height / 2)
            val r = size.minDimension / 2
            drawStar(c, r, r * 0.80f, Emerald700)
            drawStar(c, r * 0.86f, r * 0.69f, Gold, Stroke(width = 1.dp.toPx()))
        }
        Text(
            text = number.bn(),
            color = Color.White,
            fontFamily = HindSiliguri,
            fontWeight = FontWeight.Bold,
            fontSize = if (number >= 100) 11.sp else 13.sp,
        )
    }
}

@Composable
private fun EqualizerBars() {
    val transition = rememberInfiniteTransition(label = "eq")
    Row(
        modifier = Modifier.height(20.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 0 until 3) {
            val h by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 380 + i * 140), RepeatMode.Reverse),
                label = "bar$i",
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height(20.dp * h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Gold)
            )
        }
    }
}

// ---------------------------------------------------------------- Player bar

@Composable
private fun PlayerBar(vm: PlayerViewModel) {
    val verse = vm.currentVerse
    Surface(
        color = Color.Transparent,
        shadowElevation = 18.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(Emerald900, Emerald700)))
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (verse == null) "তেলাওয়াত শুনুন" else "সূরা ${verse.surahName} · ${verse.surah.bn()}:${verse.ayah.bn()}",
                        color = Color.White,
                        fontFamily = HindSiliguri,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = when {
                            verse == null -> "যেকোনো আয়াতে চাপুন, বা ▶ চাপলে প্রথম থেকে বাজবে"
                            vm.isLoading -> "ডাউনলোড হচ্ছে…"
                            vm.repeatFive -> "পুনরাবৃত্তি ${vm.playCount.bn()}/${REPEAT_TIMES.bn()} · তারপর পরের আয়াত"
                            else -> "শেষ হলে পরের আয়াত নিজে থেকেই বাজবে"
                        },
                        color = GoldLight,
                        fontFamily = HindSiliguri,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                RepeatToggle(vm.repeatFive) { vm.toggleRepeat() }
            }

            Spacer(Modifier.height(12.dp))
            if (vm.isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Gold,
                    trackColor = Color.White.copy(alpha = 0.18f),
                )
            } else {
                val progress = if (vm.durationMs > 0) (vm.positionMs.toFloat() / vm.durationMs).coerceIn(0f, 1f) else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    color = Gold,
                    trackColor = Color.White.copy(alpha = 0.18f),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(formatTime(vm.positionMs), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = HindSiliguri)
                Spacer(Modifier.weight(1f))
                Text(formatTime(vm.durationMs), color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = HindSiliguri)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { vm.stop() }, enabled = verse != null) {
                    Icon(Icons.Rounded.Stop, "বন্ধ", tint = Color.White.copy(alpha = if (verse != null) 0.8f else 0.3f))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { vm.previous() }, enabled = verse != null) {
                    Icon(Icons.Rounded.SkipPrevious, "আগের আয়াত", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(12.dp))
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .shadow(8.dp, CircleShape)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(GoldLight, Gold)))
                        .clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center,
                ) {
                    if (vm.isLoading) {
                        CircularProgressIndicator(color = Emerald900, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(
                            imageVector = if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (vm.isPlaying) "বিরতি" else "প্লে",
                            tint = Emerald900,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = { vm.next() }, enabled = verse != null) {
                    Icon(Icons.Rounded.SkipNext, "পরের আয়াত", tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(8.dp))
                // ভারসাম্যের জন্য ফাঁকা জায়গা (Stop বাটনের বিপরীতে)
                Spacer(Modifier.size(48.dp))
            }
        }
    }
}

@Composable
private fun RepeatToggle(active: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (active) Gold else Color.White.copy(alpha = 0.12f), label = "repeatBg")
    val fg = if (active) Emerald900 else Color.White
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .border(1.dp, Gold.copy(alpha = if (active) 1f else 0.6f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Rounded.Repeat, contentDescription = "৫ বার পুনরাবৃত্তি", tint = fg, modifier = Modifier.size(18.dp))
        Text(
            text = "${REPEAT_TIMES.bn()} বার",
            color = fg,
            fontFamily = HindSiliguri,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
        )
    }
}

private fun formatTime(ms: Int): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val m = totalSec / 60
    val s = totalSec % 60
    val ss = if (s < 10) "0$s" else "$s"
    return "${m.bn()}:${ss.toBengaliDigits()}"
}
