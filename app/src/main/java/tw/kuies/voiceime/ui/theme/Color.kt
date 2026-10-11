package tw.kuies.voiceime.ui.theme

import androidx.compose.ui.graphics.Color

// ============================================================================
// KuiesVox Warm & Cozy Design Tokens
// 奶茶暖萌 × 簡潔文具系
// ============================================================================

// Light Theme 色票
val WarmBgLight = Color(0xFFFFF8F2)              // 奶油白主背景
val WarmSurfaceLight = Color(0xFFFFFDF9)         // 卡片背景
val WarmSurfaceVariantLight = Color(0xFFF6EBDD)  // 燕麥奶茶區塊
val WarmPrimaryLight = Color(0xFFD39B7A)         // 暖奶茶棕主色
val WarmPrimaryContainerLight = Color(0xFFF3D8C7)// 主操作按鈕容器
val WarmSecondaryLight = Color(0xFFF3AAA4)       // 蜜桃粉
val WarmSecondaryContainerLight = Color(0xFFFCEBE8) // 淺蜜桃區塊
val WarmTertiaryLight = Color(0xFFBFD8C1)        // 鼠尾草綠
val WarmTertiaryContainerLight = Color(0xFFEBF3EC)  // 淺鼠尾草區塊
val WarmOutlineLight = Color(0xFFE5D6CA)         // 淺暖灰分隔線
val WarmOutlineVariantLight = Color(0xFFF0E5DC)  // 極淺卡片邊框
val WarmTextPrimaryLight = Color(0xFF5A4A42)     // 可可棕文字
val WarmTextSecondaryLight = Color(0xFF8A766A)   // 暖灰棕次要文字
val WarmErrorLight = Color(0xFFB84F50)           // 刪除及錯誤操作
val WarmErrorContainerLight = Color(0xFFF8DEDD)  // 淺紅容器
val WarmOnPrimaryLight = Color(0xFF35231C)       // 暖色主按鈕上的深色文字

// Dark Theme & IME 色票
val WarmBgDark = Color(0xFF2F2A28)               // 暖灰棕主背景
val WarmSurfaceDark = Color(0xFF3A312E)          // 次要表面
val WarmSurfaceVariantDark = Color(0xFF433936)   // 卡片及工具列
val WarmPrimaryDark = Color(0xFFE8BFA3)          // 奶茶杏色
val WarmPrimaryContainerDark = Color(0xFF624C40) // 主色容器
val WarmSecondaryDark = Color(0xFFF3AAA4)        // 蜜桃粉
val WarmSecondaryContainerDark = Color(0xFF583E3D) // 暗蜜桃容器
val WarmTertiaryDark = Color(0xFFBFD8C1)         // 柔和綠
val WarmTertiaryContainerDark = Color(0xFF3D4E41)  // 暗綠容器
val WarmOutlineDark = Color(0xFF63544E)          // 深暖灰分隔線
val WarmOutlineVariantDark = Color(0xFF50433E)   // 暗卡片邊框
val WarmTextPrimaryDark = Color(0xFFFFF9F4)      // 米白文字
val WarmTextSecondaryDark = Color(0xFFD1BFB5)    // 淡暖灰文字
val WarmErrorDark = Color(0xFFF2A7A7)            // 危險及錯誤狀態
val WarmErrorContainerDark = Color(0xFF7A2B2B)   // 暗紅容器
val WarmOnPrimaryDark = Color(0xFF35231C)        // 暖色主按鈕上的深色文字

// 模式色彩專用 Tokens（四種模式中央按鈕識別色）
val ModeNormalCream = Color(0xFFF3D8C7)          // 一般語音：奶油杏
val ModeTranslateSage = Color(0xFFD8E6C8)        // AI 語音翻譯：鼠尾草綠
val ModeFormatPurple = Color(0xFFE8D8F2)         // 格式指令：柔霧紫
val ModeEditPeach = Color(0xFFF3AAA4)            // AI 編輯：蜜桃粉

// 保留相容別名，指向新暖色系 Tokens
val VoiceBackground = WarmBgDark
val VoiceSurface = WarmSurfaceDark
val VoiceSurfaceVariant = WarmSurfaceVariantDark
val VoiceLavender = WarmPrimaryDark
val VoiceLavenderDeep = WarmPrimaryContainerDark
val VoiceMint = WarmTertiaryDark
val VoiceMintDeep = WarmTertiaryContainerDark
val VoicePink = WarmSecondaryDark
val VoicePinkDeep = WarmSecondaryContainerDark
val VoiceCream = WarmPrimaryContainerLight
val VoiceText = WarmTextPrimaryDark
val VoiceTextMuted = WarmTextSecondaryDark
