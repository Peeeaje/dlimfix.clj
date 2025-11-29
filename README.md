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
dlimfix --list file.clj
```

## Usage

### List candidates

```bash
clj -M:run --list file.clj
```

Output:
```
Missing end delimiter: ) (to close '(' at line 1, col 1)
Candidates:
  A1) after line 1, col 12: (defn foo []
  A2) after line 2, col 9: (+ 1 2)
  A3) after line 3, col 0: [EOF]
```

### Fix with specific position

```bash
clj -M:run --fix file.clj -p A2
```

### Preview changes (dry-run)

```bash
clj -M:run --fix file.clj -p A2 --dry-run
```

### Write to different file

```bash
clj -M:run --fix file.clj -p A2 --out fixed.clj
```

### Create backup before overwriting

```bash
clj -M:run --fix file.clj -p A2 --backup file.bak
```

## Options

| Option | Description |
|--------|-------------|
| `--list` | Show candidate positions for missing delimiters |
| `--fix` | Apply fix at specified position |
| `-p, --position ID` | Position ID to fix (e.g., A1) |
| `--dry-run` | Show diff without modifying file |
| `--out FILE` | Write to different file |
| `--backup FILE` | Create backup before overwriting |

## Exit Codes

- `0`: Success
- `1`: Argument error (missing file, unknown ID, etc.)
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
