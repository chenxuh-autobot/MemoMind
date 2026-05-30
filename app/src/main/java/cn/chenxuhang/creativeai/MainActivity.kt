package cn.chenxuhang.creativeai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import cn.chenxuhang.creativeai.ui.theme.CreativeAiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CreativeAiTheme {
                CreativeAiApp()
            }
        }
    }
}

