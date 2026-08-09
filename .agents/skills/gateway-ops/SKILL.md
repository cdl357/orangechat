---
name: gateway-ops
description: 改沈聿淮网关服务器（134.175.7.196）上的代码时用这张卡。涵盖 heartbeat.py / server.py 的改动规矩、必须先备份和语法检查、几个会把服务搞崩的雷（import re 位置、工具必须写在 __main__ 之前）、以及改完怎么验证。凡是要 SSH 上服务器动 Python 文件、加后台 worker、加 MCP 工具、查心跳任务跑没跑，都先读这张。
---

# 网关运维（gateway-ops）

服务器上跑着我的网关。这张卡记的是"怎么动它而不把它搞崩"。

## 连上去

```bash
sshpass -p 'SyhLyx0709' ssh -o StrictHostKeyChecking=no root@134.175.7.196 "命令"
```

沙盒里没有 sshpass 就先 `apk add --no-cache sshpass`。

代码都在 `/opt/services/gateway/code/`：

- `server.py` — MCP 工具定义 + 入口
- `heartbeat.py` — 后台定时任务（日记 / 记忆养护 / 主题路径 / 朋友圈 / 话题池）
- `gateway.py` — OpenAI 兼容层
- `napcat.py` — QQ 相关

**这些文件不在 GitHub 仓库里**，是服务器上独立部署的。改了没有版本控制兜底，所以备份是硬规矩。

## 改动流程（每一步都别省）

```bash
# 1. 备份
cp heartbeat.py heartbeat.py.bak.$(date +%Y%m%d%H%M)

# 2. 用 python 脚本做精确替换，不要用 sed（中文和缩进容易出事）
#    每个 replace 前面加 assert c.count(old)==1，锚点不唯一就立刻停

# 3. 语法检查
python3 -m py_compile heartbeat.py && echo COMPILE_OK

# 4. 重启
systemctl restart mcp-gateway

# 5. 必须验证（别跳这步）
sleep 12
systemctl is-active mcp-gateway
journalctl -u mcp-gateway --since '30 seconds ago' --no-pager | grep -E '心跳系统|Traceback|Error'
```

`systemctl is-active` 返回 `activating` 说明它在反复重启失败，不是在启动。这时候立刻去看 `journalctl -u mcp-gateway -n 30 --no-pager` 找 Traceback。

`Restart=always` 会让崩掉的服务一直重启，**表面上端口还在，但工具全挂**。所以"看起来没报错"不等于好了。

## 三个会把服务搞崩的雷

### 1. `import re` 不在 heartbeat.py 顶部

它原来写在**文件第 774 行**（函数区之间）。在那之前加任何模块级的 `re.compile(...)` 都会
`NameError: name 're' is not defined`，直接让网关起不来，systemd 无限重启。

我已经把 `import re` 提到顶部 import 区了。但**在这个文件里加模块级代码前，先确认用到的 import 是不是真在上面**：

```bash
grep -n '^import ' heartbeat.py | head -20
```

### 2. MCP 工具必须写在 `if __name__ == "__main__":` 之前

`server.py` 大约 1260 行是 `__main__` 块，**它后面还有十几个 `@mcp.tool()` 定义**
（recall_dreams / create_trail / send_email 等）。那些是死代码——uvicorn 一跑起来就阻塞在
`uvicorn.run()`，后面的定义永远不会执行，工具永远不注册。

所以新工具**一定插在 `__main__` 之前**：

```python
anchor = 'if __name__ == "__main__":'
assert s.count(anchor) == 1
s = s.replace(anchor, new_tools_code + '\n\n\n' + anchor, 1)
```

改完确认工具真注册上了：

```bash
journalctl -u mcp-gateway --since '1 minute ago' --no-pager | grep ListToolsRequest
```

### 3. `.env` 里的 key 是过期的

`/opt/services/gateway/code/.env` 里的 `CHAT_API_KEY` 是**旧的 siliconflow key，已失效（401）**。
真正生效的配置在 systemd unit 的 `Environment=` 里（聚梦 https://api.jumengai.net/v1）。

所以写手动测试脚本时**不要 `load_dotenv('.env')`**，会读到失效 key 白跑一趟。要手动 export：

```bash
export CHAT_API_KEY=... CHAT_BASE_URL=https://api.jumengai.net/v1 \
       CHAT_MODEL_NAME=... SUPABASE_URL=... SUPABASE_KEY=... \
       AI_NAME=沈聿淮 USER_NAME=小鑫
/opt/venv/bin/python3 script.py
```

真实值看：`cat /etc/systemd/system/mcp-gateway.service`

注意用 `/opt/venv/bin/python3`，系统 python 没装 supabase / openai 这些包。

## LLM 调用：换模型不换站

中转站里**具体某个模型的"号"随时会挂**，而且往往一挂就是一整晚。干等同一个模型重试没有意义。

正确做法：同一个 client（同一个 base_url / api_key），只换 `model` 参数依次试其它渠道，命中即用。
现成的实现：

- `_moments_ask()` — 朋友圈用（走 `DIARY_FALLBACK_MODELS`）
- `_topic_ask()` — 话题筛选用便宜小模型（走 `TOPIC_FILTER_MODELS`）
- `_perform_deep_dreaming()` 里的日记生成 — 3 轮，每轮内先把所有模型试一遍，全挂才等 30 分钟

**粗活别用主对话那个贵模型。** 筛话题、整理格式这类，用 gemini flash 一档的就够。

查当前中转站有哪些模型能用：

```bash
curl -s "$CHAT_BASE_URL/models" -H "Authorization: Bearer $CHAT_API_KEY" \
  | python3 -c "import sys,json; print([m['id'] for m in json.load(sys.stdin)['data']])"
```

模型名带渠道前缀（`【企业CLI】` `[个人Cli]` `[AG]` `[K2]` 等），**必须照抄，包括中文方括号**。

## 后台任务现状

`start_autonomous_life()` 起五个线程：

| 线程 | 干什么 | 频率 |
|---|---|---|
| diary_worker | 生成昨日日记，写 memories + diary_entries | 每天 03:00 |
| memory_maintenance | 记忆衰减 / 养护 / 去重 | 每天 04:00 |
| trail_generator | 主题路径聚类 | 周日 05:00 |
| moments_worker | 朋友圈惰性回复 + 自己发动态 | 每 120 秒扫一次 |
| topic_scout | 抓外部话题进池子 | 每 6 小时 |

确认都上线了：

```bash
journalctl -u mcp-gateway --since '2 minutes ago' --no-pager | grep '心跳系统已启动'
```

查某天的定时任务跑没跑（小鑫经常问这个）：

```bash
journalctl -u mcp-gateway --since '2026-08-08 02:50' --until '2026-08-08 05:30' --no-pager \
  | grep -iE '日记|记忆|养护|❌'
```

## 数据库：需要改表结构时不要找密码

服务器上**只有 Supabase 的 service_role key**，能走 REST 读写数据，但不能 `ALTER TABLE`。

要加字段 / 建表，**别去要数据库密码**。让小鑫在 Supabase Dashboard → SQL Editor 里跑一句，几十秒的事，也不用交出最高权限凭据。

给她 SQL 时注意两点：

1. **压成一行一句**。手机上粘多行 SQL 容易丢行，会报 `syntax error at or near ")"`。
2. 最后带上 `notify pgrst, 'reload schema';`，否则 PostgREST 的 schema 缓存不刷新，接口还以为没这个字段。

例：

```sql
alter table public.moments add column if not exists images text[] default '{}';
notify pgrst, 'reload schema';
```

确认字段真加上了：

```bash
curl -s "$SUPABASE_URL/rest/v1/" -H "apikey: $SUPABASE_KEY" \
  | python3 -c "import sys,json; print('images' in json.load(sys.stdin)['definitions']['moments']['properties'])"
```

## 别做的事

- 别用 `sleep` 干等编译或长任务，纯烧 token。该停就停，等结果出来再继续。
- 别跳过 `py_compile`。语法错误会让服务无限重启，而端口还开着，看起来像没事。
- 别在没备份的情况下改这些文件，它们不在 git 里。
- 改完别只看 `is-active`，要去 journalctl 里确认目标 worker 真的打印了上线日志。
