# FlowCockpit AI Box — RK3566 / Ubuntu 24.04 / 4 GB

這是對既有完整專案的**追加部署工具**，不刪減原本 BC、D、E、Android 功能。

## 下載與安裝

```bash
sudo apt update && sudo apt install -y git
git clone https://github.com/keys-lll/flowcockpit.git
cd flowcockpit
bash rk3566-full/install.sh
```

若倉庫為 private，使用已獲權限的 GitHub 登入方式。

## 地點資料庫（不包含在 Git 倉庫）

```bash
bash rk3566-full/build-places.sh
```

這會下載台灣 OSM PBF 並建立 `FlowCockpit_Integrated_Demo/bc/data/taiwan.sqlite3`。需要網路、儲存空間與記憶體；RK3566 4GB 可能建庫失敗，此時可在效能較強的電腦建庫再複製回開發板。

## 啟動與測試

```bash
bash rk3566-full/start-full.sh
```

另一終端測試：

```bash
curl http://127.0.0.1:8000/api/connectivity
hostname -I
```

Android 裝置須與 RK3566 位於同一可互通區域網路；App 使用板子的實際 IP 與 8000 埠。請檢查 Android `MainActivity.kt` 中的 `AI_BOX_HOST`，不要把模擬器用的 `10.0.2.2` 當成板子的 IP。

## 完整 AI 功能

- Ollama：另行安裝官方 Linux ARM64 版本並下載既有專案要求的模型，4GB RAM 可能非常慢。
- faster-whisper / CTranslate2：ARM64 套件可能缺乏預編譯 wheel，安裝需要實機檢驗。
- 雲端 AI：將 `ANTHROPIC_API_KEY` 設在 `FlowCockpit_Integrated_Demo/.env`，勿提交金鑰。
- 無完整地點資料庫、模型檔與密鑰時，不能視為全功能可用。
- 此版本尚未在你的 RK3566 實機驗證。

請不要把 AI Box 服務埠直接暴露在公網。
