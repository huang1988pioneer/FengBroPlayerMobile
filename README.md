# 鋒兄播放器（FengBroPlayerMobile）

Android 版多媒體播放器，移植自 [MusicVideoMediaPlayer](https://github.com/huang1988pioneer/MusicVideoMediaPlayer) 的 **Stage-centric Pro Player** 架構（參考 KMPlayer / PotPlayer IA）。

桌面原作為 AvaloniaUI + LibVLC；本專案改為 **Kotlin + Jetpack Compose + Media3**，在 Android 上保留同一套資訊架構與 Pro Dark 視覺語言。

## 介面架構

| 區域 | 內容 |
|------|------|
| **啟動頁** | 開啟檔案／資料夾／網路串流，以及最近播放 |
| **主舞台** | 影片滿版；音樂為封面 + 波形 + LRC。控制層蓋在畫面上，不永久佔高 |
| **播放清單** | 手機由底部滑出；平板為右側面板。分頁：清單 / 最近 / 串流 |
| **控制層** | 點一下顯示／隱藏：標題、鎖定、更多、中央播放、進度、上一首／下一首、清單、全螢幕 |
| **更多** | 速度、畫面填滿、字幕、資訊、停止、開啟檔案／資料夾／串流 |

預設播放清單為空白；開啟檔案、資料夾或網路串流後才會加入項目。

### 開啟媒體

| 方式 | 說明 |
|------|------|
| 選單 / 控制列「開啟檔案」 | 音樂 + 影片（SAF，權限可持久化） |
| 開啟資料夾 | 遞迴掃描音訊／影片，並配對同名字幕 |
| 同名字幕 | 開影片時自動載入同名 `.srt`（同資料夾；也可一次選影片+字幕） |
| 同名歌詞 | 開音樂／影片時自動載入同名 `.lrc` |
| 開啟網路串流 | http(s) 直連、HLS / DASH；YouTube / Bilibili 等網頁以 NewPipe Extractor 解析 |
| 系統分享 / 開啟方式 | 可從其他 App 丟入檔案或 URL |

本機與直連串流由 **Media3 ExoPlayer** 播放。清除清單會清空佇列並停止播放。

## 手勢與快捷行為

| 操作 | 功能 |
|------|------|
| 點一下畫面 | 顯示／隱藏控制（播放中約 3 秒自動隱藏） |
| 雙擊左側／右側 | 倒退／快轉 10 秒，連點可累加 |
| 雙擊中央 | 播放／暫停 |
| 左右滑 | 預覽並跳轉進度 |
| 左側上下滑 | 亮度 |
| 右側上下滑 | 音量 |
| 長按 | 二倍速，放開恢復 |
| 鎖定 | 隱藏控制，只留解鎖鈕 |
| 子母畫面 | 影片可縮小到系統小窗；按 Home 也會自動進入（Android 12+） |
| 返回鍵 | 先關清單／設定或解鎖，再退出橫向全螢幕 |
| 通知／鎖定畫面 | 播放中顯示媒體通知：播放／暫停、上一首、下一首 |

## 設計語言

- **Pro Dark**：中性深灰 `#0D0D0D`–`#1E1E1E`
- 強調色鎖定 **`#3B9EFF`**
- 舞台滿版 + 覆蓋式控制層（點一下顯示／隱藏）
- 中文（zh-TW）介面；預設音量 100%

## 專案結構

```
FengBroPlayerMobile/
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
| 背景播放 | 視窗內 | MediaSession 前景服務 + 媒體通知／鎖定畫面 |
| 子母畫面 | 無 | 系統 PiP，播放中按 Home 或畫面上的子母畫面鈕 |

部分容器（舊 AVI / 特殊 MKV）受 Android 解碼器限制，與桌面 LibVLC 的格式覆蓋範圍不完全相同。

## 下載

正式 APK 見 [Releases](https://github.com/huang1988pioneer/FengBroPlayerMobile/releases)。打 `v*` 標籤會由 GitHub Actions 自動建置並上傳。

## 建置

需求：

- JDK 17+
- Android SDK（compileSdk 35）
- Android Studio 或命令列 Gradle

```bash
# Windows
.\gradlew.bat :core:test
.\gradlew.bat :app:assembleRelease
```

產出 APK：`app/build/outputs/apk/release/app-release.apk`

第一次建置會下載 Gradle 與 Android 依賴，需網路。

## 來源

移植參考：<https://github.com/huang1988pioneer/MusicVideoMediaPlayer>
