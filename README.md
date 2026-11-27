# mcport

**Run Minecraft Alpha 1.1.2_01 and Indev 20100223 natively on Android — no launcher, no JVM on the phone.**

mcport converts your own copies of the desktop game jars into one Android APK (pick the version when the app starts). The game's
bytecode is turned into Android dex, and its calls to LWJGL 2, AWT and ImageIO are
redirected to a compatibility layer that draws with EGL + OpenGL ES 1.1. The game's own
code is not changed.

> **Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.**
> This repository contains **no** Mojang code or assets. You need your own legally obtained
> `a1.1.2_01.jar` / Indev jar; an APK built from it contains Mojang code and is for your personal use only —
> do not distribute it.

![Touch controls](docs/controls.png)

**Features**

- Pocket Edition 0.12-style touch controls (D-pad, jump, sneak; split controls like the original game: tap to attack or use/place whatever the crosshair points at, hold to mine)
- The game's original screens, tuned for fingers: tap on release, long-press = right click,
  tap a crafting cell while holding items to place one, snap-to-slot, no accidental drops
- Sound: a software OpenAL mixer (3D position, distance, panning, pitch, streamed music) on Android's AudioTrack —
  bring the sound files from your own launcher install
- Soft keyboard pops up for chat, signs and server addresses
- Fixes two Alpha bugs that matter on phones: random "Comparison method violates its general
  contract!" crashes, and duplicated buttons after leaving the Controls screen
- Microsoft sign-in (device code) for accounts that own Java Edition; multiplayer is locked until you sign in,
  and online-mode servers are joined through today's session server (needs your own approved Azure client ID)
- Skins: pick your own PNG (64x32 or 64x64) on the phone, or get your account's real skin when signed in;
  other players' skins in multiplayer come from the Mojang API (64x64 skins are cut to the old 64x32 layout)
- Version picker: put several versions in one APK; each runs in its own class loader with its own options and saves
- Indev 20100223 works offline: level generation, sounds and Load/Save file (the minecraft.net checks it made are answered locally)
- Desktop test harness: run the same layer on a PC (Xvfb + Mesa) with scripted input and screenshots

**Quick start** (Ubuntu / WSL)

```sh
sudo apt install openjdk-21-jdk android-sdk-platform-23 dalvik-exchange aapt zipalign apksigner libasm-java python3
./build-apk.sh a1.1.2_01.jar,in-20100223.jar minecraft.apk ~/.minecraft   # one jar is fine too; sounds are optional
```

Licensed under the MIT License (mcport code only). Full documentation below is in Traditional Chinese.

---

# mcport：Minecraft Alpha / Indev 原生 Android 移植轉接層

把桌面版 Minecraft Alpha、Indev 的 jar 直接打包成 Android APK，可以把多個版本放進同一個 APK，開啟時選版本。不需要啟動器，也不需要在手機上跑 JVM：
遊戲的位元碼會轉成 Android 的 dex，原本呼叫的 LWJGL 2、AWT、ImageIO 改接到本專案的轉接層，
畫面由 EGL + OpenGL ES 1.1 繪製。

> **本專案不含任何 Mojang 的程式碼或素材。** 你必須自己準備遊戲 jar；
> 用它打包出來的 APK 含有 Mojang 的程式碼，只能自己使用，不能散布。

目前測試過的版本：**Alpha 1.1.2_01**（`a1.1.2_01.jar`，客戶端）、**Indev 20100223**（`in-20100223.jar`）。

---

## 一、準備環境

打包要在 Linux 上進行。Windows 可以用 **WSL（Ubuntu）**，步驟完全相同。
以下以 Ubuntu 24.04 為準：

```sh
sudo apt update
sudo apt install openjdk-21-jdk android-sdk-platform-23 dalvik-exchange \
                 aapt zipalign apksigner libasm-java python3
```

若要跑電腦端測試（第四節），再加裝：

```sh
sudo apt install liblwjgl-java xvfb libgl1-mesa-dri
```

## 二、取得遊戲 jar

用你自己的正版帳號，從官方啟動器下載 Alpha 1.1.2_01，再到
`.minecraft/versions/a1.1.2_01/a1.1.2_01.jar` 複製出來。
Indev 同理（官方啟動器勾選「顯示舊版本」後可下載 `in-20100223`）。
檔名裡的日期（20100223）會被拿來當版本名稱，請保留。

## 三、打包 APK

```sh
unzip mcport-source-kit.zip -d mcport
cd mcport
./build-apk.sh $HOME/a1.1.2_01.jar,$HOME/in-20100223.jar minecraft.apk ~/.minecraft
```

第一個參數可以是一個 jar，也可以是用逗號隔開的多個 jar（逗號前後不要有空格；逗號後面不能用 `~`，請用 `$HOME`）。
放了多個版本時，App 開啟會先顯示「選擇版本」畫面，上次玩的版本會標「（上次）」；
要換版本就把 App 關掉（從最近使用的 App 滑掉）再開。

第三個參數（音效資料夾）可以省略，省略的話遊戲沒有聲音，詳見下方「音效」。

腳本會依序執行七個步驟：

| 步驟 | 內容 |
|---|---|
| 1 | `tools/Remap.java` 把 jar 裡的 `org.lwjgl`、`java.awt`、`javax.imageio`、`javax.sound`、`java.applet` 引用改成 `mcshim.*` |
| 2 | 以 Android API 23 為基準編譯轉接層 |
| 3 | `tools/Check.java` 檢查遊戲用到的每個類別、方法、欄位在轉接層裡都存在（應顯示 `OK`） |
| 4 | 編譯 Android 端程式（Activity、觸控、EGL） |
| 5 | `dx` 轉成 dex：轉接層與 Android 程式一份，每個遊戲版本各一份（版本之間類別名稱會重複，所以各用自己的 class loader 載入） |
| 6 | `aapt` 打包；每個版本的 dex 和圖片打成 `assets/games/<版本>.jar`；有給音效資料夾的話，音效放進 `assets/mcres/`（所有版本共用） |
| 7 | `zipalign` 對齊，再用自動產生的 `build/debug.keystore` 簽名 |

第一次打包會自動產生簽名金鑰並存在 `build/debug.keystore`。
**以後更新 APK 要用同一把金鑰**，否則手機會拒絕覆蓋安裝，只能先移除舊版（會刪除存檔）。

### 安裝到手機

把 APK 傳到手機上開啟，允許「安裝未知來源的應用程式」即可。也可以用 adb：

```sh
adb install -r minecraft.apk
```

需求：Android 5.0 以上，支援 OpenGL ES 1.1（幾乎所有手機都支援）。

### 音效

音效檔不在遊戲 jar 裡（當年是另外從已關閉的伺服器下載），而且同樣屬於 Mojang，所以本專案不附帶。取得方式：

1. 用官方啟動器**實際啟動一次** Alpha 1.1.2_01，啟動器會下載舊版音效
2. 打包時把 `.minecraft` 資料夾當第三個參數：`./build-apk.sh 遊戲.jar 輸出.apk ~/.minecraft`

腳本會自動在裡面找 `resources/`（舊版啟動器）或 `assets/`（新版啟動器，依 `indexes/pre-1.6.json` 還原檔名），
只收集 `sound/`、`newsound/`、`music/`、`newmusic/`、`streaming/` 底下的音效。
App 第一次開啟時會把音效複製到遊戲資料夾，畫面會顯示 `Installing sounds... %`，之後就不會再複製。

也可以不打包，直接把音效資料夾複製到手機的 `Android/data/io.github.mcport.alpha/files/.minecraft/resources/`。

設定裡的 Music、Sound 滑桿可以調音量；App 切到背景時聲音會自動暫停。

Indev 也用同一份音效（Indev 的音效名稱和 Alpha 相同）。Indev 會一直播背景音樂，沒有音樂檔時
會自動放一段 30 秒的靜音代替，不然遊戲每一刻都會出錯。

**背景音樂：**放在 `music/`（例如 `calm1.ogg`、`calm2.ogg`、`calm3.ogg`）。原版要隨機等 0～10 分鐘才播第一首，
手機上常常還沒聽到就關掉了，所以每次開遊戲的第一首改成 30～90 秒內開始，之後照原版的間隔。

### 皮膚

在「選擇版本」畫面點「皮膚」那一行：

- **自訂皮膚：**選一張手機裡的 PNG（64×32 舊格式或 64×64 新格式都可以，高解析度皮膚也行）。只有你自己看得到；
  別的玩家看到的是你正版帳號的皮膚
- **正版皮膚：**登入正版帳號、沒有選自訂皮膚時，會自動用帳號在 minecraft.net 設定的皮膚
- **多人遊戲：**其他玩家的皮膚會用他們的名字向 Mojang 查詢
- 沒登入、也沒選自訂皮膚時是預設的 Steve
- 新格式（64×64）的下半部（左手左腳分開的部分、第二層外套）舊版本畫不出來，會用上半部；細手臂（Alex）皮膚手臂會略寬

Alpha 和 Indev 共用同一張自訂皮膚。要第三人稱看自己：Alpha 按 F5（藍牙鍵盤）。

### 正版登入與多人遊戲

**沒有登入正版帳號就不能玩多人遊戲**：點標題畫面的 Multiplayer 會被擋下，跳出「需要登入」對話框。

登入方式（Microsoft 裝置代碼登入，App 裡不會輸入密碼）：

1. 在「選擇版本」畫面下方點「未登入正版帳號」，或在被擋下的對話框點「登入」
2. 點「複製代碼並開啟網頁」，瀏覽器會開 microsoft.com/link，貼上代碼
3. 用**擁有 Minecraft: Java 版**的 Microsoft 帳號登入並允許，回到 App 會自動完成

登入後遊戲裡的名字就是你的正版玩家名稱；加入正版驗證（online-mode）的伺服器時，
App 會代替當年已關閉的 minecraft.net，改用現在的 Mojang session server 驗證。
登入資料只存在 App 的私人資料夾，遊戲本身拿不到權杖。要登出就在「選擇版本」畫面點自己的名字。
只有一個版本的 APK 不會顯示選擇畫面，這時從 Multiplayer 的對話框登入。

**打包時要提供你自己的 Azure 應用程式 ID**（Mojang 規定第三方程式必須自己申請，不能借用別的啟動器的）：

1. 到 [Azure 入口網站](https://portal.azure.com) → Microsoft Entra ID → 應用程式註冊 → 新增註冊，
   帳戶類型選「**僅限個人 Microsoft 帳戶**」
2. 建好後到「驗證」，把「**允許公用用戶端流程**」設為「是」
3. 填 Mojang 的申請表 <https://aka.ms/mce-reviewappid>，送出應用程式（用戶端）ID，等待核准
   （沒核准前登入會停在「沒有獲准使用 Minecraft API（403）」）
4. 把 ID 寫進專案資料夾的 `ms-client-id.txt`（已在 `.gitignore`，不會上傳），或打包前設定
   `export MCPORT_MS_CLIENT_ID=你的ID`，再執行 `build-apk.sh`。打包訊息會顯示 `Microsoft sign-in: on`

沒有在打包時提供 ID 也沒關係：在 App 裡點登入，會跳出「設定登入」，直接貼上應用程式 ID 就能用
（存在手機裡，登出也不會清掉；之後想換可以在錯誤訊息點「修改應用程式 ID」）。
ID 貼錯、沒開公用用戶端流程、還沒被核准（403）都會用中文說明。

注意：Alpha 時代的伺服器軟體驗證玩家用的是已關閉的舊網址，所以現在大多數 Alpha 伺服器是非正版驗證（offline-mode）；
正版驗證只對改用新 session server 的伺服器（或透過 ViaProxy 之類的代理）有效。

### 存檔位置

- Alpha：`Android/data/io.github.mcport.alpha/files/.minecraft/saves/`（和舊版 APK 相同，更新後存檔還在）
- Indev：`Android/data/io.github.mcport.alpha/files/versions/indev-20100223/.minecraft/saves/*.mclevel`

每個版本的設定和存檔分開放，互不影響。

移除 App 會一併刪除存檔，移除前請先備份。

## 四、電腦端測試（不用手機）

轉接層也能接到電腦的 OpenGL 上執行，用腳本操作遊戲並截圖，方便修改後快速驗證：

```sh
./run-desktop-test.sh ~/a1.1.2_01.jar
# 截圖會存在 shots/
```

一次測一個版本（Indev 也一樣，換成 Indev 的 jar）。加上 `MCDESK_SEPARATE=1` 會像手機一樣用獨立的 class loader 載入遊戲；
`MCDESK_HOME=資料夾` 可以指定遊戲資料夾（建議 Alpha 和 Indev 分開）。

也可以自訂操作腳本（用分號分隔）：

```sh
./run-desktop-test.sh ~/a1.1.2_01.jar "wait 60; click 427 236; wait 10; click 427 100; wait 200; look 300 0; wait 30; shot shots/a.png"
```

| 指令 | 作用 |
|---|---|
| `wait N` | 等 N 個畫格 |
| `sleep MS` | 等 MS 毫秒 |
| `shot 檔名.png` | 截圖 |
| `click X Y` | 在視窗座標（左上為原點，視窗 854×480）點左鍵 |
| `key K` / `hold K` / `release K` | 按一下／按住／放開 LWJGL 鍵碼 K（例：17=W、57=空白、23=I、1=Esc） |
| `look DX DY` | 轉動視角（滑鼠位移量） |
| `mdown B` / `mup B` | 按住／放開滑鼠鍵（0=左、1=右） |
| `give ID 數量` | 把物品直接放進背包（例：`give 17 4` 給 4 個原木） |
| `counts` / `dump` / `screen` | 印出背包數量／所有配方／目前開的介面 |
| `closebtn` | 印出關閉按鈕的位置 |
| `kind` | 印出目前畫面類型（0 無、1 一般、2 打字、3 背包類） |
| `tapgui X Y [long]` | 模擬手機在背包類畫面點一下（加 long 為長按） |
| `move X Y` | 移動滑鼠游標 |
| `music` | 立刻開始播背景音樂 |
| `pig` / `turn 偏航 俯仰` / `findpig` / `taplook` / `pigstat` | 在前方放一隻豬／轉動視角／從畫面找出豬的位置／模擬手機在遊戲畫面點一下（看準心）／印出豬的數值（Alpha 專用） |
| `aim` | 印出準心是否對著生物 |
| `file 名稱` | 下一次 Indev 的 Load file / Save file 對話框回答這個檔名 |

測試世界存在 `build/mchome/`。

聲音可以錄成檔案檢查（電腦端沒有喇叭）：
`MCDESK_JAVA_OPTS="-Dmcdesk.audio=/tmp/audio.raw" ./run-desktop-test.sh 遊戲.jar "..."`，
輸出是 44.1 kHz 立體聲 16-bit 的原始 PCM。測試用的音效可以放在 `build/mchome/.minecraft/resources/`。

## 五、操作方式

### 遊戲中（PE 0.12 風格觸控）

- **十字鍵（左下）：**移動，手指可以在按鈕間滑動；往前走時會出現兩個斜向按鈕
- **十字鍵中間：**蹲下，點一下切換開關
- **右下角 ^：**跳躍，按住會連續跳（離右邊緣稍有距離，拇指比較好按）
- **畫面其他地方：**拖曳轉視角；按住不動＝挖掘（對準畫面中央準心）
  - **點一下（分離控制，同原版／PE「分離控制」）：**一律對準畫面中央準心——準心對著生物＝攻擊，否則＝使用／放置；判定和距離完全用遊戲原本的
- **熱鍵列：**點一下選格子，按住約 0.5 秒丟出物品
- **熱鍵列右邊「⋯」：**打開背包；**右上角 ‖：**暫停；**返回鍵：**Esc

### 原版介面（背包、工作台、箱子、熔爐、選單）

介面都是遊戲原本的樣子，只針對手指操作做了調整：

- **關閉按鈕 ×：**只加在原本沒有返回按鈕的畫面：背包、工作台、箱子、熔爐（× 在視窗右上角外側）和聊天（螢幕右上角）。
  點它等於按 Esc。設定、按鍵設定、告示牌已經有 Done，暫停、載入、死亡、標題畫面也都沒有 ×

- **背包類畫面：**
  - 點一下＝左鍵（拿起／放下整疊），**手指放開時才算**，可以先滑到正確的格子再放開
  - 長按約 0.45 秒＝右鍵（拿一半／放 1 個）；或第一指按著、第二指點一下
  - **拿著材料點合成表任何一格＝只放 1 個**（背包的 2×2 和工作台的 3×3 都適用）
  - 點到格子之間的縫會自動對準最近的格子
  - 拿著東西時點在視窗邊緣附近不會把整疊丟到地上（要丟請點離視窗遠一點的地方）
  - 游標會跟著手指，看得到選中的格子和手上的物品
- **一般選單：**按鈕按下即觸發，滑桿可以直接拖
- **自動鍵盤：**聊天、編輯告示牌、輸入伺服器位址（Indev：輸入世界名稱）時，手機鍵盤自動跳出，離開後收起
- 支援藍牙／USB 鍵盤

### Indev 20100223

觸控操作和 Alpha 一樣（I 鍵＝「⋯」開背包，沒有蹲下）。

- **Generate new level…：**選類型、形狀、大小、主題後 Create
- **存檔：**暫停選單 → Save level.. → 點五個格子其中一個 → 輸入世界名稱（手機鍵盤會自動跳出）→ Save。
  原版這五格是線上存檔位，這個版本本來按了不會存；現在改成存在手機裡（`saves/slot1.mclevel`～`slot5.mclevel`）
- **讀檔：**標題畫面或暫停選單 → Load level.. → 點有名字的格子
- **回到標題畫面：**暫停選單多了 **Quit to title**（原版 Indev 沒有）。不會自動存檔，要先存
- **Save file… / Load file…：**另外存成／讀取自己取名的 `.mclevel` 檔（手機對話框）
- Indev 原本要連到 minecraft.net 確認登入才肯進入剛產生的世界，這些檢查改成在本機直接回答，不用連網

## 六、專案結構

```
.github/workflows/      GitHub Actions：每次推送自動編譯（不含遊戲檔，所以只檢查程式能否編譯）
docs/                   GitHub Pages 介紹頁（index.html）與操作示意圖
LICENSE                 MIT 授權（只涵蓋本專案程式碼）
build-apk.sh            打包腳本
run-desktop-test.sh     電腦端測試腳本
tools/Remap.java        重新導向 jar 的引用（ASM ClassRemapper），在遊戲主物件建構子插入 Hooks.minecraft = this，
                        把排序呼叫換成 SafeSort，連網呼叫換成 Net；並找出版本名稱
tools/Check.java        檢查轉接層是否涵蓋所有引用
tools/collect_sounds.py 從 .minecraft 收集音效打包進 APK（支援 resources/ 和新版 assets/）
shim/src/mcshim/        轉接層（Android 與電腦共用）
  Platform.java         平台介面
  gl/GLBackend.java     GLES 1.1 子集介面
  lwjgl/opengl/GL11.java  用 GLES 1.1 模擬 GL 1.1：矩陣堆疊、display list、GL_QUADS、GL_CLAMP
  lwjgl/...             Display、Keyboard、Mouse、OpenAL（空殼）等
  awt/、imageio/        BufferedImage、Graphics、ImageIO 等子集
  sound/                javax.sound 空殼
  Hooks.java            遊戲執行緒與 UI 之間的橋樑（每幀執行排隊的工作）
  Launch.java           啟動任一版本：Alpha 用 Minecraft.main，Indev 用當年網頁上的 applet 方式
  Skins.java            皮膚：自訂 PNG 或用 Mojang API 查正版皮膚，64×64 轉成舊的 64×32
  IndevLevels.java      Indev 的五個存檔格改存本機、暫停選單加 Quit to title（Remap 在遊戲裡插入呼叫）
  Net.java              代替已關閉的 minecraft.net 回答 Indev 的登入檢查、資源清單、線上存檔清單，
                        並把 Alpha 的 joinserver.jsp 轉給已登入的帳號（現在的 session server）
  swing/                Indev 啟動失敗時的訊息框 → 錯誤畫面
  craft/                測試用：讀配方、讀寫背包（電腦端測試指令 give / inv 等）
  ui/GuiTouch.java      原版介面的觸控判斷（合成格放 1 個、對準格子、防誤丟）與 Alpha 按鈕重複錯誤的修正；
                        內含 Alpha 1.1.2_01 與 Indev 20100223 兩套混淆名稱
  audio/                軟體 OpenAL 混音器（位置、距離衰減、左右聲道、音高、音樂串流）
  lwjgl/openal/         遊戲音效庫呼叫的 OpenAL 函式，接到上面的混音器
  SafeSort.java         取代遊戲排序，避免 Alpha 的比較器在新版 Java／Android 上崩潰
android/                Android 端：MainActivity（含選擇版本畫面、Indev 檔案對話框、登入畫面與多人遊戲鎖）、
                        Auth（Microsoft → Xbox → Minecraft 登入）、Games（各版本的載入與資料夾）、EGL、AudioTrack 聲音輸出、音效安裝（SoundInstaller）、觸控（TouchOverlay）、
                        自動鍵盤（TextInput）、鍵盤對應、Manifest、圖示
desktop/                電腦端測試平台（真的 LWJGL 2）
```

## 七、已知限制

- 挖掘／攻擊對準畫面中央準心，不是手指點的位置（同原版的分離控制）
- 合成格放 1 個、防誤丟、自動鍵盤等觸控調整依賴混淆名稱（`shim/src/mcshim/ui/GuiTouch.java`，目前有 a1.1.2_01 和 in-20100223），
  其他版本可以啟動，但這些功能會退回一般點擊
- 多人遊戲沒有實際連線測試過；正版登入需要自己申請並核准的 Azure 應用程式 ID
- Indev 沒有多人遊戲介面
- 目前驗證過 Alpha 1.1.2_01 和 Indev 20100223；其他版本如果用到不同的 API，`Check` 步驟會列出缺少的部分
- 換版本要關掉 App 重開（每個版本各自載入，同一次執行只能跑一個）
- 截圖（F2）不會存檔
- 鍵盤只能打遊戲字型支援的英數字與符號，中文會被遊戲忽略

## 八、常見問題

**`[3/7]` 沒有顯示 OK？** 代表這個版本用到了轉接層沒有的 API，訊息會列出缺少的類別或方法，補進 `shim/src/mcshim/` 就行。

**手機上閃退？** App 會在畫面上顯示錯誤報告，可以直接選取複製。也可以用 `adb logcat -s MCPort` 查看紀錄。

**打包時出現 `dx: not found`？** 腳本會自動改用 `/usr/lib/android-sdk/build-tools/debian/dx`，請確認已安裝 `dalvik-exchange`。

**電腦端測試出現 `UnknownHostException`？** 這是正常的：遊戲想下載已經關閉的舊資源伺服器，失敗後會改用本機 `resources/` 裡的音效，不影響遊玩。
