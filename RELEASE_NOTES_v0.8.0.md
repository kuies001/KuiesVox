# KuiesVox v0.8.0 Release Notes

KuiesVox v0.8.0 更新語音輸入面板與智慧整理設定，並改善輸入法工具列的操作辨識度。

## 新功能

- 新增 Groq Whisper 語音辨識模型選擇：Whisper Large V3 與 Whisper Large V3 Turbo；預設使用 Turbo。
- 新增智慧文字整理 Provider 選擇：Groq、Google Gemini 與 OpenAI。
- 提供 Provider 推薦模型與自訂 Model ID 欄位。推薦模型包括 Groq Qwen 3.8 27B、Gemini 3.5 Flash-Lite 與 GPT-5.6 Luna。
- 新增 Groq、Gemini 與 OpenAI 官方 API Key 申請連結。API Key 儲存在 App 私有設定中。
- 新增句尾句號模式：智慧（依輸入欄位判斷）、加入句號、不加入句號。
- 新增 IME 設定按鈕，可從輸入法面板開啟 KuiesVox 設定。
- 新增 Enter／換行按鈕，可在目前文字欄位插入換行。

Gemini 與 OpenAI 目前提供 Provider、模型與 API Key 設定欄位，尚未串接對應 API；選用時會使用本機文字修正結果。Groq 智慧整理仍使用既有整理流程。

### 模型清單

- Groq 文字整理：Qwen 3.8 27B（推薦）、Qwen 3.6 27B（舊版相容選項）、GPT-OSS 20B。
- Gemini 設定欄位：Gemini 3.5 Flash-Lite（推薦）、Gemini 3.6 Flash、Gemini 3.8 Flash。
- OpenAI 設定欄位：GPT-5.6 Luna（推薦）、GPT-5.6 Terra、GPT-5.4 Mini。
- 各 Provider 可分別設定自訂 Model ID。

### 官方 API Key 申請頁

- Groq：[console.groq.com/keys](https://console.groq.com/keys)
- Google Gemini：[aistudio.google.com/apikey](https://aistudio.google.com/apikey)
- OpenAI：[platform.openai.com/api-keys](https://platform.openai.com/api-keys)

## UI / UX 改進

- 主語音輸入按鈕改為大圓形錄音鍵，讓待命畫面的主要操作更清楚。
- 優化 IME 工具列排列，依序為設定、刪除、換行與切換鍵盤。
- 調整刪除與換行圖示：刪除使用帶 X 的 backspace 鍵帽圖示，換行改用折返箭頭，視覺語意更明確。
- 增加刪除、換行與切換鍵盤之間的視覺間距，避免四顆按鈕等距擠在一起。
- 以粉灰色標示刪除、淡紫色標示換行，設定與切換鍵盤維持中性色，降低快速輸入時誤按刪除或換行的可能性。
- 保持工具按鈕尺寸一致，並以狀態文字省略方式支援較窄的面板寬度。
- 保留 Android navigation bar 與 system gesture inset，改善小螢幕及系統鍵盤切換／收合區域的相容性。
- 最近輸入維持單行精簡預覽，不擴大成卡片。
