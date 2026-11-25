@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package dev.cutlog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/** Lives above the tab switcher so the conversation survives switching tabs. */
class ChatState {
    val messages = mutableStateListOf<ChatMsg>()
    var input by mutableStateOf("")
    var busy by mutableStateOf(false)
    var error by mutableStateOf("")
}

private val QUICK = listOf(
    "Review my week",
    "What should dinner be tonight?",
    "Am I on track for my goal?",
    "How much protein do I still need today?",
)

@Composable
fun ClaudeScreen(store: Store, chat: ChatState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    val hasKey = store.prefs.apiKey.isNotBlank()

    LaunchedEffect(chat.messages.size) {
        if (chat.messages.isNotEmpty()) list.animateScrollToItem(chat.messages.size - 1)
    }

    fun send(text: String) {
        if (text.isBlank() || chat.busy) return
        chat.error = ""
        chat.messages += ChatMsg("user", text.trim())
        chat.input = ""
        chat.busy = true
        scope.launch {
            try {
                val reply = Claude.send(
                    store.prefs.apiKey, store.prefs.model, Claude.systemPrompt(store),
                    chat.messages.takeLast(20).dropWhile { it.role != "user" },
                )
                chat.messages += ChatMsg("assistant", reply.trim())
            } catch (e: Exception) {
                // Drop the unanswered message so the history keeps alternating user/assistant.
                val last = chat.messages.removeAt(chat.messages.lastIndex)
                chat.input = last.text
                chat.error = e.message ?: "Request failed"
            } finally {
                chat.busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Claude", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = { Claude.shareToClaude(ctx, store) }) { Text("Share to app") }
            if (chat.messages.isNotEmpty()) TextButton(onClick = { chat.messages.clear(); chat.error = "" }) { Text("Clear") }
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            state = list,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (chat.messages.isEmpty()) {
                item {
                    Panel {
                        Text(
                            if (hasKey) "Ask anything. Claude sees your targets, the last 7 days, today's entries and your weigh-ins."
                            else "Add an Anthropic API key in Settings to chat here. Or tap “Share to app” to send your week to the Claude app (free with your subscription).",
                            color = Muted, fontSize = 14.sp,
                        )
                    }
                }
                if (hasKey) item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QUICK.forEach { q -> AssistChip(onClick = { send(q) }, label = { Text(q) }) }
                    }
                }
            }
            itemsIndexed(chat.messages) { _, m ->
                val mine = m.role == "user"
                Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
                    SelectionContainer {
                        Text(
                            m.text,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .widthIn(max = 320.dp)
                                .background(
                                    if (mine) Color(0xFF1D3A2A) else Color(0xFF151A17),
                                    RoundedCornerShape(14.dp),
                                )
                                .padding(12.dp),
                        )
                    }
                }
            }
            if (chat.busy) item { Text("Claude is thinking…", color = Muted, fontSize = 13.sp) }
            if (chat.error.isNotEmpty()) item { Text(chat.error, color = Over, fontSize = 13.sp) }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = chat.input,
                onValueChange = { chat.input = it },
                placeholder = { Text(if (hasKey) "Message Claude" else "API key needed") },
                enabled = hasKey,
                maxLines = 4,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = { send(chat.input) },
                enabled = hasKey && !chat.busy && chat.input.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Send") }
        }
    }
}
