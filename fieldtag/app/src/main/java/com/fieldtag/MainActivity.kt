package com.fieldtag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fieldtag.navigation.AppNavGraph
import com.fieldtag.ui.entry.AppEntryViewModel
import com.fieldtag.ui.entry.EntryState
import com.fieldtag.ui.theme.FieldTagTheme
import dagger.hilt.android.AndroidEntryPoint
import androidx.hilt.navigation.compose.hiltViewModel

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FieldTagTheme {
                val entryViewModel: AppEntryViewModel = hiltViewModel()
                val entryState by entryViewModel.entryState.collectAsState()
                when (val state = entryState) {
                    EntryState.Loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                    is EntryState.Ready -> AppNavGraph(startDestination = state.startDestination)
                }
            }
        }
    }
}
