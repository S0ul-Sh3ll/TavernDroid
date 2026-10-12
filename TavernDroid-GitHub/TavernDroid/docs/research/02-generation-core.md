# SillyTavern 1.19 — Frontend Generation Core (spec for Android parity)

> Source: the SillyTavern 1.19.0 source root (commit 06bde93) (`package.json:118` → `"version": "1.19.0"`). This document covers frontend logic only: `public/script.js`, `public/scripts/*.js`, `public/scripts/macros/**` and `public/scripts/slash-commands/**`. Built-in extensions are referenced only where they plug into core (regex hooks, slash commands, events). Citations use `file:line`, where line is the first line of the relevant construct.

**Contents**
- §0 Mental model
- §1 Generation pipeline & events
- §2 Prompt construction per API
  - 2.A Chat Completion / PromptManager
  - 2.B Text Completion
  - 2.C KoboldAI
  - 2.D NovelAI
  - 2.E Horde
  - 2.F Instruct
  - 2.G Context / system prompts
  - 2.H chat-template detection
  - 2.I Reasoning
  - 2.J CFG
  - 2.K Logit bias / logprobs
  - 2.L Tool calling
  - 2.M custom-request
  - 2.N Prompt post-processing
- §3 Context budget & tokenization
- §4 World Info
- §5 Macros & variables
- §6 Slash commands / STscript (full 293-command registry)
- §7 Characters, groups, personas, chat features
- §8 Settings persistence, storage, secrets
- §9 Parity risk register

> Citation note: §0, §1 and §3 cite paths relative to `public/` unless prefixed with `src/` (backend). Every other section begins with its own abbreviation key. All paths resolve under the SillyTavern 1.19.0 source root (commit 06bde93).

## 0. Mental model

**State.** All state lives in browser globals:

| Global(s) | Holds |
|---|---|
| `chat` | Array of message objects |
| `chat_metadata` | Object saved as the first JSONL line of the chat file |
| `characters[]`, `groups[]` | Loaded characters and groups |
| `this_chid`, `selected_group` | Current character index / group id |
| `name1`, `name2` | User/persona name, character name |
| `main_api` | `kobold` \| `koboldhorde` \| `textgenerationwebui` \| `novel` \| `openai` |
| `kai_settings`, `textgen_settings`, `nai_settings`, `oai_settings`, `horde_settings` | Per-API settings |
| `power_user` | Large object of global preferences |

**Flow.** `Generate(type)` (`script.js:4290`):
1. Reads this state.
2. Assembles a prompt: a string for Text Completion APIs, or `messages[]` for Chat Completion.
3. POSTs it to the ST Node server, which proxies to the real backend.
4. Parses the response, either streamed via SSE or as whole JSON.
5. Mutates `chat`, re-renders, and persists via `/api/chats/save`.

**Side channel.** `extension_prompts` is a dictionary of keyed injections, each with position, depth and role (`script.js:8926`). Author's Note, WI depth entries, persona-at-depth, character depth prompts, vectors, summaries and the story string all reach the prompt through it.

**What an Android port must reproduce:**
- (a) The prompt builder, exactly: token-budget trimming, injection ordering, instruct formatting.
- (b) WI scanning.
- (c) Macro substitution.
- (d) The swipe data model.
- (e) The streaming and cleanup pipeline.

---

## 1. Generation pipeline (`script.js`)

### 1.1 Generation types

| `type` | Origin | Effect inside `Generate()` | Cite |
|---|---|---|---|
| `normal` (also `undefined`) | Send button / Enter → `sendTextareaMessage()`; group loop; tool-call recursion | Textarea text becomes a user message (`sendMessageAsUser`); the AI reply is appended as a new message | `script.js:1732`, `script.js:4440-4459` |
| `continue` | `#option_continue` (`script.js:11648`); "continue on send" when the textarea is empty and the last message is from the character (`script.js:1748-1757`); auto-continue (`script.js:5783`); `/continue` | The last message is the prefix (`cyclePrompt` / `continue_mag`); output is appended (`saveReply` type `append` / `appendFinal`) | `script.js:4414`, `script.js:4775-4808`, `script.js:5531-5535` |
| `regenerate` | `#option_regenerate` (`script.js:11629`); in groups → `regenerateGroup()` | If the last message is not from the user, it is deleted (`chat.length--`, `MESSAGE_DELETED`) and a fresh reply is generated | `script.js:4404-4412` |
| `swipe` | Overswipe right on the last AI message (`script.js:10398-10405`) → `Generate('swipe')` (`script.js:10319`) | The last message is removed from `coreChat` (`script.js:4510-4512`); the result is pushed as a new swipe slot (`saveReply` `swipe` branch) | `script.js:6671` |
| `impersonate` | `#option_impersonate` (`script.js:11643`); `/impersonate` | Generates as the user (`name1`); the result goes into `#send_textarea`, not into chat; emits `IMPERSONATE_READY` | `script.js:5523-5525`, `script.js:3800-3802` |
| `quiet` | `generateQuietPrompt()` (`script.js:3084`), used by extensions, `/gen`, summaries | No UI message. `quiet_prompt` is appended as the last line. The result is returned as a string. Never streamed (`type !== 'quiet'` check, `script.js:5395`) | `script.js:5526-5528` |
| `first_message` | Pseudo-type passed in `MESSAGE_RECEIVED` when a fresh chat gets its greeting | Not a `Generate` type | `script.js:7703-7706` |
| `append` / `appendFinal` | Internal `saveReply` modes used for continue | — | `script.js:6697`, `script.js:6719` |
| group | Not a `type`. When `selected_group && !is_group_generating`, `Generate` delegates to `generateGroupWrapper(false, type, …)`, which picks speakers and calls `Generate(type, {force_chid…})` for each member | — | `script.js:4351-4355`, `group-chats.js:1063` |

**Related definitions:**
- `GENERATION_TYPE_TRIGGERS = ['normal','continue','impersonate','swipe','regenerate','quiet']` (`scripts/constants.js:36`). Used as the WI "trigger" filter (`script.js:4633`).
- `GenerateOptions` (`script.js:4268-4281`): `automatic_trigger`, `force_name2`, `quiet_prompt`, `quietToLoud`, `skipWIAN`, `force_chid`, `signal`, `quietImage`, `quietName`, `jsonSchema`, `depth` (tool-call recursion counter).
- The third argument, `dryRun`, assembles the prompt only (for token counting and prompt itemization) and makes no network call (`script.js:5320-5322`).

### 1.2 Entry: `sendTextareaMessage()` (`script.js:1732`)

1. Abort if any of these hold (`script.js:1734-1740`):
   - a swipe edit is in progress (`SWIPE_STATE.EDITING`);
   - a swipe is in progress;
   - `is_send_press` is set;
   - slash commands are currently executing.
2. Hide the swipe buttons. Set `generateType` to `continue` when all of these hold (`script.js:1748-1757`):
   - `power_user.continue_on_send` is on;
   - the textarea is empty and there is no pending attachment;
   - this is not a group chat;
   - the last message is neither from the user nor a system message.
3. If text was typed but no character or group is selected, call `newAssistantChat()`, which opens a temporary "neutral" assistant (`script.js:1760-1762`).
4. `await Generate(generateType)`, then `showSwipeButtons()`.

### 1.3 `Generate()` step by step (`script.js:4290-5604`)

| # | Step | Details | Cite |
|---|---|---|---|
| 1 | Init | `setGenerationProgress(0)`, `generation_started = new Date()`, `unshallowCharacter(this_chid)` (lazy-loaded characters must be fully fetched first) | `4291-4296` |
| 2 | Event | `GENERATION_STARTED(type, opts, dryRun)`. Fires even when the run is then aborted by slash commands | `4299` |
| 3 | Abort controller | A new `AbortController` is created unless a `signal` was passed and a controller already exists | `4302-4304` |
| 4 | `isInstruct` | `power_user.instruct.enabled && main_api !== 'openai'` | `4307` |
| 5 | Slash commands | Skipped for dryRun, depth>0, `regenerate`, `swipe`, `quiet`. If the textarea starts with `/`, `processCommands()` → `executeSlashCommandsOnChatInput(..., {clearChatInput:true})` and generation returns without producing a reply | `4310-4318`, `3125-3133` |
| 6 | Event | `GENERATION_AFTER_COMMANDS` | `4321` |
| 7 | Guards | Kobold streaming unsupported → error. Horde not allowed (`isHordeGenerationNotAllowed`) → return | `4323-4332` |
| 8 | Ping | Non-dry runs only: `pingServer()` (`/api/ping`, `script.js:675`); on failure, toast and throw. Then `hideSwipeButtons()` and `chat_metadata.tainted = true` (affects greeting overswipe) | `4334-4348` |
| 9 | Group delegation | `selected_group && !is_group_generating` → `return generateGroupWrapper(...)`. On dryRun, picks the first enabled member for counting instead | `4350-4380` |
| 10 | Quiet prompt | `substituteParams(quiet_prompt)`. NovelAI non-loud → `adjustNovelInstructionPrompt` | `4384-4387` |
| 11 | Connection | Not dry and `online_status === 'no_connection'` → silently return | `4389-4396` |
| 12 | Textarea capture / regenerate delete | **Capture:** for `normal` / `continue` (type ∉ {regenerate, swipe, quiet}, not impersonate, not dry, depth 0), read and clear `#send_textarea`. **Otherwise:** text = `''`. If additionally the last message is **not** a user message and type ∉ {quiet, swipe}, not impersonate/dry/depth (in practice: `regenerate`), the last message is deleted: `deleteItemizedPromptForMessage`, `chat.length--`, DOM remove, `MESSAGE_DELETED` | `4398-4413` |
| 13 | Continue timer | For `continue`, rewinds `generation_started` by the previous generation's duration so the displayed time is cumulative | `4416-4427` |
| 14 | Buttons | `deactivateSendButtons()` | `4429-4431` |
| 15 | Bias | `getBiasStrings(textareaText, type)` → `{messageBias, promptBias, isUserPromptBias}`. Fallback chain below the table | `4433`, `5794-5831` |
| 16 | User message | Applies when there is text or a pending file attachment and type ∉ `['regenerate','swipe','impersonate','quiet','continue']`. Bias-only text → `sendSystemMessage(GENERIC,' ',{bias})`; otherwise `sendMessageAsUser(text, bias)`. For CC with an empty normal send, `oai_settings.send_if_empty` text is sent instead | `4440-4459` |
| 17 | Card fields | `getCharacterCardFields()` → `description, personality, persona, scenario, mesExamples, system, jailbreak, charDepthPrompt, creatorNotes`. Macro-substituted via `baseChatReplace`. `chat_metadata.scenario` / `mes_example` / `system_prompt` override the card. Group cards come from `getGroupCharacterCardsLazy` | `4461-4471`, `3402-3473` |
| 18 | Depth prompt | `removeDepthPrompts()`. Group → each `getGroupDepthPrompts` entry as `DEPTH_PROMPT_<i>`. Solo → `data.extensions.depth_prompt.{prompt, depth (default 4), role (default 'system')}` as `DEPTH_PROMPT`, `IN_CHAT` | `4474-4487`, `550-551` |
| 19 | Greeting refresh | `chat[0].mes = substituteParams(chat[0].mes)` | `4490-4492` |
| 20 | coreChat | Details below the table | `4495-4537` |
| 21 | Reasoning re-add | `PromptReasoning.addToMessage()` walks newest→oldest, adding `extra.reasoning` (regexed with `REASONING` placement) until `max_additions` is reached. In groups, only for the current speaker | `4539-4563` |
| 22 | Budget | `this_max_context = getMaxPromptTokens()` = `getMaxContextTokens() - getMaxResponseTokens()` | `4566`, `5981-5987` |
| 23 | Interceptors | Non-dry only: `runGenerationInterceptors(coreChat, this_max_context, type)`. Calls `globalThis[manifest.generate_interceptor](chat, contextSize, abort, type)` for each extension, in manifest order. Calling `abort()` cancels generation | `4568-4579`, `extensions.js:2024-2049` |
| 24 | Horde adjust | `adjustHordeGenerationParams(max_context, amount_gen)` shrinks the context to the workers' limits | `4583-4594` |
| 25 | CFG | `getGuidanceScale()`. If the scale is not 1, subtract `max(tokens(negative), tokens(positive))` from the budget | `4597-4611` |
| 26 | `force_name2` | Set true for a non-default prompt bias, `power_user.always_force_name2`, or NovelAI. Forced false for impersonate | `4615-4621` |
| 27 | Examples | `parseMesExamples(mesExamples, isInstruct)`: prefix `<START>` if missing, split on `<START>` (case-insensitive). Block heading = `<START>\n` for CC/instruct, else `context.example_separator` | `4623`, `3501-3515` |
| 28 | Author's Note | `setFloatingPrompt()` (authors-note.js) sets the `2_floating_prompt` extension prompt if the interval is hit | `4626` |
| 29 | World Info | Details below the table | `4628-4637` |
| 30 | WI examples | EM-top/bottom entries are parsed as examples and `unshift`ed / `push`ed into `mesExamplesArray`. A raw copy is kept for `{{mesExamplesRaw}}`. Instruct → `formatInstructModeExamples` | `4640-4664` |
| 31 | WI depth/outlets | Unless `skipWIAN`: `flushWIInjections()`. Each depth group → `customDepthWI_<depth>_<role>`, `IN_CHAT`. Outlets → `customWIOutlet_<key>` with position `NONE`, read via the `{{outlet::key}}` macro | `4666-4683` |
| 32 | Persona | `addPersonaDescriptionExtensionPrompt()`. TOP_AN / BOTTOM_AN merge into the AN text. AT_DEPTH → `PERSONA_DESCRIPTION`, `IN_CHAT`, at `persona_description_depth` / `role` | `4686`, `3203-3224` |
| 33 | System prompt (TC only) | With `power_user.sysprompt.enabled`: the character's `system_prompt` if `prefer_character_prompt` (with `{{original}}` = the global sysprompt), else the global `sysprompt.content`. Disabled → `''` | `4689-4699` |
| 34 | Story string | Reads the `BEFORE_PROMPT` / `IN_PROMPT` anchor extension prompts, then `renderStoryString({...})` (params below the table). Instruct → `formatInstructModeStoryString` | `4700-4722` |
| 35 | Story string in-chat | TC with `context.story_string_position === IN_CHAT` → injected as `__STORY_STRING__` at `story_string_depth` (default 1) / `story_string_role`; the top story string is then blanked | `4725-4734` |
| 36 | `strip_examples` | `power_user.strip_examples` → examples removed after the story string is rendered | `4737-4739` |
| 37 | Chat injections (TC) | `doChatInject(coreChat, isContinue)` (see §1.5) | `4742-4745` |
| 38 | Post-history instructions (TC) | With sysprompt enabled: the character's `post_history_instructions` if `prefer_character_jailbreak`, else `sysprompt.post_history`. Pushed as a fake user message at the end (on continue, placed before the last message) | `4747-4765` |
| 39 | Format history | Details below the table | `4767-4816`, `5833-5853` |
| 40 | User alignment message | Instruct `user_alignment_message` is prepended if the history does not start at a user message | `4818-4829`, `4919-4925` |
| 41 | CC prep | `setOpenAIMessages(coreChat)`, `setOpenAIMessageExamples(mesExamplesArray)` | `4831-4837` |
| 42 | Token fill (TC) | See §3.2: injected indices are pre-allocated first, then messages are added newest→oldest while the total stays `< this_max_context` | `4844-4917` |
| 43 | Examples fill | Counts how many unpinned examples fit (`count_exm_add`). `pin_examples` forces all of them in | `4859-4863`, `4943-4955` |
| 44 | Continue postfix (CC) | CC continue: append `oai_settings.continue_postfix` if the prompt doesn't end with a space | `4960-4966` |
| 45 | `mesSend` | Kept messages are reversed into `{message, extensionPrompts:[]}`. The trailing newline of the final message is stripped, unless instruct without wrap, or quiet | `4974-4994` |
| 46 | Last line | `modifyLastPromptLine()` appends, in order: the quiet prompt (instruct-formatted as a user turn); the instruct `formatInstructModePrompt(name,…)` (output sequence + name); for non-instruct impersonate, `name1:`; with `force_name2`, `name2:` | `5009-5072` |
| 47 | Size check | Only when `cyclePrompt` is set (continue): `checkPromptSize()` recursively drops examples first, then the oldest messages, until the prompt fits | `5074-5108` |
| 48 | Combine | `getCombinedPrompt(isNegative)`; details below the table | `5113-5238` |
| 49 | Event | `GENERATE_AFTER_COMBINE_PROMPTS({prompt, dryRun})`; listeners may rewrite `prompt` | `5242-5244` |
| 50 | Per-API request body | Per `main_api`; details below the table | `5249-5316` |
| 51 | Event | `GENERATE_AFTER_DATA(generate_data, dryRun)`. A dryRun returns here | `5318-5322` |
| 52 | Itemize | `finishGenerating()` builds `additionalPromptStuff` (fields below the table) and upserts it into `itemizedPrompts` by `mesId` | `5332-5393` |
| 53a | Streaming | When `isStreamingEnabled() && type !== 'quiet'`: new `StreamingProcessor`; `generator = await sendStreamingRequest(type, generate_data, {jsonSchema})`; `await streamingProcessor.generate()`; then tool-call handling; then `onFinishStreaming` | `5395-5452` |
| 53b | Non-streaming | `sendGenerationRequest(type, generate_data, {jsonSchema})` → `onSuccess(data)` | `5453-5455`, `5468-5583` |
| 54 | Errors | `onError`: toast `exception.error.message`, `unblockGeneration`, rethrow | `5590-5602` |

**Step 15 — bias fallback chain** (`5794-5831`). None of this applies to impersonate or continue.
1. Bias written in the current text as a `{{bias "text"}}` Handlebars helper, extracted by `extractMessageBias` (`3140`).
2. Otherwise, the `extra.bias` of the most recent user, system or narrator message.
3. Otherwise, `power_user.user_prompt_bias`.

**Step 20 — building `coreChat`** (`4495-4537`):
1. Filter `chat.filter(x => !x.is_system || (toolCallingSupported && x.extra.tool_invocations))`.
2. For `swipe`, pop the last message.
3. Map each message through:
   - regex (`USER_INPUT` / `AI_OUTPUT`, `{isPrompt:true, depth}`);
   - `appendFileContent` (attachment and data-bank text);
   - media / `append_title` titles.

**Step 29 — World Info** (`4628-4637`):
1. Set the `QUIET_PROMPT` extension prompt so WI can scan it.
2. Build `chatForWI` = `coreChat` (optionally as `name: mes`), reversed.
3. Build `globalScanData` = persona, description, personality, depth prompt, scenario, creatorNotes, trigger.
4. Call `getWorldInfoPrompt(chatForWI, this_max_context, dryRun, globalScanData)`, which returns `{worldInfoString, worldInfoBefore, worldInfoAfter, worldInfoExamples, worldInfoDepth, outletEntries}`.

**Step 34 — `renderStoryString` params** (`4700-4722`): `description`, `personality`, `persona` (only if the persona position is `IN_PROMPT`), `scenario`, `system`, `char`, `user`, `wiBefore` / `loreBefore`, `wiAfter` / `loreAfter`, `anchorBefore`, `anchorAfter`, `mesExamples`, `mesExamplesRaw`.

**Step 39 — formatting history** (`4767-4816`, `5833-5853`). `chat2[]` is built newest-first:
- **CC:** keeps the raw `mes`.
- **TC:** `formatMessageHistoryItem` produces `"name: mes\n"` or instruct sequences. The first message uses `force_output_sequence.FIRST`; the last user message uses `.LAST`.
- **Continue:** the suffix is stripped using the sentinel `\u0000￼\u0000�`.

**Step 48 — `getCombinedPrompt(isNegative)`** (`5113-5238`):
1. Deep-clone `mesSend`.
2. Insert the CFG prompt at its depth: depth 0 appends it to the last message; otherwise it is added to the target message's `extensionPrompts`.
3. Append the prompt bias (non-instruct, non-impersonate only).
4. Emit `GENERATE_BEFORE_COMBINE_PROMPTS(data)`. If a listener sets `data.combinedPrompt`, that string is used as-is.
5. Otherwise join the default order: `storyString + examples + chat_start + preamble + messages + cyclePrompt`.
6. Remove `\r`; apply `collapse_newlines` if enabled.

**Step 50 — per-API request body** (`5249-5316`):

| `main_api` | Body |
|---|---|
| `kobold` / `koboldhorde` | GUI preset → `{prompt, gui_settings:true, max_length, max_context_length, api_server}`; otherwise `getKoboldGenerationData` |
| `textgenerationwebui` | `getTextGenGenerationData(finalPrompt, maxLength, isImpersonate, isContinue, cfgValues, type)`; the CFG negative prompt comes from `getCombinedPrompt(true)` |
| `novel` | `getNovelGenerationData` |
| `openai` | `prepareOpenAIMessages({...}, dryRun)` returns `[messages, counts]` |

**Step 52 — `additionalPromptStuff` fields** (`5332-5393`):
- prompt text: `rawPrompt`, `finalPrompt`, `mesSendString`, story string, examples, `promptBias`;
- identity: `mesId`;
- injections: all anchors, `chatInjects`, summary / AN / vectors strings, WI string;
- character data: char fields, persona;
- config: max context, padding, api, instruction, tokenizer, preset name;
- `counts`.

**Unblocking.** `unblockGeneration(type)` (`script.js:5693`) resets `is_send_press`, re-enables the buttons, sets progress to 0, and flushes ephemeral stop strings and WI injections. It is skipped for `quiet` while a parallel stream is still running.

**Stopping.** `stopGeneration()` (`script.js:5607`) calls `streamingProcessor.onStopStreaming()` and `abortController.abort()`, hides the stop button, and emits `GENERATION_STOPPED`.

### 1.4 Non-streaming response handling — `onSuccess(data)` (`script.js:5468`)

1. **Errors and JSON mode.** `data.error` (textgen) → toast and throw (`5479-5486`). With `jsonSchema` → `extractJsonFromData` and return (`5488-5491`, `6311`).
2. **Extraction** (`5493-5498`):

| Function | What it extracts | Cite |
|---|---|---|
| `extractMessageFromData` | Per API, below | `6276` |
| `extractTitleFromData` | Horde worker name | |
| `extractReasoningFromData` | Reasoning text | |
| `extractImagesFromData` | Gemini `inlineData`, OpenRouter `message.images` | `6203` |
| `extractReasoningSignatureFromData` | Reasoning signature | |
| `extractMultiSwipes` | n>1: `choices[1..]` for CC / vLLM / Aphrodite / Tabby / Infermatic / Mancer, or the llama.cpp array. None for continue / impersonate / quiet | `6374` |

   `extractMessageFromData` per API:

| API | Path |
|---|---|
| kobold | `results[0].text` |
| horde | `text` |
| textgen | `choices[0].text ?? choices[0].message.content ?? content ?? response ?? [0].content` |
| novel | `output` |
| openai | a plain string, or Claude `content[type=text]` / `choices[0].message.content` / Cohere `message.content[0].text` / `tool_plan` |

3. **Cleanup.** `cleanUpMessage` runs twice: once to get `messageChunk` (for auto-continue), once for the final text (`5500-5521`). Reasoning gets the `REASONING` regex and is trimmed.
4. **Continue.** `getMessage = continue_mag + getMessage`, where `continue_mag` is the prefix minus any reasoning prefix.
5. **Routing** (`5523-5539`, `6235`):
   - impersonate → write into the textarea, emit `IMPERSONATE_READY`;
   - quiet → return the string;
   - otherwise `saveReply({type|'appendFinal', getMessage, title, swipes, reasoning, imageUrls, reasoningSignature})`, then `parseAndSaveLogprobs`.
6. **Tool calls** (`5541-5560`). Allowed when `canPerformToolCalls` = not dry, `ToolManager.canPerformToolCalls(type)`, and `depth < ToolManager.RECURSE_LIMIT`. If the response contains tool calls:
   1. Delete the empty placeholder message.
   2. `invokeFunctionTools`.
   3. `saveFunctionToolInvocations` (system messages with `extra.tool_invocations`).
   4. **Recurse:** `Generate('normal', {...depth+1})`.

   Generation stops instead when a stealth tool was invoked, or when there were no invocations and the message was empty.
7. **Sound and auto-swipe.** `playMessageSound()`. With `auto_swipe`, if `generatedTextFiltered()` (blacklist / minimum length) fires, `swipe(RIGHT, AUTO_SWIPE)` (`5562-5570`).
8. **Finish.** `saveChatConditional()`, `unblockGeneration`, `triggerAutoContinue` (`5572-5582`).

### 1.5 Chat injection algorithm (TC) — `doChatInject(messages, isContinue)` (`script.js:5628`)

**Algorithm:**
1. Reverse the messages so the newest is first.
2. For each depth `i = 0..MAX_INJECTION_DEPTH` (10000; `script.js:500`, `3281`), and for each role in the order SYSTEM, USER, ASSISTANT:
   - call `getExtensionPrompt(IN_CHAT, i, '\n', role, wrap=false)`;
   - each non-empty result becomes a pseudo message `{name: ''|name1|name2, is_user, mes, extra:{type: narrator for system}}`.
3. Insert the pseudo messages at `min(depth + totalInsertedSoFar, len)`, where `depth = (isContinue && i===0) ? 1 : i`.
4. Reverse back. Return the indices of the injected messages; they are used for budget pre-allocation.

**`getExtensionPrompt(position, depth, separator, role, wrap)`** (`script.js:3301`):
1. Sort the keys **alphabetically**.
2. Filter by position and non-empty `value`; by depth (an undefined depth matches all); by role; and by the optional async `filter()`.
3. Trim and join the values; optionally wrap them with the separator.
4. Apply `substituteParams`.

**Extension prompt record:** `{value, position, depth, scan, role, filter}` (`script.js:8926`).

| Enum | Values | Cite |
|---|---|---|
| Positions | `NONE:-1, IN_PROMPT:0, IN_CHAT:1, BEFORE_PROMPT:2` | `script.js:484` |
| Roles | `SYSTEM:0, USER:1, ASSISTANT:2` | `script.js:494` |

**Well-known keys:**

| Key(s) | Cite |
|---|---|
| `__STORY_STRING__`, `QUIET_PROMPT`, `DEPTH_PROMPT`, `DEPTH_PROMPT_<i>`, `customDepthWI_<d>_<r>`, `customWIOutlet_<k>` | `constants.js:48` |
| `PERSONA_DESCRIPTION` | `script.js:3204` |
| `1_memory`, `2_floating_prompt`, `3_vectors`, `4_vectors_data_bank`, `chromadb` | itemization, `script.js:5345-5349` |

`scan: true` makes an injection visible to WI scanning (e.g. Author's Note with `allowWIScan`).

### 1.6 Streaming — `StreamingProcessor` (`script.js:3540`)

| Member | Behavior | Cite |
|---|---|---|
| ctor | Initializes `result`, `messageId=-1`, `firstMessageText='...'`, `continueMessage`, `swipes[]`, `messageLogprobs[]`, `toolCalls[]`, `reasoningHandler = new ReasoningHandler(timeStarted)`, `images[]`, `reasoningSignature` | `3549-3585` |
| `generate()` | Steps listed below the table | `3874-3914` |
| `onProgressStreaming(id, text, isFinal)` | Steps listed below the table | `3655-3740` |
| `finalizeIntermediaryMessage` | Steps listed below the table | `3752-3807` |
| `onFinishStreaming` | Finalize, auto-swipe check, `saveChatConditional`, play sound | `3809-3819` |
| `onErrorStreaming` | Abort; emits `MESSAGE_RECEIVED` / `CHARACTER_MESSAGE_RENDERED` except for swipe, impersonate and continue | `3821-3832` |
| `onStopStreaming` | `abortController.abort(); isFinished = true`. Stopping keeps the partial text | `3849-3852` |

**`generate()`** (`3874-3914`):
1. On the first call, `onStartStreaming('...')` creates a placeholder message via `saveReply(fromStreaming:true)`.
2. Cache `getStoppingStrings` once.
3. Iterate `for await ({text, swipes, logprobs, toolCalls, state} of generator())`. For each chunk:
   - record time-to-first-token;
   - update reasoning from `state.reasoning`, plus images and signature;
   - emit `STREAM_TOKEN_RECEIVED(text)`;
   - call `onProgressStreaming`, throttled via `Stopwatch(1000/power_user.streaming_fps)`.
4. On exception → `onErrorStreaming()`.

**`onProgressStreaming(id, text, isFinal)`** (`3655-3740`):
1. `cleanUpMessage(displayIncompleteSentences=!isFinal)`.
2. While not final, balance odd counts of `*`, `"`, ```` ``` ```` and `~~~` by appending the closer.
3. Write `chat[id].mes`, `gen_started` / `gen_finished`, `extra.time_to_first_token`.
4. `reasoningHandler.process`.
5. Compute the final token count.
6. For swipe / continue, mirror into `swipes[swipe_id]` and `swipe_info`.
7. Render via `messageFormatting` (optionally with `stream_fade_in`).

**`finalizeIntermediaryMessage`** (`3752-3807`):
1. Final progress update.
2. Add copy buttons to code blocks.
3. `reasoningHandler.finish`.
4. Append multi-swipes, parsing reasoning per swipe (`parseReasoningInSwipes`).
5. `syncMesToSwipe`.
6. `saveLogprobsForActiveMessage`.
7. Save inline images and store the signature.
8. Emit `MESSAGE_RECEIVED` + `CHARACTER_MESSAGE_RENDERED` (or `IMPERSONATE_READY`).

**Generator contract.** Each backend returns an `async function*` that yields cumulative `{text, swipes, logprobs, toolCalls, state:{reasoning, images?, signature?}}`:

| Backend | Generator | Cite |
|---|---|---|
| Text completion | `generateTextGenWithStreaming` | `textgen-settings.js:1289` |
| NovelAI | `generateNovelWithStreaming` | `nai-settings.js:741` |
| Kobold | `generateKoboldWithStreaming` | `kai-settings.js:229` |
| Chat Completion | `sendOpenAIRequest`, using `getStreamingReply` | `openai.js:3138`, `openai.js:3222` |

Dispatch happens in `sendStreamingRequest` (`script.js:6147`). Endpoints, from `getGenerateUrl` (`script.js:6172`):

| API | Endpoint |
|---|---|
| kobold | `/api/backends/kobold/generate` |
| koboldhorde | `/api/backends/koboldhorde/generate` |
| textgenerationwebui | `/api/backends/text-completions/generate` |
| novel | `/api/novelai/generate` |
| openai (CC) | `/api/backends/chat-completions/generate` |

Example: the text completion streaming loop (`textgen-settings.js:1311-1340`):
- Multi-swipe chunks are routed into `swipes[index-1]` when `choices[0].index > 0` (or llama.cpp `index > 0`).
- Primary text accumulates `choices[0].text || content`.
- Reasoning comes from `choices[0].reasoning ?? thinking`.

**SSE parsing** (`scripts/sse-stream.js`)

`EventSourceStream` (`sse-stream.js:10`):
1. Decode with a UTF-8 `TextDecoderStream`.
2. Split the buffer into events on a blank line (`\r\n\r\n|\r\r|\n\n`).
3. Read the fields `event`, `data` (multiple data lines are joined with `\n`) and `id` (rejected if it contains `\0`).
4. Skip events with empty data.
5. Emit `MessageEvent(type||'message', {data, lastEventId})`.

`SmoothEventSourceStream` (`sse-stream.js:340`) is used when `power_user.smooth_streaming` is on (`sse-stream.js:381`). It re-splits each JSON event into one event per character, using `parseStreamData` (`sse-stream.js:113`). Non-primary choices (`index>0`) pass through unsplit. Recognised shapes:

| Provider | Field(s) |
|---|---|
| Cohere | `delta.message.content.text` |
| Claude | `delta.text` / `delta.thinking` |
| Gemini | `candidates[].content.parts[].text`, with the `thought` flag; tool and inline-data parts pass through |
| NovelAI / KoboldCpp | `token` |
| llama.cpp | `content` |
| OpenAI-likes | `choices[0].text`, `.thinking`, `delta.text`, `delta.reasoning_content`, `delta.reasoning`, `delta.content` (string), Mistral `content[0].thinking[0].text`, `message.content` |

Per-character delay, `getDelay` (`sse-stream.js:88`):

| Character | Delay |
|---|---|
| Any | base = `max(100 - smooth_streaming_speed, 1) * 0.4` ms |
| `,` or `\n` | base × 12.5 |
| `.` `!` `?` | base × 25 |
| Window unfocused, or reasoning with `smooth_streaming_no_think` | no delay |

**`streaming-display.js`.** `StreamingDisplay` (`streaming-display.js:35`) is a floating, toast-like panel with a label, LED, reasoning section, content section, and stop / minimize / close buttons. It is used by extension-side `ConnectionManagerRequestService` streaming and is not part of the main chat stream. API:

| Method | Cite |
|---|---|
| `show({label, icon, onStop})` | `76` |
| `updateReasoning` | `261` |
| `updateContent` | `276` |
| `markStopped` | `301` |
| `complete({label, delay=3000})` | `334` |
| `hide` | `372` |

### 1.7 Output cleanup — `cleanUpMessage()` (`script.js:6442`), in order

1. **User prompt bias.** Prepend `power_user.user_prompt_bias` (macro-substituted), unless impersonate, continue, or `includeUserPromptBias=false` (`6453-6461`).
2. **Stopping strings** (`6469-6478`). Only **trailing partial matches** are removed: if the message ends with a prefix of a stop string, that prefix is cut. Full stop-string truncation is done by the backend/API during generation. `getStoppingStrings` (`script.js:3025`) builds the list:
   - CC: only custom stop strings.
   - TC, with `names_as_stop_strings`: `\n{{user}}:`; `\n{{char}}:` when impersonating or continuing on a user message; other group members' `\nName:`.
   - TC: instruct sequences (`getInstructStoppingSequences`) and custom strings (`getCustomStoppingStrings`).
   - `\n` first, if `single_line`.
   - The list is deduplicated.
3. **Regex.** `AI_OUTPUT` placement, or `USER_INPUT` when impersonating (`6481`).
4. **Whitespace.** `collapse_newlines`; strip trailing whitespace on each line (`6483-6490`).
5. **Wrong names** (`6492-6516`). Only applies when `allow_name1_display` / `allow_name2_display` is false. If a non-impersonate output starts with `{{user}}:`, the whole response is dropped. Anything after a trailing `\n{{user}}:` is cut.
6. **Special tokens** (`6518-6546`). Cut at `<|endoftext|>`. Instruct: cut at `stop_sequence` and at `input_sequence`. With `sequences_as_stop_strings`, remove leaked input / output / last-output sequence lines.
7. **Groups.** `cleanGroupMessage` cuts at the first `\nOtherMember:`, unless `disable_group_trimming` (`6549-6551`, `3171`).
8. **Final passes** (`6553-6591`):
   - remove `\n{{char}}:` prefixes if `!allow_name2_display`;
   - `auto_fix_generated_markdown` → `fixMarkdown`;
   - trim the leading own name;
   - `trim_sentences` → `trimToEndSentence` (unless displaying incomplete sentences);
   - `trim_spaces` → `trim()`, unless a reasoning prefix is pending.

### 1.8 Message insertion — `saveReply()` (`script.js:6642`)

**Type coercion** (`6651-6654`). If the last message is from the user or has no `swipe_id`, the type becomes `normal` (unless it is append / continue / appendFinal).

| Mode | Behavior | Cite |
|---|---|---|
| `swipe` | `swipes.length++`. If the current `swipe_id` is the new last slot, overwrite `mes`, `title`, `gen_started`, `gen_finished`, `send_date`, `extra.api`, `extra.model`, `extra.reasoning`, `extra.reasoning_duration=null`, `extra.reasoning_signature`; attach images; count tokens. Emit `MESSAGE_RECEIVED` / `CHARACTER_MESSAGE_RENDERED` (not when `fromStreaming`) | `6671-6696` |
| `append` / `continue` | `mes += getMessage` | `6697-6718` |
| `appendFinal` | `mes = getMessage`; `extra.reasoning += reasoning` | `6719-6740` |
| `normal` | Push `{name: name2, is_user:false, send_date, mes, title, gen_started, gen_finished, extra:{api, model, reasoning, reasoning_duration, reasoning_signature, token_count?}}`. In groups, also set `force_avatar`, `original_avatar`, `extra.gen_id` | `6741-6784` |

**After the mode-specific write:**
1. **Swipe sync** (`6786-6809`). Set `swipes[swipe_id] = mes` and `swipe_info[swipe_id] = {send_date, gen_started, gen_finished, extra: clone(extra)}`. If the message has no swipes yet, initialize `swipe_id=0, swipes=[mes]`.
2. **Extra swipes** (`6811-6826`). Multi-swipes are appended with a stripped `extra`.
3. **Stats.** `statMesProcess` updates statistics (`6828`).

`extra.api` = `getGeneratingApi()` (`7039`); `extra.model` = `getGeneratingModel()` (`7050`).

**User messages** — `sendMessageAsUser()` (`script.js:5874`):
1. Apply the `USER_INPUT` regex.
2. Build `{name, is_user:true, is_system:false, send_date, mes: substituteParams(text), extra:{isSmallSys, token_count?, bias?}, force_avatar (persona thumbnail)}`.
3. `populateFileAttachment`, then `statMesProcess`.
4. Push the message, or splice it in at `insertAt`.
5. `saveChatConditional`.
6. Emit `MESSAGE_SENT(id)`, render, emit `USER_MESSAGE_RENDERED(id)`.

### 1.9 Swipes (`script.js:9954` `swipe()` + helpers)

**Data model** for each AI message (`ensureSwipes`, `script.js:6837`):
- `mes` — the displayed text;
- `swipe_id` — int;
- `swipes: string[]`;
- `swipe_info: {send_date, gen_started, gen_finished, extra}[]`.

`syncMesToSwipe` (`6896`) copies the current `mes`, `extra` and dates into the active slot. `syncSwipeToMes` (`6954`) loads a slot into the message.

| Concept | Rule | Cite |
|---|---|---|
| Allowed globally | Chat non-empty; `swipes` setting on and not hidden; not generating; `swipeState === NONE` | `9162-9173` |
| Swipeable message | Last message only; below the message being edited; not `isSmallSys`; not `extra.swipeable===false`; not from the user | `9183-9214` |
| Overswipe behavior | First match wins: `extra.overswipe_behavior` override → `NONE` for non-swipeable / small-sys → `PRISTINE_GREETING` for message 0 when `!chat_metadata.tainted` → `REGENERATE` for AI messages → otherwise `LOOP` | `9223-9240`; enum `constants.js:147-158` |
| Left | `swipe_id--`; wraps to the last swipe | `10369-10383` |
| Right | `swipe_id++`. Past the end, by behavior: NONE → cancel. REGENERATE → `clearMessageData`, show `...`, emit `MESSAGE_SWIPED`, `Generate('swipe')`. LOOP / PRISTINE → go to 0 | `10384-10418`, `10270-10322` |
| `clearMessageData` | Deletes `extra.memory, display_text, media, inline_image, files, fileLength, generationType, negative, title, append_title` and `gen_started` / `gen_finished` | `10133-10149` |
| Sources that bypass checks | `DELETE, BACK, AUTO_SWIPE, SLASH_COMMAND, SWIPE_PICKER` (`constants.js:173`) | `9973-9975` |
| End | `endSwipe` waits for the generation, clamps the id, reverts on failure (shake + red flash), then `saveChatDebounced()` | `10044-10102` |
| `deleteSwipe(swipeId, messageId)` | Cannot delete the last remaining swipe. Splices `swipes` + `swipe_info`; the new id is the previous or next swipe; sets `tainted=true`; emits `MESSAGE_SWIPE_DELETED({messageId, swipeId, newSwipeId})`; animates if the deleted swipe was current | `9339-9406` |
| Greeting | `getFirstMessage()` builds `swipes = [first_mes, ...alternate_greetings]` (AI_OUTPUT regex) with `swipe_id 0` | `7710-7742` |

### 1.10 Saving, loading, editing

**`saveChatConditional()`** (`script.js:9412`):
1. Wait for any in-flight save (`isChatSaving`).
2. Cancel the debounce timer.
3. Group → `saveGroupChat(id, true)`; otherwise `saveChat()`.
4. `saveTokenCache()` (IndexedDB) and `saveItemizedPrompts(chatId)`.

**`saveChatDebounced()`** (`script.js:7361`): a `DEFAULT_SAVE_EDIT_TIMEOUT` timer, aborted if the character or group changed in the meantime.

**`saveChat({chatName, withMetadata, mesId, force, chatData})`** (`script.js:7395`):
- The body `{ch_name, file_name, chat: [{chat_metadata, user_name:'unused', character_name:'unused'}, ...messages], avatar_url, force}` is compressed (`compressRequest`) and POSTed to `/api/chats/save`.
- The server returns `{error:'integrity'}` if the `chat_metadata.integrity` UUID does not match. The user must then type `OVERWRITE` to force the save; otherwise the page reloads (`7453-7480`).
- `chat_metadata.integrity` is initialized with `uuidv4()` on load (`7665-7667`).

**Load — `getChat()`** (`script.js:7634`). POST `/api/chats/get {ch_name, file_name, avatar_url}`. The first element of the response is the header. `getChatResult()` then:
1. inserts the greeting into an empty chat;
2. `loadItemizedPrompts`;
3. renders;
4. emits `CHAT_CHANGED(chatId)`, `CHAT_CREATED` (fresh chats only), and `MESSAGE_RECEIVED` / `CHARACTER_MESSAGE_RENDERED(0,'first_message')`.

**Edit:**
- `updateMessage()` (`8139`) applies regex (`USER_INPUT`; `SLASH_COMMAND` for narrator messages; otherwise `AI_OUTPUT`; with `isEdit:true`), applies `trim_spaces`, extracts bias, and mirrors the text into the active swipe.
- `messageEditDone` (`8397`) emits `MESSAGE_EDITED`, re-renders, emits `MESSAGE_UPDATED`, then saves.

**Delete.** `deleteMessage(id, swipeIdx, ask, deleteToolCalls)` (`1639`) also removes the preceding tool-invocation system messages (`getMessageDeletionStartId`, `1614`). Emits `MESSAGE_DELETED(chat.length)`.

### 1.11 Raw / quiet helpers (extension API)

| Function | Purpose | Cite |
|---|---|---|
| `generateQuietPrompt({quietPrompt, quietToLoud, skipWIAN, quietImage, quietName, responseLength, forceChId, jsonSchema, removeReasoning=true, trimToSentence})` | Background generation with full context (`Generate('quiet')` with `force_name2`). A temporary response length is applied via `TempResponseLength` | `script.js:3084` |
| `createRawPrompt(prompt, api, instructOverride, quietToLoud, systemPrompt, prefill)` | Converts a string or `[{role,content}]` into either CC messages (the prefill becomes a trailing assistant message) or a TC string (`name: ` prefixes or instruct formatting; system prompt via the story-string formatter; last line from `formatInstructModePrompt`) | `script.js:3924` |
| `generateRawData(params)` / `generateRaw(params)` | Generation with no chat context. Emits `GENERATE_AFTER_COMBINE_PROMPTS` (TC) or `CHAT_COMPLETION_PROMPT_READY` (CC). Aborts on `GENERATION_STOPPED`. Returns raw data / a cleaned string (`cleanUpMessage` without user bias) | `script.js:4000`, `script.js:4122` |
| `TempResponseLength` | Saves and restores `amount_gen` or `openai_max_tokens`. The restore is hooked on `GENERATE_AFTER_DATA` / `CHAT_COMPLETION_SETTINGS_READY` | `script.js:4156-4245` |

### 1.12 Auto-continue (`script.js:5716`)

Auto-continue fires only when all of these hold:
- `power_user.auto_continue.enabled`;
- not impersonate, not currently sending, not aborted;
- `target_length > 0`;
- for CC, `allow_chat_completions` is on;
- the textarea is empty;
- the trimmed chunk is longer than 5 characters;
- `getTokenCount(lastMessage.mes) < target_length`;
- not a group chat (`5783-5792`).

It triggers by clicking `#option_continue`.

### 1.13 Events (`scripts/events.js`)

**Emitter semantics:**
- `eventSource = new EventEmitter([APP_READY, APP_INITIALIZED])` (`events.js:113`). These two are "auto-fire" events: a listener that subscribes after the event already fired is called immediately with the last args (`lib/eventemitter.js:31-35`, `56-57`, `156`).
- `emit()` is async and **awaits each listener in turn** (`lib/eventemitter.js:130-146`).
- `emitAndWait()` is the synchronous variant (`lib/eventemitter.js:160`).
- `makeFirst` / `makeLast` reorder listeners.

| Event (value) | Fired at | Args / when |
|---|---|---|
| APP_INITIALIZED `app_initialized` | `script.js:787` | End of `firstLoadInit`, before the UI is ready |
| APP_READY `app_ready` | `script.js:790` | App fully loaded |
| EXTRAS_CONNECTED `extras_connected` | `extensions.js:754` | Extras API connected (legacy) |
| MESSAGE_SWIPED `message_swiped` | `script.js:10315` | `(mesId)` during the swipe animation (before generation, for overswipe) |
| MESSAGE_SENT `message_sent` | `script.js:5910,5917`; `slash-commands.js:6058`; caption ext | `(id)` after the user message is pushed and saved, before render |
| MESSAGE_RECEIVED `message_received` | `script.js:3799,3828,6691,6716,7705` | `(id, type)` after the AI message is stored (before render for non-stream; after the final chunk for stream) |
| MESSAGE_EDITED `message_edited` | `script.js:8405`; `slash-commands.js:5850,5916` | `(id)` after the edit is applied, before re-render |
| MESSAGE_DELETED `message_deleted` | `script.js:1611,1699,4411,11734` | `(chat.length)` after deletion |
| MESSAGE_UPDATED `message_updated` | `script.js:8337,8431`; `slash-commands.js:5857,5923` | `(id)` after the edited message is re-rendered |
| MESSAGE_FILE_EMBEDDED | `chats.js:496` | File attached to a message |
| MESSAGE_REASONING_EDITED / _DELETED | `reasoning.js:1325` / `1393` | Reasoning block edited / deleted |
| MESSAGE_SWIPE_DELETED | `script.js:9388` | `{messageId, swipeId, newSwipeId}` |
| MORE_MESSAGES_LOADED | `script.js:1474` | "Show more messages" lazy render |
| IMPERSONATE_READY | `script.js:3802,5525` | `(text)` |
| CHAT_CHANGED `chat_id_changed` | `script.js:1723,7700,10760,10913` | `(chatId)` after chat load / reload / close / character removed |
| CHAT_LOADED `chatLoaded` | `script.js:7669` | `{detail:{id, character}}` after `/api/chats/get` |
| GENERATION_STARTED | `script.js:4299` | `(type, opts, dryRun)` |
| GENERATION_AFTER_COMMANDS | `script.js:4321` | After the slash-command check |
| GENERATION_STOPPED | `script.js:5618` | Stop button / `stopGeneration()` |
| GENERATION_ENDED | `script.js:3536` | `(chat.length)` when the stop button is hidden |
| SD_PROMPT_PROCESSING | `extensions/stable-diffusion/index.js:3056` | SD extension |
| EXTENSIONS_FIRST_LOAD | `extensions.js:1794` | First extension activation |
| EXTENSION_SETTINGS_LOADED | `script.js:8025`, `extensions.js:1767` | |
| SETTINGS_LOADED_BEFORE / SETTINGS_LOADED_AFTER / SETTINGS_LOADED | `script.js:7940` / `7982` / `8047` | During `getSettings()` (`7913`) |
| SETTINGS_UPDATED | `script.js:8110` | After a successful `/api/settings/save` |
| GROUP_UPDATED | `group-chats.js:1777,2003` | Group edited |
| MOVABLE_PANELS_RESET | `power-user.js:2711` | UI |
| CHATCOMPLETION_SOURCE_CHANGED / CHATCOMPLETION_MODEL_CHANGED | `openai.js:6976` / `6017` | CC source / model switched |
| OAI_PRESET_CHANGED_BEFORE / _AFTER | `openai.js:5034` / `5078` | CC preset switch |
| OAI_PRESET_EXPORT_READY / OAI_PRESET_IMPORT_READY | `openai.js:4887` / `4813` | Preset (de)serialization hooks |
| WORLDINFO_SETTINGS_UPDATED | `world-info.js:5842,6228` | |
| WORLDINFO_UPDATED | `world-info.js:4160` | `(name, data)` after a book save |
| CHARACTER_EDITOR_OPENED | `script.js:8839` | `select_selected_character` |
| CHARACTER_EDITED | `script.js:9902`; `slash-commands.js:5437` | After `/api/characters/edit` |
| CHARACTER_PAGE_LOADED | `script.js:1067` | Character list rendered |
| CHARACTER_GROUP_OVERLAY_STATE_CHANGE_BEFORE / _AFTER | `BulkEditOverlay.js:434` / `437` | Bulk edit |
| USER_MESSAGE_RENDERED / CHARACTER_MESSAGE_RENDERED | `script.js:5912,5919` / `script.js:3800,3829,6693,6718,7706` | After DOM render |
| FORCE_SET_BACKGROUND | `extensions/stable-diffusion/index.js:3020` | |
| CHAT_DELETED / CHAT_CREATED / CHAT_RENAMED | `script.js:1354,1401,10884` / `7701` / `10717` | |
| GROUP_CHAT_DELETED / GROUP_CHAT_CREATED | `group-chats.js:1334,2269,2308` / `319` | |
| GENERATE_BEFORE_COMBINE_PROMPTS | `script.js:5234` | TC data object; a listener may set `combinedPrompt` |
| GENERATE_AFTER_COMBINE_PROMPTS | `script.js:5243`, `4031` | `{prompt, dryRun}`, mutable |
| GENERATE_AFTER_DATA | `script.js:5318` | `(generate_data, dryRun)`, mutable request body |
| GROUP_MEMBER_DRAFTED | `group-chats.js:1059` | `(chid)` before a member generates |
| GROUP_WRAPPER_STARTED / _FINISHED | `group-chats.js:1049` / `1088` | `{selected_group, type}` |
| WORLD_INFO_ACTIVATED | `world-info.js:902` | `(activatedEntries[])` |
| TEXT_COMPLETION_SETTINGS_READY | `textgen-settings.js:1847` | `(params)`, mutable TC body |
| CHAT_COMPLETION_SETTINGS_READY | `openai.js:3146` | `(generate_data)`, mutable CC body |
| CHAT_COMPLETION_PROMPT_READY | `openai.js:1619`, `script.js:4037` | `{chat: messages[], dryRun}`, mutable |
| CHARACTER_FIRST_MESSAGE_SELECTED | `group-chats.js:589` | Group greeting selection |
| CHARACTER_DELETED `characterDeleted` | `script.js:10888` | |
| CHARACTER_DUPLICATED | `script.js:6077`; `BulkEditOverlay.js:59` | |
| CHARACTER_RENAMED | `script.js:7246` | |
| CHARACTER_RENAMED_IN_PAST_CHAT | `script.js:7335`; `group-chats.js:731` | |
| STREAM_TOKEN_RECEIVED `stream_token_received` | `script.js:3895` | `(cumulativeText)` on each chunk. `SMOOTH_STREAM_TOKEN_RECEIVED` is a deprecated alias with the same value; nothing emits it under that name |
| STREAM_REASONING_DONE | `reasoning.js:549` | Reasoning block closed during a stream |
| FILE_ATTACHMENT_DELETED / MEDIA_ATTACHMENT_DELETED | `chats.js:1144` / `1113` | |
| WORLDINFO_FORCE_ACTIVATE | Emitted by `extensions/vectors/index.js:1725`; consumed by WI | `(entries[])`, force-activated on the next scan |
| OPEN_CHARACTER_LIBRARY | `script.js:12587` | |
| ONLINE_STATUS_CHANGED | `script.js:7156` | `(status)` |
| IMAGE_SWIPED | `chats.js:2086` | Gallery swipe on message media |
| CONNECTION_PROFILE_LOADED | `extensions/connection-manager/index.js:727,740,752,765` | |
| CONNECTION_PROFILE_CREATED | `extensions/connection-manager/index.js:780,996` | |
| CONNECTION_PROFILE_DELETED | `extensions/connection-manager/index.js:347` | |
| CONNECTION_PROFILE_UPDATED | `extensions/connection-manager/index.js:796,879,1015` | |
| TOOL_CALLS_PERFORMED / TOOL_CALLS_RENDERED | `tool-calling.js:917` / `919` | `(invocations)` |
| CHARACTER_MANAGEMENT_DROPDOWN `charManagementDropdown` | `script.js:12458` | |
| SECRET_WRITTEN / DELETED / ROTATED / EDITED | `secrets.js:375` / `400` / `471` / `494` | |
| PRESET_CHANGED | `kai-settings.js:533`, `nai-settings.js:894`, `openai.js:5079`, `textgen-settings.js:957` | `{apiId, name}` |
| PRESET_DELETED | `openai.js:4975`, `preset-manager.js:1153` | |
| PRESET_RENAMED / PRESET_RENAMED_BEFORE | `preset-manager.js:1073` / `1069` | |
| MAIN_API_CHANGED | `script.js:11750` | `{apiId}` |
| WORLDINFO_ENTRIES_LOADED | `world-info.js:4604` | Lets listeners mutate the sorted entry lists |
| WORLDINFO_SCAN_DONE | `world-info.js:5175` | Per-loop scan state (mutable) |
| PERSONA_CHANGED | `personas.js:166` | |
| PERSONA_CREATED | `personas.js:535,599,1078,1934` | |
| PERSONA_UPDATED | `personas.js:1234,1245,1259,1309` | |
| PERSONA_RENAMED | `personas.js:884,2176` | |
| PERSONA_DELETED | `personas.js:1195` | |
| TTS_JOB_STARTED / TTS_AUDIO_READY / TTS_JOB_COMPLETE | `extensions/tts/index.js:509` / `518` / `533` | |
| ITEMIZED_PROMPTS_LOADED / SAVED / DELETED | `itemized-prompts.js:35` / `53` / `90,103` | |

---

## 2. Prompt construction per API family

`main_api` values: `kobold` (KoboldAI classic), `koboldhorde` (AI Horde), `textgenerationwebui` (all Text Completion backends), `novel` (NovelAI), `openai` (all Chat Completion sources). Dispatch: `Generate()` `script.js:5249-5316`, `getGenerateUrl` `script.js:6172`, `sendStreamingRequest` `script.js:6147`. Each subsection declares its own citation abbreviations.

> **Chat-completion family (§2.A, §2.L–2.N) abbreviations.** All paths are relative to the SillyTavern 1.19.0 source root (commit 06bde93).
**O** = `public/scripts/openai.js` · **PM** = `public/scripts/PromptManager.js` · **TC** = `public/scripts/tool-calling.js` · **CR** = `public/scripts/custom-request.js` · **SH** = `public/scripts/extensions/shared.js` · **S** = `public/script.js` · **BE** = `src/endpoints/backends/chat-completions.js` · **PC** = `src/prompt-converters.js` · **K** = `src/constants.js`

> Note: this tree is a newer 1.19 build than the public release. Its model IDs include `gpt-5.6-terra`, `gpt-6-astra`, `claude-sonnet-5`, `claude-fable` and `gemini-3.7-flash`. Everything below matches the source as it is on disk.

---

### 2.A Chat Completion (openai.js + PromptManager.js)

#### 2.A.1 `chat_completion_sources` enum (O:177-204; backend mirror K:187-214)

There are 26 sources. 01.AI is not in this build.

| id | model setting key (O:1722-1780) | default model (O:440-473) | backend handler | provider base URL (BE:73-101) |
|---|---|---|---|---|
| `openai` | `openai_model` | `gpt-5.6-terra` | generic branch BE:2281 | `api.openai.com/v1` or `reverse_proxy` |
| `claude` | `claude_model` | `claude-sonnet-5` | `sendClaudeRequest` BE:230 → `/messages` | `api.anthropic.com/v1` or proxy |
| `openrouter` | `openrouter_model` (`OR_Website` sends `null`, O:1736) | `OR_Website` (O:479) | generic BE:2301 | `openrouter.ai/api/v1` |
| `ai21` | `ai21_model` | `jamba-large` | `sendAI21Request` BE:800 | `api.ai21.com/studio/v1` |
| `makersuite` | `google_model` | `gemini-3.7-flash` | `sendMakerSuiteRequest` BE:454 | `generativelanguage.googleapis.com` |
| `vertexai` | `vertexai_model` | `gemini-3.7-flash` | `sendMakerSuiteRequest` BE:454 (`useVertexAi`, BE:455) | `us-central1-aiplatform…` / region / express |
| `mistralai` | `mistralai_model` | `mistral-large-latest` | `sendMistralAIRequest` BE:881 | `api.mistral.ai/v1` or proxy |
| `custom` | `custom_model` | `''` | generic BE:2394 | `custom_url` |
| `cohere` | `cohere_model` | `command-r-plus` | `sendCohereRequest` BE:971 → v2 `/chat` | `api.cohere.ai/v2` |
| `perplexity` | `perplexity_model` | `sonar-pro` | generic BE:2422 | `api.perplexity.ai` |
| `groq` | `groq_model` | `llama-3.3-70b-versatile` | generic BE:2438 | `api.groq.com/openai/v1` |
| `electronhub` | `electronhub_model` | `gpt-4o-mini` | `sendElectronHubRequest` BE:1394 | `api.electronhub.ai/v1` |
| `chutes` | `chutes_model` | `deepseek-ai/DeepSeek-V3-0324` | `sendChutesRequest` BE:1506 | `llm.chutes.ai/v1` |
| `nanogpt` | `nanogpt_model` (+`nanogpt_provider`, `nanogpt_payg_override`) | `gpt-4o-mini` | generic BE:2476 | `nano-gpt.com/api/v1` |
| `deepseek` | `deepseek_model` | `deepseek-v4-flash` | `sendDeepSeekRequest` BE:1071 | `api.deepseek.com/beta` or proxy |
| `aimlapi` | `aimlapi_model` | `chatgpt-4o-latest` | `sendAimlapiRequest` BE:1289 | `api.aimlapi.com/v1` |
| `xai` | `xai_model` | `grok-3-beta` | `sendXaiRequest` BE:1183 | `api.x.ai/v1` or proxy |
| `pollinations` | `pollinations_model` (+`pollinations_endpoint`: `authenticated`/`anonymous`, O:272) | `openai` | generic BE:2512 | `gen.pollinations.ai/v1` or `text.pollinations.ai/v1` (anonymous) |
| `moonshot` | `moonshot_model` | `kimi-latest` | generic BE:2531 | `api.moonshot.ai/v1` or proxy |
| `fireworks` | `fireworks_model` | `accounts/fireworks/models/kimi-k2-instruct` | generic BE:2454 | `api.fireworks.ai/inference/v1` |
| `cometapi` | `cometapi_model` | `gpt-4o` | generic BE:2543. **Throws "temporarily disabled"** (BE:2550) | `api.cometapi.com/v1` |
| `azure_openai` | `azure_openai_model` (+`azure_base_url`, `azure_deployment_name`, `azure_api_version`='2024-02-15-preview') | `''` | `sendAzureOpenAIRequest` BE:1687 → `/openai/deployments/{dep}/chat/completions` | user base URL |
| `zai` | `zai_model` (+`zai_endpoint`: `common`/`coding`, O:267) | `glm-4.6` | generic BE:2551 | `api.z.ai/api/paas/v4` or `/coding/paas/v4` |
| `siliconflow` | `siliconflow_model` (+`siliconflow_endpoint`: `global`/`cn`) | `deepseek-ai/DeepSeek-V3` | generic BE:2566 | `.com` or `.cn` |
| `workers_ai` | `workers_ai_model` (+`workers_ai_account_id`) | `@cf/meta/llama-3.3-70b-instruct-fp8-fast` | generic BE:2576 | `api.cloudflare.com/client/v4/accounts/{id}/ai/v1` |
| `minimax` | `minimax_model` (+`minimax_endpoint`: `global`/`cn`) | `MiniMax-M2.7` | `sendMinimaxRequest` BE:1607 | `api.minimax.io/v1` or `api.minimaxi.com/v1` |

**Backend endpoints**
- `POST /api/backends/chat-completions/generate` (BE:2242). It applies `custom_prompt_post_processing` first (BE:2246-2252). It then flattens `json_schema` (BE:2255) and dispatches dedicated sources (BE:2259-2273). Everything else goes through the generic OpenAI-compatible branch.
  - Generic-branch base request body (BE:2652-2669): `messages | prompt, model, temperature, max_tokens, max_completion_tokens, stream, presence_penalty, frequency_penalty, top_p, top_k, stop, logit_bias, seed, n, ...bodyParams`.
  - `tools`/`tool_choice` are attached at BE:2636-2639.
  - A default `response_format: json_schema` is added at BE:2641-2650.
  - Custom source: `custom_exclude_body` is applied at BE:2671-2673.
  - Streaming is forwarded raw as SSE (BE:2691-2693).
  - Errors return `{error:{message}, quota_error}` (BE:2701-2713).
  - Text-completion models (`gpt-3.5-turbo-instruct` etc., O:146-172) go to `/completions` (BE:2279, 2625-2628).
- `POST /status` (BE:1778) returns a model list `{data:[...]}`, or `{bypass:true}`.
- `POST /bias?model=<tokenizer>` (BE:2158) tokenizes each bias entry and returns `{tokenId: value}`.
  - Claude gets `{}` (BE:2166-2169).
  - Entry text shaped `[1,2,3]` is treated as raw token ids (BE:2215-2224).
- `POST /process` (BE:2979-2995) runs standalone post-processing. Nothing in the frontend calls it.

#### 2.A.2 Per-source request parameters: `createGenerationParameters` (O:2691-3126)

Base `generate_data` (O:2803-2828):
- Core fields: `type, messages, model, temperature, frequency_penalty, presence_penalty, top_p, max_tokens, stream, logit_bias, stop, chat_completion_source, n`.
- Name fields for the backend: `user_name, char_name, group_names`.
- Feature flags: `include_reasoning`(=`show_thoughts`), `reasoning_effort`, `enable_web_search`, `request_images`, `request_image_resolution`, `request_image_aspect_ratio`.
- Other: `custom_prompt_post_processing`, `verbosity`.

Preprocessing and gates:
- Messages that are null or not objects are filtered out (O:2697).
- DeepSeek vision-exp: images are stripped from system and assistant messages (O:2700-2710).
- `stream = stream_openai && type!=='quiet' && !o1 && !(workers_ai && jsonSchema)` (O:2783-2785).
- Multi-swipe (`n`) requires `n>1`, a type other than quiet/impersonate/continue, and a source in the multiswipe list (O:2787-2788).
- Logit bias is computed via `/bias` and cached in `biasCache` (O:2790-2801, 3401-3418).
- Stop strings come from `getCustomStoppingStrings(4)` (O:144). An empty `stop` is deleted (O:2845).
- Tools are registered only when not multi-swipe and `ToolManager.canPerformToolCalls` (O:2840-2842).
- Proxy fields `reverse_proxy` + `proxy_password` are sent only for proxy sources, after `validateReverseProxy` (O:2849-2853).
- `logprobs=5` is sent when `power_user.request_token_probabilities` is on and the source supports it (O:2855-2859).

**Capability lists (exact)**

| capability | sources | cite |
|---|---|---|
| `seed` sent when ≥0 | openai, azure_openai, openrouter, mistralai, custom, cohere, groq, electronhub, nanogpt, xai, pollinations, aimlapi, vertexai, makersuite, chutes | O:2720-2737, 3046-3048 |
| reverse proxy | claude, openai, mistralai, makersuite, vertexai, deepseek, xai, zai, moonshot | O:2739-2750 |
| logprobs | openai, azure_openai, openrouter, custom, deepseek, xai, aimlapi, chutes | O:2752-2762 |
| logit_bias | openai, azure_openai, openrouter, electronhub, chutes, custom | O:2764-2772 |
| `n` multi-swipe | openai, azure_openai, custom, xai, aimlapi, moonshot | O:2774-2781 |
| string `reasoning_effort` mapping | openai, azure, custom, xai, aimlapi, openrouter, pollinations, perplexity, cometapi, electronhub, chutes, deepseek, fireworks (all others receive the raw setting) | O:2550-2568 |
| status check skipped ("Key saved") | claude, ai21, vertexai, perplexity, zai, minimax | O:4460-4472 |
| status bypass | openai with `bypass_status_check`; custom always | O:4535 |

**`getReasoningEffort`** (O:2545-2663). Settings values are `auto|low|medium|high|min|max` (O:239-246).
- `auto` → `undefined` for every source.
- DeepSeek: min/low → `low`, max → `max`, anything else → `high`.
- Fireworks: min → `low`.
- Custom with a `koboldcpp/*` model: min → `minimal`, max → `xhigh`.
- `min` on other sources:
  - OpenRouter with `!show_thoughts` → `none`.
  - OpenAI/Azure `gpt-5.4/5.5/5.6` → `none`; other `gpt-5` → `min`.
  - Otherwise → `low`.
- `max`: OpenAI/Azure `gpt-6-astra` → `max`; `gpt-5.6` → `xhigh`; otherwise → `high`.
- ElectronHub: the result is kept only if it appears in the model's `metadata.supported_reasoning_efforts`.
- `getVerbosity`: `auto` → `undefined` (O:2671-2680).

**Per-source mutations of `generate_data`**

| source / condition | adds | deletes / changes | cite |
|---|---|---|---|
| azure_openai | `azure_base_url`, `azure_deployment_name`, `azure_api_version` | deletes `reasoning_effort` for `gpt-3*/4*` | O:2830-2838 |
| gpt vision model (openai/azure/openrouter) | | deletes `logit_bias`, `stop`, `logprobs` | O:2862-2867 |
| gpt-4.5 | | deletes `logprobs` | O:2868-2870 |
| claude | `top_k`, `use_sysprompt`, unlimited `stop`. `assistant_prefill` = `substituteParams(assistant_impersonation)` if impersonate, else `assistant_prefill`; skipped for quiet and for continue+`continue_prefill` | | O:2872-2882 |
| openrouter | `top_k, min_p, repetition_penalty, top_a, use_fallback, provider, quantizations, allow_fallbacks, middleout` | | O:2884-2894 |
| nanogpt | `nanogpt_provider, nanogpt_payg_override, top_k, min_p, repetition_penalty, top_a` | | O:2896-2899, 3028-3034 |
| fireworks (not quiet) | `chat_id` (backend turns it into an `x-session-affinity` HMAC header, BE:2474-2476) | | O:2901-2903 |
| makersuite / vertexai | `top_k`, `use_sysprompt`; `stop` limited to 5 strings of 1-16 chars; Vertex adds `vertexai_auth_mode/region/express_project_id` | | O:2905-2915 |
| mistralai | `safe_prompt:false`, unlimited `stop` | | O:2917-2920 |
| custom | `custom_url`, plus `custom_include_body/exclude_body/include_headers` (macro-substituted YAML) | | O:2922-2927 |
| cohere | `top_k`; `stop` limited to 5 | `top_p` clamped 0.01-0.99; freq/pres clamped 0-1 | O:2929-2937 |
| perplexity | `top_k`, freq/pres | deletes `stop` | O:2939-2944 |
| groq | | deletes `logprobs, logit_bias, top_logprobs, n` | O:2947-2952 |
| deepseek | | `top_p` of 0 → `Number.EPSILON` | O:2955-2957 |
| xai | | `grok-3-mini`: deletes pres/freq/stop; other models delete `reasoning_effort`. `grok-4*`/`grok-code`: delete pres/freq, and `stop` unless `grok-4-fast-non-reasoning` | O:2959-2979 |
| electronhub | `top_k` | | O:2981-2983 |
| chutes | `min_p`, `top_k` (only if >0), `repetition_penalty`, unlimited `stop` | | O:2985-2990 |
| zai | `zai_endpoint`; `stop` limited to 1 | `top_p` of 0 → 0.01; deletes pres/freq | O:2993-2999 |
| pollinations / siliconflow / minimax | respective `*_endpoint` | minimax clamps `temperature` to (ε, 1] | O:3001-3015 |
| workers_ai | `workers_ai_account_id`, `top_k` (≤50), `repetition_penalty`, `seed` (only if ≥1) | `top_p` ≥0.001; deletes `n`, `logit_bias` | O:3017-3026 |
| moonshot `kimi-k2.5` | | deletes temperature, top_p, freq, pres | O:3036-3044 |
| `o1/o3/o4` (openai/azure, or openrouter `openai/…`) | `max_completion_tokens` | deletes max_tokens, logprobs, stop, logit_bias, temp, top_p, freq, pres. For o1: system→user, and deletes `n`, `tools`, `tool_choice` | O:3050-3072 |
| `gpt-5*` (gpt sources) | `max_completion_tokens` | deletes logprobs. `gpt-5-chat-latest`: no tools. `gpt-5.1-5.4` without effort: deletes freq/pres/bias/stop. Otherwise deletes temp/top_p/freq/pres/bias/stop | O:3074-3095 |
| `gpt-6-astra` | `max_completion_tokens` | deletes temp, top_p, logprobs | O:3097-3104 |
| `claude-(fable\|opus-5\|sonnet-5)` on any source | | deletes temp, top_p, top_k, freq, pres; deletes `reasoning_effort` unless source is claude | O:3109-3120 |
| `jsonSchema` option | `json_schema` | | O:3122-3124 |

**Backend-only behavior that matters for parity**

- **Claude** (BE:230-452)
  - `convertClaudeMessages(messages, assistant_prefill, use_sysprompt, useTools, names)` (BE:250).
  - Web search tool `web_search_20250305` (BE:256, 308-314).
  - Tools are converted to `input_schema` (BE:284-296).
  - `json_schema` becomes a forced tool (or `output_config.format` on Fable 5.1) (BE:298-306).
  - Thinking: adaptive or `budget_tokens` via `calculateClaudeBudgetTokens` (BE:353-391).
  - A trailing assistant prefill is converted to user when thinking is on or the model can't prefill (BE:393-395).
  - Verbosity maps to `output_config.effort` (BE:397-401).
  - Prompt caching is set from config (`claude.cachingAtDepth`, BE:108).
  - Non-stream reply is wrapped as `{choices:[{message:{content}}], content}` (BE:436).
- **Google** (BE:454-800)
  - `convertGooglePrompt` produces `system_instruction` + `contents` with roles `user`/`model`, `inlineData` media, `functionCall`/`functionResponse` and `thoughtSignature` (PC:432-629).
  - Image generation: `responseModalities` + `imageConfig` when `request_images` and the model is an image model (BE:550-563).
  - `google_search` tool when web search is enabled (BE:596-600).
  - `thinkingConfig` with `thinkingBudget` or `thinkingLevel` (BE:603-622).
  - JSON output via `responseMimeType`/`responseSchema` (BE:499-500).
- **Forced prompt conversions**
  - DeepSeek: `SEMI_TOOLS`, then `prefix:true` on a trailing assistant message, plus `thinking` (BE:1119, 1137).
  - Perplexity: `STRICT` (BE:2429).
  - MiniMax: `MERGE_TOOLS` (BE:1626).
  - xAI: `convertXAIMessages` (BE:1230).
  - Mistral: `convertMistralMessages`, with optional `prefix` from config `mistral.enablePrefix` (PC:703-783).
  - Cohere: `convertCohereMessages` (PC:384-430).
  - AI21: `convertAI21Messages` (merge + names; BE:826).
  - Moonshot: `partial:true` on a trailing assistant message, unless JSON mode (BE:2537-2541).
- **Web search, other sources**: OpenRouter `plugins:[{id:'web'}]` (BE:198-206); NanoGPT appends a `:online` model suffix (BE:2488); ElectronHub `web_search:true` (BE:1412).
- **OpenRouter extras** (BE:2301-2393): `reasoning:{exclude:!include_reasoning, effort}`, `transforms` (middle-out), `provider.order`/`quantizations`, `route:'fallback'`, `embedOpenRouterMedia` + `addOpenRouterSignatures`, Claude/Gemini caching.

#### 2.A.3 `oai_settings` defaults (`default_settings` O:411-518; `oai_settings = structuredClone(default_settings)` O:520)

| key | default | cite |
|---|---|---|
| preset_settings_openai | `'Default'` | O:412 |
| temp_openai / freq_pen_openai / pres_pen_openai | 1.0 / 0 / 0 | O:413-415 |
| top_p_openai / top_k_openai / min_p_openai / top_a_openai | 1.0 / 0 / 0 / 0 | O:416-419 |
| repetition_penalty_openai | 1 | O:420 |
| stream_openai | false | O:421 |
| openai_max_context / openai_max_tokens | 4095 (`max_4k`) / 300 | O:422-423 |
| prompts / prompt_order | `chatCompletionDefaultPrompts.prompts` / `[]` | O:424-425, PM:2001-2085 |
| send_if_empty | `''` | O:426 |
| impersonation_prompt | `[Write your next reply from the point of view of {{user}}, …Don't describe actions of {{char}}.]` | O:105, 427 |
| new_chat_prompt / new_group_chat_prompt | `[Start a new Chat]` / `[Start a new group chat. Group members: {{group}}]` | O:108-109 |
| new_example_chat_prompt / continue_nudge_prompt | `[Example Chat]` / `[Continue your last message without repeating its original content.]` | O:110-111 |
| bias_preset_selected / bias_presets | `'Default (none)'` / `{Default (none):[], 'Anti-bond':[{id,text:' bond',value:-50},…]}` | O:112, 116-124 |
| wi_format / scenario_format / personality_format | `'{0}'` / `'{{scenario}}'` / `'{{personality}}'` | O:107, 113-114 |
| group_nudge_prompt | `[Write the next reply only as {{char}}.]` | O:115 |
| sort_models / group_models | `'alphabetically'` / false | O:438-439 |
| *_model / *_endpoint | see the 2.A.1 table | O:440-478 |
| openrouter_model / use_fallback / providers / quantizations / allow_fallbacks / middleout | `OR_Website` / false / [] / [] / true / `'on'` (`auto\|on\|off`, O:233) | O:479-484 |
| tool_reasoning_mode | `'disabled'` (`disabled\|since_last_user\|active_chain`, O:255-259; only openrouter and custom use it, O:262-265) | O:485 |
| reverse_proxy / proxy_password | `''` | O:486, 490 |
| chat_completion_source | `'openai'` | O:487 |
| max_context_unlocked | false (unlocked max is 2,000,000, O:139) | O:488 |
| show_external_models | false | O:489 |
| assistant_prefill / assistant_impersonation | `''` | O:491-492 |
| use_sysprompt | false (used by Claude and Google) | O:493 |
| vertexai_auth_mode / region / express_project_id | `'express'` / `'us-central1'` / `''` | O:494-496 |
| squash_system_messages | false | O:497 |
| media_inlining (was `image_inlining`, migrated at O:4285-4288) | true | O:498 |
| inline_image_quality | `'auto'` (`low\|auto\|high`) | O:499 |
| bypass_status_check | false | O:500 |
| continue_prefill | false | O:501 |
| function_calling / tool_call_recurse_limit | false / 5 | O:502-503 |
| names_behavior | 0 = DEFAULT (`NONE:-1, DEFAULT:0, COMPLETION:1, CONTENT:2`, O:206-211) | O:504 |
| continue_postfix | `' '` (`''\|' '\|'\n'\|'\n\n'`, O:213-218) | O:505 |
| custom_prompt_post_processing | `''` | O:506 |
| show_thoughts | true | O:507 |
| reasoning_effort / verbosity | `'auto'` / `'auto'` | O:508-509 |
| enable_web_search / request_images | false / false | O:510-511 |
| request_image_aspect_ratio / request_image_resolution | `''` | O:512-513 |
| seed / n | -1 / 1 | O:514-515 |
| bind_preset_to_connection | true | O:516 |
| extensions | `{}` | O:517 |

- `wrap_in_quotes` no longer exists in this build.
- Preset↔settings key map: `settingsToUpdate` (O:305-407), shaped `presetKey → [selector, settingKey, isCheckbox, isConnection]`. Preset keys drop the `_openai` suffix (e.g. `temperature` → `temp_openai`, O:307).
- `sensitiveFields` are confirmed on import and export (O:287-299): `reverse_proxy, proxy_password, custom_url, custom_include_body, custom_exclude_body, custom_include_headers, vertexai_region, vertexai_express_project_id, azure_base_url, azure_deployment_name, workers_ai_account_id`.
- Legacy settings migration (`migrateChatCompletionSettings`, O:4274-4318):
  - `names_in_completion` → `names_behavior=1`
  - `palm` → `makersuite`
  - `claude` post-processing → `merge`
  - `claude_use_sysprompt` / `use_makersuite_sysprompt` → `use_sysprompt`
  - `openrouter_sort_models` → `sort_models`
  - assorted model renames

#### 2.A.4 PromptManager data model

**Constants:** `INJECTION_POSITION = {RELATIVE:0, ABSOLUTE:1}` (PM:37-40); `DEFAULT_DEPTH=4`, `DEFAULT_ORDER=100` (PM:31-32).

**`Prompt` class** (PM:80-199). Constructor sets:
- `identifier, role, content, name, system_prompt, position`
- `injection_depth, injection_position`
- `forbid_overrides`
- `extension` (default false)
- `injection_order` (default 100)
- `injection_trigger` (default `[]`)

It also declares `enabled` and `marker`.

**Prompt edit form** (`updatePromptWithPromptEditForm`, PM:902-920) writes `name, role, content, injection_position, injection_depth, injection_order, injection_trigger[], forbid_overrides`.
- Role options: `system | user | assistant`.
- Trigger options: `normal, continue, impersonate, swipe, regenerate, quiet` (`index.html:7322-7327`).
- An empty trigger list means the prompt fires on every type (`shouldTrigger`, PM:1549-1553).

**`PromptCollection`** (PM:201-298): `collection[]`, `overriddenPrompts[]`, `add, set(prompt,pos), get(id), index(id), has(id), override(prompt,pos)`. `override` also records the id in `overriddenPrompts`.

**Default prompts** (`chatCompletionDefaultPrompts`, PM:2001-2081):

| identifier | name | system_prompt | marker | default content |
|---|---|---|---|---|
| main | Main Prompt | true | – | `Write {{char}}'s next reply in a fictional chat between {{charIfNotGroup}} and {{user}}.` (role system) |
| nsfw | Auxiliary Prompt | true | – | `''` |
| dialogueExamples | Chat Examples | true | true | – |
| jailbreak | Post-History Instructions | true | – | `''` |
| chatHistory | Chat History | true | true | – |
| worldInfoAfter | World Info (after) | true | true | – |
| worldInfoBefore | World Info (before) | true | true | – |
| enhanceDefinitions | Enhance Definitions | true | false | `If you have more knowledge of {{char}}, add to the character's lore…` |
| charDescription / charPersonality / scenario / personaDescription | … | true | true | – |

**Default order** (`promptManagerDefaultPromptOrder`, PM:2087-2136): `main, worldInfoBefore, personaDescription, charDescription, charPersonality, scenario, enhanceDefinitions(disabled), nsfw, worldInfoAfter, dialogueExamples, chatHistory, jailbreak`. Every entry except enhanceDefinitions is enabled.

**Ordering strategy**
- `settings.prompt_order` is an array of `{character_id, order:[{identifier, enabled}]}` (PM:1207-1240).
- Chat Completion uses `strategy:'global'` with `dummyId:100001` (O:695-699). The class default is 100000 (PM:333-336).
- Under the global strategy, `activeCharacter = {id:100001}` regardless of the selected character or group (PM:432-439, 1130-1176). So there is one order shared by everyone.
- The `'character'` strategy (per-character orders, default order seeded per character) exists but isn't used for Chat Completion (PM:1133-1140, 1166-1173).
- `sanitizeServiceSettings` (PM:1006-1041):
  - seeds the default order for the dummy id
  - restores missing default prompts (`checkForMissingPrompts`, PM:1043-1062)
  - assigns UUIDs to prompts without an identifier
  - prunes order references to prompts that no longer exist

**Permissions**
- Delete is allowed only when `system_prompt===false` (PM:1073-1075).
- Edit is allowed for non-markers, plus these markers: charDescription, charPersonality, scenario, personaDescription, worldInfoBefore, worldInfoAfter (PM:1082-1092).
- Toggle is allowed for non-markers, plus those same markers and main, chatHistory, dialogueExamples (PM:1099-1113).
- `overridablePrompts = ['main','jailbreak']`; only these two show `forbid_overrides` (PM:320-323, 584).
- Reset restores main, nsfw, jailbreak and enhanceDefinitions defaults (PM:520-545).

**`getPromptCollection(type)`** (PM:1516-1547):
- Walks the active order and adds each enabled prompt whose `injection_trigger` allows the generation type, after `preparePrompt` (macro substitution).
- `main` is always present; when disabled or not triggered it is added with empty content so relative inserts still have an anchor (PM:1531-1537).

**`preparePrompt(prompt, original)`** (PM:1277-1290): `substituteParams(content, {original, groupOverride: group member names})`.

**Names and token counts**
- `isValidName` uses `/^[a-zA-Z0-9_]{1,64}$/` (PM:1343-1347).
- `sanitizeName` replaces invalid characters with `_` and truncates to 64 (PM:1349-1351).
- `setChatCompletion` / `populateTokenCounts` record per-identifier token counts for the UI (PM:1569-1594).

**Export / import**
- Export file format: `{version:1, type:'full'|'character', data:{prompts, prompt_order}}` (PM:1793-1811).
- Full export (`st-prompts-<date>.json`) contains user prompts only (`system_prompt===false && marker===false`) plus the global order (PM:633-654).
- Character export contains that character's user prompts and order (PM:657-672).
- Import (PM:1819-1875):
  - validates the shape against the control object
  - merges by identifier, with imported prompts winning
  - global strategy: `Object.assign`s the order into the dummy order
- Legacy migration moves `main_prompt` / `nsfw_prompt` / `jailbreak_prompt` into `prompts[]` (PM:45-78).

#### 2.A.5 Message / MessageCollection / ChatCompletion / TokenHandler

**`TokenHandler`** (O:3420-3477)
- Counters: `start_chat, prompt, bias, nudge, jailbreak, impersonate, examples, conversation`.
- `countAsync` uses `countTokensOpenAIAsync`, which POSTs `/api/tokenizers/openai/count?model=` with a per-message hash cache. It subtracts 2 unless `full` or Claude (`tokenizers.js:846-886`).
- A single instance is shared: `tokenHandler = new TokenHandler(countTokensOpenAIAsync)` (O:3482).

**`Message`** (O:3511-3801)
- Fields: `role, content (string | parts[]), identifier, name, tool_calls, signature, reasoning, tokens`.
- A missing role defaults to `system` (O:3542-3545).
- `createAsync` counts tokens for string content (O:3558-3566).
- `setName` re-counts with the name included (O:3599-3602).
- `setToolCalls(invocations, includeSignature, includeReasoning)` (O:3575-3593):
  - builds `tool_calls=[{id,type:'function',function:{arguments,name}, signature?}]`
  - sets `reasoning` from the first invocation that has reasoning
- Media parts are added as follows:

| method | content part | token cost | cite |
|---|---|---|---|
| `addImage` | `{type:'image_url', image_url:{url:dataURL, detail:inline_image_quality}}`. A non-data URL is fetched and base64-encoded first. | `low` = 85; `auto` with ≤512×512 = 85; otherwise the OpenAI tile formula `170·tiles + 85` (O:3753-3785) | O:3624-3651 |
| `addVideo` | `{type:'video_url', video_url:{url, detail}}`. No compression. | 263 × seconds (default 40 s) | O:3658-3686 |
| `addAudio` | `{type:'audio_url', audio_url:{url}}` | 32 × seconds (default 300 s) | O:3693-3720 |

  - `compressImage`: for OpenRouter, MakerSuite, Mistral and Vertex, an image over 2 MB is thumbnailed to 2048 px max. Any other MIME type than jpeg/png/webp is re-encoded (O:3727-3746).

**`MessageCollection`** (O:3808-3906)
- `getChat()` emits `{role, content, name?, tool_calls?, tool_call_id (=identifier when role==='tool'), signature?, reasoning?}`, skipping messages with neither content nor tool_calls (O:3832-3847).
- Other methods: `add`, `getTokens` (sum), `flatten`.

**`ChatCompletion`** (O:3917-4268)

| method | behavior | cite |
|---|---|---|
| `setTokenBudget(ctx, resp)` | `tokenBudget = openai_max_context - openai_max_tokens` | O:3982-3990 |
| `add(collection, pos)` | Throws `TokenBudgetExceededError` if unaffordable. Places the collection at array index `pos` (the prompt's index in the PromptCollection, so the array can be sparse), or pushes when `pos` is null or -1. Decrements the budget. | O:3998-4013 |
| `insert` / `insertAtStart` / `insertAtEnd(msg, collectionId)` | Budget-checked unshift / push / splice into the named sub-collection; skipped when content and tool_calls are both empty | O:4021-4056 |
| `removeLastFrom` | Pops and refunds | O:4063-4075 |
| `canAfford` / `canAffordAll` | `tokenBudget - tokens >= 0` | O:4083-4095 |
| `reserveBudget(msg\|number)` / `freeBudget` | Decrement / increment | O:4210-4220 |
| `getChat()` | Flattens top-level items in array order. Holes and empty messages are skipped. | O:4120-4142 |
| `squashSystemMessages()` | Flattens, drops empty system messages, and joins consecutive **unnamed** system messages with `\n`, except ids `newMainChat`, `newChat`, `groupNudge` | O:3922-3954 |
| `setOverriddenPrompts` / `getOverriddenPrompts` | Records which prompts were character-overridden, for the UI | O:4261-4267 |

Error classes: `IdentifierNotFoundError`, `TokenBudgetExceededError`, `InvalidCharacterNameError` (O:3485-3507).

#### 2.A.6 End-to-end prompt build

1. **`Generate()` in S**
   - `oaiMessages = setOpenAIMessages(coreChat)` and `oaiMessageExamples = setOpenAIMessageExamples(mesExamplesArray)` (S:4830-4835).
   - `coreChat` drops system messages, except tool-invocation messages when tools are usable (S:4494-4500).
   - Continue: `cyclePrompt = chat2.shift()` (S:4866-4867), then `continue_postfix` is appended if the text doesn't already end in a space (S:4975-4980).
   - Then `prepareOpenAIMessages({..., systemPromptOverride: system, jailbreakPromptOverride: jailbreak, messages, messageExamples}, dryRun)` (S:5285-5300).
   - The character overrides come from `data.system_prompt` and `post_history_instructions`, gated by `power_user.prefer_character_prompt` / `prefer_character_jailbreak` (S:3416-3420).

2. **`setOpenAIMessages(chat)`** (O:570-649) builds the array newest-first: `messages[0]` is the newest message.
   - Messages with `extra[IGNORE_SYMBOL]` are skipped.
   - Role: `is_user` → user, otherwise assistant; `extra.type===NARRATOR` → system.
   - **Names** by `names_behavior`:
     - NONE: no change.
     - DEFAULT: content prefixed `Name: ` in groups (non-user speakers) or when `force_avatar` is set (non-narrator).
     - CONTENT: always prefixed except narrator.
     - COMPLETION: handled later via the `name` field.
   - `\r` is stripped.
   - Each entry carries `media, mediaDisplay, mediaIndex, invocations (extra.tool_invocations), signature, reasoning`.
   - Signature and reasoning are kept only if `extra.api` and `extra.model` match the current source and model, and the message isn't from another group member. Mismatched invocations lose signature and reasoning.

3. **`setOpenAIMessageExamples`** (O:656-668)
   - `<START>` → `{Example Dialogue:}`.
   - `parseExampleIntoIndividual` (O:729-787) splits on the `{{user}}:` / `{{char}}:` / group-name prefixes. Each line becomes `{role:'system', name:'example_user'|'example_assistant', content}` with the speaker prefix stripped (re-added in groups).

4. **`prepareOpenAIMessages`** (O:1542-1633)
   - With a dry run and no active character, it returns `[null,false]`.
   - Creates a `ChatCompletion` and calls `setTokenBudget(max_context, max_tokens)`.
   - Runs `preparePromptsForChatCompletion`, then `populateChatCompletion`.
   - Errors: budget exceeded → toast "Mandatory prompts exceed the context size."; invalid name → warning (O:1592-1608).
   - Finally:
     - `promptManager.setChatCompletion`
     - `squashSystemMessages` if `squash_system_messages && dryRun===false`
     - render
     - `getChat()`
     - emit `CHAT_COMPLETION_PROMPT_READY {chat, dryRun}` (a mutable hook)
     - set `openai_messages_count`
     - return `[chat, tokenHandler.counts]`

5. **`preparePromptsForChatCompletion`** (O:1367-1516)
   - Builds the system prompt list: `worldInfoBefore` / `worldInfoAfter` (through `formatWorldInfo` using `wi_format`, O:789-801), `charDescription`, `charPersonality` (`personality_format`), `scenario` (`scenario_format`), `impersonate`, `quietPrompt`, `groupNudge`, and `bias` with **role assistant** (O:1374-1385).
   - Known extension prompts are added with `position = 'start'` (BEFORE_PROMPT=2) or `'end'` (IN_PROMPT=0), or `false` otherwise (`getPromptPosition`, O:1140-1150; enum S:484-489). Roles via `getPromptRole` (O:1157-1170):
     - `1_memory` → `summary`
     - `2_floating_prompt` → `authorsNote`
     - `3_vectors` → `vectorsMemory`
     - `4_vectors_data_bank` → `vectorsDataBank`
     - `chromadb` → `smartContext`
     (O:1388-1430)
   - `personaDescription` is added only if `persona_description_position === IN_PROMPT` (O:1433-1435).
   - Any other extension prompt at BEFORE/IN_PROMPT (passing its optional `filter()`) is added with `extension:true` and `identifier = key.replace(/\W/g,'_')` (O:1437-1467).
   - For each system prompt: role, `injection_position`, `injection_depth` and `injection_order` are copied from the matching PromptManager entry. The prompt then replaces the marker at its index, or is appended (O:1470-1493).
   - **Character overrides**: if an override exists, the prompt exists, `forbid_overrides!==true`, and the prompt is enabled, content becomes the override, and `{{original}}` expands to the preset text via `preparePrompt(prompt, original)`. Applies to `main` and to `jailbreak` (Post-History Instructions) (O:1496-1513).

6. **`populateChatCompletion`** (O:1185-1347)
   - `addToChatCompletion(id)` (O:1187-1207) skips:
     - ids missing from the collection
     - ids disabled for the active order (except `main`)
     - ABSOLUTE prompts
     Otherwise it wraps the prompt in `MessageCollection(id)` and places it at `prompts.index(id)`.
   - Order of operations:
     1. Reserve 3 tokens for assistant priming (O:1210).
     2. Add `worldInfoBefore, main, worldInfoAfter, charDescription, charPersonality, scenario, personaDescription` (O:1212-1218).
     3. `controlPrompts`: the impersonate prompt (type impersonate only), then the quiet prompt (always last; gets `quietImage` if image inlining is supported). Their budget is reserved (O:1222-1238).
     4. Add `nsfw`, `jailbreak`, then every relative non-system prompt (custom user prompts) (O:1241-1257). Then `enhanceDefinitions` (O:1260) and `bias` if non-empty (O:1263).
     5. Relative extension prompts (summary, authorsNote, vectorsMemory, vectorsDataBank, smartContext, then any `extension && position`) are passed to `injectToMain`. When `main` is present they are inserted at the start or end of the `main` collection. When `main` is absent (or absolute), they are copied into the absolute list next to `main`, inheriting its role, depth and order (O:1265-1307).
     6. If tools can be used, the tool JSON is serialized and its token count reserved (O:1309-1316).
     7. Continue with `continue_prefill`: the newest message is shifted out. On Claude, an assistant message gets `assistant_prefill + '\n\n' + content`. The result is added to `controlPrompts` as `continuePrefill` (name set when names are in completion) (O:1319-1331).
     8. `messages = populationInjectionPrompts(absolutePrompts, messages)` (O:1334).
     9. If `power_user.pin_examples`, examples are added before history; otherwise history first, then examples get the remaining budget (O:1337-1343).
     10. `controlPrompts` is freed and appended last (O:1345-1346).

7. **`populationInjectionPrompts`** (O:810-876). This handles in-chat / absolute prompts.
   - Depth loop runs i = 0…`MAX_INJECTION_DEPTH` (10000, S:500, 3281-3289).
   - At each depth, prompts with that `injection_depth` and non-empty content are grouped by `injection_order` (default 100). Groups are processed in **descending** order.
   - Within a group, roles go `system → user → assistant`. Prompts of the same role are joined with `\n`.
   - The order-100 group also merges `getExtensionPrompt(IN_CHAT, depth, '\n', role)`, which covers Author's Note in-chat, WI at depth, extension depth prompts, etc.
   - Resulting messages `{role, content, injected:true}` are spliced into the newest-first array at index `depth + alreadyInserted`, then the array is reversed to chronological order.
   - Net effect: depth 0 = after the last message. At the same depth, higher order lands lower, and system lands below user, which lands below assistant.

8. **`populateChatHistory`** (O:885-1092)
   - Adds an empty `chatHistory` collection at its marker index.
   - Reserves `newMainChat` (`new_group_chat_prompt` in groups, otherwise `new_chat_prompt`) (O:892-895).
   - Reserves `groupNudge` (groups only, not for impersonate) (O:897-903).
   - Continue without prefill: the last non-injected message is removed and placed in a `continueNudge` collection, followed by the system `continue_nudge_prompt` with `{{lastChatMessage}}` = cyclePrompt. Budget reserved (O:905-926).
   - `send_if_empty`: if the last message is an assistant message, insert a user `emptyUserMessageReplacement` (O:928-932).
   - Then the history loop runs **newest to oldest** (O:947-1076). For each message:
     - `Prompt` → `preparePrompt`, with identifier `chatHistory-N`.
     - COMPLETION names: `setName(valid ? name : sanitizeName(name))`.
     - Media inlining: LIST display inlines all attachments; GALLERY inlines only `media[mediaIndex]`; each type is gated by `isImage/Video/AudioInliningSupported`.
     - Messages with tool invocations become an assistant `toolCall-*` message (`setToolCalls`) plus one `role:'tool'` message per result (`tool_call_id` = invocation id, content = result or `[No content]`). All of them are inserted together if affordable, otherwise the loop stops. With interleaved reasoning (OpenRouter/Custom, `tool_reasoning_mode` ≠ disabled and `show_thoughts`), reasoning after the last user message is attached (O:996-1064).
     - Google / OpenRouter-Gemini: `signature` is attached (`isReasoningSignatureSupported`, O:6476-6482).
     - `insertAtStart` if affordable, **else break**. The oldest messages are the ones dropped.
   - Afterwards: free and insert `newMainChat` at the start, the `groupNudge` at the end, and the continue collection appended last (O:1078-1091).

9. **`populateDialogueExamples`** (O:1101-1132)
   - Each example block becomes a `newChat` system message (`new_example_chat_prompt`), followed by system messages named `example_user` / `example_assistant`.
   - Whole blocks are added in order while `canAffordAll`; otherwise it stops.

10. **Message shape sent**: `{role:'system'|'user'|'assistant'|'tool', content:string|[{type:'text',text}|image_url|video_url|audio_url], name?, tool_calls?, tool_call_id?, signature?, reasoning?}` (O:4120-4142). Example messages carry `name:'example_user'|'example_assistant'`; backends that don't support names prefix the text instead (PC:853-867, 197-372).

**Media capability checks** (all require `main_api==='openai' && media_inlining`)
- `isImageInliningSupported` (O:6226-6362):
  - matches a model-substring allowlist (O:6236-6301), or
  - reads model-list metadata: OpenRouter `architecture.input_modalities`, Mistral `capabilities.vision`, AIMLAPI `features`, Chutes / Pollinations `input_modalities`, ElectronHub `metadata.vision`, Moonshot `supports_image_in`, NanoGPT `capabilities.vision`, Workers AI `properties`, Fireworks `supports_image_input`.
  - Custom and CometAPI always return true.
- `isVideoInliningSupported`: Gemini, Gemma-4, GLM-V on MakerSuite / Vertex / ZAI, plus OpenRouter modality (O:6366-6402).
- `isAudioInliningSupported`: Gemini plus `gpt-4o-audio`/`realtime`/`gpt-audio`; OpenRouter modality; Custom always (O:6406-6443).

#### 2.A.7 Request, streaming and parsing

**`sendOpenAIRequest(type, messages, signal, {jsonSchema})`** (O:3138-3205)
1. `createGenerationParameters`.
2. Emit `CHAT_COMPLETION_SETTINGS_READY` (a mutable hook).
3. POST `/api/backends/chat-completions/generate`. If the response is not OK, `tryParseStreamingError` runs and an Error is thrown.

When streaming, the response is piped through the SSE parser and the function returns an async generator (O:3160-3188):
- Ends on `[DONE]`.
- Each chunk goes through `tryParseStreamingError` (O:1648-1680), which toasts `error`, `message` or `detail` and also checks quota and moderation (O:1689-1720).
- Chunks with `choices[0].index>0` (multi-swipe) are accumulated into `swipes[index-1]`; everything else into `text` via `getStreamingReply`.
- `ToolManager.parseToolCalls(toolCalls, parsed, state.toolSignatures)` runs on each chunk.
- Each step yields `{text, swipes, logprobs, toolCalls, state:{reasoning, images, signature, toolSignatures}}`.

When not streaming (O:3189-3204), the JSON goes through `checkQuotaError` / `checkModerationError`. Any `data.error` is toasted and thrown. Logprobs are saved for every type except quiet.

The final text is extracted by `extractMessageFromData` for openai, which tries in order: Claude `content[].text` → `choices[0].message.content` → `choices[0].text` → `text` → Cohere `message.content[0].text` → `message.tool_plan` (S:6291-6292).

**`getStreamingReply(data, state, {chatCompletionSource, overrideShowThoughts})`** (O:3222-3311)

| source(s) | text delta | reasoning delta (only if show_thoughts) | extra |
|---|---|---|---|
| claude | `delta.text` | `delta.thinking` | raw Anthropic SSE |
| makersuite, vertexai | `candidates[0].content.parts` where `!thought`: first `.text` | parts with `thought`: `.text` | `inlineData` → `state.images` (data URLs); `thoughtSignature` → `state.signature` |
| cohere | `delta.message.content.text` or `delta.message.tool_plan` | – | |
| deepseek, xai | `choices[0].delta.content` | `delta.reasoning_content` | |
| openrouter | `delta.content ?? message.content ?? text` | `delta.reasoning ?? delta.reasoning_content ?? message.reasoning ?? message.reasoning_content` | `delta.images[].image_url.url` → images; `reasoning_details[type=reasoning.encrypted]` → `toolSignatures[id]` / `signature` |
| custom, pollinations, aimlapi, moonshot, cometapi, electronhub, nanogpt, zai, siliconflow, chutes, workers_ai, fireworks | `delta.content ?? message.content ?? text` | `delta.reasoning_content ?? delta.reasoning` | |
| mistralai | `delta.content` (array → text parts joined) | `delta.content[0].thinking[0].text` | |
| others (openai, azure, groq, perplexity, ai21) | `delta.content ?? message.content ?? text` | – | |

After the stream ends: generated images go through `processImageAttachment`, and `state.signature` is saved as `extra.reasoning_signature` (S:3783-3792).

**Logprobs** (`parseChatCompletionLogprobs`, O:3314-3345)
- Sources: openai, azure, openrouter, deepseek, xai, custom, chutes; aimlapi handles both chat and text shapes.
- Chat shape: `content[{token, logprob, top_logprobs}]` → `{token, topLogprobs:[[tok,lp],…]}`, with the chosen token appended if it is missing (O:3352-3374).
- Text shape: `tokens / token_logprobs / top_logprobs` (O:3381-3399).
- Backend: OpenAI and Custom convert the numeric `logprobs` into `{logprobs:true, top_logprobs:N}` (BE:2291-2294, 2404-2407).

**Status and model list**
- `getStatusOpen` (O:4459-4575) POSTs `/status` with `{reverse_proxy, proxy_password, chat_completion_source}` plus source extras: `custom_url` + `custom_include_headers`, Azure triple, `siliconflow_endpoint`, `minimax_endpoint`, `workers_ai_account_id`, `pollinations_endpoint`.
- An invalid `custom_url` or Azure URL short-circuits with a status message.
- A response `data[]` goes to `saveModelList`, which fills `model_list` and the selectors (O:2025-2410):
  - OpenRouter: sorted / grouped by vendor, plus the "Use OpenRouter website setting" option.
  - OpenAI: `#openai_external_category`, shown when `show_external_models`; if the current model isn't in the list it resets to the default.
  - Custom: `.model_custom_select` with a "None" option; auto-picks the first model.
- "Test Message" sends `sendOpenAIRequest('quiet', [{role:'user', content:'Hi'}])` (O:6172-6186).

**Logit bias presets**
- Entry format: `{id:uuid, text, value}`.
- Request: `calculateLogitBias` POSTs the selected preset array to `/api/backends/chat-completions/bias?model=<getTokenizerModel()>` (O:3401-3418).
- Import accepts a JSON array; entries without `text`/`value` are dropped and missing ids get a UUID (O:4893-4933).
- Export writes `<name>.json` with the array (O:4935-4943).
- Changing the preset invalidates `biasCache` (`onLogitBiasPresetChange`, O:4634).

**Presets**
- Body: `getChatCompletionPreset` maps `settingsToUpdate` preset keys to their settings values (O:4582-4589).
- Save: `saveOpenAIPreset` POSTs `/api/presets/save` with `{apiId:'openai', name, preset}` (O:4598-4632).
- Import (O:4766-4847):
  - offers to strip sensitive fields (Remove / Import as-is / Cancel)
  - confirms overwrite
  - emits `OAI_PRESET_IMPORT_READY`
  - saves via `/api/presets/save`
- Export (O:4849-4891):
  - offers to strip sensitive fields
  - "export connection data?" — if declined, deletes every `isConnection` key
  - emits `OAI_PRESET_EXPORT_READY`
  - downloads `<preset>.json`
- Delete: POST `/api/presets/delete` with `{apiId:'openai', name}` (O:4945-4979).
- Apply (`onSettingsPresetChange`, O:5020-5084):
  - migrate the preset
  - emit `OAI_PRESET_CHANGED_BEFORE`
  - copy each defined key (connection keys only when `bind_preset_to_connection`; `extensions` copied raw)
  - emit `OAI_PRESET_CHANGED_AFTER` and `PRESET_CHANGED`
- Max context per model: `getMaxContextOpenAI` and the per-provider helpers (O:5088-5470).

---

> **Text-completion family (§2.B–2.K) path conventions:**

Paths are relative to the SillyTavern 1.19.0 source root (commit 06bde93). `TG` = `public/scripts/textgen-settings.js`, `SJ` = `public/script.js`, `PU` = `public/scripts/power-user.js`, `IM` = `public/scripts/instruct-mode.js`, `RS` = `public/scripts/reasoning.js`. Server files (`src/...`) are cited where the Node proxy does work that an Android port has to reproduce on the device.

---

### 2.B Text Completion (textgen-settings.js)

#### 2.B.1 `textgen_types` enum (TG:31-47) and per-type server/model handling

| type id | Server URL source | Model setting key | Status-time model loader | Notes |
|---|---|---|---|---|
| `ooba` | `server_urls.ooba` (input `#textgenerationwebui_api_url_text`, TG:132) | `custom_model` (TG:1468-1472) | none, `setOnlineStatus(data.result)` (TG:726) | `bypass_status_check` allowed (TG:674) |
| `mancer` | `MANCER_SERVER` = localStorage `mancer_server` ?? `https://neuro.mancer.tech` (TG:122-124); debug fn `change-mancer-url` (TG:602-609) | `mancer_model` (default `mytholite`) | `loadMancerModels` (TG:692) | URL not validated (TG:391) |
| `vllm` | `server_urls.vllm` (TG:133) | `vllm_model` | `loadVllmModels` (TG:710) | top_k uses -1 to mean off (TG:921-930, 1055-1058) |
| `aphrodite` | `server_urls.aphrodite` (TG:134) | `aphrodite_model` | `loadAphroditeModels` (TG:713) | same top_k -1; mirostat step 2 (TG:922) |
| `tabby` | `server_urls.tabby` (TG:135) | `tabby_model` | `loadTabbyModels` (TG:719) | json_schema supported (TG:1519-1521) |
| `koboldcpp` | `server_urls.koboldcpp` (TG:136) | none (undefined) | none | `/props` chat-template derivation (TG:747) |
| `togetherai` | const `https://api.together.xyz` (TG:125) | `togetherai_model` (default `Gryphe/MythoMax-L2-13b`) | `loadTogetherAIModels` (TG:695) | |
| `llamacpp` | `server_urls.llamacpp` (TG:137) | `llamacpp_model` | `loadLlamaCppModels` (TG:722) | `/props?model=` derivation (TG:749-751) |
| `ollama` | `server_urls.ollama` (TG:138) | `ollama_model` (throws if empty, TG:1490-1495) | `loadOllamaModels` (TG:698) | model download via `/ollama/download` (textgen-models.js:1212-1250) |
| `infermaticai` | const `https://api.totalgpt.ai` (TG:126) | `infermaticai_model` | `loadInfermaticAIModels` (TG:701) | top_k -1 |
| `dreamgen` | const `https://dreamgen.com` (TG:127) | `dreamgen_model` (default `lucid-v1-extra-large/text`) | `loadDreamGenModels` (TG:704) | |
| `openrouter` | const `https://openrouter.ai/api` (TG:128) | `openrouter_model` (default `openrouter/auto`) | `loadOpenRouterModels` (TG:707) | provider and quantization routing (TG:1752-1756) |
| `featherless` | const `https://api.featherless.ai/v1` (TG:129) | `featherless_model` | `loadFeatherlessModels` (TG:716) | |
| `huggingface` | `server_urls.huggingface` (TG:139) | literal `'tgi'` (TG:1498-1499) | none | |
| `generic` | `server_urls.generic` (TG:140) | `generic_model` (TG:1473-1477) | `loadGenericModels` (TG:722-724) | `bypass_status_check` allowed |

- `getTextGenServer(type)` (TG:351-369) returns the constants for the hosted types, otherwise `server_urls[type] ?? ''`.
- `formatTextGenURL` (TG:389-402) validates with `new URL()`. It skips validation for mancer, togetherai, infermaticai, dreamgen and openrouter.
- Legacy migration: `api_server_textgenerationwebui` is copied into every `server_urls[type]` (TG:557-563). `api_use_mancer_webui` sets type to `mancer` (TG:573-575).
- API keys come from secret IDs `api_key_<type>` (OpenRouter's is `api_key_openrouter-tg`) and are saved with `writeSecret` before each connect (TG:1094-1123).
- Server auth headers: Mancer sends `X-API-KEY` plus Bearer (src/additional-headers.js:11-18). Tabby sends `x-api-key` plus Bearer (:108-116). The others send Bearer only (e.g. Ooba :138-144). Header dispatch is at :230-235.

#### 2.B.2 Settings object `textgenerationwebui_settings` with defaults (TG:144-237)

| key | default | key | default |
|---|---|---|---|
| temp | 0.7 | temperature_last | true |
| top_p | 0.5 | top_k | 40 |
| top_a | 0 | tfs | 1 |
| epsilon_cutoff / eta_cutoff | 0 / 0 | typical_p | 1 |
| min_p | 0 | rep_pen | 1.2 |
| rep_pen_range | 0 | rep_pen_decay | 0 |
| rep_pen_slope | 1 | no_repeat_ngram_size | 0 |
| penalty_alpha | 0 | num_beams | 1 |
| length_penalty | 1 | min_length | 0 |
| encoder_rep_pen | 1 | freq_pen / presence_pen | 0 / 0 |
| skew | 0 | do_sample | true |
| early_stopping | false | dynatemp | false |
| min_temp / max_temp | 0 / 2.0 | dynatemp_exponent | 1.0 |
| smoothing_factor / smoothing_curve | 0.0 / 1.0 | dry_allowed_length | 2 |
| dry_multiplier | 0.0 | dry_base | 1.75 |
| dry_sequence_breakers | `'["\\n", ":", "\\"", "*"]'` (JSON string) | dry_penalty_last_n | 0 |
| max_tokens_second | 0 | seed | -1 |
| preset | 'Default' | add_bos_token | true |
| stopping_strings | [] | ban_eos_token | false |
| skip_special_tokens | true | include_reasoning | true |
| streaming | false | mirostat_mode / tau / eta | 0 / 5 / 0.1 |
| guidance_scale | 1 | negative_prompt | '' |
| grammar_string | '' | json_schema | null |
| json_schema_allow_empty | false | banned_tokens / global_banned_tokens | '' / '' |
| send_banned_tokens | true | sampler_priority | OOBA_DEFAULT_ORDER |
| samplers | LLAMACPP_DEFAULT_ORDER | samplers_priorities | APHRODITE_DEFAULT_ORDER |
| ignore_eos_token | false | spaces_between_special_tokens | true |
| speculative_ngram | false | type | 'ooba' |
| sampler_order | KOBOLDCPP_ORDER `[6,0,1,3,4,2,5]` | logit_bias | [] |
| n | 1 | server_urls | {} |
| custom_model | '' | bypass_status_check | false |
| openrouter_allow_fallbacks | true | openrouter_providers / openrouter_quantizations | [] / [] |
| xtc_threshold / xtc_probability | 0.1 / 0 | nsigma | 0.0 |
| min_keep | 0 | adaptive_target / adaptive_decay | -0.01 / 0.9 |
| `*_model` per type | see 2.B.1 | extensions | {} |

- **Preset-serialised keys** are `setting_names` (TG:248-323). This is every sampler key above except `type`, `server_urls`, `preset`, the per-type model keys and the OpenRouter provider fields.
- **Preset load:** `selectPreset` (TG:371-387) calls `setSettingByName` for each key, then `setGenerationParamsFromPreset` (max_length and genamt). It clears the bias cache and emits `PRESET_CHANGED` (TG:955-958).
- **Missing order entries:** `setSettingByName` back-fills missing sampler-order items with `insertMissingArrayItems` (TG:1186-1197, 1217-1241).
- **"Neutralize samplers" defaults:** these differ from the init defaults (temp 1, top_p 1, top_k 0 or -1, rep_pen 1, …) and are listed at TG:960-1018.

**Sampler order lists**

| list | where | values |
|---|---|---|
| `OOBA_DEFAULT_ORDER` | TG:79-101 | repetition_penalty, presence_penalty, frequency_penalty, dry, temperature, dynamic_temperature, quadratic_sampling, top_n_sigma, top_k, top_p, typical_p, epsilon_cutoff, eta_cutoff, tfs, top_a, min_p, adaptive_p, mirostat, xtc, encoder_repetition_penalty, no_repeat_ngram |
| `LLAMACPP_DEFAULT_ORDER` | TG:67-78 | penalties, dry, top_n_sigma, top_k, typ_p, top_p, min_p, xtc, temperature, adaptive_p |
| `APHRODITE_DEFAULT_ORDER` | TG:102-117 | dry, penalties, no_repeat_ngram, temperature, top_nsigma, top_p_top_k, top_a, min_p, tfs, eta_cutoff, epsilon_cutoff, typical_p, quadratic, xtc |
| `KOBOLDCPP_ORDER` | TG:143 | `[6,0,1,3,4,2,5]` (ids as in 2.C) |

These are reordered with drag-and-drop; the reset buttons are at TG:835-912.

**UI visibility vs. request body.** Each control's `data-tg-type` attribute (optionally with `data-tg-type-mode="except"`) decides whether it is shown (TG:1159-1180). Visibility does not filter the request: the body always contains every field. Selected visibility rules from `public/index.html`:
- `dynatemp` block: ooba, mancer, koboldcpp, tabby, llamacpp, aphrodite. This also gates whether dynatemp is sent (`isDynamicTemperatureSupported`, TG:1528-1531).
- CFG: ooba, tabby.
- grammar: ooba, aphrodite, tabby.
- json_schema: tabby, llamacpp, aphrodite.
- `n` (multi-swipe): mancer, vllm, aphrodite, tabby, infermaticai, llamacpp.
- top_k, typical_p, min_p, rep_pen and banned_tokens are hidden only for `generic`.

There is also a separate "manual sampler select" filter (TG:1131-1157, `isSamplerManualPriorityEnabled` / `getActiveManualApiSamplers`).

#### 2.B.3 Request body: `createTextGenGenerationData(settings, model, finalPrompt, maxTokens, isImpersonate, isContinue, cfgValues, type)` (TG:1587-1842)

`getTextGenGenerationData` wraps it and emits `TEXT_COMPLETION_SETTINGS_READY` with the params, which extensions may mutate (TG:1844-1849).

- `canMultiSwipe = !isContinue && !isImpersonate && type!=='quiet'` (TG:1591).
- `jsonSchema` is sent only if it is an object, and non-empty unless `json_schema_allow_empty` is set (TG:1594-1598).

**Base `params` (TG:1600-1676), sent to all types:**
- **Prompt, model and length:** `prompt`, `model`, `max_new_tokens` = `max_tokens` = maxTokens, `truncation_length` = max_context.
- **Core samplers:** `temperature` (dynatemp ? (min+max)/2 : temp), `top_p`, `typical_p`, `typical`, `min_p`, `top_k`, `skew`, `top_a`, `tfs`, `xtc_threshold`, `xtc_probability`, `nsigma` = `top_n_sigma`, `min_keep`, `adaptive_target`, `adaptive_decay`.
- **Penalties:** `repetition_penalty`, `frequency_penalty`, `presence_penalty`.
- **Seed:** `sampler_seed` (only if seed ≥ 0).
- **Logprobs:** `logprobs` = `getLogprobsNumber()`, which is 5 for vllm/infermaticai and 10 otherwise (TG:1538-1545), only if `power_user.request_token_probabilities`.
- **Minimum length:** `min_length` (ooba only), `minimum_message_content_tokens` (dreamgen only), `min_tokens`.
- **Beam search (ooba only):** `num_beams`, `length_penalty`, `early_stopping`.
- **Flags:** `add_bos_token`, `ban_eos_token`, `skip_special_tokens`, `include_reasoning`.
- **Dynatemp (only if dynatemp):** `dynamic_temperature`, `dynatemp_low`, `dynatemp_high`, `dynatemp_range` ((max-min)/2), `dynatemp_exponent`.
- **Smoothing:** `smoothing_factor`, `smoothing_curve`.
- **DRY:** `dry_allowed_length`, `dry_multiplier`, `dry_base`, `dry_sequence_breakers` (macros substituted by `replaceMacrosInList`, TG:1552-1575), `dry_penalty_last_n`.
- **ooba-only extras:** `max_tokens_second`, `sampler_priority` (ooba), `samplers` (llamacpp), `sampler_order` (koboldcpp).
- **Stop strings:** `stopping_strings` = `stop` = `getStoppingStrings(isImpersonate,isContinue)` (SJ:3025-3064).
- **Cutoffs:** `epsilon_cutoff` and `eta_cutoff` (ooba and mancer only).
- **Mirostat:** `mirostat_mode`, `mirostat_tau`, `mirostat_eta`.
- **Bans:** `custom_token_bans` (an int array for aphrodite/mancer, otherwise a CSV string), `banned_strings`.
- **Routing:** `api_type`, `api_server`.
- `parseSequenceBreakers()` is a helper method. It tries JSON.parse, falling back to a split on commas.

**Per-type merges:**

| Branch | Lines | Fields added or changed |
|---|---|---|
| `nonAphroditeParams` (default branch: every type except vllm/infermaticai/aphrodite) | TG:1677-1703 | `rep_pen`, `rep_pen_range`, `repetition_penalty_range`, `repetition_decay` (tabby), `encoder_repetition_penalty`, `no_repeat_ngram_size`, `penalty_alpha`, `do_sample` (ooba), `temperature_last` (ooba/aphrodite/tabby), `speculative_ngram` (tabby), `seed`, `guidance_scale` (= `cfgValues.guidanceScale.value ?? settings.guidance_scale ?? 1`), `negative_prompt` (= `cfgValues.negativePrompt ?? substituteParams(settings.negative_prompt)`), `grammar_string`, `json_schema` (tabby/llamacpp). llama.cpp/LM Studio/Ollama aliases: `repeat_penalty`, `repeat_last_n`, `n_predict`, `num_predict`, `num_ctx`, `mirostat`, `ignore_eos` (=ban_eos), `n_probs` (10), `rep_pen_slope` |
| `vllmParams` (vllm, infermaticai) | TG:1704-1709 | `n` (canMultiSwipe ? n : 1), `ignore_eos` (=ignore_eos_token), `spaces_between_special_tokens`, `seed` |
| `aphroditeParams` | TG:1710-1750 | `n`, penalties, `stop`, temperature, `temperature_last`, top_p/k/a, min_p, tfs, eta/epsilon (raw), typical_p, smoothing, `ignore_eos`, `min_tokens`, `skip_special_tokens`, `spaces_between_special_tokens`, `guided_grammar`, `guided_json`, `early_stopping:false`, `include_stop_str_in_output:false`, `dynatemp_min/max/exponent`, xtc, nsigma, `custom_token_bans` (int[]), `no_repeat_ngram_size`, `sampler_priority` (only if it differs from the default order) |
| openrouter | TG:1752-1756 | `provider` (array), `quantizations`, `allow_fallbacks` |
| koboldcpp | TG:1758-1763 | `grammar`, `grammar_retain_state` (continue + grammar), `trim_stop:true`, `dry_sequence_breakers` parsed to an array |
| huggingface | TG:1765-1769 | `top_p` clamped to [0, 0.999], `stop` truncated to 4, `seed` random u32 if seed < 0 |
| mancer | TG:1771-1781 | `n`; `epsilon_cutoff /= 1000` and `eta_cutoff /= 1000`; `dynatemp_mode` 0/1; `dynatemp_low/high` renamed to `dynatemp_min/max`; breakers become an array |
| tabby, llamacpp | TG:1783-1785 | `n` (multi-swipe) |
| any type with `logit_bias` | TG:1803-1807 | `params.logit_bias` = `{tokenId: bias}` (cached in `BIAS_CACHE['#textgenerationwebui_api-settings']`) |
| llamacpp, ollama | TG:1809-1828 | `logit_bias` becomes `[[id,bias],…]` plus `[id,false]` for each banned token; `grammar` = grammar_string; `cache_prompt:true`; `dry_sequence_breakers` as an array (deleted if empty) |
| llamacpp, aphrodite | TG:1830-1840 | json_schema wins over grammar: if a schema is present, delete `grammar_string`/`grammar`/`guided_grammar`; otherwise delete `json_schema`/`guided_json` |

**Server-side rewrite** (`/api/backends/text-completions/generate`, src/endpoints/backends/text-completions.js:272-436):
- **Host fix-up:** `localhost` is replaced with `127.0.0.1` (:276-278). On client abort, koboldcpp also receives `POST /api/extra/abort` (:284-289).
- **URL suffixes** (base URL has `/v1` trimmed first):

| Suffix | Types | Lines |
|---|---|---|
| `/v1/completions` | generic, vllm, featherless, aphrodite, ooba, tabby, koboldcpp, togetherai, infermaticai, huggingface | :296-308 |
| `/api/openai/v1/completions` | dreamgen | :309-311 |
| `/oai/v1/completions` | mancer | :312-314 |
| `/completion` | llamacpp | :315-317 |
| `/api/generate` | ollama | :318-320 |
| `/v1/chat/completions` | openrouter | :321-323 |

- **Body whitelists:** the body is filtered with `_.pickBy` for togetherai (`TOGETHERAI_KEYS`), infermaticai, featherless, generic (`OPENAI_KEYS`, plus `stop` sliced to 4), openrouter and vllm (src/constants.js:240-420; usage text-completions.js:336-383).
- **OpenRouter:** `provider` becomes `{allow_fallbacks, order, quantizations}` (:361-378).
- **Ollama:** the body is rebuilt as `{model, prompt, stream, keep_alive (config ollama.keepAlive), raw:true, options: pick(OLLAMA_KEYS)}`, plus `num_batch` from config (:385-399). Ollama's NDJSON stream is converted into SSE chunks `{choices:[{text, thinking}]}` (:29-52).
- **InfermaticAI (non-stream):** choices are remapped to `{text, logprobs, index}` (:416-419).
- **Status endpoints:** `/status` GETs `/v1/models`, `/api/openai/v1/models`, `/oai/v1/models`, `/v1/model/list` (tabby), `/api/models?&info` (together), `/api/tags` (ollama) or `/info` (HF) (:100-150). `/props` GETs `<base>/props[?model=]` and returns `chat_template_hash = sha256(chat_template)`; llama.cpp's trailing `\0` is fixed to `\n` (:234-269).

#### 2.B.4 Status flow `getStatusTextgen` (TG:659-811)

1. POST `/api/backends/text-completions/status` with `{api_server, api_type}` (TG:679-688). The bias cache is cleared (TG:671).
2. The per-type model loader runs, then `setOnlineStatus(model || data.result)` (TG:692-727). `power_user.chat_template_hash` is reset (TG:733).
3. `autoSelectInstructPreset(online_status)` runs (TG:736; see 2.F).
4. If the response header `x-supports-tokenization: true` is present, `sessionStorage[TOKENIZER_SUPPORTED_KEY]` is set (TG:738-739).
5. For koboldcpp and llamacpp, when instruct derivation, context derivation or context-size derivation is wanted, `/props` is called (TG:741-796):
   - `default_generation_settings.n_ctx` becomes max_context, if `context_size_derived` (TG:767-779).
   - The templates come from `power_user.model_templates_mappings[hash]` if present, otherwise `deriveTemplatesFromChatTemplate` (TG:782-784, see 2.H). They are then selected with `isAuto`.

#### 2.B.5 Banned tokens and strings: `getCustomTokenBans` (TG:439-500)

- **Sources:** `banned_tokens` + `global_banned_tokens`, each split on `\n`, plus `textgenerationwebui_banned_in_macros` from the `{{banned "…"}}` macro (macros/definitions/core-macros.js:425-443). Lines are deduplicated and macro-substituted (TG:451-457). The macro list is cleared after each use (TG:465).
- **Kill switch:** `send_banned_tokens=false` disables everything (TG:441).
- **Line formats:**
  - `[1,2,3]` is a list of raw token ids (TG:469-480).
  - `"text"` is a banned string, sent in `banned_strings` (TG:481-485).
  - Any other text is tokenized with `getTokenizerForTokenIds()` (TG:486-492).
- **Tokenizer choice** (TG:408-431), in order:
  1. API_CURRENT (remote `/encode`) if the best match is the textgen API tokenizer, or if the user picked API_CURRENT and the type is in `TEXTGEN_TOKENIZERS`. That list is ooba, tabby, koboldcpp, llamacpp, vllm, aphrodite (tokenizers.js:1214-1221).
  2. Otherwise the user's encode tokenizer.
  3. OpenRouter maps the model's `architecture.tokenizer` (textgen-models.js:1347-1373).
  4. DreamGen uses Mistral or Llama3 (:1375-1385).
  5. The default is LLAMA.
- **Output:** `banned_tokens` is a CSV of ids.

#### 2.B.6 Streaming: `generateTextGenWithStreaming` (TG:1289-1343)

- Sets `stream=true` and POSTs to `/api/backends/text-completions/generate`. The response is parsed as SSE with `getEventSourceStream()` (sse-stream.js:381).
- Each event:
  - `[DONE]` ends the stream (TG:1320).
  - `tryParseStreamingError` throws on `error.message | error | message | detail` (TG:1430-1445).
  - `choices[0].index > 0` appends to `swipes[index-1]`, the OAI-style multi-swipe (TG:1326-1328).
  - `data.index > 0` does the same for llama.cpp, using `data.content` (TG:1329-1332).
  - Otherwise `text += choices[0].text || content` (TG:1334). Logprobs are parsed per chunk (TG:1336). `state.reasoning += choices[0].reasoning ?? choices[0].thinking`, which covers OpenRouter and Ollama (TG:1337).
- Yields `{text (cumulative), swipes, logprobs, toolCalls:[], state}` (TG:1340).
- **Non-streaming:**
  - The request goes through `sendGenerationRequest` (SJ:6116-6138), using the same URL (`getGenerateUrl`, SJ:6172-6186).
  - Text is extracted from `choices[0].text ?? choices[0].message.content ?? content ?? response ?? data[0].content` (SJ:6286-6287).
  - Reasoning comes from `extractReasoningFromData`: OpenRouter `choices[0].reasoning`, Ollama `thinking` (RS:118-126).
  - Multi-swipes come from `choices[1..]` for mancer/vllm/aphrodite/tabby/infermaticai, or from the llama.cpp array `data[1..].content` (SJ:6374-6410).
- **Streaming is enabled when** `textgen_settings.streaming` is set (SJ:3517-3526).

#### 2.B.7 Logprobs extraction (TG:1353-1427; SJ:6235-6268)

- **Streaming, `parseTextgenLogprobs(token, lp)`:**
  - koboldcpp, tabby, vllm, aphrodite, mancer, infermaticai and ooba take `lp.top_logprobs[0]` (a `{tok: logprob}` map) and turn it into `[[tok,logprob]]` (TG:1359-1372).
  - llama.cpp has two shapes. Old: `[{probs:[{tok_str,prob}]}]`, used as probabilities. New: `[{top_logprobs:[{token,logprob}]}]`, converted with `Math.exp` (TG:1373-1398). The stream reads `data.completion_probabilities`.
- **Non-streaming:**
  - llamacpp: `completion_probabilities.map(x=>parseTextgenLogprobs(x.content,[x]))` (SJ:6251-6253).
  - koboldcpp, vllm, infermaticai, aphrodite, mancer, tabby: `parseTabbyLogprobs`, which slices `choices[0].text` by `logprobs.text_offset` into tokens and pairs each with `top_logprobs[i]` (TG:1405-1420; SJ:6254-6261).

#### 2.B.8 Stop-string assembly: `getStoppingStrings(isImpersonate,isContinue,api)` (SJ:3025-3064)

- **Chat Completion** (`api==='openai'`) uses only the custom strings (SJ:3027-3029).
- **Otherwise**, in this order:
  1. If `context.names_as_stop_strings`: `\n{{char}}:` when impersonating, else `\n{{user}}:`. Then always `\n{{user}}:`. Then `\n{{char}}:` when continuing after a user message. Then `\n<member>:` for every other group member (SJ:3033-3056).
  2. `getInstructStoppingSequences()` (2.F).
  3. `getCustomStoppingStrings()`: the JSON array `power_user.custom_stopping_strings`, macro-substituted if `custom_stopping_strings_macro`, plus ephemeral strings (PU:3078-3112).
  4. If `single_line`, `\n` is put first (SJ:3061-3063).
  5. The list is deduplicated.
- **Client-side trimming:** `cleanUpMessage` also trims any partial stop-string suffix from the output, i.e. the longest prefix of a stop string that matches the end of the text (SJ:6467-6476).

#### 2.B.9 textgen-models.js (model pickers)

- **Model caches:** per-type arrays (textgen-models.js:12-21) and loaders (:484-766).
  - Ollama uses `{id,name}`.
  - Tabby and llama.cpp sort by id and prepend an empty entry (:504-550).
  - Generic keeps a free-text id not present in `/v1/models` (:607-645).
  - Featherless is a paged grid with stats and a "new" view (:768-1001).
  - The OpenRouter provider list is at :27-118, with a warning UI at :339-369.
- **Tabby model load/download:** `downloadTabbyModel` (:1253).
- **OpenRouter cost estimate:** `completion*amount_gen + prompt*(max_context-amount_gen)` (:1321-1345).

---

### 2.C KoboldAI classic (kai-settings.js)

**`kai_settings` (kai-settings.js:28-50):**

| key | default |
|---|---|
| temp | 1 |
| rep_pen | 1 |
| rep_pen_range | 0 |
| top_p | 1 |
| min_p | 0 |
| top_a | 1 |
| top_k | 0 |
| typical | 1 |
| tfs | 1 |
| rep_pen_slope | 0.9 |
| streaming_kobold | false |
| sampler_order | [0..6] |
| mirostat | 0 |
| mirostat_tau | 5.0 |
| mirostat_eta | 0.1 |
| use_default_badwordsids | false |
| grammar | '' |
| seed | -1 |
| api_server | '' |
| preset_settings | 'gui' |
| extensions | {} |

- **Sampler order IDs** (from `#kobold_order` in index.html): 0 Top K, 1 Top A, 2 Top P, 3 TFS, 4 Typical, 5 Temperature, 6 Rep Pen.
- The recommended order is `KOBOLDCPP_ORDER=[6,0,1,3,4,2,5]` (kai-settings.js:76, applied by the button at :491-495). Drag-and-drop saves the order (:478-489).
- **Presets:** `koboldai_settings` are JSON presets from the server (:97-132). The `'gui'` preset means "use KoboldAI's own GUI settings": inputs are disabled (:497-520).
- **URL:** `formatKoboldUrl` forces the pathname to `/api` unless `power_user.relaxed_api_urls` (:78-89).

**`kai_flags` feature detection (kai-settings.js:57-65, 379-389).** United version comes from `/api/v1/info/version` and KoboldCpp version from `/api/extra/version`:

| flag | requires |
|---|---|
| can_use_stop_sequence | United ≥ 1.2.2 |
| can_use_default_badwordsids | United ≥ 1.2.4 |
| can_use_streaming | KCPP ≥ 1.30 |
| can_use_mirostat | KCPP ≥ 1.35 |
| can_use_tokenization | KCPP ≥ 1.41 |
| can_use_grammar | KCPP ≥ 1.44 |
| can_use_min_p | KCPP ≥ 1.48 |

- **Status:** `getStatusKobold` POSTs `/api/backends/kobold/status` with `{main_api, api_server}`, then sets online status to the model and calls `autoSelectInstructPreset` (kai-settings.js:406-450).
- **Server side** (src/endpoints/backends/kobold.js:143-188): fetches `/v1/info/version`, `/extra/version` and `/v1/model` in parallel. Model `ReadOnly` maps to `no_connection`.

**Request body.** With the `gui` preset it is only `{prompt, gui_settings:true, max_length, max_context_length, api_server}` (SJ:5258-5264; quiet path SJ:4047-4048). Otherwise `getKoboldGenerationData(finalPrompt, settings, maxLength, maxContextLength, isHorde, type)` builds (kai-settings.js:174-210):

- **Base fields:** `prompt`, `gui_settings:false`, `sampler_order`, `max_context_length`, `max_length`, `rep_pen`, `rep_pen_range`, `rep_pen_slope`, `temperature`, `tfs`, `top_a`, `top_k`, `top_p`, `typical`, `use_world_info:false`, `singleline:false`, `api_server`, `sampler_seed` (if ≥ 0).
- **Gated on flags, or always for Horde:** `min_p`, `stop_sequence` (= getStoppingStrings), `mirostat`, `mirostat_tau`, `mirostat_eta`, `use_default_badwordsids`, `grammar` (macro-substituted).
- **Other gated fields:**
  - `grammar_retain_state` (grammar flag and continue).
  - `streaming` = streaming_kobold && can_use_streaming && type ≠ quiet.
  - `can_abort` = can_use_streaming.

**Server proxy** (kobold.js:11-141):
- Forwards a whitelisted `this_settings` with `use_story/memory/authors_note/world_info:false`. When `gui_settings` is true, only prompt and lengths are sent (:40-79).
- POSTs to `{api_server}/extra/generate/stream` when streaming, otherwise `/v1/generate` (:97).
- Retries up to 50× at 2.5 s on 403/503 (:93-135).
- On abort, POSTs `/extra/abort` if `can_abort` (:21-37).

**Streaming:** `generateKoboldWithStreaming` reads SSE events `{token}` and accumulates `text += data.token` (kai-settings.js:228-257). The non-streaming response text is `data.results[0].text` (SJ:6283-6284).

---

### 2.D NovelAI (nai-settings.js)

**Models** (index.html:2412-2414; `default_presets` at nai-settings.js:27-31):

| model id | default preset | tokenizer (`getTokenizerTypeForModel`, :627-639) | base URL (src/endpoints/novelai.js:267) |
|---|---|---|---|
| `clio-v1` | Talker-Chat-Clio | NERD | `https://api.novelai.net` |
| `kayra-v1` | Carefree-Kayra | NERD2 | `https://text.novelai.net` |
| `llama-3-erato-v1` | Erato-Dragonfruit | LLAMA3 | `https://text.novelai.net` |

Changing the model auto-selects its default preset (nai-settings.js:900-909).

**`nai_settings` (nai-settings.js:36-62):**

| key | default |
|---|---|
| temperature | 1.5 |
| repetition_penalty | 2.25 |
| repetition_penalty_range | 2048 |
| repetition_penalty_slope | 0.09 |
| repetition_penalty_frequency | 0 |
| repetition_penalty_presence | 0.005 |
| tail_free_sampling | 0.975 |
| top_k | 10 |
| top_p | 0.75 |
| top_a | 0.08 |
| typical_p | 0.975 |
| min_p | 0 |
| math1_temp / math1_quad / math1_quad_entropy_scale | 1 / 0 / 0 |
| min_length | 1 |
| model_novel | clio-v1 |
| preset_settings_novel | Talker-Chat-Clio |
| streaming_novel | false |
| preamble | `'[ Style: chat, complex, sensory, visceral ]'` (:24) |
| prefix | '' |
| banned_tokens | '' |
| order | `[1,5,0,2,3,4]` (:25) |
| logit_bias | [] |
| extensions | {} |

Also present but not defaulted: `phrase_rep_pen`, `mirostat_lr`, `mirostat_tau` (loaded at :198-200).

- **phrase_rep_pen** values: off, very_light, light, medium, aggressive, very_aggressive.
- **prefix** values: vanilla, special_instruct, special_proseaugmenter, theme_textadventure (index.html).

**Sampler IDs** (nai-settings.js:71-82): temperature 0, top_k 1, top_p 2, tfs 3, top_a 4, typical_p 5, mirostat 8, math1 ("Unified") 9, min_p 10. The order holds only enabled IDs; disabled items are moved to the bottom (:641-680).

**Native preset conversion:** `convertNovelPreset` converts a NAI v3 preset (`presetVersion:3`) and filters `parameters.order` to enabled ids (:118-148).

**Subscription and tier limits:**
- `/api/novelai/status` returns the `/user/subscription` data (:162-175; server novelai.js:133-165).
- Tier names: 0 Paper, 1 Tablet, 2 Scroll, 3 Opus (:64-69).
- Max context (`getKayraMaxContextTokens`): Tablet 4096, Scroll/Opus 8192 (:92-103).
- Max response: Tablet/Scroll 150, Opus 250, default 150 (:105-116).
- Context cap: Clio/Kayra min(max_context, 8192) with the tier limit applied; Erato min(…, 8192) − 10 (SJ:5933-5952).

**`getNovelGenerationData(finalPrompt, settings, maxLength, isImpersonate, isContinue, _cfgValues, type)` (nai-settings.js:515-607).** CFG is ignored for NovelAI.
- **Stop strings:** from `getStoppingStrings`. For Erato, each stop string that starts with `\n` gets 12 extra punctuation-prefixed variants (`.`, `!`, `?`, `*`, `"`, `_`, `...`, `."`, `?"`, `!"`, `.*`, `)`) (:523-542). Stop strings are tokenized into id arrays, at most 1024 (:544-547).
- **Bad words:** `getBadWordIds(banned_tokens)`, cached by hash (:434-486):
  - `{text}` is tokenized verbatim.
  - `[ids]` is used raw.
  - Plain text expands to 10 case/leading-space permutations (`getBadWordPermutations`, :488-513).
- **Logit bias:** `logit_bias_exp` entries are `{bias, ensure_sequence_finish:false, generate_once:false, sequence:[ids]}` (:682-710, using logit-bias.js).
- **Prefix:** `selectPrefix` returns `special_instruct` if the last 1500 chars contain `}` and the model is clio/kayra/erato, otherwise the user prefix. Other models get `vanilla` (:610-625).
- **Erato prompt:** the prompt is prefixed with `<|startoftext|><|reserved_special_token81|>` (:563-565).
- **Body fields:**
  - `input`, `model`, `use_string:true`.
  - `temperature`, `max_length` = min(maxLength, the tier maximum for kayra/erato, else 150), `min_length`.
  - `tail_free_sampling`, `repetition_penalty` with `_range`, `_slope`, `_frequency`, `_presence`.
  - `top_a`, `top_p`, `top_k`, `min_p`, `math1_temp`, `math1_quad`, `math1_quad_entropy_scale`, `typical_p`, `mirostat_lr`, `mirostat_tau`, `phrase_rep_pen`.
  - `stop_sequences`, `bad_words_ids`, `logit_bias_exp`, `generate_until_sentence:true`, `use_cache:false`, `return_full_text:false`, `prefix`, `order`.
  - `num_logprobs:10` if requested (:569-606).
- **Server additions** (novelai.js:167-297):
  - Merges built-in per-model bad-word lists (:75-91).
  - Adds default biases of -0.08 on the dinkus/asterism tokens: [23], [21] for clio/kayra and [12488], [128041] for erato (:64-107).
  - Adds `repetition_penalty_whitelist` per model (:109-119).
  - Sets `eos_token_id` for `theme_textadventure` (49405 for clio/kayra, 29 for erato) (:247-256).
  - POSTs `/ai/generate-stream` or `/ai/generate` (:268).

**Preamble and instruct handling:**
- `addChatsPreamble` prepends `substituteParams(nai_settings.preamble)+'\n'` to the chat-history block (after the chat_start separator) only when `main_api==='novel'` (SJ:6013-6017, 5187-5190).
- NovelAI always sets `force_name2` (SJ:4608).
- Quiet/instruct prompts are wrapped by `adjustNovelInstructionPrompt`: `[]` is stripped and the text is wrapped in `{ … }` unless it already contains `{ ` (nai-settings.js:716-722; used at SJ:3974, 4384).

**Streaming:** SSE `{token, logprobs}` (nai-settings.js:741-783). The non-streaming text is `data.output` (SJ:6290).

**Logprobs:** `parseNovelAILogprobs` merges `after` with `before`, giving tokens missing from `after` a value of -Infinity, and adds the chosen token. It returns token **IDs**, which are later decoded in bulk (:803-838; logprobs.js:575-601).

---

### 2.E AI Horde (horde.js)

- **Settings `horde_settings`:** `models:[]`, `auto_adjust_response_length:true`, `auto_adjust_context_length:false`, `trusted_workers_only:false` (horde.js:25-30).
- **Constants:** MAX_RETRIES 480, CHECK_INTERVAL 2500 ms (roughly a 20-minute timeout), MIN_LENGTH 16 (:32-34).
- **Sampler params:** Horde reuses the **KoboldAI preset** through `getKoboldGenerationData(..., isHorde=true)`, which sends every flag-gated field. The `gui` preset is rejected (`isHordeGenerationNotAllowed`, :359-366).
- **Model selection:** `getHordeModels(force)` (:292-315):
  - Fetches `/api/horde/text-models`, which calls `aihorde.net/api/v2/status/models?type=text` and is cached for 60 s server-side (src/endpoints/horde.js:9, 96-126).
  - Sorts by whitelisted, then the `popular` tag, then performance.
  - Labels each model `name (ETA, Speed, Queue, Workers)` (:350-357).
  - Drops stale selections.
- **On model change:** `autoSelectInstructPreset(models.join(' '))` runs (:417-432).
- **`adjustHordeGenerationParams(max_ctx, max_len)`** (:147-184):
  - Fetches workers from `/api/horde/text-workers` (`/v2/workers?type=text`, cached).
  - Keeps workers whose `cluster` matches the model's and whose `models` include it, skipping untrusted workers if `trusted_workers_only`.
  - Takes the **minimum** `max_context_length` and `max_length` across them, each only if the corresponding auto-adjust flag is on.
- **Use in Generate:**
  - `this_max_context = maxContextLength - maxLength` (SJ:4576-4587).
  - The response length is clamped with `max(min(len, adjusted), MIN_LENGTH)` (SJ:5252-5255).
  - The Kobold body receives the adjusted context (SJ:5269).
- **`generateHorde(prompt, params, signal, reportProgress)`** (:202-286):
  - Deletes `params.prompt` and sets `n=1`, `frmtadsnsp/frmtrmblln/frmtrmspch/frmttriminc=false`.
  - Payload: `{prompt, params, trusted_workers, models}`, POSTed to `/api/horde/generate-text`. The server sends it to `aihorde.net/api/v2/generate/text/async` with an `apikey` (the anonymous key is `0000000000`) and a `Client-Agent` header (src/endpoints/horde.js:7, 183-196).
  - Receives the task `id`.
  - Polls `/api/horde/task-status` (`GET /v2/generate/text/status/{id}`) every 2.5 s:
    - `faulted` → error.
    - `is_possible===false` → error.
    - `done` with `generations[0]` → returns `{text, workerName:"Generated by Horde worker: W [model]"}`.
    - Otherwise progress is `100 - queue_position/first_position*100`.
  - On abort it cancels with DELETE on the same URL (:248-251; server :144-160).
  - The title shown is `data.workerName` (SJ:6188-6194).
- **Status:** `/api/horde/status` returns heartbeat `ok`. Kudos come from `/api/horde/user-info` (:106-123, 327-348).
- **Horde is non-streaming only:** `sendGenerationRequest` routes it to `generateHorde` (SJ:6121-6123).

---

### 2.F Instruct mode (instruct-mode.js)

**Template fields.** The live copy is `power_user.instruct` (defaults at PU:218-245). UI controls are listed at IM:23-48. Presets are `default/content/presets/instruct/*.json`.

| field | default | meaning |
|---|---|---|
| enabled | false | master toggle |
| preset | 'Alpaca' | selected template name |
| input_sequence / input_suffix | `### Instruction:` / '' | user prefix/suffix |
| output_sequence / output_suffix | `### Response:` / '' | assistant prefix/suffix (`separator_sequence` migrated to output_suffix, IM:57-60) |
| system_sequence / system_suffix | '' / '' | narrator/system message prefix/suffix |
| first_input_sequence / last_input_sequence | '' | prefix for the first user message in context / the last user message (falls back to input_sequence) |
| first_output_sequence / last_output_sequence | '' | prefix for the first assistant message in context / the prompt's final assistant line |
| last_system_sequence | '' | prefix for quiet (system) generation lines |
| story_string_prefix / story_string_suffix | '' | wraps the rendered story string |
| stop_sequence | '' | always a stop string; also hard-truncates output (SJ:6524-6528) |
| wrap | true | join parts with `\n`; empty suffix becomes `\n` |
| macro | true | substitute `{{macros}}` and `{{name}}` in sequences |
| names_behavior | `force` | enum `none` / `force` / `always` (IM:17-21); migrated from `names` / `names_force_groups` (IM:63-70) |
| activation_regex | '' | model-id regex for auto-selection |
| bind_to_context | false | select the same-named context template |
| user_alignment_message | '' | synthetic first user message inserted before the history (SJ:4818-4827) |
| system_same_as_user | false | narrator uses input_sequence/suffix |
| skip_examples | false | don't wrap examples in sequences |
| sequences_as_stop_strings | true | add all sequences as stop strings, and strip leaked sequences (SJ:6537-6546) |

Migration fills missing fields (IM:72-90) and deletes obsolete ones (IM:92-103).

**Name inclusion (`names_behavior`):**
- **Chat messages** (IM:389-393):
  - `always`: names are always included, except for narrator messages.
  - `force`: names are included only for non-user messages in a group or with `force_avatar`.
  - `none`: names are never included.
- **Examples** (IM:518-519, 552-554): `always` includes names. `force` includes the name only on `example_user` lines, or on all lines in groups (via `parseExampleIntoIndividual(…, includeGroupNames)`).
- **Final prompt line** (IM:595): names appear if `always`, or if `force` and in a group. Never for quiet non-loud generations.

**`formatInstructModeChat(name, mes, isUser, isNarrator, forceAvatar, name1, name2, forceOutputSequence, customInstruct)` (IM:387-457):**
- **Prefix:**
  - Narrator: system_same_as_user ? input_sequence : system_sequence.
  - User: FIRST → first_input‖input, LAST → last_input‖input, else input.
  - Assistant: FIRST → first_output‖output, LAST → last_output‖output, else output.
- **Suffix:** narrator uses input_suffix or system_suffix (as above); user uses input_suffix; assistant uses output_suffix.
- If `macro`: `substituteParams` with name overrides, then `{{name}}` becomes the speaker name, or `System` if there is none.
- If the suffix is empty and `wrap` is on, the suffix is `\n`.
- Result: `[prefix, (name? "name: ")+mes+suffix].filter(Boolean).join(wrap?'\n':'')`.
- `force_output_sequence = {FIRST:1, LAST:2}` (IM:369-372).
- **Call site** (SJ:4771-4787): the message at `coreChat[0]` (the oldest message in context) is formatted with FIRST. The last user message is formatted with LAST, except when impersonating. `formatMessageHistoryItem` (SJ:5833-5853) skips messages flagged with `IGNORE_SYMBOL`. For non-instruct it produces `name: mes\n`.

**`formatInstructModeStoryString(storyString, {customContext, customInstruct})` (IM:478-502):** unless the story string position is IN_CHAT, prepends `story_string_prefix` (macro-substituted, `{{name}}`→System, plus `\n` if wrap) and appends `story_string_suffix`.

**`formatInstructModeExamples(mesExamplesArray, name1, name2)` (IM:511-578):**
- `blockHeading` = example_separator + `\n`, or ''.
- If `skip_examples`, it only replaces `<START>\n` with the heading.
- Otherwise each `<START>` becomes `{Example Dialogue:}`. Blocks are split into `example_user` / `example_assistant` messages, and each is wrapped with input or output prefix/suffix (`{{name}}`→name1/name2 when macro is on). The heading is pushed per block.
- Falls back to the raw text if nothing parses.

**`formatInstructModePrompt(name, isImpersonate, promptBias, name1, name2, isQuiet, isQuietToLoud, customInstruct)` (IM:593-649), the final open-ended line:**
- **Sequence choice:**
  - Impersonate: last_input‖input.
  - Quiet (not loud): last_system‖output.
  - Quiet-to-loud or normal: last_output‖output.
- **Mistral hack:** if names are included, `output_sequence` ends in whitespace and `last_output_sequence` doesn't, that whitespace is added as `nameFiller` (IM:621-631).
- **Text:** `sep+seq+sep+filler+"name:"` with names, or `sep+seq` without. A quiet prompt drops the leading separator.
- **Prompt bias** (not when impersonating) is appended, trimmed when names are off.
- **Return value:** `(wrap? trimEnd : as is) + (names? '' : sep)`.
- Called at SJ:5063-5067 with name = quietName‖'System' for quiet prompts, name1 for impersonation, else name2. Quiet prompts also use it at SJ:3975.

**`getInstructStoppingSequences({customInstruct, useStopStrings})` (IM:301-361):**
- When instruct is enabled, starts from `stop_sequence`. If `sequences_as_stop_strings`, it adds input, output, first_output, last_output, system and last_system sequences, with `{{name}}` replaced by name1, name2 or `System`.
- The combined text is split on `\n`, deduplicated, and whitespace-only lines are dropped.
- Each line becomes `'\n'+seq` if `wrap`, and is macro-substituted if `macro`.
- If `useStopStrings ?? context.use_stop_strings`, adds `\n`+chat_start and `\n`+example_separator.

**Auto-selection `autoSelectInstructPreset(modelId)` (IM:236-291).** Called with `online_status` from textgen and Kobold status, and with the joined model names for Horde.
1. If `power_user.model_templates_mappings[modelId]` exists, select its instruct and context templates and stop (IM:237-248).
2. If instruct is disabled, stop.
3. Use the first preset whose `activation_regex` (parsed with `regexFromString`) matches the model id (IM:259-276).
4. Otherwise, if `bind_to_context`, select the instruct preset named like the current context preset (IM:279-288).

**Related selection behaviour:**
- `selectInstructPreset` also **enables** instruct mode (IM:205-229).
- `selectContextPreset` is at IM:180-196.
- Changing the instruct preset copies every control property and, with `bind_to_context`, selects the matching context template (IM:827-860). Enabling instruct with bind_to_context does the same (IM:804-816).
- `updateBindModelTemplatesState` reflects whether the current pair equals the mapping for `online_status` or the chat-template hash (IM:162-171).

**Macros** (`getInstructMacros`, IM:673-783):
- Instruct sequences: `instructStoryStringPrefix/Suffix`, `instructInput|instructUserPrefix`, `instructUserSuffix`, `instructOutput|instructAssistantPrefix`, `instructSeparator|instructAssistantSuffix`, `instructSystemPrefix`, `instructSystemSuffix`, `instructFirstOutput|…`, `instructLastOutput|…`, `instructStop`, `instructUserFiller` (user_alignment_message), `instructSystemInstructionPrefix` (last_system), `instructFirstInput|…`, `instructLastInput|…`. These are enabled only when instruct is enabled.
- `systemPrompt` (the character prompt if `prefer_character_prompt`, else sysprompt.content) and `defaultSystemPrompt|instructSystem|instructSystemPrompt`, both when sysprompt is enabled.
- `chatSeparator` (example_separator) and `chatStart`.

---

### 2.G Context templates & system prompts (power-user.js, sysprompt.js)

**`power_user.context` (PU:247-257) and context controls (PU:354-369).** Presets are `default/content/presets/context/*.json`; e.g. `Llama 3 Instruct.json` contains all fields below plus `name`.

| field | default | notes |
|---|---|---|
| preset | 'Default' | |
| story_string | `defaultStoryString` (PU:90): `{{#if system}}{{system}}\n{{/if}}{{#if description}}{{description}}\n{{/if}}{{#if personality}}{{char}}'s personality: {{personality}}\n{{/if}}{{#if scenario}}Scenario: {{scenario}}\n{{/if}}{{#if persona}}{{persona}}\n{{/if}}` | Handlebars |
| example_separator | `***` (PU:91) | replaces `<START>` (non-instruct, non-CC) (SJ:3510-3512) |
| chat_start | `***` (PU:92) | `addChatsSeparator` prepends `chat_start+'\n'` to the history block (SJ:6019-6025) |
| use_stop_strings | true (control default false, PU:358) | adds chat_start / example_separator as stops |
| names_as_stop_strings | true | see 2.B.8 |
| story_string_position | `extension_prompt_types.IN_PROMPT` (0) | `NONE:-1, IN_PROMPT:0, IN_CHAT:1, BEFORE_PROMPT:2` (SJ:484-489) |
| story_string_depth | 1 | used when IN_CHAT |
| story_string_role | SYSTEM (0) | roles `SYSTEM:0, USER:1, ASSISTANT:2` (SJ:494-498) |
| always_force_name2 *(global power_user key stored in the preset)* | false (control default true) | forces `{{char}}:` on the last line (SJ:4608) |
| trim_sentences *(global)* | false | `trimToEndSentence` on output (SJ:6584-6586; utils.js:882) |
| single_line *(global)* | false | adds `\n` as the first stop string (SJ:3061) |

Other global formatting keys, not stored in context presets:
- `collapse_newlines` (PU:120) collapses `\n+` to `\n` on both the prompt (SJ:5199-5201) and the output (SJ:6483-6485), via `collapseNewlines` (PU:408-410).
- `trim_spaces` (PU:208; output trim at SJ:6588).
- `pin_examples` / `strip_examples` (PU:121-122; strip applied at SJ:4738-4740).
- `custom_stopping_strings` (JSON) with `_macro` (PU:298-299).

**Loading a context preset:** `#context_presets` change copies each control (`preset[prop] ?? defaultValue`), with global settings written to `power_user` and the rest to `power_user.context`. It runs `autoFixStoryString` and, with bind_to_context, selects the instruct preset of the same name (PU:2037-2080). `autoFixStoryString` inserts `{{#if anchorBefore}}…` at the start and `{{#if anchorAfter}}…` at the end for legacy presets that lack `story_string_position` (PU:1949-1980).

**`renderStoryString(params, {customStoryString, customInstructSettings, customContextSettings})` (PU:2234-2265):**
1. `validateStoryString` warns once per field-hash when a field has content but no `{{field}}` placeholder (PU:2273+).
2. `Handlebars.compile(story_string, {noEscape:true})(params)`.
3. `substituteParams(output, user, char)`.
4. Leading `\n`s are stripped.
5. A trailing `\n` is added if one is missing and the position is not IN_CHAT and (instruct is off, or wrap is on with no story_string_suffix).

**Story string params** (SJ:4703-4719): `description`, `personality`, `persona` (only when the persona position is IN_PROMPT), `scenario`, `system`, `char`, `user`, `wiBefore`/`loreBefore`, `wiAfter`/`loreAfter`, `anchorBefore` (BEFORE_PROMPT extension prompts), `anchorAfter` (IN_PROMPT extension prompts), `mesExamples` (formatted), `mesExamplesRaw`.

**Placement** (SJ:4722-4735):
- The rendered string is wrapped with `formatInstructModeStoryString` when instruct is on.
- If `story_string_position===IN_CHAT` (text completion only), it is injected as the extension prompt `STORY_STRING` at `story_string_depth`/`story_string_role`, and the top-of-prompt story string is emptied.
- **Final assembly:** `combinedStoryString + mesExmString + [chat_start + NAI preamble + history] + generatedPromptCache` (SJ:5187-5197).

**System prompt (sysprompt.js).** State is `power_user.sysprompt = {enabled:true, name:'Neutral - Chat', content:"Write {{char}}'s next reply in a fictional chat between {{char}} and {{user}}.", post_history:''}` (PU:267-272). Presets are `{name, content, post_history}` in `default/content/presets/sysprompt/` (Actor, Assistant - Expert/Simple, Blank, Chain of Thought, Lightning 1.1, Neutral - Chat, Roleplay - Detailed/Immersive/Simple, Text Adventure, Writer - Creative/Realistic).
- **Load and selection:**
  - `loadSystemPrompts` (sysprompt.js:50-74).
  - Selecting a preset auto-enables the system prompt and copies content and post_history (:166-185).
  - Migration from the legacy `instruct.system_prompt` into a `[Migrated] <preset>` sysprompt (:23-48). Instruct templates imported with `system_prompt` trigger a confirmation popup (:76-96).
- **Applied for Text Completion only** (`main_api !== 'openai'`):
  - `system` = the character's `system_prompt` card field with `{{original}}` set to sysprompt.content, if `prefer_character_prompt`; otherwise sysprompt.content (SJ:4686-4697). It is empty when disabled. The result fills `{{system}}` in the story string.
  - `post_history` (or the character's post-history instructions with `{{original}}`, if `prefer_character_jailbreak`) is appended as a **user** message at the end of history, or before the last message when continuing (SJ:4745-4762).
- **Chat Completion** ignores power_user.sysprompt except through the `{{systemPrompt}}` macros (IM:754-762).
- **Slash commands:**
  - `/sysprompt [name] quiet= forceGet=` uses fuzzy match (:126-154, 196-238).
  - `/sysprompt-on|enable` (:239-244), `/sysprompt-off|disable` (:245-250), `/sysprompt-state|toggle [bool]` (:251-262).

---

### 2.H chat-templates.js auto-detection

- **`hash_derivations`** (chat-templates.js:5-103) maps sha256(chat_template) (as computed server-side, text-completions.js:263) to a template name used for both context and instruct:

| Template name | Models covered |
|---|---|
| Llama 3 Instruct | Llama 3.1, Llama 3.2, Nemotron 70B |
| Mistral V2 & V3 | Small 2409, Large 2407, 7B v0.3 |
| Mistral V3-Tekken | Nemo |
| Mistral V7 | Large 2411, Small 24B |
| Gemma 2 | gemma-2 (two hashes), gemma-3 |
| Command R | command-r-08-2024 |
| Tulu | Tulu 3 |
| DeepSeek-V2.5 | V2.5, R1 |
| GLM-4 | GLM-4 |
| Moonshot AI | Kimi K2 |
| OpenAI Harmony | gpt-oss, unsloth and ggml builds |

- **`substr_derivations` heuristics** (:105-111), where every substring must be present:

| Template | Required substrings |
|---|---|
| Moonshot AI | `<|im_user|>user<|im_middle|>`, `<|im_assistant|>assistant<|im_middle|>`, `<|im_end|>` |
| OpenAI Harmony | `<|start|>user<|message|>`, `<|start|>assistant<|channel|>final<|message|>`, `<|end|>` |
| ChatML | `<|im_start|>user`, `<|im_start|>assistant`, `<|im_end|>` |

- **`deriveTemplatesFromChatTemplate(chat_template, hash)`** (:120-139): an empty template returns `{context:null, instruct:null}`; a hash hit or substring match returns `{context:name, instruct:name}`; otherwise it warns.
- **Order of precedence at connect** (TG:782-794): a user mapping `model_templates_mappings[hash]` beats derivation. Context is applied only if `context_derived`. Instruct is applied only if instruct is enabled and `instruct_derived`. Derivation is skipped when `autoSelectInstructPreset` already matched (TG:743-744).
- **`bindModelTemplates(power_user, online_status)`** (:141-198), the "bind" toggle:
  - If the current context/instruct pair already equals the mapping, it unbinds by deleting the mapping for both the hash and the model name.
  - Otherwise it stores `{context?, instruct?}` (each only if the matching derived flag is on) under the `online_status` key (unless it starts with `koboldcpp/ggml-model-`) and under `chat_template_hash`.
- Settings: `instruct_derived`, `context_derived`, `context_size_derived`, `model_templates_mappings`, `chat_template_hash` (PU:259-265).

---

### 2.I Reasoning parsing (reasoning.js)

**Settings `power_user.reasoning`** (PU:274-284): `name:'Think XML'`, `auto_parse:false`, `add_to_prompts:false`, `auto_expand:false`, `show_hidden:false`, `prefix:'<think>'`, `suffix:'</think>'`, `separator:'\n'`, `max_additions:1`. UI bindings are at RS:801-872. Selecting a template copies prefix, suffix and separator (RS:853-871).

**Templates** (`default/content/presets/reasoning/*.json`; `DEFAULT_REASONING_TEMPLATE='Think XML'` at RS:34):

| name | prefix | suffix | separator |
|---|---|---|---|
| Blank | '' | '' | '' |
| DeepSeek | `<think>\n` | `\n</think>` | `\n\n` |
| Gemma 4 | `<|channel>thought\n` | `<channel|>` | `\n\n` |
| OpenAI Harmony | `<|start|>assistant<|channel|>analysis<|message|>` | `<|start|>assistant<|channel|>final<|message|>` | '' |
| Think XML | `<think>` | `</think>` | `\n` |

Customised legacy settings migrate to a `[Migrated] Custom` template (RS:1655-1693).

**Message data model:** `chat[i].extra.reasoning` (string), `extra.reasoning_duration` (ms or null), `extra.reasoning_type` ∈ `ReasoningType {model, parsed, manual, edited}` (RS:57-62), `extra.reasoning_display_text`, and `extra.reasoning_signature` (Gemini/OpenRouter encrypted signature, RS:179-215).

**API-native reasoning, `extractReasoningFromData(data, {mainApi, ignoreShowThoughts, textGenType, chatCompletionSource})`** (RS:112-168):

| API / source | Field read |
|---|---|
| textgen openrouter | `choices[0].reasoning` |
| textgen ollama | `thinking` |
| CC (only if `oai_settings.show_thoughts` or ignoreShowThoughts) deepseek, xai | `choices[0].message.reasoning_content` |
| CC openrouter | `message.reasoning ?? message.reasoning_content` |
| CC makersuite, vertexai | `responseContent.parts[thought].text` joined `\n\n` |
| CC claude | `content[type=thinking].thinking` joined |
| CC mistralai | `message.content[0].thinking[].text` |
| CC aimlapi, pollinations, moonshot, cometapi, chutes, electronhub, nanogpt, siliconflow, zai, workers_ai, fireworks, custom | `reasoning_content ?? reasoning` |

- **Streaming:** textgen accumulates into `state.reasoning` (TG:1337), and `StreamingProcessor` calls `reasoningHandler.updateReasoning(messageId, state.reasoning)` (SJ:3892).
- **Hidden-reasoning models** (CC only): ids starting with `gpt-4.5`, `o1`, `o3`, `gemini-2.0-flash-thinking-exp` or `gemini-2.0-pro-exp` (RS:221-246). These show a "Thinking…" state without text.

**`ReasoningHandler` state machine** (RS:265-670). `ReasoningState {None, Thinking, Done, Hidden}` (RS:265-270).
- **Fields:** `state`, `type`, `reasoning`, `reasoningDisplayText`, `startTime`, `endTime`, `initialTime`; private `#isParsingReasoning`, `#parsingReasoningMesStartIndex` (RS:276-315).
- **`process(messageId, mesChanged, promptReasoning)`**, called on each stream tick (RS:454-471; SJ:3692):
  1. Runs `#autoParseReasoningFromMessage`.
  2. If there is reasoning (or the model is hidden-reasoning): `updateReasoning(persist)`.
  3. If reasoning changed while in None, moves to Thinking with `startTime=initialTime`.
  4. If the message text changes while Thinking and the reasoning did not change, sets `endTime=now` and calls `finish()`.
- **`#autoParseReasoningFromMessage`** (RS:480-527), active only when `auto_parse` is on and prefix and suffix are set:
  - The parse target is `promptReasoning.prefixReasoningFormatted + mes` when continuing an incomplete prefix.
  - If the target starts with the prefix, it enters parsing mode (Thinking). All streamed text goes to `reasoning` and `mes=''`.
  - When the suffix appears, it splits there, records `#parsingReasoningMesStartIndex`, and the remainder becomes `mes`.
  - Later ticks just slice.
- **`updateReasoning`** (RS:414-441): applies `trimSpaces` and the regex placement `REASONING`, sets type `parsed` or `model`, and with `persist` writes the extra fields including `reasoning_duration = end-start`.
- **`finish`** (RS:538-553): sets `endTime` if missing, moves Thinking to Hidden (for hidden models) or Done, persists, and emits `STREAM_REASONING_DONE(reasoning, duration, messageId, state)`.
- **Other methods:**
  - `initContinue(promptReasoning)` restores the prefix reasoning and duration (RS:321-326).
  - `initHandleMessage` rebuilds state from extras: Done if `reasoning`, Hidden if only a duration; `reset` clears it (RS:339-390).
  - The UI shows "Thought for {humanized}" or "Thinking…" (RS:636-664).
- **Non-streaming / post-hoc parsing:**
  - On `MESSAGE_RECEIVED` and `MESSAGE_UPDATED`, if `auto_parse`: `parseReasoningFromString(latestPrefix + mes)` moves the reasoning into `extra` (type parsed, regex REASONING applied) and rewrites `mes` (RS:1554-1625).
  - `IMPERSONATE_READY` strips reasoning from the input box (RS:1627-1647).
  - Multi-swipes are parsed by `parseReasoningInSwipes` (RS:1533-1552; SJ:3775, 6823).

**`parseReasoningFromString(str, {strict=true}, template)`** (RS:1461-1492): regex `(strict? '^\s*?' : '') + esc(prefix) + '(.*?)' + esc(suffix)` with the `s` flag. Only the first match is removed; it returns `{reasoning, content}`, both trimmed. Returns null if prefix or suffix is missing.

**Related helpers:**
- `removeReasoningFromString` (RS:1429-1436) is a no-op unless auto_parse.
- `formatReasoning(reasoning, content, template)` gives `prefix+reasoning+suffix+separator+content`, macro-substituted (RS:1503-1517).

**`PromptReasoning`**, which adds reasoning back into prompts (RS:671-799). It is used at SJ:4531-4556, iterating the chat from newest to oldest:
- `addToMessage(content, reasoning, isPrefix, duration)` is skipped when `!add_to_prompts` or `counter ≥ max_additions` (the prefix/continue message is exempt), and when the reasoning is empty or the placeholder `​`.
- Otherwise it returns `prefix+reasoning+suffix+separator+content`.
- For a continue with empty content it returns `prefix+reasoning` only, with `prefixIncomplete=true`, so the model keeps thinking.
- In group chats, reasoning from other members is not added (SJ:4537).
- Reasoning is regex-processed with `REASONING` placement (`isPrompt`, `depth`).
- `removePrefix` strips the formatted prefix from the continued output (SJ:5386, 5510).
- `getLatestPrefix` blocks `trim_spaces` while a prefix is open (SJ:6588).
- The latest instance is cleared on `GENERATION_STOPPED/ENDED` and `CHAT_CHANGED` (RS:1621-1623).

**reasoning_effort (Chat Completion only):** `reasoning_effort_types {auto, low, medium, high, min, max}` (openai.js:239-246). `getReasoningEffort` maps auto to undefined and remaps min/max per source and model, e.g. DeepSeek, Fireworks, koboldcpp custom → `minimal/xhigh`, OpenRouter min → `none`, gpt-5.x rules (openai.js:2545-2640+). It is sent as `reasoning_effort` (openai.js:2821).

**Slash commands** (RS:899-1188):

| Command | Lines | Arguments / behaviour |
|---|---|---|
| `/reasoning-get [mesId]` | :901 | returns a message's reasoning |
| `/reasoning-set at= collapse= <text>` | :921 | sets a message's reasoning |
| `/reasoning-parse regex=true return=reasoning|content strict=true <text>` | :971 | extracts reasoning from text |
| `/reasoning-format reasoning=<r> <content>` | :1041 | builds the formatted string |
| `/reasoning-template quiet= [name]` | :1079 | fuzzy select |
| `/reasoning-collapse`, `/reasoning-expand`, `/reasoning-toggle [id or range]` | :1146, :1158, :1170 | collapse/expand blocks |

- **Macros:** `{{reasoningPrefix}}`, `{{reasoningSuffix}}`, `{{reasoningSeparator}}` (RS:1190-1206).
- **UI handlers:** edit, add, delete and copy reasoning; editing sets type `edited`/`manual` (RS:1208-1428).

---

### 2.J CFG (cfg-scale.js)

**Storage:**
- **Global:** `extension_settings.cfg.global = {guidance_scale:1, negative_prompt:'', positive_prompt?}` (cfg-scale.js:16-22).
- **Per character:** `extension_settings.cfg.chara[] = {name: <avatar filename>, guidance_scale, negative_prompt, positive_prompt, useChara?}`. An entry is removed when everything is at its default (:35-91).
- **Per chat:** `chat_metadata` keys (:389-397):

| Key | Meaning |
|---|---|
| `cfg_guidance_scale` | chat guidance scale |
| `cfg_negative_prompt` | chat negative prompt |
| `cfg_positive_prompt` | chat positive prompt |
| `cfg_prompt_combine` | int[] of `cfgType` (`chat:0, chara:1, global:2`, :383-387) |
| `cfg_groupchat_individual_chars` | use character CFG in groups |
| `cfg_prompt_insertion_depth` | default 1 |
| `cfg_prompt_separator` | JSON-encoded string; default `\n` (:437-451) |

- **Migration** of legacy keys `power_user.guidance_scale` / `negative_prompt` and `cfg_negative_*` metadata (:235-276).

**`getGuidanceScale()`** (:401-431), in priority order:
1. Chat value, if set, not 1, and the group override is off.
2. Character value, if not in a group (or the group override is on) and ≠ 1.
3. Global value, if ≠ 1.
4. Otherwise undefined.

**`getCfgPrompt(gs, isNegative, quiet)`** (:459-497): collects the prompt (negative or positive) from the source chosen by `gs.type` plus any sources listed in `prompt_combine`, in the order global, chara, chat (built with unshift). It macro-substitutes, joins non-empty parts with the custom separator, and returns `{value, depth: cfg_prompt_insertion_depth ?? 1}`.

**Use in Generate:**
- `useCfgPrompt = gs && gs.value !== 1` (SJ:4590-4591).
- Max context is reduced by max(tokens(neg), tokens(pos)) (SJ:4594-4603).
- In `getCombinedPrompt(isNegative)` the CFG prompt is appended to the last message at depth 0, otherwise pushed into `finalMesSend[len-depth].extensionPrompts` (SJ:5147-5166).
- **Positive** prompt: goes into the real prompt.
- **Negative** prompt: the whole prompt is rebuilt with the negative text, and that becomes `cfgValues.negativePrompt` (SJ:5274).

**Supported APIs:**
- Text Completion only. `guidance_scale` and `negative_prompt` are sent in `nonAphroditeParams` (TG:1689-1690), i.e. every textgen type except vllm, infermaticai and aphrodite. The UI exposes them for ooba and tabby (`#cfg_block_ooba data-tg-type="ooba, tabby"`).
- NovelAI accepts `cfgValues` but ignores it (`_cfgValues`, nai-settings.js:515).
- CC returns early, since `getCombinedPrompt` returns '' for openai (SJ:5140-5142).

---

### 2.K Logit bias (logit-bias.js) & logprobs (logprobs.js)

**Entry format** (shared by textgen and NAI): an ordered array `logit_bias: [{id: uuid, text: string, value: number}]` (logit-bias.js:57-63). It is stored in the preset (`textgenerationwebui_settings.logit_bias` or `nai_settings.logit_bias`). `BIAS_CACHE: Map<containerSelector, computed>` is invalidated on any edit, reorder, preset change, connect or type change (logit-bias.js:5, 49, 77-92; TG:384, 597, 671, 943).

**`getLogitBiasListResult(list, tokenizerType, getBiasObject)`** (logit-bias.js:104-142). For each non-empty trimmed `text`:
- `{text}` is tokenized verbatim.
- `[1,2]` is a list of raw ids.
- Plain text is tokenized as `" "+text` (a leading space is added).

`getBiasObject(value, tokens)` then decides the output shape per API:

| API | Tokenizer | Wire format |
|---|---|---|
| Textgen (most types) | `getTokenizerForTokenIds()` (TG:408-431) | `{ "tokenId": bias }`, every token of the sequence gets the same bias (TG:518-550) |
| Textgen llamacpp/ollama | same | `[[id, bias], …]` plus `[id, false]` for bans (TG:1809-1814) |
| NovelAI | model tokenizer (2.D) | `logit_bias_exp: [{sequence:[ids], bias, ensure_sequence_finish:false, generate_once:false}]` (nai-settings.js:682-710) |
| Chat Completion | server-side tokenizer for `getTokenizerModel()` | `bias_presets[selected] = [{text,value}]` is POSTed to `/api/backends/chat-completions/bias?model=`, which returns `{token: value}`. Claude returns `{}` (openai.js:3401-3418, 2790-2801; chat-completions.js:2158-2210) |

**Logprobs data model** (logprobs.js:23-68):
- `Candidate = [tokenText: string, logprob: number]`. Values ≤ 0 are treated as log-probabilities and shown as `exp`; values > 0 are treated as probabilities (:184-195).
- `TokenLogprobs = {token: string, topLogprobs: Candidate[]}`. NovelAI initially carries numeric ids that are decoded later (:575-601).
- `MessageLogprobData = {created, hash, messageId, swipeId, api, messageLogprobs: TokenLogprobs[], continueFrom: string|null}`.
- In-memory state `{selectedTokenLogprobs, messageLogprobs: Map<hash, MessageLogprobData>}`. It is not persisted to the chat file and is capped at 100 messages (`MAX_MESSAGE_LOGPROBS`, :20, 531-538).
- The hash is `getStringHash(JSON.stringify({name, mid: index, text: mes}))` (:543-552). Lookup is always for the **last** message (:559-566).

**Request toggle:** `power_user.request_token_probabilities` (PU:188) controls:

| API | Request field |
|---|---|
| Textgen | `logprobs` 5 or 10, `n_probs` 10 (TG:1605, 1701) |
| NovelAI | `num_logprobs` 10 (nai-settings.js:605) |
| Chat Completion | openai.js:2856 |

**Capture:**
- **Streaming:** `StreamingProcessor` pushes each chunk's logprobs (SJ:3888-3890) and calls `saveLogprobsForActiveMessage(list, continueMessage)` when done (SJ:3781).
- **Non-streaming:** `parseAndSaveLogprobs` (SJ:6235-6268).

**Viewer features:**
- The candidate list adds an `<others>` = 1-Σp row (:172-236).
- **Token reroll:** pick an alternative, build `continueFrom + tokens[0..i] with tokens[i] replaced`, create a new swipe containing that text, then `Generate('continue')` (:257-325). Disallowed for CC: "rerolling a token is not supported with OpenAI" (:262-267).
- **Swipe creation:** `createSwipe` runs `cleanUpMessage` and routes the text into the reasoning field when an open reasoning prefix is present (:369-453).
- **Prefix reroll:** `onPrefixClicked` (:292-300).
- **Whitespace display:** `▁`/`Ġ` are normalised to a space and `Ċ` to a newline (:218, 278).

---

### 2.L Tool calling (tool-calling.js)

**Types** (TC:1-50)
- `ToolInvocation` = `{id, displayName, name, parameters (JSON string), result (string), signature?, reasoning?, error?}`.
- `ToolInvocationResult` = `{invocations[], errors[], stealthCalls[]}`.

**`ToolDefinition`** (TC:115-238)
- Private fields: `name, displayName, description, parameters (JSON schema), action, formatMessage, shouldRegister, stealth`.
- `toFunctionOpenAI()` returns `{type:'function', function:{name, description, parameters}}` (TC:190-203).
- `invoke(params)` awaits `action(params)` (TC:209-211).
- `formatMessage` defaults to `Invoking tool: <displayName|name>` (TC:218-222).
- `shouldRegister` defaults to true (TC:224-228).

**`ToolManager`** (static; TC:242-1174)

| member | behavior | cite |
|---|---|---|
| `#tools: Map<name, ToolDefinition>`; `RECURSE_LIMIT=5` (overwritten from `oai_settings.tool_call_recurse_limit`, O:4399, 7057-7061) | registry | TC:247-255 |
| `registerFunctionTool({name, displayName, description, parameters, action, formatMessage, shouldRegister, stealth})` | Also accepts legacy positional `(name, description, parameters, action)`. Overwrites an existing name with a warning. | TC:269-291 |
| `unregisterFunctionTool(name)` | | TC:297-305 |
| `invokeFunctionTool(name, params)` | Parses params: `''` → `{}`, a string → `JSON.parse`. Non-string results are JSON-stringified. Errors are **returned** as `Error` with `cause=name`. | TC:311-346 |
| `isStealthTool`, `formatToolCallMessage`, `getDisplayName` | | TC:352-393 |
| `registerFunctionToolsOpenAI(data)` | For each tool with `await shouldRegister()`: `data.tools=[…]`, `data.tool_choice='auto'` | TC:400-418 |
| `isToolCallingSupported(settings, model)` | Requires `main_api==='openai' && function_calling`. Not allowed for openai/azure `gpt-6-astra`. Post-processing must be one of `''`, `merge_tools`, `semi_tools`, `strict_tools`. When the model is in `model_list`, per-source flags decide (pollinations `tools`, fireworks `supports_tools`, openrouter `supported_parameters` includes `tools`, mistral `capabilities.function_calling`, aimlapi `features`, chutes `supported_features`, electronhub `metadata.function_call`, workers_ai `properties`). Otherwise a static allowlist applies: **every source except perplexity**. | TC:614-686 |
| `canPerformToolCalls(type)` | Supported, and type is not impersonate / quiet / continue | TC:694-700 |
| `parseToolCalls(toolCalls, parsed, toolSignatures)` | Accumulates streaming deltas into `toolCalls[choiceIndex][toolIndex]`. Formats: OpenAI `choices[].delta.tool_calls[]` (index-keyed; attaches signature by id); Cohere `tool-call-start/delta/end` events with `delta.message`; Claude `content_block{type:'tool_use'}` + `delta.input_json_delta.partial_json`, buffered and parsed at `content_block_stop`; Gemini `candidates[].content.parts[].functionCall` (+`thoughtSignature`) | TC:427-558 |
| `#applyToolCallDelta` | Deep-merges. Strings are concatenated, except `id`, `name` and `type`, which are set once. Null never overwrites an existing value. `__proto__`/`constructor` are skipped. | TC:565-606 |
| `#getToolCallsFromData` / `hasToolCalls` | Normalizes streamed arrays (Claude → `{id, function:{name, arguments:input}}`; Google → random id + `args` + signature) and non-stream shapes (Google `responseContent.parts`, OpenAI `choices[index 0].message.tool_calls` + OpenRouter `reasoning_details` signatures, Claude `content[type=tool_use]`, Cohere `message.tool_calls`) | TC:707-779 |
| `invokeFunctionTools(data, {reasoningText})` | For each call: shows a toast with `formatMessage` (no timeout), invokes, clears the toast. On error, records an invocation with `error:true, result:error.toString()` (stealth tools go to `stealthCalls` instead). On success, records the invocation, unless it is a stealth tool (then `stealthCalls`). Fields: `{id, displayName, name, parameters:stringify, result, error, signature, reasoning}` | TC:786-857 |
| `saveFunctionToolInvocations(invocations)` | Pushes a **system chat message**: `{name: systemUserName, force_avatar: system_avatar, is_system:true, is_user:false, mes:<details><summary>Tool calls: name (n)…</summary><pre><code json>…</code></pre></details>, extra:{isSmallSys:true, tool_invocations, api, model}}`. Emits `TOOL_CALLS_PERFORMED`, renders, emits `TOOL_CALLS_RENDERED`, saves the chat. | TC:863-922 |
| `showToolCallError(errors)` | Toast; clicking it opens the error list | TC:928-933 |

**Generation integration**
- **Request**
  - `createGenerationParameters` registers tools (O:2840-2842).
  - Prompt building reserves the tool-JSON tokens (O:1309-1316).
  - Past invocations are replayed as assistant `tool_calls` + `tool` messages (O:996-1064).
  - System tool messages stay in `coreChat` only while tools are usable (S:4496).
- **Recursion** (`Generate`): `canPerformToolCalls = !dryRun && canPerformToolCalls(type) && depth < RECURSE_LIMIT` (S:4495).
  - Streaming, after the stream finishes with tool calls (S:5408-5436):
    1. If the visible reply is empty (`''`/`'...'`, no reasoning, not a swipe), delete it; otherwise finalize it as an intermediary message.
    2. `invokeFunctionTools`.
    3. If no invocations (and the reply was deleted), or any stealth call happened: stop, showing errors if any.
    4. Otherwise `depth++`, `saveFunctionToolInvocations`, and recurse with `Generate('normal', {..., depth})`.
  - Non-streaming does the same (S:5541-5560).
- **Stealth tools**: the result isn't saved to chat and no follow-up generation runs.
- **Backend translation**:
  - Claude: tools → `{name, description, input_schema}`; history `tool_calls` → `tool_use`; tool results → user `tool_result`. Without tools, these are flattened to text (BE:284-296; PC:225-366).
  - Google: `function_declarations`, `functionCall` / `functionResponse` (BE:571-596; PC).
  - Mistral: tool ids sanitized (PC:770-778).
  - Cohere: a tool-call message needs an assistant primer text (PC:390-399).
- **Extension API**: `getContext()` exposes `registerFunctionTool`, `unregisterFunctionTool`, `isToolCallingSupported`, `canPerformToolCalls`, `ToolManager` (`st-context.js:183-187`). Built-in user: Image Generation's `GenerateImage` tool (`extensions/stable-diffusion/index.js:5452-5470`).

**Slash commands** (`initToolSlashCommands`, TC:935-1173)

| command | args | behavior | cite |
|---|---|---|---|
| `/tools-list` (`/tool-list`) | `return=` (default popup-html; any return-helper type, including object) | Lists `toFunctionOpenAI()` for every tool | TC:941-963 |
| `/tools-invoke` (`/tool-invoke`) | `parameters=` (dict, required), unnamed tool name (enum) | `invokeFunctionTool`; throws on Error; returns the result | TC:965-998 |
| `/tools-register` (`/tool-register`) | `name`, `description`, `parameters` (JSON string), `displayName?`, `formatMessage?` (closure), `shouldRegister?` (closure → bool), `stealth?` (bool), unnamed **action closure** | Closures run with args exposed as `{{var::arg.*}}` / `arg` (`assignNestedVariables`, TC:62-79); the pipe value is the result | TC:1000-1149 |
| `/tools-unregister` (`/tool-unregister`) | unnamed name | | TC:1151-1171 |

---

### 2.M custom-request.js (ConnectionManagerRequestService / TextCompletionService / ChatCompletionService)

These are exported to extensions through `getContext()` as `ChatCompletionService`, `TextCompletionService` and `ConnectionManagerRequestService` (`st-context.js:108-109, 292-294`).

**`ChatCompletionService`** (`TYPE='openai'`, CR:421-606)

| method | behavior | cite |
|---|---|---|
| `createRequestData({stream=false, messages, model, chat_completion_source, max_tokens, temperature, custom_url, reverse_proxy, proxy_password, custom_prompt_post_processing, ...props})` | Adds `use_sysprompt:true`; strips undefined values | CR:428-454 |
| `sendRequest(data, extractData=true, signal)` | POST `/api/backends/chat-completions/generate`. **Non-stream**: throws on `!ok`/`json.error`. Returns raw JSON if `!extractData`, else `{content: extractMessageFromData(json,'openai'), reasoning: extractReasoningFromData(..., ignoreShowThoughts:true)}`; with `json_schema`, `content = JSON.parse(extractJsonFromData(...))`. **Stream**: returns `async function*`, using `getStreamingReply(parsed, state, {chatCompletionSource, overrideShowThoughts:true})`; yields `{text, swipes, state}` | CR:462-534 |
| `processRequest(requestData, {presetName}, extractData, signal)` | `createRequestData`. If a preset is named, `presetToGeneratePayload(preset, {}, requestData)`, then send | CR:544-565 |
| `presetToGeneratePayload(preset, overridePreset, overridePayload)` | Preset → clone of `oai_settings` via `settingsToUpdate`. Drops `bias_preset_selected` when the preset has no `bias_presets`. For `custom_url`, `vertexai_region`, `zai_endpoint`, `siliconflow_endpoint`, `minimax_endpoint`, `pollinations_endpoint`, precedence is profile > preset > settings. Calls `createGenerationParameters(settings, model, 'quiet', messages)` (so no streaming, prefill or tools), then merges `overridePayload` | CR:575-605 |

**`TextCompletionService`** (`TYPE='textgenerationwebui'`, CR:79-416)

| method | behavior | cite |
|---|---|---|
| `createRequestData({stream, prompt, max_tokens, model, api_type, api_server, temperature, min_p, ...})` | Sets `max_new_tokens = max_tokens`; `api_server` defaults to `getTextGenServer(api_type)`; strips undefined | CR:86-110 |
| `sendRequest` | Non-stream: POST `getGenerateUrl(TYPE)`, extract content and reasoning. Stream: POST `/api/backends/text-completions/generate`; SSE; `choices[0].text` or `content`; `choices[0].reasoning`; index>0 → swipes | CR:118-190 |
| `constructPrompt(messages, instructPreset\|name, instructSettings)` | Formats with `formatInstructModeChat` per message (skipping `ignoreInstruct`). A trailing assistant message is a prefill that `formatInstructModePrompt` continues | CR:198-273 |
| `processRequest(data, {presetName, instructName, instructSettings}, ...)` | Array prompt: instruct-format (plus stop strings) or join with `\n\n`. Applies the textgen preset (`presetToGeneratePayload`, CR:395-415). Non-stream post-trim: removes trailing whitespace, partial stop strings, and instruct `stop`/`input` sequences (cut), and `output` sequences (removed) | CR:283-386 |

**`ConnectionManagerRequestService`** (SH:392-660)
- `defaultSendRequestParams` = `{stream:false, signal:null, extractData:true, includePreset:true, includeInstruct:true, instructSettings:{}}` (SH:393-400).
- `getAllowedTypes()` = `{openai:'Chat Completion', textgenerationwebui:'Text Completion'}` (SH:402-407).
- `sendRequest(profileId, prompt (string or messages), maxTokens, custom, overridePayload)` (SH:423-494) throws if the connection-manager extension is disabled. It resolves the profile and validates it via `CONNECT_API_MAP[profile.api]`. Then:
  - **openai**: string prompts become `[{role:'user', content}]`, then `ChatCompletionService.processRequest`:
    - `model = profile.model`
    - `chat_completion_source = map.source`
    - `secret_id = profile['secret-id']`
    - `custom_url`, `vertexai_region`, `zai/siliconflow/minimax/pollinations_endpoint` all = `profile['api-url']`
    - proxy from `profile.proxy` (looked up in proxy presets)
    - `custom_prompt_post_processing = profile['prompt-post-processing']`
    - `presetName = profile.preset` if `includePreset`
  - **textgenerationwebui**: `TextCompletionService.processRequest` with `api_type = map.type`, `api_server = profile['api-url']`, `instructName = profile.instruct` (if `includeInstruct`), preset.
  - Any error is wrapped as `Error('API request failed', {cause})`.
- Other methods:
  - `constructPrompt` (SH:501-524)
  - `getSupportedProfiles` (SH:530-539)
  - `getProfile` (SH:546-550)
  - `getProfileIcon` (SH:558-573)
  - `isProfileSupported` (SH:579-598)
  - `validateProfile` (SH:605-623)
  - `handleDropdown` (SH:634+)

---

### 2.N Prompt post-processing

**Enum** (frontend O:220-231 = backend PC:15-26). The UI select is `#custom_prompt_post_processing` and is shown for every source (`index.html:4044-4065`). Slash command: `/prompt-post-processing` (`/ppp`), where `none` = `''` (`slash-commands.js:3550-3591`). Connection profiles store it as `prompt-post-processing` (SH:452).

| value | UI label | `mergeMessages` options (PC:85-106) | tools allowed? (TC:627-633) |
|---|---|---|---|
| `''` (NONE) | None | untouched | yes |
| `claude` (deprecated, migrated to `merge`, O:4278) | – | same as `merge` | no |
| `merge` | Merge consecutive roles (no tools) | `strict:false, placeholders:false, single:false, tools:false` | no |
| `merge_tools` | Merge consecutive roles (with tools) | same, with `tools:true` | yes |
| `semi` | Semi-strict (alternating roles; no tools) | `strict:true, placeholders:false` | no |
| `semi_tools` | Semi-strict (with tools) | `strict:true, placeholders:false, tools:true` | yes |
| `strict` | Strict (user first, alternating roles; no tools) | `strict:true, placeholders:true` | no |
| `strict_tools` | Strict (with tools) | `strict:true, placeholders:true, tools:true` | yes |
| `single` | Single user message (no tools) | `strict:true, single:true` | no |

**Where it runs**
- On the backend, at the very start of `/generate`, before dispatch, so it applies to **every** source including Claude and Google (BE:2246-2252).
- The frontend only sends the field (O:2826).
- Some sources are **force post-processed** regardless of the setting:
  - Perplexity → `strict` (BE:2429)
  - DeepSeek → `semi_tools` + trailing-assistant `prefix` (BE:1119)
  - MiniMax → `merge_tools` (BE:1626)

**`mergeMessages(messages, names, opts)`** (PC:827-954)
1. Missing content becomes `''`. Array content is flattened to text joined with `\n\n`. `image_url` / `video_url` / `audio_url` parts are replaced by random 32-byte base64 tokens kept in a map (PC:834-852).
2. Example messages: system `example_assistant` gets a `{{char}}: ` prefix and `example_user` gets `{{user}}: ` (unless already prefixed or starting with a group member name) (PC:854-863).
3. Any message with a `name` and a non-system role gets the `name: ` prefix (PC:864-868).
4. Without `tools`: `role:'tool'` becomes `user` (PC:869-871).
5. `single`: assistant messages get the char prefix, user messages the user prefix, and **every** role becomes `user` (PC:872-885).
6. `name` is deleted. Without `tools`, `tool_calls` and `tool_call_id` are deleted (PC:886-890).
7. Consecutive messages with the same role are squashed with `\n\n`, except `tool` messages and messages with empty content (PC:894-900).
8. An empty result becomes a single user message containing `PROMPT_PLACEHOLDER`. The placeholder is config `promptPlaceholder`, default `"Let's get started."` (PC:4, 903-908).
9. Media tokens are restored by splitting content on `\n\n` and rebuilding `[{type:'text'}, media, …]` (PC:911-934).
10. `strict`:
    - any `system` message after index 0 becomes `user`
    - with `placeholders` (strict / strict_tools only): if the leading system message isn't followed by a user message, insert a placeholder user message after it; if the first message is neither system nor user, unshift a placeholder user message
    - then recurse once with `strict:false` to re-merge (PC:936-951)

**Related, not `custom_prompt_post_processing`**
- Frontend `squash_system_messages`: merges consecutive unnamed system messages with `\n`, excluding `newMainChat` / `newChat` / `groupNudge`. Runs only on real (non-dry) generations (O:3922-3954, 1617-1619).
- Per-provider converters that always run:
  - `convertClaudeMessages`: leading system messages become `system[]` when `use_sysprompt`; other system messages become user; names are prefixed; same roles are merged into content arrays; the prefill becomes a final assistant message, trimmed (PC:197-372).
  - `convertGooglePrompt` (PC:432-629)
  - `convertCohereMessages` (PC:384-430)
  - `convertAI21Messages` (PC:631-701)
  - `convertMistralMessages` (PC:703-783)
  - `convertXAIMessages` (PC:785-825)
- `addAssistantPrefix(prompt, tools, prop)`: sets `prefix`/`partial` on a trailing assistant message only when there are no tools (PC:67-76).
- `convertTextCompletionPrompt`: produces `System: …` / `name: …` / `role: …` lines plus `\nassistant:` for text-completion models (PC:961-977).

---

## 3. Context budget & tokenization (`scripts/tokenizers.js`, `script.js`)

### 3.1 Budget numbers

| Function | Value | Cite |
|---|---|---|
| `getMaxContextTokens()` | Details below the table | `script.js:5929-5960` |
| `getMaxResponseTokens()` | TC / NAI / Kobold: `amount_gen`. CC: `oai_settings.openai_max_tokens` | `script.js:5966-5974` |
| `getMaxPromptTokens(override)` | context − response | `script.js:5981-5987` |
| CFG reduction | − max(negative, positive) CFG prompt tokens | `script.js:4600-4610` |
| Horde | `adjustedParams.maxContextLength - maxLength` | `script.js:4591-4593` |
| WI budget | Computed inside `checkWorldInfo` from `this_max_context` (see §4) | `script.js:4637` |

`getMaxContextTokens()` by API (`script.js:5929-5960`):

| API | Value |
|---|---|
| TC / Kobold / Horde | `max_context` |
| NovelAI clio | capped at 8192 |
| NovelAI kayra | capped at 8192, and also at the subscription tier limit |
| NovelAI erato | `min(max_context, 8192) - 10` |
| CC | `oai_settings.openai_max_context` |
| Fallback | 1487 |

### 3.2 Text Completion trimming algorithm (`script.js:4839-4955`)

1. **Base cost.** `getMessagesTokenCount()` counts tokens of `storyString + examplesString (pinned only) + userAlignment + chat_start/preamble + modifyLastPromptLine('') + cyclePrompt`, **with `power_user.token_padding` (default 64, `power-user.js:119`)**.
2. **Continue.** `cyclePrompt = chat2.shift()`, so the message being continued is always kept.
3. **Injections first.** Injected (depth-prompt) messages are pre-allocated in index order while the running total stays `< this_max_context` (`4873-4895`).
4. **History.** Walk `chat2` from newest (index 0) to oldest, adding each non-injected message's tokens. Stop at the first message that would overflow (`4897-4917`). The result is everything newer than the cut.
5. **Boundary.** Mark the `lastInContext` UI boundary and set `chat_metadata.lastInContextMessageId` (`setInContextMessages`, `6083`).
6. **Examples.** Recompute the base, then add unpinned examples **after** history while they fit, giving `count_exm_add`. History has priority over examples (`4943-4955`).
7. **Continue re-check.** For continue only, `checkPromptSize()` re-tokenizes the fully assembled prompt and drops examples first, then the oldest messages (`5077-5101`).

**Chat Completion.** CC trimming is done separately, by the `ChatCompletion` budget in `openai.js` (see §2.A). When `main_api==='openai'`, a core call `getTokenCountAsync(str, power_user.token_padding)` short-circuits to `tokenizers.NONE` (the byte-based guess), because the "shadow" TC prompt is unused (`tokenizers.js:449-457`). Other callers (WI, extensions) use the OpenAI-style counter (`counterWrapperOpenAIAsync`).

### 3.3 Tokenizer selection

`tokenizers` enum (`tokenizers.js:16`): `NONE 0, GPT2 1, OPENAI 2, LLAMA 3, NERD 4, NERD2 5, API_CURRENT 6, MISTRAL 7, YI 8, API_TEXTGENERATIONWEBUI 9, API_KOBOLD 10, CLAUDE 11, LLAMA3 12, GEMMA 13, JAMBA 14, QWEN2 15, COMMAND_R 16, NEMO 17, DEEPSEEK 18, COMMAND_A 19, BEST_MATCH 99`. Default: `power_user.tokenizer = BEST_MATCH` (`power-user.js:118`).

| Mechanism | Rule | Cite |
|---|---|---|
| `guesstimate(str)` | `ceil(utf8Bytes / 3.35)` | `tokenizers.js:12`, `166-169` |
| Server endpoints | `/api/tokenizers/{gpt2,openai,llama,nerdstash,nerdstash_v2,mistral,yi,claude,llama3,gemma,jamba,qwen2,command-r,command-a,nemo,deepseek}/{encode,decode}` (count = encode). Remote: `/api/tokenizers/remote/kobold/count`, `/api/tokenizers/remote/textgenerationwebui/encode` | `tokenizers.js:65-154` |
| Encodable to ids | LLAMA, MISTRAL, YI, LLAMA3, GEMMA, JAMBA, QWEN2, COMMAND_R, COMMAND_A, NEMO, DEEPSEEK | `tokenizers.js:41-57` |
| Best match: NovelAI | clio → NERD; kayra → NERD2; erato → LLAMA3 | `tokenizers.js:290-300` |
| Best match: TC / Kobold | Precedence below the table | `tokenizers.js:301-361`, `1213-1221` |
| CC model tokenizer: `getTokenizerModel()` | Per-source rules below the table | `tokenizers.js:569-790` |
| CC message counting | POST `/api/tokenizers/openai/count?model=<tokenizerModel>` with body `[message]`, once per message. Subtract 2 unless `full` (Claude is always counted full) | `tokenizers.js:796-891` |
| Failure fallback | `apiFailureTokenCount`: set the session warning, retry once with the new best match, else `guesstimate` | `tokenizers.js:1019-1040` |
| Cache | Per-chat object keyed `${tokenizerType}-${hash(str)}${modelHash}+${padding}` (CC: `${model}-${hash(JSON)}`). Persisted to IndexedDB (localforage instance `SillyTavern_ChatCompletions`, key `tokenCache`) on each chat save | `tokenizers.js:157-188`, `470-495`, `893-910` |
| `getTextTokens` / `decodeTextTokens` | Encode / decode token ids, for logit bias and logprobs | `tokenizers.js:1157`, `1192` |

**Best match for TC / Kobold** (`tokenizers.js:301-361`, `1213-1221`). The API tokenizers are only used if the backend is connected and no earlier tokenizer error was recorded (`sessionStorage.tokenizationWarningShown`). First match wins:
1. Kobold with `kai_flags.can_use_tokenization` → API_KOBOLD.
2. Textgen type in `TEXTGEN_TOKENIZERS` → API_TEXTGENERATIONWEBUI. The list is tabby, koboldcpp, llamacpp, vllm, aphrodite, and ooba (ooba only once its endpoint has been validated).
3. OpenRouter / DreamGen → the model's own tokenizer.
4. Model-name heuristics (TC only), first match: llama3 → LLAMA3; mistral / mixtral → MISTRAL; gemma → GEMMA; nemo / pixtral → NEMO; deepseek → DEEPSEEK; yi → YI; jamba → JAMBA; command-r → COMMAND_R; command-a → COMMAND_A; qwen2 → QWEN2.
5. Default → LLAMA.

**`getTokenizerModel()` for CC** (`tokenizers.js:569-790`):

| Source | Tokenizer |
|---|---|
| OpenAI | the model id |
| Azure | the model |
| DeepSeek | deepseek |
| OpenRouter | the model's `architecture.tokenizer` (Llama2 / Llama3 / Mistral / Yi / Gemini → gemma / Qwen / Cohere), else name heuristics |
| ElectronHub, Chutes, Workers AI | name heuristics |
| MiniMax | gpt-3.5-turbo |
| Cohere | command-r / command-a |
| Gemini (MakerSuite / Vertex) | gemma |
| AI21 | jamba |
| Claude | claude |
| Mistral | nemo / mistral |
| Custom | the custom model id |
| Perplexity, Groq | name heuristics |
| default | `gpt-3.5-turbo` |

### 3.4 Example dialogues summary

**Sources.** The card's `mes_example` (or `chat_metadata.mes_example`) (`script.js:3448-3453`), plus WI entries at EM positions (`script.js:4640-4658`).

**Splitting.** Examples are split into blocks on `<START>` (`script.js:3501`). Each block gets a format-dependent heading or structure:

| Format | Treatment |
|---|---|
| TC non-instruct | heading = `context.example_separator`, macro-substituted |
| Instruct | formatted by `formatInstructModeExamples` (`instruct-mode.js`) |
| CC | parsed into `{role, name}` messages (`setOpenAIMessageExamples`), placed by the PromptManager `dialogueExamples` marker |

**Placement in TC:**
- Inside the story string via `{{mesExamples}}` if the template uses it; otherwise appended after the story string (`mesExmString`, `script.js:5013-5014`, `5197-5202`).
- `pin_examples` → always fully included. Otherwise history goes first and examples fill the remaining budget.
- `strip_examples` → examples are removed after the story string is rendered (`script.js:4737`).

**Placement in CC.** The `oai_settings` example behavior (keep / trim / disabled) is handled in `openai.js` (§2.A).

---

## 4. World Info / Lorebooks

Source: `scripts/world-info.js` (6408 lines). Citations of the form `:LINE` refer to `world-info.js`; other files are named explicitly. Server: `src/endpoints/worldinfo.js`.

### 4.1 Enums and constants

| Enum / const | Values | Cite |
|---|---|---|
| `world_info_insertion_strategy` (character_strategy) | `evenly:0`, `character_first:1`, `global_first:2` | `world-info.js:27-31` |
| `world_info_logic` (selectiveLogic) | `AND_ANY:0`, `NOT_ALL:1`, `NOT_ANY:2`, `AND_ALL:3`. The UI dropdown order differs: AND ANY, AND ALL, NOT ALL, NOT ANY (`index.html:6836-6839`). | `world-info.js:33-38` |
| `scan_state` | `NONE:0` (stop), `INITIAL:1`, `RECURSION:2`, `MIN_ACTIVATIONS:3` | `world-info.js:43-60` |
| `world_info_position` | `before:0` (↑Char), `after:1` (↓Char), `ANTop:2`, `ANBottom:3`, `atDepth:4`, `EMTop:5`, `EMBottom:6`, `outlet:7` | `world-info.js:855-864` |
| `wi_anchor_position` (EM entries) | `before:0`, `after:1` | `world-info.js:866-869` |
| `extension_prompt_roles` (entry.role) | `SYSTEM:0`, `USER:1`, `ASSISTANT:2` | `script.js:494-498` |
| Position UI options | 0, 1, 5, 6, 2, 3, then 4 three times with data-role 0/1/2 ("at Depth System/User/AI"), then 7 "Outlet". Choosing a non-depth position sets `role=null`. | `index.html:7174-7197`, `world-info.js:3418-3440` |
| `GENERATION_TYPE_TRIGGERS` | `normal, continue, impersonate, swipe, regenerate, quiet` | `constants.js:36-43` |
| `METADATA_KEY` | `'world_info'` (chat_metadata key for the chat-bound book) | `world-info.js:94` |
| `DEFAULT_DEPTH` / `DEFAULT_WEIGHT` / `MAX_SCAN_DEPTH` | 4 / 100 / 1000 | `world-info.js:96-98` |
| `MAX_COMMENT_LENGTH` | 100 (used when backfilling memos from keys) | `world-info.js:99`, `:2483` |
| `KNOWN_DECORATORS` | `@@activate`, `@@dont_activate` | `world-info.js:100` |
| `SORT_ORDER_KEY` | `'world_info_sort_order'` (accountStorage; editor sort only) | `world-info.js:93` |

### 4.2 Global WI settings

The settings are saved as `settings.world_info_settings = getWorldInfoSettings()` (`script.js:8080`). They are loaded with `setWorldInfoSettings(settings.world_info_settings ?? settings, data)` (`script.js:8013`, `world-info.js:917-1035`).

| Setting | Default | Meaning | Cite |
|---|---|---|---|
| `world_info_depth` | 2 | Global scan depth: the number of most recent chat messages scanned | `:69` |
| `world_info_min_activations` | 0 | If >0, widens the scan depth until N entries have activated | `:70` |
| `world_info_min_activations_depth_max` | 0 | Maximum depth for min-activation widening. 0 means unlimited (capped by chat length). | `:71` |
| `world_info_budget` | 25 | Budget as a % of max context. A value >100 is migrated to 25. | `:73`, `:946-948` |
| `world_info_budget_cap` | 0 | Absolute token cap on the budget. 0 means no cap. | `:81` |
| `world_info_include_names` | true | Chat lines are scanned as `"Name: mes"` (applied by the caller) | `:74`, `script.js:4624` |
| `world_info_recursive` | false | Activated entry content is fed back into the scan | `:75` |
| `world_info_max_recursion_steps` | 0 | Maximum loop iterations. 0 means unlimited. Mutually exclusive with min_activations: setting one in the UI zeroes the other. | `:82`, `:6237-6249`, `:6303-6312` |
| `world_info_overflow_alert` | false | Shows a toast when the budget overflows | `:76`, `:5064-5067` |
| `world_info_case_sensitive` | false | Default case sensitivity | `:77` |
| `world_info_match_whole_words` | false | Default whole-word matching | `:78` |
| `world_info_use_group_scoring` | false | Default inclusion-group scoring | `:79` |
| `world_info_character_strategy` | `character_first` (1) | How global and character lore are merged | `:80` |
| `world_info.globalSelect` | `[]` | Names of the globally selected books (`selected_world_info`). Written on each debounced save. | `:66`, `:84-87`, `:1003` |
| `world_info.charLore` | `[]` | `[{name: <char filename>, extraBooks: string[]}]`: the character's extra ("auxiliary") books | `:2232`, `:6149-6173` |

- `updateWorldInfoSettings(settings, activeWorldInfo)` sets these values programmatically (`:819-853`).
- A legacy `settings.world_info` (string or array) is migrated into `selected_world_info` (`:956-964`).
- On `CHAT_CHANGED`, `getSortedEntries()` runs to pre-warm the cache (`:1012-1017`).

### 4.3 Entry data model (`newWorldInfoEntryDefinition`, `world-info.js:4082-4125`)

`newWorldInfoEntryTemplate` is built from these definitions, minus the ones marked `excludeFromTemplate` (`:4127-4129`). `createWorldInfoEntry` assigns the lowest free integer uid in the range 0..1e6 (`:4137-4149`, `:4395-4410`).

| Field | Default | Type | Meaning | V2 `originalData` path (`:2687-2724`) |
|---|---|---|---|---|
| `uid` | (assigned) | int | Key in `entries`; unique per book | `id` |
| `key` | `[]` | string[] | Primary keywords: plain text or `/regex/flags` (`:4083`) | `keys` |
| `keysecondary` | `[]` | string[] | Secondary ("optional filter") keys (`:4084`) | `secondary_keys` |
| `comment` | `''` | string | Title/memo (`:4085`) | `comment` |
| `content` | `''` | string | Inserted text; macros are substituted on activation (`:4086`) | `content` |
| `constant` | false | bool | Always active; no keys needed (`:4087`) | `constant` |
| `vectorized` | false | bool | Activated by the Vectors extension (`:4088`). The state selector cycles constant / normal / vectorized (`:3284-3316`). | `extensions.vectorized` |
| `selective` | true | bool | Secondary keys are only used when true (`:4089`, `:4924-4928`) | `selective` |
| `selectiveLogic` | 0 AND_ANY | enum | Logic applied to secondary keys (`:4090`) | `selectiveLogic` |
| `addMemo` | false | bool | Shows the comment/title field (UI) (`:4091`) | n/a |
| `order` | 100 | number | Insertion priority. Higher wins the budget and is placed closer to the end of its slot (`:4092`). | `insertion_order` |
| `position` | 0 | enum `world_info_position` | (`:4093`) | `extensions.position` (+ `position` `before_char`/`after_char`, `:3434`) |
| `disable` | false | bool | Kill switch (`:4094`) | `enabled` (inverted, `:3332`) |
| `ignoreBudget` | false | bool | Bypasses the token budget (`:4095`) | `extensions.ignore_budget` |
| `excludeRecursion` | false | bool | Cannot be activated during recursion passes (`:4096`) | `extensions.exclude_recursion` |
| `preventRecursion` | false | bool | Content is not added to the recursion buffer (`:4097`) | `extensions.prevent_recursion` |
| `matchPersonaDescription` | false | bool | Also scans the persona description (`:4098`) | `extensions.match_persona_description` |
| `matchCharacterDescription` | false | bool | Also scans the character description (`:4099`) | `extensions.match_character_description` |
| `matchCharacterPersonality` | false | bool | (`:4100`) | `extensions.match_character_personality` |
| `matchCharacterDepthPrompt` | false | bool | Character depth prompt / notes (`:4101`) | `extensions.match_character_depth_prompt` |
| `matchScenario` | false | bool | (`:4102`) | `extensions.match_scenario` |
| `matchCreatorNotes` | false | bool | (`:4103`) | `extensions.match_creator_notes` |
| `delayUntilRecursion` | 0 | number\|bool | false/0 = off; true = level 1; N = recursion level N (`:4104`, UI `:3776-3801`) | `extensions.delay_until_recursion` |
| `probability` | 100 | 0-100 \| null | Activation chance in %, clamped to 0-100 (`:4105`, `:3173-3190`) | `extensions.probability` |
| `useProbability` | true | bool | Whether to roll the probability. The UI always forces this to true and hides the toggle (`:4106`, `:3222-3223`). | `extensions.useProbability` (convert only) |
| `depth` | 4 | int 0..1000 | Insertion depth for `atDepth` (`:4107`, `:3408-3411`) | `extensions.depth` |
| `outletName` | `''` | string | Outlet key used when `position=outlet` (`:4108`) | `extensions.outlet_name` |
| `group` | `''` | string | Inclusion group(s), comma-separated (`:4109`, `:5391-5398`) | `extensions.group` |
| `groupOverride` | false | bool | Group priority: among overriders, the highest `order` wins (`:4110`) | `extensions.group_override` |
| `groupWeight` | 100 | int 1..10000 | Weight for the random group pick (`:4111`, `:3752`) | `extensions.group_weight` |
| `scanDepth` | null | int\|null | Per-entry scan depth override; null uses the global value. In the UI, empty → null and values >1000 are rejected (`:4112`, `:3704-3724`). | `extensions.scan_depth` |
| `caseSensitive` | null | bool\|null | Overrides the global setting (`:4113`) | `extensions.case_sensitive` |
| `matchWholeWords` | null | bool\|null | Overrides the global setting (`:4114`) | `extensions.match_whole_words` |
| `useGroupScoring` | null | bool\|null | Overrides the global setting (`:4115`) | `extensions.use_group_scoring` |
| `automationId` | `''` | string | Quick Reply auto-exec link: QRs with the same automationId run on `WORLD_INFO_ACTIVATED` (`:4116`; `extensions/quick-reply/src/AutoExecuteHandler.js:87-92`, `quick-reply/index.js:302`) | `extensions.automation_id` |
| `role` | 0 | enum role | Role used for `atDepth`; null for other positions (`:4117`, `:3426-3431`) | `extensions.role` |
| `sticky` | null | int\|null | Stays active for N messages after activation (`:4118`) | `extensions.sticky` |
| `cooldown` | null | int\|null | Cannot activate for N messages after activation, or after sticky ends (`:4119`) | `extensions.cooldown` |
| `delay` | null | int\|null | Cannot activate until the chat has at least N messages (`:4120`) | `extensions.delay` |
| `triggers` | `[]` | string[] ⊂ GENERATION_TYPE_TRIGGERS | Activates only for these generation types; empty means all (`:4124`, `:3830-3850`) | `extensions.triggers` |
| `characterFilterNames` / `characterFilterTags` / `characterFilterExclude` | `[]`/`[]`/false | virtual (excluded from the template) | Slash-command accessors for `characterFilter` (`:4121-4123`) | `character_filter` |
| `characterFilter` | `{isExclude:false,names:[],tags:[]}` (backfilled) | obj | `names` = character avatar filenames without extension; `tags` = tag ids (`:2128-2135`, `:3107-3166`) | `character_filter` |
| `displayIndex` | uid | int | Custom editor order (drag sort) (`:2365`, `:2661-2675`) | `extensions.display_index` |
| `extensions` | — | obj | Only present on converted character books (`:5665`) | |
| runtime only: `world`, `decorators`, `hash` | — | | Added by the lore loaders and `getSortedEntries` (`:4513`, `:4627-4633`) | |

- `addMissingWorldInfoFields` runs when the editor loads. It backfills missing template keys and forces `key`/`keysecondary` to arrays and `characterFilter` to an object (`:2104-2138`).
- `setWIOriginalDataValue` mirrors every edit into `data.originalData.entries[]` (the V2 character_book) when that structure exists (`:2756-2766`).

### 4.4 Sources and merge (`getSortedEntries`, `:4590-4643`)

Every loader maps book entries to `{uid, world: <bookName>, ...rest}`.

| Source | Where the link is stored | Loader | Dedup |
|---|---|---|---|
| Global | `selected_world_info` (settings `world_info.globalSelect`) | `getGlobalLore` `:4527-4542` | none |
| Character primary | `characters[this_chid].data.extensions.world` | `getCharacterLore` `:4475-4525` | Skipped if the book is already loaded as global, chat or persona lore (`:4498-4511`) |
| Character extra | `world_info.charLore[].extraBooks` where `name === getCharaFilename(this_chid)` | same loader, `:4486-4490` | same as primary |
| Chat-bound | `chat_metadata.world_info` | `getChatLore` `:4544-4562` | Skipped if already global |
| Persona | `power_user.persona_description_lorebook` | `getPersonaLore` `:4564-4588` | Skipped if equal to the chat book or already global |
| Group members | No separate handling. In group chats `this_chid` is set to the drafted member before each generation (`group-chats.js:1054`), so only the current speaker's primary and extra books are used. | — | — |

The four loaders run in parallel. When they finish, `WORLDINFO_ENTRIES_LOADED {globalLore, characterLore, chatLore, personaLore}` is emitted (`:4603`). Sorting uses `sortFn = b.order - a.order`, i.e. descending order (`:88`).

| Strategy | Merged order (`:4607-4621`) |
|---|---|
| evenly (0) | `[...global, ...character].sort(order desc)`. Also the fallback for unknown values. |
| character_first (1) | `[...character.sort, ...global.sort]` |
| global_first (2) | `[...global.sort, ...character.sort]` |

The final list is always `[...chatLore.sort, ...personaLore.sort, ...merged]` (`:4624`). Then, for each entry:

1. `parseDecorators` is applied to the content (`:4627-4630`).
2. `hash = getStringHash(JSON.stringify(entry))` is computed after the decorators are stripped; it includes `world` (`:4630-4633`).

The result is returned as a `structuredClone` (`:4638`). On error the function returns `[]`.

**Decorators** (`parseDecorators`, `:4652-4699`). These only apply when the content starts with `@@`:
- Leading lines that start with `@@` are consumed. A known decorator is pushed to `decorators`; an unknown one sets a "fallback" flag.
- A `@@@x` line is used, with one `@` stripped, only when the preceding `@@` line was unknown. Otherwise it is skipped.
- The first line that does not start with `@@` begins the content.

### 4.5 `WorldInfoBuffer` (`:199-473`)

| Member | Behaviour |
|---|---|
| ctor(messages, globalScanData) | Sets `#depthBuffer[d] = messages[d].trim()` for d < 1000. `messages` is newest-first because the caller reverses the chat (`:250-262`, `script.js:4624`). |
| `#startDepth` | Always 0; never changed (`:234`). |
| `#skew` | Incremented by `advanceScan()` for min-activations (`:395-397`). |
| `getDepth()` | `world_info_depth + skew` (`:402-404`) |
| `get(entry, scanState)` | 1. `depth = entry.scanDepth ?? getDepth()`. **If depth ≤ 0 it returns `''`, so no global fields are scanned either** (quirk) (`:280-283`). Depth >1000 is clamped (`:290-293`).<br>2. Builds `'\x01' + <depth messages joined by '\n\x01'>`.<br>3. Appends, with the same joiner, each enabled global field: persona description, character description, personality, depth prompt, scenario, creator notes (`:300-317`).<br>4. Appends the inject buffer (`:319-321`).<br>5. Appends the recursion buffer, except in the `MIN_ACTIVATIONS` state (`:324-326`). |
| `matchKeys(haystack, needle, entry)` | - If `parseRegexFromString(needle)` succeeds, runs `regex.test` on the raw haystack and ignores the case and whole-word settings (`:339-342`).<br>- Otherwise lowercases both sides unless `entry.caseSensitive ?? global` (`:268-271`, `:345-346`).<br>- With whole words (`entry.matchWholeWords ?? global`): a key containing whitespace uses plain `includes`; a single-word key uses `/(?:^\|\W)(escaped)(?:$\|\W)/` (`:349-359`).<br>- Without whole words: `includes` (`:360-361`). |
| `addRecurse` / `addInject` / `hasRecurse` | Buffer mutators (`:372-390`) |
| `static externalActivations: Map<"world.uid", entry>` | Filled by `WORLDINFO_FORCE_ACTIVATE` (`:203`, `:1020-1029`) and read by `getExternallyActivated` (`:411-413`). Cleared by `resetExternalEffects` at the end of a scan (`:418-420`, `:5275`), but **not** on the zero-entries early return (`:4749-4751`). |
| `getScore(entry, state)` | Counts primary matches (p) and secondary matches (s). Returns 0 if there are no primary keys. With secondary keys: AND_ANY → p+s; AND_ALL → p+s only if all secondary keys match, otherwise p; any other logic → p (`:428-472`). |

`parseRegexFromString` (`:2901-2924`):
- Requires the form `^/(.+?)/([gimsuy]*)$` and rejects any unescaped `/` inside the pattern.
- Replaces only the first `\/` with `/`, because `String.replace` is called without `g` (quirk).
- Returns null for invalid syntax.

`splitKeywordsAndRegexes` and `customTokenizer` split comma-separated lists while keeping commas that appear inside `/regex/` (`:2797-2879`).

### 4.6 `WorldInfoTimedEffects` (`:479-792`)

**Persistence.** State is stored in `chat_metadata.timedWorldInfo = { sticky: {}, cooldown: {} }`. Records are keyed by `"${world}.${uid}"` and have the shape `{hash, start, end, protected}` (`:126-131`, `:559-578`, `:593-595`). `start = chat.length` and `end = chat.length + entry[type]` (`:604-611`), where `chat` is the WI scan array (the non-system messages passed by Generate). Delay is never persisted.

**`checkTimedEffects()`** (`:682-688`). Sticky and cooldown are skipped on a dry run. For each stored record (`:619-660`), the first matching rule applies:
1. If `chat.length <= start` and the record is not protected, delete it. This covers cases where the chat did not advance (swipe, regenerate, delete).
2. If no current entry has the same `hash` (the entry was edited, or belongs to another character's book), keep the record until `chat.length >= end`, then delete it.
3. If the entry no longer has that effect configured, delete the record.
4. If `chat.length >= end`, delete the record and call `onEnded`. When a sticky ends and the entry has a cooldown, a **protected** cooldown record starting now is written and pushed to the cooldown buffer (`:517-528`).
5. Otherwise, push the entry to the active buffer.

**Delay** (`:666-676`): active while `chat.length < entry.delay`.

**`setTimedEffects(activated)`**: not run on a dry run. For each activated entry with sticky or cooldown, creates a record only if none exists yet (`:710-737`).

**`setTimedEffect(type, entry, state)`**: used by slash commands. Deletes the record, then optionally re-creates it, which refreshes the duration (`:744-761`).

**`isEffectActive`** checks by hash (`:777-783`). **`cleanUp`** empties the buffers (`:788-792`).

### 4.7 `checkWorldInfo(chat, maxContext, isDryRun, globalScanData)` (`:4709-5281`)

**Setup**

1. `buffer = new WorldInfoBuffer(chat, globalScanData)` (`:4711`).
   - `globalScanData` = `{personaDescription, characterDescription, characterPersonality, characterDepthPrompt, scenario, creatorNotes, trigger}`, with defaults `''` and `'normal'` (`:186-194`).
   - The caller sets `trigger = type ∈ GENERATION_TYPE_TRIGGERS ? type : 'normal'` (`script.js:4626-4634`).
2. For every `context.extensionPrompts[key]` with `.scan === true`, adds `getExtensionPromptByName(key)` to the inject buffer (`:4719-4726`). This covers the Author's Note when "allow WI scan" is on, the quiet prompt (`script.js:4623`), and other extension injections.
3. Computes the budget: `budget = round(world_info_budget * maxContext / 100) || 1`. If `budget_cap > 0` and the budget exceeds it, the cap is used (`:4736-4741`).
4. Loads `sortedEntries = getSortedEntries()`, creates `timedEffects`, and calls `checkTimedEffects()` (`:4744-4747`). If there are no entries, returns empty results (`:4749-4751`).
5. Builds the recursion delay levels: `availableRecursionDelayLevels` is the sorted, unique set of `delayUntilRecursion` values (`true` counts as 1). `currentRecursionDelayLevel = shift() ?? 0` (`:4754-4759`).
6. Initial state: `scanState = INITIAL`, `allActivatedEntries = Map<"world.uid", entry>`, `failedProbabilityChecks = Set`, `allActivatedText = ''`, `count = 0` (`:4729-4734`).

**Loop `while (scanState)`** (`:4766`). Each pass first breaks if `max_recursion_steps && max_recursion_steps <= count` (`:4768-4771`), then increments `count`.

**Per-entry gates.** Gates are checked in order. The first gate that applies decides: some skip the entry for this pass, some activate it.

| # | Check | Cite |
|---|---|---|
| 1 | Already activated (`world.uid`), or failed probability earlier in this scan → skip. Failed rolls are never re-rolled within one scan. | `:4797` |
| 2 | `disable == true` → skip | `:4801` |
| 3 | `triggers` is non-empty and does not include `globalScanData.trigger` → skip | `:4806-4812` |
| 4 | `characterFilter.names` is non-empty: `included = names contains getCharaFilename()`. Skip if `isExclude ? included : !included`. | `:4815-4823` |
| 5 | `characterFilter.tags` is non-empty: same include/exclude rule, using `context.tagMap[getTagKeyForEntity(this_chid)]` | `:4825-4840` |
| 6 | Delay active → skip | `:4849` |
| 7 | Cooldown active and not sticky → skip | `:4854` |
| 8 | `delayUntilRecursion` set, state ≠ RECURSION, and not sticky → skip | `:4860` |
| 9 | State = RECURSION, `delayUntilRecursion > currentLevel`, and not sticky → skip | `:4865` |
| 10 | State = RECURSION, `world_info_recursive`, `excludeRecursion`, and not sticky → skip | `:4870` |
| 11 | `@@activate` → activate | `:4875` |
| 12 | `@@dont_activate` → skip | `:4881` |
| 13 | Externally activated → activate. Note: the **external object** is added, not the entry. | `:4886-4890` |
| 14 | `constant` → activate | `:4893` |
| 15 | Sticky active → activate | `:4899` |
| 16 | No keys → skip | `:4905` |
| 17 | `textToScan = buffer.get(entry, state)`. The primary keys match if any key, after `substituteParams(key).trim()`, matches. No match → skip. | `:4911-4921` |
| 18 | If `selective` and secondary keys exist, the secondary logic below must also pass. Each secondary key is also passed through `substituteParams`. | `:4924-4985` |

Secondary key logic:

| Logic | Activates when | Cite |
|---|---|---|
| AND_ANY | Any secondary key matches (early return) | `:4955` |
| NOT_ALL | Any secondary key does not match (early return) | `:4959` |
| NOT_ANY | No secondary key matches | `:4966` |
| AND_ALL | All secondary keys match | `:4972` |

**Post-pass**

1. Sort `newEntries`: sticky entries first, then by index in `sortedEntries` (`:4994-5006`).
2. `textToScanTokens = tokens(allActivatedText)` (`:5010`).
3. Apply `filterByInclusionGroups(...)` (`:5012`; see §4.8).
4. Probability and budget loop (`:5017-5077`). For each entry:
   - `ignoresBudget` counts the remaining ignoreBudget entries. Once the budget has overflowed, non-ignoreBudget entries are skipped while ignoreBudget entries remain; when none remain, the loop breaks (`:5019-5026`).
   - Probability passes if `!useProbability || probability === 100`, or if the entry is sticky. Otherwise it rolls `Math.random()*100 <= probability`; a failure is added to `failedProbabilityChecks` (`:5028-5054`).
   - `entry.content = substituteParams(entry.content)`, so macros are resolved at activation time. Then `newContent += content + '\n'` (`:5058-5059`).
   - If `!ignoreBudget && textToScanTokens + tokens(newContent) >= budget`: set `token_budget_overflowed` (with a toast if `overflow_alert`) and do not add the entry (`:5061-5073`).
   - Otherwise `allActivatedEntries.set(world.uid, entry)` (`:5075`).
5. `successfulNewEntries` = `newEntries` minus the failed probability checks. The recursion set additionally excludes `preventRecursion` entries (`:5079-5080`).

**Next-state decision** (`:5097-5133`). Evaluated in order:
1. `world_info_recursive && !overflow && recursionSet.length` → RECURSION.
2. `recursive && !overflow && state === MIN_ACTIVATIONS && buffer.hasRecurse()` → RECURSION.
3. If there is still no next state, the budget has not overflowed, and `min_activations > 0 && activated < min`:
   - `over_max = (depth_max > 0 && depth > depth_max) || depth > chat.length`.
   - If not `over_max` → MIN_ACTIVATIONS, and `buffer.advanceScan()` widens the depth by 1.
4. If the state is still NONE and recursion delay levels remain → RECURSION with `currentRecursionDelayLevel = levels.shift()`.

**Before the next pass**
- If the scan continues, `buffer.addRecurse(join(recursionSet.content, '\n'))` is called and the same text is prepended to `allActivatedText` (`:5137-5145`).
- `WORLDINFO_SCAN_DONE` is emitted, also on dry runs (`:5150-5175`). Payload: `{state:{current,next,loopCount}, new:{all,successful}, activated:{entries,text}, sortedEntries, recursionDelay:{availableLevels,currentLevel}, budget:{current,overflowed}, timedEffects}`. Listeners may mutate `state.next`, `activated.text`, `recursionDelay.currentLevel`, `budget.current` and `budget.overflowed`; these values are read back after the event (`:5179-5187`).

**Vectorized entries.** `checkWorldInfo` has no logic for them. The Vectors extension calls `getSortedEntries()`, queries embeddings for entries marked `vectorized` (or for all entries when `enabled_for_all`), and emits `WORLDINFO_FORCE_ACTIVATE` before generation (`extensions/vectors/index.js:1623-1726`). The listener requires each entry to have `world` and `uid` (`:1020-1029`).

### 4.8 Inclusion groups (`filterByInclusionGroups`, `:5388-5474`)

1. Group `newEntries` by each name in `group.split(/,\s*/)`. An entry can belong to several groups (`:5391-5399`).
2. `filterGroupsByTimedEffects` (`:5337-5378`):
   - If a group has sticky members, keep only those and flag the group as sticky.
   - Otherwise remove members that are on cooldown or delay.
3. `filterGroupsByScoring` (`:5292-5328`):
   - Skipped unless global scoring is on or some member has `useGroupScoring`. Also skipped for sticky groups.
   - Removes members where `(useGroupScoring ?? global)` is set and `getScore` is below the group's maximum score.
4. For each group, the first matching rule applies (`:5418-5473`):
   1. Sticky group → keep all members.
   2. An entry activated in an earlier loop has `x.group === key` (exact string comparison) → remove all members.
   3. One member or fewer → keep.
   4. `groupOverride` members exist → the one with the highest `order` wins.
   5. Otherwise, weighted random: roll `Math.random()*Σ(groupWeight??100)` and pick the first member whose cumulative weight is ≥ the roll.

   When a winner is chosen (rules 4 and 5), every other member is removed from `newEntries`.

Quirk: `removeEntry` calls `splice(indexOf(e), 1)`. If the entry was already removed through another group, `indexOf` returns −1 and the **last** element is removed instead (`:5403`).

### 4.9 Output assembly (`:5191-5281`)

Activated entries are iterated in `sortFn` order (descending `order`) and **unshifted** into their target arrays. Each array therefore ends up in ascending order, with the highest-order entry last, nearest the end of its slot (`:5202`).

Each entry's content is passed through `getRegexedString(content, regex_placement.WORLD_INFO, {depth: atDepth ? depth??4 : null, isMarkdown:false, isPrompt:true})` (`:5204-5205`). Entries whose result is empty are skipped.

| Position | Destination | Cite |
|---|---|---|
| before (0) / after (1) | `WIBeforeEntries` / `WIAfterEntries`, later `join('\n')` into `worldInfoBefore` / `worldInfoAfter` | `:5213-5218`, `:5265-5266` |
| EMTop (5) / EMBottom (6) | `EMEntries.unshift({position: wi_anchor_position.before/after, content})` | `:5219-5228` |
| ANTop (2) / ANBottom (3) | `ANTopEntries` / `ANBottomEntries` | `:5229-5234` |
| atDepth (4) | Merged into `WIDepthEntries` by (depth??4, role??SYSTEM) as `{depth: entry.depth, entries:[...], role}` | `:5235-5246` |
| outlet (7) | `WIOutletEntries[outletName].push(content)`. Skipped with a warning if `outletName` is empty. | `:5248-5259` |

**Author's Note merge.** If `shouldWIAddPrompt` (an authors-note.js flag) is set, the AN extension prompt is replaced with `ANTop.join('\n') + '\n' + originalAN + '\n' + ANBottom.join('\n')`, with a leading or trailing `\n` trimmed. It is written with the chat's AN position, depth, role and `allowWIScan` (`:5268-5272`).

**Finalize.** Calls `setTimedEffects(activated)`, `resetExternalEffects()` and `cleanUp()` (`:5274-5276`).

**Return values**
- `checkWorldInfo` returns `{worldInfoBefore, worldInfoAfter, EMEntries, WIDepthEntries, ANBeforeEntries, ANAfterEntries, outletEntries, allActivatedEntries:Set}` (`:5281`).
- `getWorldInfoPrompt(chat, maxContext, isDryRun, globalScanData)` (`:892-915`) returns `{worldInfoString: before+after, worldInfoBefore, worldInfoAfter, worldInfoExamples: EMEntries, worldInfoDepth, anBefore, anAfter, outletEntries}`. It emits `WORLD_INFO_ACTIVATED(entries[])` only when the run is not a dry run and at least one entry activated (`:901-904`).

**Consumer in Generate** (`script.js:4624-4677`):
- `chatForWI = coreChat.map(include_names ? "name: mes" : mes).reverse()`, and `this_max_context` is passed as `maxContext`.
- EM entries are parsed with `parseMesExamples`, then unshifted (before) or pushed (after) into the examples (`:4638-4655`).
- Depth entries become `setExtensionPrompt(CUSTOM_WI_DEPTH_ROLE(depth, role), join('\n'), IN_CHAT, depth, false, role)`.
- Outlets become `setExtensionPrompt(customWIOutlet_<key>, join('\n'), NONE, 0)` and are read by the `{{outlet::key}}` macro (`macros.js:597`, `:668`; `constants.js:55`).
- `worldInfoBefore` / `worldInfoAfter` go into the story string as `wiBefore` / `loreBefore` and `wiAfter` / `loreAfter` (`script.js:4711-4713`).

### 4.10 Events

| Event | When / payload | Cite |
|---|---|---|
| `WORLDINFO_ENTRIES_LOADED` | Every `getSortedEntries`; payload `{globalLore, characterLore, chatLore, personaLore}` (mutable arrays) | `:4603`, `events.js:97` |
| `WORLDINFO_SCAN_DONE` | After each scan loop; payload described in §4.7 | `:5175`, `events.js:98` |
| `WORLD_INFO_ACTIVATED` | After a non-dry scan with at least one activation; payload is an array of entries | `:903`, `events.js:62` |
| `WORLDINFO_FORCE_ACTIVATE` | Listened to (not emitted here): `entries[]` with `world` and `uid`, applied on the next scan | `:1020-1029`, `events.js:77` |
| `WORLDINFO_UPDATED` | After each `/edit` save; `(name, data)` | `:4162`, `events.js:42` |
| `WORLDINFO_SETTINGS_UPDATED` | Global selection or setting changes | `:5846`, `:6228`, `events.js:41` |

### 4.11 Persistence and file format

**File format.** `<user>/worlds/<name>.json` contains `{ entries: { "<uid>": entry, ... }, originalData?: <V2 character_book>, name?, extensions? }`. The server writes it pretty-printed with 4-space indentation, using an atomic write (`src/endpoints/worldinfo.js:150`). An empty book is `{entries:{}}` (`:4449`).

**Endpoints** (all POST)

| Endpoint | Client usage | Server behaviour |
|---|---|---|
| `/api/worldinfo/get {name}` | `loadWorldInfo` (`:2036-2059`) | Returns the file, or `{entries:{}}` if it is missing (`worldinfo.js:71-79`) |
| `/api/worldinfo/edit {name, data}` | `_save` (`:4151-4163`) | Requires `data.entries`; writes `sanitize(name).json` (`worldinfo.js:134-153`) |
| `/api/worldinfo/delete {name}` | `deleteWorldInfo` (`:4346-4393`) | Unlinks the file (`worldinfo.js:81-97`) |
| `/api/worldinfo/import` (multipart: `avatar` = file, optional `convertedData`) | `importWorldInfo` (`:5850-5930`) | Uses `convertedData` if given, otherwise the raw file; requires `entries`; saves as `<originalname>.json`; returns `{name}` (`worldinfo.js:99-132`) |
| `/api/worldinfo/list` | Not called from world-info.js | Returns `[{file_id, name, extensions}]` (`worldinfo.js:39-69`) |
| `/api/settings/get` | `updateWorldInfoList` reads `world_names` (`:2061-2084`) | — |
| `/api/characters/merge-attributes` | Book rename relinks the character's primary book (`:4296-4308`) | — |

**Cache.** `worldInfoCache = StructuredCloneMap({cloneOnGet:true, cloneOnSet:false})` (`:882`).
- `loadWorldInfo` reads through the cache.
- `saveWorldInfo(name, data, immediately)` updates the cache immediately. It then calls `_save` either right away or through a 1000 ms debounce (`saveWorldDebounced`, `debounce_timeout.relaxed`) (`:83`, `:4177-4190`, `constants.js:14`).
- `deleteWorldInfo` evicts the book from the cache. It also removes the book from the global selection, unsets the character's world select, and clears the persona lorebook link (`:4363-4390`).

**Book and entry operations**

| Operation | Behaviour | Cite |
|---|---|---|
| Create | `createNewWorldInfo`: sanitizes the name and checks for overwrite | `:4448-4473` |
| Rename | Saves under the new name, deletes the old book, then runs `updateWorldInfoLinks`. That retargets charLore extraBooks, the active and other personas' lorebooks, the chat_metadata key, and (after confirmation) characters' primary `extensions.world`. | `:4192-4344` |
| Duplicate | Saves under `getFreeWorldName` ("Name (n)") | `:2628-2645`, `:4422-4440` |
| Export | Downloads `JSON.stringify(data)` as `<name>.json` | `:2620-2626` |
| Delete book | Also strips the book from charLore | `:2330-2354` |
| Entry duplicate / delete / move / copy | Move and copy give the entry a new uid in the target book with `displayIndex = max+1`; the entry is removed from the source's originalData | `:4019-4074`, `:6000-6090` |
| Backfill memos | Sets comment = keys joined, sliced to 100 chars | `:2479-2494` |
| Apply current sorting | Rewrites `order` | `:2496-2618` |
| Editor sort | Modes: search, custom (displayIndex), priority (constant < normal < disabled), or by field. Ties: `order` descending, then `uid` ascending. | `:2146-2210` |

**Linking**

| Link | Function | Behaviour | Cite |
|---|---|---|---|
| Chat book | `assignLorebookToChat` | Sets or deletes `chat_metadata.world_info`, then `saveMetadata` | `:5951-5992` |
| Character primary | `charUpdatePrimaryWorld` | Unsetting the book also removes the embedded `character_book` | `:6097-6127` |
| Character extra | `charUpdateAddAuxWorld` / `charSetAuxWorlds` | Writes `world_info.charLore`; in create mode uses `create_save.extra_books` | `:6134-6173` |
| Global toggle | `onWorldInfoChange` | Adds or removes the book from the global selection | `:5772-5848` |

**Import formats** (`importWorldInfo`, `:5850-5930`). A `.png` file is read with `extractDataFromPng(buf, 'naidata')`; anything else is parsed as JSON. The format is then detected:

| Detect | Converter | Mapping highlights |
|---|---|---|
| `lorebookVersion !== undefined` (NovelAI) | `convertNovelLorebook` `:5567-5615` | key=`keys`, content=`text`, comment=`displayName`, order=`contextConfig.budgetPriority??0`, disable=`!enabled`, position 0 |
| `kind === 'memory'` (Agnai) | `convertAgnaiMemoryBook` `:5477-5520` | key=`keywords`, comment=`name`, content=`entry`, order=`weight`, disable=`!enabled` |
| `type === 'risu'` (RisuAI) | `convertRisuLorebook` `:5522-5565` | key and secondkey comma-split; constant=`alwaysActive`; order=`insertorder`; probability=`activationPercent??100`; position before |
| otherwise | native ST JSON | Uploaded as-is; the server requires `entries` |
| Embedded V2 `character_book` | `convertCharacterBook` `:5617-5674` | uid=`entry.id` (or array index); maps keys, secondary_keys, insertion_order, enabled; position=`extensions.position ?? (position==='before_char'?0:1)`; all `extensions.*` fields per the §4.3 table; keeps `originalData` and `extensions` |

For embedded books:
- `checkEmbeddedWorld` shows a one-time prompt per avatar if the character has no linked book. The "already shown" flag is stored as `AlertWI_<avatar>` (`:5691-5729`).
- `importEmbeddedWorldInfo` saves the book as `character_book.name || "<char>'s Lorebook"` and links it as the primary book (`:5731-5770`).

### 4.12 Slash commands (registered in `registerWorldInfoSlashCommands`, `:1049-2010`)

| Command (aliases) | Args | Behaviour | Cite |
|---|---|---|---|
| `/world` | `state=on\|off\|toggle`, `silent`, unnamed book name(s) (comma-separated, case-insensitive) | Activates, deactivates or toggles global books. With no name, deactivates all. `on` can add a book that is already selected, creating a duplicate. | `:1617-1640`, `:5772-5821` |
| `/getchatbook` (`getchatlore`, `getchatwi`) | `name`, `create` (default: creates) | Returns the chat-bound book. If none is bound, creates and binds "Chat Book <chatId>" unless `create=false`. | `:1642-1665`, `:1159-1182` |
| `/getglobalbooks` (`getgloballore`, `getglobalwi`) | — | JSON array of the selected global books | `:1667-1672`, `:1604-1614` |
| `/getpersonabook` (`getpersonalore`, `getpersonawi`) | `name`, `create=false` | Returns the persona book name; optionally creates "Persona Book <user>" | `:1674-1698`, `:1091-1107` |
| `/getcharbook` (`getcharlore`, `getcharwi`) | `type=primary\|additional\|all`, `name`, `create`, unnamed character name or avatar | `primary` returns a string; the other types return a JSON array. Can create and link a book. Fails in groups unless a character name is given. | `:1700-1738`, `:1115-1152` |
| `/findentry` (`findlore`, `findwi`) | `file`, `field=key`, text | Fuse fuzzy search (threshold 0.3); returns the first match's uid | `:1741-1779`, `:1206-1248` |
| `/getentryfield` (`getlorefield`, `getwifield`) | `file`, `field=content`, uid | Returns the field value, macro-substituted; arrays are returned as JSON. Supports the characterFilter* fields. | `:1781-1822`, `:1250-1311` |
| `/createentry` (`createlore`, `createwi`) | `file`, `key`, unnamed content | Creates an entry and returns its uid. When `key` is given, the comment is set to the key and addMemo is turned on. | `:1824-1858`, `:1313-1342` |
| `/setentryfield` (`setlorefield`, `setwifield`) | `file`, `uid`, `field=content`, value | Typed set: arrays go through `parseStringArray` + `arrayFilter`; booleans and numbers are coerced; `\{ \} \|` are unescaped; originalData is kept in sync | `:1860-1904`, `:1344-1438` |
| `/wi-set-timed-effect` | `file`, `uid`, `effect=sticky\|cooldown`, `on\|off\|toggle` | Requires an open chat, and the entry must have the effect configured. Calls `saveMetadata`. | `:1906-1955`, `:1481-1547` |
| `/wi-get-timed-effect` | `file`, `effect`, `format=bool\|number`, uid | Returns whether the effect is active, or the remaining count `end − chat.length` | `:1957-2008`, `:1440-1479` |

### 4.13 Parity quirks to replicate

- An effective scan depth of 0 produces an empty haystack, which also disables the `match*` global-field scanning (`:280-283`).
- Regex keys ignore the case and whole-word settings and are tested against the buffer without lowercasing (`:339-342`).
- Whole-word mode uses ASCII `\W`, so in CJK text a key can match inside surrounding non-ASCII letters (`:356`).
- The budget check compares `tokens(allActivatedText) + tokens(this loop's newContent)` against the budget. `allActivatedText` only holds the recursion-eligible text from loops that continued, not the full activated set (`:5010`, `:5061`, `:5141-5143`).
- The timed-effect hash covers the whole entry JSON, so editing an entry orphans its active sticky or cooldown record (`:4630-4633`, `:626`).
- The `getWiPositionString` helper has no label for outlet and shows `<Unknown>` (`:543-554`).
- Deleting an entry from a book does not remove anything from charLore links; deleting a whole book does (`:2336-2350`).

---

## 5. Macros & Variables

> Path convention: unless prefixed, paths are relative to `public/scripts/` (e.g. `macros/definitions/core-macros.js:123`). `script.js` = `public/script.js`. `droll`/`seedrandom` refer to `node_modules/`.

### 5.1 Engine selection & entry points

| Item | Behavior | Ref |
|---|---|---|
| Flag | `power_user.experimental_macro_engine`, **default `true`** in 1.19 (new engine is the default; legacy is the fallback) | `power-user.js:302` |
| UI toggle | `#experimental_macro_engine` checkbox → sets flag, saves settings | `power-user.js:1670`, `power-user.js:3874-3876` |
| Init | `initMacros()` called on settings load; if legacy, registers `lastGenerationType`/`isMobile` through `MacrosParser`; always calls `initRegisterMacros()` (fills new registry) | `script.js:8020`, `macros.js:717-747` |
| Registration order (new) | core → env → state → chat → time → variables → instruct | `macros/macro-system.js:66-85` |
| Public API object | `macros = {engine, registry, envBuilder, lexer, parser, cstWalker, category, valueType, register, registerAlias}` | `macros/macro-system.js:44-60` |
| `substituteParams(content, options)` | New signature (options object: `name1Override, name2Override, original, groupOverride, replaceCharacterCard=true, dynamicMacros={}, postProcessFn`). If flag is off → `substituteParamsLegacy`. If on → `MacroEnvBuilder.buildFromRawEnv(ctx)` then `MacroEngine.evaluate(content, env)` | `script.js:2981-3015` |
| Positional (legacy) call signature | `substituteParams(content, name1, name2, original, group, replaceCharacterCard, additionalMacro, postProcessFn)` is detected (non-object 2nd arg) and routed to `substituteParamsLegacy`, which re-routes to the new engine when the flag is on | `script.js:2991-2994`, `script.js:2831-2847` |
| `substituteParamsExtended` | Deprecated wrapper → `substituteParams(content,{dynamicMacros,postProcessFn})` | `script.js:2815-2817` |
| Onboarding sniffing (legacy only) | Regex-detects new-engine features (`{{if`, `{{/`, flags `[!?~#/]`, shorthands `[.$]`, nested `{{..{{`, `greeting`/`charFirstMessage`) and shows an onboarding prompt | `script.js:2852-2862` |
| Card fields pre-substitution | Card fields (description, personality, scenario, persona, mesExamples, system, jailbreak, depth prompt, creator notes, greetings) are already passed through `baseChatReplace` = `substituteParams(value,{replaceCharacterCard:false})` + optional newline-collapse + strip `\r` | `script.js:3341-3351`, `script.js:3402-3466` |
| Story string | Rendered via Handlebars (`noEscape`) first, then `substituteParams(output, user, char)`. Handlebars helpers: `trim` → re-emits `{{trim}}`; `helperMissing` → `substituteParams('{{name}}')` | `power-user.js:2245-2251`, `macros.js:19-25` |
| Legacy extension API | `MacrosParser.registerMacro/unregisterMacro/has/get` are deprecated; when the flag is on they bridge into the new registry as 0-arg macros (`category:'legacy'`, function values called with a uuid nonce) | `macros.js:42-308`, `macros.js:81-117` |

### 5.2 Legacy engine (regex-based)

**Environment build** (`substituteParamsLegacy`, `script.js:2864-2960`). Insertion order matters because env regexes run in key order:
`original` (one-shot fn: returns value first time, `''` after) `script.js:2866-2876` → if `replaceCharacterCard`: `charPrompt`, `charInstruction`=`charJailbreak`, `description`, `personality`, `scenario`, `persona`, `mesExamples` (fn; instruct-formatted when `instruct.enabled && main_api!=='openai'`), `mesExamplesRaw`, `charVersion`, `char_version`, `charDepthPrompt`, `creatorNotes` `script.js:2924-2946` → `user`, `char`, `group`=`charIfNotGroup` (incl. muted), `groupNotMuted`, `notChar`, `model` `script.js:2949-2954` → `additionalMacro` keys merged `script.js:2956-2958` → `MacrosParser`-registered macros appended by `populateEnv` `macros.js:258-272`, `macros.js:676`.

**`evaluateMacros(content, env, postProcessFn)`** (`macros.js:610-715`): builds `[...preEnv, ...env, ...postEnv]` and runs `content.replace(regex, …)` for each macro **sequentially** (all occurrences of one macro, then the next). Output of earlier passes **is re-scanned** by later passes (e.g. a `getvar` value containing `{{user}}` gets substituted by the later env pass). Loop **breaks early** when no `{{` remains (except `<…>` regexes) `macros.js:702-705`. Each replacement is wrapped by `postProcessFn` `macros.js:708`. Env values: functions called with a uuid nonce, then `sanitizeMacroValue` (null→'', Promise/function→'', Date→ISO, object→JSON) `macros.js:684-689`, `macros.js:279-307`. Env regex is `{{escaped(key)}}` with flags `gi` (case-insensitive) `macros.js:684`. Unknown macros are left verbatim. No nesting support.

| Phase | # | Regex / macro | Ref |
|---|---|---|---|
| pre-env | 1-5 | `<USER>`→user, `<BOT>`/`<CHAR>`→char, `<CHARIFNOTGROUP>`/`<GROUP>`→group (case-insens.) | `macros.js:624-628` |
| | 6 | `{{roll[ : ](…)}}` (space or single colon; `{{roll::x}}` fails) | `macros.js:629`, `macros.js:550-572` |
| | 7 | instruct/system/context macros (`{{(a\|b)}}` alternation) | `macros.js:630`, `instruct-mode.js:673-785` |
| | 8 | variable macros (`setvar`,`addvar`,`incvar`,`decvar`,`getvar` + global versions) | `macros.js:631`, `variables.js:238-261` |
| | 9-12 | `{{newline}}`→`\n`; `(\r?\n)*{{trim}}(\r?\n)*`→''; `{{noop}}`→''; `{{input}}`→`#send_textarea` value | `macros.js:632-635` |
| env | — | one regex per env key (order above) | `macros.js:681-692` |
| post-env | 1-6 | `maxPrompt`,`maxPromptTokens`,`maxContext`,`maxContextTokens`,`maxResponse`,`maxResponseTokens` | `macros.js:643-648` |
| | 7-14 | `lastMessage`,`lastMessageId`,`lastUserMessage`,`lastCharMessage`,`firstIncludedMessageId`,`firstDisplayedMessageId`,`lastSwipeId`,`currentSwipeId` | `macros.js:649-656` |
| | 15-17 | `allChatRange`; `{{reverse:(.+?)}}` (single colon); comment `\{\{\/\/([\s\S]*?)\}\}`→'' | `macros.js:657-659` |
| | 18-24 | `time`(LT), `date`(LL), `weekday`(dddd), `isotime`(HH:mm), `isodate`(YYYY-MM-DD), `{{datetimeformat +(fmt)}}` (space only), `idle_duration` | `macros.js:660-666` |
| | 25-26 | `{{time_UTC([-+]\d+)}}` → `moment().utc().utcOffset(N).format('LT')`; `{{outlet::key}}` | `macros.js:667-668` |
| | 27-30 | `{{timeDiff::a::b}}`; `{{banned "(.*)"}}` (quoted form only); `{{random…}}`; `{{pick…}}` | `macros.js:669-672` |

Legacy variable-macro regexes (no nesting; name excludes `:`, value stops at first `}`): `{{setvar::([^:]+)::([^}]*)}}`, `{{addvar::([^:]+)::([^}]+)}}`, `{{incvar::([^}]+)}}` (returns new value), `{{decvar::…}}`, `{{getvar::…}}`, and `setglobalvar/addglobalvar/incglobalvar/decglobalvar/getglobalvar` equivalents; names are `.trim()`ed `variables.js:241-259`.

### 5.3 New engine architecture (Chevrotain)

| Component | Role | Ref |
|---|---|---|
| `MacroLexer` | Multi-mode lexer: `plaintext`, `macro_def`, `macro_identifier_end`, `macro_args`, `var_identifier`, `var_after_identifier`, `var_value` (filter modes defined but pipe tokens **disabled**) | `macros/engine/MacroLexer.js:29-40`, `:175-299`, `:217-226` |
| `MacroParser` | CST rules: `document` (plaintext \| macro \| stray `{{` as plaintext), `macro` = `{{` flags* (variableExpr \| macroBody) `}}`, `macroBody` = (`//` \| Identifier) arguments?, `arguments` = `::`-separated list **or** optional `:` + one colon-tolerant argument; error recovery enabled | `macros/engine/MacroParser.js:25-29`, `:35-184` |
| `MacroCstWalker` | Walks CST, pairs scoped open/close tags, evaluates args (inside-out) then calls resolver; evaluates variable shorthand directly | `macros/engine/MacroCstWalker.js:93-153`, `:377-513`, `:526-868`, `:1182-1373` |
| `MacroRegistry` | Case-insensitive map (keys lowercased); aliases are separate entries pointing to same def; arity + type validation; builds execution context | `macros/engine/MacroRegistry.js:175-461`, `:301-319` |
| `MacroEngine` | `evaluate()`: freeze env → pre-processors → parse → walk → post-processors; resolves dynamic macros before registry | `macros/engine/MacroEngine.js:117-159`, `:167-238` |
| `MacroEnvBuilder` | Builds `MacroEnv {content, contentHash, names, character(lazy getters), system.model, functions{original,postProcess}, dynamicMacros(lowercased), extra}`; ordered provider hooks (EARLIEST 0…LATEST 100) | `macros/engine/MacroEnvBuilder.js:36-42`, `:81-175`; type `macros/engine/MacroEnv.types.js:56-66` |
| `MacroFlags` | Flag parsing | `macros/engine/MacroFlags.js:27-198` |
| `MacroDiagnostics` | Warn/error loggers, runtime-error factory, onboarding mutex | `macros/engine/MacroDiagnostics.js:33-157` |
| `MacroBrowser` | Docs/autocomplete UI (category order, signatures, badges) | `macros/engine/MacroBrowser.js:14-25`, `:33`, `:308-470` |

**Evaluation pipeline** (`MacroEngine.evaluate`, `macros/engine/MacroEngine.js:117-159`):
1. Pre-processors (priority-sorted, lower first) `:58-61`, `:247-253`: p10 `{{time_UTC±N}}`→`{{time::UTC±N}}` `:278-281`; p20 `<USER>`→`{{user}}`, `<BOT>`/`<CHAR>`→`{{char}}`, `<GROUP>`→`{{group}}`, `<CHARIFNOTGROUP>`→`{{charIfNotGroup}}` `:285-293`.
2. Parse; lexing/parse errors are logged, evaluation continues; invalid CST → return original input `:125-139`.
3. Walk document left→right; scoped pairs merged; for each macro, args are evaluated first (each arg re-parsed as a sub-document, so nested + scoped macros work in args) `MacroCstWalker.js:421-436`, `:882-972`; then resolver. **Macro output is not re-scanned** (single pass) `MacroCstWalker.js:509-512` — only handlers that call `resolve()` re-evaluate.
4. Resolver: dynamic macro (string \| function \| options object w/ `handler`) overrides registered `MacroEngine.js:173-213`; unknown name → returns `{{rawInner}}` with nested macros already resolved `:216-217`; result passed through `env.functions.postProcess` `:224`; any thrown error → raw text left in place `:229-236`.
5. Post-processors `MacroEngine.js:305-322`: p10 unescape `\{`→`{`, `\}`→`}`; p20 legacy trim `(\r?\n)*{{trim}}(\r?\n)*`→''; p30 strip leftover `ELSE_MARKER`.
6. Result normalization: null/undefined→'', Date→ISO, object/array→JSON, else `String()` `MacroEngine.js:332-348`, applied in `MacroRegistry.js:460`.

### 5.4 New-engine syntax

| Feature | Syntax / rule | Ref |
|---|---|---|
| Identifier | `[a-zA-Z][\w-_]*` (letters, digits, `_`, `-`); `//` also valid | `MacroLexer.js:8,17`, `MacroRegistry.js:693-697` |
| Case | Macro names case-insensitive (registry keys lowercased; dynamic macros lowercased) | `MacroRegistry.js:302,354`, `MacroEnvBuilder.js:156-159` |
| Whitespace | Allowed around flags/name/args: `{{ user }}`; arg text = slice from first to last token, so leading/trailing whitespace of each arg is dropped | `MacroLexer.js:198,234`, `MacroCstWalker.js:1068-1103` |
| Args | `{{name::a::b::c}}` (list); `{{name arg}}` or `{{name:arg}}` = **one** argument that may contain `:`/`::` | `MacroParser.js:136-160`, `:177-184` |
| Nesting | Any arg / scoped body / variable value may contain macros: `{{setvar::x::{{user}}}}` | `MacroLexer.js:215,288`, `MacroParser.js:164` |
| Named args | **Not supported** — `namedArgs` always `null` (reserved) | `MacroRegistry.js:110,433` |
| Optional/list args | `unnamedArgs` defs (optional must be suffix), `list:true\|{min,max}` collects extra args after positional ones | `MacroRegistry.js:532-596`, `:424-427` |
| Arity/type check | `strictArgs` default true → mismatch throws runtime error → macro left raw; types `string\|integer(^-?\d+$)\|number(finite)\|boolean(true/false/on/off/1/0)` | `MacroRegistry.js:405-422`, `:598-602`, `:736-788` |
| Flags (prefix, before name) | `/` closing block ✔ implemented; `#` preserve whitespace in scoped content ✔ (also Handlebars-compat `{{#if}}`); `!` immediate, `?` delayed, `~` re-evaluate, `>` filter/pipe — **parsed but not implemented** | `MacroLexer.js:63-68`, `MacroFlags.js:27-136`, `MacroParser.js:52-57` |
| Scoped blocks | `{{name args}}body{{/name}}` → body appended as **last** unnamed arg; matched by same name (case-insens.); only outermost pairs at each level (inner ones handled when body re-parsed); opener only pairs if `args+1` fits `[minArgs,maxArgs]` and macro is not `list`; unknown macros may be scoped; unmatched closers stay raw | `MacroCstWalker.js:1182-1298`, `:1308-1334`, `:1344-1373` |
| Scoped trim | Body auto-trimmed + dedented by first non-empty line's indent unless `#` flag | `MacroCstWalker.js:454-457`, `MacroEngine.js:372-413` |
| Comments | `{{// text}}` → ''; alias `{{comment …}}` (hidden); multiline scoped `{{//}}…{{///}}` (`{{///}}` special-cased as closing tag). Note: nested macros inside a comment arg **are evaluated** (side-effects run) | `core-macros.js:282-299`, `MacroCstWalker.js:194-206` |
| Escaping | `\{\{…\}\}` is not lexed as a macro; backslashes removed in post-processing | `MacroLexer.js:48`, `MacroEngine.js:305-308` |
| Malformed | Missing `}}` (recovery token) → macro flattened to plaintext, complete nested macros still evaluated; lone `{{` kept as text | `MacroCstWalker.js:343-347`, `:1002-1050`, `MacroParser.js:41` |
| if/else | see `if` row in §5.5; `{{else}}` splits at top nesting depth (only 1-arg scoped `{{if}}` increases depth) | `core-macros.js:104-128`, `:134-213`, `:217-225` |
| `delayArgResolution` | Args/body passed raw; handler calls `resolve(text,{offsetDelta})` (keeps `globalOffset`) | `MacroRegistry.js:74`, `:453-455`, `MacroCstWalker.js:410-412,426-427,448-452` |

**Variable shorthand** (`{{.name…}}` local, `{{$name…}}` global; identifier `[a-zA-Z](?:[\w\-_]*[\w])?` — cannot end in `-`) `MacroLexer.js:26,102-147`, `MacroParser.js:81-133`, evaluated in `MacroCstWalker.js:526-798` via `SillyTavern.getContext().variables.{local|global}` `:657-658`. Value expression is lazy (evaluated at most once, only when needed) `:634-645`; value text trimmed `:808-868`.

| Op | Effect / return | Ref |
|---|---|---|
| (none) | get → normalized value | `MacroCstWalker.js:675-676` |
| `++` / `--` | inc/dec, returns new value | `:682-686` |
| `= v` | set, returns '' | `:678-680` |
| `+= v` | `add` (numeric add / string append / array push), '' | `:688-690` |
| `-= v` | add `-Number(v)`; non-numeric → warning, no-op | `:692-698` |
| `\|\| v` | var if truthy else v (falsy = empty or `off/false/0`) | `:672,700-705` |
| `?? v` | var if exists else v | `:707-712` |
| `\|\|= v` / `??= v` | assign v if falsy / missing; return resulting value | `:714-734` |
| `== v` / `!= v` | string compare of normalized values → `'true'/'false'` | `:736-748` |
| `> >= < <=` | numeric compare; NaN → warning + `'false'` | `:750-792` |

### 5.5 Complete macro table (new engine registry; legacy availability noted)

Legend: L = also in legacy engine (ref), — = new engine only.

**Core / utility / random** (`macros/definitions/core-macros.js`)

| Macro | Aliases | Args | Semantics | Def | Legacy |
|---|---|---|---|---|---|
| `space` | — | `[count:int=1]` | `' '.repeat(count)` | `:32-47` | — |
| `newline` | — | `[count:int=1]` | `'\n'.repeat(count)` | `:50-65` | L `macros.js:632` (no arg) |
| `noop` | — | 0 | '' | `:68-73` | L `macros.js:634` |
| `trim` | — | `[content]` | Non-scoped: emits `{{trim}}` then post-processor deletes it + surrounding newlines; scoped `{{trim}}x{{/trim}}` → trimmed content | `:77-94`, `MacroEngine.js:313-316` | L `macros.js:633` |
| `if` | — | `condition, content` (content usually scoped); delayArgResolution | Leading `!` inverts; condition resolved; if `.x`/`$x` → getvar/getglobalvar; if bare name of a registered macro with minArgs 0 → resolved (`{{if description}}`); falsy = `''` or `off/false/0`; only chosen branch is resolved; trimmed unless `#` | `:134-213` | — |
| `else` | — | 0 | Returns `ELSE_MARKER` (`\u0000\u001FELSE\u001F\u0000`), consumed by `if`, stripped if stray | `:20`, `:217-225` | — |
| `input` | — | 0 | Current chat input box text | `:228-233` | L `macros.js:635` |
| `maxPrompt` | `maxPromptTokens` | 0 | `getMaxPromptTokens()` | `:236-243` | L `macros.js:643-644` |
| `maxContext` | `maxContextTokens` | 0 | `getMaxContextTokens()` | `:246-253` | L `macros.js:645-646` |
| `maxResponse` | `maxResponseTokens` | 0 | `getMaxResponseTokens()` | `:256-263` | L `macros.js:647-648` |
| `reverse` | — | `value` | Reverse by code points (`Array.from`) | `:266-279` | L `macros.js:658` (`{{reverse:x}}`) |
| `//` | `comment` (hidden) | `comment` | '' | `:282-299` | L `macros.js:659` |
| `roll` | — | `formula` | Digits-only → `1dN`; droll grammar `^([1-9]\d*)?d([1-9]\d*)([+-]\d+)?$`; `Math.random` per die; returns total; invalid → '' + warning | `:303-337`; `droll/droll.js:62,88-107` | L `macros.js:550-572` |
| `random` | — | list | Single arg → split on `::` (trimmed) else on `,` (with `\,` escape, trimmed); uniform pick via `seedrandom(…,{entropy:true})` (re-rolls every eval) | `:340-360`, `:410-422` | L `macros.js:491-509` |
| `pick` | — | list | Same list parsing; deterministic (see §5.6) | `:363-407` | L `macros.js:516-545` |
| `banned` | — | `word` | Strips surrounding quotes; if `main_api==='textgenerationwebui'` push to `textgenerationwebui_banned_in_macros`; returns '' | `:425-447` | L `macros.js:442-453` (only `{{banned "w"}}`) |
| `outlet` | — | `key` | `extension_prompts['customWIOutlet_'+key].value` or '' | `:450-468`, `constants.js:55` | L `macros.js:597-600,668` |

**Names / character / state** (`macros/definitions/env-macros.js`, `state-macros.js`)

| Macro | Aliases | Args | Semantics | Def | Legacy |
|---|---|---|---|---|---|
| `user` | — | 0 | `name1Override ?? name1` | `env-macros.js:16-21`, `MacroEnvBuilder.js:133` | L `script.js:2949` |
| `char` | — | 0 | `name2Override ?? name2` | `env-macros.js:23-28`, `MacroEnvBuilder.js:134` | L `script.js:2950` |
| `group` | `charIfNotGroup` (hidden) | 0 | groupOverride, else all member names incl. muted joined `, `; solo → char | `env-macros.js:30-36`, `MacroEnvBuilder.js:135,189-211` | L `script.js:2878-2895,2951` |
| `groupNotMuted` | — | 0 | as above excluding `disabled_members` | `env-macros.js:38-43`, `MacroEnvBuilder.js:136` | L `script.js:2952` |
| `notChar` | — | 0 | Solo → user. Group → members except current speaker. **Diff:** legacy appends user (`script.js:2918-2921`); new does not, and new returns `groupOverride` if given | `env-macros.js:45-50`, `MacroEnvBuilder.js:137,194` | L `script.js:2897-2922` |
| `charPrompt` | — | 0 | Card main-prompt override (`chat_metadata.system_prompt \|\| data.system_prompt`) only if `prefer_character_prompt` | `env-macros.js:53-58`, `script.js:3413-3417` | L `script.js:2926` |
| `charInstruction` | — | 0 | Post-history instructions if `prefer_character_jailbreak` | `env-macros.js:60-65`, `script.js:3418-3421` | L `script.js:2927` (+`charJailbreak`, **missing in new**) |
| `charDescription` | `description` | 0 | Description (group-card override aware) | `env-macros.js:67-73` | L `description` `script.js:2928` |
| `charPersonality` | `personality` | 0 | Personality | `env-macros.js:75-81` | L `script.js:2929` |
| `charScenario` | `scenario` | 0 | `chat_metadata.scenario \|\| card scenario` | `env-macros.js:83-89`, `script.js:3442-3447` | L `script.js:2930` |
| `persona` | — | 0 | Persona description | `env-macros.js:91-96` | L `script.js:2931` |
| `mesExamplesRaw` | — | 0 | Raw example dialogue (`chat_metadata.mes_example \|\| card`) | `env-macros.js:98-103` | L `script.js:2941` |
| `mesExamples` | — | 0 | `parseMesExamples`; instruct-formatted (`formatInstructModeExamples`) when instruct on and `main_api!=='openai'` | `env-macros.js:105-126` | L `script.js:2932-2940` |
| `charDepthPrompt` | — | 0 | `data.extensions.depth_prompt.prompt` | `env-macros.js:128-133` | L `script.js:2944` |
| `charCreatorNotes` | `creatorNotes` | 0 | Creator notes | `env-macros.js:135-141` | L `creatorNotes` `script.js:2945` |
| `charFirstMessage` | `greeting` | `[index:int=0]` | 0 → `first_mes`; n≥1 → `alternate_greetings[n-1]`; OOB → '' | `env-macros.js:143-165` | — |
| `charVersion` | `version`, `char_version` (hidden) | 0 | `data.character_version` | `env-macros.js:168-177` | L `charVersion`,`char_version` `script.js:2942-2943` |
| `model` | — | 0 | `getGeneratingModel()` | `env-macros.js:180-185` | L `script.js:2954` |
| `original` | — | 0 | One-shot: first occurrence returns `original` option, later ''; if not supplied, handler throws → stays `{{original}}` | `env-macros.js:187-195`, `MacroEnvBuilder.js:144-151` | L `script.js:2866-2876` |
| `isMobile` | — | 0 | `"true"/"false"` | `env-macros.js:198-204` | L via MacrosParser `macros.js:738-741` |
| `lastGenerationType` | — | 0 | Type from last non-dry-run `GENERATION_STARTED` (`normal`,`swipe`,`continue`,`impersonate`,`regenerate`,`quiet`…); reset to '' on `CHAT_CHANGED` | `state-macros.js:5-40` | L `macros.js:720-736` |
| `hasExtension` | — | `extensionName` | `String(findExtension(name)?.enabled ?? false)` | `state-macros.js:43-56` | — |

**Chat** (`macros/definitions/chat-macros.js`; legacy helpers `macros.js:340-435`)

| Macro | Semantics | Def | Legacy |
|---|---|---|---|
| `lastMessage` | `.mes` of last message, skipping one whose `swipe_id >= swipes.length` (swipe in progress) | `:9-14`, `:83-106` | L `macros.js:649` |
| `lastMessageId` | Index of that message ('' if none) | `:16-22` | L `macros.js:650` |
| `lastUserMessage` | Last `is_user && !is_system` | `:24-29`, `:108-111` | L `macros.js:651` |
| `lastCharMessage` | Last `!is_user && !is_system` | `:31-36`, `:113-116` | L `macros.js:652` |
| `firstIncludedMessageId` | `chat_metadata.lastInContextMessageId` | `:38-44`, `:118-121` | L `macros.js:653` |
| `firstDisplayedMessageId` | `mesid` of first rendered `#chat .mes` (Android: first rendered message index) | `:46-52`, `:123-130` | L `macros.js:654` |
| `lastSwipeId` | `swipes.length` of last message (in-progress swipe included) | `:54-60`, `:132-139` | L `macros.js:655` |
| `currentSwipeId` | `swipe_id + 1` (1-based) | `:62-68`, `:141-148` | L `macros.js:656` |
| `allChatRange` | `0-{len-1}` or '' | `:70-80` | L `macros.js:657` |

**Time** (`macros/definitions/time-macros.js`, moment.js)

| Macro | Aliases | Args | Semantics | Def | Legacy |
|---|---|---|---|---|---|
| `time` | — | `[offset]` `UTC±N` | `format('LT')`; with `UTC±N` → `utc().utcOffset(N)` (hours); bad spec → local | `:11-39` | L `macros.js:660`, `{{time_UTC±N}}` `:667` (new: pre-processed) |
| `date` | — | 0 | `LL` | `:41-46` | L `macros.js:661` |
| `weekday` | — | 0 | `dddd` | `:48-53` | L `:662` |
| `isotime` | — | 0 | `HH:mm` | `:55-60` | L `:663` |
| `isodate` | — | 0 | `YYYY-MM-DD` | `:62-67` | L `:664` |
| `datetimeformat` | — | `format` | `moment().format(format)` | `:69-83` | L `:665` (space form only) |
| `idleDuration` | `idle_duration` (hidden) | 0 | Walks back over non-system msgs; takes the first user message *after skipping the newest non-system message*; `duration(now-send_date).humanize()`; default `'just now'` | `:85-91`, `:121-151` | L `macros.js:455-485,666` |
| `timeDiff` | — | `left, right` | `duration(moment(left).diff(moment(right))).humanize(true)` (includes "in …"/"… ago") | `:94-118` | L `macros.js:579-590` |

**Instruct / prompt templates** (`macros/definitions/instruct-macros.js`; each returns '' when its enable-flag is off; legacy `instruct-mode.js:673-785`)

| Primary (new) | Aliases (new) | Source field | Enabled when | Def | Legacy key(s) |
|---|---|---|---|---|---|
| `instructStoryStringPrefix` / `…Suffix` | — | `instruct.story_string_prefix/suffix` | instruct | `:34-35` | same `instruct-mode.js:677-686` |
| `instructUserPrefix` | `instructInput` | `input_sequence` | instruct | `:37` | `instructInput\|instructUserPrefix` `:688` |
| `instructUserSuffix` | — | `input_suffix` | instruct | `:38` | same `:693` |
| `instructAssistantPrefix` | `instructOutput` | `output_sequence` | instruct | `:40` | `:698` |
| `instructAssistantSuffix` | `instructSeparator` | `output_suffix` | instruct | `:41` | `:703` |
| `instructSystemPrefix` / `instructSystemSuffix` | — | `system_sequence` / `system_suffix` | instruct | `:43-44` | `:708-716` |
| `instructFirstAssistantPrefix` | `instructFirstOutputPrefix` | `first_output_sequence \|\| output_sequence` | instruct | `:46` | `instructFirstOutput\|instructFirstAssistantPrefix` `:718` (**`instructFirstOutput` missing in new**) |
| `instructLastAssistantPrefix` | `instructLastOutputPrefix` | `last_output_sequence \|\| output_sequence` | instruct | `:47` | `instructLastOutput\|…` `:723` (**`instructLastOutput` missing in new**) |
| `instructStop` | — | `stop_sequence` | instruct | `:49` | `:728` |
| `instructUserFiller` | — | `user_alignment_message` | instruct | `:50` | `:733` |
| `instructSystemInstructionPrefix` | — | `last_system_sequence` | instruct | `:51` | `:738` |
| `instructFirstUserPrefix` | `instructFirstInput` | `first_input_sequence \|\| input_sequence` | instruct | `:53` | `:743` |
| `instructLastUserPrefix` | `instructLastInput` | `last_input_sequence \|\| input_sequence` | instruct | `:54` | `:748` |
| `defaultSystemPrompt` | `instructSystem`, `instructSystemPrompt` | `sysprompt.content` | sysprompt.enabled | `:57` | `:759` |
| `systemPrompt` | — | `charPrompt` if `prefer_character_prompt` && non-empty, else `sysprompt.content` | sysprompt.enabled | `:59-71` | `:754` |
| `exampleSeparator` | `chatSeparator` | `context.example_separator` | always | `:74` | `chatSeparator` only `:765` |
| `chatStart` | — | `context.chat_start` | always | `:75` | `:770` |

**Variables** (`macros/definitions/variable-macros.js`; all call `SillyTavern.getContext().variables.{local,global}` = functions in `variables.js`, mapping `st-context.js:258-277`)

| Macro | Aliases | Args | Returns | Def | Legacy |
|---|---|---|---|---|---|
| `setvar` | — | `name, value` | '' | `:11-32` | L |
| `addvar` | — | `name, value` | '' | `:35-56` | L |
| `incvar` / `decvar` | — | `name` | new value | `:59-96` | L |
| `getvar` | — | `name` | value | `:99-116` | L |
| `hasvar` | `varexists` | `name` | `true/false` | `:119-136` | — |
| `deletevar` | `flushvar` | `name` | '' | `:139-156` | — |
| `setvarkey` | `setvarindex` | `name, key, value` | '' (`set(name,value,{index:key})`) | `:159-186` | — |
| `getvarkey` | `getvarindex` | `name, key` | value at key/index | `:189-212` | — |
| `setglobalvar` / `addglobalvar` | — | `name, value` | '' | `:215-260` | L |
| `incglobalvar` / `decglobalvar` | — | `name` | new value | `:263-300` | L |
| `getglobalvar` | — | `name` | value | `:303-320` | L |
| `hasglobalvar` | `globalvarexists` | `name` | `true/false` | `:323-340` | — |
| `deleteglobalvar` | `flushglobalvar` | `name` | '' | `:343-360` | — |
| `setglobalvarkey` | `setglobalvarindex` | `name, key, value` | '' | `:363-390` | — |
| `getglobalvarkey` | `getglobalvarindex` | `name, key` | value | `:393-416` | — |

**Macros registered by other modules** (both engines unless noted)

| Macro | Semantics | Ref |
|---|---|---|
| `authorsNote`, `charAuthorsNote`, `defaultAuthorsNote` | chat AN (`chat_metadata[metadata_keys.prompt]`), character AN, default AN | `authors-note.js:586-617` |
| `reasoningPrefix`, `reasoningSuffix`, `reasoningSeparator` | `power_user.reasoning.*` — registered **only** in new registry | `reasoning.js:1190-1205` |
| `summary` | latest Summarize memory | `extensions/memory/index.js:1119-1130` |
| `charPrefix`, `charNegativePrefix` | SD per-character prompt prefixes | `extensions/stable-diffusion/index.js:5977-5996` |
| `defaultExpression`, `lastExpression [name='{{char}}']`, `availableExpressions` | Expressions extension | `extensions/expressions/index.js:2654-2720` |
| `timesIndex` | closure-scope macro inside `/times` | `variables.js:371,374` |

### 5.6 Deterministic `{{pick}}` and `{{roll}}`

| Aspect | Legacy | New | Ref |
|---|---|---|---|
| Chat hash | `chat_metadata.chat_id_hash` cached; else `getStringHash(main_chat ?? currentChatId)` (recomputed if falsy) | same, cached if `typeof number` | `macros.js:315-328`; `core-macros.js:471-481` |
| Content hash | `getStringHash(rawContent)` (original input) | `env.contentHash = getStringHash(ctx.content)` | `macros.js:520`; `MacroEnvBuilder.js:87` |
| Position | regex match `offset` in the *current, partially-substituted* string | `globalOffset` = contextOffset + macro start in original doc (preserved through nested args/scopes/`resolve`) | `macros.js:523,536`; `MacroCstWalker.js:504`, `MacroRegistry.js:453-455` |
| Reroll | none | `chat_metadata.pick_reroll_seed` (falsy/0 ignored) appended; `/reroll-pick [n]` increments or sets it | `core-macros.js:399-401`; `slash-commands.js:3596-3608` |
| Seed | `seedrandom(getStringHash("chat-content-offset"))` (**number** seed) | `seedrandom(String(getStringHash([chat,content,offset,reroll].join('-'))))` (**string** seed). seedrandom appends `\0` to non-string seeds, so legacy and new picks differ for identical input | `macros.js:536-540`; `core-macros.js:401-405`; `seedrandom/seedrandom.js:164-172` |
| Index | `floor(rng()*list.length)` | same | `macros.js:540`; `core-macros.js:404` |
| `getStringHash` | cyrb53-style 53-bit hash (`0xdeadbeef`/`0x41c6ce57`, imul constants) | same | `utils.js:522-539` |

`{{roll}}`: bare integer N → `1dN`; formula must match droll regex (dice count ≥1 optional, sides ≥1, optional `+K`/`-K`; no `*`,`/`, no multiple terms); result = sum of `1+floor(Math.random()*sides)` per die + modifier; not seeded. Refs: `core-macros.js:321-336`, `macros.js:550-572`, `droll/droll.js:62,88-107`.

### 5.7 Key legacy ↔ new differences (parity checklist)

| Topic | Legacy | New | Ref |
|---|---|---|---|
| Resolution model | Sequential global regex passes; outputs re-scanned by later passes | Single-pass CST walk, inside-out args; outputs not re-scanned | `macros.js:694-712`; `MacroCstWalker.js:123-152,509-512` |
| Nesting / scoped / if-else / shorthands / comments-multiline | No | Yes | §5.4 |
| Arg separators | Per-regex (`::`, `:`, space) | Uniform `::` list or single `:`/space arg | `MacroParser.js:136-160` |
| Unknown / invalid macro | Left verbatim | Left verbatim (nested parts resolved); arity/type errors also leave raw | `MacroEngine.js:216-237` |
| postProcessFn | Per replacement | Per resolved macro (incl. nested) | `macros.js:708`; `MacroEngine.js:224` |
| Missing in new | `charJailbreak`, `instructFirstOutput`, `instructLastOutput` | — | `script.js:2927`; `instruct-mode.js:718,723` |
| New only | `space`, `if`, `else`, `trim` scoped, `hasvar`, `deletevar`, `*varkey`, `hasglobalvar`, `deleteglobalvar`, `charFirstMessage/greeting`, `hasExtension`, `exampleSeparator`, `char*` primaries, `version` alias, `{{time::UTC±N}}`, `{{newline::N}}`, reasoning macros | — | §5.5 |
| `notChar` in group | others + user | others only | `script.js:2918-2921`; `MacroEnvBuilder.js:194-210` |
| `{{pick}}` seed | see §5.6 | see §5.6 | — |

### 5.8 variables.js — storage & API

| Item | Behavior | Ref |
|---|---|---|
| Local store | `chat_metadata.variables[name]` (created on demand) → saved in chat file via `saveMetadataDebounced()` | `variables.js:22-27,53-79` |
| Global store | `extension_settings.variables.global[name]` (default `{}`) → settings via `saveSettingsDebounced()` | `extensions.js:205-207`; `variables.js:83-84,105-133` |
| `get{Local,Global}Variable(name, {key, index})` | Reads `args.key ?? name`. With `index`: `JSON.parse` stored value, numeric index → array element else object key; object result re-stringified; parse errors ignored. Return: if value is numeric and not blank → `Number`, else `value \|\| ''` (missing → '') | `variables.js:22-46,83-103` |
| `set{Local,Global}Variable(name, value, {index, as})` | Empty name throws. With `index`: parse existing (or create `{}` for non-numeric key / `[]` for numeric), assign `convertValueType(value, as)`, store JSON string. Without index: store raw value. Returns value | `variables.js:48-81,105-134` |
| `convertValueType` types | `string/str`, `null`, `undefined/none`, `number`, `int`, `float`, `boolean/bool` (isTrueBoolean), `list/array` (JSON, else `[]`), `object/dict/dictionary` (JSON, else `{}`); closures untouched | `utils.js:208-262` |
| `add{Local,Global}Variable(name, value)` | `current = get(name) \|\| 0`; if current parses as JSON array → push `value`, store JSON, return array; else if both numeric → store sum; else string append `String(current \|\| '') + value` (note: current `0` is treated as '' when appending) | `variables.js:136-194` |
| `increment*/decrement*` | `add(name, ±1)` → returns new value | `variables.js:196-210` |
| `exists*` | `!== undefined` | `variables.js:422-433` |
| `delete*` | Warn if missing; `delete`; save | `variables.js:592-617` |
| `resolveVariable(name, scope)` | closure scope → local → global → literal name | `variables.js:218-232` |
| Truthiness helpers | `isTrueBoolean`: `on/true/1`; `isFalseBoolean`: `off/false/0` (trim, lowercase) | `utils.js:1010-1021` |
| Loop guard | `MAX_LOOPS = 100` | `variables.js:20` |

**`/if` & `/while` comparison** — operands `left` (aliases in args object: `a`, `first`, `x`) and `right` (`b`, `second`, `y`) `variables.js:479-480`. Operand resolution: `undefined`→undefined; `''`→''; numeric literal (non-blank) → Number; closure-scope var; local var; global var; else string literal `variables.js:440-484`. `evalBoolean(rule,a,b)` `variables.js:494-561`:

| Case | Rules | Semantics |
|---|---|---|
| `a` undefined | — | throws "Left operand is not provided" |
| `b` undefined | none or `not` | truthy test: `on/true/1` → true, `off/false/0` → false, else JS truthiness; `not` inverts; other rule throws |
| both numbers | `gt gte lt lte eq neq` | numeric; `in`/`nin` fall through to string |
| otherwise | `in nin eq neq` | case-insensitive; non-strings `JSON.stringify`'d; `gt/lt…` on strings throws |
| default rule | `eq` | `variables.js:515` |

### 5.9 Slash commands registered in variables.js (`registerVariableCommands`, `variables.js:902`, invoked `slash-commands.js:3766`)

| Command | Aliases | Args | Purpose / return | Ref |
|---|---|---|---|---|
| `/listvar` | `listchatvar` | `scope=all\|local\|global`, `return` (default `popup-html`) | List vars; JSON `[{key,value,scope}]` | `variables.js:263-297,903-932` |
| `/setvar` | `setchatvar` | `key`(req), `index`, `as`; value | Set local; returns value | `:933-981` |
| `/getvar` | `getchatvar` | `key` or unnamed name, `index` | Get local | `:982-1025` |
| `/addvar` | `addchatvar` | `key`; value | add semantics; returns new value | `:1026-1059` |
| `/setglobalvar` | — | `key`, `index`, `as`; value | Set global | `:1060-1107` |
| `/getglobalvar` | — | `key`/name, `index` | Get global | `:1108-1149` |
| `/addglobalvar` | — | `key`; value | add global | `:1150-1182` |
| `/incvar` / `/decvar` | `incchatvar` / `decchatvar` | name | ±1 local; new value | `:1183-1240` |
| `/incglobalvar` / `/decglobalvar` | — | name | ±1 global | `:1241-1296` |
| `/if` | — | `left`(req), `right`, `rule` (`eq neq in nin gt gte lt lte not`, default eq), `else` (closure/subcommand); unnamed command/closure | Executes then/else branch; returns its pipe | `:388-415,1297-1397` |
| `/while` | — | `left`,`right`,`rule`,`guard`; command/closure | Re-evaluates condition each iteration; max 100 iterations unless `guard=off` (doc default 'off' is display-only; runtime default is guarded); stops on abort/`/break`; returns last pipe | `:304-342,1398-1487` |
| `/times` | — | `guard`; `N` + command/closure | Runs `min(N,100)` (or unlimited with `guard=off`); closures get scope macro `{{timesIndex}}`, string commands get `{{timesIndex}}` textually replaced (0-based) | `:350-381,1488-1532` |
| `/flushvar` | `flushchatvar` | name or closure | Delete local | `:1533-1558` |
| `/flushglobalvar` | — | name or closure | Delete global | `:1559-1585` |
| `/add` | — | list | Sum (no `/sum` command exists) | `:677-679,1586-1620` |
| `/mul` | — | list | Product | `:681-683,1621-1655` |
| `/max` / `/min` | — | list | Max / min | `:685-691,1656-1725` |
| `/sub` | — | list | first − rest | `:693-695,1726-1760` |
| `/div` / `/mod` | — | a b | first two only; divisor 0 → 0 + warning | `:697-715,1761-1832` |
| `/pow` | — | a b | `a**b` | `:717-719,1833-1868` |
| `/sin` `/cos` `/log` `/abs` `/sqrt` `/round` | — | single value | Math fn on first operand (`log` = natural log) | `:721-743,1869-2037` |
| `/len` | `length` | value | JSON array length / string length / object key count / number digit count | `:745-765,2038-2075` |
| `/sort` | — | `keysort` (default true); list/dict | Arrays sorted by value (type-grouped comparator); dicts → sorted key list (by key, or by value if `keysort=false`); invalid JSON → input returned | `:782-814,2076-2121` |
| `/rand` | — | `from`(0), `to`(1 or unnamed), `round=round\|ceil\|floor` | Uniform `from + random*(to-from)` | `:767-780,2122-2175` |
| `/var` | — | `key`, `index`, `as`; [name] [value] | Get/set closure-scoped variable | `:854-876,2176-2240` |
| `/let` | — | `key`; [name] [value] | Declare scoped variable in current scope | `:822-846,2241-2286` |
| `/closure-serialize` | — | closure | Returns `closure.rawText` | `:883-888,2287-2317` |
| `/closure-deserialize` | — | string | Parses into closure, parent scope = caller | `:895-900,2318-2340` |

Math operand parsing (all numeric commands): accepts number, array, JSON array string, or space-separated string; non-numeric tokens are resolved via `resolveVariable` (scope→local→global); NaN items dropped; empty input or NaN result → `"0"`; result returned as string `variables.js:625-675`. Subcommand strings wrapped in quotes are unquoted before execution `variables.js:571-585`.

---

## 6. Slash Commands / STscript

All paths are relative to `public/` unless noted. `SCP` = `scripts/slash-commands/SlashCommandParser.js`, `SCC` = `scripts/slash-commands/SlashCommandClosure.js`, `SC` = `scripts/slash-commands.js`.

### 6.A Engine

#### 6.A.1 Object model
- **Registry.** `SlashCommandParser.commands` is one static map from name to `SlashCommand` (SCP:43). Aliases are stored in the same map and point to the same object (SCP:95-100). Registering a name or alias that already exists only prints a console warning; the new entry overwrites the old one (SCP:79-81).
- **Command source.** At registration the engine reads the JS stack trace to fill `isExtension`, `isThirdParty` and `source` (SCP:83-93). Paths under `/scripts/extensions/` count as extension, and paths under `/scripts/extensions/third-party/` count as third-party.
- **Reserved prefixes.** `addCommandObject` rejects any name or alias that starts with `/`, `#`, `:`, `parser-flag` or `breakpoint` (SCP:65-73). The parser's own built-ins skip this check by calling `addCommandObjectUnsafe` (SCP:150, 170, 182, 187).
- **Legacy registration.** `addCommand(name, cb, aliases, help)` is a deprecated wrapper (SCP:53-60). It is re-exported as `registerSlashCommand` (SC:109-111) and exposed on the extension context (`scripts/st-context.js:172`).
- **`SlashCommand` fields** (`SlashCommand.js:50-66`): `name`, `callback(namedArgs, unnamedArgs)`, `helpString`, `splitUnnamedArgument`, `splitUnnamedArgumentCount`, `rawQuotes`, `aliases`, `returns`, `namedArgumentList`, `unnamedArgumentList`. `fromProps` is a plain `Object.assign` (`SlashCommand.js:44-47`).
- **Arguments.** `SlashCommandArgument` has `description`, `typeList`, `isRequired`, `acceptsMultiple`, `defaultValue`, `enumList`, `enumProvider(executor, scope)` and `forceEnum` (`SlashCommandArgument.js:48-55`). `SlashCommandNamedArgument` adds `name` and `aliasList` (`SlashCommandArgument.js:112-131`). The type enum `ARGUMENT_TYPE` has: string, number, range, bool, varname, closure, subcommand, list, dictionary (`SlashCommandArgument.js:10-20`). A bool-only argument with no enums gets true/false enums automatically (`SlashCommandArgument.js:78`).
- **Parsed form.** The parser turns text into a tree of `SlashCommandClosure` objects. Each closure holds `scope`, `executeNow`, `argumentList`, `providedArgumentList`, `executorList`, the abort/break/debug controllers, `rawText` and `fullText` (SCC:16-29). Each `SlashCommandExecutor` holds `injectPipe`, source offsets, `name`, `command`, `namedArgumentList`, `unnamedArgumentList` and `parserFlags` (`SlashCommandExecutor.js:7-30`).
- **Closure result.** `SlashCommandClosureResult` has `interrupt`, `pipe`, `isBreak`, `isAborted`, `isQuietlyAborted`, `abortReason`, `isError` and `errorMessage` (`SlashCommandClosureResult.js:1-10`).

#### 6.A.2 Grammar (parser `SCP`)
- **Entry point.** `parse(text, verifyCommandNames=true, flags, abortController, debugController)` resets parser state and calls `parseClosure(true)` for the root closure, which has no `{: :}` delimiters (SCP:708-725, 730-735).
- **Main loop** (SCP:764-802). Each pass tries, in this order:
  1. block comment
  2. line comment
  3. `/parser-flag`
  4. `/:` run shorthand
  5. `/breakpoint`
  6. `/break`
  7. a normal `/command`

  Any text that does not begin with `/` is silently discarded up to the next command end (SCP:788-789).
- **Command separator `|`.** One `|` ends a command. A double `||` means the next command does **not** get the previous pipe value injected (SCP:792-800, 785-787). A `|` inside unclosed `{{ }}` macro braces is not treated as a separator (SCP:969-996).
- **Command name.** The name is every character after `/` up to whitespace or command end (SCP:1005). If `verifyCommandNames` is on, an unknown name throws `Unknown command at position…` (SCP:1007). After the arguments, anything other than a command end throws `Unexpected end of command` (SCP:1044-1050).
- **Named arguments.**
  - Syntax is `key=value`, where the key matches `/^(\w+)=/` (SCP:1054-1056).
  - The value can be a closure `{:…:}`, a quoted `"…"`, a list `[…]` (taken raw up to the first `]`), or a bare token that ends at whitespace or command end (SCP:1057-1075, 1258-1291).
  - All named arguments must come before the unnamed argument (SCP:1012-1018).
- **Unnamed argument.** This is everything else up to command end (SCP:1083-1230).
  - If it starts with `"` (and the command is not `rawQuotes`), the quoted part is taken as a value and any trailing text is added as extra list parts (SCP:1091-1102).
  - Without splitting, the parts are joined at execution time (SCC:586-592).
  - With `splitUnnamedArgument`, values are tokenized by whitespace, quotes, lists and closures (SCP:1144-1170). `splitUnnamedArgumentCount` caps the number of splits; the rest stays one value, with quotes restored (SCP:1104-1116, 1199-1214).
  - Unquoted first and last parts are trimmed (SCP:1179-1198).
- **`rawQuotes`.** Commands with `rawQuotes` keep the wrapping quotes. Passing `raw=false` turns this off (SCP:1018-1020). The commands that set it are `/sendas` (SC:1256), `/sys` (SC:1331), `/comment` (SC:1409), `/send` (SC:1733) and `/echo` (SC:2110).
- **Quoting and `|`.** In the default (loose) mode, a `|` inside a quoted value is a parse error ("Unexpected end of quoted value") unless it is escaped. Under `STRICT_ESCAPING`, a quoted value may contain `|` (SCP:1235-1245).
- **Closures.**
  - `{: … :}` parses as a child closure whose scope's parent is the current scope (SCP:743-756).
  - Leading `key=value` pairs inside the closure become closure parameters and are declared as scope variable names (SCP:757-763).
  - `{: … :}()` with a trailing `()` sets `executeNow`, which makes the closure run immediately during argument resolution (SCP:805-808).
  - An unclosed closure throws (SCP:739).
  - `closure.rawText` stores the closure's source text (SCP:803). `/closure-serialize` uses it (`scripts/variables.js:883-888`), and `/closure-deserialize` re-parses it (`scripts/variables.js:895-900`).
- **Run shorthand.** `/:name` (or `/:"quoted name"`) becomes an executor bound to `/run`, followed by optional named args and no unnamed arg (SCP:928-964). The name is the cursor target for variable and QR autocomplete (SCP:522-547).
- **Comments.**
  - `//text` and `/#text` run up to the next `|`; `/` (alias `#`) is registered as a no-op help entry (SCP:878-899, 170-180). Per the code, a line comment that reaches the end of the text without a `|` throws "Unclosed comment" when verifying (SCP:881-887).
  - Block comments `/* … *|` can be nested (SCP:849-876). They bind to `commands['*']`, which is never registered.
- **Parser flags.**
  - `PARSER_FLAG = {STRICT_ESCAPING:1, REPLACE_GETVAR:2}` (SCP:37-40).
  - Default values come from `power_user.stscript.parser.flags`, which defaults to `{}` (SCP:710-712; `scripts/power-user.js:325-328`).
  - `/parser-flag NAME [on|off]` is handled at **parse time**, so it only affects the text after it (SCP:901-926).
  - Each executor stores a snapshot of the flags (SCP:1001), and the callback receives it as `args._parserFlags` (SCC:398).
  - **STRICT_ESCAPING.** Backslashes are counted: an odd count escapes the next symbol, and `\\` is a literal backslash (SCP:583-624). In loose mode, a single preceding backslash escapes `|`, `{:`, `:}`, `"` and the like (SCP:626-645).
  - **REPLACE_GETVAR.** At parse time, every `{{getvar::x}}` / `{{getglobalvar::x}}` is rewritten into hidden executors (`/let` pipe, `/getvar`, `/let` temp, `/return` pipe) and replaced by `{{var::temp}}`. This prevents double macro substitution (SCP:647-706). It is a no-op when `power_user.experimental_macro_engine` is on (SCP:649-651).
- **Brace unescape.** At execution time, `\{` and `\}` become `{` and `}` in named args, unnamed args and closure args (SCC:274-280, 501-506, 594-609).

#### 6.A.3 Execution (`SCC`)
- **Copy before run.** `execute()` runs a **copy** of the closure, made by `getCopy` with a copied scope. Loops and repeated `/run` calls therefore do not leak state from one run to the next (SCC:241-254, 219-235).
- **Closure arguments.** `executeDirect`:
  1. resolves declared closure args with `letVariable` (SCC:259-282);
  2. applies caller-provided args (from `/run name key=val`) with `setVariable`, substituting in the parent scope (SCC:283-306);
  3. sets the pipe to `''` if the closure is empty (SCC:308-310);
  4. steps through the executors until done or a break (SCC:311-361).
- **Per executor** (`executeStep`, SCC:380-459):
  - The callback gets `args = {_scope, _parserFlags, _abortController, _debugController, _hasUnnamedArgument}` (SCC:396-402).
  - Named args are resolved first (SCC:481-547). A non-immediate closure is passed as a `SlashCommandClosure` object. An immediate closure is executed and its pipe is used. Strings are macro-substituted. `acceptsMultiple` args become arrays (SCC:509-521).
  - The unnamed arg is resolved next (SCC:555-619).
  - The abort check runs (SCC:421-424), and then `scope.pipe = await command.callback(args, value ?? '')` (SCC:438).
  - Exceptions are wrapped in `SlashCommandExecutionError`, which carries the command name and position (SCC:439-441; line, column and hint at `SlashCommandExecutionError.js:10-48`).
  - `#lintPipe` turns `undefined`/`null` into `''` and JSON-stringifies any value that is not a string or closure (SCC:625-633).
- **Pipe semantics.**
  - Every command's return value becomes `scope.pipe` (SCC:438).
  - A command with **no** unnamed argument receives the previous pipe value as its unnamed argument. This does not happen for the first command of a closure or after `||` (SCC:558-562).
  - `{{pipe}}` substitutes the current pipe. `scope.pipe` falls back to the parent scope's pipe when the local one is unset (`SlashCommandScope.js:20-25`; SCC:61, 178, 185).
- **Macro substitution timing.** Macros are substituted **per argument, at execution time, just before each command runs**, not at parse time (SCC:544, 582, 272, 296). Text inside a non-immediate closure is only substituted when that closure is executed.
  - **Legacy path** (`closure.substituteParams`, SCC:155-217): one regex handles `{{pipe}}`, `{{var::name[::index]}}` (a scoped variable) and scope macros such as `{{arg::x}}`, `{{timesIndex}}` and wildcard `*` keys. All other text goes through the global `substituteParams` from `script.js` (SCC:1, 183, 209), which covers `{{user}}`, `{{getvar::}}` and the rest. If a scope macro's value is a closure, the argument becomes a list that contains that closure object (SCC:186-202).
  - **Experimental macro engine** (`power_user.experimental_macro_engine`): `substituteWithMacroEngine` registers `pipe`, `var` and the scope macros as dynamic macros, then calls `substituteParams(text, {dynamicMacros})`. Closures are spliced back in through boundary markers (SCC:58-147, 174-176). An unknown `{{var}}` gives `''` here instead of throwing (SCC:66-71).
- **Scoped variables** (`scripts/slash-commands/SlashCommandScope.js`):
  - Each closure has a scope whose `parent` is the enclosing scope (`SlashCommandScope.js:18, 28-30`; SCC:43-45).
  - `letVariable` throws if the name already exists in the **same** scope (`SlashCommandScope.js:55-58`).
  - `setVariable` and `getVariable` walk up the parent chain and throw "No such variable" if the name is not found (`SlashCommandScope.js:59-108`). Both accept an `index` and treat the value as JSON (`SlashCommandScope.js:61-75, 87-98`).
  - `getVariable` returns a Number for numeric strings (`SlashCommandScope.js:100-101`).
  - `/let` → `letCallback` (`scripts/variables.js:822-846`).
  - `/var` gets or sets depending on whether a value is given, and supports `index=` and `as=` (`scripts/variables.js:854-876`).
  - The parser also records `/let` and `/import` names for autocomplete (SCP:1023-1042).
- **Chat and global variables.** These are separate from scoped variables. `/setvar` writes to `chat_metadata.variables` (`scripts/variables.js:48-77`), and `/setglobalvar` writes to `extension_settings.variables.global` (`scripts/variables.js:83-130`).
- **Control flow.**
  - `/if` (`scripts/variables.js:388-415`), `/while` (`scripts/variables.js:304-342`) and `/times` (`scripts/variables.js:350-381`) take either a closure or a quoted command string. Strings run through `executeSubCommands` → `executeSlashCommandsWithOptions` with the caller's scope, flags and abort controller (`scripts/variables.js:571-585`).
  - Loops stop after `MAX_LOOPS = 100` iterations unless `guard=off` (`scripts/variables.js:20, 306-307, 365-366`).
  - `/times` sets the scope macro `{{timesIndex}}` (`scripts/variables.js:371, 374`).
- **`/break [value]`.** The parser makes a `SlashCommandBreak` executor (SCP:830-847). At run time its argument becomes the pipe (SCC:408-415), and if a break controller exists it calls `breakController.break()` and stops (SCC:349-354, 313). Break controllers are created fresh by `/run` (SC:4222, 4234), `/while` (`scripts/variables.js:325`) and `/times` (`scripts/variables.js:370`). The controller is passed down to child closures (SCC:534, 572).
- **`/abort [reason] quiet=true|false`.** `abortCallback` calls `_abortController.abort(reason, isQuiet)`, and `quiet` defaults to true (SC:4273-4277; `SlashCommandAbortController.js:11-16`).
  - Abort is checked after argument resolution and after each command (SCC:421-424, 450-453). Pause is a 200 ms polling loop (SCC:461-465).
  - An abort returns a `SlashCommandClosureResult` with `isAborted` set (SCC:466-475).
  - A non-quiet abort shows a "Command execution aborted" toast (SC:7034-7037).
- **`/breakpoint`.** It only matches when followed by `|` (`/\/breakpoint\s*\|/`, SCP:815-829). It is added to the executor list only when a debug controller exists, which is the QR editor debug mode (SCP:775-779).
  - The executor yields to the debugger (SCC:316-336, 385-393; `SlashCommandDebugController.js:43-77`, which provides resume, step, stepInto and stepOut).
  - The QR editor starts it through `QuickReplySet.debug` (`scripts/extensions/quick-reply/src/QuickReplySet.js:118-125`).

#### 6.A.4 Entry points
- **`executeSlashCommandsWithOptions(text, {handleParserErrors=true, scope, handleExecutionErrors=false, parserFlags, abortController, debugController, onProgress, source})`** (SC:6988-7066):
  1. Parses with `verifyCommandNames=true` (SC:7005) and sets `closure.scope.parent = options.scope` (SC:7006).
  2. On a parser error it shows a toast with line, column and hint, then returns an empty result (SC:7011-7026).
  3. Runs `closure.execute()` (SC:7033). Execution errors are shown as a toast when `handleExecutionErrors` is set, otherwise rethrown (SC:7040-7064).

  The deprecated positional form is `executeSlashCommands` (SC:7079-7088). Both are exported (SC:104) and exposed on the extension context (`scripts/st-context.js:171`).
- **`executeSlashCommandsOnChatInput(text, {scope, parserFlags, clearChatInput, source})`** (SC:6896-6980):
  - Only one chat-input execution can run at a time (SC:6897, 6906).
  - Shows the pause and stop buttons (SC:6808-6841, 6908) and drives a progress bar through the CSS vars `--prog` and `--progDone` (SC:6919-6938).
  - Sets the `script_success`, `script_error` or `script_aborted` classes (SC:6943-6949).
  - Shows a toast for execution errors (SC:6953-6971).
- **Typing `/...` in the send box.**
  - `sendTextareaMessage` returns early while a script is running (`script.js:1740`), then calls `Generate(type)` (`script.js:1764`).
  - Unless the call is a dryRun, `depth>0`, `regenerate`, `swipe` or `quiet`, `Generate` calls `processCommands(textarea)` (`script.js:4310-4311`).
  - If the trimmed text starts with `/`, it runs via `executeSlashCommandsOnChatInput(msg, {clearChatInput:true})` and returns true (`script.js:3125-3133`).
  - Generation is then cancelled with `unblockGeneration` and no LLM call is made (`script.js:4313-4317`).
  - `GENERATION_STARTED` is emitted before this check (`script.js:4299`). `GENERATION_AFTER_COMMANDS` is emitted only when no command ran (`script.js:4321`).
- **Quick Replies.**
  - `QuickReply.execute(args, isEditor, isRun, options)` builds a fresh scope with `scope.setMacro('arg::<key>', value)` for each non-underscore arg, plus `arg::*` → `''` (`scripts/extensions/quick-reply/src/QuickReply.js:1878-1899`).
  - It then calls `QuickReplySet.executeWithOptions` (`scripts/extensions/quick-reply/src/QuickReplySet.js:260`).
  - That method may merge the QR text with the textarea input according to `injectInput` / `placeBeforeInput` (`QuickReplySet.js:153-161`).
  - If the text starts with `/` and `disableSend` is off, the call depends on the trigger: auto-execute or `/run` → `executeSlashCommandsWithOptions` (`QuickReplySet.js:165-170`); editor → the same with `handleParserErrors:false` (`QuickReplySet.js:171-178`); button click → `executeSlashCommandsOnChatInput` (`QuickReplySet.js:179-184`). The pipe is returned (`QuickReplySet.js:185`).
  - Otherwise the text is run through `substituteParams`, written into the textarea, and `#send_but` is clicked (`QuickReplySet.js:188-194`).
- **`/run` and `/:`** (`runCallback`, SC:4216-4266):
  - A closure argument is executed directly.
  - A scoped variable holding a closure has the caller's named args mapped onto its declared params (SC:4228-4249).
  - Anything else goes to `globalThis.executeQuickReplyByName(name, args, {abortController, debugController})` (SC:4251-4265). That function looks up the label across the global, chat and character QR sets, then falls back to `"Set.Label"` (`scripts/extensions/quick-reply/index.js:184-207`).
- **Persisted injects.** `/inject` data is re-applied on chat load by `processChatSlashCommands`, which also revives filter closures from text (SC:3893-3933).

#### 6.A.5 Return helper
- `slashCommandReturnHelper.enumList({allowPipe, allowObject, allowChat, allowPopup, allowTextVersion})` produces the options for a `return=` argument: `pipe`, `object`, `chat-html`, `chat-text`, `popup-html`, `popup-text`, `toast-html`, `toast-text`, `console`, `none` (`SlashCommandReturnHelper.js:8, 25-36`).
- `doReturn(type, value, {objectToStringFunc, objectToHtmlFunc})` behaves as follows (`SlashCommandReturnHelper.js:48-80`):
  - **html variants:** showdown + DOMPurify; **text variants:** escaped.
  - **popup:** `callGenericPopup`; **chat:** `sendSystemMessage(GENERIC)`; **toast:** `toastr.info`. All three return `''`.
  - **pipe:** returns the string value; **object:** returns `JSON.stringify(value)`; **console:** `console.info`; **none:** `''`.
- Commands that use it: `/char-get` (SC:1085), `/sendas` (SC:1290), `/sys` (SC:1360), `/comment` (SC:1432), `/sysgen` (SC:1665), `/ask` (SC:1693), `/send` (SC:1763), `/listinjects` (SC:2953), `/persona-get` (`scripts/personas.js:2666`), `/tools-list` (`scripts/tool-calling.js:952`), `/listvar` (`scripts/variables.js:928`), `/expression-list` (`scripts/extensions/expressions/index.js:2520`), `/db-search` (`scripts/extensions/vectors/index.js:2173`).

#### 6.A.6 Autocomplete and enums (brief)
- `setSlashCommandAutoComplete` attaches an `AutoComplete` to a textarea when the text starts with `/` and `stscript.autocomplete.state` is ALWAYS, or MIN_LENGTH with length > 2 (SC:7096-7110). The send box switches to a monospace font when the text starts with `/` (SC:7112-7121).
- The name lookup is `parser.getNameAt(text, index)`. It re-parses with `verifyCommandNames=false` and offers macro completion inside `{{…}}`, variables and QRs for `/:`, or command and argument options through `SlashCommandAutoCompleteNameResult` (SCP:473-552).
- Shared enum providers (booleans, variables, characters, personas, tags, messages, worlds, injects, connection profiles and others) are in `scripts/slash-commands/SlashCommandCommonEnumsProvider.js:128-378`.
- Default settings: `stscript.matching='fuzzy'`, autocomplete `state=ALWAYS`, `select=TAB+ENTER`, `font.scale=0.8` (`scripts/power-user.js:308-329`).

### 6.B Complete command registry (SillyTavern 1.19)

**Totals.**

| | Count |
|---|---|
| Registered command objects | **293** |
| Core | 226 |
| Built-in extensions | 67 |
| Aliases | 176 |
| Invocable names (commands + aliases) | **469** |

No name or alias is duplicated (checked by script).

**Pseudo-commands not in the table.** `/:` is run shorthand (SCP:928-964). `/* … *|` is a block comment and is never registered (SCP:849-876).

**Extensions with no commands.** `assets` registers none. `extensions/third-party/` is empty in this checkout. Extension commands only exist when the extension is active: disabled extensions are never loaded (`scripts/extensions.js:626-637`).

**How the count was verified.**
1. `grep -rc "SlashCommand.fromProps" public` (excluding `/lib/`) sums to 294. That includes one generic wrapper inside `addCommand` (SCP:54), which leaves 293.
2. `grep -rho "addCommandObject\(Unsafe\)\?(SlashCommand.fromProps" public/scripts | wc -l` also gives 294, which confirms every `fromProps` call is registered.
3. There are no direct `registerSlashCommand(...)` calls left in the codebase; only the definitions and re-exports remain.
4. An acorn AST pass over all 33 files extracted every `SlashCommand.fromProps({...})` call's `name`, `aliases` and `helpString` along with its line. Every name is a string literal.

Purpose = first sentence of `helpString` (HTML stripped). Paths are relative to `public/scripts/`.

**slash-commands.js** — 100 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /api | - | slash-commands.js:240 | Connect to an API. |
| /impersonate | /imp | slash-commands.js:344 | Calls an impersonation response, with an optional additional prompt. |
| /delchat | - | slash-commands.js:405 | Deletes the current chat. |
| /renamechat | - | slash-commands.js:437 | Renames the current chat. |
| /getchatname | - | slash-commands.js:463 | Returns the name of the current chat file into the pipe. |
| /closechat | - | slash-commands.js:471 | Closes the current chat. |
| /tempchat | - | slash-commands.js:479 | Opens a temporary chat with Assistant. |
| /panels | /togglepanels | slash-commands.js:500 | Toggle UI panels on/off |
| /forcesave | - | slash-commands.js:509 | Forces a save of the current chat and settings |
| /instruct | - | slash-commands.js:519 | Selects instruct mode template by name. |
| /instruct-on | - | slash-commands.js:579 | Enables instruct mode. |
| /instruct-off | - | slash-commands.js:584 | Disables instruct mode |
| /instruct-state | /instruct-toggle | slash-commands.js:589 | Gets the current instruct mode state. |
| /context | - | slash-commands.js:610 | Selects context template by name. |
| /chat-manager | /chat-history, /manage-chats | slash-commands.js:650 | Opens the chat manager for the current character/group. |
| /? | /help | slash-commands.js:659 | Get help on macros, chat formatting and commands. |
| /bg | /background | slash-commands.js:675 | Sets a background according to the provided filename. |
| /char-find | /findchar | slash-commands.js:707 | Searches for a character and returns its avatar key. |
| /char-create | - | slash-commands.js:908 | Creates a new character with the specified attributes. |
| /char-update | - | slash-commands.js:955 | Updates an existing character's attributes. |
| /char-duplicate | /dupe | slash-commands.js:1000 | Duplicates a character. |
| /char-get | /char-data | slash-commands.js:1046 | Retrieves character data. |
| /char-delete | - | slash-commands.js:1111 | Deletes a character from the system. |
| /message-role | - | slash-commands.js:1162 | Changes the role of a message sender to one of your choice. |
| /message-name | - | slash-commands.js:1204 | Changes the name of a message sender to one of your choice. |
| /sendas | - | slash-commands.js:1254 | Sends a message as a specific character. |
| /sys | /nar | slash-commands.js:1329 | Sends a message as a system narrator. |
| /sysname | - | slash-commands.js:1397 | Sets a name for future system narrator messages in this chat (display only). |
| /comment | - | slash-commands.js:1407 | Adds a note/comment message not part of the chat. |
| /single | /story | slash-commands.js:1471 | Sets the message style to single document mode without names or avatars visible. |
| /bubble | /bubbles | slash-commands.js:1477 | Sets the message style to bubble chat mode. |
| /flat | /default | slash-commands.js:1483 | Sets the message style to flat chat mode. |
| /continue | /cont | slash-commands.js:1489 | Continues the last message in the chat, with an optional additional prompt. |
| /regenerate | /regen | slash-commands.js:1530 | Regenerates the latest reply in the chat. |
| /swipe | - | slash-commands.js:1553 | Swipes the latest reply. |
| /go | /char | slash-commands.js:1590 | Opens up a chat with the character or group by its name |
| /rename-char | - | slash-commands.js:1605 | Renames the current character. |
| /sysgen | - | slash-commands.js:1628 | Generates a system message using a specified prompt. |
| /ask | - | slash-commands.js:1676 | Asks a specified character card a prompt. |
| /delname | /cancel | slash-commands.js:1704 | Deletes all messages attributed to a specified name. |
| /send | - | slash-commands.js:1731 | Adds a user message to the chat log without triggering a generation. |
| /trigger | - | slash-commands.js:1805 | Triggers a message generation. |
| /hide | - | slash-commands.js:1835 | Hides a chat message from the prompt. |
| /unhide | - | slash-commands.js:1858 | Unhides a message from the prompt. |
| /member-get | /getmember, /memberget | slash-commands.js:1881 | Retrieves a group member's name, index, id, or avatar. |
| /member-disable | /disable, /disablemember, /memberdisable | slash-commands.js:1932 | Disables a group member from being drafted for replies. |
| /member-enable | /enable, /enablemember, /memberenable | slash-commands.js:1946 | Enables a group member to be drafted for replies. |
| /member-add | /addmember, /memberadd | slash-commands.js:1960 | Adds a new group member to the group chat. |
| /member-remove | /removemember, /memberremove | slash-commands.js:1986 | Removes a group member from the group chat. |
| /member-up | /upmember, /memberup | slash-commands.js:2013 | Moves a group member up in the group chat list. |
| /member-down | /downmember, /memberdown | slash-commands.js:2027 | Moves a group member down in the group chat list. |
| /member-peek | /peek, /memberpeek, /peekmember | slash-commands.js:2041 | Shows a group member character card without switching chats. |
| /member-count | /countmember, /membercount | slash-commands.js:2068 | Returns the total number of group members in the group chat list. |
| /delswipe | /swipedel | slash-commands.js:2074 | Deletes a swipe from the last chat message. |
| /echo | - | slash-commands.js:2108 | Echoes the provided text to a toast message. |
| /gen | - | slash-commands.js:2210 | Generates text using the provided prompt and passes it down the pipe, optionally locking user input while generating. |
| /genraw | - | slash-commands.js:2260 | Generates text using the provided prompt (raw) and passes it down the pipe, optionally locking user input while generating. |
| /addswipe | /swipeadd | slash-commands.js:2320 | Adds a swipe to the last chat message. |
| /stop | /generate-stop | slash-commands.js:2346 | Stops the generation and any streaming if it is currently running. |
| /abort | - | slash-commands.js:2363 | Aborts the slash command batch execution. |
| /fuzzy | - | slash-commands.js:2382 | Performs a fuzzy match of each item in the list against the text to search. |
| /pass | /return | slash-commands.js:2450 | Passes the text to the next command through the pipe. |
| /delay | /wait, /sleep | slash-commands.js:2477 | Delays the next command in the pipe by the specified number of milliseconds. |
| /input | /prompt | slash-commands.js:2500 | Shows a popup with the provided text and an input field. |
| /run | /call, /exec | slash-commands.js:2578 | Runs a closure from a scoped variable, or a Quick Reply by name from the active or another preset. |
| /messages | /message | slash-commands.js:2606 | Returns the specified message or range of messages as a string. |
| /setinput | - | slash-commands.js:2662 | Sets the user input to the specified text and passes it to the next command through the pipe. |
| /popup | - | slash-commands.js:2684 | Shows a blocking popup with the specified text and buttons. |
| /buttons | - | slash-commands.js:2773 | Shows a blocking popup with the specified text and buttons. |
| /trimtokens | - | slash-commands.js:2820 | Trims the start or end of text to the specified number of tokens. |
| /trimstart | - | slash-commands.js:2858 | Trims the text to the start of the first full sentence. |
| /trimend | - | slash-commands.js:2881 | Trims the text to the end of the last full sentence. |
| /inject | - | slash-commands.js:2892 | Injects a text into the LLM prompt for the current chat. |
| /listinjects | - | slash-commands.js:2942 | Lists all script injections for the current chat. |
| /flushinject | /flushinjects | slash-commands.js:2958 | Removes a script injection for the current chat. |
| /tokens | - | slash-commands.js:2972 | Counts the number of tokens in the provided text. |
| /model | - | slash-commands.js:2986 | Sets the model for the current API. |
| /getpromptentry | /getpromptentries | slash-commands.js:3008 | Gets the state of the specified prompt entries. |
| /setpromptentry | /setpromptentries | slash-commands.js:3051 | Sets the specified prompt manager entry/entries on or off. |
| /pm-render | - | slash-commands.js:3089 | Rerenders the prompt manager content. |
| /pick-icon | - | slash-commands.js:3107 | Opens a popup with all the available Font Awesome icons and returns the selected icon's name. |
| /api-url | /server | slash-commands.js:3123 | Set the API URL / server URL / endpoint for the currently selected API, including the port. |
| /tokenizer | - | slash-commands.js:3177 | Selects tokenizer by name. |
| /upper | /uppercase, /to-upper | slash-commands.js:3199 | Converts the provided string to uppercase. |
| /lower | /lowercase, /to-lower | slash-commands.js:3211 | Converts the provided string to lowercase. |
| /substr | /substring | slash-commands.js:3223 | Extracts text from the provided string. |
| /is-mobile | - | slash-commands.js:3263 | Returns true if the current device is a mobile device, false otherwise. |
| /chat-render | - | slash-commands.js:3269 | Renders a specified number of messages into the chat window. |
| /chat-reload | - | slash-commands.js:3294 | Reloads the current chat. |
| /replace | /re | slash-commands.js:3302 | Replaces text within the provided string based on the pattern. |
| /test | - | slash-commands.js:3362 | Tests text for a regular expression match. |
| /match | - | slash-commands.js:3401 | Retrieves regular expression matches in the given text; returns an array of groups (first = full match). |
| /chat-jump | /chat-scrollto, /floor-teleport | slash-commands.js:3448 | Scrolls the chat view to the specified message index. |
| /clipboard-get | - | slash-commands.js:3507 | Retrieves the text from the OS clipboard. |
| /clipboard-set | - | slash-commands.js:3528 | Copies the provided text to the OS clipboard. |
| /prompt-post-processing | /ppp | slash-commands.js:3549 | Sets a "Prompt Post-Processing" type. |
| /reroll-pick | - | slash-commands.js:3595 | Rerolls all {{pick}} macro choices in the current chat. |
| /beep | /ding | slash-commands.js:3637 | Plays the message received sound effect. |
| /array-wrap | /list-wrap | slash-commands.js:3648 | Wraps a single unnamed argument into an array if it's not already an array. |
| /array-unwrap | /list-unwrap | slash-commands.js:3714 | Unwraps the first element of an array provided as an unnamed argument. |

**action-loader-slashcommands.js** — 4 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /loader-wrap | - | action-loader-slashcommands.js:59 | Wraps a closure execution with an action loader overlay and optional toast notification. |
| /loader-show | - | action-loader-slashcommands.js:179 | Manually shows an action loader. |
| /loader-hide | - | action-loader-slashcommands.js:291 | Hides an action loader that was shown with /loader-show. |
| /loader-stop | - | action-loader-slashcommands.js:331 | Triggers the stop action on a specific action loader, as if the user clicked the stop button. |

**authors-note.js** — 5 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /note | - | authors-note.js:501 | Sets an author's note for the currently selected chat if specified and returns the current note. |
| /note-depth | /depth | authors-note.js:516 | Sets an author's note depth for in-chat positioning if specified and returns the current depth. |
| /note-frequency | /freq, /note-freq | authors-note.js:532 | Sets an author's note insertion frequency if specified and returns the current frequency. |
| /note-position | /pos, /note-pos | authors-note.js:549 | Sets an author's note position if specified and returns the current position. |
| /note-role | - | authors-note.js:566 | Sets an author's note chat insertion role if specified and returns the current role. |

**backgrounds.js** — 3 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /lockbg | /bglock | backgrounds.js:1813 | Locks a background for the currently selected chat |
| /unlockbg | /bgunlock | backgrounds.js:1822 | Unlocks a background for the currently selected chat |
| /autobg | /bgauto | backgrounds.js:1831 | Automatically changes the background based on the chat context using the AI request prompt |

**bookmarks.js** — 7 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /branch-create | - | bookmarks.js:494 | Create a new branch from the selected message. |
| /checkpoint-create | - | bookmarks.js:523 | Create a new checkpoint for the selected message with the provided name. |
| /checkpoint-go | - | bookmarks.js:576 | Open the checkpoint linked to the selected message. |
| /checkpoint-exit | - | bookmarks.js:612 | Exit the checkpoint chat. |
| /checkpoint-parent | - | bookmarks.js:621 | Get the name of the parent chat for this checkpoint. |
| /checkpoint-get | - | bookmarks.js:630 | Get the name of the checkpoint linked to the selected message. |
| /checkpoint-list | - | bookmarks.js:653 | List all existing checkpoints in this chat. |

**extensions-slashcommands.js** — 6 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /extension-enable | - | extensions-slashcommands.js:88 | Enables a specified extension. |
| /extension-disable | - | extensions-slashcommands.js:129 | Disables a specified extension. |
| /extension-toggle | - | extensions-slashcommands.js:170 | Toggles the state of a specified extension. |
| /extension-state | - | extensions-slashcommands.js:229 | Returns the state of a specified extension (true if enabled, false if disabled). |
| /extension-exists | /extension-installed | extensions-slashcommands.js:265 | Checks if a specified extension exists. |
| /reload-page | - | extensions-slashcommands.js:297 | Reloads the current page. |

**openai.js** — 1 command, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /proxy | - | openai.js:6758 | Sets a proxy preset by name. |

**personas.js** — 8 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /persona-create | - | personas.js:2557 | Creates a new persona with the specified attributes. |
| /persona-update | - | personas.js:2602 | Updates an existing persona's attributes. |
| /persona-get | /persona-data | personas.js:2637 | Retrieves persona data. |
| /persona-delete | - | personas.js:2696 | Deletes a persona and its avatar from the system. |
| /persona-duplicate | - | personas.js:2736 | Duplicates a persona including all its data and avatar. |
| /persona-lock | /lock, /bind | personas.js:2781 | Locks/unlocks the current persona to a chat, character, or as the default. |
| /persona-set | /persona, /name | personas.js:2822 | Selects an existing persona by name or avatar key, or sets a temporary user name. |
| /persona-sync | /sync | personas.js:2864 | Syncs the user persona (name and avatar) in user-attributed messages in the current chat. |

**power-user.js** — 12 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /vn | - | power-user.js:4115 | Swaps Visual Novel Mode On/Off |
| /newchat | - | power-user.js:4120 | Start a new chat with the current character |
| /random | - | power-user.js:4138 | Start a new chat with a random character. |
| /del | /delete, /delmode | power-user.js:4150 | Enter message deletion mode, and auto-deletes last N messages if numeric argument is provided. |
| /cut | - | power-user.js:4171 | Cuts the specified message or continuous chunk from the chat. |
| /resetpanels | /resetui | power-user.js:4211 | resets UI panels to original state |
| /bgcol | - | power-user.js:4217 | Generates a new theme based on a dominant color of the specified background image. |
| /theme | - | power-user.js:4242 | Sets a UI theme by name. |
| /css-var | - | power-user.js:4272 | Sets a CSS variable to a specified value on a target element. |
| /movingui | - | power-user.js:4361 | activates a movingUI preset by name |
| /stop-strings | /stopping-strings, /custom-stopping-strings, /custom-stop-strings | power-user.js:4374 | Sets a list of custom stopping strings. |
| /start-reply-with | - | power-user.js:4437 | Sets a "Start Reply With". |

**preset-manager.js** — 1 command, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /preset | - | preset-manager.js:994 | Sets a preset by name for the current API. |

**reasoning.js** — 8 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /reasoning-get | /get-reasoning | reasoning.js:900 | Get the contents of a reasoning block of a message. |
| /reasoning-set | /set-reasoning | reasoning.js:920 | Set the reasoning block of a message. |
| /reasoning-parse | /parse-reasoning | reasoning.js:970 | Extracts the reasoning block from a string using the Reasoning Formatting settings. |
| /reasoning-format | /format-reasoning | reasoning.js:1040 | Formats reasoning and content into a single string using Reasoning Formatting settings. |
| /reasoning-template | /reasoning-formatting, /reasoning-preset | reasoning.js:1078 | Selects a reasoning template by name, using fuzzy search to find the closest match. |
| /reasoning-collapse | /collapse-reasoning | reasoning.js:1145 | Collapse the reasoning block of a message or range of messages. |
| /reasoning-expand | /expand-reasoning | reasoning.js:1157 | Expand the reasoning block of a message or range of messages. |
| /reasoning-toggle | /toggle-reasoning | reasoning.js:1169 | Toggle the reasoning block of a message or range of messages. |

**scrapers.js** — 1 command, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /yt-script | - | scrapers.js:473 | Scrape a transcript from a YouTube video by ID or URL. |

**secrets.js** — 5 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /secret-id | /secret-rotate | secrets.js:767 | Sets the ID of a currently active secret key. |
| /secret-delete | - | secrets.js:845 | Deletes a secret key by ID. |
| /secret-write | - | secrets.js:910 | Writes a secret key with a value and an optional label. |
| /secret-rename | - | secrets.js:989 | Renames a secret key by ID. |
| /secret-read | /secret-find, /secret-get | secrets.js:1067 | Reads a secret key by ID. |

**slash-commands/SlashCommandParser.js** — 4 commands, core (parser built-ins, registered via `addCommandObjectUnsafe` in the constructor)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /parser-flag | - | slash-commands/SlashCommandParser.js:150 | Set a parser flag (STRICT_ESCAPING / REPLACE_GETVAR, on/off). |
| // | /# | slash-commands/SlashCommandParser.js:170 | Write a comment. |
| /breakpoint | - | slash-commands/SlashCommandParser.js:182 | Set a breakpoint for debugging in the QR Editor. |
| /break | - | slash-commands/SlashCommandParser.js:187 | Break out of a loop or closure executed through /run or /: |

**sysprompt.js** — 4 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /sysprompt | /system-prompt | sysprompt.js:196 | Selects a system prompt by name, using fuzzy search to find the closest match. |
| /sysprompt-on | /sysprompt-enable | sysprompt.js:239 | Enables system prompt. |
| /sysprompt-off | /sysprompt-disable | sysprompt.js:245 | Disables system prompt |
| /sysprompt-state | /sysprompt-toggle | sysprompt.js:251 | Gets the current system prompt state. |

**tags.js** — 5 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /tag-add | - | tags.js:2351 | Adds a tag to the character. |
| /tag-remove | - | tags.js:2398 | Removes a tag from the character. |
| /tag-exists | - | tags.js:2444 | Checks whether the given tag is assigned to the character. |
| /tag-list | - | tags.js:2488 | Lists all assigned tags of the character. |
| /tag-import | - | tags.js:2525 | Imports character card tags as SillyTavern tags for folder/filter use. |

**tool-calling.js** — 4 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /tools-list | /tool-list | tool-calling.js:941 | Gets a list of all registered tools in the OpenAI function JSON format. |
| /tools-invoke | /tool-invoke | tool-calling.js:965 | Invokes a registered tool by name. |
| /tools-register | /tool-register | tool-calling.js:1000 | Registers a new tool with the tool registry. |
| /tools-unregister | /tool-unregister | tool-calling.js:1151 | Unregisters a tool from the tool registry. |

**variables.js** — 37 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /listvar | /listchatvar | variables.js:903 | List registered chat variables. |
| /setvar | /setchatvar | variables.js:933 | Set a local (chat) variable value and pass it down the pipe. |
| /getvar | /getchatvar | variables.js:982 | Get a local variable value and pass it down the pipe. |
| /addvar | /addchatvar | variables.js:1026 | Add a value to a local variable and pass the result down the pipe. |
| /setglobalvar | - | variables.js:1060 | Set a global variable value and pass it down the pipe. |
| /getglobalvar | - | variables.js:1108 | Get a global variable value and pass it down the pipe. |
| /addglobalvar | - | variables.js:1150 | Add a value to a global variable and pass the result down the pipe. |
| /incvar | /incchatvar | variables.js:1183 | Increment a local variable by 1 and pass the result down the pipe. |
| /decvar | /decchatvar | variables.js:1212 | Decrement a local variable by 1 and pass the result down the pipe. |
| /incglobalvar | - | variables.js:1241 | Increment a global variable by 1 and pass the result down the pipe. |
| /decglobalvar | - | variables.js:1269 | Decrement a global variable by 1 and pass the result down the pipe. |
| /if | - | variables.js:1297 | Compares left operand a with right operand b (rule=) and, if true, executes the given command/closure (else= otherwise). |
| /while | - | variables.js:1398 | Compares a with b and repeatedly executes the given command/closure while the condition holds (guard: 100 loops). |
| /times | - | variables.js:1488 | Execute any valid slash command enclosed in quotes repeats number of times. |
| /flushvar | /flushchatvar | variables.js:1533 | Delete a local variable. |
| /flushglobalvar | - | variables.js:1559 | Deletes the specified global variable. |
| /add | - | variables.js:1586 | Performs an addition of the set of values and passes the result down the pipe. |
| /mul | - | variables.js:1621 | Performs a multiplication of the set of values and passes the result down the pipe. |
| /max | - | variables.js:1656 | Returns the maximum value of the set of values and passes the result down the pipe. |
| /min | - | variables.js:1691 | Returns the minimum value of the set of values and passes the result down the pipe. |
| /sub | - | variables.js:1726 | Performs a subtraction of the set of values and passes the result down the pipe. |
| /div | - | variables.js:1761 | Performs a division of two values and passes the result down the pipe. |
| /mod | - | variables.js:1797 | Performs a modulo operation of two values and passes the result down the pipe. |
| /pow | - | variables.js:1833 | Performs a power operation of two values and passes the result down the pipe. |
| /sin | - | variables.js:1869 | Performs a sine operation of a value and passes the result down the pipe. |
| /cos | - | variables.js:1897 | Performs a cosine operation of a value and passes the result down the pipe. |
| /log | - | variables.js:1925 | Performs a logarithm operation of a value and passes the result down the pipe. |
| /abs | - | variables.js:1954 | Performs an absolute value operation of a value and passes the result down the pipe. |
| /sqrt | - | variables.js:1982 | Performs a square root operation of a value and passes the result down the pipe. |
| /round | - | variables.js:2010 | Rounds a value and passes the result down the pipe. |
| /len | /length | variables.js:2038 | Gets the length of a value and passes the result down the pipe. |
| /sort | - | variables.js:2076 | Sorts a list or dictionary in ascending order and passes the result down the pipe. |
| /rand | - | variables.js:2122 | Returns a random number between from and to (inclusive). |
| /var | - | variables.js:2176 | Get or set a (scoped) variable. |
| /let | - | variables.js:2241 | Declares a new variable in the current scope. |
| /closure-serialize | - | variables.js:2287 | Serialize a closure as text that can be stored in global and chat variables. |
| /closure-deserialize | - | variables.js:2318 | Deserialize a closure from text. |

**world-info.js** — 11 commands, core

| name | aliases | file:line | purpose |
|---|---|---|---|
| /world | - | world-info.js:1616 | Sets active World, or unsets if no args; state=off / state=toggle to deactivate/toggle; silent=true suppresses toasts. |
| /getchatbook | /getchatlore, /getchatwi | world-info.js:1641 | Get a name of the chat-bound lorebook or create a new one if was unbound, and pass it down the pipe. |
| /getglobalbooks | /getgloballore, /getglobalwi | world-info.js:1666 | Get a list of names of the selected global lorebooks and pass it down the pipe. |
| /getpersonabook | /getpersonalore, /getpersonawi | world-info.js:1673 | Get a name of the current persona-bound lorebook and pass it down the pipe. |
| /getcharbook | /getcharlore, /getcharwi | world-info.js:1699 | Get a name of the character-bound lorebook and pass it down the pipe. |
| /findentry | /findlore, /findwi | world-info.js:1740 | Find a UID of the record from the specified book using fuzzy match of a field value (default: key) and pass it down the pipe. |
| /getentryfield | /getlorefield, /getwifield | world-info.js:1780 | Get a field value (default: content) of the record with the UID from the specified book and pass it down the pipe. |
| /createentry | /createlore, /createwi | world-info.js:1823 | Create a new record in the specified book with the key and content (both optional) and pass the UID down the pipe. |
| /setentryfield | /setlorefield, /setwifield | world-info.js:1859 | Set a field value (default: content) of the record with the UID from the specified book. |
| /wi-set-timed-effect | - | world-info.js:1905 | Set a timed effect for the record with the UID from the specified book. |
| /wi-get-timed-effect | - | world-info.js:1956 | Get the current state of the timed effect for the record with the UID from the specified book. |

**extensions/attachments/index.js** — 8 commands, EXTENSION (attachments / Data Bank)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /db | /databank, /data-bank | extensions/attachments/index.js:274 | Open the data bank |
| /db-list | /databank-list, /data-bank-list | extensions/attachments/index.js:284 | List attachments in the Data Bank as a JSON-serialized array. |
| /db-get | /databank-get, /data-bank-get | extensions/attachments/index.js:296 | Get attachment text from the Data Bank. |
| /db-add | /databank-add, /data-bank-add | extensions/attachments/index.js:316 | Add an attachment to the Data Bank. |
| /db-update | /databank-update, /data-bank-update | extensions/attachments/index.js:331 | Update an attachment in the Data Bank, preserving its name. |
| /db-disable | /databank-disable, /data-bank-disable | extensions/attachments/index.js:357 | Disable an attachment in the Data Bank by its name or URL. |
| /db-enable | /databank-enable, /data-bank-enable | extensions/attachments/index.js:375 | Enable an attachment in the Data Bank by its name or URL. |
| /db-delete | /databank-delete, /data-bank-delete | extensions/attachments/index.js:393 | Delete an attachment from the Data Bank. |

**extensions/caption/index.js** — 1 command, EXTENSION (caption)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /caption | - | extensions/caption/index.js:780 | Caption an image with an optional prompt and passes the caption down the pipe. |

**extensions/connection-manager/index.js** — 6 commands, EXTENSION (connection-manager)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /profile | - | extensions/connection-manager/index.js:893 | Switch to a connection profile or return the name of the current profile if no argument is provided. |
| /profile-list | - | extensions/connection-manager/index.js:964 | List all connection profile names. |
| /profile-create | - | extensions/connection-manager/index.js:971 | Create a new connection profile using the current settings. |
| /profile-update | - | extensions/connection-manager/index.js:1001 | Update the selected connection profile. |
| /profile-get | - | extensions/connection-manager/index.js:1020 | Get the details of the connection profile. |
| /profile-genstream | - | extensions/connection-manager/index.js:1049 | Generates text using Connection Manager with streaming display. |

**extensions/expressions/index.js** — 7 commands, EXTENSION (expressions)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /expression-set | /sprite, /emote | extensions/expressions/index.js:2397 | Force sets the expression for the current character. |
| /expression-fallback | - | extensions/expressions/index.js:2430 | Gets the currently selected global fallback expression. |
| /expression-folder-override | /spriteoverride, /costume | extensions/expressions/index.js:2466 | Sets an override sprite folder for the current character. |
| /expression-last | /lastsprite | extensions/expressions/index.js:2495 | Returns the last set expression for the named character. |
| /expression-list | /expressions | extensions/expressions/index.js:2510 | Returns a list of available expressions, including custom expressions. |
| /expression-classify | /classify | extensions/expressions/index.js:2549 | Performs an emotion classification of the given text and returns a label. |
| /expression-upload | /uploadsprite | extensions/expressions/index.js:2596 | Upload a sprite from a URL. |

**extensions/gallery/index.js** — 2 commands, EXTENSION (gallery)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /show-gallery | /sg | extensions/gallery/index.js:754 | Shows the gallery. |
| /list-gallery | /lg | extensions/gallery/index.js:763 | List images in the gallery of the current char / group or a specified char / group. |

**extensions/memory/index.js** — 1 command, EXTENSION (memory)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /summarize | - | extensions/memory/index.js:1084 | Summarizes the given text. |

**extensions/quick-reply/src/SlashCommandHandler.js** — 22 commands, EXTENSION (quick-reply)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /qr | - | extensions/quick-reply/src/SlashCommandHandler.js:78 | Activates the specified Quick Reply |
| /qrset | - | extensions/quick-reply/src/SlashCommandHandler.js:87 | DEPRECATED – The command /qrset has been deprecated. |
| /qr-set | - | extensions/quick-reply/src/SlashCommandHandler.js:94 | Toggle global QR set |
| /qr-set-on | - | extensions/quick-reply/src/SlashCommandHandler.js:114 | Activate global QR set |
| /qr-set-off | - | extensions/quick-reply/src/SlashCommandHandler.js:134 | Deactivate global QR set |
| /qr-chat-set | - | extensions/quick-reply/src/SlashCommandHandler.js:149 | Toggle chat QR set |
| /qr-chat-set-on | - | extensions/quick-reply/src/SlashCommandHandler.js:170 | Activate chat QR set |
| /qr-chat-set-off | - | extensions/quick-reply/src/SlashCommandHandler.js:190 | Deactivate chat QR set |
| /qr-set-list | - | extensions/quick-reply/src/SlashCommandHandler.js:205 | Gets a list of the names of all quick reply sets. |
| /qr-list | - | extensions/quick-reply/src/SlashCommandHandler.js:216 | Gets a list of the names of all quick replies in this quick reply set. |
| /qr-create | - | extensions/quick-reply/src/SlashCommandHandler.js:281 | Creates a new Quick Reply. |
| /qr-get | - | extensions/quick-reply/src/SlashCommandHandler.js:304 | Get a Quick Reply's properties. |
| /qr-update | - | extensions/quick-reply/src/SlashCommandHandler.js:345 | Updates Quick Reply. |
| /qr-delete | - | extensions/quick-reply/src/SlashCommandHandler.js:376 | Deletes a Quick Reply from the specified set. |
| /qr-contextadd | - | extensions/quick-reply/src/SlashCommandHandler.js:411 | Add a context menu preset to a QR. |
| /qr-contextdel | - | extensions/quick-reply/src/SlashCommandHandler.js:470 | Remove context menu preset from a QR. |
| /qr-contextclear | - | extensions/quick-reply/src/SlashCommandHandler.js:521 | Remove all context menu presets from a QR. |
| /qr-set-create | /qr-presetadd | extensions/quick-reply/src/SlashCommandHandler.js:571 | Create a new preset (overrides existing ones). |
| /qr-set-update | /qr-presetupdate | extensions/quick-reply/src/SlashCommandHandler.js:602 | Update an existing preset. |
| /qr-set-delete | /qr-presetdelete | extensions/quick-reply/src/SlashCommandHandler.js:627 | Delete an existing preset. |
| /qr-arg | - | extensions/quick-reply/src/SlashCommandHandler.js:652 | Set a fallback value for a Quick Reply argument ({{arg::name}}). |
| /import | - | extensions/quick-reply/src/SlashCommandHandler.js:680 | Import one or more closures from another Quick Reply. |

**extensions/regex/index.js** — 4 commands, EXTENSION (regex)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /regex-preset | - | extensions/regex/index.js:265 | Selects a regex preset by name or ID. |
| /regex | - | extensions/regex/index.js:2051 | Runs a Regex extension script by name on the provided string. |
| /regex-state | - | extensions/regex/index.js:2071 | Returns the current state of a regex script. |
| /regex-toggle | - | extensions/regex/index.js:2102 | Toggles the state of a specified regex script. |

**extensions/stable-diffusion/index.js** — 4 commands, EXTENSION (stable-diffusion)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /imagine | /sd, /img, /image | extensions/stable-diffusion/index.js:5492 | Requests to generate an image and posts it to chat (unless quiet=true argument is specified). |
| /imagine-source | /sd-source, /img-source | extensions/stable-diffusion/index.js:5727 | If an argument is provided, change the source of the image generation. |
| /imagine-style | /sd-style, /img-style | extensions/stable-diffusion/index.js:5759 | If an argument is provided, change the style of the image generation. |
| /imagine-comfy-workflow | /icw | extensions/stable-diffusion/index.js:5787 | Change the workflow to be used for image generation with ComfyUI. |

**extensions/token-counter/index.js** — 1 command, EXTENSION (token-counter)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /count | - | extensions/token-counter/index.js:112 | Counts the number of tokens in the current chat. |

**extensions/translate/index.js** — 1 command, EXTENSION (translate)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /translate | - | extensions/translate/index.js:778 | Translate text to a target language. |

**extensions/tts/index.js** — 1 command, EXTENSION (tts)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /speak | /narrate, /tts | extensions/tts/index.js:1582 | Narrate any text using currently selected character's voice. |

**extensions/vectors/index.js** — 9 commands, EXTENSION (vectors)

| name | aliases | file:line | purpose |
|---|---|---|---|
| /db-ingest | /databank-ingest, /data-bank-ingest | extensions/vectors/index.js:2100 | Force the ingestion of all Data Bank attachments. |
| /db-purge | /databank-purge, /data-bank-purge | extensions/vectors/index.js:2110 | Purge the vector index for all Data Bank attachments. |
| /db-search | /databank-search, /data-bank-search | extensions/vectors/index.js:2125 | Search the Data Bank for a specific query using vector similarity. |
| /vector-threshold | - | extensions/vectors/index.js:2184 | Set the vector score threshold or return the current threshold if no argument is provided. |
| /vector-query | - | extensions/vectors/index.js:2214 | Set the vector query messages or return the current query messages count if no argument is provided. |
| /vector-max-entries | - | extensions/vectors/index.js:2244 | Set the vector world info max entries or return the current max entries if no argument is provided. |
| /vector-chats-state | - | extensions/vectors/index.js:2274 | Set whether chat vectorization is enabled or return the current boolean if no argument is provided. |
| /vector-files-state | - | extensions/vectors/index.js:2300 | Set whether file vectorization is enabled or return the current boolean if no argument is provided. |
| /vector-worldinfo-state | - | extensions/vectors/index.js:2326 | Set whether world info vectorization is enabled or return the current boolean if no argument is provided. |

**Count per file** (all match `grep -c "SlashCommand.fromProps"`):

| Group | Files and counts | Total |
|---|---|---|
| Core | slash-commands.js 100, variables.js 37, power-user.js 12, world-info.js 11, personas.js 8, reasoning.js 8, bookmarks.js 7, extensions-slashcommands.js 6, authors-note.js 5, secrets.js 5, tags.js 5, action-loader-slashcommands.js 4, SlashCommandParser.js 4 (5 minus the `addCommand` wrapper), sysprompt.js 4, tool-calling.js 4, backgrounds.js 3, openai.js 1, preset-manager.js 1, scrapers.js 1 | 226 |
| Extensions | quick-reply 22, vectors 9, attachments 8, expressions 7, connection-manager 6, regex 4, stable-diffusion 4, gallery 2, caption 1, memory 1, token-counter 1, translate 1, tts 1 | 67 |

Grand total: **293** commands plus **176** aliases.

---

## 7. Characters, Groups, Personas & Chat Features

> Source: SillyTavern 1.19.0 (`package.json` `"version": "1.19.0"`). Paths are relative to the repo root; `script.js` = `public/script.js`, `scripts/` = `public/scripts/`, `src/` = server.

### 7.1 Characters

#### 7.1.1 In-memory character object (`characters[]`, `script.js:427`)
Type: `Character = v1CharData` (`public/global.d.ts:22`, `scripts/char-data.js:105-121`). The server builds it as a V1 object with the V2 `data` mirrored inside (`src/endpoints/characters.js:504-554` readFromV2, `:406-425` processCharacter).

| Field | Meaning / source | Cite |
|---|---|---|
| `name, description, personality, scenario, first_mes, mes_example` | V1 top-level fields, hoisted from `data.*` on read | char-data.js:105-112; characters.js:513-552 |
| `creatorcomment` | legacy creator notes (V1) | char-data.js:111; characters.js:588 |
| `tags` (string[]) | card tags (not the ST tag system) | characters.js:593 |
| `talkativeness` (0..1, default 0.5), `fav` (bool) | hoisted from `data.extensions.*`, defaults backfilled | characters.js:520-538; script.js:549 |
| `create_date`, `date_added` (ctimeMs), `date_last_chat`, `chat_size`, `data_size` | server-computed stats | characters.js:416-424 |
| `avatar` | PNG filename; **unique key** for a character | char-data.js:118; characters.js:413 |
| `chat` | current chat file name (no `.jsonl`); default `"<name> - <humanizedDateTime>"` | script.js:1300-1307; characters.js:554 |
| `json_data` | full raw card JSON string | characters.js:415 |
| `shallow` | true when lazy-loaded | char-data.js:120; characters.js:370-372 |
| `spec`, `spec_version` | `chara_card_v2` / `2.0` on write | characters.js:596-597 |
| `data.{name, description, personality, scenario, first_mes, mes_example, creator_notes, system_prompt, post_history_instructions, tags, creator, character_version, alternate_greetings[], character_book}` | V2 spec fields | char-data.js:51-68; characters.js:598-612 |
| `data.extensions.talkativeness / fav / world` | ST extension fields (`world` = linked lorebook name) | char-data.js:70-73; characters.js:615-617 |
| `data.extensions.depth_prompt {prompt, depth (default 4), role (default 'system')}` | character's note @ depth | characters.js:619-626; script.js:550-551 |
| `data.extensions.regex_scripts[]` | scoped regex (RegexScriptData: id, scriptName, findRegex, replaceString, trimStrings, placement[], disabled, markdownOnly, promptOnly, runOnEdit, substituteRegex, minDepth, maxDepth) | char-data.js:79, 88-102 |
| `data.extensions.{pygmalion_id, github_repo, source_url, chub.full_path, risuai.source, perchance_data.slug, sd_character_prompt}` | external source links (used by `getCharacterSource`) | char-data.js:80-86; script.js:1246-1292 |

`character_book` entries (V2 world-info) shape: `char-data.js:1-46`; WI→book conversion on save if `world` set: `characters.js:628-644, 662+`.

#### 7.1.2 V2/V3 card handling
| Aspect | Behavior | Cite |
|---|---|---|
| PNG read | tEXt chunk `ccv3` takes precedence over `chara`; base64 JSON | src/character-card-parser.js:50-70 |
| PNG write | removes old `chara`/`ccv3`, writes `chara` (V2) **and** `ccv3` copy with `spec='chara_card_v3', spec_version='3.0'` | character-card-parser.js:16-44 |
| V1→V2 | `getCharaCardV2`: no `spec` → `convertToV2`, else `readFromV2` hoist | characters.js:450-461 |
| Form→card | `charaFormatData` writes V1 + V2 fields, deep-merges `extensions` JSON from form | characters.js:565-655 |
| Import formats | `json, png, yaml, yml, charx, byaf` | script.js:10536-10537; characters.js:1560-1574 |
| Export formats | `png`, `json` | characters.js:1658-1670; script.js:12040-12068 |

#### 7.1.3 Lazy (shallow) loading
- Server flag `performance.lazyLoadCharacters` (config.yaml) → `/api/characters/all` returns `toShallow()` objects: `{shallow:true, name, avatar, chat, fav, date_added, create_date, date_last_chat, chat_size, data_size, tags, data:{name, character_version, creator, creator_notes, tags, extensions:{fav, world}}}` (characters.js:34-35, 370-394, 1466-1470).
- `unshallowCharacter(chid)` → `getOneCharacter(avatar)` → `POST /api/characters/get {avatar_url}` replaces the array slot (script.js:7607-7632, 1223-1245). Called before chat load (7636), before Generate (4295-4296), before chat delete (1366), for all group members (group-chats.js:1378-1394).
- Overflow without lazy-load: `/all` error with `overflow` → popup instructing to enable lazyLoadCharacters (script.js:1328-1333).

#### 7.1.4 CRUD flow & endpoints (all `POST`, JSON unless noted, header `X-CSRF-Token`)
| Endpoint | Frontend caller | Body / notes | Cite |
|---|---|---|---|
| `/api/characters/all` | `getCharacters()` | `{}`; sanitizes `name` with DOMPurify, reselects previous avatar, then `getGroups()` + `printCharacters` | script.js:1294-1335; characters.js:1466 |
| `/api/characters/get` | `getOneCharacter` | `{avatar_url}` | script.js:1223-1245; characters.js:1480 |
| `/api/characters/create` | `createOrEditCharacter` (actiontype `createcharacter`) | multipart FormData from `#form_create` + `alternate_greetings` (repeated) + `extensions` JSON; optional `?crop=` JSON; blocked while generating | script.js:9747-9810; characters.js:1024 |
| `/api/characters/edit` | `createOrEditCharacter` (edit) | multipart; then `getOneCharacter`, `CHARACTER_EDITED`; if chat untainted & only greeting → regenerates first message | script.js:9867-9921; characters.js:1101 |
| `/api/characters/edit-attribute` | (server only; no 1.19 frontend caller) | `{avatar_url, ch_name, field, value}`; writes `char[field]` and `char.data[field]`; `json_data` forbidden | characters.js:1193-1231 |
| `/api/characters/merge-attributes` | extensions/WI/slash/bulk-edit | single: `{avatar, ...partialCard}`; bulk: `{avatars[], data, filter?{path}}` (empty `avatars` = all); value `'__@@UNSET@@__'` deletes a key; validated by TavernCardValidator | characters.js:1243-1300, 1328-1412; script.js:10785; BulkEditOverlay.js:84 |
| `/api/characters/edit-avatar` | slash cmd | multipart avatar replace | characters.js:1142; slash-commands.js:5158 |
| `/api/characters/rename` | `renameCharacter()` | `{avatar_url, new_name}` → `{avatar}`; then renames tag_map key, `world_info.charLore[].name`, `extension_settings.note.chara[].name`, `active_character`; `renameGroupMember`; optional `renamePastChats` (rewrites `message.name` in every past chat via get/save) | script.js:7185-7300, 7302-7358; characters.js:1053 |
| `/api/characters/duplicate` | `duplicateCharacter()` | `{avatar_url}` → `{path}`; emits `CHARACTER_DUPLICATED` | script.js:6034-6081; characters.js:1601 |
| `/api/characters/delete` | `deleteCharacter(keys, {deleteChats})` | `{avatar_url, delete_chats}`; removes accountStorage `AlertWI_*`, `AlertRegex_*`, `mediaWarningShown:*`, deletes `tag_map[avatar]`, emits `CHAT_DELETED` per chat & `CHARACTER_DELETED` | script.js:10828-10885; characters.js:1416 |
| `/api/characters/chats` | `getPastCharacterChats`, `replaceCurrentChat` | `{avatar_url, metadata?}` → `ChatInfo[] {file_id, file_name, file_size, chat_items, mes, last_mes, chat_metadata?}` | script.js:8510, 1386-1410; characters.js:1499-1534; chats.js:372-380 |
| `/api/characters/import` | `importCharacter(file)` | FormData `{avatar, file_type, user_name, preserved_name?}` → `{file_name}`; tag import if `tag_import_setting != NONE` | script.js:10530-10590, 10503-10505; characters.js:1560-1594 |
| `/api/characters/export` | export popup | `{format:'png'|'json', avatar_url}` → blob; saves character first | script.js:12040-12068; characters.js:1646 |

Create form defaults object `create_save` (name, description, creator_notes, post_history_instructions, character_version, system_prompt, tags, creator, personality, first_message, avatar, scenario, mes_example, world, talkativeness, alternate_greetings[], depth_prompt_prompt/depth/role, extensions{}, extra_books[]): script.js:570-593.

#### 7.1.5 Favorites
`fav` sent as form field from `fav_ch_checked` (script.js:459, 9756); toggled via `updateFavButtonState` (script.js:8986-8990, 11474); stored at `fav` and `data.extensions.fav` (characters.js:592, 616); hotswap bar `favsToHotswap()` (RossAscends-mods.js:305); filter `FILTER_TYPES.FAV` / actionable tag FAV (filters.js:19; tags.js:269-276).

#### 7.1.6 First message, alternate greetings as swipes
- `getFirstMessage()` (script.js:7712-7743): `mes = getRegexedString(first_mes, AI_OUTPUT)`; if `data.alternate_greetings` non-empty → `swipes=[first_mes, ...greetings]` (each regexed), `swipe_id=0`, `swipe_info[]` with `{send_date, gen_started:undefined, gen_finished:undefined, extra:{}}`; empty `first_mes` → shift so first greeting becomes `mes`.
- Inserted only when chat is empty in `getChatResult()` then `saveChatConditional()`; emits `CHAT_CREATED`, `MESSAGE_RECEIVED`/`CHARACTER_MESSAGE_RENDERED` with type `'first_message'` (script.js:7684-7710).
- Group first messages: random pick among `[first_mes, ...alternate_greetings]`, then `CHARACTER_FIRST_MESSAGE_SELECTED` event `{input, output, character}` lets extensions override (`output` wins); `substituteParams(..., {name2Override})`; sets `original_avatar`, `force_avatar`, `extra.gen_id` (group-chats.js:578-609; events.js:66).
- Pristine greeting: `chat_metadata.tainted` absent ⇒ greeting swipes loop (`OVERSWIPE_BEHAVIOR.PRISTINE_GREETING`) and swipe text is not synced back so macros re-resolve (script.js:6932, 9220-9235; constants.js:147-155).

#### 7.1.7 Name substitution
`substituteParams(content, {name1Override, name2Override, original, groupOverride, replaceCharacterCard, dynamicMacros, postProcessFn})` → new macro engine if `power_user.experimental_macro_engine` (default true, power-user.js:302) else `substituteParamsLegacy` (script.js:2981-3010, 2831). Message #0 is re-substituted at render time and written back to `chat[0].mes` if unchanged (script.js:1805-1812). `getCharacterCardFields[Lazy]` provides card fields for prompts (script.js:3402, 3476).

### 7.2 Groups (`scripts/group-chats.js`)

#### 7.2.1 Group object (`public/global.d.ts:26-43`; server create `src/endpoints/groups.js:156-187`)
| Field | Type / default | Notes |
|---|---|---|
| `id` | `String(Date.now())` | file `groups/<id>.json` (groups.js:162, 180) |
| `name` | default `"Group: <member names>"` | group-chats.js:2096-2098 |
| `members` | avatar filenames[] (order = list order) | |
| `disabled_members` | avatar[] (muted) | |
| `avatar_url` | data URL or `img/ai4.png` | group-chats.js:2100, 2104 |
| `allow_self_responses` | bool | |
| `activation_strategy` | `NATURAL=0, LIST=1, MANUAL=2, POOLED=3` (default 0) | group-chats.js:122-127 |
| `generation_mode` | `SWAP=0, APPEND=1, APPEND_DISABLED=2` (default 0) | group-chats.js:129-133 |
| `generation_mode_join_prefix/suffix` | strings, `<FIELDNAME>` + macros | groups.js:180-181 |
| `auto_mode_delay` | seconds, default `5` | group-chats.js:135 |
| `hideMutedSprites` | bool | group-chats.js:1771-1779 |
| `fav` | bool | |
| `chat_id` | current chat file name (humanizedDateTime) | group-chats.js:2101-2102 |
| `chats` | chat ids[] (deduped by `validateGroup`) | group-chats.js:218-247 |
| `date_last_chat` | set on save | group-chats.js:630 |
Deprecated `chat_metadata`/`past_metadata` keys are stripped server-side (groups.js:18-28).

#### 7.2.2 Endpoints
| Endpoint | Use | Cite |
|---|---|---|
| `POST /api/groups/all` | list groups (`getGroups`) | group-chats.js:758-798; groups.js:113 |
| `POST /api/groups/create` | `createGroup()` model above → returns group | group-chats.js:2087-2135; groups.js:156 |
| `POST /api/groups/edit` | `_save(group)` full object; `editGroup(id, immediately)` else debounced 1000 ms | group-chats.js:140, 155-165, 1359-1371; groups.js:190 |
| `POST /api/groups/delete` | `{id}` | group-chats.js:1323-1357; groups.js:203 |
| `POST /api/chats/group/get` | `{id: chat_id}` → JSONL array (header first) | group-chats.js:195-212; chats.js:872 |
| `POST /api/chats/group/save` | `{id, chat:[header,...chat], force}` (gzip-compressed); integrity error → "OVERWRITE" prompt | group-chats.js:623-681; chats.js:922 |
| `POST /api/chats/group/delete` | `{id}` | group-chats.js:2242-2317; chats.js:900 |
| `POST /api/chats/group/info` | past chat info | group-chats.js:2162-2193; chats.js:883 |
| `POST /api/chats/group/import` | import chat | group-chats.js:2319-2357; chats.js:751 |

#### 7.2.3 Group chat load/save
`getGroupChat(groupId)` (group-chats.js:255-322): `validateGroup` (drop missing members, dedupe chats) → `unshallowGroupMembers` → load; fresh = `!metadata.tainted && no messages` → push each member's first message, `saveGroupChat`; else print messages; mint `metadata.integrity = uuidv4()` if missing; `updateChatMetadata(metadata, true)`; emit `CHAT_CHANGED` (+`GROUP_CHAT_CREATED`). New chat: `createNewGroupChat` pushes humanizedDateTime into `chats`, sets `chat_id`, saves group (2140-2155).

#### 7.2.4 `generateGroupWrapper(byAutoMode, type, params)` flow (group-chats.js:945-1092)
Entry: `Generate()` in a group with `!is_group_generating` returns `generateGroupWrapper(false, type, {quiet_prompt, force_chid, signal, quietImage, jsonSchema})` (script.js:4350-4354).
1. Abort if `online_status==='no_connection'` or already `is_group_generating` (953-962).
2. Empty group → system message `EMPTY` (972-975).
3. `unshallowGroupMembers`, `hideSwipeButtons`, `is_group_generating=true`, clear `name2`/`this_chid`; `group_generation_id = Date.now()` (978-987).
4. Activation text = textarea input if user typed and not auto mode (`isUserInput=true`), else last non-system message text (992-1000).
5. Member selection (1003-1029):

| Condition | Function | Algorithm | Cite |
|---|---|---|---|
| `params.force_chid` number | — | `[force_chid]` (member "speak" button uses `Generate('normal',{force_chid})`) | 1006-1007; 1996-2001 |
| `type==='quiet'` | `activateSwipe(members,{allowSystem:true})[0]`, fallback first member | | 1008-1013 |
| `type==='swipe'/'continue'` | `activateSwipe` | last msg's `original_avatar` (or name match for legacy); if last is user/system/narrator → most recent non-user `original_avatar`, else random member; empty → toast + throw | 1130-1172 |
| `type==='impersonate'` | `activateImpersonate` | one random member (incl. disabled) | 1114-1122 |
| NATURAL | `activateNaturalOrder(enabled, text, lastMsg, allow_self_responses, isUserInput)` | `bannedUser = lastMessage.name` if not user input and last msg is from a char (unless `allow_self_responses`); (a) mentions: for each word in `extractAllWords(input)`, first non-banned member whose `extractAllWords(name)` contains it; (b) shuffled members: activate if `talkativeness (default 0.5) >= Math.random()`; collect `chattyMembers` (talkativeness>0); (c) if none → random from chatty pool (or all), up to pool-size retries; dedupe | 1242-1320 |
| LIST | `activateListOrder` | all enabled members in list order (unique) | 1180-1188 |
| POOLED | `activatePooledOrder` | walk back to last user msg (or none if user input now), collect `original_avatar`s who spoke; pick random among enabled who haven't; else random excluding last speaker (if >1 member) | 1197-1233 |
| MANUAL | — | only when not user input (auto mode/regenerate): 1 random enabled member; with user input → nobody | 1027-1028 |

6. If nobody activated: send user message as-is (`sendMessageAsUser`), save, clear input (1031-1038).
7. `show_group_chat_queue` → queue numbers map (1039-1045); emit `GROUP_WRAPPER_STARTED`.
8. Loop each chId: `setCharacterId`, `setCharacterName`, emit `GROUP_MEMBER_DRAFTED`, `await Generate(type ∈ {swipe,impersonate,quiet,continue} ? type : 'normal', {automatic_trigger: byAutoMode, ...params})`; loop `Generate('continue')` while `shouldAutoContinue` (1047-1069).
9. `finally`: reset flags, `activateSendButtons`, `showSwipeButtons`, emit `GROUP_WRAPPER_FINISHED` (1070-1089).
- No typing indicator exists in 1.19 core (none found in `script.js`/`scripts/*.js`); the visible "queue" is the `show_group_chat_queue` option (power-user.js:187).
- Group replies stored with `force_avatar`, `original_avatar`, `extra.gen_id = group_generation_id` (script.js:6766-6776). Regenerate deletes trailing messages sharing last `gen_id` then re-runs (group-chats.js:167-189).

#### 7.2.5 Generation mode (card joining)
- SWAP: only active char's card; no group depth prompts (group-chats.js:439-441).
- APPEND / APPEND_DISABLED: `getGroupCharacterCardsLazy` (497-571) joins `description, personality, scenario, mesExamples` of members with `\n`; disabled members skipped except current speaker unless APPEND_DISABLED; each value: `prefix + trimmed(value) + suffix`, where prefix/suffix = join templates with `<FIELDNAME>` (case-insensitive) → `Description|Personality|Scenario|Example Messages`, then `baseChatReplace(..., characterName)` (506-536). `chat_metadata.scenario` / `chat_metadata.mes_example` override joined scenario/examples; examples get `<START>\n` prefix if missing (559-570).
- Group depth prompts: each (non-disabled or current) member's `depth_prompt` (427-468).

#### 7.2.6 Auto mode, member management
- Auto mode is runtime-only (`is_group_automode_enabled`, not persisted); `setInterval(groupChatAutoModeWorker, auto_mode_delay*1000)` (111, 144-148); worker calls `generateGroupWrapper(true,'auto')` if idle (1398-1416); typing in textarea or stop disables it (2397-2412).
- Member actions `remove|add|enable|disable|up|down|view|speak` (1956-2004); add = `members.unshift` (1423-1459); reorder swaps neighbors (1461-1489); `view` opens character definition (2055).
- `renameGroupMember` rewrites members & chats on character rename (683-756).

### 7.3 Personas (`scripts/personas.js`)

#### 7.3.1 Data model (stored in `power_user`, persisted via settings)
| Key | Shape | Cite |
|---|---|---|
| `power_user.personas` | `{ [avatarFile]: name }` | power-user.js:286 |
| `power_user.default_persona` | avatarFile or null | power-user.js:287 |
| `power_user.persona_descriptions` | `{ [avatarFile]: {description, position, depth (default 2), role (0=system default), lorebook, connections: [{type:'character'|'group', id: avatar|groupId}], title} }` | power-user.js:288; personas.js:71-75, 104-105, 1064-1072 |
| Active copies | `persona_description`, `persona_description_position` (IN_PROMPT), `_role` (0), `_depth` (2), `_lorebook` | power-user.js:290-294; personas.js:908-920 |
| Flags | `persona_show_notifications` (true), `persona_sort_order` ('asc'), `persona_allow_multi_connections`, `persona_auto_lock` | power-user.js:295-296, 3853-3866 |
| Runtime | `user_avatar` (current persona key); `name1` (user name) | personas.js:108 |

`persona_description_positions`: `IN_PROMPT=0, AFTER_CHAR=1 (deprecated → migrated to IN_PROMPT), TOP_AN=2, BOTTOM_AN=3, AT_DEPTH=4, NONE=9` (personas.js:88-98, 626-628). Slash names: `inprompt|topan|bottoman|atdepth|none`; roles `system=0,user=1,assistant=2` (1966-1985).

#### 7.3.2 Prompt application
`addPersonaDescriptionExtensionPrompt()` (script.js:3203-3225): clears `PERSONA_DESCRIPTION` ext prompt; NONE/empty → skip; TOP_AN/BOTTOM_AN → prepend/append to the AN extension prompt (only when AN fires, `shouldWIAddPrompt`); AT_DEPTH → `setExtensionPrompt('PERSONA_DESCRIPTION', desc, IN_CHAT, depth, scan=true, role)`. IN_PROMPT → passed as `persona` to story string (script.js:4706).

#### 7.3.3 Selection & locking
- Lock types `'chat' | 'character' | 'default'` (personas.js:77): chat → `chat_metadata.persona = user_avatar`; character → add `{type,id}` connection (removing same connection from other personas unless `persona_allow_multi_connections`); default → `power_user.default_persona` (965-1138, 1359+).
- `loadPersonaForCurrentChat()` priority (1543-1666): (1) `chat_metadata.persona` if exists; (2) connected personas to current char/group (`getConnectedPersonas`, 1674-1681) — 1 → use; >1 → first (if multi disabled) or popup; (3) `default_persona`. Missing avatars cleared. If `persona_auto_lock` → lock to chat. Notifications toasts if `persona_show_notifications`.
- `selectCurrentPersona` sets `name1` from `personas[user_avatar]`, loads descriptor, auto-locks (897-958).
- User messages: `sendMessageAsUser(text, bias, insertAt, compact, name=name1, avatar=user_avatar)`: regex USER_INPUT → `substituteParams`; `force_avatar = getThumbnailUrl('persona', avatar)` if avatar is a persona; `extra.bias`, `extra.isSmallSys`, optional `token_count`; attachments; stats; `tainted=true` (script.js:5874-5910).
- Avatar endpoints: `/api/avatars/get` (personas.js:276), `/api/avatars/upload` (370, 2040), `/api/avatars/delete` (1170). Backup JSON `{personas, persona_descriptions, default_persona}` (1743-1754).
- Slash commands (registered personas.js:2472-2919): `/persona` (persona-set alias `persona`,`name`, mode arg), `/persona-create`, `/persona-update`, `/persona-get` (alias `persona-data`), `/persona-delete`, `/persona-duplicate`, `/persona-lock` (aliases `lock`,`bind`; type chat|character|default), `/persona-sync` (alias `sync`; rewrites user message names).

### 7.4 Author's Note (`scripts/authors-note.js`)
| Item | Value | Cite |
|---|---|---|
| Extension prompt key | `'2_floating_prompt'` (`NOTE_MODULE_NAME`) | authors-note.js:20, 26 |
| chat_metadata keys | `note_prompt, note_interval, note_depth, note_position, note_role` | 30-36 |
| Defaults (`extension_settings.note`) | `default` text, `defaultInterval=1`, `defaultPosition=1` (IN_CHAT), `defaultDepth=4`, `defaultRole=0` (SYSTEM), `allowWIScan`, `chara[]`, `wiAddition[]` | 271-291; extensions.js:149-153 |
| Per-chat init | each key `= chat_metadata[k] ?? default` | 292-296 |
| Character note | `extension_settings.note.chara[] = {name: getCharaFilename(), prompt, useChara, position}`; position enum `replace=0, before=1, after=2`; disabled in groups | 38-42, 202-245, 441-448 |
| Position values | `extension_prompt_types`: `NONE=-1, IN_PROMPT=0 (after scenario), IN_CHAT=1 (@depth), BEFORE_PROMPT=2` | script.js:484-489 |
| Roles | `SYSTEM=0, USER=1, ASSISTANT=2` | script.js:494-498 |

`setFloatingPrompt()` (authors-note.js:324-391), called each Generate (script.js:4619):
1. `lastMessageNumber` = count of `is_user` messages; if `note_interval === 1` force 1 (330-349).
2. `<=0` count or interval → disable (`NONE`) (351-356).
3. `messagesTillInsertion = n >= interval ? n % interval : interval - n`; insert when 0 (`shouldWIAddPrompt`) (358-362).
4. Prompt = textarea; if char note `useChara`: before → `chara + '\n' + prompt`, after → `prompt + '\n' + chara`, else replace (364-381).
5. `setExtensionPrompt('2_floating_prompt', prompt, note_position, note_depth, allowWIScan, note_role)` (383-390).
Slash: `/note`, `/note-depth` (`depth`), `/note-frequency` (`freq`,`note-freq`), `/note-position` (`pos`,`note-pos`; `after|scenario=0`, `chat=1`, `before|before_scenario=2`), `/note-role` (authors-note.js:80-124, 501-581). Macros `{{authorsNote}}`, `{{charAuthorsNote}}`, `{{defaultAuthorsNote}}` (587-619).

### 7.5 Bookmarks / checkpoints / branches (`scripts/bookmarks.js`)
| Feature | Behavior | Cite |
|---|---|---|
| Checkpoint `createNewBookmark(mesId,{forceName})` | name suggestion `"<chat> - Checkpoint #N"`; saves `chat[0..mesId]` as new file with metadata `{main_chat: currentChat, integrity: uuidv4()}` (solo: `saveChat({chatName, withMetadata, mesId})`; group: `saveGroupBookmarkChat`); copies itemized prompts; sets `chat[mesId].extra.bookmark_link = name`; re-saves current chat | bookmarks.js:47, 87-111, 255-301 |
| Branch `createBranch(mesId,{swipeId})` | name `"<chat> - Branch #N"`; snapshot (optionally with chosen swipe applied via `syncSwipeToMes`); metadata `{main_chat, integrity}`; pushes name into `chat[mesId].extra.branches[]` | 172-246 |
| `branchChat` | createBranch + open it | 452-472 |
| Back to parent | `chat_metadata.main_chat` (legacy: parse name before "Checkpoint #") → open | 113-127, 315-329 |
| Solo→group | `convertSoloToGroupChat` creates group via `/api/groups/create` and saves chat via `/api/chats/group/save` (drops `main_chat`) | 331-450 |
| Endpoints | list via `/api/characters/chats` or group chats; save via `/api/chats/save` / `/api/chats/group/save` | 53-85, 381, 431; group-chats.js:2359-2395 |
| Slash | `/branch-create`, `/checkpoint-create`, `/checkpoint-go`, `/checkpoint-exit`, `/checkpoint-parent`, `/checkpoint-get`, `/checkpoint-list` | 495-675 |

Chat rename: `renameGroupOrCharacterChat` → `POST /api/chats/rename {is_group, avatar_url, original_file:'x.jsonl', renamed_file:'y.jsonl'}` → may return `sanitizedFileName`; updates `characters[].chat` or group `chats`; emits `CHAT_RENAMED` (script.js:10650-10720; chats.js:621).

### 7.6 `scripts/chats.js` features
| Feature | Details | Cite |
|---|---|---|
| Hide/unhide | `hideChatMessageRange(start,end,unhide,nameFilter)` sets `message.is_system = hide` (hidden msgs excluded from prompt), saves | chats.js:147-196 |
| File attachments | `populateFileAttachment`: media (image/video/audio) → `saveBase64AsFile` (`/api/images/upload`) → `extra.media[] {url,type,title,source:'upload'}`, `media_index`, `inline_image=true`; other files → convert (PDF, HTML, MD, EPUB, DOCX/XLSX/PPTX, ODT/ODP/ODS) to text → `/api/files/upload {name,data(base64)}` → `extra.files[] {url,size,name,created}`; limit 350 MB | chats.js:76-97, 198-301; utils.js:1661 |
| Prompt inclusion | `appendFileContent`: prepend file texts + `\n\n`; sets `extra.fileLength` | chats.js:508-528; script.js:4507 |
| Embed into existing message | `embedMessageFile` → `MESSAGE_FILE_EMBEDDED` | chats.js:468-500 |
| Data Bank | sources `global` (`extension_settings.attachments`), `chat` (`chat_metadata.attachments`), `character` (`extension_settings.character_attachments[avatar]`); disabled list `extension_settings.disabled_attachments` (urls); endpoints `/api/files/delete`, `/api/files/verify` | chats.js:77-81, 1750-1815, 1130, 1832 |
| Style tags | `encodeStyleTags` `<style>`→`<custom-style>` URI-encoded; `decodeStyleTags` scopes selectors with `.mes_text ` prefix | chats.js:536-560 |
| External media | DOMPurify hook removes external `img/audio/video/source/track/embed/object` unless allowed (`forbid_external_media` + per-entity overrides) | chats.js:1901-2060; power-user.js:337-339 |
| Neutral chat | temp chat kept in `sessionStorage['neutralChat']` | chats.js:1855-1880 |

`chat-backups.js`: "Backups browser" UI listing server-side chat backups: `/api/backups/chat/get` (list `chat_*.jsonl` in backups dir), `/download` (view/restore → re-import via `importCharacterChat`/`importGroupChat`), `/delete` (chat-backups.js:22-160, 326-335; src/endpoints/backups.js:9-70). Server creates throttled backups on every save (`backups.chat.enabled`, `maxTotalBackups`, `throttleInterval=10000`, `checkIntegrity`) (src/endpoints/chats.js:27-30, 59-98).

### 7.7 Tags (`scripts/tags.js`)
| Item | Details | Cite |
|---|---|---|
| Tag object | `{id (uuid), name, folder_type?, filter_state?, sort_order?, color?, color2?, create_date?, is_hidden_on_character_card?}` (+ UI-only `action, class, icon, title`) | tags.js:323-339 |
| `tags[]` | global list; defaults: Plain Text, OpenAI, W++, Boostyle, PList, AliChat | 293-300, 345 |
| `tag_map` | `{ [entityKey: avatar or groupId]: tagId[] }` | 351 |
| Persistence | both saved in settings payload; loaded by `loadTagsSettings` | 614-617; script.js:8086-8087 |
| Folder types | `OPEN` (show all chars), `CLOSED` (hide unless selected), `NONE` (default) | 316-321 |
| Actionable filters | FAV(id 1), GROUP(0), FOLDER(4), VIEW(2), HINT(3), UNFILTER(5) | 269-276 |
| Filter states | `SELECTED / EXCLUDED / UNDEFINED`; filter types `search, tag, folder, fav, group, world_info_search, persona_search` | filters.js:15-41 |
| Filter persistence | accountStorage `TagFilterState_{GROUP,FAV,FOLDER}` and `<CharacterList|GroupCandidates|GroupMembers>_tag_<id>` | tags.js:166-189, 1379, 1423 |
| Import from cards | `tag_import_setting`: `ASK=1, NONE=2, ALL=3, ONLY_EXISTING=4`; excludes `ROOT`,`TAVERN`; max 50; also adds open folder tags | 246-251, 959-1042 |
| Sort | `tag_sort_mode`: manual, alphabetical, by_entries | 254-258 |
| Rename key on char rename | `renameTagKey` | 619-624 |
| Slash | `/tag-add, /tag-remove, /tag-exists, /tag-list, /tag-import` | 2352-2527 |

### 7.8 Stats (`scripts/stats.js`)
Per-character (keyed by avatar) `{total_gen_time, user_word_count, non_user_word_count, user_msg_count, non_user_msg_count, total_swipe_count, date_first_chat, date_last_chat}`; `statMesProcess` fetches `/api/stats/get`, increments (continue/append counts only word delta; swipe increments `total_swipe_count`; gen time = `gen_finished - gen_started`), posts whole map to `/api/stats/update`; `/api/stats/recreate` rebuilds (stats.js:178-255, 271-324).

### 7.9 Itemized prompts (`scripts/itemized-prompts.js`)
- Stored client-side only in IndexedDB via localforage instance `SillyTavern_Prompts`, key = chat id, value = array (itemized-prompts.js:15-57). Loaded on chat load (script.js:7698), saved in `saveChatConditional` (script.js:9433), copied for checkpoints/branches (bookmarks.js:286, 464).
- Per-entry fields (script.js:5340-5374): `...thisPromptBits[last]`, `rawPrompt, mesId, allAnchors, chatInjects, summarizeString ('1_memory'), authorsNoteString ('2_floating_prompt'), smartContextString, chatVectorsString ('3_vectors'), dataBankVectorsString ('4_vectors_data_bank'), worldInfoString, storyString, beforeScenarioAnchor, afterScenarioAnchor, examplesString, mesSendString, generatedPromptCache, promptBias, finalPrompt, charDescription, charPersonality, scenarioText, this_max_context, padding, main_api, instruction, userPersona, tokenizer, presetName, messagesCount, examplesCount`. Replaced if same `mesId` (5376-5381).
- Other localforage stores: `SillyTavern_ChatCompletions` (token cache, tokenizers.js:157), `SillyTavern_TextCompletions` (samplerSelect.js:22), `SillyTavern_Thumbnails` (backgrounds.js:42).

### 7.10 Message formatting pipeline
`messageFormatting(mes, ch_name, isSystem, isUser, messageId, sanitizerOverrides, isReasoning)` (script.js:1769-1971; documented order at 1771-1785):
1. Msg #0 (char): `substituteParams` and write-back (1805-1812).
2. Comment/hidden normalization: hidden non-system-user msgs get markdown (1817-1824).
3. Strip `user_prompt_bias` (Start Reply With) unless `show_user_prompt_bias` (1827-1830).
4. `MessageFormatter` stage `beforeRegex` → `getRegexedString(mes, placement, {characterOverride, isMarkdown:true, depth})` where placement = REASONING / USER_INPUT / SLASH_COMMAND (narrator) / AI_OUTPUT; depth counts only non-system msgs → stage `afterRegex` (1832-1867).
5. `auto_fix_generated_markdown` → `fixMarkdown(mes, true)` (removes spaces inside `*`/`_` pairs; closes odd `*`/`"` per line) (1869-1871; power-user.js:429-466).
6. `encode_tags` → escape `<` and `>` (not at line start, for blockquotes) (1873-1877).
7. Escape reasoning prefix/suffix strings (1880-1889).
8. Quote wrapping into `<q>` for `"…"`, `“…”`, `«…»`, `「…」`, `『…』`, `＂…＂`, skipping code/`<style>`; quotes in tags protected with `￾` (1891-1928).
9. `\begin{align*}`/`\end{align*}` → `$$`; Showdown `converter.makeHtml`; fix `<code>` newlines and `&amp;` (1930-1942).
10. Stage `afterMarkdown` (1944-1946).
11. Strip `Name:` prefixes unless `allow_name2_display` (1949-1951).
12. DOMPurify config `{RETURN_DOM:false, RETURN_DOM_FRAGMENT:false, RETURN_TRUSTED_TYPE:false, MESSAGE_SANITIZE:true, ADD_TAGS:['custom-style'], ...overrides}` wrapped by `encodeStyleTags` / `decodeStyleTags` (1957-1968).

| Component | Details | Cite |
|---|---|---|
| Showdown options | `emoji, literalMidWordUnderscores, parseImgDimensions, tables, underline, simpleLineBreaks, strikethrough, disableForced4SpacesIndentedSublists` (all true) | script.js:521-532 |
| `showdown-underscore` | output ext: `_text_` → `<em>` outside `<code>/<style>` (needs lookbehind) | showdown-underscore.js |
| `showdown-exclusion` | lang ext: lines starting with any of `power_user.markdown_escape_strings` (comma list, macros) are escaped from markdown | showdown-exclusion.js; script.js:536 |
| DOMPurify hooks | links get `target=_blank rel=noopener`; classes prefixed `custom-` except `fa-*`, `note-*`, `monospace`; unknown elements newline→`<br>`; external media blocking | chats.js:1901-2060 |
| MessageFormatter hooks | stages `beforeRegex, afterRegex, afterMarkdown`; order `EARLIEST 0…LATEST 100`; sync only | message-formatter.js |
| Markdown hotkeys | `enable_md_hotkeys`, textarea `.mdHotkeys`: Ctrl+B `**`, Ctrl+I `*`, Ctrl+U `__`, Ctrl+K `` ` ``, Ctrl+Shift+` `~~` (toggle wrap) | input-md-formatting.js:2-60 |

### 7.11 Regex hooks (`scripts/extensions/regex/engine.js`)
- `regex_placement`: `MD_DISPLAY=0 (deprecated), USER_INPUT=1, AI_OUTPUT=2, SLASH_COMMAND=3, (4 legacy sendAs), WORLD_INFO=5, REASONING=6` (engine.js:281-292). `substitute_find_regex`: `NONE=0, RAW=1, ESCAPED=2` (298-302).
- Script sources/priority: GLOBAL (`extension_settings.regex`), SCOPED (character `regex_scripts`, only if avatar in `extension_settings.character_allowed_regex`), PRESET (preset `extensions.regex_scripts`, only if allowed in `preset_allowed_regex[api]`) (engine.js:11-16, 108-131).
- Gate: `markdownOnly && isMarkdown` OR `promptOnly && isPrompt` OR neither flag and neither mode; `isEdit` requires `runOnEdit`; depth within `minDepth`/`maxDepth`; `placement.includes(p)` (engine.js:334-383).

| Core call site | Placement / options | Cite |
|---|---|---|
| Render | per above, `isMarkdown:true, depth` | script.js:1860 |
| Prompt build (chat history) | USER_INPUT/AI_OUTPUT `{isPrompt:true, depth}`; reasoning REASONING `{isPrompt, depth}` | script.js:4501-4551 |
| AI reply cleanup (persisted) | AI_OUTPUT (or USER_INPUT for impersonate) | script.js:6481 |
| Reasoning persisted | REASONING | script.js:5503; reasoning.js:429, 1595 |
| User send | USER_INPUT | script.js:5875 |
| Greetings | AI_OUTPUT | script.js:7719, 7724 |
| Message edit | placement by sender, `isEdit:true` | script.js:8150-8167 |
| Slash /sys, /sendas, /comment, narrator | SLASH_COMMAND | slash-commands.js:4716, 5717, 5944, 6087 |
| World Info content | WORLD_INFO `{depth, isPrompt:true}` | world-info.js:5205 |

### 7.12 System messages (`scripts/system-messages.js`)
Types: `help, welcome, empty, generic, narrator, comment, slash_commands, formatting, hotkeys, macros, welcome_prompt, assistant_note, assistant_message` (system-messages.js:18-32). Base message `{name: systemUserName, force_avatar: system_avatar ('img/five.png'), is_user:false, is_system:true, extra:{swipeable:false}}`; `getSystemMessageByType` sets `send_date`, `extra.type` (34-43, 122-150; script.js:435). Swipe picker: opens for non-user messages with >1 swipes, jump only on swipeable last message, per-swipe branch (`branchChat` with swipeId) and delete (`deleteSwipe`) (swipe-picker.js:16-44, 120-170).

### 7.13 Message object (`chat[]` element; `public/global.d.ts:66-128`)
| Field | Notes | Cite |
|---|---|---|
| `name, mes, is_user, is_system (hidden/system), send_date` | core | global.d.ts:66-80 |
| `title, gen_started, gen_finished` | set on AI replies | script.js:6757-6760 |
| `force_avatar` (thumbnail URL), `original_avatar` (char avatar key in groups) | | script.js:5894, 6773-6774 |
| `swipes[], swipe_id, swipe_info[] {send_date, gen_started, gen_finished, extra}` | swipe_info.extra = structuredClone(extra) | script.js:6786-6806 |
| `extra.api, extra.model` | `getGeneratingApi/Model` | script.js:6749-6750 |
| `extra.reasoning, reasoning_duration, reasoning_type, reasoning_signature` | ReasoningMessageExtra | reasoning.js:1527-1532; script.js:6751-6753 |
| `extra.token_count` | if `message_token_count_enabled` | script.js:6762-6765 |
| `extra.bias` | Start-Reply-With/bias text | script.js:5897-5900 |
| `extra.gen_id` | group batch id | script.js:6775 |
| `extra.type` | system type (`narrator`, `comment`…) | system-messages.js:146 |
| `extra.display_text, reasoning_display_text, tool_invocations[], isSmallSys, swipeable, overswipe_behavior, uses_system_ui, memory, title` | | global.d.ts:90-104 |
| `extra.media[] {url, type, title?, source?, generation_type?, negative?, width?, height?, append_title?, captioned?}`, `media_display`, `media_index`, `inline_image` | media | global.d.ts:105-152 |
| `extra.files[] {url,size,name,created,text?}`; deprecated `file, image, video, image_swipes, append_title, generationType, negative` | | global.d.ts:106-124; chats.js:62-67 |
| `extra.bookmark_link`, `extra.branches[]`, `extra.fileLength` | checkpoints/branches/file | bookmarks.js:293, 238-245; chats.js:524 |
| `[IGNORE_SYMBOL]` | excluded from prompt | global.d.ts:126 |

### 7.14 Chat file format, load/save, metadata
- File: JSONL at `chats/<avatar without .png>/<file_name>.jsonl` (groups: group chats dir by `chat_id`); line 1 = header `{chat_metadata, user_name:'unused', character_name:'unused'}`, then one message per line; reader parses each line, drops invalid (src/endpoints/chats.js:545-590; global.d.ts:45-56).
- Save: `saveChat({chatName, withMetadata, mesId, force, chatData})` → `POST /api/chats/save {ch_name, file_name, chat:[header,...], avatar_url, force}` gzip-compressed via `compressRequest`; updates `date_last_chat`; throws for groups (script.js:7395-7470). Server `trySaveChat` compares header `chat_metadata.integrity` with the file on disk; mismatch → `400 {error:'integrity'}` → client prompt requiring literal `OVERWRITE` to retry with `force:true`, else reload (chats.js:332-368, 532-570; script.js:7455-7470).
- Debounce/serialization: `saveChatDebounced` 1000 ms (aborts if char/group changed); `saveChatConditional` waits on `isChatSaving`, routes to `saveGroupChat`/`saveChat`, then saves token cache + itemized prompts (script.js:466, 7361-7383, 9412-9439). `saveMetadata()` = `saveChatConditional()` (9408-9410).
- Load: `getChat()` → `POST /api/chats/get {ch_name, file_name, avatar_url}`; shift header into `chat_metadata`; mint `integrity` uuid if missing; `getChatResult` (script.js:7634-7682; chats.js:592-619).
- Other chat endpoints: `/api/chats/delete` (script.js:1339), `/rename`, `/export` (11517), `/import` (9449), `/search` (8583), `/recent` (welcome-screen.js:765).

`chat_metadata` keys used by core:
| Key | Purpose | Cite |
|---|---|---|
| `integrity` | uuid for save conflict detection | script.js:7660-7662; global.d.ts:60 |
| `tainted` | chat has been interacted with (affects greeting regen/loop) | script.js:1687, 5905, 9910 |
| `persona` | chat-locked persona | personas.js:982, 1100 |
| `main_chat` | parent of checkpoint/branch | bookmarks.js:113-127 |
| `note_prompt/interval/depth/position/role` | Author's Note | authors-note.js:30-36 |
| `scenario`, `mes_example`, `system_prompt` | per-chat overrides | group-chats.js:559-560 |
| `world_info` | chat-bound lorebook | world-info.js:94, 1178 |
| `timedWorldInfo` | sticky/cooldown WI state | world-info.js |
| `variables` | chat-local variables | variables.js |
| `script_injects` | `/inject` prompts | slash-commands.js |
| `attachments` | chat Data Bank | chats.js:1761-1763 |
| `custom_background`, `chat_backgrounds` | backgrounds | backgrounds.js:14-15 |
| `cfg_guidance_scale, cfg_negative_prompt, cfg_positive_prompt, cfg_prompt_combine, cfg_groupchat_individual_chars, cfg_prompt_insertion_depth, cfg_prompt_separator` | CFG | cfg-scale.js:389-397 |
| `narrator_name` | `/sys` name | slash-commands.js:3770 |
| `lastInContextMessageId`, `chat_id_hash`, `pick_reroll_seed` | macros/context | script.js:6100; macros.js:316-323; slash-commands.js:3598 |

---

## 8. Settings Persistence, Storage & Secrets

### 8.1 `saveSettings()` → `POST /api/settings/save` (script.js:8051-8115)
Guarded by `settingsReady` (else reschedules) and `TempResponseLength` override (max 3 retries) (8052-8067). Body is gzip-compressed when enabled (8095-8103). Server writes body verbatim to `<userRoot>/settings.json` and triggers snapshot autosave (src/endpoints/settings.js:206-216).

| Payload key | Source | Cite |
|---|---|---|
| `firstRun` | onboarding flag | script.js:420, 8070 |
| `accountStorage` | `accountStorage.getState()` | 8071 |
| `currentVersion` | app version | 8072 |
| `username` | `name1` | 8073 |
| `active_character` / `active_group` | last opened avatar / group id | 641-643, 8074-8075 |
| `user_avatar` | current persona key | 8076 |
| `amount_gen` (default 80), `max_context` (default 2048) | response length / context | 616-617, 8077-8078 |
| `main_api` | `kobold|koboldhorde|textgenerationwebui|novel|openai` | 8079 |
| `world_info_settings` | `getWorldInfoSettings()` | 8080 |
| `textgenerationwebui_settings` | `textgen_settings` | 8081 |
| `swipes` | bool (default true) | 620, 8082 |
| `horde_settings`, `power_user`, `extension_settings` | | 8083-8085 |
| `tags`, `tag_map` | tag system | 8086-8087 |
| `nai_settings`, `kai_settings`, `oai_settings` | per-API settings | 8088-8090 |
| `background` | `background_settings` | 8091 |
| `proxies`, `selected_proxy` | reverse-proxy presets | 8092-8093 |
Not present in 1.19 payload: `api_server`, `preset_settings` (these live inside `kai_settings`, script.js:4047-4048, 5263-5268); `selected_button` is read on load but not saved (script.js:8005).

`saveSettingsDebounced = debounce(saveSettings, DEFAULT_SAVE_EDIT_TIMEOUT=debounce_timeout.relaxed=1000ms)` (script.js:466, 470; constants.js:14). `saveCharacterDebounced` clicks the save button (471).

### 8.2 `getSettings()` load order (script.js:7913-8048)
`POST /api/settings/get` → `{settings (JSON string), koboldai_settings/_names, novelai_settings/_names, openai_settings/_names, textgenerationwebui_presets/_names, world_names, themes, movingUIPresets, quickReplyPresets, instruct, context, sysprompt, reasoning, enable_extensions, enable_extensions_auto_update, enable_accounts, request_compression{enabled,minPayloadSize,maxPayloadSize,timeout}}` (settings.js:219-296).
Order: parse settings → `username`→`name1` → `accountStorage.init(settings.accountStorage)` → `setUserControls(enable_accounts)` → `setRequestCompressionConfig` → emit `SETTINGS_LOADED_BEFORE` → `amount_gen`, `max_context`, `swipes` → `loadKoboldSettings` → `loadNovelSettings` → `loadTextGenSettings` → `loadOpenAISettings` → `loadHordeSettings` → `loadPowerUserSettings` → `applyPowerUserSettings` → `loadTagsSettings` → `loadBackgroundSettings` → `loadProxyPresets` → emit `SETTINGS_LOADED_AFTER` → `main_api` (default `kobold`, `poe`→`openai`) + `changeMainAPI` → `initUserAvatar`, `setPersonaDescription` → `active_character/active_group` → `setWorldInfoSettings` → `initMacros` → extensions (`loadExtensionSettings` or raw assign) → `firstRun` onboarding → `validateDisabledSamplers` → `settingsReady=true` → `SETTINGS_LOADED` (7927-8047). Failed fetch → `reloadLoop()` using `sessionStorage['reloads']` (7903-7906, 7921-7925).

Snapshots: `/api/settings/get-snapshots`, `/load-snapshot`, `/make-snapshot`, `/restore-snapshot` (user.js:473-585; settings.js:298-350).

### 8.3 Presets (`scripts/preset-manager.js`)
- Managers registered per `<select data-preset-manager-for="a,b">`; `getPresetManager(apiId)` maps `koboldhorde`→`kobold`, default = `main_api` (preset-manager.js:83-108).
- apiIds and server folders: `kobold|koboldhorde`→KoboldAI Settings, `novel`→NovelAI Settings, `textgenerationwebui`→TextGen Settings, `openai`→OpenAI Settings, `instruct`, `context`, `sysprompt`, `reasoning` (all `.json`) (src/endpoints/presets.js:16-38).
- Endpoints: `POST /api/presets/save {preset, name, apiId}` → `{name}` (preset-manager.js:474-503; presets.js:42); `/api/presets/delete {name, apiId}` (816-819); `/api/presets/restore {name, apiId}` → `{isDefault, preset}` (831-834; presets.js:83). Chat Completion presets saved by `saveOpenAIPreset` with `apiId:'openai'` (openai.js:4598-4606).
- `getPresetSettings(name)`: copies the live settings object for the API, removes connection/model keys (`api_server, preset, streaming, truncation_length, n, streaming_url, stopping_strings, …, *_model, server_urls, type, seed, legacy_api, include_reasoning, global_banned_tokens, send_banned_tokens, auto_parse, add_to_prompts, auto_expand, show_hidden, max_additions`), adds `genamt=amount_gen`, `max_length=max_context` for non-advanced, non-openai (preset-manager.js:647-750).
- Advanced formatting = `context, instruct, sysprompt, reasoning` (601).
- Preset `extensions` field read/write (e.g. preset regex scripts) (853-900).
- Auto-select: on `CHAT_CHANGED`, a preset named exactly like the character/group is selected (49-76, 992).
- Master import/export sections: `instruct`, `context`, `sysprompt`, `preset` (Text Completion), `reasoning`, `srw` (Start Reply With: `{value: user_prompt_bias, show: show_user_prompt_bias}`) (117-210, 244-370).

### 8.4 `power_user` highlights (power-user.js:116-343)
| Group | Keys (defaults) |
|---|---|
| Tokens/context | `tokenizer: BEST_MATCH`, `token_padding: 64`, `max_context_unlocked: false` |
| Prompt build | `collapse_newlines:false, pin_examples:false, strip_examples:false, trim_sentences:false, always_force_name2:false, user_prompt_bias:'', show_user_prompt_bias:true, prefer_character_prompt:true, prefer_character_jailbreak:true, trim_spaces:true, single_line:false, disable_group_trimming:false, custom_stopping_strings:'', custom_stopping_strings_macro:true` |
| Auto-continue | `auto_continue:{enabled:false, allow_chat_completions:false, target_length:400}` |
| Streaming | `streaming_fps:30, smooth_streaming:false, smooth_streaming_no_think:false, smooth_streaming_speed:50, stream_fade_in:false` |
| Instruct (`instruct.*`) | `enabled:false, preset:'Alpaca', input_sequence:'### Instruction:', output_sequence:'### Response:', *_suffix, system_sequence, last_system_sequence, first/last_input/output_sequence, story_string_prefix/suffix, stop_sequence, wrap:true, macro:true, names_behavior:FORCE, activation_regex, bind_to_context, user_alignment_message, system_same_as_user, sequences_as_stop_strings:true` |
| Context (`context.*`) | `preset:'Default', story_string, chat_start, example_separator, use_stop_strings:true, names_as_stop_strings:true, story_string_position: IN_PROMPT, story_string_role: SYSTEM, story_string_depth:1` |
| Sysprompt | `sysprompt:{enabled:true, name:'Neutral - Chat', content:"Write {{char}}'s next reply…", post_history:''}` |
| Reasoning | `reasoning:{auto_parse:false, add_to_prompts:false, auto_expand:false, show_hidden:false, prefix:'<think>', suffix:'</think>', separator:'\n', max_additions:1}` |
| Derived templates | `instruct_derived, context_derived, context_size_derived, model_templates_mappings:{}, chat_template_hash` |
| Personas | see §7.3.1 |
| Display/formatting | `auto_fix_generated_markdown:true, encode_tags:false, markdown_escape_strings:'', allow_name1_display/allow_name2_display:false, chat_display, avatar_style, timestamps_enabled, message_token_count_enabled, forbid_external_media:true, external_media_*_overrides, media_display, image_overswipe` |
| Behavior | `send_on_enter:AUTO, auto_swipe*, confirm_message_delete:true, auto_save_msg_edits, show_group_chat_queue:false, tag_import_setting:ASK, tag_sort_mode:MANUAL, enable_md_hotkeys:false, experimental_macro_engine:true, auto_connect, auto_load_chat, restore_user_input:true, servers:[]` |

### 8.5 Account storage & localStorage
- `f-localStorage.js`: only deprecated wrappers (`SaveLocal/LoadLocal/LoadLocalBool/CheckLocal/ClearLocal`) over `localStorage` (f-localStorage.js:1-41).
- `AccountStorage` (`scripts/util/AccountStorage.js`): string-valued KV persisted **inside settings.json** (`accountStorage` key); `setItem/removeItem` call `saveSettingsDebounced`; `init(state)` merges server state, and if `__migrated` missing, moves matching browser localStorage keys into it and deletes them, then sets `__migrated='1'` (AccountStorage.js:3-35, 50-75, 145).
- Migratable patterns: `AlertRegex_*, AlertWI_*, Assets_SkipConfirm_*, Characters_PerPage, DataBank_sortField, DataBank_sortOrder, extension_update_nag, extensions_sortByName, FeatherlessModels_PerPage, GroupMembers_PerPage, GroupCandidates_PerPage, LNavLockOn, LNavOpened, mediaWarningShown:*, NavLockOn, NavOpened, Personas_PerPage, Personas_GridView, Proxy_SkipConfirm_*, qr--executeShortcut, qr--syntax, qr--tabSize, qr--wrap, RegenerateWithCtrlEnter, SelectedNavTab, sendAsNamelessWarningShown, StoryStringValidationCache, WINavOpened, WI_PerPage, world_info_sort_order` (AccountStorage.js:3-34).

Additional accountStorage keys written by core: `characterSearchFormVisible` (script.js:11076-11087), `WINavLockOn` (RossAscends-mods.js:749), `pinnedChats`, `recentChatsSettings`, `WelcomePage_RecentChatsHidden`, `assistant` (welcome-screen.js:40-42, 333), `TagFilterState_*`, `<ctx>_tag_<id>` (tags.js:166-189, 1379), `extensionInstallationWarningShown` (extensions.js:1714), `<handle>_openrouter_code_verifier` (secrets.js:506, 534), `slash_command_experimental_engine_warning_shown` (MacroDiagnostics.js:67).

Raw browser storage still used (not account-synced): `localStorage`: `language` (i18n.js:4, 290), `trackDynamicTranslate` (i18n.js:305-319), `mancer_server` (textgen-settings.js:122-124, 604), `eventTracing` (script.js:11030), user input draft key from `getUserInputKey()` (RossAscends-mods.js:458-466). `sessionStorage`: `reloads` (script.js:7903), `neutralChat` (chats.js:1855-1870), tokenizer warning/support keys (tokenizers.js:306-307, 1025; textgen-settings.js:741), extension warning keys (extensions/shared.js:307-327). IndexedDB/localforage stores: see §7.9.

### 8.6 Secrets (`scripts/secrets.js`, server `src/endpoints/secrets.js`)
`SECRET_KEYS` (secrets.js:1-63): `HORDE api_key_horde, MANCER api_key_mancer, VLLM api_key_vllm, APHRODITE api_key_aphrodite, TABBY api_key_tabby, OPENAI api_key_openai, NOVEL api_key_novel, CLAUDE api_key_claude, DEEPL deepl, LIBRE libre, LIBRE_URL libre_url, LINGVA_URL lingva_url, OPENROUTER api_key_openrouter, AI21 api_key_ai21, ONERING_URL oneringtranslator_url, DEEPLX_URL deeplx_url, MAKERSUITE api_key_makersuite, VERTEXAI api_key_vertexai, SERPAPI api_key_serpapi, MISTRALAI api_key_mistralai, TOGETHERAI api_key_togetherai, INFERMATICAI api_key_infermaticai, DREAMGEN api_key_dreamgen, CUSTOM api_key_custom, OOBA api_key_ooba, NOMICAI api_key_nomicai, KOBOLDCPP api_key_koboldcpp, LLAMACPP api_key_llamacpp, COHERE api_key_cohere, PERPLEXITY api_key_perplexity, GROQ api_key_groq, AZURE_TTS api_key_azure_tts, AZURE_OPENAI api_key_azure_openai, FEATHERLESS api_key_featherless, HUGGINGFACE api_key_huggingface, STABILITY api_key_stability, CUSTOM_OPENAI_TTS api_key_custom_openai_tts, CHUTES api_key_chutes, ELECTRONHUB api_key_electronhub, NANOGPT api_key_nanogpt, TAVILY api_key_tavily, BFL api_key_bfl, COMFY_RUNPOD api_key_comfy_runpod, GENERIC api_key_generic, DEEPSEEK api_key_deepseek, SERPER api_key_serper, AIMLAPI api_key_aimlapi, FALAI api_key_falai, XAI api_key_xai, FIREWORKS api_key_fireworks, VERTEXAI_SERVICE_ACCOUNT vertexai_service_account_json, MINIMAX api_key_minimax, MINIMAX_GROUP_ID minimax_group_id, MOONSHOT api_key_moonshot, COMETAPI api_key_cometapi, ZAI api_key_zai, SILICONFLOW api_key_siliconflow, ELEVENLABS api_key_elevenlabs, POLLINATIONS api_key_pollinations, VOLCENGINE_APP_ID volcengine_app_id, VOLCENGINE_ACCESS_KEY volcengine_access_key, WORKERS_AI api_key_workers_ai`.

Storage model: `secrets.json` in user root = `{ [key]: [{id (uuid), value, label, active}] }` + `_migrated` marker; old flat `{key: value}` files auto-migrated with backup (src/endpoints/secrets.js:8-10, 74-98, 381-417). Write deactivates others and appends new active entry; delete of active re-activates first remaining (secrets.js server 205-255).

| Endpoint | Body → Response | Frontend fn | Cite (client / server) |
|---|---|---|---|
| `/api/secrets/write` | `{key, value, label}` (label default `moment().format('L LT')`) → `{id}`; empty value → delete | `writeSecret` | 352-377 / 511 |
| `/api/secrets/read` | → `{ [key]: [{id, value: masked, label, active}] | null }` | `readSecretState` → `secret_state` | 408-422 / 529, 340-372 |
| `/api/secrets/view` | → all plaintext; 403 unless `allowKeysExposure` | `viewSecrets` | 308-327 / 540 |
| `/api/secrets/find` | `{key, id?}` → `{value}`; 403 unless exposure or key in EXPORTABLE (`libre_url, lingva_url, oneringtranslator_url, deeplx_url`) | `findSecret` | 431-446 / 560, 100-106 |
| `/api/secrets/delete` | `{key, id?}` → 204; client re-triggers `#main_api` change | `deleteSecret` | 385-401 / 588 |
| `/api/secrets/rotate` | `{key, id}` → set active | `rotateSecret` | 456-472 / 606 |
| `/api/secrets/rename` | `{key, id, label}` | `renameSecret` | 481-495 / 624 |
| `/api/secrets/settings` | → `{allowKeysExposure}` | `canViewSecrets` | 289-305 / 642 |

- Masking: unless `allowKeysExposure`/exportable: values ≤10 chars → `**********`; else 7 `*` + last 3 chars (server secrets.js:180-195). Frontend only holds masked `secret_state` (used for placeholders/labels, `updateSecretDisplay`, secrets.js:265-287) and never plaintext except via `/view` or `/find` with exposure enabled; generation requests carry no key — server reads the active secret.
- `resolveSecretKey()` maps current API/source to a key (Vertex AI: `express`→`api_key_vertexai`, `full`→service account JSON) (secrets.js:193-232). OpenRouter OAuth PKCE (S256) with verifier in accountStorage (506-545). Slash: `/secret-id, /secret-delete, /secret-write, /secret-rename, /secret-read` (768-1068). Events `SECRET_WRITTEN/DELETED/ROTATED/EDITED`.

### 8.7 CSRF & user accounts
- `firstLoadInit` fetches `GET /csrf-token` → `{token}` (fatal toast on failure) (script.js:694-702); every request uses `getRequestHeaders()` = `{'Content-Type':'application/json','X-CSRF-Token': token}` (omitContentType for multipart) and jQuery ajax prefilter adds the header (script.js:647-669). Server: `csrf-sync` stores token in session, reads `x-csrf-token`; disabled by `--disableCsrf` (src/server-main.js:167-200). Login page also fetches it (login.js:14).
- Request compression: gzip (fflate) with `Content-Encoding: gzip` when server enables it and size within limits (request-compression.js:23-119).
- `user.js`: `setUserControls(enable_accounts)`; `getCurrentUserHandle()` (default `'default-user'`); `/api/users/me, get, enable, disable, promote, demote, create, backup, change-password, delete, reset-settings, change-name, change-avatar, logout, slugify, reset-step1/2`; session kept alive by `POST /api/ping?extend=1` every 10 min; `isAdmin()` true when accounts disabled (user.js:10-60, 62-904, 904-934). Login page: `/api/users/list, login, recover-step1/2` (login.js:24-115). Per-user data dirs (`request.user.directories`) back every endpoint above.

---

## 9. Parity risk register: logic that is hardest to replicate

| # | Area | Why it is hard / what must be byte-exact | Primary cites |
|---|---|---|---|
| 1 | TC prompt assembly in `Generate()` | Order of operations is implicit in roughly 1300 lines. Specific traps: injected messages are budgeted before history; history is budgeted before examples; the `continue` path uses a sentinel token (`\u0000￼\u0000�`) to strip suffixes; the trailing-newline rules depend on instruct `wrap` and on quiet mode; the `force_name2` / impersonate last-line rules; the user-alignment message. | `script.js:4290-5604` (§1.3, §3.2) |
| 2 | Extension-prompt injection | `extension_prompts` keys are processed in **alphabetical** order (`sort()`). Depth injection runs over 0..10000 with role priority system→user→assistant and a cumulative offset, and continue shifts depth 0 to 1. This is how AN, WI@depth, persona@depth, char depth prompt, vectors and summary all land in the prompt. | `script.js:3301`, `5628-5676`, `8926` |
| 3 | Chat Completion PromptManager | Covers: the prompt order (global `dummyId 100001`), markers vs. content prompts, absolute in-chat injections merged by depth, role and order, `ChatCompletion` budget reservations, newest-first history fill, the example-dialogue strategy, squash/post-processing modes, and the per-source body quirks for 26 sources. | §2.A, §2.N |
| 4 | World Info scan | Covers: the multi-loop state machine (INITIAL → RECURSION / MIN_ACTIVATIONS, plus recursion-delay levels); timed effects persisted in `chat_metadata.timedWorldInfo` and keyed by entry **hash**; inclusion groups with scoring and weighted randomness; the budget check against `allActivatedText`; and the quirks listed in §4.13. | `world-info.js:4709-5281` |
| 5 | Macro engine | The new Chevrotain CST engine (default on) supports nested and scoped macros, `if`/`else`, `{{.var}}`/`{{$var}}` shorthand operators, and is single-pass. The legacy engine is a sequential regex chain that re-scans its own output. `{{pick}}` determinism depends on cyrb53 `getStringHash` + `seedrandom` with a **string** seed and the global offset of the macro in the source text. | §5.3-5.7 |
| 6 | STscript | The parser, closures, scopes, pipes, `{{pipe}}`, parser flags, abort/break/breakpoints and 293 commands, each with its own argument-coercion semantics. Quick Replies and many extensions depend on it. | §6 |
| 7 | Streaming + reasoning | Covers: SSE framing and per-provider delta shapes, including reasoning fields; the `ReasoningHandler` state machine that detects `<think>`-style prefixes in streamed text; markdown balancing of partial text; multi-swipe (`n>1`) routing by choice index; and tool-call deltas followed by recursive `Generate('normal', depth+1)`. | §1.6, §2.I, §2.A.7, §2.L |
| 8 | Output cleanup | `cleanUpMessage` mixes stop-string *partial-suffix* trimming, regex scripts, name trimming that depends on `allow_name1/2_display`, instruct sequence leakage, group trimming and sentence trimming, and its order matters. | `script.js:6442-6592` |
| 9 | Swipe/data model | The invariants `mes == swipes[swipe_id]` and `swipe_info[i].extra` mirrors are maintained in many places (`saveReply`, the streaming processor, edits, `syncMesToSwipe`). Overswipe behaviour depends on `chat_metadata.tainted`. | §1.8-1.9 |
| 10 | Tokenization | Token counts drive every budget. To match ST exactly, Android needs local tokenizers (Llama/Llama3/Mistral/Nemo/Qwen2/Gemma/Claude/tiktoken/NerdStash…), the best-match heuristics and the 3.35 bytes/token fallback. The server currently does this work. | §3.3 |
| 11 | Server-side duties hidden from the frontend | The Node server does the following, and a standalone Android app must reimplement it: chat JSONL integrity check, character PNG/`chara` (V2/V3) parsing, CC prompt converters (Claude/Google/Cohere/Mistral…), tokenizers, secrets storage, and provider request translation. | `src/prompt-converters.js`, `src/endpoints/*` (§2.N, §7, §8) |
