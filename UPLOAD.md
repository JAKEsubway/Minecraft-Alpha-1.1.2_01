# 上傳說明：把 mcport 放上 GitHub

這份說明教你怎麼把轉接層的原始碼公開到 GitHub，又不會把 Mojang 的東西一起傳上去。

## 一、能上傳和不能上傳的東西

| 可以上傳 ✅ | 絕對不要上傳 ❌ |
|---|---|
| `shim/`、`android/`、`desktop/`、`tools/` 的原始碼 | 遊戲 jar（`a1.1.2_01.jar` 或任何 `.jar`） |
| `build-apk.sh`、`run-desktop-test.sh` | 打包好的 APK（`*.apk`）、`*.dex` |
| `README.md`、`UPLOAD.md`、`.gitignore` | `build/` 資料夾（裡面有轉換過的遊戲程式碼和素材） |
| 你自己畫的圖示 `android/res/drawable/icon.png` | `build/debug.keystore`（你的簽名私鑰） |
| | 反編譯出來的遊戲原始碼 |

APK 裡面含有 Mojang 的程式碼，**也不要放進 GitHub Releases**。別人要用的話，請他們照 README 用自己的 jar 打包。

套件附的 `.gitignore` 已經排除了上表右欄的東西。不過它只對 git 指令有效，用網頁上傳時要自己注意（見第三節）。

## 二、用 git 指令上傳（建議）

1. 到 <https://github.com/new> 建立新的 repository，例如 `mcport`。
   **不要**勾選「Add a README」、「.gitignore」或「license」，因為套件裡都已經有了。
2. 在電腦上：

```sh
unzip mcport-github.zip
cd mcport
git init
git add -A
git status          # 上傳前先檢查清單，確認沒有 .jar、.apk、build/
git commit -m "mcport: native Android port layer for Minecraft Alpha"
git branch -M main
git remote add origin https://github.com/你的帳號/mcport.git
git push -u origin main
```

如果你在這個資料夾裡跑過 `build-apk.sh`，`git status` 也不會列出 `build/` 和 APK，因為 `.gitignore` 已經擋掉了。
不過還是建議每次 commit 前都看一下 `git status`。

## 三、用網頁上傳（不用裝 git）

1. 建立 repository（同上）。
2. 在 repository 頁面按 **Add file → Upload files**。
3. 把解壓縮後的資料夾**內容**拖進去。

注意：

- 網頁上傳**不會**套用 `.gitignore`。請用剛解壓縮、**還沒打包過**的乾淨資料夾上傳，
  不要用跑過 `build-apk.sh` 的資料夾（裡面會多出 `build/`、`out/`）。
- `.gitignore` 是隱藏檔（檔名以點開頭）。macOS 的 Finder 預設看不到，拖曳時可能漏掉，
  可以按 `Cmd + Shift + .` 顯示隱藏檔。`.github` 資料夾（自動編譯的設定）也是隱藏的，同樣要記得一起拖進去。漏掉的話，之後在網頁上用 **Add file → Create new file** 補建一個，內容照抄套件裡的版本。

## 四、這個包已經準備好的東西

| 檔案 | 用途 |
|---|---|
| `README.md` | repository 首頁：上方是英文簡介、免責聲明和操作示意圖，下面是完整中文說明 |
| `LICENSE` | MIT 授權，只涵蓋本專案程式碼。署名目前是「mcport contributors」，想改成自己的名字就直接編輯這一行 |
| `docs/index.html` | GitHub Pages 介紹頁（中英文切換），會自動從網址算出 repository 連結，不用修改 |
| `docs/controls.png` | 操作示意圖，全部自己畫的，不含遊戲畫面 |
| `.github/workflows/build.yml` | 每次推送自動編譯（GitHub Actions）。repository 裡沒有遊戲檔，所以只檢查程式能不能編譯，不會產生 APK |
| `.gitignore` | 擋掉遊戲 jar、APK、`build/` 和簽名金鑰 |

### 開啟介紹頁（GitHub Pages）

1. 上傳後到 repository 的 **Settings → Pages**
2. **Source** 選 **Deploy from a branch**
3. **Branch** 選 `main`，資料夾選 `/docs`，按 **Save**
4. 等一兩分鐘，網址會是 `https://你的帳號.github.io/mcport/`

### 自動編譯（GitHub Actions）

上傳後不用設定，到 repository 的 **Actions** 分頁就能看到每次推送的編譯結果，綠色勾勾代表程式能正常編譯。

### 其他建議

- **名稱：**`android/AndroidManifest.xml` 裡的 App 顯示名稱是 `Minecraft Alpha`，自己用沒關係；公開版本想更中性，可以改成 `mcport` 之類的名稱
- **截圖：**README 想放遊戲截圖的話，只放自己實際遊玩的畫面，不要放材質檔或拆出來的圖片

## 五、之後更新

```sh
git add -A
git status
git commit -m "說明這次改了什麼"
git push
```
