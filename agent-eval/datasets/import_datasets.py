"""
数据集导入脚本

从 code-assistant-eval-datasets.json 读取数据集定义，
通过 agent-eval 后端 API 批量创建数据集。

用法:
    python import_datasets.py [--base-url http://localhost:8000]

前提: agent-eval 后端已启动。
"""
import argparse
import json
import sys
import urllib.request
import urllib.error
from pathlib import Path


def import_datasets(base_url: str, json_path: str):
    api_url = f"{base_url.rstrip('/')}/api/datasets"

    with open(json_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    datasets = data.get("datasets", [])
    if not datasets:
        print("JSON 文件中没有 datasets 数据。")
        return

    created = 0
    failed = 0

    for ds in datasets:
        payload = {
            "name": ds["name"],
            "description": ds.get("description"),
            "items": ds["items"],
        }

        try:
            req = urllib.request.Request(
                api_url,
                data=json.dumps(payload).encode("utf-8"),
                headers={"Content-Type": "application/json"},
                method="POST",
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                result = json.loads(resp.read().decode("utf-8"))
                print(f"[OK] 已创建数据集 id={result['id']} — {ds['name']} ({len(ds['items'])} 条用例)")
                created += 1
        except urllib.error.HTTPError as e:
            body = e.read().decode("utf-8", errors="replace")
            print(f"[FAIL] {ds['name']}: HTTP {e.code} — {body}")
            failed += 1
        except Exception as e:
            print(f"[FAIL] {ds['name']}: 请求异常 — {e}")
            failed += 1

    print(f"\n导入完成: 成功 {created}, 失败 {failed}")


def main():
    parser = argparse.ArgumentParser(description="导入评测数据集到 agent-eval 平台")
    parser.add_argument(
        "--base-url",
        default="http://localhost:8000",
        help="agent-eval 后端地址 (默认: http://localhost:8000)",
    )
    args = parser.parse_args()

    json_path = Path(__file__).parent / "code-assistant-eval-datasets.json"
    if not json_path.exists():
        print(f"找不到数据集文件: {json_path}")
        sys.exit(1)

    import_datasets(args.base_url, str(json_path))


if __name__ == "__main__":
    main()
