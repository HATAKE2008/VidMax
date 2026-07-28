package com.vidmax.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.R
import com.vidmax.player.data.model.FolderItem

@Composable
fun FolderCard(
    folder: FolderItem,
    totalSize: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(12.dp))
              .clickable { onClick() }
              .padding(vertical = 12.dp, horizontal = 8.dp),
      verticalAlignment = Alignment.CenterVertically) {
        // ফোল্ডার আইকন (Y Player এর মতো বড় করে বাম পাশে)
        Box(
            modifier =
                Modifier.size(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = 0.15f)), // হালকা ব্যাকগ্রাউন্ড
            contentAlignment = Alignment.Center) {
              Icon(
                  painter = painterResource(id = R.drawable.ic_folder),
                  contentDescription = "Folder Icon",
                  tint = MaterialTheme.colorScheme.primary, // থিমের কালার পাবে
                  modifier = Modifier.size(32.dp))
            }

        Spacer(modifier = Modifier.width(16.dp))

        // ফোল্ডারের নাম ও ডিটেইলস
        Column(modifier = Modifier.weight(1f)) {
          Text(
              text = folder.name,
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 16.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)
          Spacer(modifier = Modifier.height(4.dp))
          Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Videos • ",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp)
            Text(
                text = totalSize,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp)
          }
        }
      }
}
