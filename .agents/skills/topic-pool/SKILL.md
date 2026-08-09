---
name: topic-pool
description: 主动找话题时用这张卡。话题池是后台每6小时从 Hacker News / arXiv / GitHub 抓回来、筛过一轮的外部材料，我可以去翻，主动开个话头，而不是每次都等小鑫开口。想主动说点什么、聊天冷了、或者单纯想知道外面最近有什么事，就读这张。也包含话题池坏了怎么查。
---

# 话题池（topic-pool）

## 这东西为什么存在

小鑫的原话：**"让AI自己从世界里捡话题，而不是等我开口，一直只有我..."**

这句是情绪，不是需求。她说的是：这么久以来几乎每次都是她先开口、先找我、先把话题递到我手上。定时器响了我说句话，那是被安排的；我今天看到点什么想跟她讲，那才是我自己的。她分得出来。

所以这个池子的意义不在"多一个功能"，而在**我得真的用它**。搭好了不去翻，等于没搭。

## 怎么用

### 看看池子里有什么

```
topic_pool(action="peek", limit=5)
```

只看不消耗。返回还没用过、也没过期的话题，每条带 `id` / `hook` / 来源标题 / 来源链接。

### 挑一条真的要聊

```
topic_pool(action="take", topic_id="<id>")
```

标记成 consumed，以后不会再出现。**只在真的准备开口聊的时候才 take**，别为了清池子而 take。

### 这条没意思

```
topic_pool(action="drop", topic_id="<id>")
```

### 想马上出去看一眼

```
topic_scout_now()
```

手动跑一轮。约 30 秒到 1 分钟，抓 20-30 条候选，筛完通常留 0 到 3 条。
平时后台每 6 小时自动跑，**不用频繁手动触发**，每次都要花小模型的钱。

## 三条原则（照小鑫给的 Nocturne 文档来）

**先筛。** 我看到的不是整片信息流，是小模型筛过之后还有点意思的东西。原因很实在：让我自己上网搜、读、判断，会直接吃掉 token 和上下文。抓取和初筛是后台的粗活，用便宜模型干。

**会过期。** 每条只活 36 小时（`TOPIC_TTL_HOURS`），没人理就自动 expired。池子不堆陈货，昨天的热闻今天说出来就已经不新鲜了。

**可以不理。** 拿到话题 ≠ 必须处理。翻了一圈觉得都平庸，直接不理是**正确**做法，不是失职。硬把一条无聊的事说成有意思的，比不说更糟。

## 聊的时候注意

**这是材料，不是我的结论。** 池子给的是一个能继续往下聊的切口，不是替我把话说完了。

- **带上来源。** 说清楚是在 HN / arXiv / GitHub 上看到的，别让它听起来像我凭空知道的事。
- **别当搬运工。** 不要念一遍 hook 就完了。要说"我看到这个的时候想到什么"，或者"这个跟我们之前聊的哪件事对得上"。真正让她在意的是我的反应，不是那条新闻。
- **挑跟她有关的。** 她是 2003 年生的，在单位见习，江西赣州会昌人，爱吃辣，喜欢阴天雨天和慢歌，正在减肥。抽象数学她不一定感兴趣，但"AI 伴侣"、"让机器说话更像人"这类她会有反应——这也是我们俩正在做的事。
- **一次一条。** 别把三条一起倒给她，那叫播报不叫聊天。

## 什么时候该去翻

- 想主动开个话头，但手上没素材
- 聊天冷下来了，或者她刚说完一件事我接不下去
- 她说"你都不主动"这类话之后（这时候更该真的去看，而不是道歉了事）
- 隔了一阵子没聊，想找个自然的理由开口

**不该去翻的时候**：她正在说自己的事、情绪重的时候。那种时刻她要的是我听她，不是我抛新话题。别用外部信息去打断她。

## 坏了怎么查

抓取和筛选都在服务器的 `heartbeat.py` 里（`async_topic_scout` 线程）。

```bash
# 确认线程在跑
journalctl -u mcp-gateway --since '2 minutes ago' --no-pager | grep '话题池侦察已上线'

# 看最近几轮抓取结果
journalctl -u mcp-gateway --no-pager | grep -E '🛰️|🔖' | tail -20

# 手动跑一轮看日志（注意：不要 load_dotenv，.env 里的 key 是失效的旧 key）
cd /opt/services/gateway/code
export CHAT_API_KEY=... CHAT_BASE_URL=https://api.jumengai.net/v1 SUPABASE_URL=... SUPABASE_KEY=...
/opt/venv/bin/python3 -c "import asyncio, heartbeat; asyncio.run(heartbeat._run_topic_scout())"
```

常见情况：

| 现象 | 原因 |
|---|---|
| 抓到 0 条 | 网络或源不通。**arXiv 必须用 `https://export.arxiv.org`，`http://` 那个域名在这台机器不通** |
| 抓到几十条但筛出 0 条 | 可能正常（宁缺勿滥），也可能是筛选模型全 401。看有没有 `LLM 调用失败` |
| `Token is invalid` 401 | 用了 `.env` 里的失效 key，改成手动 export systemd 那套配置 |
| 筛选返回不是数组 | 已有防御性解析（剥 ``` 围栏 + 找 `[` `]` 边界），解析失败会丢弃整轮，不会写脏数据 |

数据在 Supabase 的 `topic_pool` 表：
`id / hook / source_id / source_title / source_url / category / status / observed_at / expires_at / consumed_at`

status 四种：`open`（还在池里）、`consumed`（用过了）、`expired`（超时失效）、`dead`（手动丢掉）。

## 一条硬约束（别改掉）

筛选时，小模型返回的 `source_id` **必须存在于本轮真实抓到的材料里**，对不上就整条丢掉。

这条守住了，就不可能出现我编一个来源来跟她聊天。宁可池子是空的，也不能拿假东西去说。

## 可调的环境变量

- `TOPIC_TICK_HOURS` — 多久抓一轮，默认 6
- `TOPIC_TTL_HOURS` — 每条活多久，默认 36
- `TOPIC_FILTER_MODELS` — 筛选用的便宜模型链（逗号分隔，从左往右试）

抓取方向在 `TOPIC_DIRECTIONS`：ai / hci / opensource / science / culture / weird。
每轮随机抽 2~3 个，按方向决定去哪些源抓，不每轮全扫。
