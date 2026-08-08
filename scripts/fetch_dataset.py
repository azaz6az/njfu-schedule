"""尝试下载开源 FIFA 球员数据集并转换为 data/players.csv；失败降级为内置球星名单。

设计文档 §17.1：一次性脚本。
- 成功：data/players.csv（name,ovr,position,age,club）
- 失败：内置约 50 名真实球星兜底（含武磊/韦世豪/朱辰杰等）
"""
import csv
import os
import sys
import urllib.request

SOURCES = [
    "https://raw.githubusercontent.com/kevinzhang1996/fifa21_male/main/players.csv",
]
FALLBACK_TOP = [
    # (name, ovr, position, age, club) —— 内置兜底：各联赛代表球星 + 中国球员
    ("梅西", 87, "RW", 36, "迈阿密国际"),
    ("C罗", 84, "ST", 39, "利雅得胜利"),
    ("哈兰德", 91, "ST", 23, "曼城"),
    ("姆巴佩", 91, "ST", 25, "皇家马德里"),
    ("贝林厄姆", 90, "CM", 21, "皇家马德里"),
    ("维尼修斯", 89, "LW", 23, "皇家马德里"),
    ("萨卡", 88, "RW", 22, "阿森纳"),
    ("福登", 88, "CAM", 24, "曼城"),
    ("凯恩", 89, "ST", 30, "拜仁慕尼黑"),
    ("罗德里", 89, "CDM", 28, "曼城"),
    ("德布劳内", 88, "CAM", 33, "曼城"),
    ("范戴克", 87, "CB", 33, "利物浦"),
    ("萨拉赫", 88, "RW", 32, "利物浦"),
    ("库尔图瓦", 88, "GK", 32, "皇家马德里"),
    ("莱万多夫斯基", 87, "ST", 35, "巴塞罗那"),
    ("巴尔韦德", 87, "CM", 26, "皇家马德里"),
    ("劳塔罗·马丁内斯", 87, "ST", 26, "国际米兰"),
    ("奥布拉克", 87, "GK", 31, "马德里竞技"),
    ("阿利松", 87, "GK", 31, "利物浦"),
    ("亚马尔", 87, "RW", 17, "巴塞罗那"),
    ("穆西亚拉", 87, "CAM", 21, "拜仁慕尼黑"),
    ("赖斯", 86, "CDM", 25, "阿森纳"),
    ("维尔茨", 86, "CAM", 21, "勒沃库森"),
    ("佩德里", 86, "CM", 21, "巴塞罗那"),
    ("多纳鲁马", 86, "GK", 25, "巴黎圣日耳曼"),
    ("埃德森", 86, "GK", 30, "曼城"),
    ("萨利巴", 86, "CB", 23, "阿森纳"),
    ("格列兹曼", 86, "CAM", 34, "马德里竞技"),
    ("奥西门", 85, "ST", 25, "那不勒斯"),
    ("布鲁诺·费尔南德斯", 85, "CAM", 29, "曼联"),
    ("基米希", 85, "CM", 30, "拜仁慕尼黑"),
    ("麦尼昂", 85, "GK", 28, "AC米兰"),
    ("金玟哉", 84, "CB", 27, "拜仁慕尼黑"),
    ("孙兴慜", 84, "LW", 31, "托特纳姆热刺"),
    ("莱奥", 84, "LW", 25, "AC米兰"),
    ("特奥·埃尔南德斯", 84, "LB", 26, "AC米兰"),
    ("阿什拉夫", 84, "RB", 25, "巴黎圣日耳曼"),
    ("亚历山大-阿诺德", 84, "RB", 25, "利物浦"),
    ("帕尔默", 84, "CAM", 22, "切尔西"),
    ("三笘薫", 83, "LW", 27, "布莱顿"),
    ("久保建英", 82, "RW", 23, "皇家社会"),
    ("莫德里奇", 83, "CM", 38, "皇家马德里"),
    ("京多安", 83, "CM", 33, "巴塞罗那"),
    ("凯塞多", 84, "CDM", 23, "切尔西"),
    ("恩佐·费尔南德斯", 83, "CM", 23, "切尔西"),
    ("武磊", 75, "ST", 32, "上海海港"),
    ("韦世豪", 76, "LW", 29, "成都蓉城"),
    ("朱辰杰", 74, "CB", 23, "上海申花"),
    ("蒋光太", 74, "CB", 30, "上海海港"),
    ("王大雷", 74, "GK", 35, "山东泰山"),
    ("颜骏凌", 73, "GK", 33, "上海海港"),
    ("张玉宁", 73, "ST", 27, "北京国安"),
    ("谢鹏飞", 72, "CAM", 31, "上海申花"),
    ("林良铭", 72, "RW", 27, "北京国安"),
    ("徐皓阳", 71, "CM", 25, "上海申花"),
    ("王上源", 70, "CM", 31, "河南队"),
    ("胡荷韬", 70, "LB", 21, "成都蓉城"),
    ("拜合拉木", 69, "ST", 21, "山东泰山"),
]

HEADER = ["name", "ovr", "position", "age", "club"]


def download(force=False) -> int:
    """下载数据集；已存在且非 force 时直接返回。返回写入球员数。"""
    out = "data/players.csv"
    if os.path.exists(out) and not force:
        return len(list(csv.DictReader(open(out, encoding="utf-8"))))
    for url in SOURCES:
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (football-life-simulator)"})
            data = urllib.request.urlopen(req, timeout=20).read().decode("utf-8", "ignore")
            rows = list(csv.DictReader(data.splitlines()))
            if not rows:
                continue
            with open(out, "w", encoding="utf-8", newline="") as f:
                w = csv.writer(f)
                w.writerow(HEADER)
                for r in rows[:20000]:
                    w.writerow([
                        r.get("full_name") or r.get("short_name", ""),
                        r.get("overall", "60"),
                        (r.get("player_positions", "CM") or "CM").split(",")[0].strip(),
                        r.get("age", "20"),
                        r.get("club_name", ""),
                    ])
            return len(rows)
        except Exception as e:
            print(f"[降级] 源 {url} 失败: {e}", file=sys.stderr)
    with open(out, "w", encoding="utf-8", newline="") as f:  # 内置兜底名单
        w = csv.writer(f)
        w.writerow(HEADER)
        for row in FALLBACK_TOP:
            w.writerow(row)
    return len(FALLBACK_TOP)


if __name__ == "__main__":
    n = download(force="--force" in sys.argv)
    print(f"players.csv 就绪: {n} 名球员")
