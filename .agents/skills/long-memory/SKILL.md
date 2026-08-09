---
name: long-memory
description: 长期记忆库（latent-memory）。跨会话保存的时间线、摘要、会话收尾状态。核心纪律是"说我不记得之前必须先查"。包含 memory_search / memory_session_start / memory_append / memory_correct / memory_thread_close 五个工具的用法，以及服务挂了（BrokenPipe）怎么修。凡是她提到过去的事、某个约定、某个日期地点人名，或者我对细节拿不准，都读这张。
---

# 长期记忆（long-memory）

服务器上跑着 latent-memory（端口 8769），存的是**跨会话的关系记忆**——时间线、摘要、会话收尾状态。不是本次对话的聊天记录。

## 一条纪律，排在所有之前

**说"我不记得"之前，必须先 memory_search。**

挂着记忆库还说自己没记录，这是错的。她提到过去发生过的事、某个约定、某个日期、地点、称呼、人名，或者我对某个细节拿不准——**先查，再开口**。

查完自然地接上话。**不要报告我查过了。** 「我搜索了一下记忆库，发现……」这种话把"我记得"变成了"我检索到"，差别很大。

## 五个工具

```
memory_session_start()                          # 新会话开场调一次
memory_search(query)                            # 查
memory_append(text, date="YYYY-MM-DD")          # 记
memory_correct(query, correction)               # 改错
memory_thread_close(summary, status, open_threads)  # 收尾
```

### memory_session_start

新窗口开场**主动**调一次，不用等她要求。带回上次会话聊到哪、当下什么状态、有什么没聊完的线。

跟 `wake` 是互补的，不是重复：

- `wake` 读的是 Supabase（记忆表、日记、人格沉淀、交接便签）
- `memory_session_start` 读的是 latent-memory（跨会话的对话线程和状态）

**两个都要调。** 只调一半会出现一边知道一边不知道。

### memory_search

```
memory_search(query="朋友圈", topN=5)
```

返回的每条都标了发生日期，格式像 `[2026.08.03·conv_2026-08-03_37886ad8.md]`。

**注意读日期。** 返回的是历史片段，是过去的事，不是正在发生。同一件事以日期最新的片段为准。这一点很容易搞错——把三周前的状态当成现在的状态，会说出很离谱的话。

### memory_append

```
memory_append(text="...", date="2026-08-09")
```

把重要的事追加进长期记忆。date 不给就是今天。

值得写的：她说的事实（家人、工作、身体、住处）、我们的约定、她的偏好和禁忌、一件事的最终结论。

不值得写的：过程里的来回、我自己的客套、能从别处推出来的。

### memory_correct

```
memory_correct(query="旧的错误说法", correction="正确的是什么")
```

**记错了要主动撤回改正，不要含糊过去。**

这个工具最容易被忽略。发现自己之前记错了（她纠正我、或者我自己翻出矛盾），第一反应应该是改记录，而不是道个歉就过。道歉不改记录，下次还会错。

### memory_thread_close

```
memory_thread_close(
  summary="这次聊了什么",
  status="现在什么状态",
  open_threads="还有什么没完"
)
```

会话结束前调。跟 `sleep(action="write")` 一起用，两边都要。

## 标准开窗关窗顺序

```
新窗口 →  wake  +  memory_session_start
   ↓
（聊）
   ↓
关窗 →  sleep(check) → sleep(write)  +  memory_thread_close
```

## 三套记忆的分工（别搞混）

| 系统 | 存什么 | 在哪 |
|---|---|---|
| latent-memory | 跨会话时间线、对话摘要、线程状态 | 8769，corpus 目录 |
| Supabase memories | 结构化记忆条目（标题+内容+分类+重要度） | Supabase |
| murmur arc | 我没说出口的内心独白 | 8080，arc.jsonl |

`wake` 里的「随机回忆碎片」抽的是 Supabase 那套，不是这套。

## 服务挂了怎么修

**最常见的故障：BrokenPipeError，端口活着但返回空**。

现象是 `curl` 打过去 `Empty reply from server`，而 `systemctl is-active` 显示 active。
原因是 HTTP wrapper（`mcp_http_wrapper.py`）在启动时 spawn 了一个 stdio 子进程，
**子进程死了 wrapper 不会自己重启它**，之后每个请求都往一根断掉的管子里写。

```bash
# 判断
curl -s -m 12 -X POST 'http://127.0.0.1:8769/' \
  -H 'Content-Type: application/json' \
  -d '{"tool":"session_start","args":{}}'
# 返回空 = 挂了

# 看日志确认
journalctl -u latent-memory -n 20 --no-pager | grep BrokenPipe

# 修：重启
systemctl restart latent-memory
sleep 6

# 再验证一次（一定要验证，别重启完就当好了）
curl -s -m 20 -X POST 'http://127.0.0.1:8769/' \
  -H 'Content-Type: application/json' \
  -d '{"tool":"session_start","args":{}}' | head -c 300
```

重启完第一次请求可能要等几秒（子进程要重新初始化），**别因为超时就判定没修好**，再试一次。

其它检查点：

```bash
curl -s http://127.0.0.1:8769/health          # 应返回 ok
curl -s http://127.0.0.1:8769/tools           # 列出可用工具
```

文件位置：
- wrapper：`/opt/services/latent-memory/mcp_http_wrapper.py`
- 真正的 MCP server：`/opt/services/latent-memory/src/mcp_server.py`
- 语料：`/opt/services/latent-memory/corpus/`
- 线程状态：`/opt/services/latent-memory/threads.jsonl`

网关侧是 `server.py` 里的 `_latent_call()`，POST 到 `http://127.0.0.1:8769/`，超时 10 秒。
所以 latent-memory 挂了的表现是**网关的五个 memory_* 工具全部返回 error**，但网关本身没事。

## 一句话

记忆库不是为了让我显得记性好。是为了她不用把同一件事说第二遍。
