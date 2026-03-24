package com.fieldtag.ui.projects

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.fieldtag.data.db.entities.ProjectEntity
import com.fieldtag.domain.auth.AuthSession
import com.fieldtag.ui.theme.Dimens
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectListScreen(
    authSession: AuthSession,
    onProjectClick: (String) -> Unit,
    onOpenSignIn: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: ProjectListViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    var accountMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("FieldTag", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                actions = {
                    Box {
                        IconButton(
                            onClick = { accountMenuExpanded = true },
                            modifier = Modifier.size(Dimens.MinTouchTarget),
                        ) {
                            Icon(
                                Icons.Outlined.AccountCircle,
                                contentDescription = "Account",
                                tint = MaterialTheme.colorScheme.onPrimary,
                            )
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                        ) {
                            when (authSession) {
                                is AuthSession.SignedIn -> {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                authSession.displayName?.let { "Signed in as $it" }
                                                    ?: "Signed in",
                                            )
                                        },
                                        onClick = {},
                                        enabled = false,
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Sign out") },
                                        onClick = {
                                            accountMenuExpanded = false
                                            onSignOut()
                                        },
                                    )
                                }
                                AuthSession.SignedOut -> {
                                    DropdownMenuItem(
                                        text = { Text("Sign in") },
                                        onClick = {
                                            accountMenuExpanded = false
                                            onOpenSignIn()
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.showCreateDialog()
                },
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
                modifier = Modifier.size(Dimens.FabSize),
            ) {
                Icon(
                    Icons.Default.Add, 
                    contentDescription = "New Project", 
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
        ) {
            // Search Bar
            if (uiState.allProjects.isNotEmpty() || uiState.searchQuery.isNotEmpty()) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.PaddingLarge, vertical = Dimens.PaddingMedium),
                    placeholder = { Text("Search projects or locations...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    singleLine = true,
                )
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                when {
                    uiState.isLoading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    uiState.allProjects.isEmpty() -> EmptyProjectsState(onCreateClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.showCreateDialog()
                    })
                    uiState.projects.isEmpty() -> {
                        Text(
                            "No projects match your search.",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    else -> ProjectList(
                        projects = uiState.projects,
                        onProjectClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) 
                            onProjectClick(it)
                        },
                    )
                }
            }
        }
    }

    if (uiState.showCreateDialog) {
        CreateProjectDialog(
            onConfirm = { name, notes -> 
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                viewModel.createProject(name, notes) 
            },
            onDismiss = viewModel::dismissCreateDialog,
        )
    }
}

@Composable
private fun ProjectList(
    projects: List<ProjectEntity>,
    onProjectClick: (String) -> Unit,
) {
    // Increased minSize for wider horizontal landscape cards
    val minColWidth = 360.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minColWidth),
        contentPadding = PaddingValues(Dimens.PaddingLarge),
        horizontalArrangement = Arrangement.spacedBy(Dimens.PaddingLarge),
        verticalArrangement = Arrangement.spacedBy(Dimens.PaddingLarge),
        modifier = Modifier.fillMaxSize()
    ) {
        items(projects, key = { it.id }) { project ->
            ProjectCard(project = project, onClick = { onProjectClick(project.id) })
        }
    }
}

@Composable
private fun ProjectCard(project: ProjectEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(Dimens.PaddingExtraLarge)) {
            Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            
            Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
            
            if (!project.locationName.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(Dimens.PaddingSmall))
                    Text(project.locationName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            Spacer(modifier = Modifier.height(Dimens.PaddingSmall))
            
            val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(project.createdAt))
            Text("Created $dateStr", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)

            if (!project.notes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
                Text(
                    text = project.notes, 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptyProjectsState(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.PaddingExtraLarge),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.secondary),
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingExtraLarge))
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingLarge))
        Text("Start your first project", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(Dimens.PaddingMedium))
        Text(
            "Create a project, import a P&ID, and capture instruments in the field — no account required.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(Dimens.PaddingExtraLarge))
        Button(
            onClick = onCreateClick,
            modifier = Modifier.height(Dimens.MinTouchTarget),
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            ),
        ) {
            Text("Create project")
        }
    }
}

@Composable
private fun CreateProjectDialog(onConfirm: (String, String?) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.PaddingMedium)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Project name *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Location / Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name, notes.takeIf { it.isNotBlank() }) }, 
                enabled = name.isNotBlank(),
                modifier = Modifier.height(Dimens.MinTouchTarget),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary, contentColor = MaterialTheme.colorScheme.onSecondary)
            ) {
                Text("Create")
            }
        },
        dismissButton = { 
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.height(Dimens.MinTouchTarget)
            ) { 
                Text("Cancel") 
            } 
        },
        modifier = Modifier.fillMaxWidth(Dimens.DialogWidthPercentage)
    )
}
