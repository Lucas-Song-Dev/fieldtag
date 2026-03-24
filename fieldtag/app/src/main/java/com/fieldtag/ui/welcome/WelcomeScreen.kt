package com.fieldtag.ui.welcome

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldtag.ui.theme.Dimens

private data class WelcomePage(
    val title: String,
    val body: String,
    val icon: ImageVector,
)

private val pages = listOf(
    WelcomePage(
        title = "Capture in the field",
        body = "Photograph instruments and keep everything organized by project and P&ID.",
        icon = Icons.Outlined.CameraAlt,
    ),
    WelcomePage(
        title = "Import P&IDs",
        body = "Bring your diagrams on-device, calibrate once, and jump straight to the right tags.",
        icon = Icons.Outlined.Description,
    ),
    WelcomePage(
        title = "Work offline first",
        body = "Use FieldTag with no account. Sign in only when you want to share jobs with your team.",
        icon = Icons.Outlined.Hub,
    ),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WelcomeScreen(
    onContinueWithoutAccount: () -> Unit,
    onSignIn: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        MaterialTheme.colorScheme.background,
                    ),
                ),
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.PaddingExtraLarge, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "FieldTag",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Field documentation for instruments & P&IDs",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f),
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(32.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) { page ->
                val item = pages[page]
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = null,
                        modifier = Modifier.size(88.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    repeat(pages.size) { i ->
                        Box(
                            modifier = Modifier
                                .size(if (pagerState.currentPage == i) 10.dp else 8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (pagerState.currentPage == i) MaterialTheme.colorScheme.secondary
                                    else MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.35f),
                                ),
                        )
                    }
                }
            }

            Button(
                onClick = {
                    viewModel.completeWelcome(onContinueWithoutAccount)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.MinTouchTarget),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ),
            ) {
                Text("Continue without account", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
            OutlinedButton(
                onClick = onSignIn,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.MinTouchTarget),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.75f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            ) {
                Text("Sign in", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
