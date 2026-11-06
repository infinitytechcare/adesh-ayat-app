package com.adeshayat.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adeshayat.app.AudioMode
import com.adeshayat.app.PlayerViewModel
import com.adeshayat.app.RepeatSetting

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: PlayerViewModel, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Cream,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 24.dp)
        ) {
            Text(
                text = "সেটিংস",
                fontFamily = HindSiliguri,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Emerald800,
            )
            Spacer(Modifier.height(14.dp))

            // ---- বাংলা অনুবাদ ----
            SettingsCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { vm.updateShowBangla(!vm.showBangla) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            "বাংলা অনুবাদ দেখান",
                            fontFamily = HindSiliguri,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = Ink,
                        )
                        Text(
                            "প্রতিটি আয়াতের নিচে বাংলা ভাবার্থ",
                            fontFamily = HindSiliguri,
                            fontSize = 13.sp,
                            color = Muted,
                        )
                    }
                    Switch(
                        checked = vm.showBangla,
                        onCheckedChange = { vm.updateShowBangla(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Emerald700,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("অডিও")
            SettingsCard {
                for (mode in AudioMode.values()) {
                    OptionRow(
                        title = mode.label,
                        subtitle = when (mode) {
                            AudioMode.ARABIC_ONLY -> "শায়খ মিশারী রাশিদ আল-আফাসি"
                            AudioMode.ARABIC_URDU -> "প্রতিটি আয়াতের আরবির পর উর্দু অনুবাদ (শামশাদ আলী খান)"
                            AudioMode.URDU_ONLY -> "শুধু উর্দু অনুবাদের অডিও"
                        },
                        selected = vm.audioMode == mode,
                        onClick = { vm.updateAudioMode(mode) },
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            SectionLabel("রিপিট")
            SettingsCard {
                for (mode in RepeatSetting.values()) {
                    OptionRow(
                        title = mode.short,
                        subtitle = mode.label,
                        selected = vm.repeatMode == mode,
                        onClick = { vm.updateRepeatMode(mode) },
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "নতুন অডিও সেটিং পরের আয়াত থেকে কার্যকর হবে। প্লেয়ারের রিপিট চিপ চেপেও মোড বদলানো যায়।",
                fontFamily = HindSiliguri,
                fontSize = 12.sp,
                color = Muted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        fontFamily = HindSiliguri,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = Emerald700,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        content()
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = Emerald700),
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                fontFamily = HindSiliguri,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (selected) Emerald800 else Ink,
            )
            Text(subtitle, fontFamily = HindSiliguri, fontSize = 12.sp, color = Muted)
        }
    }
}
