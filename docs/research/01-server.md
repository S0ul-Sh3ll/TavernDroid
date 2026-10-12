# 01 — SillyTavern 1.19 Server Layer (Node/Express)

Source: the SillyTavern 1.19.0 source root (commit 06bde93) (package `sillytavern@1.19.0`, `package.json:118`), read-only. All citations are `file:line` relative to that root.
Scope: `server.js`, `plugins.js`, `src/**`, `default/config.yaml`, `default/content`. The browser client (`public/**`) is covered elsewhere and cited only where it defines data that the server stores.

Quick facts

| Fact | Value | Cite |
|---|---|---|
| Runtime | Node `>= 20`, ESM (`"type": "module"`) | `package.json:103-105,113` |
| Entry | `node server.js` (also `--global` bin `src/server-global.js`, Electron, Deno, Bun scripts) | `package.json:119-135` |
| HTTP | Express 4, one app, HTTP or HTTPS, IPv4 and/or IPv6 listeners | `src/server-main.js:103`, `src/server-startup.js:274-368` |
| Default bind | `127.0.0.1:8000` (`listen:false`), IPv6 off | `src/command-line.js:60-65`, `default/config.yaml:6,14-16,40` |
| Route count | 335 handlers in `src/endpoints/**` + ~12 in `server-main.js`/`users.js`, plus 50 legacy 308 redirects | generated from sources (§3), `src/server-startup.js:84-133` |
| Child processes | none in server code; `git` is reached only through `simple-git` (spawns `git`) | §6, §7 |
| Native addons | none (`*.node`/`binding.gyp` absent); heavy WASM (onnxruntime-web, tiktoken, jsquash/jimp codecs) | §7 |

---

## 1. Boot sequence

### 1.1 Call chain

| # | Step | Cite |
|---|---|---|
| 1 | `server.js` logs Node version, parses CLI via `CommandLineParser().parse(process.argv)` | `server.js:5-8` |
| 2 | Parser picks global vs standalone mode; global = `env-paths('SillyTavern').data/{config.yaml,data}`, standalone = `./config.yaml`, `./data` | `src/command-line.js:53-57,269-290` |
| 3 | `initConfig(configPath)` → `setConfigFilePath` + `addMissingConfigValues` (creates config.yaml from `default/config.yaml` if missing; migrates 23 legacy keys; `_.defaultsDeep` adds missing keys and rewrites file) | `src/command-line.js:286`, `src/config-init.js:9-137,164-253` |
| 4 | `dataRoot` = CLI `--dataRoot` ?? config `dataRoot` ?? `./data`; `mkdirSync` if missing | `src/command-line.js:288-297` |
| 5 | Build `CommandLineArguments` (CLI overrides config) | `src/command-line.js:300-381` |
| 6 | `globalThis.DATA_ROOT`, `globalThis.COMMAND_LINE_ARGS` set; `process.chdir(serverDirectory)`; dynamic `import('./src/server-main.js')` | `server.js:9-14` |
| 7 | `server-main.js` top level: import `fetch-patch.js` (file:// fetch for `.wasm` under server dir), Node 20.0–20.2 autoSelectFamily workaround, IPv4/IPv6 sanity, global keep-alive agents | `src/server-main.js:22,78-101`, `src/fetch-patch.js:8-40` |
| 8 | Middleware + routes registered synchronously (see §2) | `src/server-main.js:103-278` |
| 9 | Promise chain: `initUserStorage(DATA_ROOT)` (node-persist at `_storage`, creates `default-user` if none) → `setDnsResolutionOrder` → `ensurePublicDirectoriesExist` → `migrateUserData` (pre-1.12 `public/*` → `data/<user>`, 10 s countdown) → `migrateSystemPrompts` → `migratePublicOverrides` (`public/error/*.html`, `public/css/user.css` → `_errors`, `_css`) → `verifySecuritySettings` → `preSetupTasks` → `apply404Middleware` → `ServerStartup.start()` → `postSetupTasks` | `src/server-main.js:479-490`, `src/users.js:113-131,232-530,556-575` |
| 10 | `preSetupTasks`: print version (git via simple-git if `git` on PATH), `migrateGroupChatsMetadataFormat`, `checkForNewContent` (seed default content), `diskCache.verify`, `migrateFlatSecrets`, `cleanUploads`, `migrateAccessLog`, `settingsInit` (settings snapshots), `statsInit`, `loadPlugins`, SIGINT/SIGTERM/uncaughtException handlers, private request filter, request proxy, **webpack compile of `public/lib.js`** | `src/server-main.js:284-355`, `src/util.js:136-165` |
| 11 | `ServerStartup.start()`: resolve `auto` protocols via `os.networkInterfaces()`, create HTTP(S) server(s) with `ipv6Only:true` per family, fatal on all-fail | `src/server-startup.js:402-451`, `src/util.js:1063-1098` |
| 12 | `postSetupTasks`: optional browser launch with `open` (no app selection on Android), heartbeat file, window title, emit `server-started` event | `src/server-main.js:362-450`, `src/server-events.js:8-21` |
| — | `src/server-init.js` (npm `init`) only runs `addMissingConfigValues('./config.yaml')` | `src/server-init.js:8-12`, `package.json:120` |
| — | `src/server-global.js` sets `FORCE_GLOBAL_MODE` then imports `server.js` | `src/server-global.js:2-3` |
| — | Electron wrapper listens for `server-started` and opens a `BrowserWindow` | `src/electron/index.js:24-40` |

### 1.2 Command-line arguments (`src/command-line.js:110-267`)

All default `null` ⇒ fall back to config (`:300-326`), then hard default (`:57-96`).

| Flag | Type | Config key | Hard default |
|---|---|---|---|
| `--global` | bool | — (forces env-paths dirs; ignores `--configPath/--dataRoot`) | false (`:269-278`) |
| `--configPath` | string | — | `./config.yaml` |
| `--dataRoot` | string | `dataRoot` | `./data` |
| `--port` | number | `port` | 8000 |
| `--listen` | bool | `listen` | false |
| `--listenAddressIPv4` / `--listenAddressIPv6` | string | `listenAddress.ipv4/ipv6` | `0.0.0.0` / `[::]` |
| `--enableIPv4` / `--enableIPv6` | string (`true/false/auto`) | `protocol.ipv4/ipv6` | true / false |
| `--dnsPreferIPv6` | bool | `dnsPreferIPv6` | false |
| `--browserLaunchEnabled` (`--autorun` deprecated) | bool | `browserLaunch.enabled` | false (yaml ships `true`) |
| `--browserLaunchHostname` (`--autorunHostname`) | string | `browserLaunch.hostname` | `auto` |
| `--browserLaunchPort` (`--autorunPortOverride`) | number | `browserLaunch.port` | -1 |
| `--browserLaunchAvoidLocalhost` (`--avoidLocalhost`) | bool | `browserLaunch.avoidLocalhost` | false |
| `--corsProxy` | bool | `enableCorsProxy` | false |
| `--disableCsrf` | bool | `disableCsrfProtection` | false |
| `--ssl`, `--certPath`, `--keyPath`, `--keyPassphrase` | | `ssl.*` | false, `certs/cert.pem`, `certs/privkey.pem`, '' |
| `--whitelist` | bool | `whitelistMode` | true |
| `--basicAuthMode` | bool | `basicAuthMode` | false |
| `--enableKeepAlive` | bool | `enableKeepAlive` | false |
| `--requestProxyEnabled`, `--requestProxyUrl`, `--requestProxyBypass` (array) | | `requestProxy.*` | false, '', [] |
| `--heartbeatInterval` | number (s) | `heartbeatInterval` | 0 |

Listen URL rules: when `listen=false` binds `127.0.0.1` / `[::1]`, else the configured address if it is a valid IP, else `0.0.0.0` / `[::]` (`src/command-line.js:327-342`). Browser-launch hostname `auto` ⇒ `localhost`, `[::1]` or `127.0.0.1` (`:343-359`).

### 1.3 Environment variables

| Variable | Effect | Cite |
|---|---|---|
| `SILLYTAVERN_<KEY>` | **Any** config key: upper-case, dots→`_` (e.g. `SILLYTAVERN_EXTENSIONS_MODELS_SPEECHTOTEXT`). Takes precedence over config.yaml; JSON-parsed if the default is an object | `src/util.js:36-38,88-98` |
| deprecated `SILLYTAVERN_<OLDKEY>` | Rewritten to the new key on boot | `src/config-init.js:179-189` |
| `SILLYTAVERN_SSL_KEYPASSPHRASE` | Suggested for key passphrase (generic rule above) | `default/config.yaml:56` |
| `SILLYTAVERN_GIT_BACKEND` | `plugins.js` CLI git backend (`auto/system/builtin`) | `plugins.js:18` |
| `SILLYTAVERN_HEARTBEATINTERVAL`, `SILLYTAVERN_DATAROOT` | Docker healthcheck script | `src/healthcheck.js:6,17` |
| `all_proxy`, `no_proxy` | **Written** by request proxy; read by `proxy-agent` (proxy-from-env) | `src/request-proxy.js:41-47` |
| `NODE_ENV` | Logged only | `server.js:5` |

### 1.4 `config.yaml` — every key (`default/config.yaml`)

`getConfigValue(key, default, type)` reads env → cached YAML → default (`src/util.js:55-109`). Config is cached after first read; changes need restart.

| Key (yaml line) | Default | Effect | Consumer |
|---|---|---|---|
| `dataRoot` (3) | `./data` | Data root dir | `src/command-line.js:290` |
| `listen` (6) | false | Bind all interfaces; triggers security checks | `src/command-line.js:304`, `src/users.js:152-196` |
| `listenAddress.ipv4/ipv6` (9-10) | `0.0.0.0` / `[::]` | Listen address when `listen` | `src/command-line.js:305-306` |
| `protocol.ipv4/ipv6` (15-16) | true / false | Enable families; `auto` probes interfaces | `src/command-line.js:307-308`, `src/server-startup.js:402-445` |
| `dnsPreferIPv6` (18) | false | `dns.setDefaultResultOrder` | `src/server-main.js:465-477` |
| `browserLaunch.enabled` (22) | true | Auto-open browser | `src/command-line.js:311`, `src/server-main.js:367-395` |
| `browserLaunch.browser` (27) | `default` | firefox/chrome/edge/brave via `open.apps`; ignored on Android | `src/server-main.js:365,373-388` |
| `browserLaunch.hostname/port/avoidLocalhost` (31,35,38) | auto / -1 / false | Launch URL | `src/command-line.js:312-314` |
| `port` (40) | 8000 | Listen port | `src/command-line.js:303` |
| `heartbeatInterval` (43) | 0 | Write `DATA_ROOT/heartbeat.json` every N s | `src/server-main.js:397-417` |
| `enableKeepAlive` (46) | false | Global http/https Agent keepAlive | `src/server-main.js:100-101` |
| `ssl.enabled/certPath/keyPath/keyPassphrase` (50-57) | false, `./certs/*.pem`, '' | HTTPS | `src/command-line.js:317-320`, `src/server-startup.js:248-296` |
| `whitelistMode` (60) | true | IP whitelist middleware | `src/command-line.js:321`, `src/server-main.js:145-148` |
| `enableForwardedWhitelist` (62) | true (code default false) | Also check X-Real-IP/XFF | `src/middleware/whitelist.js:13,70` |
| `whitelist` (64) | `::1`, `127.0.0.1` | IPs/CIDRs; legacy `whitelist.txt` overrides | `src/middleware/whitelist.js:12-29` |
| `whitelistDockerHosts` (68) | true | Add `host.docker.internal` IPs when `is-docker` | `src/middleware/whitelist.js:35-51` |
| `basicAuthMode` (70) | false | HTTP Basic (only applied when `listen`) | `src/server-main.js:141-143` |
| `basicAuthUser.username/password` (72-74) | user/password | Basic creds | `src/middleware/basicAuth.js:33-34` |
| `enableCorsProxy` (76) | false | Mount `/proxy/:url(*)` | `src/server-main.js:257-265` |
| `cors.enabled/origin/methods/allowedHeaders/exposedHeaders/credentials/maxAge` (80-95) | true, `["null"]`, `["OPTIONS"]`, [], [], false, null | `cors()` options | `src/server-main.js:114-139` |
| `requestProxy.enabled/url/bypass` (99-105) | false, socks5 example, localhost/127.0.0.1 | Outbound proxy via `proxy-agent` | `src/request-proxy.js:19-63` |
| `enableUserAccounts` (107) | false | Multi-user + login page | `src/users.js:29` |
| `enableDiscreetLogin` (109) | false | Hide user list | `src/endpoints/users-public.js:10,30-32` |
| `perUserBasicAuth` (113) | false | Basic auth checks user passwords; auto-login | `src/middleware/basicAuth.js:13,53-73`, `src/users.js:32,900-936` |
| `sso.autheliaAuth` (124) | false | Trust `Remote-User` header | `src/users.js:30,805-807` |
| `sso.authentikAuth` (132) | false | Trust `X-Authentik-Username` | `src/users.js:31,815-817` |
| `sso.trustedProxies` (137) | `::1`,`127.0.0.1` | Source IPs allowed to send SSO headers (`*` = any) | `src/users.js:34,824-855` |
| `hostWhitelist.enabled/scan/hosts` (144-152) | false/true/[] | Host header validation (DNS-rebinding) | `src/middleware/hostWhitelist.js:8-45` |
| `privateAddressWhitelist.enabled/allowUnresolvedHosts/log.*/allowedRanges` (159-172) | false/false/true,false/loopback | SSRF filter replacing global agents | `src/server-main.js:338-348`, `src/private-request-filter.js:240-263` |
| `sessionTimeout` (178) | -1 | Cookie maxAge (s); -1 ⇒ 400 days; 0 ⇒ session cookie | `src/users.js:619-637` |
| `disableCsrfProtection` (180) | false | Skip csrf-sync | `src/command-line.js:316`, `src/server-main.js:167-210` |
| `securityOverride` (182) | false | Don't exit on insecure `listen` config | `src/users.js:137-147` |
| `logging.enableAccessLog` (187) | true | `DATA_ROOT/access.log` new-IP lines | `src/middleware/accessLogWriter.js:6,33-58` |
| `logging.minLogLevel` (189) | 0 | Console level filter | `src/util.js:1137` |
| `rateLimiting.preferRealIpHeader` (194) | false | Key limiter by forwarded IP | `src/middleware/basicAuth.js:15`, `src/endpoints/users-public.js:11`, `src/endpoints/users-private.js:16` |
| `rateLimiting.basicAuthMaxAttempts` (196) | 5 /60 s | | `src/middleware/basicAuth.js:16-21` |
| `rateLimiting.accountsLoginMaxAttempts` (198) | 5 /60 s | | `src/endpoints/users-public.js:12,19-22` |
| `rateLimiting.accountsRecoverMaxAttempts` (200) | 5 /300 s | | `src/endpoints/users-public.js:13,23-26` |
| `rateLimiting.accountsResetMaxAttempts` (202) | 5 | | `src/endpoints/users-private.js:15` |
| `forwardedHeaders.xRealIp/xForwardedFor/cfConnectingIp` (207-211) | true/true/false | Header trust for IP | `src/express-common.js:38-61` |
| `backups.allowFullDataBackup` (216) | true | `/api/users/backup` | `src/endpoints/users-private.js:158` |
| `backups.common.numberOfBackups` (220) | 50 | Per-prefix backup retention | `src/util.js:656-672` |
| `backups.chat.enabled/checkIntegrity/maxTotalBackups/throttleInterval` (223-229) | true/true/-1/10000 ms | Chat backup behaviour | `src/endpoints/chats.js:27-30` |
| `thumbnails.enabled/format/quality` (234-241) | true/jpg/95 | Thumbnail generation | `src/endpoints/thumbnails.js:20-22` |
| `thumbnails.dimensions` (243) | bg 160×90, avatar/persona 96×144 | | `src/endpoints/image-metadata.js:41-45` |
| `performance.lazyLoadCharacters` (249) | false | Shallow `/characters/all` | `src/endpoints/characters.js:35` |
| `performance.memoryCacheCapacity` (251) | 100mb | Parsed-card LRU (disabled on Android) | `src/endpoints/characters.js:30-33,199` |
| `performance.useDiskCache` (253) | true | node-persist card cache `_cache/characters` | `src/endpoints/characters.js:36,39-159` |
| `performance.requestCompression.enabled/minPayloadSize/maxPayloadSize/timeout` (257-263) | false/256kb/8mb/4000 (code 3000) | Advertised to client for gzip uploads | `src/endpoints/settings.js:17-20,288-293` |
| `cacheBuster.enabled/userAgentPattern` (269-272) | false/'' | `Clear-Site-Data: "cache"` on first `/` | `src/middleware/cacheBuster.js:28-104` |
| `allowKeysExposure` (275) | false | Allow `/api/secrets/view|find`, include secrets in backup | `src/endpoints/secrets.js:108`, `src/users.js:1174` |
| `skipContentCheck` (277) | false | Skip default-content seeding | `src/endpoints/content-manager.js:217-219` |
| `whitelistImportDomains` (279) | localhost, cdn.discordapp.com, files.catbox.moe, raw.githubusercontent.com | Generic URL import / asset download hosts | `src/endpoints/content-manager.js:22,972-974` |
| `requestOverrides` (294) | [] | Per-host header overrides for Kobold/TextGen | `src/additional-headers.js:202-216` |
| `extensions.enabled` (299) | true | Feature-guard `/api/extensions/*` & third-party static | `src/endpoints/extensions.js:72-79`, `src/users.js:1219` |
| `extensions.autoUpdate` (301) | true | Client flag | `src/endpoints/settings.js:15` |
| `extensions.models.autoDownload` (304) | true | HF downloads (else `local_files_only`) | `src/transformers.js:136-138` |
| `extensions.models.classification/captioning/embedding/speechToText/textToSpeech` (306-310) | see §5.3 | ONNX model IDs | `src/transformers.js:19-50,75-85`, `src/endpoints/vectors.js:229` |
| `git.backend` (317) | auto | system git vs isomorphic-git for **clone only** | `src/endpoints/extensions.js:12,110`, `src/git/client.js:19-37` |
| `enableDownloadableTokenizers` (321) | true | Fetch tokenizer JSONs from GitHub | `src/endpoints/tokenizers.js:60,110-112` |
| `promptPlaceholder` (324) | `[Start a new chat]` | Strict post-processing filler | `src/prompt-converters.js:4` |
| `openai.randomizeUserId` (327) | false | Random `user` field | `src/endpoints/backends/chat-completions.js:2296` |
| `openai.captionSystemPrompt` (331) | '' | System msg for captions | `src/endpoints/openai.js:131` |
| `deepl.formality` (335) | default | DeepL formality | `src/endpoints/translate.js:218` |
| `mistral.enablePrefix` (340) | false | Assistant prefix | `src/prompt-converters.js:709` |
| `ollama.keepAlive/batchSize` (347,351) | -1/-1 | Ollama `keep_alive`, `num_batch` | `src/endpoints/backends/text-completions.js:386-397` |
| `claude.enableSystemPromptCache/cachingAtDepth/extendedTTL/enableAdaptiveThinking` (360-376) | false/-1/false/false (code default true) | Anthropic caching/thinking | `src/endpoints/backends/chat-completions.js:106-112` |
| `gemini.apiVersion` (380) | v1beta | Gemini URL version | `src/endpoints/backends/chat-completions.js:679,1911`, `src/endpoints/google.js:223` |
| `gemini.thoughtSignatures` (382) | true | | `src/prompt-converters.js:34` |
| `gemini.enableSystemPromptCache` (388) | false | OpenRouter Gemini caching | `src/endpoints/backends/chat-completions.js:2370` |
| `gemini.image.personGeneration` (392) | allow_adult | Imagen param | `src/endpoints/google.js:442` |
| `enableServerPlugins` (394) | false | Load `./plugins` | `src/plugin-loader.js:10,46-48` |
| `enableServerPluginsAutoUpdate` (396) | true | `git pull` plugins on boot (needs `git`) | `src/plugin-loader.js:11,237-293` |
| *(legacy)* `cookieSecret` | — | Migrated into `DATA_ROOT/cookie-secret.txt`, then removed | `src/users.js:587-592`, `src/config-init.js:95-100` |

### 1.5 Data-root layout

Global (`DATA_ROOT`, default `./data`):

| Path | Content | Cite |
|---|---|---|
| `_storage/` | node-persist store: user records `user:<handle>` (`{handle,name,created,password(scrypt b64),salt,enabled,admin}`) and `avatar:<handle>` data-URLs; one file per key | `src/users.js:27-28,52-61,556-575`, `src/constants.js:54-62` |
| `_uploads/` | multer temp files (field `avatar`); emptied on boot | `src/server-main.js:268-270`, `src/users.js:198-218`, `src/constants.js:219` |
| `_cache/` | transformers.js HF model cache; downloaded tokenizer JSONs; `_cache/characters/` node-persist parsed-card cache | `src/transformers.js:87-115,134`, `src/endpoints/tokenizers.js:97-125`, `src/endpoints/characters.js:68-70` |
| `_webpack/<hash>/{cache,output/lib.js}` | Compiled client vendor bundle (non-Docker) | `webpack.config.js:63-124` |
| `_errors/{forbidden-by-whitelist,host-not-allowed,unauthorized,url-not-found}.html` | Error pages (global seeded content) | `src/users.js:491-530`, `src/endpoints/content-manager.js:374-383` |
| `_css/user.css` | Served at `/css/user.css` | `src/middleware/userCss.js:8-17` |
| `cookie-secret.txt` | 64-byte b64 cookie-session secret | `src/users.js:42,577-597` |
| `content.log`, `access.log`, `heartbeat.json` | seeding log; new-IP log; heartbeat | `src/endpoints/content-manager.js:204-207`, `src/middleware/accessLogWriter.js:10`, `src/server-main.js:400` |
| `<handle>/` | One per user (`default-user` when accounts off) | `src/users.js:683-697,955-964` |

Per user (`DATA_ROOT/<handle>/`, template `src/constants.js:16-48`):

| Subpath (const line) | Lives there |
|---|---|
| `settings.json` | Whole client settings blob (`src/constants.js:9`, §4.8) |
| `secrets.json` | API keys (§4.9) |
| `stats.json`, `image-metadata.json`, `content.log` | usage stats (`src/endpoints/stats.js:13`), image index (`src/endpoints/image-metadata.js:15`), per-user seed log (`src/endpoints/content-manager.js:195`) |
| `characters/` (29) | `*.png` cards; `characters/<name>/` = sprites, `backgrounds/`, asset categories (`src/endpoints/sprites.js:28-37`, `src/charx.js:309-380`, `src/endpoints/assets.js:333`) |
| `chats/<charFileBase>/` (28) | `*.jsonl` chats per character |
| `groups/` (26), `group chats/` (27) | `<id>.json` groups; `<chatId>.jsonl` |
| `worlds/` (22) | Lorebook `*.json` |
| `User Avatars/` (24) | Persona images |
| `backgrounds/` (30) | Chat backgrounds |
| `thumbnails/{bg,avatar,persona}` (18-21) | Thumbnail cache |
| `user/` (23), `user/images/` (25), `user/files/` (43), `user/workflows/` (42) | Public user files; gallery images; chat attachments; ComfyUI workflows |
| `KoboldAI Settings/`, `NovelAI Settings/`, `OpenAI Settings/`, `TextGen Settings/` (31-34) | API presets |
| `instruct/`, `context/`, `sysprompt/`, `reasoning/` (38-39,46-47) | Prompt templates |
| `themes/`, `movingUI/`, `QuickReplies/` (35-36,40) | UI presets |
| `extensions/` (37) | User-scoped third-party extensions (git clones) |
| `assets/` (41) | Asset downloads (bgm, ambient, blip, live2d, vrm, character, temp) (`src/endpoints/assets.js:14`) |
| `vectors/` (44) | vectra indexes `vectors/<source>/<collection>/<model>/index.json` |
| `backups/` (45) | `chat_*.jsonl`, `settings_<handle>_*.json`, `secrets_migration_*.json`, `_sysprompt/`, `_group_metadata_update/` |

Outside the data root: `public/scripts/extensions/third-party/` (global extensions, `src/constants.js:5-6`), server-root `backups/_migration` (`src/users.js:382`), `plugins/` (`src/server-main.js:312`), `config.yaml`, legacy `whitelist.txt` (`src/middleware/whitelist.js:12`), `certs/`.

### 1.6 Default content seeding (`default/content/index.json`)

* Index = 169 entries `{filename, type}`; types: instruct 38, context 34, novel_preset 24, background 23, sysprompt 13, kobold_preset 6, textgen_preset 6, theme 5, reasoning 5, error_page 4, workflow 2, settings/character/sprites/world/avatar/openai_preset/moving_ui/quick_replies/stylesheet 1 each (counted from `default/content/index.json`). Optional `default/scaffold/index.json` is merged first (`src/endpoints/content-manager.js:254-289`).
* `CONTENT_TYPES` and target directory mapping: `src/endpoints/content-manager.js:37-58,326-388`. `error_page`/`stylesheet` are GLOBAL scope (→ `_errors`, `_css`); everything else per user (`:63-84`).
* Algorithm `seedContent`: skip if filename already in `content.log` (unless forced category); skip if target exists; `fs.cpSync` + `setPermissionsSync`; append to log (`src/endpoints/content-manager.js:135-188`). Runs for every user at boot (`src/server-main.js:303`, `:215-252`), on user creation, and on settings reset with `forceCategories=['settings']` (`src/endpoints/users-private.js:184-200`).
* Presets restore reads bundled defaults (`getDefaultPresets/getDefaultPresetFile`, `src/endpoints/content-manager.js:86-133`; `src/endpoints/presets.js:83-103`).

---

## 2. Middleware stack (registration order)

| # | Middleware | Condition | Notes | Cite |
|---|---|---|---|---|
| 1 | `helmet({contentSecurityPolicy:false})` | always | default security headers, no CSP | `src/server-main.js:104-106` |
| 2 | `compression()` | always | gzip responses (SSE also passes through) | `:107` |
| 3 | `response-time` | always | `X-Response-Time` | `:108` |
| 4 | `body-parser` json + urlencoded | always | **500 MB** limit; inflates gzip bodies sent by the client's optional request compression (`public/scripts/request-compression.js:119`) | `:110-111` |
| 5 | `cors` | `cors.enabled` | origin `"null"`, methods OPTIONS by default | `:113-139` |
| 6 | `basicAuthMiddleware` | `listen && basicAuthMode` | global creds or per-user scrypt check; RateLimiterMemory 5/60 s; 401 + `_errors/unauthorized.html` | `:141-143`, `src/middleware/basicAuth.js:18-85` |
| 7 | whitelist | `whitelistMode` | `ip-matching` CIDR/wildcards on socket IP (IPv4-mapped normalised) and optional forwarded IP; 403 Handlebars page | `:145-148`, `src/middleware/whitelist.js:57-104`, `src/express-common.js:17-31` |
| 8 | `hostWhitelistMiddleware` | always (enforces only if enabled) | `host-validation-middleware`; logs untrusted hosts | `:150`, `src/middleware/hostWhitelist.js:12-45` |
| 9 | access logger | `listen` | first-seen IP → console + `access.log` | `:152-154` |
| 10 | `cookie-session` | always | signed client-side cookie; name `session-<sha256(os.hostname())[:8]>`; `sameSite:lax`, `httpOnly`; maxAge per `sessionTimeout`; secret from `cookie-secret.txt` | `:156-162`, `src/users.js:577-637` |
| 11 | `setUserDataMiddleware` | always | accounts off ⇒ `req.user = default-user`; on ⇒ load `session.handle` from node-persist, check `enabled`, compare `session.version` = shake256(handle,password,salt) else 403 + clear session | `:164`, `src/users.js:943-1020` |
| 12 | CSRF (`csrf-sync`) | `!disableCsrf` | synchronizer token in `session.csrfToken`, header `X-CSRF-Token`, 32 bytes; `GET /csrf-token` → `{token}`; skipped for `/proxy/*` when CORS proxy on | `:166-210` |
| 13 | `GET /` | | redirect to `/login` if accounts on and no user; else `public/index.html`; cache-buster | `:214-222` |
| 14 | `GET /callback/:source?` | | OAuth PKCE return (OpenRouter) → 307 `/?source=&query=` | `:225-233` |
| 15 | `GET /login` | | auto-login (single passwordless user, Authelia, Authentik, per-user basic) else `public/login.html` | `:236`, `src/users.js:751-780,1040-1058` |
| 16 | webpack `lib.js` server | | serves compiled `lib.js` from `_webpack/.../output` | `:239-240`, `src/middleware/webpack-serve.js:13-24` |
| 17 | `userCssMiddleware` | | `/css/user.css` from `_css` | `:241` |
| 18 | `express.static(public)` | | client assets | `:242` |
| 19 | `/api/users` public router | | list/login/recover (pre-auth) | `:245` |
| 20 | `requireLoginMiddleware` | | 403 if no `req.user` — **everything below needs a session** | `:248`, `src/users.js:1027-1033` |
| 21 | `POST /api/ping` | | keep-alive; `?extend` touches session | `:249-255` |
| 22 | `/proxy/:url(*)` | `enableCorsProxy` | strips cookie/host/forwarding headers, forces `accept-encoding: gzip, deflate, br`, re-sends JSON body, pipes response | `:257-265`, `src/middleware/corsProxy.js:9-52` |
| 23 | `multer({dest:_uploads}).single('avatar')` | | **all** multipart uploads must use field name `avatar`; fieldSize 500 MB; latin1→utf8 filename fix | `:268-270`, `src/middleware/multerMonkeyPatch.js:19-30` |
| 24 | `GET /version` | | `{agent, pkgVersion, gitRevision, gitBranch, commitDate, isLatest}` | `:272-275`, `src/util.js:136-165` |
| 25 | legacy redirects (308) | | 50 old paths → `/api/...` | `src/server-startup.js:69-134` |
| 26 | private routers | | see §3; `users.js` router serves per-user static files | `src/server-startup.js:140-188` |
| 27 | server plugins `/api/plugins/<id>` | `enableServerPlugins` | registered later in `preSetupTasks` | `src/plugin-loader.js:213-223` |
| 28 | 404 | | `_errors/url-not-found.html` | `src/server-main.js:455-460` |

Other cross-cutting pieces:

| Component | Behaviour | Cite |
|---|---|---|
| Per-user static file router | `/backgrounds/*`, `/characters/*`, `/User%20Avatars/*`, `/assets/*`, `/user/images/*`, `/user/files/*` from the user dir; `/scripts/extensions/third-party/*` user-dir then global dir; path-traversal check `isPathUnderParent` | `src/users.js:1065-1127,1212-1219` |
| Filename validators | `validateAvatarUrlMiddleware` / `getFileNameValidationFunction(field)` reject `/`, `\0` (and `\` on Windows) | `src/middleware/validateFileName.js:3-44` |
| Admin guard | `requireAdminMiddleware` (403 non-admin) | `src/users.js:1129-1140` |
| Passwords | `crypto.scryptSync(password.normalize(), salt, 64)` b64; salt 16 random bytes | `src/users.js:604-606,644-646` |
| Recovery/reset | 6-digit codes printed to **server stdout**; 5-min cache; rate-limited | `src/endpoints/users-public.js:15-16,110-200`, `src/endpoints/users-private.js:234-300` |
| Offline recovery CLI | `node recover.js <handle> [password]` edits node-persist directly | `recover.js:4-19`, `src/recover-password.js:28-65` |
| Security self-check | listen without whitelist/basic/accounts ⇒ `process.exit(1)` unless `securityOverride` | `src/users.js:137-196` |
| Request proxy | `ProxyAgent` replaces `http(s).globalAgent`; env `all_proxy/no_proxy` | `src/request-proxy.js:19-63` |
| Private request filter | `PrivateRequestAgent` (http.Agent subclass) resolves DNS, blocks RFC1918/loopback/link-local/ULA unless whitelisted; replaces global agents when enabled; a strict instance is always used for `/api/search/visit` | `src/private-request-filter.js:15-34,160-263`, `src/endpoints/search.js:437` |
| fetch patch | global `fetch(file://…)` allowed only for `.wasm` inside server dir (lets onnxruntime-web load local WASM) | `src/fetch-patch.js:8-40` |
| Outbound HTTP | 30 modules use `node-fetch` (≈216 call sites) — subject to the global agents above | `grep "from 'node-fetch'" src` |
| Extra headers | per-backend auth headers from secrets + `requestOverrides` | `src/additional-headers.js:11-265` |

---

## 3. HTTP routes

Conventions: almost everything is `POST` with a JSON body (even reads); responses JSON unless noted. Prefix mounting: `src/server-startup.js:140-188`. "Handler" is `src/endpoints/<file>:<line>` of the route definition. "Req fields" are body/query/param names statically read in the handler (auto-extracted; nested/spread fields not shown). Multipart uploads always use field `avatar` (§2 #23).

### 3.1 Routes defined outside `src/endpoints`

| M | Path | Cite | Purpose |
|---|---|---|---|
| GET | `/csrf-token` | `src/server-main.js:192,205` | `{token}` (or `"disabled"`) |
| GET | `/` | `src/server-main.js:214` | index.html / redirect `/login` |
| GET | `/callback/:source?` | `src/server-main.js:225` | OAuth PKCE return |
| GET | `/login` | `src/server-main.js:236` | login page / auto-login |
| POST | `/api/ping` | `src/server-main.js:249` | 204, optional session touch |
| ALL | `/proxy/:url(*)` | `src/server-main.js:258,260` | CORS proxy or 404 |
| GET | `/version` | `src/server-main.js:272` | version JSON |
| USE | `/backgrounds/*`, `/characters/*`, `/User%20Avatars/*`, `/assets/*`, `/user/images/*`, `/user/files/*`, `/scripts/extensions/third-party/*` | `src/users.js:1213-1219` | per-user static files |
| ALL | `/api/plugins/<id>/*` | `src/plugin-loader.js:222` | server plugin routers |
| USE | 50 legacy paths (`/getcharacters`, `/savechat`, …) | `src/server-startup.js:84-133` | 308 → new API |

### 3.2 Streaming (SSE) forwarding

| Route | Mechanism | Cite |
|---|---|---|
| `/api/backends/chat-completions/generate` | When `stream:true`, every provider branch calls `forwardFetchResponse(remote, res)`: copies status (401→400), on error writes remote text and ends; on success **pipes the raw upstream body** (OpenAI-style SSE, Anthropic SSE, Gemini `alt=sse`, Cohere v2 events…) unchanged; client socket `close` destroys upstream + `AbortController.abort()` | `src/util.js:732-779`; branches `src/endpoints/backends/chat-completions.js:424,743,854,944,1043,1156,1262,1367,1479,1580,1661,1755,2693`; abort `:2631-2634` (per-provider `:243,675,812,…`) |
| `/api/backends/text-completions/generate` | `forwardFetchResponse` for OpenAI-compatible SSE; **Ollama** NDJSON is parsed and re-emitted as `data: {"choices":[{"text","thinking"}]}\n\n` + `data: [DONE]` | `src/endpoints/backends/text-completions.js:29-71,401-408`; KoboldCpp abort on disconnect `:73-96,286-293` |
| `/api/backends/kobold/generate` | `streaming` ⇒ `/api/extra/generate/stream` piped | `src/endpoints/backends/kobold.js:95-103` |
| `/api/novelai/generate` | `streaming` ⇒ `/ai/generate-stream` piped | `src/endpoints/novelai.js:268-274` |
| `/api/speech/elevenlabs/synthesize`, `/history-audio` | audio piped | `src/endpoints/speech.js:267,331` |
| `/proxy/*` | piped | `src/middleware/corsProxy.js:44` |
| `/api/users/backup` | zip streamed via `archiver.pipe(res)` | `src/users.js:1148-1185` |
| `/api/assets/download` | upstream body piped to a file (not to client) | `src/endpoints/assets.js:239` |

No WebSocket server exists (the `ws` dependency is not imported by `src/`); ComfyUI completion is polled over HTTP (`src/endpoints/stable-diffusion.js:589-602`).

### 3.3 Endpoint tables (all `src/endpoints/**`, 335 handlers)

Chat-completion `chat_completion_source` values (26): `src/constants.js:187-214`; text-completion `api_type` values (15): `src/constants.js:222-238`; secret key names: `src/endpoints/secrets.js:9-74`. `src/endpoints/backends/google-models.js` is a helper with no routes. Every non-GET route below requires the CSRF header (§2 #12) and, except `/api/users/{list,login,recover-step1,recover-step2}`, a logged-in session (§2 #20).

#### `src/endpoints/anthropic.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/anthropic/caption-image` | anthropic.js:8 | Caption image via Claude Messages API (key from secrets or reverse_proxy+proxy_password) → `{caption}` | body: image, model, prompt, proxy_password, reverse_proxy |

#### `src/endpoints/assets.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/assets/get` | assets.js:108 | List downloaded assets per category (bgm, ambient, blip, live2d, vrm, character, temp) in `<user>/assets` → `{category:[paths]}` |  |
| POST | `/api/assets/download` | assets.js:190 | Download `url` into `assets/<category>/<filename>` (stream piped to file; whitelisted hosts) | body: category, filename, url |
| POST | `/api/assets/delete` | assets.js:269 | Delete one asset file | body: category, filename |
| POST | `/api/assets/character` | assets.js:315 | List per-character assets `characters/<name>/<category>` (query `name`,`category`); live2d/vrm folder walk | query: category, name |

#### `src/endpoints/avatars.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/avatars/get` | avatars.js:17 | List persona images in `User Avatars/` → string[] |  |
| POST | `/api/avatars/delete` | avatars.js:22 | Delete persona image (+thumbnail) | body: avatar; mw: validateName('avatar') |
| POST | `/api/avatars/upload` | avatars.js:41 | Multipart `avatar` → crop (query `crop` JSON) → PNG into `User Avatars/` → `{path}` | body: overwrite_name; query: crop; multipart `avatar`; mw: validateName('overwrite_name') |

#### `src/endpoints/azure.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/azure/list` | azure.js:8 | List Azure TTS voices for `region` (key `api_key_azure_tts`) | body: region |
| POST | `/api/azure/generate` | azure.js:46 | Azure TTS synth → audio bytes | body: region, text, voice |

#### `src/endpoints/backends/chat-completions.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/backends/chat-completions/status` | backends/chat-completions.js:1778 | Fetch model list / connectivity for `chat_completion_source` → provider `/models` JSON (+`bias` etc.) | body: azure_api_version, azure_base_url, azure_deployment_name, chat_completion_source, custom_include_headers, custom_url, pollinations_endpoint, proxy_password, reverse_proxy, secret_id, siliconflow_endpoint, workers_ai_account_id |
| POST | `/api/backends/chat-completions/bias` | backends/chat-completions.js:2158 | Tokenize logit-bias entries (body = `[{text,value}]`, query `model`) → `{tokenId: bias}` | query: model |
| POST | `/api/backends/chat-completions/generate` | backends/chat-completions.js:2242 | **Main chat-completion proxy**. Switches on `chat_completion_source` (26 sources) → provider-specific converters; `stream:true` ⇒ remote SSE piped verbatim (SSE) | body: allow_fallbacks, chat_completion_source, chat_id, custom_exclude_body, custom_include_body, custom_include_headers, custom_prompt_post_processing, custom_url, enable_web_search, frequency_penalty, include_reasoning, json_schema, logit_bias, logprobs … |
| POST | `/api/backends/chat-completions/multimodal-models/pollinations` | backends/chat-completions.js:2733 | List vision-capable models (Pollinations) |  |
| POST | `/api/backends/chat-completions/multimodal-models/aimlapi` | backends/chat-completions.js:2759 | List vision-capable models (AIMLAPI) |  |
| POST | `/api/backends/chat-completions/multimodal-models/nanogpt` | backends/chat-completions.js:2782 | List vision-capable models (NanoGPT) |  |
| POST | `/api/backends/chat-completions/multimodal-models/electronhub` | backends/chat-completions.js:2805 | List vision-capable models (ElectronHub) |  |
| POST | `/api/backends/chat-completions/multimodal-models/chutes` | backends/chat-completions.js:2823 | List vision-capable models (Chutes) |  |
| POST | `/api/backends/chat-completions/multimodal-models/mistral` | backends/chat-completions.js:2854 | List vision-capable models (Mistral) |  |
| POST | `/api/backends/chat-completions/multimodal-models/xai` | backends/chat-completions.js:2882 | List vision-capable models (xAI) |  |
| POST | `/api/backends/chat-completions/multimodal-models/moonshot` | backends/chat-completions.js:2915 | List vision-capable models (Moonshot) |  |
| POST | `/api/backends/chat-completions/multimodal-models/workers_ai` | backends/chat-completions.js:2944 | List vision-capable models (Cloudflare Workers AI, needs account id) | body: workers_ai_account_id |
| POST | `/api/backends/chat-completions/process` | backends/chat-completions.js:2979 | Apply prompt post-processing (`type` = merge/semi/strict/single/tools…) to `messages` without sending | body: messages, type |

#### `src/endpoints/backends/kobold.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/backends/kobold/generate` | backends/kobold.js:11 | KoboldAI/KoboldCpp generate; `streaming` ⇒ `/api/extra/generate/stream` piped as SSE; retries 50×2.5 s on 503 | body: api_server, can_abort, grammar, gui_settings, max_context_length, max_length, min_p, mirostat, mirostat_eta, mirostat_tau, prompt, rep_pen, rep_pen_range, rep_pen_slope … |
| POST | `/api/backends/kobold/status` | backends/kobold.js:143 | Probe `/v1/model`,`/v1/info/version`,`/extra/version` → `{model, koboldUnitedVersion, koboldCppVersion}` | body: api_server |
| POST | `/api/backends/kobold/transcribe-audio` | backends/kobold.js:190 | Multipart audio → KoboldCpp `/api/extra/transcribe` | body: server; multipart `avatar` |
| POST | `/api/backends/kobold/embed` | backends/kobold.js:241 | KoboldCpp embeddings for `items` → vectors | body: items, server |

#### `src/endpoints/backends/text-completions.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/backends/text-completions/status` | backends/text-completions.js:100 | Model list/health for `api_type` (ooba, vllm, aphrodite, tabby, koboldcpp, togetherai, llamacpp, ollama, infermaticai, dreamgen, openrouter, featherless, huggingface, generic, mancer) → `{result, data}` | body: api_server, api_type |
| POST | `/api/backends/text-completions/props` | backends/text-completions.js:234 | llama.cpp `/props` (chat template, ctx) for `api_server` | body: api_server, api_type, model |
| POST | `/api/backends/text-completions/generate` | backends/text-completions.js:272 | **Main text-completion proxy**; URL suffix per api_type; `stream` ⇒ SSE pipe; Ollama NDJSON re-wrapped to SSE `data: {choices:[{text,thinking}]}` + `[DONE]`; socket close ⇒ abort (+KoboldCpp `/api/extra/abort`) | body: allow_fallbacks, api_server, api_type, model, num_batch, prompt, provider, quantizations, stop, stream |
| POST | `/api/backends/text-completions/ollama/download` | backends/text-completions.js:445 | Ollama `/api/pull` model `name` | body: api_server, name |
| POST | `/api/backends/text-completions/ollama/caption-image` | backends/text-completions.js:475 | Ollama `/api/generate` with image → `{caption}` | body: image, model, prompt, server_url |
| POST | `/api/backends/text-completions/llamacpp/props` | backends/text-completions.js:521 | llama.cpp `/props` | body: server_url |
| POST | `/api/backends/text-completions/llamacpp/slots` | backends/text-completions.js:549 | llama.cpp slot save/restore/erase/info (`action`,`id_slot`,`filename`) | body: action, filename, id_slot, server_url |
| POST | `/api/backends/text-completions/tabby/download` | backends/text-completions.js:600 | TabbyAPI `/v1/download` model (admin key) | body: api_server |

#### `src/endpoints/backgrounds.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/backgrounds/all` | backgrounds.js:14 | List backgrounds → `{images:[{filename,isAnimated}], config:{width,height}}` |  |
| POST | `/api/backgrounds/folders` | backgrounds.js:45 | List background virtual folders (from image-metadata) |  |
| POST | `/api/backgrounds/delete` | backgrounds.js:68 | Delete background `bg` + thumbnail | body: bg; mw: validateName('bg') |
| POST | `/api/backgrounds/rename` | backgrounds.js:100 | Rename background file | body: new_bg, old_bg |
| POST | `/api/backgrounds/upload` | backgrounds.js:135 | Multipart `avatar` → `backgrounds/<originalname>` | multipart `avatar` |

#### `src/endpoints/backups.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/backups/chat/get` | backups.js:9 | List chat backups (`backups/chat_*.jsonl`) with chat info |  |
| POST | `/api/backups/chat/delete` | backups.js:32 | Delete one chat backup (`name` must start with `chat_`) | body: name |
| POST | `/api/backups/chat/download` | backups.js:54 | Send backup file | body: name |

#### `src/endpoints/caption.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/extra/caption` | caption.js:8 | Local transformers.js image-to-text on base64 `image` → `{caption}` | body: image |

#### `src/endpoints/characters.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/characters/create` | characters.js:1024 | Create card from form fields (+optional multipart avatar, crop) → writes PNG with `chara`/`ccv3` → returns avatar file name | body: ch_name, file_name; query: crop; multipart `avatar`; mw: validateName('file_name') |
| POST | `/api/characters/rename` | characters.js:1053 | Rename card PNG + chat folder, re-key chats → `{avatar}` | body: avatar_url, new_name; mw: validateAvatarUrlMiddleware |
| POST | `/api/characters/edit` | characters.js:1101 | Rewrite card from full form (+optional new avatar) | body: avatar_url, ch_name, chat, create_date; query: crop; multipart `avatar`; mw: validateAvatarUrlMiddleware |
| POST | `/api/characters/edit-avatar` | characters.js:1142 | Replace image only, keep metadata | body: avatar_url; query: crop; multipart `avatar`; mw: validateAvatarUrlMiddleware |
| POST | `/api/characters/edit-attribute` | characters.js:1193 | Set single `field`=`value` in card JSON | body: avatar_url, ch_name, field, value; mw: validateAvatarUrlMiddleware |
| POST | `/api/characters/merge-attributes` | characters.js:1328 | Deep-merge partial JSON into one/many cards (`avatar`/`avatars`, `filter`); validated by TavernCardValidator | body: avatars, data, filter; mw: validateName('avatar') |
| POST | `/api/characters/delete` | characters.js:1416 | Delete card PNG (+chats if `delete_chats`) and thumbnail/cache | body: avatar_url, delete_chats; mw: validateAvatarUrlMiddleware |
| POST | `/api/characters/all` | characters.js:1466 | List all characters (full or shallow if `lazyLoadCharacters`) → array of card objects + `json_data`,`chat_size`,`date_last_chat` |  |
| POST | `/api/characters/get` | characters.js:1480 | Full card for `avatar_url` | body: avatar_url; mw: validateAvatarUrlMiddleware |
| POST | `/api/characters/chats` | characters.js:1499 | List chats of a character (`simple` → names only; `metadata` → include chat_metadata) | body: avatar_url, metadata, simple; mw: validateAvatarUrlMiddleware |
| POST | `/api/characters/import` | characters.js:1560 | Multipart `avatar` + `file_type` ∈ png|json|yaml|yml|charx|byaf (+`preserved_name`) → `{file_name}` | body: file_type; multipart `avatar` |
| POST | `/api/characters/duplicate` | characters.js:1601 | Copy PNG with `_N` suffix → `{path}` | body: avatar_url; mw: validateAvatarUrlMiddleware |
| POST | `/api/characters/export` | characters.js:1646 | `format` png (strips fav/chat, re-embeds) or json (V2 pretty) | body: avatar_url, format; mw: validateAvatarUrlMiddleware |

#### `src/endpoints/chats.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/chats/save` | chats.js:545 | Save JSONL (body `chat` array; `force` skips integrity) → `{ok}` or 400 `{error:"integrity"}`; throttled backup | body: avatar_url, chat, file_name, force; mw: validateAvatarUrlMiddleware |
| POST | `/api/chats/get` | chats.js:592 | Read `chats/<char>/<file_name>.jsonl` → array of objects (header first) | body: avatar_url, file_name; mw: validateAvatarUrlMiddleware |
| POST | `/api/chats/rename` | chats.js:621 | Rename chat file (char or group) | body: avatar_url, is_group, original_file, renamed_file; mw: validateAvatarUrlMiddleware |
| POST | `/api/chats/delete` | chats.js:654 | Delete chat file | body: avatar_url, chatfile; mw: validateAvatarUrlMiddleware |
| POST | `/api/chats/export` | chats.js:679 | Export chat: `jsonl` raw or plain text `name: mes` (skips is_system, prefers extra.display_text) → `{message, result}` | body: avatar_url, exportfilename, file, format, is_group; mw: validateAvatarUrlMiddleware |
| POST | `/api/chats/group/import` | chats.js:751 | Import group chat JSONL (multipart) → `{res: id}` | multipart `avatar` |
| POST | `/api/chats/import` | chats.js:771 | Import chat from ST JSONL, Ooba, Agnai, CAI, Kobold Lite, Chub, Risu JSON (multipart, `file_type`) | body: avatar_url, character_name, file_type, user_name; multipart `avatar`; mw: validateAvatarUrlMiddleware |
| POST | `/api/chats/group/get` | chats.js:872 | Read group chat `group chats/<id>.jsonl` | body: id |
| POST | `/api/chats/group/info` | chats.js:883 | Chat info (size, last message, metadata) for a group chat | body: id |
| POST | `/api/chats/group/delete` | chats.js:900 | Delete group chat file | body: id |
| POST | `/api/chats/group/save` | chats.js:922 | Save group chat (integrity checked) | body: chat, force, id |
| POST | `/api/chats/search` | chats.js:949 | Full-text search over a character’s or group’s chats (`query`) | body: avatar_url, group_id, query; mw: validateAvatarUrlMiddleware |
| POST | `/api/chats/recent` | chats.js:1054 | Most recent chats across all chars/groups (`max`, `pinned`, `metadata`) for welcome screen | body: max, metadata, pinned |

#### `src/endpoints/classify.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/extra/classify/labels` | classify.js:14 | Labels of local text-classification model → `{labels}` |  |
| POST | `/api/extra/classify` | classify.js:25 | Classify `text` (top-5, in-memory cache) → `{classification}` | body: text |

#### `src/endpoints/content-manager.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/content/importURL` | content-manager.js:978 | Import character/lorebook from URL: Chub, JanitorAI, Pygmalion, AICC, RisuRealm, Perchance, or generic PNG on `whitelistImportDomains` → file bytes + `Content-Type`/`Content-Disposition` | body: url |
| POST | `/api/content/importUUID` | content-manager.js:1071 | Same, by UUID (Janny/Risu/Perchance) | body: url |

#### `src/endpoints/data-maid.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/data-maid/report` | data-maid.js:673 | Scan for orphaned images/files/chats/thumbnails/backups → `{report, token}` |  |
| POST | `/api/data-maid/finalize` | data-maid.js:692 | Invalidate report `token` | body: token |
| GET | `/api/data-maid/view` | data-maid.js:721 | GET preview of a reported file by `hash` + `token` | query: hash, token |
| POST | `/api/data-maid/delete` | data-maid.js:770 | Delete reported files by `hashes` | body: hashes, token |

#### `src/endpoints/extensions.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/extensions/install` | extensions.js:92 | Clone git `url` (depth 1, optional `branch`; system git or isomorphic-git) into user or global (admin) third-party dir → manifest fields | body: branch, global, url |
| POST | `/api/extensions/update` | extensions.js:169 | `git pull` (simple-git ⇒ **system git required**) → `{shortCommitHash, extensionPath, isUpToDate, remoteUrl}` | body: extensionName, global |
| POST | `/api/extensions/branches` | extensions.js:217 | List local+remote branches (unshallows) — system git | body: extensionName, global |
| POST | `/api/extensions/switch` | extensions.js:266 | Checkout branch — system git | body: branch, extensionName, global |
| POST | `/api/extensions/move` | extensions.js:329 | Move extension between local/global (admin) | body: destination, extensionName, source |
| POST | `/api/extensions/version` | extensions.js:387 | Current commit/branch/up-to-date — system git | body: extensionName, global |
| POST | `/api/extensions/delete` | extensions.js:442 | rm -r extension folder | body: extensionName, global |
| GET | `/api/extensions/discover` | extensions.js:480 | GET list `{type: system|local|global, name}` of folders with manifest.json |  |

#### `src/endpoints/files.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/files/sanitize-filename` | files.js:13 | Return sanitized `fileName` | body: fileName |
| POST | `/api/files/upload` | files.js:28 | Write base64 `data` to `user/files/<name>` → `{path}` | body: data, name |
| POST | `/api/files/delete` | files.js:54 | Delete file under `user/files` | body: path |
| POST | `/api/files/verify` | files.js:78 | Check existence of `urls[]` under `user/files` → `{url: bool}` | body: urls |

#### `src/endpoints/google.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/google/caption-image` | google.js:234 | Gemini/Vertex multimodal caption | body: image, model, prompt |
| POST | `/api/google/list-voices` | google.js:291 | Static list of Google TTS voices |  |
| POST | `/api/google/generate-voice` | google.js:295 | Google (translate-tts style) TTS → audio | body: text, voice |
| POST | `/api/google/list-native-voices` | google.js:313 | Gemini native TTS voices |  |
| POST | `/api/google/generate-native-tts` | google.js:356 | Gemini TTS model → WAV (PCM wrapped by wavefile) | body: model, text, voice |
| POST | `/api/google/generate-image` | google.js:432 | Imagen / Gemini image generation (`api` makersuite|vertexai) → base64 | body: api, aspect_ratio, enhance, model, negative_prompt, prompt, seed |
| POST | `/api/google/generate-video` | google.js:498 | Veo video generation (long-running op polled) → base64 video | body: api, aspect_ratio, model, negative_prompt, prompt, seconds, seed |

#### `src/endpoints/groups.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/groups/all` | groups.js:113 | List group JSONs (+`chat_size`, `date_last_chat`) |  |
| POST | `/api/groups/create` | groups.js:156 | Create `groups/<Date.now()>.json` with defaults → group object | body: activation_strategy, allow_self_responses, auto_mode_delay, avatar_url, chat_id, chats, disabled_members, fav, generation_mode, generation_mode_join_prefix, generation_mode_join_suffix, members, name |
| POST | `/api/groups/edit` | groups.js:190 | Overwrite group JSON with body | body: id; mw: validateName('id') |
| POST | `/api/groups/delete` | groups.js:203 | Delete group + its chats | body: id; mw: validateName('id') |

#### `src/endpoints/horde.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/horde/text-workers` | horde.js:58 | AI Horde text workers (cached, `force` refresh) | body: force |
| POST | `/api/horde/text-models` | horde.js:96 | AI Horde text models | body: force |
| POST | `/api/horde/status` | horde.js:128 | Horde heartbeat |  |
| POST | `/api/horde/cancel-task` | horde.js:144 | Cancel text task | body: taskId |
| POST | `/api/horde/task-status` | horde.js:164 | Poll text task | body: taskId |
| POST | `/api/horde/generate-text` | horde.js:183 | Submit async text job (client polls task-status) |  |
| POST | `/api/horde/sd-samplers` | horde.js:214 | Horde image samplers |  |
| POST | `/api/horde/sd-models` | horde.js:224 | Horde image models |  |
| POST | `/api/horde/caption-image` | horde.js:235 | Horde interrogation (caption) | body: image |
| POST | `/api/horde/user-info` | horde.js:285 | Horde account info (key `api_key_horde`) |  |
| POST | `/api/horde/generate-image` | horde.js:309 | Submit+poll Horde image job → base64 | body: clip_skip, enable_hr, height, karras, model, negative_prompt, nfsw, prompt, restore_faces, sampler, sanitize, scale, seed, steps … |

#### `src/endpoints/image-metadata.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/image-metadata/folders/get` | image-metadata.js:485 | List virtual folders |  |
| POST | `/api/image-metadata/folders/create` | image-metadata.js:499 | Create virtual folder | body: name |
| POST | `/api/image-metadata/folders/set-thumbnails` | image-metadata.js:517 | Batch set folder cover images | body: updates |
| POST | `/api/image-metadata/folders/update` | image-metadata.js:535 | Rename/update folder | body: ...updates, id |
| POST | `/api/image-metadata/folders/delete` | image-metadata.js:556 | Delete folder | body: id |
| POST | `/api/image-metadata/folders/assign` | image-metadata.js:577 | Assign image paths to folder | body: id, paths |
| POST | `/api/image-metadata/folders/unassign` | image-metadata.js:601 | Unassign | body: id, paths |
| POST | `/api/image-metadata` | image-metadata.js:622 | Get/compute metadata (hash, aspectRatio, isAnimated, dominantColor) for `path`/`paths` of `type` | body: path, paths, type |
| POST | `/api/image-metadata/all` | image-metadata.js:704 | All metadata (optional `prefix`) | body: prefix |
| POST | `/api/image-metadata/cleanup` | image-metadata.js:732 | Drop entries for missing files |  |

#### `src/endpoints/images.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/images/upload` | images.js:39 | Save base64 `image` to `user/images/<ch_name>/<filename>.<format>` → `{path}` | body: ch_name, filename, format, image |
| POST | `/api/images/list/:folder?` | images.js:80 | List media in `user/images/<folder>` (filter `type` bitmask image/video/audio, sort) → string[] | body: folder, sortField, sortOrder, type; params: folder |
| POST | `/api/images/folders` | images.js:115 | List subfolders of `user/images` |  |
| POST | `/api/images/delete` | images.js:133 | Delete image by `path` | body: path |

#### `src/endpoints/minimax.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/minimax/generate-voice` | minimax.js:19 | MiniMax T2A v2 TTS → audio (hex-decoded) | body: apiHost, audioSampleRate, bitrate, format, language, model, pitch, speed, text, voiceId, volume |

#### `src/endpoints/moving-ui.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/moving-ui/save` | moving-ui.js:8 | Save MovingUI preset `movingUI/<name>.json` | body: name |

#### `src/endpoints/nanogpt.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/nanogpt/credits` | nanogpt.js:36 | NanoGPT balance |  |
| POST | `/api/nanogpt/models/providers` | nanogpt.js:104 | NanoGPT providers for `model` | body: model |

#### `src/endpoints/novelai.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/novelai/status` | novelai.js:133 | NovelAI subscription data |  |
| POST | `/api/novelai/generate` | novelai.js:167 | NovelAI text gen; `streaming` ⇒ `/ai/generate-stream` SSE pipe | body: bad_words_ids, generate_until_sentence, input, logit_bias_exp, math1_quad, math1_quad_entropy_scale, math1_temp, max_length, min_length, min_p, mirostat_lr, mirostat_tau, model, num_logprobs … |
| POST | `/api/novelai/generate-image` | novelai.js:300 | NAI Diffusion (zip response unzipped, optional upscale) → base64 PNG | body: decrisper, height, model, negative_prompt, prompt, sampler, scale, scheduler, seed, sm, sm_dyn, steps, upscale_ratio, variety_boost … |
| POST | `/api/novelai/generate-voice` | novelai.js:442 | NAI TTS → audio | body: text, voice |

#### `src/endpoints/openai.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/openai/caption-image` | openai.js:16 | Vision caption across `api` ∈ openai, openrouter, custom, ooba, koboldcpp, llamacpp, vllm, mistral, groq, xai, cohere, zai, aimlapi, electronhub, chutes, nanogpt, pollinations, moonshot, workers_ai → `{caption}` | body: api, custom_exclude_body, custom_include_body, custom_include_headers, image, model, pollinations_endpoint, prompt, proxy_password, reverse_proxy, server_url, workers_ai_account_id, zai_endpoint |
| POST | `/api/openai/generate-voice` | openai.js:273 | OpenAI TTS → audio | body: instructions, model, speed, text, voice |
| POST | `/api/openai/electronhub/generate-voice` | openai.js:321 | ElectronHub TTS | body: cfg_filter_top_k, cfg_scale, emotional_style, input, instructions, model, pitch_adjustment, speaker_transcript, speech_rate, speed, temperature, voice |
| POST | `/api/openai/electronhub/models` | openai.js:387 | ElectronHub model list |  |
| POST | `/api/openai/chutes/generate-voice` | openai.js:419 | Chutes TTS | body: input, speed, voice |
| POST | `/api/openai/chutes/models/embedding` | openai.js:461 | Chutes embedding models |  |
| POST | `/api/openai/nanogpt/models/embedding` | openai.js:497 | NanoGPT embedding models |  |
| POST | `/api/openai/siliconflow/models/embedding` | openai.js:534 | SiliconFlow embedding models | body: siliconflow_endpoint |
| POST | `/api/openai/workers-ai/models/embedding` | openai.js:575 | Workers AI embedding models | body: workers_ai_account_id |
| POST | `/api/openai/generate-image` | openai.js:619 | OpenAI Images (body passed through) → JSON |  |
| POST | `/api/openai/generate-video` | openai.js:653 | OpenAI Sora video (poll + download) → base64 | body: model, prompt, seconds, size |
| POST | `/api/openai/custom/generate-voice` | openai.js:756 | Custom OpenAI-compatible TTS (`provider_endpoint`) | body: input, model, provider_endpoint, response_format, speed, voice |
| POST | `/api/openai/transcribe-audio` | openai.js:856 | Whisper STT (multipart audio) via OpenAI |  |
| POST | `/api/openai/groq/transcribe-audio` | openai.js:862 | Whisper STT via Groq |  |
| POST | `/api/openai/mistral/transcribe-audio` | openai.js:868 | STT via Mistral |  |
| POST | `/api/openai/zai/transcribe-audio` | openai.js:874 | STT via Z.AI |  |
| POST | `/api/openai/chutes/transcribe-audio` | openai.js:880 | STT via Chutes | body: model; multipart `avatar` |

#### `src/endpoints/openrouter.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/openrouter/models/providers` | openrouter.js:10 | OpenRouter endpoints/providers for `model` | body: model |
| POST | `/api/openrouter/models/multimodal` | openrouter.js:73 | OpenRouter vision models |  |
| POST | `/api/openrouter/models/embedding` | openrouter.js:83 | OpenRouter embedding models |  |
| POST | `/api/openrouter/models/image` | openrouter.js:93 | OpenRouter image-output models |  |
| POST | `/api/openrouter/credits` | openrouter.js:103 | OpenRouter credits |  |
| POST | `/api/openrouter/image/generate` | openrouter.js:138 | Image gen via OpenRouter chat (modalities image) | body: aspect_ratio, model, prompt |

#### `src/endpoints/presets.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/presets/save` | presets.js:42 | Write preset JSON to folder by `apiId` (kobold/koboldhorde/novel/textgenerationwebui/openai/instruct/context/sysprompt/reasoning) → `{name}` | body: apiId, name, preset |
| POST | `/api/presets/delete` | presets.js:60 | Delete preset file | body: apiId, name |
| POST | `/api/presets/restore` | presets.js:83 | Return bundled default for `name` → `{isDefault, preset}` | body: apiId, name |

#### `src/endpoints/quick-replies.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/quick-replies/save` | quick-replies.js:10 | Save QR set `QuickReplies/<name>.json` | body: name |
| POST | `/api/quick-replies/delete` | quick-replies.js:21 | Delete QR set | body: name |

#### `src/endpoints/search.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/search/serpapi` | search.js:95 | SerpApi Google search | body: query |
| POST | `/api/search/transcript` | search.js:128 | Scrape YouTube page → caption track → transcript text/JSON | body: id, json, lang |
| POST | `/api/search/searxng` | search.js:165 | SearXNG query (`baseUrl`) → HTML/JSON | body: baseUrl, categories, preferences, query |
| POST | `/api/search/tavily` | search.js:219 | Tavily search | body: include_images, query |
| POST | `/api/search/koboldcpp` | search.js:268 | KoboldCpp `/api/extra/websearch` | body: query, url |
| POST | `/api/search/serper` | search.js:304 | Serper.dev web/image search | body: images, query |
| POST | `/api/search/zai` | search.js:346 | Z.AI web search | body: query |
| POST | `/api/search/visit` | search.js:392 | Fetch arbitrary public URL (SSRF guards: no IPs/ports/localhost; strict private-IP agent) → HTML text or raw bytes | body: html, url |

#### `src/endpoints/secrets.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/secrets/write` | secrets.js:511 | Add secret value for `key` (new id, becomes active) → `{id}` | body: key, label, value |
| POST | `/api/secrets/read` | secrets.js:529 | Masked state map `{key: [{id,value(masked),label,active}]}` |  |
| POST | `/api/secrets/view` | secrets.js:540 | Full secrets (403 unless `allowKeysExposure`) |  |
| POST | `/api/secrets/find` | secrets.js:560 | Unmasked value for key/id (403 unless exposure or URL-type key) | body: id, key |
| POST | `/api/secrets/delete` | secrets.js:588 | Delete a secret id (or key) | body: id, key |
| POST | `/api/secrets/rotate` | secrets.js:606 | Mark `id` active for `key` | body: id, key |
| POST | `/api/secrets/rename` | secrets.js:624 | Change label | body: id, key, label |
| POST | `/api/secrets/settings` | secrets.js:642 | `{allowKeysExposure}` |  |

#### `src/endpoints/settings.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/settings/save` | settings.js:206 | Write whole body to `<user>/settings.json` (atomic); arms 10-min throttled snapshot |  |
| POST | `/api/settings/get` | settings.js:219 | Returns `{settings (raw string), *_settings + *_names per API, world_names, themes, movingUIPresets, quickReplyPresets, instruct, context, sysprompt, reasoning, enable_extensions, enable_extensions_auto_update, enable_accounts, request_compression}` |  |
| POST | `/api/settings/get-snapshots` | settings.js:298 | List `backups/settings_<handle>_*.json` → `[{date,name,size}]` |  |
| POST | `/api/settings/load-snapshot` | settings.js:316 | Return snapshot content | body: name; mw: validateName('name') |
| POST | `/api/settings/make-snapshot` | settings.js:340 | Snapshot now → 204 |  |
| POST | `/api/settings/restore-snapshot` | settings.js:350 | Copy snapshot over settings.json → 204 | body: name; mw: validateName('name') |

#### `src/endpoints/speech.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/speech/recognize` | speech.js:42 | Local transformers.js Whisper on base64 WAV data-URI (`audio`,`lang`,`model`) → `{text}` | body: audio, lang, model |
| POST | `/api/speech/synthesize` | speech.js:61 | Local transformers.js SpeechT5 (`speaker` embedding b64) → `audio/wav` | body: model, speaker, text |
| POST | `/api/speech/pollinations/voices` | speech.js:88 | Pollinations voices | body: model |
| POST | `/api/speech/pollinations/generate` | speech.js:117 | Pollinations TTS → audio | body: model, text, voice |
| POST | `/api/speech/elevenlabs/voices` | speech.js:179 | ElevenLabs voices |  |
| POST | `/api/speech/elevenlabs/voice-settings` | speech.js:207 | ElevenLabs default voice settings |  |
| POST | `/api/speech/elevenlabs/synthesize` | speech.js:234 | ElevenLabs TTS; audio stream piped (`forwardFetchResponse`) | body: request, voiceId |
| POST | `/api/speech/elevenlabs/history` | speech.js:274 | ElevenLabs history |  |
| POST | `/api/speech/elevenlabs/history-audio` | speech.js:302 | History item audio (piped) | body: historyItemId |
| POST | `/api/speech/elevenlabs/voices/add` | speech.js:338 | Clone voice from data-URI `files` | body: description, files, labels, name |
| POST | `/api/speech/elevenlabs/recognize` | speech.js:390 | ElevenLabs STT (multipart) | body: model; multipart `avatar` |

#### `src/endpoints/sprites.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| GET | `/api/sprites/get` | sprites.js:118 | GET list sprites of character `name` (optionally subfolder) → `[{label, path}]` | query: name |
| POST | `/api/sprites/delete` | sprites.js:152 | Delete sprite by `label`/`spriteName` | body: label, name, spriteName |
| POST | `/api/sprites/upload-zip` | sprites.js:186 | Multipart ZIP → extract images into `characters/<name>/` | body: name; multipart `avatar` |
| POST | `/api/sprites/upload` | sprites.js:239 | Multipart single sprite → `characters/<name>/<spriteName>.<ext>` | body: label, name, spriteName; multipart `avatar` |

#### `src/endpoints/stable-diffusion.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/sd/ping` | stable-diffusion.js:31 | A1111/Forge/SD.Next reachability (`url`, basic `auth`) | body: auth, url |
| POST | `/api/sd/upscalers` | stable-diffusion.js:54 | A1111 upscalers (+latent) | body: auth, url |
| POST | `/api/sd/vaes` | stable-diffusion.js:108 | A1111 VAEs | body: auth, url |
| POST | `/api/sd/samplers` | stable-diffusion.js:140 | A1111 samplers | body: auth, url |
| POST | `/api/sd/schedulers` | stable-diffusion.js:166 | A1111 schedulers | body: auth, url |
| POST | `/api/sd/models` | stable-diffusion.js:192 | A1111 checkpoints | body: auth, url |
| POST | `/api/sd/get-model` | stable-diffusion.js:218 | Current checkpoint | body: auth, url |
| POST | `/api/sd/set-model` | stable-diffusion.js:238 | Set checkpoint and wait for progress idle | body: auth, model, url |
| POST | `/api/sd/generate` | stable-diffusion.js:297 | A1111 `/sdapi/v1/txt2img` (body passthrough); socket close ⇒ `/sdapi/v1/interrupt` | body: auth, url |
| POST | `/api/sd/sd-next/upscalers` | stable-diffusion.js:352 | SD.Next upscalers | body: auth, url |
| POST | `/api/sd/comfy/ping` | stable-diffusion.js:387 | ComfyUI `/system_stats` | body: url |
| POST | `/api/sd/comfy/samplers` | stable-diffusion.js:403 | ComfyUI KSampler samplers (`/object_info`) | body: url |
| POST | `/api/sd/comfy/models` | stable-diffusion.js:421 | ComfyUI checkpoints/unets | body: url |
| POST | `/api/sd/comfy/schedulers` | stable-diffusion.js:449 | ComfyUI schedulers | body: url |
| POST | `/api/sd/comfy/vaes` | stable-diffusion.js:467 | ComfyUI VAEs | body: url |
| POST | `/api/sd/comfy/workflows` | stable-diffusion.js:485 | List `user/workflows/*.json` |  |
| POST | `/api/sd/comfy/workflow` | stable-diffusion.js:495 | Read workflow file | body: file_name |
| POST | `/api/sd/comfy/save-workflow` | stable-diffusion.js:509 | Save workflow file | body: file_name, workflow |
| POST | `/api/sd/comfy/delete-workflow` | stable-diffusion.js:521 | Delete workflow file | body: file_name |
| POST | `/api/sd/comfy/rename-workflow` | stable-diffusion.js:534 | Rename workflow file | body: new_name, old_name; mw: validateName('old_name'), validateName('new_name') |
| POST | `/api/sd/comfy/generate` | stable-diffusion.js:562 | POST `/prompt`, poll `/history/<id>` every 100 ms, fetch `/view` → `{format, data(base64)}`; abort ⇒ `/interrupt` | body: auth, prompt, url |
| POST | `/api/sd/comfyrunpod/ping` | stable-diffusion.js:637 | RunPod serverless ComfyUI health | body: url |
| POST | `/api/sd/comfyrunpod/generate` | stable-diffusion.js:668 | RunPod ComfyUI run+poll | body: prompt, url |
| POST | `/api/sd/together/models` | stable-diffusion.js:739 | Together image models |  |
| POST | `/api/sd/together/generate` | stable-diffusion.js:778 | Together image gen | body: height, model, negative_prompt, prompt, seed, steps, width |
| POST | `/api/sd/sdcpp/ping` | stable-diffusion.js:834 | stable-diffusion.cpp server ping | body: url |
| POST | `/api/sd/sdcpp/models` | stable-diffusion.js:850 | sd.cpp models | body: url |
| POST | `/api/sd/sdcpp/generate` | stable-diffusion.js:867 | sd.cpp txt2img | body: batch_size, cfg_scale, clip_skip, height, model, negative_prompt, prompt, sampler_name, scheduler, seed, steps, url, width |
| POST | `/api/sd/drawthings/ping` | stable-diffusion.js:919 | Draw Things HTTP API ping | body: url |
| POST | `/api/sd/drawthings/get-model` | stable-diffusion.js:939 | Draw Things current model | body: url |
| POST | `/api/sd/drawthings/get-upscaler` | stable-diffusion.js:958 | Draw Things upscaler | body: url |
| POST | `/api/sd/drawthings/generate` | stable-diffusion.js:977 | Draw Things txt2img | body: auth, url |
| POST | `/api/sd/pollinations/models` | stable-diffusion.js:1013 | Pollinations image models |  |
| POST | `/api/sd/pollinations/generate` | stable-diffusion.js:1038 | Pollinations image → base64 | body: enhance, height, model, negative_prompt, prompt, seed, width |
| POST | `/api/sd/stability/generate` | stable-diffusion.js:1085 | Stability AI v2beta (multipart form-data built server-side) | body: model, payload |
| POST | `/api/sd/huggingface/generate` | stable-diffusion.js:1145 | HF Inference API image | body: model, prompt |
| POST | `/api/sd/electronhub/models` | stable-diffusion.js:1184 | ElectronHub image models |  |
| POST | `/api/sd/electronhub/generate` | stable-diffusion.js:1224 | ElectronHub image gen | body: model, prompt, quality, size |
| POST | `/api/sd/electronhub/sizes` | stable-diffusion.js:1282 | Supported sizes for model | body: model |
| POST | `/api/sd/chutes/models` | stable-diffusion.js:1310 | Chutes image models |  |
| POST | `/api/sd/chutes/generate` | stable-diffusion.js:1343 | Chutes image gen | body: guidance_scale, height, model, negative_prompt, prompt, steps, width |
| POST | `/api/sd/nanogpt/models` | stable-diffusion.js:1391 | NanoGPT image models |  |
| POST | `/api/sd/nanogpt/generate` | stable-diffusion.js:1430 | NanoGPT image gen |  |
| POST | `/api/sd/bfl/generate` | stable-diffusion.js:1473 | Black Forest Labs FLUX (submit + poll) | body: guidance, height, model, prompt, prompt_upsampling, seed, steps, width |
| POST | `/api/sd/falai/models` | stable-diffusion.js:1593 | fal.ai models |  |
| POST | `/api/sd/falai/generate` | stable-diffusion.js:1641 | fal.ai queue submit + poll | body: guidance, height, model, prompt, seed, steps, width |
| POST | `/api/sd/xai/generate` | stable-diffusion.js:1738 | xAI Grok image | body: aspect_ratio, model, prompt, resolution |
| POST | `/api/sd/aimlapi/models` | stable-diffusion.js:1796 | AIMLAPI image models |  |
| POST | `/api/sd/aimlapi/generate-image` | stable-diffusion.js:1837 | AIMLAPI image |  |
| POST | `/api/sd/zai/generate` | stable-diffusion.js:1880 | Z.AI CogView image | body: model, prompt, quality, size |
| POST | `/api/sd/zai/generate-video` | stable-diffusion.js:1957 | Z.AI CogVideo (poll) | body: aspect_ratio, model, prompt, quality, size |
| POST | `/api/sd/workersai/models` | stable-diffusion.js:2062 | Cloudflare Workers AI image models | body: account_id |
| POST | `/api/sd/workersai/generate` | stable-diffusion.js:2108 | Workers AI image | body: account_id, height, model, negative_prompt, prompt, scale, seed, steps, width |

#### `src/endpoints/stats.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/stats/get` | stats.js:444 | Return `stats.json` contents |  |
| POST | `/api/stats/recreate` | stats.js:452 | Rebuild stats by scanning all chats |  |
| POST | `/api/stats/update` | stats.js:465 | Overwrite stats with body |  |

#### `src/endpoints/themes.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/themes/save` | themes.js:10 | Save `themes/<name>.json` | body: name |
| POST | `/api/themes/delete` | themes.js:21 | Delete theme | body: name |

#### `src/endpoints/thumbnails.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| GET | `/thumbnail` | thumbnails.js:249 | GET `?type=bg|avatar|persona&file=…&animated=` → cached JPG/PNG thumb (Jimp), generated lazily; falls back to original |  |

#### `src/endpoints/tokenizers.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/tokenizers/llama/encode` | tokenizers.js:739 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/nerdstash/encode` | tokenizers.js:740 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/nerdstash_v2/encode` | tokenizers.js:741 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/mistral/encode` | tokenizers.js:742 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/yi/encode` | tokenizers.js:743 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/gemma/encode` | tokenizers.js:744 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/jamba/encode` | tokenizers.js:745 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/gpt2/encode` | tokenizers.js:746 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/claude/encode` | tokenizers.js:747 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/llama3/encode` | tokenizers.js:748 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/qwen2/encode` | tokenizers.js:749 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/command-r/encode` | tokenizers.js:750 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/command-a/encode` | tokenizers.js:751 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/nemo/encode` | tokenizers.js:752 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/deepseek/encode` | tokenizers.js:753 | `{text}` → `{ids, count, chunks}` |  |
| POST | `/api/tokenizers/llama/decode` | tokenizers.js:754 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/nerdstash/decode` | tokenizers.js:755 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/nerdstash_v2/decode` | tokenizers.js:756 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/mistral/decode` | tokenizers.js:757 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/yi/decode` | tokenizers.js:758 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/gemma/decode` | tokenizers.js:759 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/jamba/decode` | tokenizers.js:760 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/gpt2/decode` | tokenizers.js:761 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/claude/decode` | tokenizers.js:762 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/llama3/decode` | tokenizers.js:763 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/qwen2/decode` | tokenizers.js:764 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/command-r/decode` | tokenizers.js:765 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/command-a/decode` | tokenizers.js:766 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/nemo/decode` | tokenizers.js:767 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/deepseek/decode` | tokenizers.js:768 | `{ids}` → `{text, chunks}` |  |
| POST | `/api/tokenizers/openai/encode` | tokenizers.js:770 | Model-routed (query `model`): llama3/llama/mistral/yi/gemma/jamba/claude/qwen2/command-r/command-a/nemo/deepseek or tiktoken → `{ids,count,chunks}` | query: model |
| POST | `/api/tokenizers/openai/decode` | tokenizers.js:843 | Model-routed decode | query: model |
| POST | `/api/tokenizers/openai/count` | tokenizers.js:916 | Count tokens of chat `messages[]` (body) incl. per-message overhead → `{token_count}` | query: model |
| POST | `/api/tokenizers/remote/kobold/count` | tokenizers.js:1037 | Proxy KoboldCpp `/extra/tokencount` → `{count, ids}` | body: text, url |
| POST | `/api/tokenizers/remote/textgenerationwebui/encode` | tokenizers.js:1072 | Proxy backend tokenizer for `api_type` (ooba/tabby/koboldcpp/llamacpp/vllm/aphrodite) → `{count, ids}` | body: api_type, model, text, url |

#### `src/endpoints/translate.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/translate/libre` | translate.js:16 | LibreTranslate (`libre_url`, key) | body: lang, text |
| POST | `/api/translate/google` | translate.js:76 | google-translate-api-x (unofficial, no key) | body: lang, text |
| POST | `/api/translate/yandex` | translate.js:103 | Yandex translate (unofficial endpoint, `chunks`) | body: chunks, lang |
| POST | `/api/translate/lingva` | translate.js:159 | Lingva instance (`lingva_url`) | body: lang, text |
| POST | `/api/translate/deepl` | translate.js:203 | DeepL free/pro (`endpoint`), formality from config | body: endpoint, lang, text |
| POST | `/api/translate/onering` | translate.js:265 | OneRingTranslator instance | body: from_lang, lang, text, to_lang |
| POST | `/api/translate/deeplx` | translate.js:322 | DeepLX instance | body: lang, text |
| POST | `/api/translate/bing` | translate.js:378 | bing-translate-api (unofficial) | body: lang, text |

#### `src/endpoints/users-admin.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/users/get` | users-admin.js:36 | Admin: list all users (view models) | mw: requireAdminMiddleware |
| POST | `/api/users/disable` | users-admin.js:66 | Admin: disable user (not self) | body: handle; mw: requireAdminMiddleware |
| POST | `/api/users/enable` | users-admin.js:95 | Admin: enable user | body: handle; mw: requireAdminMiddleware |
| POST | `/api/users/promote` | users-admin.js:119 | Admin: grant admin | body: handle; mw: requireAdminMiddleware |
| POST | `/api/users/demote` | users-admin.js:143 | Admin: revoke admin (not self) | body: handle; mw: requireAdminMiddleware |
| POST | `/api/users/create` | users-admin.js:172 | Admin: create user (handle slugified, scrypt hash), ensure dirs + seed content | body: admin, handle, name, password; mw: requireAdminMiddleware |
| POST | `/api/users/delete` | users-admin.js:219 | Admin: delete user record (+`purge` rm -r data dir) | body: handle, purge; mw: requireAdminMiddleware |
| POST | `/api/users/slugify` | users-admin.js:251 | Admin: slugify `text` | body: text; mw: requireAdminMiddleware |

#### `src/endpoints/users-private.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/users/logout` | users-private.js:27 | Clear session → 204 |  |
| GET | `/api/users/me` | users-private.js:45 | GET current user view model |  |
| POST | `/api/users/change-avatar` | users-private.js:68 | Set user avatar (data URL stored in node-persist key `avatar:<handle>`) → 204 | body: avatar, handle |
| POST | `/api/users/change-password` | users-private.js:103 | Change password (old required unless admin) | body: handle, newPassword, oldPassword |
| POST | `/api/users/backup` | users-private.js:156 | Stream ZIP of whole user dir (archiver), excludes secrets.json unless exposure allowed | body: handle |
| POST | `/api/users/reset-settings` | users-private.js:184 | Verify password, delete settings.json, re-seed defaults | body: password |
| POST | `/api/users/change-name` | users-private.js:204 | Change display name | body: handle, name |
| POST | `/api/users/reset-step1` | users-private.js:234 | Generate 6-digit reset code **printed to server console** |  |
| POST | `/api/users/reset-step2` | users-private.js:261 | Verify code (+password) → wipe user data dir and re-seed | body: code, password |

#### `src/endpoints/users-public.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/users/list` | users-public.js:28 | Login-screen user list (204 if discreet login) |  |
| POST | `/api/users/login` | users-public.js:61 | Verify scrypt hash, set `session.handle` + `session.version`; rate-limited | body: handle, password |
| POST | `/api/users/recover-step1` | users-public.js:110 | 6-digit recovery code **printed to server console** (5-min cache) | body: handle |
| POST | `/api/users/recover-step2` | users-public.js:150 | Verify code, set new password | body: code, handle, newPassword |

#### `src/endpoints/vectors.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/vector/query` | vectors.js:472 | Embed `searchText` via `source`, vectra `queryItems(topK)` filtered by `threshold` → `{metadata, hashes}` | body: collectionId, searchText, source, threshold, topK |
| POST | `/api/vector/query-multi` | vectors.js:492 | Query several collections → `{[collectionId]: {hashes, metadata}}` | body: collectionIds, searchText, source, threshold, topK |
| POST | `/api/vector/insert` | vectors.js:512 | Batch-embed `items[{hash,text,index}]` (10/batch) and upsert | body: collectionId, items, source |
| POST | `/api/vector/list` | vectors.js:530 | Saved hashes in collection | body: collectionId, source |
| POST | `/api/vector/delete` | vectors.js:547 | Delete items by hash | body: collectionId, hashes, source |
| POST | `/api/vector/purge-all` | vectors.js:565 | rm -r `<user>/vectors` |  |
| POST | `/api/vector/purge` | vectors.js:583 | Delete one collection across sources | body: collectionId |

#### `src/endpoints/volcengine.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/volcengine/generate-voice` | volcengine.js:9 | Volcengine (ByteDance) TTS → audio | body: provider_endpoint, resource_id, speed, text, voice_speaker |

#### `src/endpoints/worldinfo.js`

| M | Path | Handler | Purpose / shape | Req fields |
|---|---|---|---|---|
| POST | `/api/worldinfo/list` | worldinfo.js:39 | List lorebooks `[{file_id, name, extensions}]` |  |
| POST | `/api/worldinfo/get` | worldinfo.js:71 | Read `worlds/<name>.json` (dummy `{entries:{}}` if missing) | body: name |
| POST | `/api/worldinfo/delete` | worldinfo.js:81 | Delete lorebook | body: name |
| POST | `/api/worldinfo/import` | worldinfo.js:99 | Multipart JSON (or client `convertedData`, e.g. from Novel/Agnai/Risu formats) → must contain `entries` → `{name}` | body: convertedData; multipart `avatar` |
| POST | `/api/worldinfo/edit` | worldinfo.js:134 | Write `{name, data}` (pretty JSON) → `{ok}` | body: data, name |

---

## 4. File formats

### 4.1 Character cards (PNG)

| Aspect | Detail | Cite |
|---|---|---|
| Container | Standard PNG; metadata in `tEXt` chunks inserted before `IEND`; chunk CRC via `crc32` | `src/character-card-parser.js:15-46`, `src/png/encode.js:9-68` |
| Keywords | `chara` = base64(UTF-8 JSON, V2); `ccv3` = base64(same JSON with `spec:"chara_card_v3"`, `spec_version:"3.0"`) | `src/character-card-parser.js:27-42` |
| Write | removes old `chara`/`ccv3` (case-insensitive), always writes both | `:15-46` |
| Read | prefers `ccv3`, falls back to `chara`; throws if neither | `:54-78` |
| Image | Jimp read → optional crop `{x,y,width,height,want_resize}` → cover to 512×768 when `want_resize` → PNG; fallback image `public/img/ai4.png` | `src/endpoints/characters.js:220-330`, `src/constants.js:358-360` |
| File name | `characters/<sanitized name>.png`; chat folder `chats/<same base>/` | `src/endpoints/characters.js:257-259,419` |
| Caching | memory LRU (`MemoryLimitedMap`, off on Android) + node-persist disk cache keyed `path-mtime` | `src/endpoints/characters.js:28-36,166-208`, `src/util.js:1148` |
| JSON shape written (V2 + ST mirrors) | top-level V1 mirror: `name, description, personality, scenario, first_mes, mes_example, creatorcomment, avatar:"none", chat, talkativeness, fav, tags, create_date`; `spec:"chara_card_v2", spec_version:"2.0"`; `data.{name, description, personality, scenario, first_mes, mes_example, creator_notes, system_prompt, post_history_instructions, tags, creator, character_version, alternate_greetings, extensions{talkativeness, fav, world, depth_prompt{prompt,depth(4),role("system")}, …}, character_book?}`; unknown keys preserved via `json_data` | `src/endpoints/characters.js:565-660` |
| `character_book` | built from linked lorebook (`data.extensions.world`): `{name, entries:[{id, keys, secondary_keys, comment, content, constant, selective, insertion_order, enabled, position(before_char/after_char), use_regex:true, extensions{position, depth, probability, group, sticky, cooldown, delay, match_*, triggers, …}}]}` | `src/endpoints/characters.js:663-727` |
| V1→V2 / V2 read | `convertToV2`, `readFromV2` (backfills talkativeness 0.5, fav false) | `src/endpoints/characters.js:450-562` |
| Validation | `TavernCardValidator` (V1/V2/V3) on merge-attributes | `src/validator/TavernCardValidator.js:35-144`, `src/endpoints/characters.js:1295` |
| Export | PNG: strip `fav`, `chat` (`unsetPrivateFields`) and rewrite chunks; JSON: V2 pretty-printed | `src/endpoints/characters.js:498-502,1646-1690` |
| Import formats | `png`, `json` (V1/V2/V3 or Pygmalion/ Agnai etc.), `yaml/yml`, `charx`, `byaf` | `src/endpoints/characters.js:1560-1599` |
| RisuAI sprites | embedded emotion images in V3 `assets` extracted to sprites folder | `src/endpoints/characters.js:22,982`, `src/endpoints/sprites.js:48-110` |

### 4.2 CharX (`.charx`, `src/charx.js`)

ZIP (SFX prefix tolerated by scanning for `PK\x03\x04`, `:16-31`). `card.json` (CCv2/CCv3) mandatory (`:68-72`). Asset URIs `embeded://`, `embedded://`, `__asset:` resolve to ZIP paths (`:9-10,112-150`). Icon = asset `type:"icon"`, name `main` preferred → becomes the PNG avatar (`:81-108,203-209`). Other image assets mapped (`:240-280`): `emotion`/`expression` → sprites in `characters/<name>/` (hyphenated names), `background` → `characters/<name>/backgrounds/`, others → `user/images/<name>/` (`:309-380`). Unzip via `yauzl` helpers (`src/util.js:207-260`).

### 4.3 BYAF (Backyard AI archive, `src/byaf.js`, types `src/types/byaf.d.ts`)

ZIP with `manifest.json` `{schemaVersion:1, createdAt, characters:[path], scenarios:[path], author?{name, backyardURL}}` (`src/byaf.js:310-321`, `byaf.d.ts:29-38`). Character JSON `{schemaVersion, id, name, displayName, isNSFW, persona, createdAt, updatedAt, loreItems[{key,value}], images[{path,label}]}` (`byaf.d.ts:16-27`). Scenario `{title, model, formattingInstructions, minP, temperature, repeatPenalty, topK, topP, exampleMessages, firstMessages, narrative, promptTemplate, grammar, messages[human|ai{outputs[]}], backgroundImage}` (`byaf.d.ts:40-77`). Mapping → V2 card (`src/byaf.js:248-270`: scenario→`scenario`, first message→`first_mes`, other scenarios' first messages→`alternate_greetings`, formattingInstructions→`system_prompt`, lore→`character_book`), each scenario → imported chat JSONL, backgrounds → `user/images/` (`src/endpoints/characters.js:803-880`, `src/byaf.js:332-430`).

### 4.4 Chat files (JSONL)

| Item | Detail | Cite |
|---|---|---|
| Location | `chats/<charBase>/<name>.jsonl`; group: `group chats/<chatId>.jsonl` | `src/endpoints/chats.js:549-551,878,930` |
| Default name | `<Char> - YYYY-MM-DD@HHhMMmSSsMSms` | `src/util.js:553-570`, `src/endpoints/characters.js:489` |
| Line 1 (header) | `{"chat_metadata":{…}, "user_name":"unused", "character_name":"unused"}`; `chat_metadata.integrity` = UUID, plus client keys (`note_prompt`, `note_interval`, `note_position`, `note_depth`, `note_role`, `timedWorldInfo`, `objective`, `variables`, `world_info`, …) | observed `data/default-user/chats/*/*.jsonl`; importer headers `src/endpoints/chats.js:133-137` |
| Lines 2..n | message objects: `name`, `is_user`, `is_system`, `send_date` (ISO or legacy humanized), `mes`, `extra{}`, optional `swipes[]`, `swipe_id`, `swipe_info[{send_date, gen_started, gen_finished, extra}]`, `force_avatar`, `original_avatar`, `gen_started`, `gen_finished` | importers `src/endpoints/chats.js:126-330`; client `public/script.js:2575,2623,2645,3707,6774` |
| `extra` (client-defined) | `api`, `model`, `reasoning`, `reasoning_duration`, `reasoning_signature`, `token_count`, `bias`, `display_text`, `media[]`, `files[]`, `inline_image`, `title`, `isSmallSys`, extension keys (e.g. `qvink_memory`) | `public/script.js:2204,2223,2662,3699,3791,5811,6681-6683,7035,10122` |
| Save | whole array re-serialised (`JSON.stringify` per line, `\n` joined), `write-file-atomic` | `src/endpoints/chats.js:532-543`, `src/util.js:1514` |
| Integrity | if `checkIntegrity` and not `force`, first line of on-disk file must carry the same `chat_metadata.integrity`, else 400 `{error:"integrity"}` | `src/endpoints/chats.js:337-369,532-569` |
| Read | split lines, `tryParse`, drop invalid | `src/endpoints/chats.js:577-591` |
| Imports | Ooba, Agnai, CAI (multiple histories), Kobold Lite, Chub (flattens swipes), Risu, ST JSONL | `src/endpoints/chats.js:126-330,771-870` |
| Group chats | same JSONL; legacy group `chat_metadata` migrated into chat header at boot | `src/endpoints/groups.js:26-110` |

### 4.5 Groups (`groups/<id>.json`)

`{id (Date.now()), name, members[avatar files], avatar_url, allow_self_responses, activation_strategy, generation_mode, disabled_members[], fav, chat_id, chats[], auto_mode_delay(5), generation_mode_join_prefix, generation_mode_join_suffix}` (`src/endpoints/groups.js:163-179`); `edit` overwrites with arbitrary client JSON (`:190-201`); deprecated `chat_metadata`/`past_metadata` stripped (`:14-24`).

### 4.6 World Info (`worlds/<name>.json`)

`{ "entries": { "<uid>": entry } , "originalData"?: <imported source> }`; must contain `entries` (`src/endpoints/worldinfo.js:99-160`). Entry keys (bundled `default/content/Eldoria.json`): `uid, key[], keysecondary[], comment, content, constant, selective, selectiveLogic, order, position, disable, displayIndex, excludeRecursion, preventRecursion, delayUntilRecursion, probability, useProbability, depth, group, groupOverride, groupWeight, scanDepth, caseSensitive, matchWholeWords, useGroupScoring, automationId, role, vectorized, sticky, cooldown, delay, addMemo` (+ `match*`, `triggers`, `ignoreBudget`, `outletName` read in `src/endpoints/characters.js:688-723`).

### 4.7 Presets (folder by `apiId`, `src/endpoints/presets.js:16-37`)

| apiId | Folder | Example keys (bundled default) |
|---|---|---|
| `kobold`, `koboldhorde` | `KoboldAI Settings/` | `temp, top_p, top_k, top_a, typical, tfs, rep_pen, rep_pen_range, rep_pen_slope, sampler_order, mirostat*, grammar, min_p, use_default_badwordsids` |
| `novel` | `NovelAI Settings/` | `temperature, max_length, min_length, top_k/p/a, typical_p, tail_free_sampling, repetition_penalty*, phrase_rep_pen, order, math1_*, prefix, …` |
| `textgenerationwebui` | `TextGen Settings/` | 61 sampler keys (`temp, min_p, dry_*, dynatemp, mirostat_*, rep_pen*, xtc…, banned_tokens, grammar_string, json_schema, logit_bias, …`) |
| `openai` | `OpenAI Settings/` | 62 keys (`chat_completion_source`, `*_model` per provider, `openai_max_context/tokens`, `temperature…`, `prompts[]`, `prompt_order[]`, `custom_url`, `custom_include_body/headers`, …) |
| `instruct` | `instruct/` | `input_sequence, output_sequence, system_sequence, *_suffix, first/last_*, stop_sequence, wrap, macro, names_behavior, activation_regex, …` |
| `context` | `context/` | `story_string, example_separator, chat_start, story_string_position/depth/role, use_stop_strings, names_as_stop_strings, trim_sentences, single_line, always_force_name2` |
| `sysprompt` | `sysprompt/` | `name, content, post_history` |
| `reasoning` | `reasoning/` | `name, prefix, suffix, separator` |
| (themes / movingUI / QuickReplies) | own routes | `src/endpoints/themes.js:10`, `moving-ui.js:8`, `quick-replies.js:10` |

All are listed back to the client by `/api/settings/get` (`src/endpoints/settings.js:219-294`).

### 4.8 `settings.json`

Opaque client blob written verbatim (`src/endpoints/settings.js:206-217`). Bundled default top-level keys: `amount_gen, api_server, extension_settings, firstRun, horde_settings, kai_settings, main_api, max_context, nai_settings, oai_settings, power_user, preset_settings, swipes, tag_map, tags, textgenerationwebui_settings, user_avatar, username, world_info_settings` (`default/content/settings.json`). Server reads only `power_user.default_persona` / `user_avatar` for login avatars (`src/users.js:704-737`). Snapshots: `backups/settings_<handle>_<YYYYMMDD-HHMMSS>.json` at boot and ≤ every 10 min after a save, de-duplicated, pruned to `numberOfBackups` (`src/endpoints/settings.js:23-45,84-202`, `src/util.js:638-672`).

### 4.9 `secrets.json`

`{ "<secret_key>": [ {id: uuid, value, label, active: bool}, … ], "_migrated": [] }` (`src/endpoints/secrets.js:76-97`). 64 key names (`:9-74`). Legacy flat `{key: "value"}` migrated at boot with backup `backups/secrets_migration_<ts>.json` (`src/endpoints/secrets.js:381-420,498-507`). Masked reads unless `allowKeysExposure` (`:108,181-200,343-365`). Also holds per-user `csrfSecret` (`src/users.js:653-666`).

### 4.10 Thumbnails, image metadata, backups, vectors

| Store | Format | Cite |
|---|---|---|
| Thumbnails | `thumbnails/{bg,avatar,persona}/<original filename>`; JPEG (q95, ycbcr) or PNG; avatar/persona `cover` 96×144; bg keeps aspect at 160×90 area; GIF/animated served original | `src/endpoints/thumbnails.js:17-22,103-246,249-307` |
| `image-metadata.json` | `{version, images:{relPath:{hash, aspectRatio, isAnimated, dominantColor, folderIds[], addedTimestamp, thumbnailResolution, mtime}}, folders:[{id,name,thumbnailFile}]}` | `src/endpoints/image-metadata.js:15-36,164-190` |
| Chat backups | `backups/chat_<key>_<YYYYMMDD-HHMMSS>.jsonl` (key ASCII-sanitised + sha256 suffix for non-ASCII); throttled 10 s per user+chat, flushed on exit | `src/endpoints/chats.js:32-124` |
| Full backup | `/api/users/backup` → `<handle>-<ts>.zip` of the user dir | `src/users.js:1148-1185` |
| Vectors | vectra `LocalIndex` folder `vectors/<source>/<collectionId>/<model>/` (`index.json`, items `{vector, metadata:{hash, text, index}}`) | `src/endpoints/vectors.js:300-330,389,413` |
| Stats | `stats.json` per user, rebuilt from chats | `src/endpoints/stats.js:13,126-214,229-360` |

---

## 5. Subsystems

### 5.1 Tokenizers (`src/endpoints/tokenizers.js`, models in `src/tokenizers/`)

| Tokenizer | Library | Model source | Cite |
|---|---|---|---|
| llama, nerdstash, nerdstash_v2, mistral, yi, gemma, jamba | `@agnai/sentencepiece-js` (WASM-in-JS) | bundled `src/tokenizers/*.model` (≈18 MB total) | `src/endpoints/tokenizers.js:156-203,251-257` |
| claude, llama3 | `@agnai/web-tokenizers` (HF tokenizers compiled to WASM) | bundled `claude.json`, `llama3.json` | `:205-249,258-259` |
| command-r, command-a, qwen2, nemo, deepseek | web-tokenizers | downloaded `.json.gz` from `github.com/SillyTavern/SillyTavern-Tokenizers` into `_cache` (fallback llama3) | `:260-264,64-125` |
| gpt2 / OpenAI models | `tiktoken` (WASM, 5.4 MB) `encoding_for_model` | built-in | `:534`, model routing `:440-500` |
| Remote | KoboldCpp `/extra/tokencount`; ooba/tabby/llamacpp/vllm/aphrodite tokenize endpoints | proxied | `:1037-1120` |
| Fallback | `guesstimate` = bytes / 3.35 | | `:68-71` |

### 5.2 Vectors

20 embedding sources (`src/endpoints/vectors.js:22-43`): transformers (local), openai/mistral/togetherai/electronhub/openrouter/chutes/nanogpt/siliconflow/workers_ai (OpenAI-compatible, `src/vectors/openai-vectors.js`), extras, palm/vertexai (`google-vectors.js`), nomicai, cohere, ollama, llamacpp, vllm, and client-side `webllm`/`koboldcpp` (embeddings precomputed by client and sent in `sourceSettings.embeddings`) (`src/endpoints/vectors.js:54-95`). Storage = `vectra` LocalIndex (pure JS, whole index JSON loaded in memory) (`:300-330`). Batch size 10 (`:107-110`).

### 5.3 transformers.js (`src/transformers.js`)

| Task | Default model | Config override | Quantized | Used by |
|---|---|---|---|---|
| text-classification | `Cohee/distilbert-base-uncased-go-emotions-onnx` | `extensions.models.classification` | yes | `/api/extra/classify` |
| image-to-text | `Xenova/vit-gpt2-image-captioning` | `extensions.models.captioning` | yes | `/api/extra/caption` |
| feature-extraction | `Xenova/all-mpnet-base-v2` (yaml ships `Cohee/jina-embeddings-v2-base-en`) | `extensions.models.embedding` | yes | vectors `transformers` source (`src/vectors/embedding.js:9-27`) |
| automatic-speech-recognition | `Xenova/whisper-small` | `extensions.models.speechToText` | yes | `/api/speech/recognize` |
| text-to-speech | `Xenova/speecht5_tts` | `extensions.models.textToSpeech` | no | `/api/speech/synthesize` |

Rows: `src/transformers.js:19-50`, `default/config.yaml:306-310`. Library `sillytavern-transformers@2.14.6` (fork of transformers.js v2) using **onnxruntime-web 1.14 WASM backend only** (`node_modules/sillytavern-transformers/package.json` deps; `src/backends/onnx.js:21`), `numThreads=1` "to avoid issues on Android", WASM loaded from local `node_modules/sillytavern-transformers/dist/` via the patched `fetch(file://…)` (`src/transformers.js:12-17`, `src/fetch-patch.js:15-40`). Models downloaded from Hugging Face into `DATA_ROOT/_cache` unless `autoDownload:false` (`:134-138`). Audio: `wavefile` resamples data-URI WAV to 16 kHz float32 mono (`src/endpoints/speech.js:19-40`).

### 5.4 Image processing

Custom Jimp 1.6 build: WASM codecs `@jimp/wasm-{webp,png,jpeg,avif}` (backed by `@jsquash/*` WASM), JS codecs bmp/gif/tiff, plugins blit/circle/color/contain/cover/crop/displace/fisheye/flip/mask/resize/rotate/threshold/quantize (`src/jimp.js:1-63`). Used for avatar crop/resize, card PNG re-encode, thumbnails, content import, image metadata (dominant colour/hash). `image-size` for dimension probing (`src/endpoints/thumbnails.js:8`).

### 5.5 Translation providers (`src/endpoints/translate.js`)

| Route | Backend | Key/URL secret | Cite |
|---|---|---|---|
| libre | LibreTranslate | `libre`, `libre_url` | `:16` |
| google | `google-translate-api-x` (unofficial) | none | `:5,76` |
| yandex | `translate.yandex.net …srv=android` (unofficial) | none | `:103-158` |
| lingva | Lingva (default `lingva.ml`) | `lingva_url` | `:12,159` |
| deepl | `api(-free).deepl.com/v2/translate` | `deepl` | `:203-264` |
| onering | OneRingTranslator | `oneringtranslator_url` | `:265` |
| deeplx | DeepLX | `deeplx_url` | `:322` |
| bing | `bing-translate-api` (unofficial) | none | `:3,378` |

### 5.6 Search / scrapers (`src/endpoints/search.js`)

SerpApi (`:95-126`), YouTube transcript scraper (watch page → `captionTracks` → XML) (`:30-93,128-163`), SearXNG (`:165`), Tavily (`:219-266`), KoboldCpp websearch (`:268`), Serper (`:304-344`), Z.AI web search (`:346-390`), generic `visit` with SSRF guards + strict private-IP agent (`:392-470`). Chrome-like UA (`:14-28`).

### 5.7 Speech (TTS/STT)

| Provider | Routes | Cite |
|---|---|---|
| Local transformers (Whisper / SpeechT5) | `/api/speech/recognize`, `/synthesize` | `src/endpoints/speech.js:42-84` |
| Pollinations | `/api/speech/pollinations/*` | `src/endpoints/speech.js:86-175` |
| ElevenLabs | `/api/speech/elevenlabs/*` (TTS, history, voice clone, STT) | `src/endpoints/speech.js:177-432` |
| OpenAI / custom / ElectronHub / Chutes TTS; OpenAI/Groq/Mistral/Z.AI/Chutes STT | `/api/openai/...` | `src/endpoints/openai.js:273-460,756-890` |
| Google Translate TTS (`google-translate-api-x.speak`), Gemini native TTS | `/api/google/generate-voice`, `/generate-native-tts` | `src/endpoints/google.js:295-430` |
| NovelAI, Azure, MiniMax, Volcengine, KoboldCpp STT | own routes | `novelai.js:442`, `azure.js:8-80`, `minimax.js:19`, `volcengine.js:9`, `backends/kobold.js:190` |

### 5.8 Image/video generation backends (`/api/sd/*` and others)

`stable-diffusion.js` sub-routers mounted at `src/endpoints/stable-diffusion.js:2199-2215`:

| Backend | Prefix | Defined at |
|---|---|---|
| AUTOMATIC1111 / Forge / SD.Next (+ `sd-next/upscalers`) | `/api/sd/` | `:31-383` |
| ComfyUI (local; workflows in `user/workflows`) | `/api/sd/comfy` | `:385-633` |
| ComfyUI on RunPod serverless | `/api/sd/comfyrunpod` | `:635-735` |
| Together AI | `/api/sd/together` | `:737-830` |
| stable-diffusion.cpp server | `/api/sd/sdcpp` | `:832-915` |
| Draw Things | `/api/sd/drawthings` | `:917-1009` |
| Pollinations | `/api/sd/pollinations` | `:1011-1081` |
| Stability AI | `/api/sd/stability` | `:1083-1141` |
| Hugging Face Inference | `/api/sd/huggingface` | `:1143-1180` |
| ElectronHub | `/api/sd/electronhub` | `:1182-1306` |
| Chutes | `/api/sd/chutes` | `:1308-1387` |
| NanoGPT | `/api/sd/nanogpt` | `:1389-1469` |
| Black Forest Labs | `/api/sd/bfl` | `:1471-1589` |
| fal.ai | `/api/sd/falai` | `:1591-1734` |
| xAI | `/api/sd/xai` | `:1736-1792` |
| AIMLAPI | `/api/sd/aimlapi` | `:1794-1876` |
| Z.AI (CogView image, CogVideo video) | `/api/sd/zai` | `:1878-2058` |
| Cloudflare Workers AI | `/api/sd/workersai` | `:2060-2197` |
| Outside `sd`: NovelAI Diffusion, AI Horde, OpenAI Images/Sora, Google Imagen/Veo, OpenRouter image | `/api/novelai/generate-image`, `/api/horde/generate-image`, `/api/openai/generate-image|video`, `/api/google/generate-image|video`, `/api/openrouter/image/generate` | `novelai.js:300`, `horde.js:309`, `openai.js:619,653`, `google.js:432,498`, `openrouter.js:138` |

### 5.9 Caption / classify

Local: `/api/extra/caption` (`src/endpoints/caption.js:8-30`), `/api/extra/classify[/labels]` (`src/endpoints/classify.js:14-60`). Remote captioning: OpenAI-compatible multi-provider (`src/endpoints/openai.js:16-270`), Anthropic (`anthropic.js:8`), Google (`google.js:234`), Ollama (`backends/text-completions.js:475`), Horde interrogation (`horde.js:235`). Vertex AI service-account JWT is signed locally with `crypto.createSign('RSA-SHA256')` (`src/endpoints/google.js:41-130`).

---

## 6. Extensions & server plugins

| Feature | Mechanism | Needs `git` binary? | Cite |
|---|---|---|---|
| Install UI extension | `createGitClient({backend: git.backend})` → `auto`: system git if `command-exists('git')`, else **isomorphic-git** (pure JS, `fs` + `isomorphic-git/http/node`), depth 1, optional branch; then reads `manifest.json` | No (builtin fallback) | `src/endpoints/extensions.js:92-157`, `src/git/client.js:19-138` |
| Update / branches / switch / version / auto-update check | `simple-git` (`git fetch/pull/branch/revparse/log/checkout`, 5-min block timeout) | **Yes** — spawns `git` | `src/endpoints/extensions.js:17,40-64,169-440` |
| Move / delete / discover | plain `fs` (`rm -r`, rename, readdir) | No | `src/endpoints/extensions.js:329-385,442-520` |
| Where | user: `<user>/extensions/<name>`; global (admin): `public/scripts/extensions/third-party/<name>`; built-in: `public/scripts/extensions/*` | | `src/constants.js:4-6`, `src/endpoints/extensions.js:120` |
| Serving | client loads extension JS/CSS via `/scripts/extensions/third-party/*` static route (user dir first, then global) | | `src/users.js:1092-1127,1219` |
| Server plugins | `enableServerPlugins`: for each entry in `./plugins`: dir with `package.json.main` or `index.{js,cjs,mjs}`, or loose `.js/.cjs/.mjs` → dynamic `import()`; must export `info{id,name,description}` + `init(router)`; optional `exit()`; router mounted at `/api/plugins/<id>` | | `src/plugin-loader.js:40-231` |
| Plugin auto-update | `simple-git` fetch/pull per plugin dir on boot; skipped with error if `git` absent | **Yes** | `src/plugin-loader.js:237-293` |
| Plugin CLI | `node plugins.js update|install <url>` (install uses git client w/ builtin fallback; update needs git) | update: yes | `plugins.js:30-98` |
| Version banner | `getVersion()` uses simple-git if `git` on PATH (optional) | optional | `src/util.js:136-165` |

Other process/native touch points: none of `child_process`, `worker_threads`, `fs.watch` are used in server code (grep of `src/`, `server.js`, `plugins.js`, `recover.js`, `webpack.config.js`); `simple-git` and `open` (browser launch: `xdg-open`/`open`/`start`, Android branch skips app selection, `src/server-main.js:370-388`) are the only spawners. `webpack` runs in-process at every boot to build `public/lib.js` (`src/server-main.js:354`, `src/middleware/webpack-serve.js:33-52`).

---

## 7. Android portability notes

### 7.1 Risk table

| Area | Issue | Severity | Cite / mitigation |
|---|---|---|---|
| `git` binary | Extension **update/branch/switch/version** and plugin auto-update use `simple-git` ⇒ spawn `git`. Install works without git (isomorphic-git) | High for parity | `src/endpoints/extensions.js:42,194,241,290,406`, `src/plugin-loader.js:252-262`. Port: reimplement with isomorphic-git (fetch/pull/checkout/log), or bundle a git binary |
| Server-side JS plugins | Arbitrary Node code via dynamic `import()`; require a full Node runtime | High | `src/plugin-loader.js:150-160`. Options: embed nodejs-mobile, or drop/sandbox |
| Node runtime | Node ≥ 20, ESM, top-level await, `import.meta.dirname`, global `fetch`/`Blob`/`structuredClone` | High | `package.json:103-105`, `server.js:14`, `src/server-directory.js:3`, `src/transformers.js:61` |
| Recovery/reset codes | printed to **stdout** only; no console on Android | Medium | `src/endpoints/users-public.js:135`, `src/endpoints/users-private.js:246`. Surface via notification/log view |
| webpack at boot | `webpack` (prod dep) compiles `public/lib.js` on every start; caches in `_webpack`; slow on phones | Medium | `src/server-main.js:354`, `webpack.config.js:63-124`. Pre-build (`forceDist`/Docker path `dist/_webpack`, `webpack.config.js:65-67`) |
| ONNX/WASM | onnxruntime-web WASM (4 variants ≈9-10 MB each, duplicated in `sillytavern-transformers/dist`), single-thread; large HF downloads to `_cache`; RAM-heavy (whisper-small, ViT-GPT2) | Medium | `src/transformers.js:12-17,134-138`. Pure WASM ⇒ portable; consider ORT-Mobile natively |
| tiktoken / sentencepiece / web-tokenizers | WASM (tiktoken_bg.wasm 5.4 MB) or WASM-in-JS; file-path model loads relative to cwd (`src/tokenizers/*.model`) | Low | `src/endpoints/tokenizers.js:251-264`; `process.chdir(serverDirectory)` at `server.js:11` is required |
| Jimp codecs | `@jsquash` WASM (avif/webp/jpeg/png/oxipng) | Low | `src/jimp.js:3-7` |
| Native addons | **none** (no `*.node`, no `binding.gyp` in `node_modules`) | — | `find node_modules -name '*.node'` empty |
| OS paths | relative `./config.yaml`, `./data`, `public/…`, `certs/…`; `env-paths` (XDG) for `--global` | Medium | `src/command-line.js:53-57`, `src/constants.js:1-7`. Set `--configPath/--dataRoot` to app-private storage; `process.chdir` to the bundle dir |
| Writes into install dir | global extensions in `public/scripts/extensions/third-party`, server-root `backups/`, `plugins/`, `config.yaml` rewrite on boot | Medium (APK assets read-only) | `src/constants.js:1-7`, `src/config-init.js:239`, `src/users.js:113-118`. Copy server tree to writable dir |
| Ports / bind | default `127.0.0.1:8000`; `EADDRINUSE` ⇒ `process.exit(1)` | Low | `src/server-startup.js:375-396`. Pick free port, keep `listen:false` |
| IPv6 | `ipv6Only:true` per listener; `auto` uses `os.networkInterfaces()` (may be restricted on Android) | Low | `src/server-startup.js:289-317`, `src/util.js:1063-1098`. Use explicit `protocol.ipv4:true, ipv6:false` |
| `os.hostname()` | cookie name hash; fine | Low | `src/users.js:612-617` |
| Browser launch | `open` package; Android branch exists but no default browser via shell in-app | Low | `src/server-main.js:367-395`. Set `browserLaunch.enabled:false`, load URL in WebView |
| `process.platform === 'android'` | disables character memory cache | Info | `src/endpoints/characters.js:33,199` |
| Terminal title | writes OSC escape to stdout | Info | `src/util.js:1340-1346` |
| File watching | none | — | — |
| Signals | SIGINT/SIGTERM handlers flush stats, plugins, disk cache | Low | `src/server-main.js:317-335`; call equivalent on app pause/stop |
| Docker detection | `is-docker` reads `/proc` and `/.dockerenv` | Info | `src/middleware/whitelist.js:36`, `webpack.config.js:65` |
| Security defaults | WebView on 127.0.0.1 passes default whitelist; CSRF requires cookie + `X-CSRF-Token` ⇒ WebView must keep cookies | Info | `src/server-main.js:156-210` |
| Upload temp | multer writes to `DATA_ROOT/_uploads`, 500 MB limits | Info | `src/server-main.js:268-269` |
| Atomic writes | `write-file-atomic` (rename) — fine on app storage, problematic on SAF/SD-card URIs | Low | many, e.g. `src/endpoints/settings.js:209` |
| Outbound TLS/DNS | `node-fetch` + global agents (`proxy-agent`, `PrivateRequestAgent` uses `dns.promises.lookup`) | Low | `src/private-request-filter.js:166-172` |
| Unofficial scrapers | Google/Bing/Yandex translate, YouTube transcript depend on desktop-like UAs | Info | §5.5–5.6 |

### 7.2 Dependency footprint

| Measure | Value | How |
|---|---|---|
| Declared prod deps | 103 (`package.json:2-101`) | `npm ls --omit=dev --depth=0` |
| Prod closure | 667 packages, ≈ **281 MB** on disk | `npm ls --omit=dev --all --parseable` + size sum |
| Whole `node_modules` | 334 MB (incl. dev) | `du -sh` |
| Largest | onnxruntime-web 66 MB, sillytavern-transformers 52 MB, tiktoken 23 MB, protobufjs 21 MB, @jimp 17 MB, highlight.js 9.1 MB, @jsquash 8.9 MB, webpack 7.4 MB, web-streams-polyfill 7.3 MB, isomorphic-git 5.0 MB | `du -sh node_modules/*` |
| Repo dirs | `public` 26 MB, `src` 18 MB (tokenizer models), `default` 16 MB | `du -sh` |
| Client-only deps shipped as prod (bundled by webpack into `lib.js`) | lodash, fuse.js, dompurify, highlight.js, localforage, handlebars, @adobe/css-tools, bowser, diff-match-patch, @mozilla/readability, @iconfu/svg-inject, showdown, moment, seedrandom, @popperjs/core, droll, morphdom, slidetoggle, chalk, yaml, chevrotain, fflate, js-sha256 | `public/lib.js:5-27` |
| WASM blobs | ort-wasm{,-simd,-threaded,-simd-threaded}.wasm ×2 copies (~37 MB each set), tiktoken_bg.wasm 5.4 MB (+lite 1 MB), @jsquash avif/webp/jpeg/png/oxipng codecs (~6 MB) | `find node_modules -name '*.wasm'` |
| Alternate runtimes declared | Deno (`--allow-run …`), Bun | `package.json:125-126` |

---

## Appendix A — REA evidence cross-check

Evidence ID **`ev_6f32771fddaf2acda688c6e5c4c616627a2c5291a3be13adb9be76c07b9cd159`** (a local REA evidence bundle (not committed, 219 MB), provider `rea-javascript-application` v1, authority `shipped-artifact`, artifact = directory `st-app` containing `server.js`, `plugins.js`, `src/`, `script.js`, `scripts/`, `lib.js`). Extracted by a one-pass Python walk (peak RSS ≈ 1.2 GB) for families `request`, `child-process`, `boundary`, `configuration` and relations `constructs-request`, `reads-environment`, `reads-argv`.

| Family / relation | Count | Finding |
|---|---|---|
| `constructs-request` | 68 | All in client scripts (`scripts/*.js`, `scripts/extensions/*`); 45 unique resolved endpoints, **all 45 present in the §3 server route table** (e.g. `/api/characters/merge-attributes`, `/api/backgrounds/upload`, `/api/extensions/install`, `/api/backends/chat-completions/generate`, `/api/data-maid/*`, `/api/image-metadata/folders/*`); 18 unresolved (`endpoint:null`, dynamic URLs) |
| `request` family | 20 | "unknown" records, reason `ambiguous-target` (dynamic fetch URLs) in `scripts/backgrounds.js`, `scripts/extensions*.js`, `scripts/extensions/{caption,expressions,assets}/index.js`, `scripts/chats.js`, `scripts/data-maid.js`, `scripts/custom-request.js` |
| `supplies-request-field` | 171 | request option fields (headers/body) |
| `child-process` | 1 summary, 0 retained relations | consistent with no `child_process` use in server code (§6) |
| `reads-environment` / `reads-argv` | 1 / 2 | `plugins.js:18` (`SILLYTAVERN_GIT_BACKEND`), `plugins.js:20,36` |
| `boundary` | 31 | client DOM/storage boundaries (`scripts/RossAscends-mods.js`, `scripts/chats.js`, …) — not server |

Limitation: the bundle contains **no** `constructs-request` edges for server-side `node-fetch` calls; those were enumerated by grep instead (30 modules import `node-fetch`; ≈216 call sites; largest: `stable-diffusion.js` 63, `backends/chat-completions.js` 29, `openai.js` 16, `backends/text-completions.js` 15, `content-manager.js` 13, `search.js` 11).
