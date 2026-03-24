package com.fieldtag.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldtag.BuildConfig
import com.fieldtag.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    fromWelcomeFlow: Boolean,
    onBack: () -> Unit,
    onSignedInFromWelcome: () -> Unit,
    onSignedInFromApp: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel(),
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Dimens.PaddingExtraLarge),
            verticalArrangement = Arrangement.spacedBy(Dimens.PaddingExtraLarge),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Sign in to share jobs with your team",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "You can capture projects and export locally without an account. " +
                    "An account is only needed when collaboration and cloud sharing are available.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { /* Google Sign-In wired with Supabase / Credential Manager later */ },
                enabled = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimens.MinTouchTarget),
            ) {
                Text("Sign in with Google (coming soon)")
            }
            if (BuildConfig.DEBUG) {
                Button(
                    onClick = {
                        viewModel.signInWithGoogleStub()
                        if (fromWelcomeFlow) onSignedInFromWelcome() else onSignedInFromApp()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Dimens.MinTouchTarget),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary,
                        contentColor = MaterialTheme.colorScheme.onSecondary,
                    ),
                ) {
                    Text("Debug: complete sign-in preview")
                }
            }
            TextButton(
                onClick = onBack,
                modifier = Modifier.height(Dimens.MinTouchTarget),
            ) {
                Text("Not now")
            }
        }
    }
}
