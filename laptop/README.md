# FlowCockpit AI Box — Ubuntu 26.04 筆電部署

保留原始 BC、D、E、Android 全部功能；不使用 RK3566。安裝腳本使用獨立 Python 3.12（透過 pipx + uv），不變更 Ubuntu 26.04 系統 Python。Windows 筆電請參考 `FlowCockpit_Integrated_Demo/README.md`。

## 首次安裝

```bash
git clone https://github.com/keys-lll/flowcockpit.git
cd flowcockpit
bash laptop/install-ubuntu.sh
```

安裝 [Ollama](https://ollama.com/download/linux) 後，確認 Ollama 服務正在執行：

```bash
ollama pull llama3.2:1b
ollama list
```

建立台灣 OSM 地點資料庫（需網路、磁碟空間，第一次較久）：

```bash
bash laptop/build-places-ubuntu.sh
```

`taiwan.sqlite3` 不在 GitHub 倉庫內，建置後位於 `FlowCockpit_Integrated_Demo/bc/data/`。

## 啟動

```bash
bash laptop/start-ubuntu.sh
```

另一個終端機測試：

```bash
curl http://127.0.0.1:8000/api/system/status
curl http://127.0.0.1:8000/api/connectivity
hostname -I
```

D 服務監聽筆電的 8000 埠；BC/E 只監聽本機 8001/8002。筆電與 Android 必須連同一個可互通的 Wi-Fi。Android 實機使用筆電的區域網路 IP（如 `192.168.1.100:8000`），不是模擬器位址 `10.0.2.2:8000`。新版 Android App 在右側麥克風欄位提供「連線設定」，可輸入筆電 IP:8000，按「儲存並連線」後會保存設定並重連，不需要每次 IP 變動就重新編譯。**必須先安裝包含此修改的新 APK**；舊 APK 仍可能使用固定的 10.0.2.2。

雲端 E 為選用功能。若要使用 Claude，可在 repo 根目錄 `.env` 或 `FlowCockpit_Integrated_Demo/.env` 設定 `ANTHROPIC_API_KEY=...`；既有 shell 環境變數優先，否則整合版目錄的 `.env` 優先於 repo 根目錄。不要提交金鑰；沒有 Key 也可啟動 E 並以離線模式運作。啟動後可查看 `http://筆電IP:8000/api/system/status` 確認 E 是否為 `ready`（狀態不會回傳金鑰）。

預設 Whisper 是 `small`，可在 `.env` 設 `WHISPER_MODEL=base` 或 `tiny` 以減輕負載，但中文辨識率可能下降。

Ubuntu 26.04 使用隔離 Python 3.12，降低 faster-whisper/CTranslate2 與新系統 Python 的相容性風險；實際安裝仍取決於套件是否提供對應 wheel，**尚未在你的筆電實測**。請勿把 8000 埠直接對公網開放。
