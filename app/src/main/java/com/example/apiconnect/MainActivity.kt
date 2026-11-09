package com.example.apiconnect

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.apiconnect.ui.theme.ApiConnectTheme
import kotlinx.coroutines.launch

//fist app project

sealed interface PostUiState {
    object Idle : PostUiState
    object Loading : PostUiState
    data class Success(val post: Post) : PostUiState
    data class Error(val message: String) : PostUiState
}

class MainViewModel : ViewModel() {
    var uiState: PostUiState by mutableStateOf(PostUiState.Idle)
        private set

    fun fetchPost(postId: Int) {
        viewModelScope.launch {
            uiState = PostUiState.Loading
            try {
                val response = RetrofitClient.apiService.getPost(postId)
                uiState = PostUiState.Success(response)
            } catch (e: Exception) {
                uiState = PostUiState.Error(e.localizedMessage ?: "An error occurred")
            }
        }
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ApiConnectTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    PostScreen(viewModel = viewModel)
                }
            }
        }
    }
}

@Composable
fun PostScreen(viewModel: MainViewModel) {
    PostContent(
        uiState = viewModel.uiState,
        onFetchPost = { id -> viewModel.fetchPost(id) }
    )
}

@Composable
fun PostContent(
    uiState: PostUiState,
    onFetchPost: (Int) -> Unit
) {
    var postIdInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = R.drawable.api_internet),
            contentDescription = "API and Internet icon",
            modifier = Modifier
                .size(180.dp)
                .padding(bottom = 16.dp)
        )

        Text(
            text = "API Connect to Internet ",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        OutlinedTextField(
            value = postIdInput,
            onValueChange = { postIdInput = it },
            label = { Text("Post ID (1-100)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val id = postIdInput.trim().toIntOrNull()
                if (id != null && id in 1..100) {
                    onFetchPost(id)
                } else {
                    onFetchPost(-1)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Fetch Post")
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (uiState) {
            is PostUiState.Idle -> {
                Text("Enter a post ID and click Fetch Post.")
            }
            is PostUiState.Loading -> {
                CircularProgressIndicator()
            }
            is PostUiState.Success -> {
                val post = uiState.post
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "User ID: ${post.userId}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Post ID: ${post.id}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Title:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(text = post.title)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Body:",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(text = post.body)
                }
            }
            is PostUiState.Error -> {
                Text(
                    text = "Error: ${uiState.message}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PostScreenPreview() {
    ApiConnectTheme {
        PostContent(
            uiState = PostUiState.Success(
                Post(
                    userId = 1,
                    id = 1,
                    title = "Sample Title",
                    body = "Sample body content for preview."
                )
            ),
            onFetchPost = {}
        )
    }
}
