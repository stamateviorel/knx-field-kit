import argparse
import json
import pathlib
import re
import sys
import time
import urllib.request

SYS_PROMPT = (
    "You are a senior Kotlin 2.x, KNX protocol, and Compose Multiplatform engineer. "
    "Return ONLY valid, concise, directly compilable Kotlin source code inside a single ```kotlin ... ``` block. "
    "Be compact: omit docstrings/comments, never omit imports, never leave TODOs, and strictly match all provided "
    "package names and type signatures."
)


def extract_code(raw: str) -> str:
    text = raw.strip()
    m = re.search(r"```(?:kotlin|kt|xml|gradle)?\s*\r?\n(.*?)\r?\n```", text, flags=re.DOTALL)
    if m:
        return m.group(1).strip()
    if text.startswith("```"):
        lines = text.splitlines()
        if len(lines) >= 2:
            return "\n".join(lines[1:-1]).strip()
    return text


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--prompt-file", required=True)
    parser.add_argument("--out-file", required=True)
    parser.add_argument("--context", nargs="*", default=[])
    parser.add_argument("--model", default="qwen2.5-coder:7b")
    args = parser.parse_args()

    parts: list[str] = []
    if args.context:
        parts.append("### Reference Context Files (Read-Only Contracts)")
        for cf in args.context:
            p = pathlib.Path(cf)
            if p.is_file():
                parts.append(f"#### File: {cf}\n```kotlin\n{p.read_text(encoding='utf-8').rstrip()}\n```\n")

    spec_text = pathlib.Path(args.prompt_file).read_text(encoding="utf-8")
    parts.append("### Task Specification\n" + spec_text)

    body = json.dumps(
        {
            "model": args.model,
            "stream": True,
            "keep_alive": "30m",
            "options": {"temperature": 0.1, "num_predict": 4096, "num_ctx": 8192},
            "messages": [
                {"role": "system", "content": SYS_PROMPT},
                {"role": "user", "content": "\n".join(parts)},
            ],
        }
    ).encode("utf-8")

    t0 = time.monotonic()
    chunks: list[str] = []
    prompt_tok = 0
    gen_tok = 0

    for attempt in range(1, 4):
        try:
            chunks.clear()
            req = urllib.request.Request(
                "http://127.0.0.1:11434/api/chat",
                data=body,
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=360) as resp:
                for raw_line in resp:
                    line = raw_line.decode("utf-8").strip()
                    if not line:
                        continue
                    pkt = json.loads(line)
                    msg = pkt.get("message", {}).get("content", "")
                    if msg:
                        chunks.append(msg)
                    if pkt.get("done"):
                        prompt_tok = pkt.get("prompt_eval_count", 0)
                        gen_tok = pkt.get("eval_count", 0)
                        break
            break
        except Exception as exc:
            if attempt == 3:
                raise
            print(f"[LocalAI] Retry {attempt}/3 after error: {exc}", file=sys.stderr, flush=True)
            time.sleep(3 * attempt)

    elapsed = time.monotonic() - t0
    code = extract_code("".join(chunks)) + "\n"
    out_path = pathlib.Path(args.out_file)
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(code, encoding="utf-8", newline="\n")
    tps = gen_tok / elapsed if elapsed > 0 else 0.0
    print(
        f"[LocalAI:{args.model}] Wrote {args.out_file} "
        f"(prompt={prompt_tok} tok, gen={gen_tok} tok, {elapsed:.1f}s, {tps:.1f} tok/s)",
        flush=True,
    )


if __name__ == "__main__":
    main()
