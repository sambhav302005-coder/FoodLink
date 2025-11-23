package com.example.igp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.igp.theme.EasyBotTheme

class ChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyBotTheme {
                ChatScreen(
                    onNavigateBack = {
                        finish() // This will close the activity and return to previous screen
                    }
                )
            }
        }
    }
}

@Composable
fun ChatScreen(onNavigateBack: () -> Unit) {
    val viewModel: ChatViewModel = viewModel()
    ChatPage(
        viewModel = viewModel,
        onNavigateBack = onNavigateBack
    )
}