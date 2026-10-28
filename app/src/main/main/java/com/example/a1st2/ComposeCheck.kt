package com.ligtas.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/**
 * Step 1 proof that Kotlin + Compose compile. Nothing in the app calls this yet,
 * so no screen changes. It is deleted or replaced in Step 2.
 */
@Composable
fun ComposeCheck() {
    MaterialTheme {
        Text("Compose works")
    }
}

@Preview(showBackground = true)
@Composable
private fun ComposeCheckPreview() {
    ComposeCheck()
}
