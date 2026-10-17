# FlowCockpit AI Box — 筆電部署（Ubuntu Linux）

保留原始 BC、D、E、Android 全部功能；不使用 RK3566。Windows 筆電請參考 `FlowCockpit_Integrated_Demo/README.md`。

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

D 服務監聽筆電的 8000 埠；BC/E 只監聽本機 8001/8002。筆電與 Android 必須連同一個可互通的 Wi-Fi。Android 實機使用筆電的區域網路 IP（如 `192.168.1.100:8000`），不是模擬器位址 `10.0.2.2:8000`。目前 `A1/app/src/main/java/com/example/testapp/MainActivity.kt` 的 `AI_BOX_HOST` 是編譯期常數，需依筆電 IP 修改後重新建置 APK。

雲端 E 為選用功能。若要使用 Claude，在 `FlowCockpit_Integrated_Demo/.env` 放入 `ANTHROPIC_API_KEY=...`，不要提交金鑰；沒有 Key 也可啟動 E 並以離線模式運作。

預設 Whisper 是 `small`，可在 `.env` 設 `WHISPER_MODEL=base` 或 `tiny` 以減輕負載，但中文辨識率可能下降。

若使用 Ubuntu 26.04 或較新的 Python，請先檢查 `python3 --version` 以及依賴安裝是否相容；此流程尚未在你的筆電實測。請勿把 8000 埠直接對公網開放。
