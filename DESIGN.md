---
name: 鋒兄播放器
description: Android 滿版播放器，Pro Dark + 手機播放器手勢
colors:
  accent: "#3B9EFF"
  accent-soft: "#2B7FD4"
  accent-glow: "#5CB0FF"
  bg-app: "#0D0D0D"
  bg-stage: "#0D0D0D"
  bg-panel: "#161616"
  bg-card: "#1A1A1A"
  text-primary: "#F0F0F0"
  text-secondary: "#A8A8A8"
  text-muted: "#6E6E6E"
typography:
  title:
    fontFamily: "sans-serif"
    fontSize: "16sp"
    fontWeight: 600
  body:
    fontFamily: "sans-serif"
    fontSize: "13sp"
    fontWeight: 400
  time:
    fontFamily: "sans-serif"
    fontSize: "12sp"
    fontWeight: 500
rounded:
  control: "999px"
  sheet: "14px"
  cover: "16px"
spacing:
  touch: "48dp"
  overlay-pad: "12dp"
components:
  overlay-play:
    backgroundColor: "#66000000"
    textColor: "#FFFFFF"
    rounded: "{rounded.control}"
    size: "76dp"
  scrub-active:
    backgroundColor: "{colors.accent}"
    height: "3dp"
---

# Design System: 鋒兄播放器

## Overview

**Creative North Star: "The phone player you already know"**

播放中畫面是唯一主角。控制層是半透明覆蓋，不是桌面工具列。手勢對齊 YouTube、Bilibili、MX Player、nPlayer、VLC for Android。

**Key Characteristics:**
- Pro Dark 中性深灰，強調色只出現在進度與目前項目
- 控制層點一下出現、播放中約 3 秒消失
- 48dp 觸控目標

## Colors

單一強調色 `#3B9EFF`，只用在進度、目前清單列、主按鈕。

**The One Voice Rule.** 強調色不當背景裝飾。

## Typography

系統 sans。標題 16sp semibold，時間 12sp，啟動頁標題 28sp。

## Layout

- 空狀態：安全區內的啟動頁（開啟檔案為主按鈕 + 最近播放）
- 播放中：影片或封面滿版；頂欄／中央播放／底欄覆蓋
- 清單：窄螢幕底部工作表，寬螢幕右側 320dp
- 全螢幕：橫向 + 隱藏系統列，同一套覆蓋控制
- 子母畫面：只留影片，系統小窗自帶上一首／播放／下一首
- 背景：媒體通知顯示標題與封面，控制與播放器同一套清單

## Elevation & Depth

控制層用上下黑色漸層，不用卡片陰影。HUD 用 `#D9000000` 圓角塊。封面 16dp 陰影。

## Shapes

播放鈕正圓。清單列與啟動頁按鈕 10–14dp。進度條圓角細線。

## Components

### Overlay chrome
點一下切換。播放中自動隱藏。暫停時保持。鎖定時只留解鎖鈕。

### Gestures
單擊顯示／隱藏；雙擊左右 ±10 秒；雙擊中央播放；左右滑進度；左垂直亮度；右垂直音量；長按 2×。

### Playlist sheet
清單／最近／串流。不要在手機上常駐側欄。

## Do's and Don'ts

### Do:
- **Do** 讓畫面自己播，控制層退到後面。
- **Do** 用和主流手機播放器相同的手勢。

### Don't:
- **Don't** 把停止、音量拉條、±10 秒、狀態列放回主畫面。
- **Don't** 讓單擊左右直接跳轉（那會跟顯示控制打架）。
