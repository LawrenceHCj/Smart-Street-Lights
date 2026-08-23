# 部署说明

负责人：6号。

## 本地运行

```powershell
cd "C:\Users\Lawrence\Desktop\Smart Street Lights"
npm run dev
```

访问：

```text
http://localhost:3000
```

## 端口配置

默认端口为 3000。可通过环境变量修改：

```powershell
$env:PORT=3100
npm run dev
```

## 交付检查

- `frontend/` 页面可访问。
- `/api/summary` 正常返回。
- 低光、高光、离线场景可验证。
- `docs/team-division.md`、`docs/test-cases.md`、`docs/deployment.md` 已更新。
