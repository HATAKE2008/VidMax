package com.vidmax.player.ui.permission

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.ui.theme.*

@Composable
fun PermissionScreen(onRequestPermission: () -> Unit) {
Box(
modifier = Modifier
.fillMaxSize()
.background(Background),
contentAlignment = Alignment.Center
) {
Column(
horizontalAlignment = Alignment.CenterHorizontally,
verticalArrangement = Arrangement.spacedBy(20.dp),
modifier = Modifier.padding(40.dp)
) {
Box(
modifier = Modifier
.size(100.dp)
.clip(RoundedCornerShape(24.dp))
.background(CardBg),
contentAlignment = Alignment.Center
) {
Icon(
painter = painterResource(id = R.drawable.ic_folder_open),
contentDescription = null,
tint = Gold,
modifier = Modifier.size(52.dp)
)
}

Text(
text = "VidMax",
color = Gold,
fontSize = 28.sp,
fontWeight = FontWeight.ExtraBold
)

Text(
text = "Storage Permission Required",
color = TextPrimary,
fontSize = 18.sp,
fontWeight = FontWeight.SemiBold,
textAlign = TextAlign.Center
)

Text(
text = "VidMax needs access to your storage\nto browse and play your video files.",
color = TextSecondary,
fontSize = 14.sp,
textAlign = TextAlign.Center,
lineHeight = 22.sp
)

Button(
onClick = onRequestPermission,
modifier = Modifier
.fillMaxWidth()
.height(52.dp),
shape = RoundedCornerShape(14.dp),
colors = ButtonDefaults.buttonColors(
containerColor = Gold,
contentColor = Background
)
) {
Text(
text = "Grant Permission",
fontSize = 15.sp,
fontWeight = FontWeight.Bold
)
}
}
}
}