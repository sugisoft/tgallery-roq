# T-Gallery

Instagram の投稿 20 枚を表示する静的サイトです。Roq で生成し、GitHub Pages で公開します。カスタムドメインは https://t-gallery.sugit.jp/ を Pages に設定します。

## 開発

```bash
./mvnw quarkus:dev
```

http://localhost:8080 で開きます。起動時に Instagram から最大 20 枚をメモリへ取り込み、テンプレートは `cdi:pictures` を参照します。`IG_TOKEN` が必要です。値はログや Git に出しません。

`GALLERY_GA_ID` に Google Analytics の測定 ID（`G-` から始まる値）を入れると、ページに計測タグが出ます。未設定ならタグは出ません。

## テストと生成

```bash
./mvnw test
QUARKUS_ROQ_GENERATOR_BATCH=true ./mvnw package quarkus:run -DskipTests -Dsite.url=https://t-gallery.sugit.jp
```

生成物は `target/roq` です。起動時にメモリへ取った一覧と画像から静的ファイルを作ります。`IG_TOKEN` が空だと失敗します。

画像はメモリ上にあり、静的生成のとき `target/roq` へ書き出します。取得した画像は、短い辺いっぱいに収まる最大の正方形を中央から切り出し、歪ませず 480×480 の WebP にします。

## 公開

ワークフローは Roq 標準の `.github/workflows/deploy.yml` です。`quarkiverse/quarkus-roq@v1.1` がサイトを生成し、`actions/deploy-pages` が GitHub Pages に出します。

1. リポジトリの Settings > Pages で、Source を GitHub Actions にします。
2. Actions secret に `IG_TOKEN` を置きます。計測タグを出すときは `GALLERY_GA_ID` も置きます。値はファイルにもログにも書きません。
3. `main` へ push するとデプロイします。毎日 05:00 UTC にも同じワークフローが動きます。

このプロジェクトは Java 25 でコンパイルするので、ワークフローの `java-version` は `25` です。`site.url` は GitHub Pages の URL です。カスタムドメインを Pages に付けると、その URL が使われます。
