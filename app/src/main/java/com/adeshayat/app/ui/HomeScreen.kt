package com.adeshayat.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import com.adeshayat.app.data.Hadith
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.adeshayat.app.CategoryStyle
import com.adeshayat.app.PlayerViewModel
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import com.adeshayat.app.REPEAT_TIMES
import com.adeshayat.app.RepeatSetting
import com.adeshayat.app.data.Verse
import com.adeshayat.app.data.bn
import com.adeshayat.app.data.toBengaliDigits
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private const val HEADER_ITEMS = 2 // হেডার + সার্চ ও বিষয়ের অংশ

@Composable
fun HomeScreen(vm: PlayerViewModel) {
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    var showSettings by remember { mutableStateOf(false) }
    if (showSettings) SettingsSheet(vm) { showSettings = false }

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
            item(key = "header") { Header(vm, onSettings = { showSettings = true }) }
            item(key = "filters") { FilterBar(vm) }
            if (vm.visible.isEmpty()) {
                item(key = "empty") { EmptyState(vm) }
            }
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
private fun Header(vm: PlayerViewModel, onSettings: () -> Unit) {
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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 36.dp),
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
                text = "তেলাওয়াত: ${vm.reciter.label}",
                fontFamily = HindSiliguri,
                fontSize = 14.sp,
                color = GoldLight.copy(alpha = 0.9f),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatPill("${vm.data.verses.size.bn()} আয়াত")
                StatPill("${(vm.data.categories.size + vm.data.themes.size).bn()} বিষয়")
                StatPill(
                    text = "${vm.offlineCount().bn()} অফলাইনে",
                    selected = vm.offlineOnly,
                    onClick = { vm.toggleOfflineOnly() },
                )
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
        IconButton(
            onClick = onSettings,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(6.dp),
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "সেটিংস", tint = GoldLight, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun StatPill(text: String, selected: Boolean = false, onClick: (() -> Unit)? = null) {
    var mod = Modifier
        .clip(RoundedCornerShape(50))
        .background(if (selected) Gold else Color.White.copy(alpha = 0.12f))
    if (onClick != null) {
        mod = mod
            .border(1.dp, GoldLight.copy(alpha = if (selected) 1f else 0.5f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
    }
    Row(
        modifier = mod.padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onClick != null) {
            Icon(
                if (selected) Icons.Rounded.FilterAlt else Icons.Rounded.DownloadDone,
                contentDescription = null,
                tint = if (selected) Emerald900 else GoldLight,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = if (selected) Emerald900 else Color.White,
            fontSize = 13.sp,
            fontFamily = HindSiliguri,
            fontWeight = FontWeight.SemiBold,
        )
    }
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
private fun FilterBar(vm: PlayerViewModel) {
    Column(Modifier.padding(top = 14.dp)) {
        SearchField(vm)
        if (vm.categoryStyle == CategoryStyle.DROPDOWN) {
            CategoryDropdown(vm)
        } else {
            CategoryChips(vm)
        }
        if (vm.isFiltered) FilterSummary(vm)
    }
}

@Composable
private fun SearchField(vm: PlayerViewModel) {
    val focus = LocalFocusManager.current
    OutlinedTextField(
        value = vm.searchQuery,
        onValueChange = { vm.updateSearch(it) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        singleLine = true,
        shape = RoundedCornerShape(50),
        textStyle = TextStyle(fontFamily = HindSiliguri, fontSize = 15.sp, color = Emerald900),
        placeholder = {
            Text(
                "বাংলা বা আরবি শব্দ, যেমন: সত্য, ধৈর্য, ২:১৫৩",
                fontFamily = HindSiliguri,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null, tint = Emerald700) },
        trailingIcon = {
            if (vm.searchQuery.isNotEmpty()) {
                IconButton(onClick = { vm.updateSearch("") }) {
                    Icon(Icons.Rounded.Close, contentDescription = "মুছুন", tint = Emerald700)
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedBorderColor = Emerald700,
            unfocusedBorderColor = Emerald700.copy(alpha = 0.2f),
            cursorColor = Emerald700,
        ),
    )
}

@Composable
private fun CategoryChips(vm: PlayerViewModel) {
    LazyRow(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
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
    if (vm.data.themes.isNotEmpty()) {
        LazyRow(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                Text(
                    "বিষয়ভিত্তিক:",
                    fontFamily = HindSiliguri,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = GoldDark,
                )
            }
            items(vm.data.themes, key = { it.id }) { t ->
                CategoryChip(t.title, selected = vm.selectedCategory == t.id, theme = true) {
                    vm.selectCategory(if (vm.selectedCategory == t.id) null else t.id)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(vm: PlayerViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val sel = vm.selectedCategory
    val label = when {
        sel == null -> "সব আয়াত (${vm.data.verses.size.bn()})"
        sel >= 100 -> "${vm.data.categoryTitle(sel)} (${vm.data.countIn(sel).bn()})"
        else -> "${sel.bn()}. ${vm.data.categoryTitle(sel)} (${vm.data.countIn(sel).bn()})"
    }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("বিষয় বেছে নিন", fontFamily = HindSiliguri) },
            textStyle = TextStyle(fontFamily = HindSiliguri, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Emerald900),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White,
                focusedBorderColor = Emerald700,
                unfocusedBorderColor = Emerald700.copy(alpha = 0.2f),
                focusedLabelColor = Emerald700,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = Color.White,
        ) {
            DropdownItem("সব আয়াত", vm.data.verses.size, sel == null) {
                vm.selectCategory(null); expanded = false
            }
            DropdownHeader("মূল বিষয়")
            vm.data.categories.forEach { c ->
                DropdownItem("${c.id.bn()}. ${c.title}", vm.data.countIn(c.id), sel == c.id) {
                    vm.selectCategory(c.id); expanded = false
                }
            }
            if (vm.data.themes.isNotEmpty()) {
                DropdownHeader("বিষয়ভিত্তিক (কিওয়ার্ড)")
                vm.data.themes.forEach { t ->
                    DropdownItem(t.title, vm.data.countIn(t.id), sel == t.id) {
                        vm.selectCategory(t.id); expanded = false
                    }
                }
            }
        }
    }
}

@Composable
private fun DropdownHeader(text: String) {
    Text(
        text,
        fontFamily = HindSiliguri,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        color = GoldDark,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun DropdownItem(text: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text,
                    fontFamily = HindSiliguri,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    color = if (selected) Emerald700 else Emerald900,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Text(count.bn(), fontFamily = HindSiliguri, fontSize = 13.sp, color = Emerald700.copy(alpha = 0.6f))
            }
        },
        onClick = onClick,
    )
}

@Composable
private fun FilterSummary(vm: PlayerViewModel) {
    val parts = buildList {
        vm.selectedCategory?.let { add(vm.data.categoryTitle(it)) }
        if (vm.searchQuery.isNotBlank()) add("“${vm.searchQuery.trim()}”")
        if (vm.offlineOnly) add("অফলাইনে")
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${vm.visible.size.bn()}টি আয়াত · ${parts.joinToString(" · ")}",
            fontFamily = HindSiliguri,
            fontSize = 13.sp,
            color = Emerald800,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            "ফিল্টার মুছুন",
            fontFamily = HindSiliguri,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = GoldDark,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { vm.clearFilters() }
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun EmptyState(vm: PlayerViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = Emerald700.copy(alpha = 0.5f), modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(10.dp))
        Text(
            text = if (vm.offlineOnly && vm.searchQuery.isBlank() && vm.selectedCategory == null)
                "এখনো কোনো আয়াত অফলাইনে সেভ হয়নি। একবার প্লে করলে বা \"সব অফলাইনে সেভ\" চাপলে এখানে দেখাবে।"
            else "এই ফিল্টারে কোনো আয়াত পাওয়া যায়নি।",
            fontFamily = HindSiliguri,
            fontSize = 15.sp,
            color = Emerald800,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CategoryChip(text: String, selected: Boolean, theme: Boolean = false, onClick: () -> Unit) {
    val accent = if (theme) GoldDark else Emerald700
    val bg by animateColorAsState(if (selected) accent else if (theme) GoldLight.copy(alpha = 0.25f) else Color.White, label = "chipBg")
    val fg by animateColorAsState(if (selected) Color.White else if (theme) Emerald900 else Emerald800, label = "chipFg")
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
            .border(1.dp, if (selected) accent else accent.copy(alpha = 0.25f), RoundedCornerShape(50))
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
                    text = if (verse.continuation.isEmpty()) "আয়াত ${verse.surah.bn()}:${verse.ayah.bn()}"
                    else "আয়াত ${verse.surah.bn()}:${verse.ayah.bn()}–${verse.lastAyah.bn()}",
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
            text = if (verse.continuation.isEmpty()) verse.arabic
            else buildString {
                append(verse.arabic).append(" \uFD3F").append(verse.ayah.arabicDigits()).append("\uFD3E")
                for (c in verse.continuation) {
                    append(' ').append(c.arabic).append(" \uFD3F").append(c.ayah.arabicDigits()).append("\uFD3E")
                }
            },
            fontFamily = Amiri,
            fontSize = 25.sp,
            lineHeight = 48.sp,
            color = Ink,
            textAlign = TextAlign.Right,
            style = androidx.compose.ui.text.TextStyle(textDirection = TextDirection.Rtl),
            modifier = Modifier.fillMaxWidth(),
        )

        if (verse.continuation.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "ۙ  আয়াতের শেষে না থামার চিহ্ন — তাই পরের আয়াতসহ দেখানো ও বাজানো হচ্ছে",
                fontFamily = HindSiliguri,
                fontSize = 12.sp,
                color = GoldDark,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(GoldLight.copy(alpha = 0.2f))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }

        if (vm.showBangla && verse.translation.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Emerald700.copy(alpha = 0.12f))
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (verse.continuation.isEmpty()) verse.translation
                else buildString {
                    append("(${verse.ayah.bn()}) ").append(verse.translation)
                    for (c in verse.continuation) append("\n(${c.ayah.bn()}) ").append(c.translation)
                },
                fontFamily = HindSiliguri,
                fontSize = 15.5.sp,
                lineHeight = 26.sp,
                color = Ink.copy(alpha = 0.9f),
            )
        }

        val hadiths = remember(verse.key) { vm.data.hadithsFor(verse) }
        if (hadiths.isNotEmpty()) {
            HadithSection(hadiths)
        }

        AnimatedVisibility(visible = isCurrent && vm.repeatMode == RepeatSetting.ONE) {
            Row(
                modifier = Modifier.padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.RepeatOne, contentDescription = null, tint = Gold, modifier = Modifier.size(18.dp))
                Text(
                    text = "বারবার বাজছে · ${vm.playCount.bn()} বার হয়েছে",
                    fontFamily = HindSiliguri,
                    fontSize = 12.sp,
                    color = Emerald700,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        AnimatedVisibility(visible = isCurrent && vm.repeatMode == RepeatSetting.FIVE) {
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
        vm.isOffline(verse) -> Icon(
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

/** "সম্পর্কিত হাদিস" — চাপলে খোলে/বন্ধ হয়; এর ভেতরে চাপলে আয়াত বাজে না */
@Composable
private fun HadithSection(hadiths: List<Hadith>) {
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Gold.copy(alpha = 0.13f))
                .clickable { open = !open }
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = Emerald700, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = "সম্পর্কিত হাদিস (${hadiths.size.bn()})",
                fontFamily = HindSiliguri,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = Emerald800,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = if (open) "বন্ধ করুন" else "খুলুন",
                tint = Emerald700,
            )
        }
        AnimatedVisibility(visible = open) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for (h in hadiths) HadithItem(h)
            }
        }
    }
}

@Composable
private fun HadithItem(h: Hadith) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFFF6F3EA))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = h.ref,
                fontFamily = HindSiliguri,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Emerald800,
                modifier = Modifier.weight(1f),
            )
            if (h.grade.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = h.grade,
                    fontFamily = HindSiliguri,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (h.grade.startsWith("সহিহ")) Emerald600 else Color(0xFFB08A1E))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = h.text,
            fontFamily = HindSiliguri,
            fontSize = 14.sp,
            lineHeight = 23.sp,
            color = Ink.copy(alpha = 0.86f),
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
                            vm.isLoading -> "${vm.currentSource?.shortLabel ?: "অডিও"} ডাউনলোড হচ্ছে…"
                            else -> {
                                val src = vm.currentSource?.shortLabel ?: ""
                                when (vm.repeatMode) {
                                    RepeatSetting.ONCE -> "$src · শেষে পরের আয়াত"
                                    RepeatSetting.FIVE -> "$src · পুনরাবৃত্তি ${vm.playCount.bn()}/${REPEAT_TIMES.bn()}"
                                    RepeatSetting.ONE -> "$src · একই আয়াত বারবার (${vm.playCount.bn()})"
                                }
                            }
                        },
                        color = GoldLight,
                        fontFamily = HindSiliguri,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(8.dp))
                RepeatToggle(vm.repeatMode) { vm.cycleRepeatMode() }
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
private fun RepeatToggle(mode: RepeatSetting, onClick: () -> Unit) {
    val active = mode != RepeatSetting.ONCE
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
        Icon(
            imageVector = if (mode == RepeatSetting.ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            contentDescription = "রিপিট: ${mode.short}",
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = mode.short,
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

/** আরবি-ভারতীয় অঙ্কে আয়াত নম্বর, যেমন ٢٢ */
private fun Int.arabicDigits(): String = toString().map { '\u0660' + (it - '0') }.joinToString("")
