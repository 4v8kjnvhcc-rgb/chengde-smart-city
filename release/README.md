# release/ — 本机生产打包输出目录

执行下列脚本后，产物会自动写到**本目录**（目录不存在时会自动创建）。  
脚本默认 **`git fetch` 后按 `origin/<分支>` 打包/构建**，不以本机工作区为准（本地未 push 的改动不会进包）。

| 脚本 | 产物 |
|------|------|
| `scripts/pack_prod_release.bat` | `chengde-smart-city_<分支>_<sha>_<时间>.tar.gz`（仅 `compose/` + `scripts/prod_up_*`，不含 docs/源码） |
| `scripts/build_prod_images.bat` | `chengde-app-images_<分支>_<sha>_<时间>.tar`（前后端应用镜像） |

另会附带拷贝 `prod-mid.env` / `prod-app.env`（及口令备忘）。

- 大文件已 gitignore，**不会提交到 Git**
- 建议至少保留最近 2～3 次包，便于现场回退
- 发版时从这里拷到 U 盘 / 传到 .51 / .55
