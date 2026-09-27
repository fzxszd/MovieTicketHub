package me.ibrahim.moviesapp.compose.presentation.movies_detail.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.ibrahim.moviesapp.compose.data.database.CommentEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MovieCommentsSection(
    comments: List<CommentEntity>,
    onSubmitComment: (String, Int?) -> Unit,
    onLikeComment: (Int) -> Unit,
    isLiked: (Int) -> Boolean,
    modifier: Modifier = Modifier
) {
    var newCommentText by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<CommentEntity?>(null) }

    // 1. 提取所有顶层评论 (parentId 为 null)
    val rootComments = remember(comments) {
        comments.filter { it.parentId == null }.sortedByDescending { it.timestamp }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            text = "影评 (${comments.size})",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- 发表/回复 输入框 ---
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(16.dp))
                .padding(8.dp)
        ) {
            if (replyingTo != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "正在回复 @${replyingTo!!.userName}",
                        color = Color.Yellow,
                        fontSize = 12.sp
                    )
                    Text(
                        text = "取消",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.clickable { replyingTo = null }
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (replyingTo == null) "发表你的看法..." else "回复...", color = Color.White.copy(alpha = 0.5f)) },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        cursorColor = Color.Yellow
                    )
                )

                IconButton(
                    onClick = {
                        if (newCommentText.isNotBlank()) {
                            onSubmitComment(newCommentText, replyingTo?.id)
                            newCommentText = ""
                            replyingTo = null
                        }
                    },
                    enabled = newCommentText.isNotBlank()
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = if (newCommentText.isNotBlank()) Color.Yellow else Color.Gray
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 循环显示评论树 ---
        rootComments.forEach { root ->
            RecursiveCommentItem(
                comment = root,
                allComments = comments,
                depth = 0,
                onLikeClick = onLikeComment,
                onReplyClick = { replyingTo = it },
                isLiked = isLiked
            )
            
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                thickness = 0.5.dp,
                color = Color.White.copy(alpha = 0.1f)
            )
        }
    }
}

@Composable
fun RecursiveCommentItem(
    comment: CommentEntity,
    allComments: List<CommentEntity>,
    depth: Int,
    onLikeClick: (Int) -> Unit,
    onReplyClick: (CommentEntity) -> Unit,
    isLiked: (Int) -> Boolean
) {
    // 找到被回复人的名字（如果有）
    val replyToName = remember(comment.parentId, allComments) {
        if (comment.parentId != null) {
            allComments.find { it.id == comment.parentId }?.userName
        } else null
    }

    // 1. 显示当前评论项
    CommentItemView(
        comment = comment,
        onLikeClick = { onLikeClick(comment.id) },
        onReplyClick = { onReplyClick(comment) },
        isLiked = isLiked(comment.id),
        depth = depth,
        replyToName = replyToName
    )

    // 2. 递归寻找并显示子回复
    val children = remember(allComments, comment.id) {
        allComments.filter { it.parentId == comment.id }.sortedBy { it.timestamp }
    }

    if (children.isNotEmpty()) {
        Column(modifier = Modifier.padding(start = if (depth < 2) 32.dp else 8.dp)) {
            children.forEach { child ->
                RecursiveCommentItem(
                    comment = child,
                    allComments = allComments,
                    depth = depth + 1,
                    onLikeClick = onLikeClick,
                    onReplyClick = onReplyClick,
                    isLiked = isLiked
                )
            }
        }
    }
}

@Composable
fun CommentItemView(
    comment: CommentEntity,
    onLikeClick: () -> Unit,
    onReplyClick: () -> Unit,
    isLiked: Boolean,
    depth: Int = 0,
    replyToName: String? = null
) {
    val timeStr = remember(comment.timestamp) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(comment.timestamp))
    }
    val avatarSize = if (depth > 0) 28.dp else 36.dp

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        // 头像
        Box(
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(comment.userName.take(1), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(12.dp))

        // 内容区
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = comment.userName, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (replyToName != null) {
                    Text(text = " ▶ ", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 4.dp))
                    Text(text = replyToName, color = Color.Yellow.copy(alpha = 0.8f), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = comment.content, color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp)
            
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = timeStr, color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                Spacer(modifier = Modifier.width(16.dp))
                
                // 回复按钮：核心修复点，每一层点击都能触发针对该 ID 的回复
                Row(
                    modifier = Modifier.clickable { onReplyClick() },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                    Text("回复", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }

        // 点赞区域
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(start = 8.dp)) {
            IconButton(onClick = onLikeClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = null,
                    tint = if (isLiked) Color.Red else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
            if (comment.likes > 0) {
                Text(text = comment.likes.toString(), color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp)
            }
        }
    }
}
