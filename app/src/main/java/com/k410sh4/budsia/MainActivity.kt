package com.k410sh4.budsia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.hilt.navigation.compose.hiltViewModel
import com.k410sh4.budsia.feature.main.BudsIAViewModel
import com.k410sh4.budsia.ui.BudsIAAppRoot
import com.k410sh4.budsia.ui.theme.BudsIATheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BudsIATheme {
                val vm: BudsIAViewModel = hiltViewModel()
                BudsIAAppRoot(vm)
            }
        }
    }
}
