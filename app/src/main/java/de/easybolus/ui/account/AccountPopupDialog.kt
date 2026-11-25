package de.easybolus.ui.account

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.easybolus.R
import de.easybolus.data.sync.DriveSyncState
import de.easybolus.data.sync.SyncStatus
import de.easybolus.ui.theme.BgInRangeGreen
import de.easybolus.ui.theme.BgLowRed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private object AvatarBitmapCache {
    private val cache = HashMap<String, ImageBitmap>()
    fun get(url: String): ImageBitmap? = cache[url]
    fun put(url: String, bmp: ImageBitmap) {
        if (cache.size > 10) cache.clear()
        cache[url] = bmp
    }
}

@Composable
fun rememberRemoteAvatar(url: String?): ImageBitmap? {
    var bitmap by remember(url) { mutableStateOf(url?.let { AvatarBitmapCache.get(it) }) }
    LaunchedEffect(url) {
        if (url.isNullOrBlank() || bitmap != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                URL(url).openStream().use { stream ->
                    val raw: Bitmap? = BitmapFactory.decodeStream(stream)
                    raw?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (loaded != null) {
            AvatarBitmapCache.put(url, loaded)
            bitmap = loaded
        }
    }
    return bitmap
}

@Composable
fun AccountAvatarButton(
    syncState: DriveSyncState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (syncState.isSignedIn) {
            AccountAvatarImage(
                photoUrl = syncState.photoUrl,
                displayName = syncState.accountName ?: syncState.accountEmail,
                size = 30.dp
            )
            val dotColor = when (syncState.lastSyncStatus) {
                SyncStatus.SUCCESS -> BgInRangeGreen
                SyncStatus.SYNCING -> MaterialTheme.colorScheme.primary
                SyncStatus.ERROR -> BgLowRed
                SyncStatus.IDLE -> MaterialTheme.colorScheme.outline
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(3.dp)
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dotColor)
                    .border(1.5.dp, MaterialTheme.colorScheme.background, CircleShape)
            )
        } else {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = stringResource(R.string.account_button_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
fun AccountAvatarImage(
    photoUrl: String?,
    displayName: String?,
    size: Dp,
    modifier: Modifier = Modifier
) {
    val avatarBitmap = rememberRemoteAvatar(photoUrl)
    val initial = displayName?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "G"
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        if (avatarBitmap != null) {
            Image(
                bitmap = avatarBitmap,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = initial,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.SemiBold,
                fontSize = (size.value * 0.45f).sp
            )
        }
    }
}

@Composable
fun AccountPopupDialog(
    syncState: DriveSyncState,
    isBusy: Boolean,
    onDismiss: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onToggleAutoSync: (Boolean) -> Unit,
    onBackupNow: () -> Unit,
    onRestoreNow: () -> Unit
) {
    val locale = Locale.getDefault()
    val dateFmt = remember(locale) { SimpleDateFormat("dd.MM. HH:mm", locale) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.account_popup_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (!syncState.isSignedIn) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            text = stringResource(R.string.account_not_signed_in),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = stringResource(R.string.account_sign_in_hint),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.action_cancel))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = onSignIn, enabled = !isBusy) {
                            if (isBusy) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text(stringResource(R.string.account_sign_in_google))
                            }
                        }
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        AccountAvatarImage(
                            photoUrl = syncState.photoUrl,
                            displayName = syncState.accountName ?: syncState.accountEmail,
                            size = 44.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            if (!syncState.accountName.isNullOrBlank()) {
                                Text(
                                    text = syncState.accountName,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = syncState.accountEmail.orEmpty(),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    val statusText = when {
                        isBusy || syncState.lastSyncStatus == SyncStatus.SYNCING ->
                            stringResource(R.string.account_status_syncing)
                        syncState.lastSyncStatus == SyncStatus.ERROR && !syncState.lastSyncMessage.isNullOrBlank() ->
                            stringResource(R.string.account_status_error, syncState.lastSyncMessage)
                        syncState.lastSyncMillis != null ->
                            stringResource(R.string.account_status_last_sync, dateFmt.format(Date(syncState.lastSyncMillis)))
                        else ->
                            stringResource(R.string.account_status_never)
                    }
                    val statusColor: Color = when {
                        syncState.lastSyncStatus == SyncStatus.ERROR -> BgLowRed
                        syncState.lastSyncStatus == SyncStatus.SUCCESS -> BgInRangeGreen
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = statusColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = statusText,
                            fontSize = 12.sp,
                            color = statusColor
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.account_auto_sync),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.account_auto_sync_sub),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = syncState.autoSyncEnabled,
                            onCheckedChange = onToggleAutoSync
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onBackupNow,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.account_sync_now),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                        OutlinedButton(
                            onClick = onRestoreNow,
                            enabled = !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.account_restore_now),
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onSignOut, enabled = !isBusy) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(stringResource(R.string.account_sign_out))
                        }
                        TextButton(onClick = onDismiss) {
                            Text(stringResource(R.string.btn_close))
                        }
                    }
                }
            }
        }
    }
}
