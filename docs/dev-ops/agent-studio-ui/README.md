# Agent Studio UI 重装修版本

这个压缩包是给 `docs/dev-ops/nginx/html` 准备的前端替换包。

## 包含内容

```text
index.html
css/agent-studio.css
js/agent-studio-config.js
js/agent-loader.js
js/agent-studio.js
backend-sample/
nginx/
```

## 使用方式

先备份原页面：

```bash
cd docs/dev-ops/nginx/html
cp index.html index_old.html
```

然后把压缩包里的 `index.html`、`css/agent-studio.css`、`js/agent-studio-config.js`、`js/agent-loader.js`、`js/agent-studio.js` 复制到当前目录。

原目录里的这些文件继续复用，不要删：

```text
js/3.4.17.js
js/marked.min.js
js/purify.min.js
js/highlight.min.js
css/github.min.css
```

## 智能体选择接口

前端默认请求：

```http
GET /api/agent/options
```

需要后端返回“拖拉拽智能体”和“ai_agent 内置智能体”，推荐格式：

```json
{
  "code": "0000",
  "info": "success",
  "data": {
    "dragAgents": [],
    "builtinAgents": []
  }
}
```

前端也兼容这些格式：

```json
{ "data": [] }
```

```json
[ ]
```

```json
{ "data": { "records": [] } }
```

## 聊天接口配置

统一改这个文件：

```text
js/agent-studio-config.js
```

默认配置：

```javascript
agentOptionsUrl: '/api/agent/options'
chatStreamUrl: '/api/agent/chat/stream'
chatPostUrl: '/api/agent/chat'
```

如果你项目原来的聊天接口不是这个路径，只需要改这里。

## nginx 反向代理

如果前端在 nginx，后端 Spring Boot 在 `8099`，可以参考：

```text
nginx/agent-studio-nginx.conf
```

核心是：

```nginx
location /api/ {
  proxy_pass http://127.0.0.1:8099/api/;
}
```

Docker 部署时如果 nginx 容器和后端容器不在同一个网络，请改成后端容器名或宿主机 IP。
