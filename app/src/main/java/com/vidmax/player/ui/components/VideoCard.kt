package com.vidmax.player.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidmax.player.data.model.VideoItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VideoCard(
    video: VideoItem,
    duration: String,
    size: String,
    resolution: String,
    isSelected: Boolean = false, // ম্যাজিক: সিলেকশন স্টেট
    onClick: () -> Unit,
    onLongClick: () -> Unit = {}, // ম্যাজিক: লং-প্রেস ইভেন্ট
    modifier: Modifier = Modifier
) {
  // সিলেক্ট হলে কার্ডের ব্যাকগ্রাউন্ড হালকা পরিবর্তন হবে
  val backgroundColor =
      if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
      else MaterialTheme.colorScheme.surfaceVariant

  Row(
      modifier =
          modifier
              .fillMaxWidth()
              .clip(RoundedCornerShape(10.dp))
              .background(backgroundColor)
              // ম্যাজিক: CombinedClickable দিয়ে শর্ট এবং লং ক্লিক হ্যান্ডেল করা
              .combinedClickable(onClick = onClick, onLongClick = onLongClick)
              .padding(10.dp),
      verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier =
                Modifier.size(width = 112.dp, height = 68.dp).clip(RoundedCornerShape(8.dp))) {
              VideoThumbnail(path = video.path, modifier = Modifier.fillMaxSize())

              // ম্যাজিক: সিলেক্টেড অবস্থায় থাম্বনেইলের ওপর টিকমার্ক দেখাবে
              if (isSelected) {
                Box(
                    modifier =
                        Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center) {
                      Icon(
                          imageVector = Icons.Filled.Done,
                          contentDescription = "Selected",
                          tint = Color.White,
                          modifier = Modifier.size(32.dp))
                    }
              }

              Box(
                  modifier =
                      Modifier.align(Alignment.BottomEnd)
                          .padding(4.dp)
                          .clip(RoundedCornerShape(4.dp))
                          .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f))
                          .padding(horizontal = 4.dp, vertical = 2.dp)) {
                    Text(
                        text = duration,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium)
                  }
            }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          Text(
              text = video.title,
              color = MaterialTheme.colorScheme.onBackground,
              fontSize = 14.sp,
              fontWeight = FontWeight.SemiBold,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
              lineHeight = 18.sp)
          Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically) {
                ResolutionBadge(label = resolution)
                Text(
                    text = size,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp)
              }
          Text(
              text = video.folderName,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              fontSize = 11.sp,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis)
        }
      }
}

@Composable
fun ResolutionBadge(label: String) {
  Box(
      modifier =
          Modifier.clip(RoundedCornerShape(4.dp))
              .background(MaterialTheme.colorScheme.secondary)
              .padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold)
      }
}
