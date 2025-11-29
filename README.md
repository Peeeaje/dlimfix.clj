# dlimfix

A CLI tool that fixes missing end delimiters in Clojure code.

## Features

- Detects missing `)`, `]`, `}` delimiters
- Shows candidate insertion positions
- Iterative approach: fix one delimiter at a time
- Supports `--dry-run` to preview changes
- Handles strings, comments, and reader macros correctly

## Installation

Requires [Clojure CLI tools](https://clojure.org/guides/install_clojure).

```bash
git clone https://github.com/yourname/dlimfix.clj
cd dlimfix.clj
```

### bbin (Babashka) install

`bbin` が入っていれば、リポジトリから直接インストールできます。

```bash
# GitHub 上のリポジトリから
bbin install git+https://github.com/yourname/dlimfix.clj

# またはローカルクローンから
bbin install .
```

インストール後は `dlimfix` コマンドとして使えます:

```bash
dlimfix file.clj
```

## Usage

### List candidates (default)

```bash
clj -M:run file.clj
```

Output:
```
Missing end delimiter: ) (to close '(' at line 1, col 1)
Candidates:
  1) insert at line 1: (defn foo []|
  2) insert at line 2: (+ 1 2)|
```

The `|` marker shows where the delimiter will be inserted.

### Fix with specific position

```bash
clj -M:run --fix file.clj -p 2
```

### Preview changes (dry-run)

```bash
clj -M:run --fix file.clj -p 2 --dry-run
```

### Write to different file

```bash
clj -M:run --fix file.clj -p 2 --out fixed.clj
```

## Options

| Option | Description |
|--------|-------------|
| `--list` | Show candidate positions for missing delimiters (default) |
| `--fix` | Apply fix at specified position |
| `-p, --position ID` | Position ID to fix (e.g., 1) |
| `--dry-run` | Show diff without modifying file |
| `--out FILE` | Write to different file |

## Exit Codes

- `0`: Success (no missing delimiters, or fix applied successfully)
- `1`: Missing delimiter found, or argument error (missing file, unknown ID, etc.)
- `2`: Parse error (fatal syntax error)

## Development

### Run tests

```bash
clj -M:test
```

### Project structure

```
src/dlimfix/
├── core.clj        # CLI entry point
├── parser.clj      # edamame wrapper
├── candidates.clj  # Candidate position generation
├── fixer.clj       # Text manipulation
└── output.clj      # Output formatting
```

## License

MIT
