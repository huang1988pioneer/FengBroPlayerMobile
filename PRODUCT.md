# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

在手機或平板上看本機影片、聽音樂、或播 YouTube / Bilibili / HLS 串流的人。單手或躺著操作，多數時間視線在畫面上，不想找桌面播放器那排小按鈕。

## Product Purpose

風哥播放器是 Android 本機＋串流播放器。成功標準是：打開檔案或網址後，用和其他主流手機播放器一樣的手勢就能播、暫停、快轉、調音量與亮度，不必先學一套桌面快捷鍵。

## Positioning

單一播放佇列（清單 / 最近 / 串流）加上 NewPipe 解析網頁影片，介面走手機播放器慣例，而不是把桌面 KM / PotPlayer 控制列搬到手機。

## Operating Context

- 深夜或室內暗環境為主，深色畫面。
- 直向瀏覽與開啟；看影片時常轉橫向全螢幕。
- 從檔案管理員、分享選單或 App 內開啟。
- 系統返回鍵必須有用：先關清單／設定，再退出橫向全螢幕。

## Capabilities and Constraints

- 本機音訊／影片（SAF）、資料夾掃描、http(s)／HLS／DASH、YouTube／Bilibili 網頁解析。
- Media3 ExoPlayer、前景 MediaSession 媒體通知／鎖定畫面、影片子母畫面、zh-TW 文案。
- 強調色鎖定 `#3B9EFF`；名稱「風哥播放器」。
- **假設（使用者點名參考、確認回合未回傳細部）：** 操作模型對齊 YouTube、Bilibili、MX Player、KMPlayer、Bubble Player、nPlayer、VLC for Android 的共同習慣：畫面滿版、點一下顯示／隱藏控制、雙擊左右快轉、左右滑進度、左側亮度右側音量、長按二倍速、清單由底部滑出。

## Brand Commitments

- 中文名稱：風哥播放器。
- 參考產品（操作慣例，不抄品牌）：YouTube、Bilibili、MX Player、KMPlayer、Bubble Player、nPlayer、VLC for Android。
- 不複製上述產品的 logo 或專有皮膚。

## Evidence on Hand

- 既有 Compose 播放殼、Media3 引擎、播放清單語意在 `core/`。
- 無使用者研究或行銷素材；不可虛構下載量或評分。

## Product Principles

1. 手勢是主控，按鈕是確認。
2. 播放中畫面是主角；控制層蓋在影片上，不永久佔高。
3. 和主流手機播放器同一套肌肉記憶，不發明新手勢。
4. 進階功能（字幕、速度、停止、資訊）放「更多」，不擠主列。
5. 空狀態先讓人打開東西，不要先丟一排桌面工具。

## Accessibility & Inclusion

觸控目標至少 48dp。系統返回與預測返回必須有效。字級用 sp。
