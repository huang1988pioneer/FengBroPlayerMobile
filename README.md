# 風哥播放器（FengBro Player）

Android 版多媒體播放器，移植自 [MusicVideoMediaPlayer](https://github.com/huang1988pioneer/MusicVideoMediaPlayer) 的 **Stage-centric Pro Player** 架構（參考 KMPlayer / PotPlayer IA）。

桌面原作為 AvaloniaUI + LibVLC；本專案改為 **Kotlin + Jetpack Compose + Media3**，在 Android 上保留同一套資訊架構與 Pro Dark 視覺語言。

## 介面架構

| 區域 | 內容 |
|------|------|
| **選單** | 開啟檔案 / 資料夾 / 網路串流、字幕、播放速度、影片資訊 |
| **主舞台** | 影片：ExoPlayer 畫面；音樂／待機：封面 + 波形 + LRC |
| **播放清單** | 右側 dock（平板橫向 300dp；手機為覆蓋層）：清單 / 最近 / 串流 |
| **控制列** | 停止、上一首、播放/暫停、下一首、±10s、進度、靜音、音量、清單、全螢幕、開啟 |
| **狀態列** | 狀態訊息與格式摘要 |

預設播放清單為空白；開啟檔案、資料夾或網路串流後才會加入項目。

### 開啟媒體

| 方式 | 說明 |
|------|------|
| 選單 / 控制列「開啟檔案」 | 音樂 + 影片（SAF，權限可持久化） |
| 開啟資料夾 | 遞迴掃描音訊／影片 |
| 開啟網路串流 | http(s) 直連、HLS / DASH；YouTube / Bilibili 等網頁以 NewPipe Extractor 解析 |
| 系統分享 / 開啟方式 | 可從其他 App 丟入檔案或 URL |

本機與直連串流由 **Media3 ExoPlayer** 播放。清除清單會清空佇列並停止播放。

## 手勢與快捷行為

| 操作 | 功能 |
|------|------|
| 點舞台中央 | 播放／暫停 |
| 點舞台左側／右側 | 倒退／快轉 10 秒 |
| 雙擊舞台 | 全螢幕 |
| 返回鍵 | 離開全螢幕 |
| 全螢幕閒置 2.5 秒 | 自動隱藏控制列 |

## 設計語言

- **Pro Dark**：中性深灰 `#0D0D0D`–`#1E1E1E`
- 強調色鎖定 **`#3B9EFF`**
- 舞台中心 + 底部密集 control bar
- 中文（zh-TW）介面；預設音量 100%

## 專案結構

```
FengBroPlayer/
├── core/     # 純 JVM：模型、播放清單語意、LRC、最近播放、串流 URI
├── app/      # Android：Compose UI、Media3、SAF、NewPipe 解析
└── docs 對應來源：MusicVideoMediaPlayer 的 stage-centric 設計
```

桌面版行為對照（刻意保留）：

- `Playlist` 為唯一佇列；next / prev / 播畢自動跳過不可播放列
- 混合匯入只自動播放**第一個新加入且可播放**的項目
- 最近播放 50 筆、網路串流 30 筆，JSON 持久化
- 進入全螢幕會暫藏清單，離開後還原

## 與桌面版的差異

| 項目 | 桌面（Avalonia） | Android（本專案） |
|------|------------------|-------------------|
| UI | Avalonia XAML | Jetpack Compose |
| 引擎 | LibVLC | Media3 ExoPlayer（HLS / DASH / RTSP） |
| 影片手勢 | HWND 限制，鍵盤為主 | 可點畫面暫停／左右跳轉 |
| 檔案 | 原生路徑 + 拖放 | Storage Access Framework |
| YouTube | 系統 yt-dlp | NewPipe Extractor |
| 背景播放 | 視窗內 | MediaSession 前景服務 + 通知 |

部分容器（舊 AVI / 特殊 MKV）受 Android 解碼器限制，與桌面 LibVLC 的格式覆蓋範圍不完全相同。

## 建置

需求：

- JDK 17+
- Android SDK（compileSdk 35）
- Android Studio 或命令列 Gradle

```bash
# Windows
.\gradlew.bat :core:test
.\gradlew.bat :app:assembleDebug
```

產出 APK：`app/build/outputs/apk/debug/app-debug.apk`

第一次建置會下載 Gradle 與 Android 依賴，需網路。

## 來源

移植參考：<https://github.com/huang1988pioneer/MusicVideoMediaPlayer>
