# 題庫通 QuizMaster

類似 Quizlet 的 Android 選擇題練習 App（Kotlin + Jetpack Compose + Room，資料全部存在手機本地）。

## 功能

- **題庫管理**：建立多個題庫，手動新增／編輯／刪除題目
- **單選與多選**：單選點一下即作答；多選勾選後按「提交」，要全部選對才算對
- **選項隨機**：每次作答選項順序都會重新打亂（題目順序也可選擇隨機）
- **錯題本**：答錯自動加入；連續答對 2 次自動移出；可單獨練習錯題，或跨題庫總複習
- **作答紀錄**：每題記錄作答次數、答錯次數、上次對錯；每次練習的成績與逐題作答內容都可回顧
- **練習模式**：全部題目／只做沒做過的／只做錯題；結束後可一鍵「重做答錯的題」
- **批次匯入／匯出**：貼上文字或開啟 .txt 一次匯入多題；可匯出成文字檔備份或分享
- 支援深色模式

## 匯入格式

每題之間空一行：

```
1. 台灣最高的山是哪一座？
A. 雪山
*B. 玉山
C. 合歡山
解析: 玉山主峰海拔約 3,952 公尺。

2. [多選] 下列哪些是質數？
A. 9
*B. 13
*C. 29
D. 21

3. 水的化學式是？
A. CO2
B. H2O
答案: B
```

- 正確選項前加 `*`，或另起一行寫 `答案: B`（多選寫 `答案: BC`）
- 題目前可加 `[多選]` / `[單選]`；不寫的話，有多個正確答案就自動視為多選
- 選項開頭可用 `A.`、`A、`、`A)`、`A：` 等
- `解析:` 可省略

## 如何取得 APK

### 方法一：Android Studio（推薦）
1. 安裝 [Android Studio](https://developer.android.com/studio)
2. File → Open → 選擇本資料夾，等待 Gradle 同步完成（第一次會下載依賴，需要幾分鐘）
3. 手機開啟「開發人員選項 → USB 偵錯」並接上電腦，按 ▶ Run 直接安裝
4. 或用 Build → Build App Bundle(s) / APK(s) → Build APK(s) 產生 APK 檔

如果 Android Studio 提示升級 Android Gradle Plugin，可以直接接受。

### 方法二：GitHub 自動編譯（不用安裝 Android Studio）
1. 在 GitHub 建立一個新的 repository，把整個資料夾上傳（包含 `.github` 資料夾）
2. 上傳後會自動開始編譯（Actions 分頁），約 5–10 分鐘
3. 完成後點進該次執行，在下方 Artifacts 下載 `QuizMaster-apk`，解壓後把 APK 傳到手機安裝
   （手機需允許「安裝未知來源的應用程式」）

> release 版目前用 debug 金鑰簽名，方便自己安裝。若要上架 Google Play，請在 `app/build.gradle.kts` 換成自己的簽名金鑰。

## 專案結構

```
app/src/main/java/com/quizmaster/app/
├── data/            Room 資料庫：題庫、題目、練習紀錄、逐題作答
│   └── QuizRepository.kt   作答判分、錯題本規則、統計更新
├── util/QuestionParser.kt  文字匯入／匯出
└── ui/
    ├── home/        首頁（題庫列表、錯題本入口）
    ├── set/         題庫詳情（統計、練習模式、題目列表）
    ├── edit/        新增／編輯題目
    ├── importer/    批次匯入
    ├── quiz/        作答畫面與結果（QuizViewModel 負責打亂選項）
    ├── wrong/       錯題本
    └── history/     作答紀錄與每次練習的明細
```

想調整「連續答對幾次移出錯題本」，改 `data/Entities.kt` 的 `WRONG_BOOK_CLEAR_STREAK`。
