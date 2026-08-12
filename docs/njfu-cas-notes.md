# 南林 CAS 登录侦察笔记（任务 7）

> 侦察时间：2026-08-12。目标站点：`https://uia.njfu.edu.cn/authserver/`（金智教育系统一身份认证，Wisedu CAS）。
> 结论先行：**本站密码加密是「金智 AES 复合加密」形态，不是 RSA/JSEncrypt**。任务计划中的 RSA 假设已按实际算法修正（见文末说明）。

## 1. 加密 JS 文件与关键代码

登录页引用的 JS（`/authserver/custom/js/` 下，URL 带 `jsessionid` 后缀）：

| 文件 | 作用 |
|---|---|
| `jquery.min.js` | jQuery |
| `syalert.min.js` / `icheck.min.js` / `swiper-bundle.min.js` | UI 组件 |
| `login.js` | 动态码（手机验证码）登录页逻辑 |
| `login-wisedu_v1.0.js` | 账号密码登录表单逻辑（核心） |
| `encrypt.js` | **加密库：CryptoJS（AES-128-CBC + MD5/EvpKDF），无 JSEncrypt** |

全站（页面 HTML + 全部 7 个 JS）grep `jsencrypt|rsa|publicKey` 均无命中，**不存在 RSA**。

### encrypt.js 关键摘录（原文）

```js
function _gas(data,key0,iv0){
  key0 = key0.replace(/(^\s+)|(\s+$)/g, "");
  var key = CryptoJS.enc.Utf8.parse(key0);   // salt 字符串直接作 AES key（16 字符 = AES-128）
  var iv  = CryptoJS.enc.Utf8.parse(iv0);    // iv 为 16 字符随机串（UTF-8 字节）
  var encrypted = CryptoJS.AES.encrypt(data,key,{iv:iv,mode:CryptoJS.mode.CBC,padding:CryptoJS.pad.Pkcs7});
  return encrypted.toString();               // 直接 key（WordArray）→ OpenSSL formatter 输出纯 Base64，无 "Salted__" 前缀
}
function encryptAES(data,_p1){
  if(!_p1){return data;}                     // salt 为空则明文直传（兜底）
  var encrypted = _gas(_rds(64)+data,_p1,_rds(16));  // 明文 = 64 字符随机前缀 + 密码
  return encrypted;
}
var $_chars = 'ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678';  // 随机串字符集（53 字符）
function _rds(len){ /* 从 $_chars 随机取 len 个字符 */ }
```

### login-wisedu_v1.0.js 关键摘录（表单提交时调用）

```js
// 提交前（表单 submit 事件 doLogin / 滑块流程 submitLoginForm）：
_etd2(password.val(), casLoginForm.find("#pwdDefaultEncryptSalt").val());
// _etd2 实现：
function _etd2(_p0,_p1){try{var _p2 = encryptAES(_p0,_p1);
  $("#casLoginForm").find("#passwordEncrypt").val(_p2);}catch(e){
  $("#casLoginForm").find("#passwordEncrypt").val(_p0);}}
```

## 2. 确认的加密算法（形态：金智 AES 复合加密，无 RSA）

```
passwordEncrypt = Base64( AES-128-CBC-Pkcs7(
    key       = UTF8(pwdDefaultEncryptSalt),   // 登录页隐藏域，16 字符，如 "GL2ABrOeJffTQRpf"
    iv        = UTF8(_rds(16)),                // 每次加密随机生成 16 字符
    plaintext = _rds(64) + password            // 64 字符随机前缀 + 明文密码
))
```

要点：

- **salt 是 AES 密钥，不拼进明文**（与任务计划假设的 RSA `salt+password` 明文不同）。
- key 为 `CryptoJS.enc.Utf8.parse(salt)`（直接 key 模式，非 passphrase），故输出**无 "Salted__" 前缀**，为纯 `Base64(ciphertext)`。
- 随机串字符集：`ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678`（53 字符，无 I/L/O/U/o/l/u/0/1/9）。
- 明文 64+len(pass) 字节 → 80 字节（5 块）→ Base64 108 字符（15 字符密码时）。

**关于随机 IV 服务器如何解密**：CBC 解密时只有第 1 块受 IV 影响，随机 64 字符前缀的第 1 块（16 字节）正好"吸收"IV 不一致产生的乱码，第 2 块起（含全部密码）与 IV 无关、可正确解出。已用 Node（`crypto`）验证：同一密文用随机 IV / iv=key / 全零 IV 解密，尾部密码均一致。故客户端随机 IV 即可，服务器用什么 IV 都不影响。

## 3. RSA 公钥获取方式

**不适用**：本部署无 JSEncrypt、无 `rsaPublicKey` 隐藏域、无 `getEncryptPublicKey` 接口。无公钥环节。

## 4. 表单提交字段名与顺序

`POST /authserver/login?service=http%3A%2F%2Fjwxt.njfu.edu.cn%2Fsso.jsp`（`application/x-www-form-urlencoded`），`casLoginForm` 字段：

| 顺序 | name | 值 |
|---|---|---|
| 1 | `username` | 学号明文 |
| 2 | `password` | **加密后密文**（隐藏域 `passwordEncrypt`，name 为 `password`，覆盖无 name 的明文密码框） |
| 3 | `lt` | 登录页隐藏票据（每次会话不同，如 `LT-111593-...-MrX5-cas`） |
| 4 | `dllt` | `userNamePasswordLogin` |
| 5 | `execution` | 当前恒为 `e1s1`（每次会话不同则需从页面取） |
| 6 | `_eventId` | `submit` |
| 7 | `rmShown` | `1` |
| 8 | `captchaResponse` | 仅当 needCaptcha 判定需要验证码时提交 |

登录页每次 GET 的 `pwdDefaultEncryptSalt` / `lt` / `execution` 均会刷新（已实测多次不同），必须每次登录重新抓取。加密用**页面隐藏域 `pwdDefaultEncryptSalt` 的值**（`_etd2` 读的是隐藏域而非 JS 全局变量）。

## 5. 验证码 / 滑块触发条件

- `GET /authserver/needCaptcha.html?username=<学号>&pwdEncrypt2=pwdEncryptSalt` 返回 `true` / `false`（文本）；已实测陌生学号返回 `false`（无需验证码）。
- 返回体若含 `::::`（格式 `true|false::::新salt`），JS 会更新全局 salt 变量（但 `_etd2` 仍读隐藏域，故对我们无影响）。
- `true` 且隐藏域 `isSliderCaptcha` 非空 → 滑块验证码（`createSliderCaptcha()`，提交 `sliderCaptchaDynamicCode`）；`isSliderCaptcha` 为空 → 图形验证码 `GET /authserver/captcha.html`，需人工/OCR 填写 `captchaResponse`。
- 手机号（动态码）登录另走 `casDynamicLoginForm`，用 `dynamicPwdEncryptSalt` 做 AES，与本任务无关。

## 6. 实测验证（模拟真实提交）

用 Node 加载线上 encrypt.js 原文（vm 执行 `encryptAES`），对假账号完整 POST 登录表单：

- 响应 200，无跳转，页面提示 **"您提供的用户名或者密码有误"**。
- 说明服务器**成功解密**了 AES 密文并进入账号密码校验阶段（若解密失败会报参数/解密类错误）。加密方案与服务器完全兼容。
- `needCaptcha.html` 对陌生账号返回 `false`，未触发验证码。

## 7. 实现说明（与任务计划的偏差）

- 任务计划假设 RSA 形态 A 被实测推翻 → `RsaEncryptor.kt` 按**金智 AES 算法**实现（类名按任务约定保留 `RsaEncryptor`，供任务 8 引用；KDoc 已注明命名沿革）。
- API：`RsaEncryptor.encryptPassword(password: String, salt: String): String`，随机前缀/IV 内部生成。
- 测试改用 AES 往返校验：解密（任意 IV）后尾部 == 密码，且随机前缀长度/字符集符合 `_rds` 约定。
- 后续任务（任务 8）登录流程：GET 登录页 → 解析 salt/lt/execution →（可选）needCaptcha 判定 → AES 加密密码 → POST 表单 → 跟随 302 到 `sso.jsp` 完成教务 SSO。
