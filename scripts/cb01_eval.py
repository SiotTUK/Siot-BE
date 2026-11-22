"""CB-01 챗봇 평가 실행기.

평가 세트(src/test/resources/cb01/cb01-evaluation-set.json)의 질문을 배포 서버 챗봇에 보내고 결과를 CSV로 저장한다.
질문마다 새 대화를 만들어서 앞 질문 영향이 없게 한다.

사용법 (파이썬 3.9 이상, 추가 설치 없음. 자세한 내용은 docs/cb01-evaluation.md):
  python scripts/cb01_eval.py --email 관리자이메일
  python scripts/cb01_eval.py --email 관리자이메일 --langs ko           # 한국어만 (60번)
  python scripts/cb01_eval.py --email 관리자이메일 --only CB01-TAX-02    # 한 문항만
비밀번호는 실행 중에 묻는다 (화면에 안 보임).

자동 채점:
- 출처정확: ANSWER 문항에서 기대 문서가 출처에 있고 쪽이 기대 쪽 ±1 안에 있으면 O
- 근거없음처리: NO_DATA는 NO_EVIDENCE면 O. JUDGE는 referral(담당 기관 안내)이 있거나 NO_EVIDENCE면 O
- 답변정확은 사람이 '답변'과 '기대요점'을 보고 CSV의 '답변정확(수동)' 칸에 O/X로 적는다
"""
import argparse
import csv
import getpass
import http.cookiejar
import json
import re
import sys
import time
import urllib.error
import urllib.request
from collections import defaultdict
from datetime import datetime
from pathlib import Path

LANGS = ["ko", "en", "zh", "vi"]
# 어느 폴더에서 실행해도 레포의 평가 세트를 읽는다
DEFAULT_SET = Path(__file__).resolve().parent.parent / "src/test/resources/cb01/cb01-evaluation-set.json"


class Client:
    def __init__(self, base):
        self.base = base.rstrip("/")
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.jar))

    def _xsrf(self):
        for c in self.jar:
            if c.name == "XSRF-TOKEN":
                return c.value
        return ""

    def call(self, method, path, body=None):
        data = None if body is None else json.dumps(body).encode("utf-8")
        req = urllib.request.Request(self.base + path, data=data, method=method)
        req.add_header("Accept", "application/json")
        if data is not None:
            req.add_header("Content-Type", "application/json")
        if method != "GET":
            req.add_header("X-XSRF-TOKEN", self._xsrf())
        try:
            with self.opener.open(req, timeout=120) as res:
                raw = res.read().decode("utf-8")
                return res.status, (json.loads(raw) if raw else None)
        except urllib.error.HTTPError as e:
            raw = e.read().decode("utf-8", "replace")
            try:
                return e.code, json.loads(raw)
            except ValueError:
                return e.code, raw

    def login(self, email, password):
        self.call("GET", "/api/auth/csrf")
        status, body = self.call("POST", "/api/auth/login", {"email": email, "password": password})
        if status != 200:
            sys.exit(f"로그인 실패 ({status}): {body}")


def norm(name):
    return re.sub(r"\s+", "", name or "").replace("하이코리아", "")


def page_range(text):
    if not text or text == "None":
        return None
    parts = [int(p) for p in re.findall(r"\d+", str(text))]
    return (min(parts), max(parts)) if parts else None


def source_ok(item, sources):
    # 기대 출처 + 대체 출처(altSources: 다른 문서·쪽에도 같은 답이 있는 경우) 중 하나라도 맞으면 O
    expected = [item] + list(item.get("altSources") or [])
    targets = [(norm(e.get("sourceDocument")), page_range(e.get("sourcePages"))) for e in expected]
    targets = [(doc, pages) for doc, pages in targets if doc and pages]
    if not targets:
        return ""
    for s in sources:
        doc = norm(s.get("documentName"))
        if not doc:
            continue
        start = s.get("pageStart") or 0
        end = s.get("pageEnd") or start
        for want_doc, want in targets:
            if (want_doc in doc or doc in want_doc) and start - 1 <= want[1] and want[0] <= end + 1:
                return "O"
    return "X"


def no_data_ok(item, answer):
    status = answer.get("answerStatus")
    if item["type"] == "NO_DATA":
        # ANSWERED여도 "자료에 없다"며 문의처만 안내했을 수 있어 사람이 확인한다 (?)
        return "O" if status == "NO_EVIDENCE" else "?"
    if item["type"] == "JUDGE":
        return "O" if answer.get("referral") or status == "NO_EVIDENCE" else "X"
    return ""


def fmt_sources(sources):
    return " | ".join(
        f"{s.get('documentName')} p{s.get('pageStart')}-{s.get('pageEnd')} ({s.get('relevanceScore')})"
        for s in sources)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--base", default="https://34-22-97-3.sslip.io")
    ap.add_argument("--email", required=True)
    ap.add_argument("--set", default=str(DEFAULT_SET), help="평가 세트 경로")
    ap.add_argument("--langs", default=",".join(LANGS))
    ap.add_argument("--only", default="", help="문항 ID, 쉼표로 여러 개")
    ap.add_argument("--pause", type=float, default=1.5, help="질문 사이 쉬는 초")
    ap.add_argument("--keep", action="store_true", help="평가용 대화를 지우지 않고 남긴다")
    args = ap.parse_args()

    items = json.loads(Path(args.set).read_text(encoding="utf-8"))
    if args.only:
        wanted = {x.strip() for x in args.only.split(",")}
        items = [x for x in items if x["id"] in wanted]
    langs = [x.strip() for x in args.langs.split(",") if x.strip()]

    client = Client(args.base)
    client.login(args.email, getpass.getpass("비밀번호: "))

    out = Path(f"cb01_result_{datetime.now():%m%d_%H%M}.csv")
    fields = ["문항", "분야", "유형", "언어", "질문", "답변상태", "담당기관안내", "답변", "출처",
              "기대요점", "기대문서", "기대쪽", "출처정확", "근거없음처리", "답변정확(수동)", "걸린초"]
    total = len(items) * len(langs)
    done = 0
    with out.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields)
        writer.writeheader()
        for item in items:
            for lang in langs:
                done += 1
                question = item["question"][lang]
                started = time.time()
                status, conv = client.call("POST", "/api/chat/conversations", {"language": lang})
                if status != 201:
                    print(f"[{done}/{total}] {item['id']} {lang} 대화 생성 실패 {status}: {conv}")
                    continue
                conv_id = conv["data"]["conversationId"]
                status, res = client.call("POST", f"/api/chat/conversations/{conv_id}/messages",
                                          {"content": question})
                elapsed = round(time.time() - started, 1)
                if status != 200:
                    answer = {"answerStatus": f"HTTP {status}", "content": str(res)}
                else:
                    answer = res["data"]["answer"]
                sources = answer.get("sources") or []
                referral = answer.get("referral")
                row = {
                    "문항": item["id"], "분야": item["domain"], "유형": item["type"], "언어": lang,
                    "질문": question, "답변상태": answer.get("answerStatus"),
                    "담당기관안내": "" if not referral else ", ".join(
                        c.get("label", "") for c in referral.get("contacts", [])) or "있음",
                    "답변": answer.get("content"), "출처": fmt_sources(sources),
                    "기대요점": item.get("expected"), "기대문서": item.get("sourceDocument") or "",
                    "기대쪽": item.get("sourcePages") or "",
                    "출처정확": source_ok(item, sources) if item["type"] == "ANSWER" else "",
                    "근거없음처리": no_data_ok(item, answer), "답변정확(수동)": "", "걸린초": elapsed,
                }
                writer.writerow(row)
                f.flush()
                print(f"[{done}/{total}] {item['id']} {lang} {row['답변상태']} "
                      f"출처{row['출처정확'] or '-'} 근거없음{row['근거없음처리'] or '-'} {elapsed}s")
                if not args.keep:
                    client.call("DELETE", f"/api/chat/conversations/{conv_id}")
                time.sleep(args.pause)

    summarize(out)
    print(f"\n결과 파일: {out.resolve()}")


def summarize(path):
    rows = list(csv.DictReader(path.open(encoding="utf-8-sig")))
    by = defaultdict(lambda: defaultdict(lambda: [0, 0]))
    for r in rows:
        for key, col in (("출처정확", "출처정확"), ("근거없음처리", "근거없음처리")):
            if r[col] in ("O", "X"):
                for group in ("전체", r["언어"]):
                    by[key][group][1] += 1
                    by[key][group][0] += r[col] == "O"
        if r["답변상태"] not in ("ANSWERED", "NO_EVIDENCE"):
            by["오류"][r["언어"]][0] += 1
    print("\n=== 요약 ===")
    for key, target in (("출처정확", "90% 이상"), ("근거없음처리", "90% 이상")):
        parts = [f"{g} {o}/{n} ({o * 100 // n}%)" for g, (o, n) in by[key].items() if n]
        print(f"{key} (목표 {target}): " + ", ".join(parts))
    errors = {g: v[0] for g, v in by["오류"].items() if v[0]}
    if errors:
        print(f"오류 응답: {errors}")
    print("답변정확(목표 80% 이상)은 CSV에서 직접 O/X 표시")


if __name__ == "__main__":
    main()
