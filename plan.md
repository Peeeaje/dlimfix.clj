This CLI fixes missing end delimiters in Clojure code. Scope: single file, 非対話。曖昧ケースは候補を列挙し、ユーザーが ID を指定して再実行する方式。実装委譲前提で、詳細仕様を記す。

## 非目標
- マルチファイル/ディレクトリ再帰対応はしない。
- インタラクティブTUI/REPLプロンプトは作らない。
- フォーマッタ機能（インデント調整等）は行わない。括弧挿入のみ。

## CLI インターフェイス（確定）
- `dlmfix --list file.clj`
  - 不足閉じ括弧の候補を列挙し終了。ファイルは変更しない。
  - exit 0: 成功（候補あり/なし）。
  - exit 2: パース不能（文字化けなど致命的エラー）。
- `dlmfix --fix file.clj --position A1,B2`
  - 指定IDの位置に `)` を挿入し、デフォルトは上書き保存。
  - exit 0: 成功。exit 1: 引数不整合（ID不足/未知ID）。exit 2: パース不能。
- 共通オプション
  - `--dry-run` : 書き換えず差分を stdout（unified diff, -u形式）に表示。
  - `--out out.clj` : 修正結果を別ファイルに保存。指定時は元ファイルは触らない。
  - `--backup file.bak` : 上書き前にコピーを作成。`--out` 指定時は無視。
  - `--encoding utf-8` 固定（他はサポート外と明記）。

## 候補IDのルール
- 開始デリミタ検出順に A,B,C… を割り当て（ソース出現順）。
- その開始デリミタに対する閉じ位置の候補を 1,2,3… で付番 → `A1`, `A2`。
- 同値候補の束ね方: 開デリミタから末尾までのサブフォームが閉じられる位置が同一なら 1 候補に統合。判定は「親チェーンの end-row/end-col が一致するか」で行う。
- `--fix` では欠落数と同数の ID 指定が必須。数が合わない/未知IDはエラー(exit 1)。

## 内部データモデル（案）
- `ParsedForm` : {:start-row :start-col :end-row :end-col :delim "("|"["|"{"}
- `Missing` : {:start-id "A" :starts-at {:row :col} :candidates [Candidate]}
- `Candidate` : {:idx 1 :insert-pos {:row :col :offset <abs-index>} :context-line "(+ x 2)"}
- `Plan` : {:missings [Missing] :source string}

## 解析ロジック（edamame 使用）
1. ファイルを文字列で読み込む（UTF-8固定）。
2. `edamame/parse-string-all` に `{:all true :row-key :row :col-key :col :end-location true}` を指定。
3. 成功時: 構文木を走査し、開閉スタックを維持。入力末尾でスタックに残った開デリミタが「不足」。
4. 失敗時: `ex-data` の `:edamame/expected-delimiter` とパーサが残したスタックを参照。致命エラー（不正文字等）は exit 2。
5. 候補位置の算出:
   - 各不足開デリミタについて、そのサブツリーで最後に消費されたトークンの end-row/end-col を基本候補とする。
   - 親チェーンで end-row/end-col が同じものは同値とみなし、1候補に束ねる。
   - 同値でない複数の閉じ位置が存在する場合（曖昧ケース）、Candidate を複数生成。

## 挿入位置の扱い
- 行・列を絶対オフセット（0始まり）に変換し `offset` を保持。改行は1文字とカウント。
- 複数挿入は `offset` 降順に適用して位置ずれを防ぐ。
- 既存の閉じデリミタ直後に挿入しても構文が崩れないよう、常に「対象トークンの末尾直後」に挿入する。

## 出力フォーマット (`--list`)
```
Missing end delimiter candidates:
A (start line 1 col 1):
  1) after line 4 col 15: (+ x y)
B (start line 2 col 3):
  1) after line 5 col 7: (println "done")
```
- `line/col` は 1 始まり。`context-line` は該当行をそのまま抜粋（タブはそのまま）。
- 候補が 0 の場合は `No missing end delimiters found.` とだけ出力。

## 差分出力 (`--dry-run`)
- `diff -u` 形式を自前で生成（既存ファイル vs 修正後文字列）。
- 変更がない場合は空出力で exit 0。

## バックアップ
- `--backup path` 指定時のみ作成。`cp file path` 相当（上書き可）。`--out` と併用時はバックアップせず警告なしで無視。

## エッジケース方針
- 文字列/コメント/キーワード/シンボル名内の括弧はカウントしない（edamameに任せる）。
- リーダーマクロ `#()`, `#'`, `#?`, `#?@`, `#=` などは edamame の通常処理に従う。
- `#_` 無効化フォーム: 無効化されたフォーム内の括弧は無視される想定。パーサ結果を信頼。
- EOF 直前の未完文字列など、致命エラーは「修復不可」として exit 2。

## ビルド/実行
- deps.edn に `borkdude/edamame {:mvn/version "1.4.32"}` を追加。
- エントリ: `-m dlimfix.core` などシンプルな `-main`。
- 単一バイナリ配布は bb/uberscript で後続対応可（初版は `clj -M -m dlimfix.core ...`）。

## テスト方針（TDD）
- ユニット
  - 開閉スタックから Missing を生成する関数。
  - 同値候補の束ねロジック（親チェーン end-row/end-col 比較）。
  - 挿入オフセットの降順適用で意図通りの結果になるか。
- 統合（ゴールデン）
  - `--list` 出力と期待テキストを比較。
  - `--fix --position ...` の生成結果を期待ファイルと比較。
- ケースセット
  1) 単欠落: 末尾 `)` 抜け。
  2) 複数欠落: ネスト2段で2個欠落。
  3) 曖昧2択: `(let [x] (+ x 1) (println x)`
  4) 曖昧3択: 深いネストで同値候補を含む。
  5) 同値束ね: `(+ x y)` 直後と親フォーム直後が等価。
  6) コメント内括弧: `; )` を無視する。
  7) 文字列内括弧: `"foo)"` を無視する。
  8) `#_` 無効化フォーム内の括弧を無視。
  9) リーダーマクロ `#()` を含むケース。
 10) 変更なし入力: `--fix` で何も変えずに終了すること。

## 実装ステップ指針
1) deps.edn 追加、`core` 名前空間を用意。
2) 読み込み・パース関数を作成（edamame設定込み）。
3) スタックから Missing/Candidate を生成するロジックを実装。
4) 候補束ね・ID付与・ソート（出現順A,B…）。
5) `--list` コマンド出力整形を実装。
6) 挿入オフセット計算と降順挿入を実装。
7) `--fix` と `--dry-run`/`--out`/`--backup` のI/Oを実装。
8) テストケースを追加し、TDDで順に緑にする。

## 例

before
```clojure
(let [x 1]
  (+ x 2)
```

after
```clojure
(let [x 1]
  (+ x 2))
```

曖昧例（同じ閉じ結果は束ねる）
```clojure
(let [x 1]
  (let [y 2]
    (let [z 3]
      (+ x y))
```
候補表示イメージ
```
Missing end delimiter candidates:
A (start line 1 col 1):
  1) after line 5 col 15: (+ x y)
```
