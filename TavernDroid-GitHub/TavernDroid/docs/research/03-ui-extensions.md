# SillyTavern 1.19 — UI, Extensions & Frontend Modules (Android parity reference)

Source: the SillyTavern 1.19.0 source root (commit 06bde93) (read-only). Citations are `path:line` from the repo root. A bare `:N` means a line in the file named in that section or row. Tables are used wherever possible.

**Contents:** 1 UI structure (1.1 drawers/controls · 1.2 power_user · 1.3 RossAscends-mods · 1.4 popup/keyboard/a11y/drag-drop/bulk-edit/welcome/search/backgrounds/i18n/templates · 1.5 moving UI/themes/CSS · 1.6 mobile behaviors) · 2 Built-in extensions (2.1–2.16) · 3 Extension system & `getContext()` · 4 Other frontend modules · 5 Third-party libraries

## 1. UI structure

### 1.1 Top bar & drawers (public/index.html)

**Legend.** *element id* = DOM id; `.class` / `[name=x]` / `[data-action=x]` when the element has no id (templates). *type* = input type, tag, `button` (clickable div/i: menu_button / icon+title / interactable), `range+number` = slider `X` paired with numeric counter `X_counter`-style input (`data-for`), `div` = container, `section` = h3/h4/inline-drawer header (navigation aid). *purpose*: label text (from `<label>`/data-i18n/nearby text); `tip:` = title/tooltip; `placeholder:`; `[min= max= step=]` = input bounds; `opts:` = static `<option>` value=label (model lists truncated, many are filled at runtime); `{tg=…}` shown only for those Text Completion backends (`data-tg-type`; `tg≠` = data-tg-type-mode="except"); `{src=…}` shown only for those Chat Completion sources (`data-source`); `preset mgr:` = `data-preset-manager-for`; `secret key:` = `data-key` of the Manage-API-keys button; `server-history:` = URL history bucket; `sampler:` = `data-tg-samplers`. Textareas carrying a sibling `.editor_maximize` button (expand to full-screen editor) are not listed separately. Drawer mechanics: each top-bar item is `.drawer` > `.drawer-toggle` > `.drawer-icon` + `.drawer-content` (classes openDrawer/closedDrawer); left/right nav and WI have pin checkboxes that keep them open. All line numbers refer to `public/index.html` (8231 lines) unless another file is named.

#### Top-bar drawer order (left → right)

| # | drawer wrapper id | toggle icon (Font Awesome) | content panel id | title | index.html:line |
|---|---|---|---|---|---|
| 1 | ai-config-button | #leftNavDrawerIcon fa-sliders | left-nav-panel (fillLeft, pinnable #lm_button_panel_pin) | AI Response Configuration | 68 |
| 2 | sys-settings-button | #API-status-top fa-plug-circle-exclamation (icon reflects connection; attr no_connection_text) | rm_api_block | API Connections | 2282 |
| 3 | advanced-formatting-button | .drawer-icon fa-font | AdvancedFormatting | AI Response Formatting | 4091 |
| 4 | WI-SP-button | #WIDrawerIcon fa-book-atlas | WorldInfo (pinnable #WI_panel_pin) | World Info | 4671 |
| 5 | user-settings-button | .drawer-icon fa-user-cog | user-settings-block | User Settings | 4878 |
| 6 | backgrounds-button | #backgrounds-drawer-toggle fa-panorama | Backgrounds | Change Background Image | 5653 |
| 7 | extensions-settings-button | .drawer-icon fa-cubes | rm_extensions_block | Extensions | 5750 |
| 8 | persona-management-button | .drawer-icon fa-face-smile | PersonaManagement | Persona Management | 5833 |
| 9 | rightNavHolder | #rightNavDrawerIcon fa-address-card (toggle #unimportantYes) | right-nav-panel (fillRight, pinnable #rm_button_panel_pin) | Character Management | 6001 |

#### Top bar shell (#top-bar, #top-settings-holder)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| top-bar | div | container | 64 |
| top-settings-holder | div | container | 66 |
| ai-config-button | div | container .drawer | 68 |
| leftNavDrawerIcon | button | tip: AI Response Configuration | 70 |

#### AI Response Configuration — header (#left-nav-panel, toggle #leftNavDrawerIcon in #ai-config-button)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| left-nav-panel | div | container .drawer-content | 72 |
| left-nav-panelheader | div | container .drag-grabber | 73 |
| lm_button_panel_pin_div | div | tip: Locked = AI Configuration panel will stay open | 74 |
| lm_button_panel_pin | checkbox | Lock left panel open | 75 |
| .unchecked | button | pin icon (panel unlocked state) | 77 |
| .checked | button | pin icon (panel locked state) | 78 |
| clickSlidersTips | div | Click slider numbers to input manually. | 81 |
| labModeWarning | div | MAD LAB MODE ON | 84 |
| .topRightInset | a | tip: Documentation on sampling parameters. | 85 |

##### Preset selectors per API (#respective-presets-block) — one block shown per main_api

| element id | type | purpose | index.html:line |
|---|---|---|---|
| ai_response_configuration | div | container | 89 |
| respective-presets-block | div | container | 90 |
| kobold_api-presets | div | Kobold Presets GUI KoboldAI Settings | 91 |
| settings_preset | select | opts: gui=GUI KoboldAI Settings — preset mgr: kobold | 110 |
| novel_api-presets | div | NovelAI Presets Default | 130 |
| settings_preset_novel | select | opts: gui=Default — preset mgr: novel | 152 |
| openai_api-presets | div | Chat Completion Presets Default | 172 |
| bind_preset_to_connection | checkbox | Bind presets to API connections | 181 |
| import_oai_preset | button | tip: Import preset | 185 |
| export_oai_preset | button | tip: Export preset | 188 |
| delete_oai_preset | button | tip: Delete the preset | 191 |
| settings_preset_openai | select | opts: gui=Default — preset mgr: openai | 198 |
| openai_preset_import_file | file | Hidden file input for CC preset import (.json) | 202 |
| update_oai_preset | button | tip: Update current preset | 203 |
| new_oai_preset | button | tip: Save preset as | 209 |
| textgenerationwebui_api-presets | div | Text Completion presets | 216 |
| settings_preset_textgenerationwebui | select | preset mgr: textgenerationwebui | 235 |

##### Common generation settings (#common-gen-settings-block): response length, context size, streaming

| element id | type | purpose | index.html:line |
|---|---|---|---|
| common-gen-settings-block | div | container | 255 |
| pro-settings-block | div | container | 256 |
| amount_gen_block | div | Response length block | 257 |
| amount_gen / amount_gen_counter | range+number | Response (tokens) — [min=16 max=2048 step=1] | 259 |
| streaming_textgenerationwebui_block | div | Streaming | 263 |
| streaming_textgenerationwebui | checkbox | Streaming — tip: Display the response bit by bit as it is generated. When this is off, responses will be displayed all at once when they… | 265 |
| streaming_kobold_block | div | Streaming | 271 |
| streaming_kobold | checkbox | Streaming — tip: Display the response bit by bit as it is generated. When this is off, responses will be displayed all at once when they… | 273 |
| streaming_novel_block | div | Streaming | 279 |
| streaming_novel | checkbox | Streaming — tip: Display the response bit by bit as it is generated. When this is off, responses will be displayed all at once when they… | 281 |
| max_context_block | div | Context size block | 288 |
| max_context / max_context_counter | range+number | Context (tokens) — [min=512 max=8192 step=64] | 290 |
| max_context_unlocked_block | div | Unlocked | 294 |
| max_context_unlocked | checkbox | Unlocked — tip: Only enable this if your model supports context sizes greater than 8192 tokens. Increase only if you know what you're d… | 296 |
| max_context_unlocked_warning | button | tip: Only enable this if your model supports context sizes greater than 8192 tokens. Increase only if you know what you're d… | 298 |
| prompt_cost_block | small | Max prompt cost: – — {tg=openrouter} | 303 |
| or_prompt_cost | span | – — {tg=openrouter} | 304 |

##### NovelAI module (#ai_module_block_novel)  [note: Kobold legacy #range_block at :327 is commented out]

| element id | type | purpose | index.html:line |
|---|---|---|---|
| ai_module_block_novel | div | container | 307 |
| nai_prefix | select | AI Module Changes the style of the generated text. — opts: vanilla=No Module, special_instruct=Instruct, special_proseaugmenter=Prose Augmenter, theme_textadventure=Text Adventure | 315 |
| respective-ranges-and-temps | div | container | 325 |

##### NovelAI samplers (#range_block_novel)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| range_block_novel | div | container | 330 |
| temp_novel / temp_counter_novel | range+number | Temperature — [min=0.1 max=2.50 step=0.01] | 337 |
| rep_pen_novel / rep_pen_counter_novel | range+number | Repetition Penalty — [min=1 max=8 step=0.025] | 350 |
| rep_pen_size_novel / rep_pen_size_counter_novel | range+number | Rep Pen Range — [min=0 max=8192 step=1] | 363 |
| rep_pen_slope_novel / rep_pen_slope_counter_novel | range+number | Repetition Penalty Slope — [min=0 max=10 step=0.01] | 376 |
| rep_pen_freq_novel / rep_pen_freq_counter_novel | range+number | Repetition Penalty Frequency — [min=0 max=1 step=0.001] | 389 |
| rep_pen_presence_novel / rep_pen_presence_counter_novel | range+number | Repetition Penalty Presence — [min=0 max=1 step=0.001] | 402 |
| min_p_novel / min_p_counter_novel | range+number | Min P — [min=0 max=1 step=0.001] | 415 |
| tail_free_sampling_novel / tail_free_sampling_counter_novel | range+number | TFS — [min=0 max=1 step=0.001] | 428 |
| top_p_novel / top_p_counter_novel | range+number | Top P — [min=0 max=1 step=0.001] | 441 |
| top_a_novel / top_a_counter_novel | range+number | Top A — [min=0 max=1 step=0.001] | 454 |
| top_k_novel / top_k_counter_novel | range+number | Top K — [min=0 max=300 step=1] | 467 |
| mirostat_tau_novel / mirostat_tau_counter_novel | range+number | Mirostat Tau — [min=0 max=6 step=0.01] | 480 |
| mirostat_lr_novel / mirostat_lr_counter_novel | range+number | Mirostat LR — [min=0 max=1 step=0.01] | 493 |
| typical_p_novel / typical_p_counter_novel | range+number | Typical P — [min=0 max=1 step=0.001] | 506 |
| math1_temp_novel / math1_temp_counter_novel | range+number | Linear — [min=0 max=1.5 step=0.01] | 519 |
| math1_quad_novel / math1_quad_counter_novel | range+number | Quad — [min=0 max=0.4 step=0.01] | 532 |
| math1_quad_entropy_scale_novel / math1_quad_entropy_scale_counter_novel | range+number | Conf — [min=-0.4 max=0.4 step=0.01] | 545 |
| min_length_novel / min_length_counter_novel | range+number | Min Length — [min=1 max=150 step=1] | 558 |
| phrase_rep_pen_novel | select | Phrase Repetition Penalty — opts: off=Off, very_light=Very light, light=Light, medium=Medium, aggressive=Aggressive, very_aggressive=Very aggressive | 569 |
| nai_preamble_restore | button | tip: Restore default prompt | 581 |
| nai_preamble_textarea | textarea | NovelAI preamble (style tags) | 589 |
| nai_banned_tokens | textarea | Banned Tokens Sequences you don't want to appear in the output. One per line. Text or [token ids]. — placeholder: "Example: some text [42, 69, 1337]" | 600 |
| logit_bias_novel | div | Logit Bias Add | 604 |
| novelai_logit_bias_new_entry | button | Add | 606 |

##### Chat Completion samplers + quick/utility prompts (#range_block_openai) — visibility via data-source

| element id | type | purpose | index.html:line |
|---|---|---|---|
| range_block_openai | div | container | 619 |
| oai_max_context_unlocked | checkbox | Unlocked Context Size | 622 |
| openai_max_context / openai_max_context_counter | range+number | Context Size (tokens) — [min=512 max=4095 step=1] | 640 |
| openai_max_tokens | number | Max Response Length (tokens) — [min=1 max=128000 step=1] | 652 |
| n_openai | number | Multiple swipes per generation — [min=1] — {src=openai,custom,xai,aimlapi,moonshot,azure_openai} | 660 |
| openrouter_middleout | select | Middle-out Transform — opts: auto=Auto, on=Allow, off=Forbid — {src=openrouter} | 669 |
| openrouter_max_prompt_cost | span | Unknown — {src=openrouter} | 677 |
| electronhub_max_prompt_cost | span | Unknown — {src=electronhub} | 680 |
| chutes_max_prompt_cost | span | Unknown — {src=chutes} | 683 |
| stream_toggle | checkbox | Streaming | 688 |
| temp_openai / temp_counter_openai | range+number | Temperature — [min=0 max=2.0 step=0.01] — {src=openai,claude,aimlapi,openrouter,ai21,makersuite,vertexai,mistralai,custom,cohere,perplexity,groq,siliconflow,minimax,electronhub,chutes,nanogpt,deepseek,xai,pollinations,moonshot,fireworks,cometapi,azure_openai,zai,workers_ai} | 706 |
| freq_pen_openai / freq_pen_counter_openai | range+number | Frequency Penalty — [min=-2 max=2 step=0.01] — {src=openai,aimlapi,openrouter,custom,cohere,perplexity,groq,siliconflow,mistralai,electronhub,nanogpt,deepseek,xai,pollinations,moonshot,fireworks,cometapi,azure_openai,chutes,workers_ai} | 719 |
| pres_pen_openai / pres_pen_counter_openai | range+number | Presence Penalty — [min=-2 max=2 step=0.01] — {src=openai,aimlapi,openrouter,custom,cohere,perplexity,groq,siliconflow,mistralai,electronhub,nanogpt,deepseek,xai,pollinations,moonshot,fireworks,cometapi,azure_openai,chutes,workers_ai} | 732 |
| top_k_openai / top_k_counter_openai | range+number | Top K — [min=0 max=500 step=1] — {src=claude,aimlapi,openrouter,makersuite,vertexai,cohere,perplexity,electronhub,chutes,nanogpt,workers_ai} | 745 |
| top_p_openai / top_p_counter_openai | range+number | Top P — [min=0 max=1 step=0.01] — {src=openai,claude,aimlapi,openrouter,ai21,makersuite,vertexai,mistralai,custom,cohere,perplexity,groq,siliconflow,minimax,electronhub,chutes,nanogpt,deepseek,xai,pollinations,moonshot,fireworks,cometapi,azure_openai,zai,workers_ai} | 758 |
| repetition_penalty_openai / repetition_penalty_counter_openai | range+number | Repetition Penalty — [min=1 max=2 step=0.01] — {src=openrouter,nanogpt,chutes,workers_ai} | 771 |
| min_p_openai / min_p_counter_openai | range+number | Min P — [min=0 max=1 step=0.001] — {src=openrouter,nanogpt,chutes} | 784 |
| top_a_openai / top_a_counter_openai | range+number | Top A — [min=0 max=1 step=0.001] — {src=openrouter,nanogpt} | 797 |
| **— Quick Prompts Edit —** | section |  | 805 |
| main_prompt_quick_edit_textarea | textarea | Main — placeholder: "—" | 814 |
| nsfw_prompt_quick_edit_textarea | textarea | Auxiliary — placeholder: "—" | 821 |
| jailbreak_prompt_quick_edit_textarea | textarea | Post-History Instructions — placeholder: "—" | 828 |
| **— Utility Prompts —** | section |  | 834 |
| impersonation_prompt_restore | button | tip: Restore default prompt | 842 |
| impersonation_prompt_textarea | textarea | Impersonation prompt Prompt that is used for Impersonation function — placeholder: "—" | 850 |
| wi_format_restore | button | tip: Restore default format | 856 |
| wi_format_textarea | textarea | World Info format template Wraps activated World Info entries before inserting into the prompt. Use… — placeholder: "—" | 866 |
| scenario_format_restore | button | tip: Restore default format | 872 |
| scenario_format_textarea | textarea | Scenario format template Use {{scenario}} to mark a place where the content is inserted. — placeholder: "—" | 880 |
| personality_format_restore | button | tip: Restore default format | 886 |
| personality_format_textarea | textarea | Personality format template Use {{personality}} to mark a place where the content is inserted. — placeholder: "—" | 894 |
| group_nudge_prompt_restore | button | tip: Restore default format | 900 |
| group_nudge_prompt_textarea | textarea | Group Nudge prompt template Sent at the end of the group chat history to force reply from a specifi… — placeholder: "—" | 910 |
| newchat_prompt_restore | button | tip: Restore new chat prompt | 916 |
| newchat_prompt_textarea | textarea | New Chat Set at the beginning of the chat history to indicate that a new chat is about to start. — placeholder: "—" | 926 |
| newgroupchat_prompt_restore | button | tip: Restore default prompt | 932 |
| newgroupchat_prompt_textarea | textarea | New Group Chat Set at the beginning of the chat history to indicate that a new group chat is about… — placeholder: "—" | 942 |
| newexamplechat_prompt_restore | button | tip: Restore new example chat prompt | 948 |
| newexamplechat_prompt_textarea | textarea | New Example Chat Set at the beginning of Dialogue examples to indicate that a new example chat is a… — placeholder: "—" | 958 |
| continue_nudge_prompt_restore | button | tip: Restore new chat prompt | 964 |
| continue_nudge_prompt_textarea | textarea | Continue nudge Set at the end of the chat history when the continue button is pressed. — placeholder: "—" | 974 |
| send_if_empty_textarea | textarea | Replace empty message Send this text instead of nothing when the text box is empty. — placeholder: "—" | 987 |
| seed_openai | number | Seed Set to get deterministic results. Use -1 for random seed. — [min=-1 max=2147483647] — {src=openai,openrouter,mistralai,custom,cohere,groq,electronhub,chutes,nanogpt,xai,pollinations,aimlapi,makersuite,vertexai,azure_openai,workers_ai} | 1000 |

##### KoboldAI Classic / AI Horde samplers (#kobold_api-settings)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| advanced-ai-config-block | div | container | 1005 |
| kobold_api-settings | div | container | 1006 |
| temp / temp_counter | range+number | Temperature — [min=0.0 max=4.0 step=0.01] | 1013 |
| top_k / top_k_counter | range+number | Top K — [min=0 max=100 step=1] | 1021 |
| top_p / top_p_counter | range+number | Top P — [min=0 max=1 step=0.01] | 1029 |
| typical_p / typical_p_counter | range+number | Typical P — [min=0 max=1 step=0.001] | 1037 |
| min_p / min_p_counter | range+number | Min P — [min=0 max=1 step=0.001] | 1045 |
| top_a / top_a_counter | range+number | Top A — [min=0 max=1 step=0.001] | 1053 |
| tfs / tfs_counter | range+number | TFS — [min=0 max=1 step=0.001] | 1061 |
| rep_pen / rep_pen_counter | range+number | Repetition Penalty — [min=1 max=3 step=0.01] | 1068 |
| rep_pen_range / rep_pen_range_counter | range+number | Rep Pen Range — [min=0 max=4096 step=1] | 1075 |
| rep_pen_slope / rep_pen_slope_counter | range+number | Repetition Penalty Slope — [min=0 max=10 step=0.01] | 1082 |
| **— Mirostat —** | section |  | 1086 |
| mirostat_mode_kobold / mirostat_mode_counter_kobold | range+number | Mode — [min=0 max=2 step=1] | 1093 |
| mirostat_tau_kobold / mirostat_tau_counter_kobold | range+number | Tau — [min=0 max=20 step=0.01] | 1101 |
| mirostat_eta_kobold / mirostat_eta_counter_kobold | range+number | Eta — [min=0 max=1 step=0.01] | 1109 |
| use_default_badwordsids | checkbox | Ban EOS Token — tip: Ban the End-of-Sequence (EOS) token with KoboldCpp (and possibly also other tokens with KoboldAI). Good for story writi… | 1117 |
| seed_kobold | number | Seed — [min=-1 max=18446744073709551615] | 1128 |
| grammar_block | div | GBNF Grammar | 1130 |
| **— GBNF Grammar —** | section |  | 1132 |
| grammar | textarea | GBNF Grammar — placeholder: "Type in the desired custom grammar" | 1139 |
| kobold_order | div | sortable order: 0:Top K, 1:Top A, 2:Top P & Min P, 3:Tail Free Sampling, 4:Typical P, 5:Temperature, 6:Repetition Penalty | 1150 |
| samplers_order_recommended | button | Load koboldcpp order | 1180 |

##### NovelAI sampler order (#novel_api-settings)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| novel_api-settings | div | container | 1186 |
| novel_order | div | sortable order: 0:Temperature, 1:Top K Sampling, 2:Nucleus Sampling, 3:Tail Free Sampling, 4:Top A Sampling, 5:Typical P, 8:Mirostat, 9:Unified Sampling, 10:Min P | 1194 |

##### Text Completion samplers (#textgenerationwebui_api-settings) — visibility via data-tg-type

| element id | type | purpose | index.html:line |
|---|---|---|---|
| textgenerationwebui_api-settings | div | container | 1244 |
| samplerResetButton | button | Neutralize Samplers | 1247 |
| samplerSelectButton | button | Sampler Select | 1252 |
| n_textgenerationwebui | number | Multiple swipes per generation — [min=1 step=1] — {tg=mancer,vllm,aphrodite,tabby,infermaticai,llamacpp} | 1258 |
| temp_textgenerationwebui / temp_counter_textgenerationwebui | range+number | Temperature — [min=0.0 max=5.0 step=0.01] | 1266 |
| top_k_textgenerationwebui / top_k_counter_textgenerationwebui | range+number | Top K — [min=-1 max=500 step=1] — {tg≠generic} | 1275 |
| top_p_textgenerationwebui / top_p_counter_textgenerationwebui | range+number | Top P — [min=0 max=1 step=0.01] | 1283 |
| typical_p_textgenerationwebui / typical_p_counter_textgenerationwebui | range+number | Typical P — [min=0 max=1 step=0.01] — {tg≠generic} | 1291 |
| min_p_textgenerationwebui / min_p_counter_textgenerationwebui | range+number | Min P — [min=0 max=1 step=0.001] — {tg≠generic} | 1299 |
| top_a_textgenerationwebui / top_a_counter_textgenerationwebui | range+number | Top A — [min=0 max=1 step=0.01] — {tg≠generic,llamacpp} | 1307 |
| tfs_textgenerationwebui / tfs_counter_textgenerationwebui | range+number | TFS — [min=0 max=1 step=0.001] — {tg≠generic,llamacpp} | 1315 |
| epsilon_cutoff_textgenerationwebui / epsilon_cutoff_counter_textgenerationwebui | range+number | Epsilon Cutoff — [min=0 max=9 step=0.01] — {tg=ooba,mancer,aphrodite} | 1323 |
| nsigma_textgenerationwebui / nsigma_counter_textgenerationwebui | range+number | Top nsigma — [min=0 max=5 step=0.01] — {tg=ooba,aphrodite,koboldcpp,llamacpp} | 1331 |
| min_keep_textgenerationwebui / min_keep_counter_textgenerationwebui | range+number | Min Keep — [min=0 max=50 step=1] — {tg=llamacpp} | 1339 |
| eta_cutoff_textgenerationwebui / eta_cutoff_counter_textgenerationwebui | range+number | Eta Cutoff — [min=0 max=20 step=0.01] — {tg=ooba,mancer,aphrodite} | 1347 |
| rep_pen_textgenerationwebui / rep_pen_counter_textgenerationwebui | range+number | Repetition Penalty — [min=1 max=3 step=0.01] — {tg≠generic} | 1352 |
| rep_pen_range_textgenerationwebui / rep_pen_range_counter_textgenerationwebui | range+number | Rep Pen Range — [min=-1 max=8192 step=1] — {tg=ooba,koboldcpp,tabby,ollama,llamacpp} | 1357 |
| rep_pen_slope_textgenerationwebui / rep_pen_slope_counter_textgenerationwebui | range+number | Rep Pen Slope — [min=0 max=10 step=0.01] — {tg=koboldcpp} | 1362 |
| rep_pen_decay_textgenerationwebui / rep_pen_decay_counter_textgenerationwebui | range+number | Rep Pen Decay — [min=-1 max=8192 step=1] — {tg=tabby} | 1367 |
| encoder_rep_pen_textgenerationwebui / encoder_rep_pen_counter_textgenerationwebui | range+number | Encoder Penalty — [min=0.8 max=1.5 step=0.01] — {tg=ooba} | 1372 |
| freq_pen_textgenerationwebui / freq_pen_counter_textgenerationwebui | range+number | Frequency Penalty — [min=-2 max=2 step=0.01] | 1377 |
| presence_pen_textgenerationwebui / presence_pen_counter_textgenerationwebui | range+number | Presence Penalty — [min=-2 max=2 step=0.01] | 1382 |
| no_repeat_ngram_size_textgenerationwebui / no_repeat_ngram_size_counter_textgenerationwebui | range+number | No Repeat Ngram Size — [min=0 max=20 step=1] — {tg=aphrodite,ooba} | 1387 |
| skew_textgenerationwebui / skew_counter_textgenerationwebui | range+number | Skew — [min=-5 max=5 step=0.01] — {tg=tabby,aphrodite} | 1392 |
| min_length_textgenerationwebui / min_length_counter_textgenerationwebui | range+number | Min Length — [min=0 max=2000 step=1] — {tg=mancer,ooba,tabby,dreamgen,infermaticai,aphrodite} | 1397 |
| max_tokens_second_textgenerationwebui / max_tokens_second_counter_textgenerationwebui | range+number | Maximum tokens/second — [min=0 max=20 step=1] — {tg=ooba} | 1402 |
| adaptive_p_block | div | Adaptive-P Target Decay — {tg=koboldcpp,llamacpp,ooba,tabby} | 1406 |
| **— Adaptive-P —** | section |  | 1407 |
| adaptive_target_textgenerationwebui / adaptive_target_counter_textgenerationwebui | range+number | Target — [min=-0.01 max=1 step=0.01] — {tg=koboldcpp,llamacpp,ooba,tabby} | 1416 |
| adaptive_decay_textgenerationwebui / adaptive_decay_counter_textgenerationwebui | range+number | Decay — [min=0 max=0.99 step=0.01] — {tg=koboldcpp,llamacpp,ooba,tabby} | 1421 |
| smoothingBlock | div | Smooth Sampling Smoothing Factor Smoothing Curve — sampler: smoothing_factor — {tg=mancer,ooba,koboldcpp,aphrodite,tabby} | 1427 |
| **— Smooth Sampling —** | section |  | 1428 |
| smoothing_factor_textgenerationwebui / smoothing_factor_counter_textgenerationwebui | range+number | Smoothing Factor — [min=0 max=10 step=0.01] — {tg=mancer,ooba,koboldcpp,aphrodite,tabby} | 1435 |
| smoothing_curve_textgenerationwebui / smoothing_curve_counter_textgenerationwebui | range+number | Smoothing Curve — [min=1 max=10 step=0.01] — {tg=mancer,ooba,koboldcpp,aphrodite} | 1440 |
| xtc_block | div | Exclude Top Choices (XTC) Threshold Probability — sampler: xtc_probability — {tg=koboldcpp,aphrodite,mancer,tabby,ooba,llamacpp} | 1446 |
| **— Exclude Top Choices (XTC) —** | section |  | 1447 |
| xtc_threshold_textgenerationwebui / xtc_threshold_counter_textgenerationwebui | range+number | Threshold — [min=0 max=0.5 step=0.01] — {tg=koboldcpp,aphrodite,mancer,tabby,ooba,llamacpp} | 1456 |
| xtc_probability_textgenerationwebui / xtc_probability_counter_textgenerationwebui | range+number | Probability — [min=0 max=1 step=0.01] — {tg=koboldcpp,aphrodite,mancer,tabby,ooba,llamacpp} | 1461 |
| dryBlock | div | sampler: dry_multiplier — {tg=aphrodite,mancer,ooba,koboldcpp,tabby,llamacpp,dreamgen} | 1467 |
| **— DRY Repetition Penalty —** | section | tip: DRY penalizes tokens that would extend the end of the input into a sequence that has previously occ… | 1468 |
| dry_multiplier_textgenerationwebui / dry_multiplier_counter_textgenerationwebui | range+number | Multiplier — [min=0 max=5 step=0.01] — {tg=aphrodite,mancer,ooba,koboldcpp,tabby,llamacpp,dreamgen} | 1477 |
| dry_base_textgenerationwebui / dry_base_counter_textgenerationwebui | range+number | Base — [min=1 max=4 step=0.01] — {tg=aphrodite,mancer,ooba,koboldcpp,tabby,llamacpp,dreamgen} | 1482 |
| dry_allowed_length_textgenerationwebui / dry_allowed_length_counter_textgenerationwebui | range+number | Allowed Length — [min=1 max=20 step=1] — {tg=aphrodite,mancer,ooba,koboldcpp,tabby,llamacpp,dreamgen} | 1487 |
| dry_penalty_last_n_textgenerationwebui / dry_penalty_last_n_counter_textgenerationwebui | range+number | Penalty Range — [min=0 max=8192 step=1] — {tg=llamacpp,koboldcpp,tabby,aphrodite} | 1492 |
| dry_sequence_breakers_textgenerationwebui | textarea | Sequence Breakers — placeholder: "JSON-serialized array of strings." — {tg=aphrodite,mancer,ooba,koboldcpp,tabby,llamacpp,dreamgen} | 1500 |
| dynatemp_block_ooba | div | Dynamic Temperature Minimum Temp Maximum Temp Exponent — sampler: dynatemp — {tg=ooba,mancer,koboldcpp,tabby,llamacpp,aphrodite} | 1503 |
| **— Dynamic Temperature —** | section |  | 1504 |
| dynatemp_textgenerationwebui | checkbox | Enable Dynamic Temperature — {tg=ooba,mancer,koboldcpp,tabby,llamacpp,aphrodite} | 1507 |
| min_temp_textgenerationwebui / min_temp_counter_textgenerationwebui | range+number | Minimum Temp — [min=0 max=5 step=0.01] — {tg=ooba,mancer,koboldcpp,tabby,llamacpp,aphrodite} | 1516 |
| max_temp_textgenerationwebui / max_temp_counter_textgenerationwebui | range+number | Maximum Temp — [min=0 max=5 step=0.01] — {tg=ooba,mancer,koboldcpp,tabby,llamacpp,aphrodite} | 1521 |
| dynatemp_exponent_textgenerationwebui / dynatemp_exponent_counter_textgenerationwebui | range+number | Exponent — [min=0.001 max=10 step=0.001] — {tg=ooba,mancer,koboldcpp,tabby,llamacpp,aphrodite} | 1526 |
| mirostat_block_ooba | div | Mirostat Mode Tau Eta — sampler: mirostat_mode — {tg=ooba,infermaticai,koboldcpp,llamacpp,tabby} | 1531 |
| **— Mirostat —** | section |  | 1532 |
| mirostat_mode_textgenerationwebui / mirostat_mode_counter_textgenerationwebui | range+number | Mode — [min=0 max=2 step=1] — {tg=ooba,infermaticai,koboldcpp,llamacpp,tabby} | 1539 |
| mirostat_tau_textgenerationwebui / mirostat_tau_counter_textgenerationwebui | range+number | Tau — [min=0 max=20 step=0.01] — {tg=ooba,infermaticai,koboldcpp,llamacpp,tabby} | 1544 |
| mirostat_eta_textgenerationwebui / mirostat_eta_counter_textgenerationwebui | range+number | Eta — [min=0 max=1 step=0.01] — {tg=ooba,infermaticai,koboldcpp,llamacpp,tabby} | 1549 |
| beamSearchBlock | div | Beam Search # of Beams Length Penalty Early Stopping — sampler: num_beams — {tg=ooba} | 1554 |
| **— Beam Search —** | section |  | 1555 |
| num_beams_textgenerationwebui / num_beams_counter_textgenerationwebui | range+number | [min=1 max=20 step=1] — {tg=ooba} | 1567 |
| length_penalty_textgenerationwebui / length_penalty_counter_textgenerationwebui | range+number | [min=-5 max=5 step=0.1] — {tg=ooba} | 1575 |
| early_stopping_textgenerationwebui | checkbox | Early Stopping — tip: Controls the stopping condition for beam search. If checked, the generation stops as soon as there are '# of Beams' seq… — {tg=ooba} | 1580 |
| contrastiveSearchBlock | div | Contrastive Search Penalty Alpha — sampler: penalty_alpha — {tg=ooba} | 1587 |
| **— Contrastive Search —** | section |  | 1588 |
| penalty_alpha_textgenerationwebui / penalty_alpha_counter_textgenerationwebui | range+number | [min=0 max=5 step=0.05] — {tg=ooba} | 1597 |
| do_sample_textgenerationwebui | checkbox | Do Sample — {tg=ooba} | 1604 |
| add_bos_token_textgenerationwebui | checkbox | Add BOS Token — tip: Add the bos_token to the beginning of prompts. Disabling this can make the replies more creative. — {tg=ooba,tabby} | 1608 |
| ban_eos_token_textgenerationwebui | checkbox | Ban EOS Token — tip: Ban the eos_token. This forces the model to never end the generation prematurely. — {tg=ooba,llamacpp,tabby,koboldcpp,dreamgen} | 1613 |
| ignore_eos_token_textgenerationwebui | checkbox | Ignore EOS Token — tip: Ignore the EOS Token even if it generates. — {tg=vllm,aphrodite,infermaticai} | 1618 |
| skip_special_tokens_textgenerationwebui | checkbox | Skip Special Tokens — {tg≠generic} | 1623 |
| include_reasoning_textgenerationwebui | checkbox | Request Model Reasoning — {tg=openrouter} | 1627 |
| temperature_last_textgenerationwebui | checkbox | Temperature Last — tip: Use the temperature sampler last. This is almost always the sensible thing to do. When enabled: sample the set of plaus… — {tg=ooba,aphrodite,tabby} | 1631 |
| speculative_ngram_textgenerationwebui | checkbox | Speculative Ngram — tip: Use a different speculative decoding method without a draft model. Using a draft model is preferred. Speculative ngram… — {tg=tabby} | 1636 |
| spaces_between_special_tokens_textgenerationwebui | checkbox | Spaces Between Special Tokens — {tg=vllm,aphrodite,infermaticai} | 1641 |
| seed_textgenerationwebui | number | Seed (-1 = random) — [min=-1] — {tg=mancer,ooba,koboldcpp,vllm,aphrodite,llamacpp,ollama,infermaticai,huggingface,generic} | 1651 |
| banned_tokens_block_ooba | div | Banned Tokens/Strings Global list Preset-specific list — sampler: banned_tokens — {tg≠generic} | 1653 |
| send_banned_tokens_label | label | sampler: send_banned_tokens — {tg≠generic} | 1660 |
| send_banned_tokens_textgenerationwebui | checkbox | Power toggle: send banned tokens/strings — {tg≠generic} | 1661 |
| banned_tokens_controls_ooba | div | Global list Preset-specific list — {tg≠generic} | 1665 |
| global_banned_tokens_textgenerationwebui | textarea | Global banned tokens/strings — placeholder: "some text as tokens [420, 69, 1337] "Some verbatim string"" — {tg≠generic} | 1670 |
| banned_tokens_textgenerationwebui | textarea | Preset-specific banned tokens/strings — placeholder: "some text as tokens [420, 69, 1337] "Some verbatim string"" — {tg≠generic} | 1676 |
| logit_bias_block_ooba | div | Logit Bias Add Helps to ban or reinforce the usage of certain tokens. — sampler: logit_bias | 1680 |
| logit_bias_textgenerationwebui | div | Logit Bias Add | 1681 |
| **— Logit Bias —** | section |  | 1682 |
| textgen_logit_bias_new_entry | button | Add | 1685 |
| cfg_block_ooba | div | CFG Scale Negative Prompt — sampler: guidance_scale — {tg=ooba,tabby} | 1697 |
| **— CFG —** | section |  | 1699 |
| guidance_scale_textgenerationwebui / guidance_scale_counter_textgenerationwebui | range+number | Scale — [min=0.1 max=4 step=0.05] — {tg=ooba,tabby} | 1705 |
| negative_prompt_textgenerationwebui | textarea | Negative Prompt — placeholder: "Add text here that would make the AI generate things you don't want i…" — tip: Used if CFG Scale is unset globally, per chat or character — {tg=ooba,tabby} | 1713 |
| grammar_block_ooba | div | Grammar String — sampler: grammar_string — {tg=ooba,aphrodite,tabby} | 1716 |
| **— Grammar String —** | section |  | 1718 |
| grammar_string_textgenerationwebui | textarea | Grammar String — placeholder: "Type in the desired custom grammar" — {tg=ooba,aphrodite,tabby} | 1727 |
| json_schema_block | div | JSON Schema Allow empty schema objects — sampler: json_schema — {tg=tabby,llamacpp,aphrodite} | 1729 |
| **— JSON Schema —** | section |  | 1731 |
| tabby_json_schema | textarea | JSON Schema — placeholder: "Type in the desired JSON schema" — {tg=tabby,llamacpp,aphrodite} | 1738 |
| json_schema_allow_empty_textgenerationwebui | checkbox | Allow empty schema objects — {tg=all} | 1740 |
| sampler_order_block_kcpp | div | sampler: sampler_order — {tg=koboldcpp} | 1744 |
| koboldcpp_order | div | sortable order: 0:Top K, 1:Top A, 2:Top P & Min P, 3:Tail Free Sampling, 4:Typical P, 5:Temperature, 6:Repetition Penalty — {tg=koboldcpp} | 1753 |
| koboldcpp_default_order | button | Load default order — {tg=koboldcpp} | 1783 |
| sampler_order_block_lcpp | div | sampler: samplers — {tg=llamacpp} | 1787 |
| **— Sampler Order —** | section |  | 1789 |
| llamacpp_samplers_sortable | div | sortable order: temperature:Temperature, top_k:Top K, top_p:Top P, typ_p:Typical P, min_p:Min P, xtc:Exclude Top Choices, dry:DRY, penalties:Rep/Freq/Pres Penalties, top_n_sigma:Top N-Sigma — {tg=llamacpp} | 1796 |
| llamacpp_samplers_default_order | button | Load default order — {tg=llamacpp} | 1807 |
| sampler_priority_block_ooba | div | sampler: sampler_priority — {tg=ooba} | 1811 |
| **— Sampler Priority —** | section |  | 1813 |
| sampler_priority_container | div | sortable order: repetition_penalty:Repetition Penalty, presence_penalty:Presence Penalty, frequency_penalty:Frequency Penalty, dry:DRY, temperature:Temperature, dynamic_temperature:Dynamic Temperature, quadratic_sampling:Quadratic / Smooth Sampling, top_n_sigma:Top Nsigma, top_k:Top K, top_p:Top P, typical_p:Typical P, epsilon_cutoff:Epsilon Cutoff, eta_cutoff:Eta Cutoff, tfs:Tail Free Sampling, top_a:Top A, min_p:Min P, adaptive_p:Adaptive-P, mirostat:Mirostat, xtc:XTC, encoder_repetition_penalty:Encoder Repetition Penalty, no_repeat_ngram:No Repeat Ngram — {tg=ooba} | 1820 |
| textgenerationwebui_default_order | button | Load default order — {tg=ooba} | 1843 |
| sampler_priority_block_aphrodite | div | sampler: samplers_priorities — {tg=aphrodite} | 1847 |
| **— Sampler Order —** | section |  | 1849 |
| sampler_priority_container_aphrodite | div | sortable order: dry:DRY, penalties:Penalties, no_repeat_ngram:No Repeat Ngram, temperature:Dynatemp & Temperature, top_nsigma:Top Nsigma, top_p_top_k:Top P & Top K, top_a:Top A, min_p:Min P, tfs:Tail-Free Sampling, eta_cutoff:Eta Cutoff, epsilon_cutoff:Epsilon Cutoff, typical_p:Typical P, quadratic:Cubic and Quadratic Sampling, xtc:XTC — {tg=aphrodite} | 1856 |
| aphrodite_default_order | button | Load default order — {tg=aphrodite} | 1872 |

##### Chat Completion settings (#openai_settings): names behavior, continue postfix, functions, media, reasoning, prefill, logit bias, Prompt Manager host

| element id | type | purpose | index.html:line |
|---|---|---|---|
| openai_settings | div | container | 1878 |
| **— Character Names Behavior ( ) —** | section |  | 1881 |
| character_names_display | span | container | 1885 |
| character_names_none | radio | None Never add character name prefixes. May behave poorly in groups, choose with caution. — value=-1 — name=character_names | 1891 |
| character_names_default | radio | Default Add prefixes for groups and past personas. Otherwise, make sure you provide names… — value=0 — name=character_names | 1898 |
| character_names_completion | radio | Completion Object Add character names to completion objects. Restrictions apply: only Lat… — value=1 — name=character_names | 1905 |
| character_names_content | radio | Message Content Prepend character names to message contents. — value=2 — name=character_names | 1912 |
| names_behavior | hidden | Hidden value bound to character_names_* radios | 1919 |
| **— Continue Postfix ( ) —** | section |  | 1923 |
| continue_postfix_display | span | container | 1927 |
| continue_postfix_none | radio | None — value=0 — name=continue_postfix | 1933 |
| continue_postfix_space | radio | Space — value=1 — name=continue_postfix | 1937 |
| continue_postfix_newline | radio | Newline — value=2 — name=continue_postfix | 1941 |
| continue_postfix_double_newline | radio | Double Newline — value=3 — name=continue_postfix | 1945 |
| continue_postfix | hidden | Hidden value bound to continue_postfix_* radios | 1949 |
| continue_prefill | checkbox | Continue prefill | 1954 |
| squash_system_messages | checkbox | Squash system messages | 1965 |
| use_sysprompt | checkbox | Use system prompt — {src=claude,makersuite,vertexai} | 1978 |
| openai_enable_web_search | checkbox | Enable web search — {src=makersuite,vertexai,aimlapi,openrouter,claude,electronhub,chutes,nanogpt} | 1991 |
| openai_function_calling | checkbox | Enable function calling — tip: Unsupported by the current model — {src=openai,cohere,mistralai,custom,claude,aimlapi,openrouter,groq,siliconflow,minimax,deepseek,makersuite,vertexai,ai21,xai,pollinations,moonshot,fireworks,cometapi,electronhub,chutes,azure_openai,zai,nanogpt,workers_ai} | 2005 |
| openai_function_calling_supported | div | {src=openai,cohere,mistralai,custom,claude,aimlapi,openrouter,groq,siliconflow,minimax,deepseek,makersuite,vertexai,ai21,xai,pollinations,moonshot,fireworks,cometapi,electronhub,chutes,azure_openai,zai,nanogpt,workers_ai} | 2007 |
| tool_call_recurse_limit_block | div | Tool Call Recurse Limit — {src=openai,cohere,mistralai,custom,claude,aimlapi,openrouter,groq,siliconflow,minimax,deepseek,makersuite,vertexai,ai21,xai,pollinations,moonshot,fireworks,cometapi,electronhub,chutes,azure_openai,zai,nanogpt,workers_ai} | 2017 |
| tool_call_recurse_limit / tool_call_recurse_limit_counter | range+number | Tool Call Recurse Limit — [min=1 max=50 step=1] — {src=openai,cohere,mistralai,custom,claude,aimlapi,openrouter,groq,siliconflow,minimax,deepseek,makersuite,vertexai,ai21,xai,pollinations,moonshot,fireworks,cometapi,electronhub,chutes,azure_openai,zai,nanogpt,workers_ai} | 2024 |
| tool_reasoning_mode | select | Interleaved Thinking — opts: disabled=Disabled, since_last_user=Since Last User Message, active_chain=Active Tool Chain — {src=openrouter,custom} | 2038 |
| openrouter_interleaved_thinking_disabled_hint | div | Enable "Request model reasoning" to use Interleaved Thinking. — {src=openrouter,custom} | 2050 |
| openai_media_inlining | checkbox | Send inline media — {src=openai,aimlapi,openrouter,mistralai,makersuite,vertexai,claude,custom,xai,pollinations,moonshot,cohere,cometapi,nanogpt,electronhub,azure_openai,zai,siliconflow,chutes,workers_ai} | 2058 |
| openai_image_inlining_supported | div | {src=openai,aimlapi,openrouter,mistralai,makersuite,vertexai,claude,custom,xai,pollinations,moonshot,cohere,cometapi,nanogpt,electronhub,azure_openai,zai,siliconflow,chutes,workers_ai} | 2061 |
| openai_video_inlining_supported | div | {src=makersuite,vertexai,openrouter,zai} | 2065 |
| openai_audio_inlining_supported | div | {src=makersuite,vertexai,openrouter,openai,custom} | 2069 |
| image_inlining_hint | div | {src=openai,aimlapi,openrouter,mistralai,makersuite,vertexai,claude,custom,xai,pollinations,moonshot,cohere,cometapi,nanogpt,electronhub,azure_openai,zai,siliconflow,chutes,workers_ai} | 2075 |
| openai_inline_image_quality | select | Inline Image Quality — opts: auto=Auto, low=Low, high=High — {src=openai,custom,xai,pollinations,cohere,cometapi,nanogpt,moonshot,aimlapi,openrouter,mistralai,electronhub,azure_openai,zai,siliconflow,chutes,makersuite,vertexai,workers_ai} | 2085 |
| request_images_block | div | {src=makersuite,vertexai} | 2093 |
| openai_request_images | checkbox | Request inline images — tip: Gemini 2.5 Flash Image / Gemini 3 Pro Image — {src=makersuite,vertexai} | 2095 |
| request_images_settings | div | {src=makersuite,vertexai} | 2109 |
| request_image_resolution | select | Resolution — opts: Auto, 1K, 2K, 4K — {src=makersuite,vertexai} | 2114 |
| request_image_aspect_ratio | select | Aspect Ratio — opts: Auto, 1:1, 9:16, 16:9, 3:4, 4:3, 3:2, 2:3, 5:4, 4:5, 21:9 — {src=makersuite,vertexai} | 2125 |
| openai_show_thoughts | checkbox | Request model reasoning — {src=deepseek,aimlapi,openrouter,custom,claude,xai,makersuite,vertexai,pollinations,moonshot,mistralai,fireworks,cometapi,electronhub,chutes,azure_openai,nanogpt,zai,workers_ai} | 2143 |
| .oneline-dropdown | div | tip: Constrains effort on reasoning for reasoning models. Reducing reasoning effort can result in faster responses and fewer… — {src=openai,custom,claude,xai,makersuite,vertexai,aimlapi,openrouter,pollinations,perplexity,cometapi,electronhub,azure_openai,chutes,nanogpt,deepseek,fireworks} | 2159 |
| openai_reasoning_effort | select | Reasoning Effort — opts: auto=Auto, min=Minimum, low=Low, medium=Medium, high=High, max=Maximum — {src=openai,custom,claude,xai,makersuite,vertexai,aimlapi,openrouter,pollinations,perplexity,cometapi,electronhub,azure_openai,chutes,nanogpt,deepseek,fireworks} | 2164 |
| openai_verbosity | select | Verbosity — opts: auto=Auto, low=Low, medium=Medium, high=High — {src=openai,custom,openrouter,claude} | 2200 |
| claude_assistant_prefill_text | span | Assistant Prefill — {src=claude} | 2217 |
| claude_assistant_prefill | textarea | Assistant Prefill — placeholder: "Start Claude's answer with..." — {src=claude} | 2220 |
| claude_assistant_impersonation_text | span | Assistant Impersonation Prefill — {src=claude} | 2222 |
| claude_assistant_impersonation | textarea | Assistant Impersonation Prefill — placeholder: "Start Claude's answer with..." — {src=claude} | 2225 |
| claude_function_prefill_warning | div | {src=claude} | 2227 |
| logit_bias_openai | div | Logit Bias — {src=openai,aimlapi,openrouter,custom,electronhub,azure_openai,chutes} | 2234 |
| openai_logit_bias_preset | select | Logit bias preset (ban/reinforce tokens) — {src=openai,aimlapi,openrouter,custom,electronhub,azure_openai,chutes} | 2243 |
| openai_logit_bias_new_preset | button | tip: New preset — {src=openai,aimlapi,openrouter,custom,electronhub,azure_openai,chutes} | 2245 |
| openai_logit_bias_import_preset | button | tip: Import preset — {src=openai,aimlapi,openrouter,custom,electronhub,azure_openai,chutes} | 2246 |
| openai_logit_bias_export_preset | button | tip: Export preset — {src=openai,aimlapi,openrouter,custom,electronhub,azure_openai,chutes} | 2247 |
| openai_logit_bias_delete_preset | button | tip: Delete preset — {src=openai,aimlapi,openrouter,custom,electronhub,azure_openai,chutes} | 2248 |
| openai_logit_bias_import_file | file | Hidden file input: logit bias preset import — {src=openai,aimlapi,openrouter,custom,electronhub,azure_openai,chutes} | 2249 |
| **— View / Edit bias preset —** | section |  | 2252 |
| openai_logit_bias_new_entry | button | Add bias entry — {src=openai,aimlapi,openrouter,custom,electronhub,azure_openai,chutes} | 2257 |
| completion_prompt_manager | div | Prompt Manager list host (rendered by PromptManager.js) | 2274 |

#### API Connections — main API selector & AI Horde (#rm_api_block, toggle #API-status-top in #sys-settings-button)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| sys-settings-button | div | container .drawer | 2282 |
| API-status-top | button | tip: API Connections | 2284 |
| rm_api_block | div | container .drawer-content | 2286 |
| **— API —** | section |  | 2287 |
| title_api | h3 | API | 2287 |
| main-API-selector-block | div | Text Completion Chat Completion NovelAI AI Horde KoboldAI Classic | 2289 |
| main_api | select | API — opts: textgenerationwebui=Text Completion, openai=Chat Completion, novel=NovelAI, koboldhorde=AI Horde, kobold=KoboldAI Classic | 2290 |
| kobold_horde | div | container | 2298 |
| kobold_horde_block | div | container | 2300 |
| horde_auto_adjust_context_length | checkbox | Adjust context size to worker capabilities | 2325 |
| horde_auto_adjust_response_length | checkbox | Adjust response length to worker capabilities | 2329 |
| horde_trusted_workers_only | checkbox | Trusted workers only | 2333 |
| adjustedHordeParams | small | Context : --, Response : -- | 2336 |
| **— API key —** | section |  | 2337 |
| horde_kudos | a | View my Kudos | 2339 |
| horde_api_key | text | AI Horde API key (0000000000 = anonymous) — placeholder: "0000000000" | 2343 |
| horde_api_key_button | button | tip: Save and connect | 2344 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_horde | 2345 |
| **— Models —** | section |  | 2350 |
| horde_refresh | button | tip: Refresh models | 2352 |
| horde_model | select | Horde model(s) — opts: -- Horde models not loaded -- | 2356 |

##### KoboldAI Classic (#kobold_api)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| kobold_api | div | container | 2366 |
| kobold_api_block | div | container | 2368 |
| **— API url —** | section |  | 2369 |
| api_url_text | text | API url Example: http://127.0.0.1:5000/api — placeholder: "http://127.0.0.1:5000/api" — server-history: kobold | 2371 |
| koboldcpp_hint | div | container .neutral_warning | 2372 |
| api_button | button | Connect | 2376 |
| .api_loading | button | Cancel | 2377 |

##### NovelAI (#novel_api)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| novel_api | div | container | 2386 |
| **— Novel API key —** | section |  | 2388 |
| api_key_novel | text | NovelAI API key | 2399 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_novel | 2400 |
| **— Novel AI Model —** | section |  | 2406 |
| model_novel_select | select | NovelAI model — opts: clio-v1=Clio, kayra-v1=Kayra, llama-3-erato-v1=Erato | 2411 |
| api_button_novel | button | Connect | 2417 |
| .api_loading | button | Cancel | 2418 |

##### Text Completion (#textgenerationwebui_api) — per-backend fields via data-tg-type

| element id | type | purpose | index.html:line |
|---|---|---|---|
| textgenerationwebui_api | div | container | 2426 |
| **— API Type —** | section |  | 2429 |
| textgen_type | select | API Type — opts: aphrodite=Aphrodite, dreamgen=DreamGen, featherless=Featherless, generic=Generic (OpenAI-compatible) [LM S…, huggingface=HuggingFace (Inference Endpoi…, infermaticai=InfermaticAI, koboldcpp=KoboldCpp, llamacpp=llama.cpp, mancer=Mancer, ollama=Ollama, openrouter=OpenRouter, tabby=TabbyAPI, ooba=Text Generation WebUI (oobabooga), togetherai=TogetherAI, vllm=vLLM | 2430 |
| **— TogetherAI API Key —** | section |  | 2449 |
| api_key_togetherai | text | TogetherAI API Key — {tg=togetherai} | 2451 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_togetherai — {tg=togetherai} | 2452 |
| **— TogetherAI Model —** | section |  | 2458 |
| model_togetherai_select | select | TogetherAI Model — opts: -- Connect to the API -- — {tg=togetherai} | 2459 |
| **— OpenRouter API Key —** | section |  | 2467 |
| api_key_openrouter-tg | text | OpenRouter API key (or OAuth via Authorize) — {tg=openrouter} | 2476 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_openrouter — {tg=openrouter} | 2477 |
| **— OpenRouter Model —** | section |  | 2483 |
| openrouter_model | select | OpenRouter Model — opts: -- Connect to the API -- — {tg=openrouter} | 2484 |
| **— Model Providers —** | section |  | 2491 |
| openrouter_provider_warning_text | button | tip: Deselect inapplicable provider(s) or select an applicable provider to avoid a 404 error. — {tg=openrouter} | 2493 |
| openrouter_providers_text | select | OpenRouter provider allow-list (multi) — {tg=openrouter} | 2495 |
| openrouter_allow_fallbacks_textgenerationwebui | checkbox | Allow fallback providers — {tg=openrouter} | 2498 |
| **— Model Quantizations —** | section |  | 2503 |
| openrouter_quantizations_text | select | Model Quantizations — opts: int4=Integer (4 bit), int8=Integer (8 bit), fp4=Floating point (4 bit), fp6=Floating point (6 bit), fp8=Floating point (8 bit), fp16=Floating point (16 bit), bf16=Brain floating point (16 bit), fp32=Floating point (32 bit), unknown=Unknown — {tg=openrouter} | 2504 |
| **— InfermaticAI API Key —** | section |  | 2518 |
| api_key_infermaticai | text | InfermaticAI API Key — {tg=infermaticai} | 2520 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_infermaticai — {tg=infermaticai} | 2521 |
| **— InfermaticAI Model —** | section |  | 2527 |
| model_infermaticai_select | select | InfermaticAI Model — opts: -- Connect to the API -- — {tg=infermaticai} | 2528 |
| **— DreamGen API key —** | section |  | 2536 |
| api_key_dreamgen | text | DreamGen API key — {tg=dreamgen} | 2543 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_dreamgen — {tg=dreamgen} | 2544 |
| **— DreamGen Model —** | section |  | 2550 |
| model_dreamgen_select | select | DreamGen Model — opts: -- Connect to the API -- — {tg=dreamgen} | 2551 |
| **— Mancer API key —** | section |  | 2561 |
| api_key_mancer | text | Mancer API key — {tg=mancer} | 2568 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_mancer — {tg=mancer} | 2569 |
| **— Mancer Model —** | section |  | 2576 |
| mancer_model | select | Mancer Model — opts: -- Connect to the API -- — {tg=mancer} | 2577 |
| **— API key (optional) —** | section |  | 2585 |
| api_key_generic | text | API key (optional) — {tg=generic} | 2587 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_generic — {tg=generic} | 2588 |
| **— Server URL —** | section |  | 2595 |
| generic_api_url_text | text | Server URL Example: http://127.0.0.1:5000 — server-history: generic — {tg=generic} | 2597 |
| **— Enter a Model ID —** | section |  | 2599 |
| generic_model_textgenerationwebui | text | Model ID (optional, free text) — placeholder: "Model ID (optional)" — {tg=generic} | 2601 |
| generic_model_fill | datalist | {tg=generic} | 2602 |
| **— Available Models —** | section |  | 2604 |
| generic_model_select | select | Available models (from server) — opts: -- Connect to the API -- — {tg=generic} | 2606 |
| **— API key (optional) —** | section |  | 2625 |
| api_key_ooba | text | API key (optional) — {tg=ooba} | 2627 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_ooba — {tg=ooba} | 2628 |
| **— Server URL —** | section |  | 2635 |
| textgenerationwebui_api_url_text | text | Server URL Example: http://127.0.0.1:5000 — server-history: ooba_blocking — {tg=ooba} | 2637 |
| custom_model_textgenerationwebui | text | Custom model (optional) — placeholder: "Custom model (optional)" — {tg=ooba} | 2639 |
| **— API key —** | section |  | 2647 |
| api_key_featherless | text | API key — {tg=featherless} | 2649 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_featherless — {tg=featherless} | 2650 |
| **— Featherless Model Selection —** | section |  | 2657 |
| featherless_model_search_bar | search | Search Featherless models — placeholder: "Search..." — {tg=featherless} | 2661 |
| featherless_model_sort_order | select | Featherless model sort — opts: search=A-Z, asc=A-Z, desc=Z-A, date_asc=Date Asc, date_desc=Date Desc — {tg=featherless} | 2662 |
| featherless_category_selection | select | Featherless category — opts: category, Top, New, All — {tg=featherless} | 2669 |
| featherless_class_selection | select | Featherless model class — opts: All Classes — {tg=featherless} | 2676 |
| featherless_model_pagination_container | div | {tg=featherless} | 2679 |
| featherless_model_grid_toggle | button | tip: Toggle grid view — {tg=featherless} | 2680 |
| featherless_model_card_block | div | Featherless model card grid — {tg=featherless} | 2682 |
| featherless_model | select | Featherless model — opts: -- Connect to the API -- — {tg=featherless} | 2686 |
| **— vLLM API key —** | section |  | 2698 |
| api_key_vllm | text | vLLM API key — {tg=vllm} | 2700 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_vllm — {tg=vllm} | 2701 |
| **— API URL —** | section |  | 2708 |
| vllm_api_url_text | text | API URL Example: http://127.0.0.1:8000 — server-history: vllm — {tg=vllm} | 2710 |
| **— vLLM Model —** | section |  | 2713 |
| vllm_model | select | vLLM Model — opts: -- Connect to the API -- — {tg=vllm} | 2714 |
| **— HuggingFace Token —** | section |  | 2722 |
| api_key_huggingface | text | HuggingFace Token — {tg=huggingface} | 2724 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_huggingface — {tg=huggingface} | 2725 |
| **— Endpoint URL —** | section |  | 2732 |
| huggingface_api_url_text | text | Endpoint URL Example: https://****.endpoints.huggingface.cloud — server-history: huggingface — {tg=huggingface} | 2734 |
| **— Aphrodite API key —** | section |  | 2744 |
| api_key_aphrodite | text | Aphrodite API key — {tg=aphrodite} | 2746 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_aphrodite — {tg=aphrodite} | 2747 |
| **— API URL —** | section |  | 2754 |
| aphrodite_api_url_text | text | API URL Example: http://127.0.0.1:5000 — server-history: aphrodite — {tg=aphrodite} | 2756 |
| **— Aphrodite Model —** | section |  | 2759 |
| aphrodite_model | select | Aphrodite Model — opts: -- Connect to the API -- — {tg=aphrodite} | 2760 |
| **— API key (optional) —** | section |  | 2773 |
| api_key_llamacpp | text | API key (optional) — {tg=llamacpp} | 2775 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_llamacpp — {tg=llamacpp} | 2776 |
| **— API URL —** | section |  | 2783 |
| llamacpp_api_url_text | text | API URL Example: http://127.0.0.1:8080 — server-history: llamacpp — {tg=llamacpp} | 2785 |
| **— llama.cpp Model —** | section |  | 2788 |
| llamacpp_model | select | llama.cpp Model — opts: -- Connect to the API -- — {tg=llamacpp} | 2791 |
| **— API URL —** | section |  | 2805 |
| ollama_api_url_text | text | API URL Example: http://127.0.0.1:11434 — server-history: ollama — {tg=ollama} | 2807 |
| **— Ollama Model —** | section |  | 2810 |
| ollama_model | select | Ollama Model — opts: -- Connect to the API -- — {tg=ollama} | 2813 |
| ollama_download_model | button | Download — {tg=ollama} | 2818 |
| **— Tabby API key —** | section |  | 2830 |
| api_key_tabby | text | Tabby API key — {tg=tabby} | 2832 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_tabby — {tg=tabby} | 2833 |
| **— API URL —** | section |  | 2840 |
| tabby_api_url_text | text | API URL Example: http://127.0.0.1:5000 — server-history: tabby — {tg=tabby} | 2842 |
| **— Tabby Model —** | section |  | 2845 |
| tabby_model | select | Tabby Model — opts: -- Connect to the API -- — {tg=tabby} | 2848 |
| tabby_download_model | button | Download — {tg=tabby} | 2870 |
| **— koboldcpp API key (optional) —** | section |  | 2882 |
| api_key_koboldcpp | text | koboldcpp API key (optional) — {tg=koboldcpp} | 2884 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_koboldcpp — {tg=koboldcpp} | 2885 |
| **— API URL —** | section |  | 2892 |
| koboldcpp_api_url_text | text | API URL Example: http://127.0.0.1:5001 — server-history: koboldcpp — {tg=koboldcpp} | 2894 |
| bypass_status_check_textgenerationwebui | checkbox | Bypass status check — {tg=ooba,generic} | 2899 |
| context_size_derived | checkbox | Derive context size from backend — {tg=koboldcpp,llamacpp} | 2903 |
| api_button_textgenerationwebui | button | Connect | 2908 |
| .openrouter_authorize | button | Authorize — tip: Get your OpenRouter API token using OAuth flow. You will be redirected to openrouter.ai — {tg=openrouter} | 2909 |
| .api_loading | button | Cancel | 2910 |

##### Chat Completion (#openai_api) — per-source forms via data-source

| element id | type | purpose | index.html:line |
|---|---|---|---|
| openai_api | div | container | 2918 |
| **— Chat Completion Source —** | section |  | 2919 |
| chat_completion_source | select | Chat Completion Source — opts: openai=OpenAI, custom=Custom (OpenAI-compatible), ai21=AI21, aimlapi=AI/ML API, azure_openai=Azure OpenAI, chutes=Chutes, claude=Claude, workers_ai=Cloudflare Workers AI, cohere=Cohere, deepseek=DeepSeek, electronhub=Electron Hub, fireworks=Fireworks AI, groq=Groq, makersuite=Google AI Studio, vertexai=Google Vertex AI, mistralai=MistralAI, minimax=MiniMax, moonshot=Moonshot AI, nanogpt=NanoGPT, openrouter=OpenRouter, perplexity=Perplexity, pollinations=Pollinations, siliconflow=SiliconFlow, xai=xAI (Grok), zai=Z.AI (GLM) | 2922 |
| **— Reverse Proxy —** | section |  | 2956 |
| openai_proxy_preset | select | Proxy Presets Saved addresses and passwords. — {src=openai,claude,mistralai,makersuite,vertexai,deepseek,xai,zai,moonshot} | 2971 |
| save_proxy | button | tip: Save Proxy — {src=openai,claude,mistralai,makersuite,vertexai,deepseek,xai,zai,moonshot} | 2973 |
| delete_proxy | button | tip: Delete Proxy — {src=openai,claude,mistralai,makersuite,vertexai,deepseek,xai,zai,moonshot} | 2974 |
| openai_reverse_proxy_name | text | Proxy Name This will show up as your saved preset. — placeholder: "..." — {src=openai,claude,mistralai,makersuite,vertexai,deepseek,xai,zai,moonshot} | 2986 |
| openai_reverse_proxy | text | Proxy Server URL Alternative server URL (leave empty to use the default value). — placeholder: "https://api.openai.com/v1" — {src=openai,claude,mistralai,makersuite,vertexai,deepseek,xai,zai,moonshot} | 2998 |
| openai_proxy_access_key | text | Proxy Password Will be used as a password for the proxy instead of API key. — {src=openai,claude,mistralai,makersuite,vertexai,deepseek,xai,zai,moonshot} | 3014 |
| openai_proxy_access_key_show | button | tip: Peek a password — {src=openai,claude,mistralai,makersuite,vertexai,deepseek,xai,zai,moonshot} | 3015 |
| ReverseProxyWarningMessage | div | {src=openai,claude,mistralai,makersuite,vertexai,deepseek,xai,zai,moonshot} | 3019 |
| openai_form | form | {src=openai} | 3035 |
| **— OpenAI API key —** | section |  | 3036 |
| openai_api_usage | a | View API Usage Metrics — {src=openai} | 3038 |
| api_key_openai | text | OpenAI API key — {src=openai} | 3055 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_openai — {src=openai} | 3056 |
| ReverseProxyWarningMessage2 | div | Use "Proxy password" field instead. This input will be ignored. — {src=openai} | 3058 |
| **— OpenAI Model —** | section |  | 3067 |
| model_openai_select | select | OpenAI Model — opts: gpt-6-astra, gpt-5.6, gpt-5.6-sol, gpt-5.6-terra, gpt-5.6-luna, gpt-5.5, gpt-5.5-2026-04-23, gpt-5.4, gpt-5.4-2026-03-05, gpt-5.4-mini, gpt-5.4-mini-2026-03-17, gpt-5.4-nano (+45 more) — {src=openai} | 3068 |
| openai_external_category | optgroup | {src=openai} | 3160 |
| openai_bypass_status_check | checkbox | Bypass API status check — {src=openai} | 3164 |
| openai_show_external_models | checkbox | Show "External" models (provided by API) — {src=openai} | 3168 |
| claude_form | form | {src=claude} | 3173 |
| **— Claude API Key —** | section |  | 3174 |
| api_key_claude | text | Claude API key — {src=claude} | 3179 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_claude — {src=claude} | 3180 |
| **— Claude Model —** | section |  | 3186 |
| model_claude_select | select | Claude Model — opts: claude-fable-5-1, claude-fable-5, claude-opus-5, claude-sonnet-5, claude-opus-4-8, claude-opus-4-7, claude-opus-4-6, claude-opus-4-5, claude-opus-4-5-20251101, claude-sonnet-4-6, claude-sonnet-4-5, claude-sonnet-4-5-20250929 (+6 more) — {src=claude} | 3187 |
| openrouter_form | form | {src=openrouter} | 3211 |
| **— OpenRouter API Key —** | section |  | 3212 |
| api_key_openrouter | text | OpenRouter API key (or OAuth via Authorize) — {src=openrouter} | 3221 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_openrouter — {src=openrouter} | 3222 |
| **— OpenRouter Model —** | section |  | 3228 |
| model_openrouter_select | select | OpenRouter Model — opts: -- Connect to the API -- — {src=openrouter} | 3229 |
| openrouter_use_fallback | checkbox | Allow fallback models — {src=openrouter} | 3234 |
| **— Model Providers —** | section |  | 3238 |
| openrouter_provider_warning_chat | button | tip: Deselect inapplicable provider(s) or select an applicable provider to avoid a 404 error. — {src=openrouter} | 3240 |
| openrouter_providers_chat | select | OpenRouter provider allow-list (multi) — {src=openrouter} | 3242 |
| openrouter_allow_fallbacks | checkbox | Allow fallback providers — {src=openrouter} | 3245 |
| **— Model Quantizations —** | section |  | 3254 |
| openrouter_quantizations_chat | select | Model Quantizations — opts: int4=Integer (4 bit), int8=Integer (8 bit), fp4=Floating point (4 bit), fp6=Floating point (6 bit), fp8=Floating point (8 bit), fp16=Floating point (16 bit), bf16=Brain floating point (16 bit), fp32=Floating point (32 bit), unknown=Unknown — {src=openrouter} | 3255 |
| ai21_form | form | {src=ai21} | 3268 |
| **— AI21 API Key —** | section |  | 3269 |
| api_key_ai21 | text | AI21 API Key — {src=ai21} | 3271 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_ai21 — {src=ai21} | 3272 |
| **— AI21 Model —** | section |  | 3278 |
| model_ai21_select | select | AI21 Model — opts: jamba-mini, jamba-large, jamba-1.7-mini, jamba-1.7-large, jamba-1.6-mini, jamba-1.6-large, jamba-1.5-mini, jamba-1.5-large, jamba-instruct-preview — {src=ai21} | 3279 |
| makersuite_form | form | {src=makersuite} | 3302 |
| **— Google AI Studio API Key —** | section |  | 3303 |
| api_key_makersuite | text | Google AI Studio API Key — {src=makersuite} | 3305 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_makersuite — {src=makersuite} | 3306 |
| **— Google Model —** | section |  | 3312 |
| model_google_select | select | Google Model — opts: gemini-3.7-flash, gemini-3.6-flash, gemini-3.5-flash, gemini-3.5-flash-lite, gemini-3.1-pro-preview, gemini-3.1-flash-lite, gemini-3.1-flash-image, gemini-3-pro-image, gemini-3-flash-preview, gemini-2.5-pro, gemini-2.5-flash, gemini-2.5-flash-lite (+15 more) — {src=makersuite} | 3313 |
| google_other_models | optgroup | {src=makersuite} | 3361 |
| vertexai_form | div | {src=vertexai} | 3365 |
| **— Google Vertex AI Configuration —** | section |  | 3366 |
| vertexai_auth_mode | select | Authentication Mode: — opts: express=Express Mode (API Key), full=Full Version (Service Account) — {src=vertexai} | 3371 |
| vertexai_express_config | div | {src=vertexai} | 3378 |
| **— Google Vertex AI Configuration (Express mode) —** | section |  | 3379 |
| api_key_vertexai | text | Vertex AI API key (Express) — {src=vertexai} | 3391 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_vertexai — {src=vertexai} | 3392 |
| vertexai_express_project_id | text | Vertex project ID (Express) — placeholder: "Enter your Project ID" — {src=vertexai} | 3404 |
| vertexai_full_config | div | {src=vertexai} | 3416 |
| **— Service Account Configuration —** | section |  | 3417 |
| vertexai_service_account_status | div | {src=vertexai} | 3428 |
| vertexai_service_account_info | span | {src=vertexai} | 3429 |
| vertexai_service_account_json | text | Service account JSON — {src=vertexai} | 3432 |
| .manage-api-keys | button | tip: Manage API keys — secret key: vertexai_service_account_json — {src=vertexai} | 3433 |
| vertexai_validate_service_account | button | Validate JSON — {src=vertexai} | 3439 |
| .notes-link | a | tip: View available regions and models — {src=vertexai} | 3450 |
| vertexai_region | text | Vertex region — placeholder: "e.g., global, us-central1, europe-west1, asia-northeast1" — {src=vertexai} | 3454 |
| vertexai_region_suggestions | datalist | {src=vertexai} | 3456 |
| **— Google Model —** | section |  | 3481 |
| model_vertexai_select | select | Google Model — opts: gemini-3.7-flash, gemini-3.6-flash, gemini-3.5-flash, gemini-3.5-flash-lite, gemini-3.1-pro-preview, gemini-3.1-flash-lite, gemini-3.1-flash-image, gemini-3-pro-image, gemini-3-flash-preview, gemini-2.5-pro, gemini-2.5-flash, gemini-2.5-flash-lite (+4 more) — {src=vertexai} | 3482 |
| mistralai_form | form | {src=mistralai} | 3517 |
| **— MistralAI API Key —** | section |  | 3518 |
| api_key_mistralai | text | MistralAI API Key — {src=mistralai} | 3520 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_mistralai — {src=mistralai} | 3521 |
| **— MistralAI Model —** | section |  | 3527 |
| model_mistralai_select | select | MistralAI Model — opts: -- Connect to the API -- — {src=mistralai} | 3528 |
| groq_form | div | {src=groq} | 3533 |
| **— Groq API Key —** | section |  | 3534 |
| api_key_groq | text | Groq API Key — {src=groq} | 3536 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_groq — {src=groq} | 3537 |
| **— Groq Model —** | section |  | 3542 |
| model_groq_select | select | Groq model — opts: qwen/qwen3-32b, deepseek-r1-distill-llama-70b, gemma2-9b-it, meta-llama/llama-4-scout-17b-16e-instruct, meta-llama/llama-4-maverick-17b-128e-inst…, llama-3.1-8b-instant, llama-3.3-70b-versatile, llama-guard-3-8b, llama3-70b-8192, llama3-8b-8192, mistral-saba-24b — {src=groq} | 3543 |
| siliconflow_form | div | {src=siliconflow} | 3557 |
| **— SiliconFlow API Key —** | section |  | 3558 |
| api_key_siliconflow | text | SiliconFlow API Key — {src=siliconflow} | 3560 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_siliconflow — {src=siliconflow} | 3561 |
| **— SiliconFlow Endpoint —** | section |  | 3566 |
| siliconflow_endpoint | select | SiliconFlow endpoint — opts: global=Global (siliconflow.com), cn=China (siliconflow.cn) — {src=siliconflow} | 3567 |
| **— SiliconFlow Model —** | section |  | 3571 |
| model_siliconflow_select | select | SiliconFlow model — opts: -- Connect to the API -- — {src=siliconflow} | 3572 |
| minimax_form | div | {src=minimax} | 3576 |
| **— MiniMax API Key —** | section |  | 3577 |
| api_key_minimax | text | MiniMax API Key — {src=minimax} | 3579 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_minimax — {src=minimax} | 3580 |
| **— MiniMax Endpoint —** | section |  | 3585 |
| minimax_endpoint | select | MiniMax endpoint — opts: global=Global (minimax.io), cn=China (minimaxi.com) — {src=minimax} | 3586 |
| **— MiniMax Model —** | section |  | 3590 |
| model_minimax_select | select | MiniMax model — opts: MiniMax-M2.7, MiniMax-M2.7-highspeed, MiniMax-M2.5, MiniMax-M2.5-highspeed, MiniMax-M2.1, MiniMax-M2.1-highspeed, MiniMax-M2, M2-her — {src=minimax} | 3591 |
| electronhub_form | div | {src=electronhub} | 3602 |
| **— Electron Hub API Key —** | section |  | 3603 |
| api_key_electronhub | text | Electron Hub API key — {src=electronhub} | 3608 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_electronhub — {src=electronhub} | 3609 |
| **— Electron Hub Model —** | section |  | 3615 |
| model_electronhub_select | select | Electron Hub Model — opts: -- Connect to the API -- — {src=electronhub} | 3616 |
| chutes_form | div | {src=chutes} | 3621 |
| **— Chutes API Key —** | section |  | 3622 |
| chutes_credits_display | div | View Billing/Balance — {src=chutes} | 3623 |
| api_key_chutes | text | Chutes API key — {src=chutes} | 3627 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_chutes — {src=chutes} | 3628 |
| **— Chutes Model —** | section |  | 3634 |
| model_chutes_select | select | Chutes Model — opts: -- Connect to the API -- — {src=chutes} | 3635 |
| nanogpt_form | div | {src=nanogpt} | 3640 |
| **— NanoGPT API Key —** | section |  | 3641 |
| api_key_nanogpt | text | NanoGPT API key — {src=nanogpt} | 3645 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_nanogpt — {src=nanogpt} | 3646 |
| **— NanoGPT Model —** | section |  | 3652 |
| model_nanogpt_select | select | NanoGPT Model — opts: -- Connect to the API -- — {src=nanogpt} | 3653 |
| **— Model Providers —** | section |  | 3658 |
| nanogpt_provider_warning | button | tip: Deselect inapplicable provider(s) or select an applicable provider to avoid a 404 error. — {src=nanogpt} | 3660 |
| nanogpt_provider | select | Model Providers — opts: Auto — {src=nanogpt} | 3662 |
| nanogpt_payg_override | checkbox | Use pay-as-you-go billing — {src=nanogpt} | 3666 |
| workers_ai_form | div | {src=workers_ai} | 3671 |
| **— Cloudflare Workers AI API Key —** | section |  | 3672 |
| api_key_workers_ai | text | Cloudflare Workers AI API key — {src=workers_ai} | 3674 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_workers_ai — {src=workers_ai} | 3675 |
| **— Cloudflare Account ID —** | section |  | 3680 |
| workers_ai_account_id | text | Cloudflare account ID — {src=workers_ai} | 3682 |
| **— Workers AI Model —** | section |  | 3685 |
| model_workers_ai_select | select | Workers AI Model — opts: -- Connect to the API -- — {src=workers_ai} | 3686 |
| deepseek_form | div | {src=deepseek} | 3691 |
| **— DeepSeek API Key —** | section |  | 3692 |
| api_key_deepseek | text | DeepSeek API Key — {src=deepseek} | 3694 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_deepseek — {src=deepseek} | 3695 |
| **— DeepSeek Model —** | section |  | 3701 |
| model_deepseek_select | select | DeepSeek Model — opts: -- Connect to the API -- — {src=deepseek} | 3702 |
| fireworks_form | div | {src=fireworks} | 3707 |
| **— Fireworks AI API Key —** | section |  | 3708 |
| api_key_fireworks | text | Fireworks AI API Key — {src=fireworks} | 3710 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_fireworks — {src=fireworks} | 3711 |
| **— Fireworks AI Model —** | section |  | 3717 |
| model_fireworks_select | select | Fireworks AI Model — opts: -- Connect to the API -- — {src=fireworks} | 3718 |
| cometapi_form | div | {src=cometapi} | 3723 |
| **— CometAPI API Key —** | section |  | 3724 |
| api_key_cometapi | text | CometAPI API key — {src=cometapi} | 3726 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_cometapi — {src=cometapi} | 3727 |
| **— CometAPI Model —** | section |  | 3733 |
| model_cometapi_select | select | CometAPI Model — opts: -- Connect to the API -- — {src=cometapi} | 3735 |
| perplexity_form | div | {src=perplexity} | 3741 |
| **— Perplexity API Key —** | section |  | 3742 |
| api_key_perplexity | text | Perplexity API Key — {src=perplexity} | 3744 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_perplexity — {src=perplexity} | 3745 |
| **— Perplexity Model —** | section |  | 3750 |
| model_perplexity_select | select | Perplexity model — opts: sonar, sonar-pro, sonar-reasoning, sonar-reasoning-pro, sonar-deep-research, r1-1776, llama-3.1-sonar-small-128k-online, llama-3.1-sonar-large-128k-online, llama-3.1-sonar-huge-128k-online, llama-3.1-sonar-small-128k-chat, llama-3.1-sonar-large-128k-chat — {src=perplexity} | 3751 |
| cohere_form | form | {src=cohere} | 3773 |
| **— Cohere API Key —** | section |  | 3774 |
| api_key_cohere | text | Cohere API Key — {src=cohere} | 3776 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_cohere — {src=cohere} | 3777 |
| **— Cohere Model —** | section |  | 3783 |
| model_cohere_select | select | Cohere Model — opts: c4ai-aya-23-8b, c4ai-aya-23, c4ai-aya-expanse-8b, c4ai-aya-expanse-32b, c4ai-aya-vision-8b, c4ai-aya-vision-32b, command-light, command, command-r, command-r-plus, command-r-08-2024, command-r-plus-08-2024 (+5 more) — {src=cohere} | 3784 |
| custom_form | form | {src=custom} | 3809 |
| **— Custom Endpoint (Base URL) —** | section |  | 3810 |
| custom_api_url_text | text | Custom Endpoint (Base URL) — placeholder: "Example: http://localhost:1234/v1" — {src=custom} | 3812 |
| **— Custom API Key (Optional) —** | section |  | 3820 |
| api_key_custom | text | Custom API key (optional); /chat/completions is appended to base URL automatically — {src=custom} | 3825 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_custom — {src=custom} | 3826 |
| **— Enter a Model ID —** | section |  | 3831 |
| custom_model_id | text | Model ID (free text) — placeholder: "Example: gpt-4o" — {src=custom} | 3833 |
| model_custom_select_fill | datalist | {src=custom} | 3834 |
| **— Available Models —** | section |  | 3836 |
| model_custom_select | select | Available models (from custom endpoint) — {src=custom} | 3838 |
| xai_form | div | {src=xai} | 3841 |
| **— xAI API Key —** | section |  | 3842 |
| api_key_xai | text | xAI API key — {src=xai} | 3848 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_xai — {src=xai} | 3849 |
| **— xAI Model —** | section |  | 3855 |
| model_xai_select | select | xAI Model — opts: -- Connect to the API -- — {src=xai} | 3856 |
| aimlapi_form | div | {src=aimlapi} | 3861 |
| **— AI/ML API Key —** | section |  | 3862 |
| api_key_aimlapi | text | AI/ML API Key — {src=aimlapi} | 3866 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_aimlapi — {src=aimlapi} | 3867 |
| **— AI/ML Model —** | section |  | 3872 |
| model_aimlapi_select | select | AI/ML API model — {src=aimlapi} | 3873 |
| pollinations_form | div | {src=pollinations} | 3876 |
| **— Pollinations Endpoint —** | section |  | 3877 |
| pollinations_endpoint | select | Pollinations Endpoint — opts: anonymous=Anonymous (No Key), authenticated=Authenticated (API Key) — {src=pollinations} | 3878 |
| pollinations_key_section | div | {src=pollinations} | 3882 |
| **— Pollinations API Key —** | section |  | 3883 |
| api_key_pollinations | text | Pollinations API key — placeholder: "sk_..." — {src=pollinations} | 3889 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_pollinations — {src=pollinations} | 3890 |
| **— Pollinations Model —** | section |  | 3896 |
| model_pollinations_select | select | Pollinations model — {src=pollinations} | 3897 |
| moonshot_form | div | {src=moonshot} | 3901 |
| **— Moonshot AI API Key —** | section |  | 3902 |
| api_key_moonshot | text | Moonshot AI API key — {src=moonshot} | 3908 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_moonshot — {src=moonshot} | 3909 |
| **— Moonshot AI Model —** | section |  | 3914 |
| model_moonshot_select | select | Moonshot model — opts: kimi-k2-0711-preview, moonshot-v1-8k, moonshot-v1-32k, moonshot-v1-128k, moonshot-v1-auto, kimi-latest, moonshot-v1-8k-vision-preview, moonshot-v1-32k-vision-preview, moonshot-v1-128k-vision-preview, kimi-thinking-preview — {src=moonshot} | 3915 |
| zai_form | div | {src=zai} | 3928 |
| **— Z.AI API Key —** | section |  | 3929 |
| api_key_zai | text | Z.AI API key — {src=zai} | 3935 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_zai — {src=zai} | 3936 |
| **— Z.AI Endpoint —** | section |  | 3941 |
| zai_endpoint | select | Z.AI endpoint — opts: common=Common API, coding=Coding API — {src=zai} | 3942 |
| **— Z.AI Model —** | section |  | 3946 |
| model_zai_select | select | Z.AI model — opts: glm-5.2, glm-5-turbo, glm-5v-turbo, glm-5.1, glm-5, glm-4.7, glm-4.7-flash, glm-4.7-flashx, glm-4.6, glm-4.6v, glm-4.6v-flash, glm-4.6v-flashx (+8 more) — {src=zai} | 3947 |
| azure_openai_settings | div | {src=azure_openai} | 3970 |
| **— Azure Base URL —** | section |  | 3972 |
| azure_base_url | text | Azure Base URL — placeholder: "https://your-resource.openai.azure.com/" — setting: azure_base_url — {src=azure_openai} | 3974 |
| **— Deployment Name —** | section |  | 3978 |
| azure_deployment_name | text | Azure deployment name — tip: The name of your model deployment in Azure. — placeholder: "your-deployment-name" — setting: azure_deployment_name — {src=azure_openai} | 3980 |
| **— API Version —** | section |  | 3984 |
| azure_api_version | select | Azure API version — opts: 2025-04-01-preview, 2024-10-21 — setting: azure_api_version — {src=azure_openai} | 3986 |
| **— Azure API Key —** | section |  | 3993 |
| api_key_azure_openai | text | Azure API key — setting: api_key_azure_openai — {src=azure_openai} | 3995 |
| .manage-api-keys | button | tip: Manage API keys — secret key: api_key_azure_openai — {src=azure_openai} | 3996 |
| **— Model Name —** | section |  | 4003 |
| azure_openai_model | select | Azure model name (fetched on Connect) — opts: Click 'Connect' to fetch model name — setting: azure_openai_model — {src=azure_openai} | 4005 |
| model_sorting_form | div | {src=openrouter,chutes,electronhub,nanogpt,aimlapi} | 4013 |
| **— Model Sorting —** | section |  | 4015 |
| cc_sort_models | select | Model list sorting — opts: alphabetically=Alphabetically, pricing.prompt=Prompt Price (cheapest), pricing.completion=Completion Price (chea…, context_length=Context Size — {src=openrouter,chutes,electronhub,nanogpt,aimlapi} | 4022 |
| cc_group_models | checkbox | Group by vendors — {src=openrouter,chutes,electronhub,nanogpt,aimlapi} | 4032 |
| prompt_post_processing_form | div | container | 4044 |
| **— Prompt Post-Processing —** | section |  | 4045 |
| custom_prompt_post_processing | select | Prompt Post-Processing — tip: Applies additional processing to the prompt before sending it to the API. — opts: None, merge_tools=Merge consecutive roles (with…, semi_tools=Semi-strict (alternating roles…, strict_tools=Strict (user first, alternat…, merge=Merge consecutive roles (no tools), semi=Semi-strict (alternating roles; no t…, strict=Strict (user first, alternating ro…, single=Single user message (no tools) | 4053 |
| api_button_openai | button | Connect | 4069 |
| .api_loading | button | Cancel | 4070 |
| customize_additional_parameters | button | Additional Parameters — {src=custom} | 4071 |
| .openrouter_authorize | button | Authorize — tip: Get your OpenRouter API token using OAuth flow. You will be redirected to openrouter.ai — {src=openrouter} | 4072 |
| test_api_button | button | Test Message — tip: Verifies your API connection by sending a short test message. Be aware that you'll be credited for it! | 4073 |
| auto-connect-checkbox | checkbox | Auto-connect to Last Server | 4082 |
| viewSecrets | a | View hidden API keys | 4085 |

#### AI Response Formatting (#AdvancedFormatting, #advanced-formatting-button): context template, instruct, system prompt, stop strings, tokenizer, reasoning, misc

| element id | type | purpose | index.html:line |
|---|---|---|---|
| advanced-formatting-button | div | container .drawer | 4091 |
| .fa-font | button | tip: AI Response Formatting | 4093 |
| AdvancedFormatting | div | container .drawer-content | 4095 |
| **— Advanced Formatting —** | section |  | 4097 |
| af_master_import_file | file | Hidden file input: Master Import (.json) | 4107 |
| af_master_import | button | Master Import — tip: Import Advanced Formatting settings You can also provide legacy files for Instruct and Context templates. | 4108 |
| af_master_export | button | Master Export — tip: Export Advanced Formatting settings | 4112 |
| advanced-formatting-cc-notice | div | Grayed-out options have no effect when Chat Completion API is used. | 4118 |
| ContextSettings | div | container | 4125 |
| **— Context Template —** | section |  | 4127 |
| context_derived | checkbox | Derive from Model Metadata, if possible. | 4133 |
| context_presets | select | Context template preset — preset mgr: context | 4139 |
| .fa-save | button | tip: Update current template | 4142 |
| .fa-pencil | button | tip: Rename current template | 4143 |
| .fa-file-circle-plus | button | tip: Save template as | 4144 |
| .fa-file-import | button | tip: Import template | 4145 |
| .fa-file-export | button | tip: Export template | 4146 |
| .fa-recycle | button | tip: Restore current template | 4147 |
| context_delete_preset | button | tip: Delete the template | 4148 |
| context_story_string | textarea | Story String | 4157 |
| context_story_string_position_block | div | Position: Default (top of context) In-chat @ Depth | 4160 |
| context_story_string_position | select | Position: — opts: 0=Default (top of context), 1=In-chat @ Depth | 4164 |
| context_story_string_inject_settings | div | Depth: Role: System User Assistant | 4169 |
| context_story_string_depth | number | Story string depth (In-chat) — [min=0] | 4174 |
| context_story_string_role | select | Story string role — opts: 0=System, 1=User, 2=Assistant | 4180 |
| context_example_separator | textarea | Example Separator | 4194 |
| context_chat_start | textarea | Chat Start | 4202 |
| **— Context Formatting —** | section |  | 4207 |
| always-force-name2-checkbox | checkbox | Always add character's name to prompt | 4214 |
| single_line | checkbox | Generate only one line per request | 4220 |
| collapse-newlines-checkbox | checkbox | Collapse Consecutive Newlines | 4226 |
| trim_spaces | checkbox | Trim spaces — tip: Disabling is not recommended. | 4232 |
| trim_sentences_checkbox | checkbox | Trim Incomplete Sentences | 4237 |
| context_use_stop_strings | checkbox | Separators as Stop Strings | 4243 |
| context_names_as_stop_strings | checkbox | Names as Stop Strings | 4247 |
| InstructSettingsColumn | div | container | 4254 |
| **— Instruct Template —** | section |  | 4255 |
| instruct_derived | checkbox | Derive from Model Metadata, if possible. | 4261 |
| instruct_bind_to_context | checkbox | Bind to Context If enabled, Context templates will be automatically selected based on selected Inst… | 4265 |
| instruct_enabled_label | label | tip: Enable Instruct Mode | 4268 |
| instruct_enabled | checkbox | Enable Instruct Mode | 4269 |
| instructSettingsBlock | div | container | 4274 |
| instruct_presets | select | Instruct template preset — preset mgr: instruct | 4278 |
| .fa-save | button | tip: Update current template | 4281 |
| .fa-pencil | button | tip: Rename current template | 4282 |
| .fa-file-circle-plus | button | tip: Save template as | 4283 |
| .fa-file-import | button | tip: Import template | 4284 |
| .fa-file-export | button | tip: Export template | 4285 |
| .fa-recycle | button | tip: Restore current template | 4286 |
| .fa-trash-can | button | tip: Delete template | 4287 |
| instruct_activation_regex | text | Activation regex (auto-select template by model name) — placeholder: "e.g. /llama(-)?[3/3.1]/i" | 4297 |
| instruct_wrap | checkbox | Wrap Sequences with Newline | 4301 |
| instruct_macro | checkbox | Replace Macro in Sequences | 4305 |
| instruct_sequences_as_stop_strings | checkbox | Sequences as Stop Strings | 4309 |
| instruct_skip_examples | checkbox | Skip Example Dialogues Formatting | 4313 |
| instruct_names_behavior | select | Include Names — opts: none=Never, force=Groups and Past Personas, always=Always | 4320 |
| InstructSequencesColumn | div | container | 4328 |
| **— Instruct Sequences —** | section |  | 4329 |
| instruct_story_string_prefix | textarea | Story String Prefix | 4348 |
| instruct_story_string_suffix | textarea | Story String Suffix | 4356 |
| instruct_input_sequence | textarea | User Message Prefix | 4366 |
| instruct_input_suffix | textarea | User Message Suffix | 4370 |
| instruct_output_sequence | textarea | Assistant Message Prefix | 4379 |
| instruct_output_suffix | textarea | Assistant Message Suffix | 4383 |
| instruct_system_sequence_block | div | System Message Prefix — tip: Inserted before a System (added by slash commands or extensions) message. | 4390 |
| instruct_system_sequence | textarea | System Message Prefix | 4392 |
| instruct_system_suffix_block | div | System Message Suffix — tip: Inserted after a System message. | 4394 |
| instruct_system_suffix | textarea | System Message Suffix | 4396 |
| instruct_system_same_as_user | checkbox | System same as User | 4401 |
| instruct_first_output_sequence | textarea | First Assistant Prefix | 4411 |
| instruct_last_output_sequence | textarea | Last Assistant Prefix | 4415 |
| instruct_first_input_sequence | textarea | First User Prefix | 4421 |
| instruct_last_input_sequence | textarea | Last User Prefix | 4425 |
| instruct_last_system_sequence | textarea | System Instruction Prefix | 4431 |
| instruct_stop_sequence | textarea | Stop Sequence | 4435 |
| instruct_user_alignment_message | textarea | User Filler Message | 4441 |
| SystemPromptColumn | div | container | 4447 |
| **— System Prompt —** | section |  | 4448 |
| sysprompt_enabled_label | label | tip: Enable System Prompt | 4453 |
| sysprompt_enabled | checkbox | Enable System Prompt | 4454 |
| SystemPromptBlock | div | Prompt Content Post-History Instructions | 4459 |
| sysprompt_select | select | System Prompt — preset mgr: sysprompt | 4461 |
| .fa-save | button | tip: Update current prompt | 4464 |
| .fa-pencil | button | tip: Rename current prompt | 4465 |
| .fa-file-circle-plus | button | tip: Save prompt as | 4466 |
| .fa-file-import | button | tip: Import template | 4467 |
| .fa-file-export | button | tip: Export template | 4468 |
| .fa-recycle | button | tip: Restore current prompt | 4469 |
| .fa-trash-can | button | tip: Delete prompt | 4470 |
| sysprompt_content | textarea | Prompt Content | 4479 |
| sysprompt_post_history | textarea | Post-History Instructions | 4487 |
| **— Custom Stopping Strings —** | section |  | 4492 |
| custom_stopping_strings | textarea | JSON serialized array of strings | 4507 |
| custom_stopping_strings_macro | checkbox | Replace Macro in Stop Strings | 4510 |
| **— Tokenizer —** | section |  | 4519 |
| tokenizer | select | Tokenizer — opts: 99=Best match (recommended), 0=None / Estimated, 1=GPT-2, 3=Llama 1/2, 12=Llama 3, 13=Gemma / Gemini, 14=Jamba, 15=Qwen2, 16=Command-R, 19=Command-A, 4=NerdStash (NovelAI Clio), 5=NerdStash v2 (NovelAI Kayra), 7=Mistral V1, 17=Mistral Nemo, 8=Yi, 11=Claude 1/2, 18=DeepSeek V3, 6=API (WebUI / koboldcpp) | 4524 |
| token_padding | number | Token Padding — [min=-2048 max=2048 step=1] | 4552 |
| **— Reasoning —** | section |  | 4556 |
| reasoning_auto_parse | checkbox | Auto-Parse | 4562 |
| reasoning_auto_expand | checkbox | Auto-Expand | 4568 |
| reasoning_show_hidden | checkbox | Show Hidden | 4574 |
| reasoning_add_to_prompts | checkbox | Add to Prompts (include reasoning in prompt) | 4582 |
| reasoning_max_additions | number | Max reasoning blocks added to prompt (Add to Prompts count) — [min=0 max=999] | 4588 |
| reasoning_select | select | Reasoning Formatting — preset mgr: reasoning | 4597 |
| .fa-save | button | tip: Update current template | 4600 |
| .fa-pencil | button | tip: Rename current template | 4601 |
| .fa-file-circle-plus | button | tip: Save template as | 4602 |
| .fa-file-import | button | tip: Import template | 4603 |
| .fa-file-export | button | tip: Export template | 4604 |
| .fa-recycle | button | tip: Restore current template | 4605 |
| .fa-trash-can | button | tip: Delete template | 4606 |
| reasoning_prefix | textarea | Prefix | 4612 |
| reasoning_suffix | textarea | Suffix | 4616 |
| reasoning_separator | textarea | Separator | 4622 |
| **— Miscellaneous —** | section |  | 4629 |
| bind_model_templates | checkbox | Bind Model to Templates — tip: When connecting to an API or choosing a model, automatically activate the current Instruct and Context templates if the… | 4632 |
| markdown_escape_strings | text | Non-markdown strings — placeholder: "comma delimited,no spaces between" | 4644 |
| start_reply_with | textarea | Start Reply With | 4656 |
| chat-show-reply-prefix-checkbox | checkbox | Show reply prefix in chat | 4659 |

#### World Info (#WorldInfo, #WI-SP-button)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| WI-SP-button | div | container .drawer | 4671 |
| WIDrawerIcon | button | tip: World Info | 4673 |
| WorldInfo | div | container .drawer-content | 4675 |
| WorldInfoheader | div | container .drag-grabber | 4676 |
| WI_panel_pin_div | div | tip: Locked = World Editor will stay open | 4678 |
| WI_panel_pin | checkbox | Lock World Info panel open | 4679 |
| **— Worlds/Lorebooks —** | section |  | 4685 |
| wi-holder | div | container | 4692 |
| wiTopBlock | div | container | 4693 |
| WIMultiSelector | div | Active World(s) for all chats -- World Info not found -- | 4694 |
| world_info | select | Active World(s) for all chats — opts: -- World Info not found -- | 4703 |
| **— Click to expand —** | section |  | 4715 |
| wiActivationSettings | div | container | 4720 |
| wiSliders | div | container | 4721 |
| world_info_depth / world_info_depth_counter | range+number | Scan Depth — [min=0 max=1000 step=1] | 4726 |
| world_info_budget / world_info_budget_counter | range+number | Context % — [min=1 max=100 step=1] | 4734 |
| world_info_budget_cap / world_info_budget_cap_counter | range+number | Budget Cap — [min=0 max=65536 step=1] | 4743 |
| world_info_min_activations / world_info_min_activations_counter | range+number | Min Activations — [min=0 max=100 step=1] | 4752 |
| world_info_min_activations_depth_max / world_info_min_activations_depth_max_counter | range+number | Max Depth — [min=0 max=100 step=1] | 4761 |
| world_info_max_recursion_steps / world_info_max_recursion_steps_counter | range+number | Max Recursion Steps — [min=0 max=10 step=1] | 4769 |
| world_info_character_strategy | select | Insertion Strategy — opts: 0=Sorted Evenly, 1=Character Lore First, 2=Global Lore First | 4777 |
| wiCheckboxes | div | container | 4784 |
| world_info_include_names | checkbox | Include Names | 4786 |
| world_info_recursive | checkbox | Recursive Scan | 4792 |
| world_info_case_sensitive | checkbox | Case-sensitive | 4798 |
| world_info_match_whole_words | checkbox | Match Whole Words | 4804 |
| world_info_use_group_scoring | checkbox | Use Group Scoring | 4810 |
| world_info_overflow_alert | checkbox | Alert On Overflow | 4816 |
| world_popup | div | container | 4827 |
| world_import_file | file | Hidden file input: WI import (.json/.png etc.) | 4830 |
| world_create_button | button | Create | 4831 |
| world_editor_select | select | Lorebook to edit — opts: --- Pick to Edit --- | 4836 |
| world_import_button | button | tip: Import World Info | 4839 |
| world_popup_export | button | tip: Export World Info | 4840 |
| world_popup_name_button | button | tip: Rename World Info | 4841 |
| world_duplicate | button | tip: Duplicate World Info | 4842 |
| world_popup_delete | button | tip: Delete World Info | 4843 |
| world_popup_new | button | tip: New Entry | 4846 |
| OpenAllWIEntries | button | tip: Open all Entries | 4847 |
| CloseAllWIEntries | button | tip: Close all Entries | 4848 |
| world_backfill_memos | button | tip: Fill empty Memo/Titles with Keywords | 4849 |
| world_apply_current_sorting | button | tip: Apply current sorting as Order | 4850 |
| world_info_search | search | Search WI entries — placeholder: "Search..." | 4851 |
| world_info_sort_order | select | WI entry sort order — opts: 14=Search, 0=Priority, 13=Custom, 1=Title A-Z, 2=Title Z-A, 3=Tokens ↗, 4=Tokens ↘, 5=Depth ↗, 6=Depth ↘, 7=Order ↗, 8=Order ↘, 9=UID ↗, 10=UID ↘, 11=Trigger% ↗, 12=Trigger% ↘ | 4852 |
| world_refresh | button | tip: Refresh | 4869 |
| world_info_pagination | div | container | 4870 |
| world_popup_entries_list | div | WI entries list (entry_edit_template instances) | 4872 |

#### User Settings (#user-settings-block, #user-settings-button)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| user-settings-button | div | container .drawer | 4878 |
| .fa-user-cog | button | tip: User Settings | 4880 |
| user-settings-block | div | container .drawer-content | 4882 |
| **— User Settings —** | section |  | 4887 |
| UI-language-block | div | Language: Default English | 4896 |
| ui_language_select | select | Language: — opts: Default, en=English | 4898 |
| version_display | small | container | 4903 |
| account_controls | div | Account Admin Panel Logout | 4906 |
| account_button | button | Account | 4907 |
| admin_button | button | Admin Panel | 4911 |
| logout_button | button | Logout | 4915 |
| settingsSearch | textarea | Filter User Settings by text — placeholder: "Search Settings" | 4920 |
| user-settings-block-content | div | container | 4923 |
| UI-Theme-Block | div | container | 4924 |
| UI-presets-block | div | UI Theme | 4925 |
| **— UI Theme —** | section |  | 4926 |
| ui_preset_import_button | button | tip: Import a theme file | 4929 |
| ui_preset_export_button | button | tip: Export a theme file | 4932 |
| ui-preset-delete-button | button | tip: Delete a theme | 4935 |
| ui_preset_import_file | file | Hidden file input: theme import (.json) | 4939 |
| themes | select | UI theme preset | 4942 |
| ui-preset-update-button | button | tip: Update a theme file | 4944 |
| ui-preset-save-button | button | tip: Save as a new theme | 4947 |
| avatar_style | select | Avatars: — opts: 0=Circle, 2=Square, 3=Rounded, 1=Rectangle | 4957 |
| chat_display | select | Chat Style: — opts: 0=Flat, 1=Bubbles, 2=Document | 4966 |
| media_display | select | Media Style: — opts: list=List, gallery=Gallery | 4974 |
| toastr_position | select | Notifications: — opts: toast-top-left=Top Left, toast-top-center=Top Center, toast-top-right=Top Right, toast-bottom-left=Bottom Left, toast-bottom-center=Bottom Center, toast-bottom-right=Bottom Right | 4981 |
| **— Theme Colors —** | section | tip: Specify colors for your theme. | 4992 |
| color-picker-block | div | container | 4997 |
| main-text-color-picker | color-picker | Main Text | 4999 |
| italics-color-picker | color-picker | Italics Text | 5003 |
| underline-color-picker | color-picker | Underlined Text | 5007 |
| quote-color-picker | color-picker | Quote Text | 5011 |
| shadow-color-picker | color-picker | Text Shadow | 5015 |
| chat-tint-color-picker | color-picker | Chat Background | 5019 |
| blur-tint-color-picker | color-picker | UI Background | 5023 |
| border-color-picker | color-picker | UI Border | 5027 |
| user-mes-blur-tint-color-picker | color-picker | User Message | 5031 |
| bot-mes-blur-tint-color-picker | color-picker | AI Message | 5035 |
| chat_width_slider / chat_width_slider_counter | range+number | Chat Width — [min=25 max=100 step=1] | 5052 |
| font_scale / font_scale_counter | range+number | Font Scale — [min=0.5 max=1.5 step=0.01] | 5061 |
| blur_strength / blur_strength_counter | range+number | Blur Strength — [min=0 max=30 step=1] | 5070 |
| shadow_width / shadow_width_counter | range+number | Shadow Width — [min=0 max=5 step=1] | 5079 |
| reduced_motion | checkbox | Reduced Motion | 5088 |
| fast_ui_mode | checkbox | No Blur Effect | 5092 |
| noShadowsmode | checkbox | No Text Shadows | 5096 |
| waifuMode | checkbox | Visual Novel Mode | 5100 |
| expandMessageActions | checkbox | Expand Message Actions | 5104 |
| enableZenSliders | checkbox | Zen Sliders | 5108 |
| enableLabMode | checkbox | Mad Lab Mode | 5112 |
| messageTimerEnabled | checkbox | Message Timer | 5116 |
| messageTimestampsEnabled | checkbox | Chat Timestamps | 5120 |
| messageModelIconEnabled | checkbox | Model Icons | 5124 |
| mesIDDisplayEnabled | checkbox | Message IDs | 5128 |
| hideChatAvatarsEnabled | checkbox | Hide Chat Avatars | 5132 |
| messageTokensEnabled | checkbox | Message Token Count | 5136 |
| compact_input_area | checkbox | Compact Input Area | 5140 |
| show_swipe_num_all_messages | checkbox | Swipe # for All Messages | 5144 |
| hotswapEnabled | checkbox | Characters Hotswap | 5148 |
| zoomed_avatar_magnification | checkbox | Avatar Hover Magnification — tip: Enables a magnification effect on hover when you display the zoomed avatar after clicking an avatar's image in chat. | 5152 |
| bogus_folders | checkbox | Tags as Folders — tip: Recent change: Tags must be marked as folders in the Tag Management menu to appear as such. Click here to bring it up. | 5157 |
| click_to_edit | checkbox | Click to Edit | 5162 |
| UI-Customization | div | container | 5168 |
| **— Character Handling —** | section |  | 5170 |
| aux_field | select | Char List Subheader — opts: character_version=Character Version, creator=Created by | 5175 |
| tag_import_setting | select | Import Card Tags — opts: 1=Ask, 2=None, 3=All, 4=Existing | 5182 |
| fuzzy_search_checkbox | checkbox | Advanced Character Search | 5190 |
| prefer_character_prompt | checkbox | Prefer Char. Prompt | 5194 |
| prefer_character_jailbreak | checkbox | Prefer Char. Instructions | 5198 |
| never_resize_avatars | checkbox | Never resize avatars | 5202 |
| background_thumbnails_animation | checkbox | Animated background thumbnails | 5206 |
| show_card_avatar_urls | checkbox | Show avatar filenames | 5210 |
| spoiler_free_mode | checkbox | Spoiler Free Mode | 5214 |
| **— Miscellaneous —** | section |  | 5221 |
| reload_chat | button | Reload Chat — tip: Reload and redraw the currently open chat. | 5223 |
| debug_menu | button | Debug Menu | 5226 |
| data_maid_button | button | Clean-Up — tip: Find and delete backups, unused chats, files, images, etc. | 5229 |
| smooth_streaming_control | label | Smooth Streaming | 5233 |
| smooth_streaming | checkbox | Smooth Streaming | 5234 |
| smooth_streaming_no_think_control | label | Exclude 'Thinking...' — tip: Bypass smooth streaming in reasoning blocks. | 5241 |
| smooth_streaming_no_think | checkbox | Exclude 'Thinking...' | 5242 |
| smooth_streaming_speed_control | div | Slow Fast | 5247 |
| smooth_streaming_speed | range | Smooth streaming speed (Slow..Fast) — [min=0 max=100 step=10] | 5248 |
| stream_fade_in | checkbox | Stream Fade-In — tip: Experimental feature. May not work for all backends. | 5256 |
| play_message_sound | checkbox | Message Sound | 5262 |
| audio_message_sound | audio | container | 5263 |
| play_sound_unfocused | checkbox | Background Sound Only | 5269 |
| relaxed_api_urls | checkbox | Relaxed API URLs | 5273 |
| world_import_dialog | checkbox | Lorebook Import Dialog | 5277 |
| enable_auto_select_input | checkbox | Auto-select Input Text | 5281 |
| enable_md_hotkeys | checkbox | Markdown Hotkeys | 5285 |
| restore_user_input | checkbox | Restore User Input | 5292 |
| movingUIModeCheckBlock | label | MovingUI — tip: Allow repositioning certain UI elements by dragging them. PC only, no effect on mobile. | 5296 |
| movingUImode | checkbox | MovingUI | 5297 |
| movingUIreset | button | Reset — tip: Reset MovingUI panel sizes/locations. | 5303 |
| MovingUI-presets-block | div | MovingUI Preset: | 5308 |
| movingUIPresets | select | MovingUI Preset: | 5313 |
| movingui-preset-save-button | button | tip: Save changes to a new MovingUI preset file. | 5317 |
| CustomCSS-block | div | Custom CSS | 5320 |
| **— Custom CSS —** | section | tip: Apply a custom CSS style to all of the ST GUI. | 5321 |
| CustomCSS-textAreaBlock | div | container | 5325 |
| customCSS | textarea | Custom CSS | 5326 |
| power-user-options-block | div | container | 5331 |
| power-user-option-checkboxes | div | container | 5332 |
| **— Chat/Message Handling —** | section |  | 5335 |
| chat_truncation / chat_truncation_counter | range+number | # Msg. to Load — [min=0 max=1000 step=5] | 5343 |
| streaming_fps / streaming_fps_counter | range+number | Streaming FPS — [min=5 max=100 step=5] | 5353 |
| examples-behavior-block | div | container | 5357 |
| example_messages_behavior | select | Example Messages Behavior: — opts: normal=Gradual push-out, keep=Always include examples, strip=Never include examples | 5363 |
| image_overswipe | select | Image Swipe Behavior: — opts: generate=Generate new, rollover=Roll over | 5375 |
| send_on_enter | select | Enter to Send: — opts: -1=Disabled, 0=Automatic (PC), 1=Enabled | 5384 |
| continue_on_send | checkbox | "Send" to Continue | 5391 |
| quick_continue | checkbox | Quick "Continue" button | 5397 |
| quick_impersonate | checkbox | Quick "Impersonate" button | 5403 |
| swipes-checkbox | checkbox | Swipes | 5410 |
| gestures-checkbox | checkbox | Gestures | 5414 |
| auto-load-chat-checkbox | checkbox | Auto-load Last Chat | 5420 |
| auto_scroll_chat_to_bottom | checkbox | Auto-scroll Chat | 5424 |
| auto_save_msg_edits | checkbox | Auto-save Message Edits | 5428 |
| confirm_message_delete | checkbox | Confirm message deletion | 5432 |
| auto_fix_generated_markdown | checkbox | Auto-fix Markdown | 5436 |
| forbid_external_media | checkbox | Forbid External Media | 5440 |
| allow_name2_display | checkbox | Show {{char}}: in responses | 5444 |
| allow_name1_display | checkbox | Show {{user}}: in responses | 5448 |
| encode_tags | checkbox | Show <tags> in responses | 5452 |
| experimental_macro_engine | checkbox | Experimental Macro Engine — tip: Experimental feature. Currently in development to test. | 5456 |
| disable_group_trimming | checkbox | Relax message trim in Groups | 5461 |
| console_log_prompts | checkbox | Log prompts to console | 5465 |
| request_token_probabilities | checkbox | Request token probabilities | 5469 |
| show_group_chat_queue | checkbox | Show group chat queue | 5473 |
| pin_styles | checkbox | Pin greeting message styles | 5477 |
| **— Auto-swipe —** | section | tip: Automatically reject and re-generate AI message based on configurable criteria. | 5481 |
| auto_swipe | checkbox | Enabled | 5487 |
| auto_swipe_minimum_length | number | Minimum generated message length — tip: If the generated message is shorter than these many characters, trigger an auto-swipe. — [min=0 step=1] | 5491 |
| auto_swipe_blacklist | textarea | Blacklisted words (comma-separated) — placeholder: "words you don't want generated separated by comma ','" | 5494 |
| auto_swipe_blacklist_threshold | number | Blacklisted word count to swipe — tip: Minimum number of blacklisted words detected to trigger an auto-swipe. — [min=0 step=1] | 5496 |
| **— Auto-Continue —** | section | tip: Automatically 'continue' a response if the model stopped before reaching a certain amount of tokens. | 5501 |
| auto_continue_enabled | checkbox | Enabled | 5508 |
| auto_continue_allow_chat_completions | checkbox | Allow for Chat Completion APIs | 5514 |
| auto_continue_target_length | number | Target length (tokens) — [min=0 max=1024] | 5523 |
| **— AutoComplete Settings —** | section | tip: Options for the various autocomplete input boxes. | 5532 |
| stscript_autocomplete_state | select | Visibility — opts: 0=Don't show, 1=Input length > 1, 2=Always show | 5541 |
| stscript_autocomplete_autoHide | checkbox | Automatically hide details | 5548 |
| stscript_autocomplete_showInAllMacroFields | checkbox | Show in all macro fields | 5554 |
| stscript_matching | select | Matching — opts: strict=Starts with, includes=Includes, fuzzy=Fuzzy | 5564 |
| stscript_autocomplete_style | select | Style — opts: theme=Follow Theme, dark=Dark, light=Light | 5575 |
| stscript_autocomplete_select | select | Autocomplete keyboard select key — opts: 3=Select with Tab or Enter, 1=Select with Tab, 2=Select with Enter | 5588 |
| stscript_autocomplete_font_scale / stscript_autocomplete_font_scale_counter | range+number | Font Scale — [min=0.5 max=2 step=0.01] | 5596 |
| stscript_autocomplete_width_left | range | Autocomplete list width (left) — [min=0 max=2 step=1] | 5605 |
| stscript_autocomplete_width_left_values | datalist | container | 5606 |
| stscript_autocomplete_width_right | range | Autocomplete details width (right) — [min=0 max=2 step=1] | 5613 |
| stscript_autocomplete_width_right_values | datalist | container | 5614 |
| **— STscript Settings —** | section |  | 5625 |
| stscript_parser_flag_strict_escaping | checkbox | STRICT_ESCAPING | 5629 |
| stscript_parser_flag_replace_getvar | checkbox | REPLACE_GETVAR | 5638 |

#### Backgrounds (#Backgrounds, #backgrounds-button)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| backgrounds-button | div | container .drawer | 5653 |
| backgrounds-drawer-toggle | div | tip: Change Background Image | 5654 |
| Backgrounds | div | container .drawer-content | 5657 |
| bg-header-fixed | div | container | 5658 |
| **— Backgrounds —** | section |  | 5660 |
| background_fitting | select | Backgrounds — tip: Background Fitting — opts: classic=Classic, cover=Cover, contain=Contain, stretch=Stretch, center=Center | 5664 |
| auto_background | button | Auto-select — tip: Automatically select a background based on the chat context. | 5671 |
| bg_add_folder_button | button | New Folder | 5675 |
| add_background_button_top | label | Add Background — tip: Add a new background | 5679 |
| bg-filter | search | Filter backgrounds by name — placeholder: "Search..." | 5685 |
| bg-sort | select | Background sort order — tip: Sort backgrounds — opts: az=A-Z, za=Z-A, newest=Newest, oldest=Oldest | 5686 |
| bg_tabs | div | container .heading-container-with-controls | 5694 |
| bg_group_add_to_folder_button | button | Add to Folder — tip: Add selected backgrounds to folder | 5703 |
| bg_selection_mode_button | button | tip: Select backgrounds for bulk folder actions | 5707 |
| bg_group_select_count | span | container | 5709 |
| bg_thumb_zoom_out | button | tip: Make thumbnails smaller | 5711 |
| bg_thumb_zoom_in | button | tip: Make thumbnails larger | 5714 |
| bg_global_tab | div | Back Remove from Folder | 5719 |
| bg_folder_breadcrumb | div | Back Remove from Folder | 5720 |
| bg_back_to_folders | button | Back | 5721 |
| bg_current_folder_name | span | container .bg_current_folder_name | 5725 |
| bg_folder_remove_selected_button | button | Remove from Folder — tip: Remove selected backgrounds from this folder | 5727 |
| bg_folder_grid | div | container .bg_list | 5732 |
| bg_menu_content | div | System backgrounds grid (background_template) | 5733 |
| bg_chat_tab | div | container .bg_tab_panel | 5735 |
| bg_custom_content | div | Chat-specific backgrounds grid | 5736 |
| bg_chat_hint | div | container | 5737 |
| form_bg_upload | form | container | 5742 |
| add_bg_button | file | Add Background | 5743 |
| bg-scroll-top | button | tip: Scroll backgrounds to top | 5745 |

#### Extensions (#rm_extensions_block, #extensions-settings-button)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| extensions-settings-button | div | container .drawer | 5750 |
| .fa-cubes | button | tip: Extensions | 5752 |
| rm_extensions_block | div | container .drawer-content | 5754 |
| **— Extensions —** | section |  | 5757 |
| extensions_notify_updates | checkbox | Notify on extension updates | 5761 |
| extensions_details | button | Manage extensions | 5764 |
| third_party_extension_button | button | Install extension — tip: Import Extension From Git Repo | 5768 |
| extensions_settings | div | Extension settings mount (left column) | 5773 |
| assets_container | div | container .extension_container | 5774 |
| typing_indicator_container | div | container .extension_container | 5775 |
| expressions_container | div | container .extension_container | 5776 |
| sd_container | div | container .extension_container | 5777 |
| tts_container | div | container .extension_container | 5778 |
| rvc_container | div | container .extension_container | 5779 |
| stt_container | div | container .extension_container | 5780 |
| audio_container | div | container .extension_container | 5781 |
| silence_container | div | container .extension_container | 5782 |
| objective_container | div | container .extension_container | 5783 |
| blip_container | div | container .extension_container | 5784 |
| live2d_container | div | container .extension_container | 5785 |
| vrm_container | div | container .extension_container | 5786 |
| timelines_container | div | container .extension_container | 5787 |
| webllm_container | div | container .extension_container | 5788 |
| rss_container | div | container .extension_container | 5789 |
| extensions_settings2 | div | Extension settings mount (right column) | 5791 |
| websearch_container | div | container .extension_container | 5792 |
| emulatorjs_container | div | container .extension_container | 5793 |
| qr_container | div | container .extension_container | 5794 |
| translation_container | div | container .extension_container | 5795 |
| caption_container | div | container .extension_container | 5796 |
| idle_container | div | container .extension_container | 5797 |
| summarize_container | div | container .extension_container | 5798 |
| hypebot_container | div | container .extension_container | 5799 |
| regex_container | div | container .extension_container | 5800 |
| vectors_container | div | container .extension_container | 5801 |
| randomizer_container | div | container .extension_container | 5802 |
| chromadb_container | div | container .extension_container | 5803 |
| message_limit_container | div | container .extension_container | 5804 |
| injects_container | div | container .extension_container | 5805 |
| accuweather_container | div | container .extension_container | 5806 |
| dice_container | div | container .extension_container | 5807 |
| **— (DEPRECATED) Extras API: —** | section |  | 5811 |
| extensions_status | div | Extras connection status | 5816 |
| extensions_autoconnect | checkbox | Auto-connect | 5818 |
| extensions_url | text | Extras API URL (deprecated) — placeholder: "Extras API URL" | 5824 |
| extensions_api_key | text | Extras API key (deprecated) — placeholder: "Extras API key (optional)" | 5825 |
| extensions_connect | button | Connect | 5827 |

#### Persona Management (#PersonaManagement, #persona-management-button)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| persona-management-button | div | container .drawer | 5833 |
| .fa-face-smile | button | tip: Persona Management | 5835 |
| PersonaManagement | div | container .drawer-content | 5837 |
| **— Persona Management —** | section |  | 5841 |
| .user_stats_button | button | Usage Stats — tip: Click for stats! | 5849 |
| personas_backup | button | Backup — tip: Backup your personas to a file | 5853 |
| personas_restore | button | Restore — tip: Restore your personas from a file | 5857 |
| personas_restore_input | file | Hidden file input: personas backup restore | 5861 |
| persona-management-block | div | container | 5864 |
| create_dummy_persona | button | Create — tip: Create a dummy persona | 5867 |
| persona_search_bar | search | Search personas — placeholder: "Search..." | 5871 |
| persona_sort_order | select | Persona sort order — opts: search=Search, asc=A-Z, desc=Z-A | 5872 |
| persona_pagination_container | div | container | 5877 |
| persona_grid_toggle | button | tip: Toggle grid view | 5878 |
| user_avatar_block | div | Persona list (user_avatar_template instances) | 5880 |
| form_upload_avatar | form | container | 5883 |
| avatar_upload_file | file | Hidden file input: persona avatar upload | 5884 |
| avatar_upload_overwrite | hidden | Hidden: persona avatar to overwrite | 5885 |
| **— Current Persona —** | section |  | 5890 |
| persona_controls | div | [Persona Name] More... Link to Persona Lorebook | 5892 |
| your_name | h5 | Current persona name | 5893 |
| persona_rename_button | button | tip: Rename Persona | 5895 |
| sync_name_button | button | tip: Click to set user name for all messages | 5896 |
| persona_lore_button | button | tip: Persona Lore Click to load Shift/Alt-click or long-press to open 'Link to Persona Lorebook' popup | 5897 |
| persona_set_image_button | button | tip: Change Persona Image | 5899 |
| persona_duplicate_button | button | tip: Duplicate Persona | 5900 |
| persona_delete_button | button | tip: Delete Persona | 5901 |
| persona-management-dropdown | select | "More..." persona actions — opts: default=More..., Link to Persona Lorebook | 5904 |
| persona_lorebook_link | option | Link to Persona Lorebook | 5906 |
| **— Persona Description —** | section |  | 5911 |
| persona_description | textarea | Persona Description — placeholder: "Example: [{{user}} is a 28-year-old Romanian cat girl.]" | 5915 |
| **— Position —** | section |  | 5918 |
| persona_description_token_count | span | 0 | 5920 |
| persona_description_position | select | Persona description injection position — opts: 9=None (disabled), 0=In Story String / Prompt Manager, 2=Top of Author's Note, 3=Bottom of Author's Note, 4=In-chat @ Depth | 5925 |
| persona_depth_position_settings | div | Depth: Role: System User Assistant | 5932 |
| persona_depth_value | number | Persona description depth — [min=0 max=9999 step=1] | 5935 |
| persona_depth_role | select | Persona description role — opts: 0=System, 1=User, 2=Assistant | 5939 |
| **— Connections —** | section |  | 5948 |
| persona_connections_buttons | div | Default Character Chat | 5949 |
| lock_persona_default | button | Default — tip: Click to select this as default persona for the new chats. Click again to remove it. | 5950 |
| lock_persona_to_char | button | Character — tip: Click to lock your selected persona to the current character. Click again to remove the lock. | 5954 |
| lock_user_name | button | Chat — tip: Click to lock your selected persona to the current chat. Click again to remove the lock. | 5958 |
| persona_connections_info_block | div | container | 5963 |
| persona_connections_list | div | container .avatars_inline | 5964 |
| **— Global Settings —** | section |  | 5969 |
| persona_show_notifications | checkbox | Show notifications on switching personas | 5973 |
| persona_allow_multi_connections | checkbox | Allow multiple persona connections per character | 5981 |
| persona_auto_lock | checkbox | Auto-lock a chosen persona to the chat | 5989 |

#### Character Management — header, pin, tabs, selected-char token info (#right-nav-panel, #rightNavHolder)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| rightNavHolder | div | container .drawer | 6001 |
| unimportantYes | div | container .drawer-toggle | 6002 |
| rightNavDrawerIcon | button | tip: Character Management | 6003 |
| right-nav-panel | nav | container .drawer-content | 6006 |
| right-nav-panelheader | div | container .drag-grabber | 6007 |
| CharListButtonAndHotSwaps | div | container | 6009 |
| rm_button_panel_pin_div | div | tip: Locked = Character Management panel will stay open | 6011 |
| rm_button_panel_pin | checkbox | Lock right panel open | 6012 |
| .unchecked | button | pin icon (panel unlocked state) | 6014 |
| .checked | button | pin icon (panel locked state) | 6015 |
| rm_button_characters | button | tip: Select/Create Characters | 6018 |
| HotSwapWrapper | div | container | 6020 |
| rm_PinAndTabs | div | Calculating... Tokens ( Permanent ) | 6027 |
| right-nav-panel-tabs | div | Calculating... Tokens ( Permanent ) | 6028 |
| rm_button_selected_ch | div | container | 6029 |
| result_info | div | Calculating... Tokens ( Permanent ) | 6032 |
| result_info_text | div | Calculating... Tokens ( Permanent ) — tip: Token counts may be inaccurate and provided just for reference. | 6033 |
| result_info_total_tokens | strong | Calculating... — tip: Total tokens | 6035 |
| result_info_permanent_tokens | span | container | 6039 |
| chartokenwarning | a | tip: About Token 'Limits' | 6043 |
| .rm_stats_button | button | tip: Click for stats! | 6044 |
| hideCharPanelAvatarButton | button | tip: Toggle character info panel | 6045 |

##### Character create/edit form (#rm_ch_create_block / #form_create)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| rm_ch_create_block | div | container .right_menu | 6052 |
| form_create | form | container | 6053 |
| avatar-and-name-block | div | container | 6054 |
| name_div | div | Tokens: counting... | 6055 |
| character_name_pole | text | placeholder: "Name this character" | 6056 |
| avatar_div | div | container .avatar_div | 6062 |
| avatar_div_div | label | tip: Click to select a new avatar for this character | 6063 |
| avatar_load_preview | img | Character avatar preview | 6064 |
| add_avatar_button | file | Avatar file input | 6065 |
| avatar_controls | div | container | 6067 |
| rm_button_back | button | Back to character list | 6069 |
| favorite_button | button | tip: Add to Favorites | 6071 |
| fav_checkbox | hidden | Favorite flag | 6072 |
| advanced_div | button | tip: Advanced Definitions | 6073 |
| world_button | button | tip: Character Lore Click to load Shift/Alt-click or long-press to open 'Link to World Info' popup | 6074 |
| .chat_lorebook_button | button | tip: Chat Lore Click to load Shift/Alt-click or long-press to open 'Link to Chat Lorebook' popup | 6075 |
| char_connections_button | button | tip: Connected Personas | 6076 |
| export_button | button | tip: Export and Download | 6077 |
| dupe_button | button | tip: Duplicate Character | 6080 |
| create_button_label | label | tip: Create Character | 6081 |
| create_button | submit | Create/Save character submit | 6082 |
| delete_button | button | tip: Delete Character | 6084 |
| char-management-dropdown | select | "More..." character actions menu — opts: default=More..., Link to World Info, Import Card Lore, Character Settings Overrides, Convert to Persona, Rename, Link to Source, Replace / Update, Import Tags, Set / Unset as Welcome Page Assistant | 6087 |
| set_character_world | option | Link to World Info | 6089 |
| import_character_info | option | Import Card Lore | 6092 |
| set_chat_character_settings | option | Character Settings Overrides | 6095 |
| convert_to_persona | option | Convert to Persona | 6098 |
| renameCharButton | option | Rename | 6101 |
| character_source | option | Link to Source | 6104 |
| replace_update | option | Replace / Update | 6107 |
| import_tags | option | Import Tags | 6110 |
| set_as_assistant | option | Set / Unset as Welcome Page Assistant | 6113 |
| tags_div | div | container | 6129 |
| tagInput | text | placeholder: "Search / Create tags" | 6131 |
| .tags_view | button | tip: View all tags | 6132 |
| tagList | div | Character tags (tag_template) | 6134 |
| spoiler_free_desc | div | Creator's Notes No Creator's Notes provided. | 6138 |
| **— Creator's Notes —** | section |  | 6139 |
| creators_notes_div | div | Creator's Notes | 6140 |
| creators_note_styles_button | button | tip: Allow / Forbid the use of global styles for this character. | 6142 |
| spoiler_free_desc_button | button | tip: Show / Hide Description and First Message | 6143 |
| creator_notes_spoiler | div | container | 6150 |
| creator_notes_empty | div | No Creator's Notes provided. | 6151 |
| creators_note_desc_hidden | small | Character details are hidden. | 6156 |
| descriptionWrapper | div | Description Ext. Media Tokens: counting... | 6157 |
| description_div | div | Description Ext. Media | 6158 |
| character_open_media_overrides | button | Ext. Media — tip: Click to allow/forbid the use of external media for this character. | 6166 |
| character_media_allowed_icon | i | container | 6167 |
| character_media_forbidden_icon | i | container | 6168 |
| description_textarea | textarea | Character Description (main card field) | 6174 |
| firstMessageWrapper | div | First message Alt. Greetings Tokens: counting... | 6179 |
| first_message_div | div | First message Alt. Greetings | 6180 |
| .open_alternate_greetings | button | Alt. Greetings — tip: Click to set additional greeting messages | 6188 |
| firstmessage_textarea | textarea | First message / greeting | 6194 |
| hidden-divs | div | container | 6200 |
| character_json_data | hidden | Hidden: char JSON | 6201 |
| avatar_url_pole | hidden | Hidden: avatar file name | 6202 |
| selected_chat_pole | hidden | Hidden: selected chat | 6203 |
| create_date_pole | hidden | Hidden: create date | 6204 |
| last_mes_pole | hidden | Hidden: last message date | 6205 |
| character_world | hidden | Hidden: linked WI name | 6206 |

##### Group chat panel (#rm_group_chats_block)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| rm_group_chats_block | div | container .right_menu | 6211 |
| groupControlsToggle | button | Group Controls | 6213 |
| group-metadata-controls | div | container | 6223 |
| rm_group_chat_name | text | Group chat name — placeholder: "Chat Name (Optional)" | 6225 |
| .chat_lorebook_button | button | tip: Chat Lore Click to load Shift/Alt-click or long-press to open 'Link to Chat Lorebook' popup | 6226 |
| group-chat-lorebook-dropdown | select | "More..." group actions — opts: default=More..., Link to Chat Lorebook | 6229 |
| group_chat_lorebook_link | option | Link to Chat Lorebook | 6231 |
| group_tags_div | div | container | 6234 |
| groupTagInput | text | placeholder: "Search / Create tags" | 6236 |
| .tags_view | button | tip: View all tags | 6237 |
| groupTagList | div | Group tags | 6239 |
| rm_group_top_bar | div | container | 6241 |
| .add_avatar | label | tip: Click to select a new avatar for this group | 6243 |
| group_avatar_preview | div | container | 6244 |
| group_avatar_button | file | Click to select a new avatar for this group | 6249 |
| rm_group_buttons | div | container | 6252 |
| rm_group_activation_strategy | select | Group reply strategy — opts: 2=Manual, 0=Natural order, 1=List order, 3=Pooled order | 6257 |
| rm_group_generation_mode | select | Group generation handling mode — opts: 0=Swap character cards, 1=Join character cards (exclude muted), 2=Join character cards (include muted) | 6268 |
| rm_group_generation_mode_join_prefix | textarea | Join Prefix — placeholder: "—" — tip: When 'Join character cards' is selected, all respective fields of the characters are being joined together. This means… | 6280 |
| rm_group_generation_mode_join_suffix | textarea | Join Suffix — placeholder: "—" — tip: When 'Join character cards' is selected, all respective fields of the characters are being joined together. This means… | 6288 |
| GroupFavDelOkBack | div | Allow self responses Auto Mode Hide Muted Member Sprites | 6291 |
| rm_button_back_from_group | button | Back to character list | 6292 |
| rm_group_scenario | button | tip: Set group chat character settings overrides | 6293 |
| group_favorite_button | button | tip: Add to Favorites | 6294 |
| rm_group_fav | hidden | Hidden: group favorite flag | 6295 |
| group_open_media_overrides | button | tip: Click to allow/forbid the use of external media for this group. | 6296 |
| group_media_allowed_icon | i | container | 6297 |
| group_media_forbidden_icon | i | container | 6298 |
| rm_group_submit | button | tip: Create | 6300 |
| rm_group_restore_avatar | button | tip: Restore collage avatar | 6301 |
| rm_group_delete | button | tip: Delete | 6302 |
| rm_group_allow_self_responses | checkbox | Allow self responses | 6305 |
| rm_group_automode_label | label | Auto Mode | 6308 |
| rm_group_automode | checkbox | Auto Mode | 6309 |
| rm_group_automode_delay | number | Auto Mode delay (s) — tip: Auto Mode delay — [min=1 max=999 step=1] | 6311 |
| rm_group_hidemutedsprites_label | label | Hide Muted Member Sprites | 6313 |
| rm_group_hidemutedsprites | checkbox | Hide Muted Member Sprites | 6314 |
| groupCurrentMemberListToggle | button | Current Members | 6324 |
| groupCurrentMemberPopoutButton | button | Pop out current-members list | 6325 |
| currentGroupMembers | div | container | 6329 |
| rm_group_members_header | div | container | 6330 |
| rm_group_members_filter | search | Filter current members — placeholder: "Search..." | 6331 |
| rm_group_members_pagination | div | container .rm_group_members_pagination | 6336 |
| rm_group_members | div | Current group members list (group_member_template) | 6337 |
| groupAddMemberListToggle | button | Add Members | 6342 |
| unaddedCharList | div | container | 6347 |
| rm_group_add_members_header | div | container | 6348 |
| rm_group_filter | search | Filter addable characters — placeholder: "Search..." | 6349 |
| rm_group_add_members_pagination | div | container .group_pagination | 6354 |
| rm_group_add_members | div | Addable characters list (group_member_template) | 6355 |

##### Character import (hidden forms, #rm_character_import)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| rm_character_import | div | container .right_menu | 6360 |
| form_import | form | container | 6361 |
| character_import_file | file | Hidden file input: character import (png/json/charx/yaml…) | 6362 |
| character_import_file_type | text | Hidden: import file type | 6363 |
| character_replace_file | file | Hidden file input: Replace/Update character | 6365 |

##### Character list (#rm_characters_block): create/import/group/sort/search/bulk

| element id | type | purpose | index.html:line |
|---|---|---|---|
| rm_characters_block | div | container .right_menu | 6367 |
| charListFixedTop | div | container | 6368 |
| rm_button_bar | div | container | 6369 |
| rm_button_create | button | tip: Create New Character | 6370 |
| character_import_button | button | tip: Import Character from File | 6371 |
| external_import_button | button | tip: Import content from external URL | 6372 |
| rm_button_group_chats | button | tip: Create New Chat Group | 6373 |
| rm_buttons_container | div | container | 6374 |
| character_sort_order | select | tip: Characters sorting order — opts: Search, A-Z, Z-A, Newest, Oldest, Favorites, Recent, Most chats, Least chats, Most tokens, Least tokens, Random | 6377 |
| rm_button_search | button | tip: Toggle search bar | 6391 |
| form_character_search_form | div | container | 6393 |
| character_search_bar | search | Search characters — placeholder: "Search..." | 6394 |
| rm_print_characters_pagination | div | container | 6402 |
| charListGridToggle | button | tip: Toggle character grid view | 6403 |
| bulkEditButton | button | tip: Bulk edit characters Click to toggle characters Shift + Click to select/deselect a range of characters Right-click for… | 6404 |
| bulkSelectedCount | div | container .bulkEditOptionElement | 6405 |
| bulkSelectAllButton | button | tip: Bulk select all characters | 6406 |
| bulkDeleteButton | button | tip: Bulk delete characters | 6407 |
| rm_print_characters_block | div | Character/group/folder list host (character_template / group_list_template / bogus_folder_template) | 6409 |

#### Misc templates: export format, zoomed avatar, generic draggable, persona tile (user_avatar_template), popup_template (Popup class)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| NULL_SELECTOR | template | container | 6418 |
| export_format_popup | div | PNG JSON | 6419 |
| .export_format | div | PNG | 6420 |
| .export_format | div | JSON | 6421 |
| zoomed_avatar_template | div | container | 6423 |
| .zoomed_avatar | div | container | 6424 |
| .drag-grabber | div | container | 6426 |
| closeZoom | button | Close zoomed avatar | 6427 |
| .zoomed_avatar_container | div | container | 6429 |
| .zoomed_avatar_img | img | container | 6430 |
| generic_draggable_template | template | container | 6434 |
| .draggable | div | container | 6435 |
| .dragTitle | div | container | 6436 |
| .drag-grabber | div | container | 6438 |
| .dragClose | button | Close draggable | 6439 |
| user_avatar_template | div | Chat Character | 6443 |
| .avatar-container | div | Chat Character | 6444 |
| .avatar | div | container | 6445 |
| .character_select_container | div | Chat Character | 6448 |
| .character_name_block | div | container | 6449 |
| .ch_name | span | Header row: name, ghost icon, timestamp, buttons | 6450 |
| .ch_additional_info | small | container | 6451 |
| .ch_description | div | container | 6453 |
| .avatar_container_states | div | Chat Character | 6454 |
| .locked_to_chat_label | button | Chat — tip: Persona is locked to the current chat | 6455 |
| .icon | i | container | 6456 |
| .label_icon | i | container | 6457 |
| .label | div | Chat | 6458 |
| .locked_to_character_label | button | Character — tip: Persona is locked to the current character | 6460 |
| .icon | i | container | 6461 |
| .label_icon | i | container | 6462 |
| .label | div | Character | 6463 |
| popup_template | template | container | 6469 |
| .popup-body | div | Popup body | 6471 |
| .popup-content | div | text | 6472 |
| **— text —** | section |  | 6473 |
| .popup-crop-wrap | div | container | 6475 |
| .popup-crop-image | img | container | 6476 |
| .popup-input | textarea | Popup text input | 6478 |
| .popup-inputs | div | container | 6479 |
| .popup-controls | div | Popup button row (+ custom buttons) | 6480 |
| .popup-button-ok | button | OK/affirmative (label set per popup; default "Delete") | 6481 |
| .popup-button-cancel | button | Cancel | 6482 |
| .popup-button-close | button | tip: Close popup | 6485 |

#### #shadow_popup (legacy callPopup dialogue)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| shadow_popup | div | Legacy modal overlay | 6488 |
| dialogue_popup | div | Legacy dialogue box | 6489 |
| dialogue_popup_holder | div | container | 6490 |
| dialogue_popup_text | div | text | 6491 |
| **— text —** | section |  | 6492 |
| dialogue_popup_input | textarea | Legacy popup text input | 6494 |
| dialogue_popup_controls | div | Delete Cancel | 6495 |
| dialogue_popup_ok | button | OK/confirm (default label "Delete") | 6496 |
| dialogue_popup_cancel | button | Cancel | 6497 |

#### #character_popup — Advanced Definitions (opened by #advanced_div)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| character_popup | div | container | 6502 |
| character_popup_text | div | - Advanced Definitions | 6503 |
| character_popup-button-h3 | h3 | container | 6504 |
| character_cross | div | Close Advanced Definitions popup | 6508 |
| **— Prompt Overrides (For Chat Completion and Instruct Mode) —** | section |  | 6510 |
| **— Main Prompt —** | section |  | 6520 |
| system_prompt_textarea | textarea | Main Prompt — placeholder: "Any contents here will replace the default Main Prompt used for this…" | 6524 |
| **— Post-History Instructions —** | section |  | 6530 |
| post_history_instructions_textarea | textarea | Post-History Instructions — placeholder: "Any contents here will replace the default Post-History Instructions…" | 6534 |
| **— Creator's Metadata (Not sent with the AI Prompt) —** | section |  | 6543 |
| **— Created by —** | section |  | 6554 |
| creator_textarea | textarea | Created by — placeholder: "(Botmaker's name / Contact info)" | 6555 |
| **— Character Version —** | section |  | 6558 |
| character_version_textarea | textarea | Character Version — placeholder: "(If you want to track character versions)" | 6559 |
| **— Creator's Notes —** | section |  | 6564 |
| creator_notes_textarea | textarea | Creator's Notes — placeholder: "(Describe the bot, give use tips, or list the chat models it has been…" | 6568 |
| **— Tags to Embed —** | section |  | 6571 |
| tags_textarea | textarea | Tags to Embed — placeholder: "(Write a comma-separated list of tags)" | 6575 |
| personality_div | div | Personality summary Tokens: counting... | 6581 |
| **— Personality summary —** | section |  | 6582 |
| personality_textarea | textarea | Personality summary — placeholder: "(A brief description of the personality)" | 6587 |
| scenario_div | div | Scenario Tokens: counting... | 6592 |
| **— Scenario —** | section |  | 6593 |
| scenario_pole | textarea | Scenario — placeholder: "(Circumstances and context of the interaction)" | 6600 |
| depth_prompt_div | div | container | 6605 |
| **— Character's Note —** | section |  | 6607 |
| depth_prompt_prompt | textarea | Character's Note — placeholder: "(Text to be inserted in-chat @ designated depth and role)" | 6613 |
| **— @ Depth —** | section |  | 6616 |
| depth_prompt_depth | number | @ Depth — [min=0 max=9999] | 6621 |
| **— Role —** | section |  | 6622 |
| depth_prompt_role | select | Character's Note role — opts: system=System, user=User, assistant=Assistant | 6627 |
| talkativeness_div | div | container | 6637 |
| **— Talkativeness —** | section |  | 6638 |
| talkativeness_slider | range | Talkativeness How often the character speaks in group chats! — [min=0 max=1 step=0.05] | 6642 |
| mes_example_div | div | container | 6650 |
| **— Examples of dialogue —** | section |  | 6652 |
| mes_example_textarea | textarea | Examples of dialogue Important to set the character's writing style. — placeholder: "(Examples of chat dialog. Begin each example with <START> on a new li…" | 6663 |
| character_popup_ok | button | Save | 6669 |

#### #shadow_select_chat_popup — Chat History / Manage chat files

| element id | type | purpose | index.html:line |
|---|---|---|---|
| shadow_select_chat_popup | div | Chat History New Chat Import Chat | 6672 |
| select_chat_popup | div | Chat History New Chat Import Chat | 6673 |
| select_chat_import | div | container | 6675 |
| form_import_chat | form | container | 6676 |
| chat_import_file | file | Hidden file input: chat import (.jsonl/.json) | 6677 |
| chat_import_file_type | text | Save | 6678 |
| chat_import_avatar_url | text | Save | 6679 |
| chat_import_character_name | text | Save | 6680 |
| selectChatPopupHeaderText | div | Chat History | 6683 |
| ChatHistoryCharName | span | container | 6684 |
| newChatFromManageScreenButton | button | New Chat | 6687 |
| chat_import_button | button | Import Chat | 6691 |
| select_chat_search | search | Search chat files — placeholder: "Search..." | 6695 |
| select_chat_cross | div | Close chat-history popup | 6696 |
| select_chat_div | div | Past chats list (past_chat_template) | 6698 |

#### Templates: background tile, bg folder tile, character world (Link to World Info popup), past chat row, tag view row

| element id | type | purpose | index.html:line |
|---|---|---|---|
| background_template | div | container | 6701 |
| .bg_example | div | container | 6702 |
| .bg_example_img | div | container | 6703 |
| .mobile-only-menu-toggle | div | container | 6704 |
| .jg-menu | div | container | 6707 |
| .jg-lock | button | tip: Lock — action: lock | 6708 |
| .jg-unlock | button | tip: Unlock — action: unlock | 6709 |
| .jg-copy | button | tip: Copy to global backgrounds — action: copy | 6710 |
| .jg-edit | button | tip: Rename Background — action: edit | 6711 |
| .jg-delete | button | tip: Delete Background — action: delete | 6712 |
| .jg-folder | button | tip: Add to folder — action: folder | 6713 |
| .jg-set-cover | button | tip: Set as folder cover — action: set-cover | 6714 |
| .BGSampleTitle | div | container | 6716 |
| bg_folder_tile_template | div | container | 6719 |
| .bg_folder_tile | div | container | 6720 |
| .bg_folder_tile_cover | div | container | 6721 |
| .bg_folder_tile_overlay | div | container | 6722 |
| .bg_folder_tile_name | span | container | 6724 |
| .mobile-only-menu-toggle | div | container | 6726 |
| .bg_folder_tile_menu | div | container | 6729 |
| [data-action=rename-folder] | button | tip: Rename folder — action: rename-folder | 6730 |
| [data-action=delete-folder] | button | tip: Delete folder — action: delete-folder | 6731 |
| bg_new_folder_template | div | New Folder | 6735 |
| .bg_folder_tile | div | New Folder | 6736 |
| character_world_template | div | container | 6742 |
| .character_world | div | container | 6743 |
| **— Select a World Info file for : —** | section |  | 6745 |
| .character_name | span | container | 6747 |
| **— Primary Lorebook —** | section |  | 6750 |
| .character_world_info_selector | select | Select a World Info file for : Primary Lorebook A selected World Info will be bound to this charact… — opts: --- None --- | 6757 |
| **— Additional Lorebooks —** | section |  | 6762 |
| .character_extra_world_info_selector | select | Additional Lorebooks Associate one or more auxillary Lorebooks with this character. NOTE: These cho… — opts: -- World Info not found -- | 6771 |
| past_chat_template | div | container | 6777 |
| .select_chat_block_wrapper | div | container | 6778 |
| .select_chat_block | div | container | 6779 |
| select_chat_name_wrapper | div | container | 6780 |
| .select_chat_block_filename | small | container | 6782 |
| .renameChatButton | button | tip: Rename chat file | 6783 |
| .select_chat_info | div | container | 6786 |
| .chat_messages_date | small | container | 6787 |
| .chat_file_size | small | container | 6788 |
| .chat_messages_num | small | container | 6789 |
| .select_chat_actions | div | container | 6791 |
| .exportRawChatButton | button | tip: Export JSONL chat file | 6792 |
| .exportChatButton | button | tip: Download chat as plain text document | 6793 |
| .PastChat_cross | button | tip: Delete chat file | 6794 |
| .select_chat_block_mes | div | container | 6798 |
| tag_view_template | div | ☰ entries | 6802 |
| .tag_view_item | div | ☰ entries | 6803 |
| .drag-handle | div | ☰ | 6804 |
| .tag_as_folder | button | tip: Tag as folder | 6805 |
| .tag_folder_indicator | span | container | 6806 |
| .eye-toggle | button | Hide/show tag in filters | 6808 |
| .tag_view_color_picker | div | container | 6809 |
| .tag_view_color_picker | div | container | 6810 |
| .tag_view_name | div | container | 6811 |
| .tag_view_counter | div | entries | 6812 |
| .tag_view_counter_value | span | container | 6812 |
| .tag_delete | button | tip: Delete tag | 6813 |

#### #entry_edit_template — World Info entry (header row + expanded editor)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| entry_edit_template | div | container | 6816 |
| .world_entry_edit | div | container | 6817 |
| .world_entry_form_control | div | Comma separated (required) Primary Keywords ⌨️ | 6820 |
| [name=key] | select | Comma separated (required) Primary Keywords — (multi-select) | 6827 |
| [name=key] | textarea | Comma separated (required) Primary Keywords — placeholder: "Comma separated list" | 6828 |
| .switch_input_type_icon | button | ⌨️ — tip: Switch to plaintext mode | 6829 |
| .world_entry_form_control | div | Logic AND ANY AND ALL NOT ALL NOT ANY | 6833 |
| [name=entryLogicType] | select | Logic — opts: 0=AND ANY, 3=AND ALL, 1=NOT ALL, 2=NOT ANY | 6835 |
| .world_entry_form_control | div | (ignored if empty) Optional Filter ⌨️ | 6842 |
| [name=keysecondary] | select | (ignored if empty) Optional Filter — (multi-select) | 6849 |
| [name=keysecondary] | textarea | (ignored if empty) Optional Filter — placeholder: "Comma separated list (ignored if empty)" | 6850 |
| .switch_input_type_icon | button | ⌨️ — tip: Switch to plaintext mode | 6851 |
| .world_entry_form_control | div | Outlet Name — tip: Set the outlet name for this WI entry. WI entries with position 'outlet' will not be added to the prompt automatically.… | 6857 |
| [name=outletName] | text | Outlet Name — placeholder: "Outlet Name" | 6862 |
| .world_entry_form_control | div | Scan Depth | 6864 |
| [name=scanDepth] | number | Scan Depth — placeholder: "Use global setting" — [max=1000] | 6866 |
| .world_entry_form_control | div | Case-Sensitive Use global Yes No | 6868 |
| [name=caseSensitive] | select | Case-Sensitive — opts: null=Use global, true=Yes, false=No | 6870 |
| .world_entry_form_control | div | Whole Words Use global Yes No | 6876 |
| [name=matchWholeWords] | select | Whole Words — opts: null=Use global, true=Yes, false=No | 6878 |
| .world_entry_form_control | div | Group Scoring Use global Yes No | 6884 |
| [name=useGroupScoring] | select | Group Scoring — opts: null=Use global, true=Yes, false=No | 6886 |
| .world_entry_form_control | div | Automation ID — tip: Can be used to automatically activate Quick Replies | 6892 |
| [name=automationId] | text | Automation ID — placeholder: "( None )" | 6894 |
| .world_entry_form_control | div | Recursion Level — tip: Defines delay levels for recursive scans. Initially, only the first level (smallest number) will match. Once no matches… | 6896 |
| [name=delayUntilRecursionLevel] | text | Recursion Level — placeholder: "1" | 6901 |
| .world_entry_thin_controls | div | container | 6904 |
| .world_entry_form_control | div | container | 6905 |
| .mdhotkey_location | span | Content | 6910 |
| .world_entry_form_token_counter | span | counting... | 6916 |
| .world_entry_form_uid_value | span | container | 6917 |
| [name=excludeRecursion] | checkbox | Non-recursable | 6922 |
| [name=preventRecursion] | checkbox | Prevent further recursion | 6928 |
| [name=delay_until_recursion] | checkbox | Delay until recursion | 6936 |
| [name=ignoreBudget] | checkbox | Ignore budget | 6942 |
| [name=content] | textarea | Entry content (sent verbatim) — placeholder: "What this keyword should mean to the AI, sent verbatim" | 6957 |
| .world_entry_thin_controls | div | container | 6960 |
| .notes-link | a | tip: Inclusion Groups ensure only one entry from a group is activated at a time, if multiple are triggered. Supports multipl… | 6968 |
| [name=groupOverride] | checkbox | Prioritize | 6973 |
| [name=group] | text | placeholder: "Only one entry with the same label will be activated" | 6983 |
| [name=groupWeight] | number | Group Weight — placeholder: "100" — [min=1 max=999999] | 6993 |
| [name=sticky] | number | Sticky — placeholder: "Non-sticky" — [min=0 max=999999] | 7006 |
| [name=cooldown] | number | Cooldown — placeholder: "No cooldown" — [min=0 max=999999] | 7019 |
| [name=delay] | number | Delay — placeholder: "No delay" — [min=0 max=999999] | 7032 |
| [name=character_exclusion] | checkbox | Exclude | 7043 |
| [name=characterFilter] | select | Filter to Characters or Tags — opts: -- Characters not found -- | 7053 |
| [name=__invisible] | checkbox | Not a real control (layout spacer) | 7069 |
| [name=triggers] | select | Filter to Generation Triggers — opts: normal=Normal, continue=Continue, impersonate=Impersonate, swipe=Swipe, regenerate=Regenerate, quiet=Quiet | 7074 |
| .world_entry_form_horizontal | div | Selective Use Probability Add Memo | 7085 |
| .wi-enter-footer-text | div | Selective Use Probability Add Memo | 7086 |
| [name=selective] | checkbox | Selective | 7088 |
| [name=useProbability] | checkbox | Use Probability | 7092 |
| [name=addMemo] | checkbox | Add Memo | 7096 |
| .inline-drawer | div | container | 7101 |
| **— Additional Matching Sources —** | section |  | 7102 |
| .inline-drawer-icon | button | Expand/collapse chevron | 7104 |
| .inline-drawer-content | div | container | 7106 |
| [name=matchCharacterDescription] | checkbox | Character Description | 7109 |
| [name=matchCharacterPersonality] | checkbox | Character Personality | 7115 |
| [name=matchScenario] | checkbox | Scenario | 7121 |
| [name=matchPersonaDescription] | checkbox | Persona Description | 7129 |
| [name=matchCharacterDepthPrompt] | checkbox | Character's Note | 7135 |
| [name=matchCreatorNotes] | checkbox | Creator's Notes | 7141 |
| .world_entry | div | container | 7150 |
| .inline-drawer | div | container | 7152 |
| **— ☰ 🔵 🟢 🔗 Position: ↑Char ↓Char ↑EM ↓EM ↑AN ↓AN @D ⚙️ @D 👤 @D 🤖 ➡️ Outl… —** | section |  | 7153 |
| .drag-handle | span | ☰ | 7154 |
| .world_entry_thin_controls | div | container | 7155 |
| .inline-drawer-toggle | button | Expand/collapse WI entry editor | 7156 |
| .killSwitch | button | tip: Toggle entry's active state. | 7157 |
| .WIEntryTitleAndStatus | div | 🔵 🟢 🔗 | 7159 |
| [name=comment] | textarea | Entry title / memo — placeholder: "Entry Title/Memo" | 7161 |
| [name=entryStateSelector] | select | Entry status (constant/normal/vectorized) — tip: WI Entry Status: 🔵 Constant 🟢 Normal 🔗 Vectorized — opts: constant=🔵, normal=🟢, vectorized=🔗 | 7164 |
| .WIEnteryHeaderControls | div | container | 7170 |
| .world_entry_form_control | div | Position: ↑Char ↓Char ↑EM ↓EM ↑AN ↓AN @D ⚙️ @D 👤 @D 🤖 ➡️ Outlet | 7171 |
| .WIEntryHeaderTitleMobile | label | Position: | 7172 |
| [name=position] | select | Insertion position — tip: ↑Char: Before Character Definitions ↓Char: After Character Definitions ↑EM: Before Example Messages ↓EM: After Example… — opts: 0=↑Char, 1=↓Char, 5=↑EM, 6=↓EM, 2=↑AN, 3=↓AN, 4=@D ⚙️, 4=@D 👤, 4=@D 🤖, 7=➡️ Outlet | 7173 |
| .world_entry_form_control | div | Depth: | 7206 |
| .WIEntryHeaderTitleMobile | label | Depth: | 7207 |
| [name=depth] | number | Depth (for @D positions) — tip: Depth — [min=0 max=9999] | 7208 |
| .world_entry_form_control | div | Order: | 7210 |
| .WIEntryHeaderTitleMobile | label | Order: | 7211 |
| [name=order] | number | Insertion order (priority) — tip: Order — [min=0 max=9999] | 7212 |
| .world_entry_form_control | div | Trigger %: | 7214 |
| .WIEntryHeaderTitleMobile | label | Trigger %: | 7215 |
| [name=probability] | number | Trigger % — tip: Probability — [min=0 max=100] | 7216 |
| .move_entry_button | button | tip: Move/Copy Entry to Another Lorebook | 7221 |
| .duplicate_entry_button | button | tip: Duplicate world info entry | 7222 |
| .delete_entry_button | button | tip: Delete world info entry | 7223 |
| .inline-drawer-content | div | container | 7225 |

#### Templates: character list item, tag chip, logit bias rows

| element id | type | purpose | index.html:line |
|---|---|---|---|
| character_template | div | +++ | 7232 |
|  | div | +++ | 7233 |
| .avatar | div | container | 7234 |
| .character_select_container | div | +++ | 7237 |
| .character_name_block | div | +++ | 7238 |
| .ch_name | span | Header row: name, ghost icon, timestamp, buttons | 7239 |
| .ch_additional_info | small | +++ | 7240 |
| .ch_assistant | small | tip: This character will be used as a welcome page assistant. | 7241 |
| .ch_additional_info | small | container | 7244 |
| .ch_additional_info | small | container | 7245 |
| .ch_fav_icon | i | container | 7247 |
| .ch_fav | text | Hidden: favorite flag — +++ | 7248 |
| .ch_description | div | container | 7249 |
| .tags | div | container | 7250 |
| tag_template | div | container | 7254 |
|  | span | container | 7255 |
| .tag_name | span | container | 7256 |
| .tag_remove | i | container | 7257 |
| openai_logit_bias_template | div | ☰ | 7260 |
| .openai_logit_bias_form | div | ☰ | 7261 |
| .drag-handle | span | ☰ | 7262 |
| .openai_logit_bias_text | text | Token text / ids — placeholder: "Text or [token ids]" | 7263 |
| .openai_logit_bias_value | number | Bias value — [min=-100 max=100] | 7264 |
| .openai_logit_bias_remove | button | Remove bias entry | 7265 |
| logit_bias_template | div | ☰ | 7268 |
| .logit_bias_form | div | ☰ | 7269 |
| .drag-handle | span | ☰ | 7270 |
| .logit_bias_text | text | Token text / ids — placeholder: "type here..." | 7271 |
| .logit_bias_value | number | Bias value — [min=-100 max=100 step=0.01] | 7272 |
| .logit_bias_remove | button | Remove bias entry | 7273 |

#### #completion_prompt_manager_popup — Prompt Manager inspect/edit (Chat Completion)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| completion_prompt_manager_popup | div | container .drawer-content | 7276 |
| completion_prompt_manager_popup_inspect | div | Inspect Prompt List The list of prompts associated with this marker. | 7277 |
| **— Inspect —** | section |  | 7278 |
| completion_prompt_manager_popup_close_button | a | tip: close | 7286 |
| completion_prompt_manager_popup_entry_form_inspect_list | div | container | 7289 |
| completion_prompt_manager_popup_edit | div | container | 7294 |
| **— Edit —** | section |  | 7295 |
| completion_prompt_manager_popup_entry_form_name | text | Name | 7303 |
| completion_prompt_manager_popup_entry_form_role | select | Role — opts: system=System, user=User, assistant=AI Assistant | 7310 |
| completion_prompt_manager_popup_entry_form_injection_trigger | select | Triggers — opts: normal=Normal, continue=Continue, impersonate=Impersonate, swipe=Swipe, regenerate=Regenerate, quiet=Quiet | 7321 |
| completion_prompt_manager_popup_entry_form_injection_position | select | Position — opts: 0=Relative, 1=In-chat | 7339 |
| completion_prompt_manager_depth_block | div | Depth 0 = after the last message, 1 = before the last message, etc. | 7345 |
| completion_prompt_manager_popup_entry_form_injection_depth | number | Depth — [min=0 max=9999] | 7349 |
| completion_prompt_manager_order_block | div | container .completion_prompt_manager_popup_entry_form_control | 7352 |
| completion_prompt_manager_popup_entry_form_injection_order | number | Order — tip: Prompt injections from other sources (World Info, Author's Note, etc.) always have a default order of 100. — [min=0 max=9999] | 7357 |
| completion_prompt_manager_forbid_overrides_block | div | Forbid Overrides | 7368 |
| completion_prompt_manager_popup_entry_form_forbid_overrides | checkbox | Forbid Overrides | 7370 |
| completion_prompt_manager_popup_entry_source_block | div | Source: | 7375 |
| completion_prompt_manager_popup_entry_source | span | container | 7378 |
| completion_prompt_manager_popup_entry_form_prompt | textarea | Prompt List — placeholder: "The prompt to be sent." | 7380 |
| completion_prompt_manager_popup_entry_form_close | a | tip: Close | 7383 |
| completion_prompt_manager_popup_entry_form_reset | a | tip: Reset | 7384 |
| completion_prompt_manager_popup_entry_form_save | a | tip: Save | 7385 |

#### #message_template — chat message (mes_buttons, extraMesButtons, edit buttons, reasoning, swipe arrows)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| message_template | div | Template root cloned per chat message into #chat | 7391 |
| .mes | div | Message root; attrs mesid, ch_name, is_user, is_system, bookmark_link | 7392 |
| .for_checkbox | div | container | 7393 |
| .del_checkbox | checkbox | Per-message select checkbox (Delete messages mode) | 7393 |
| .mesAvatarWrapper | div | Avatar column (avatar, id, timer, tokens) | 7394 |
| .avatar | div | container | 7395 |
| .mesIDDisplay | div | Message #id (User Settings > Message IDs) | 7398 |
| .mes_timer | div | Generation time (Message Timer) | 7399 |
| .tokenCounterDisplay | div | Message token count (Message Token Count) | 7400 |
| .swipe_left | button | Swipe left: show previous swipe (alternate reply) | 7402 |
| .mes_block | div | Message body column | 7403 |
| .ch_name | div | Header row: name, ghost icon, timestamp, buttons | 7404 |
| .name_text | span | Sender name | 7407 |
| .mes_ghost | button | tip: This message is invisible for the AI | 7408 |
| .timestamp | small | Message timestamp (Chat Timestamps) | 7409 |
| .mes_buttons | div | Per-message action bar (hover/tap) | 7412 |
| .extraMesButtonsHint | button | tip: Message Actions | 7413 |
| .extraMesButtons | div | Collapsed extra actions (expanded via ellipsis or Expand Message Actions setting) | 7414 |
| .mes_translate | button | tip: Translate message | 7415 |
| .sd_message_gen | button | tip: Generate Image | 7416 |
| .mes_narrate | button | tip: Narrate | 7417 |
| .mes_prompt | button | tip: Prompt | 7418 |
| .mes_hide | button | tip: Exclude message from prompts | 7419 |
| .mes_unhide | button | tip: Include message in prompts | 7420 |
| .mes_media_gallery | button | tip: Toggle media display style | 7421 |
| .mes_media_list | button | tip: Toggle media display style | 7422 |
| .mes_embed | button | tip: Embed file or image | 7423 |
| .mes_swipe_picker | button | tip: Jump to swipe history | 7424 |
| .mes_create_bookmark | button | tip: Create checkpoint | 7425 |
| .mes_create_branch | button | tip: Create branch | 7426 |
| .mes_copy | button | tip: Copy | 7427 |
| .mes_bookmark | button | tip: Click to open checkpoint chat Shift+Click to replace the existing checkpoint with a new one | 7429 |
| .mes_edit | button | tip: Edit | 7430 |
| .mes_edit_buttons | button | Edit-mode button bar (replaces .mes_buttons while editing) | 7432 |
| .mes_edit_done | button | tip: Confirm | 7433 |
| .mes_edit_copy | button | tip: Copy this message | 7434 |
| .mes_edit_add_reasoning | button | tip: Add a reasoning block | 7435 |
| .mes_edit_delete | button | tip: Delete this message | 7436 |
| .mes_edit_up | button | tip: Move message up | 7437 |
| .mes_edit_down | button | tip: Move message down | 7438 |
| .mes_edit_cancel | button | tip: Cancel | 7439 |
| .mes_reasoning_details | details | Collapsible reasoning block (<details>) | 7442 |
| .mes_reasoning_summary | summary | Reasoning header "Thought for …" + actions | 7443 |
| .mes_reasoning_header_block | div | Thought for some time | 7444 |
| .mes_reasoning_header | div | Thought for some time | 7445 |
| .mes_reasoning_header_title | span | Thought for some time | 7446 |
| .mes_reasoning_arrow | div | container | 7447 |
| .mes_reasoning_actions | div | container | 7450 |
| .mes_reasoning_edit_done | button | tip: Confirm | 7451 |
| .mes_reasoning_delete | button | tip: Remove reasoning | 7452 |
| .mes_reasoning_edit_cancel | button | tip: Cancel edit | 7453 |
| .mes_reasoning_close_all | button | tip: Collapse all reasoning blocks | 7454 |
| .mes_reasoning_copy | button | tip: Copy reasoning | 7455 |
| .mes_reasoning_edit | button | tip: Edit reasoning | 7456 |
| .mes_reasoning | div | Reasoning / thinking text | 7459 |
| .mes_text | div | Rendered message body (markdown→HTML) | 7461 |
| .mes_media_wrapper | div | Attached media (images/video/audio) | 7462 |
| .mes_file_wrapper | div | Attached files | 7463 |
| .mes_bias | div | Shows "Start Reply With" bias text | 7464 |
| .swipeRightBlock | div | container | 7466 |
| .swipe_right | button | Swipe right: next swipe; on last swipe generates a new one | 7467 |
| .swipes-counter | div | Swipe counter "n/total" | 7468 |

#### Templates: group avatars, onboarding, group member, group list item, tag folders, alternate greetings, message file/image/video/gallery/audio

| element id | type | purpose | index.html:line |
|---|---|---|---|
| group_avatars_template | div | container | 7472 |
| .avatar | div | container | 7473 |
| .img_1 | img | container | 7474 |
| .avatar | div | container | 7476 |
| .img_1 | img | container | 7477 |
| .img_2 | img | container | 7478 |
| .avatar | div | container | 7480 |
| .img_1 | img | container | 7481 |
| .img_2 | img | container | 7482 |
| .img_3 | img | container | 7483 |
| .avatar | div | container | 7485 |
| .img_1 | img | container | 7486 |
| .img_2 | img | container | 7487 |
| .img_3 | img | container | 7488 |
| .img_4 | img | container | 7489 |
| onboarding_template | div | container | 7493 |
| .onboarding | div | container | 7494 |
| **— Welcome to SillyTavern! —** | section |  | 7495 |
| onboarding-UI-language-block | div | Language: English | 7496 |
| onboarding_ui_language_select | select | Language: — opts: en=English | 7498 |
| .expander | div | container | 7510 |
| **— Looking for AI characters? —** | section |  | 7512 |
| .external_import_button | button | Import | 7516 |
| .open_characters_library | button | Sample characters | 7523 |
| .expander | div | container | 7529 |
| **— Your Persona —** | section |  | 7530 |
| **— Persona Name: —** | section |  | 7540 |
| group_member_template | div | container | 7543 |
| .group_member | div | container | 7544 |
| .avatar | div | container | 7545 |
| .group_member_name | div | container | 7548 |
| .character_name_block | div | container | 7549 |
| .ch_name | span | Header row: name, ghost icon, timestamp, buttons | 7550 |
| .ch_additional_info | small | container | 7551 |
| .tags | div | container | 7553 |
| .ch_fav | text | Hidden: favorite flag | 7555 |
| .queue_position | div | container | 7556 |
| .group_member_icon | div | container | 7557 |
| [data-action=disable] | button | tip: Temporarily disable automatic replies from this character — action: disable | 7558 |
| [data-action=enable] | button | tip: Enable automatic replies from this character — action: enable | 7559 |
| [data-action=speak] | button | tip: Trigger a message from this character — action: speak | 7560 |
| [data-action=up] | button | tip: Move up — action: up | 7562 |
| [data-action=down] | button | tip: Move down — action: down | 7563 |
| [data-action=view] | button | tip: View character card — action: view | 7565 |
| [data-action=remove] | button | tip: Remove from group — action: remove | 7566 |
| [data-action=add] | button | tip: Add to group — action: add | 7567 |
| group_list_template | div | in this group | 7571 |
| .group_select | div | in this group | 7572 |
| .avatar | div | container | 7573 |
| .group_select_container | div | in this group | 7576 |
| .group_name_block | div | container | 7577 |
| .ch_name | div | Header row: name, ghost icon, timestamp, buttons | 7578 |
| .ch_additional_info | small | container | 7579 |
| .character_name_block_sub_line | small | in this group | 7581 |
| .group_fav_icon | i | container | 7582 |
| .ch_fav | text | Hidden: favorite flag — in this group | 7583 |
| .group_select_block_list | div | container | 7584 |
| .tags | div | container | 7585 |
| bogus_folder_template | div | container | 7589 |
| .bogus_folder_select | div | container | 7590 |
| .avatar | div | container | 7591 |
| .bogus_folder_icon | i | container | 7592 |
| .character_select_container | div | container | 7594 |
| .character_name_block | div | container | 7595 |
| .ch_name | span | Header row: name, ghost icon, timestamp, buttons | 7596 |
| .ch_additional_info | small | container | 7597 |
| .character_name_block_sub_line | small | container | 7599 |
| .bogus_folder_avatars_block | div | container | 7600 |
| bogus_folder_back_template | div | Go back | 7604 |
| BogusFolderBack | div | Go back | 7605 |
| .avatar | div | container | 7606 |
| .bogus_folder_icon | i | container | 7607 |
| .bogus_folder_back_placeholder | div | container | 7609 |
| .bogus_folder_icon | i | container | 7610 |
| .character_select_container | div | Go back | 7612 |
| .character_name_block | div | Go back | 7613 |
| .ch_name | span | Header row: name, ghost icon, timestamp, buttons | 7614 |
| inline_avatar_template | div | container | 7619 |
| .avatar | div | container | 7620 |
| alternate_greetings_template | div | container | 7624 |
| .alternate_grettings | div | container | 7625 |
| **— Alternate Greetings —** | section |  | 7627 |
| .mdhotkey_location | span | Alternate Greetings | 7627 |
| .add_alternate_greeting | button | Add | 7628 |
| .alternate_greetings_list | div | Click the button to get started! | 7638 |
| alternate_greeting_form_template | div | Alternate Greeting # Delete | 7645 |
| .alternate_greeting | div | Alternate Greeting # Delete | 7646 |
| .greeting_index | span | container | 7651 |
| .expander | span | container | 7654 |
| .move_up_alternate_greeting | button | tip: Move up | 7655 |
| .move_down_alternate_greeting | button | tip: Move down | 7658 |
| .delete_alternate_greeting | button | Delete | 7661 |
| [name=alternate_greetings] | textarea | Alternate greeting text — placeholder: "(This will be the first message from the character that starts every…" | 7667 |
| message_file_template | div | container | 7673 |
| .mes_media_container | div | container | 7674 |
| .mes_file_icon | div | container | 7675 |
| .mes_file_name | div | container | 7676 |
| .mes_file_size | div | container | 7677 |
| .mes_file_open | button | tip: View contents | 7678 |
| .mes_file_delete | button | tip: Remove the file | 7679 |
| message_image_template | div | container | 7683 |
| .mes_media_container | div | container | 7684 |
| .mes_img_controls | div | container | 7685 |
| .mes_media_enlarge | button | tip: Expand and zoom | 7686 |
| .mes_img_caption | button | tip: Caption | 7687 |
| .mes_media_delete | button | tip: Delete | 7688 |
| .mes_img | img | container | 7690 |
| message_video_template | div | container | 7694 |
| .mes_media_container | div | container | 7695 |
| .mes_video_controls | div | container | 7696 |
| .mes_media_enlarge | button | tip: Expand and zoom | 7697 |
| .mes_img_caption | button | tip: Caption | 7698 |
| .mes_media_delete | button | tip: Delete | 7699 |
| message_gallery_controls | div | 1/1 | 7705 |
| .mes_img_swipes | div | 1/1 | 7706 |
| .mes_img_swipe_left | button | tip: Swipe left | 7707 |
| .mes_img_swipe_counter | div | 1/1 | 7708 |
| .mes_img_swipe_right | button | tip: Swipe right | 7709 |
| message_audio_template | div | Audio 0:00 / 0:00 | 7713 |
| .mes_media_container | div | Audio 0:00 / 0:00 | 7714 |
| .audio-player-header | div | Audio | 7716 |
| .audio-player-title | div | Audio | 7717 |
| .mes_media_delete | button | tip: Delete | 7718 |
| .audio-player-controls | div | 0:00 / 0:00 | 7720 |
| .audio-player-play-pause | button | tip: Play | 7721 |
| .audio-player-time | div | 0:00 / 0:00 | 7722 |
| .audio-player-current-time | span | 0:00 | 7723 |
| .audio-player-time-separator | span | / | 7724 |
| .audio-player-total-time | span | 0:00 | 7725 |
| .audio-player-progress | div | container | 7727 |
| .audio-player-progress-bar | div | container | 7728 |
| .audio-player-volume-control | div | container | 7730 |
| .audio-player-volume | button | tip: Mute | 7731 |

#### #floatingPrompt — Author's Note panel (#movingDivs; opened via #option_toggle_AN)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| movingDivs | div | container | 7737 |
| floatingPrompt | div | container .drawer-content | 7738 |
| floatingPromptheader | div | container .drag-grabber | 7740 |
| floatingPromptMaximize | button | Maximize panel | 7741 |
| ANClose | button | Close panel | 7744 |
| ANBlockToggle | button | Author's Note | 7748 |
| extension_floating_prompt | textarea | Author's Note text (this chat; checkpoints inherit) | 7757 |
| extension_floating_prompt_token_counter | span | 0 | 7759 |
| extension_floating_allow_wi_scan | checkbox | Include in World Info Scanning | 7762 |
| extension_floating_position_before | radio | Before Main Prompt / Story String — value=2 — name=extension_floating_position | 7767 |
| extension_floating_position_after | radio | After Main Prompt / Story String — value=0 — name=extension_floating_position | 7771 |
| extension_floating_position_depth | radio | In-chat @ Depth — value=1 — name=extension_floating_position | 7775 |
| extension_floating_depth | number | AN in-chat depth — [min=0 max=9999] | 7777 |
| extension_floating_role | select | AN role @ depth — opts: 0=System, 1=User, 2=Assistant | 7779 |
| extension_floating_interval | number | AN insertion frequency (0=disable, 1=always) — [min=0 max=9999] | 7792 |
| extension_floating_counter | span | (disabled) | 7795 |
| charaANBlockToggle | button | Character Author's Note (Private) Won't be shared with the character card on export. | 7800 |
| extension_floating_chara | textarea | Character Author's Note — placeholder: "Example: [Scenario: wacky adventures; Genre: romantic comedy; Style:…" | 7814 |
| extension_floating_chara_token_counter | span | 0 | 7816 |
| extension_use_floating_chara | checkbox | Use character author's note | 7819 |
| [name=extension_floating_char_position] | radio | Replace Author's Note — value=0 | 7824 |
| [name=extension_floating_char_position] | radio | Top of Author's Note — value=1 | 7828 |
| [name=extension_floating_char_position] | radio | Bottom of Author's Note — value=2 | 7832 |
| defaultANBlockToggle | button | Default Author's Note | 7840 |
| extension_floating_default | textarea | Default Author's Note for new chats — placeholder: "Example: [Scenario: wacky adventures; Genre: romantic comedy; Style:…" | 7846 |
| extension_floating_default_token_counter | span | 0 | 7848 |
| extension_default_position_before | radio | Before Main Prompt / Story String — value=2 — name=extension_default_position | 7852 |
| extension_default_position_after | radio | After Main Prompt / Story String — value=0 — name=extension_default_position | 7856 |
| extension_default_position_depth | radio | In-chat @ Depth — value=1 — name=extension_default_position | 7860 |
| extension_default_depth | number | Default AN depth — [min=0 max=9999] | 7862 |
| extension_default_role | select | Default AN role — opts: 0=System, 1=User, 2=Assistant | 7864 |
| extension_default_interval | number | Default AN insertion frequency — [min=0 max=9999] | 7876 |

#### #cfgConfig — CFG Scale panel (opened via #option_toggle_CFG)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| cfgConfig | div | container .drawer-content | 7882 |
| cfgConfigheader | div | container .drag-grabber | 7884 |
| cfgConfigMaximize | button | Maximize panel | 7885 |
| CFGClose | button | Close panel | 7888 |
| chat_cfg_container | div | container | 7891 |
| CFGBlockToggle | button | Chat CFG | 7893 |
| chat_cfg_guidance_scale / chat_cfg_guidance_scale_counter | range+number | Scale 1 = disabled — [min=0.10 max=4.00 step=0.05] | 7907 |
| chat_cfg_negative_prompt | textarea | Negative Prompt — placeholder: "write short replies, write replies using past tense" | 7917 |
| chat_cfg_positive_prompt | textarea | Positive Prompt — placeholder: "write short replies, write replies using past tense" | 7921 |
| groupchat_cfg_use_chara_container | div | Use character CFG scales | 7923 |
| groupchat_cfg_use_chara | checkbox | Use character CFG scales | 7925 |
| chara_cfg_container | div | container | 7932 |
| charaANBlockToggle | button | Character CFG | 7935 |
| chara_cfg_guidance_scale / chara_cfg_guidance_scale_counter | range+number | Scale 1 = disabled — [min=0.10 max=4.00 step=0.05] | 7948 |
| chara_cfg_negative_prompt | textarea | Negative Prompt — placeholder: "write short replies, write replies using past tense" | 7958 |
| chara_cfg_positive_prompt | textarea | Positive Prompt — placeholder: "write short replies, write replies using past tense" | 7962 |
| global_cfg_container | div | container | 7967 |
| defaultANBlockToggle | button | Global CFG | 7970 |
| global_cfg_guidance_scale / global_cfg_guidance_scale_counter | range+number | Scale 1 = disabled — [min=0.10 max=4.00 step=0.05] | 7983 |
| global_cfg_negative_prompt | textarea | Negative Prompt — placeholder: "write short replies, write replies using past tense" | 7993 |
| global_cfg_positive_prompt | textarea | Positive Prompt — placeholder: "write short replies, write replies using past tense" | 7997 |
| cfg_prompt_combine_container | div | container | 8002 |
| defaultANBlockToggle | button | CFG Prompt Cascading | 8005 |
| [name=cfg_prompt_combine] | checkbox | Chat Negatives — value=0 | 8023 |
| [name=cfg_prompt_combine] | checkbox | Character Negatives — value=1 | 8027 |
| [name=cfg_prompt_combine] | checkbox | Global Negatives — value=2 | 8031 |
| cfg_prompt_separator | text | Custom Separator: — placeholder: ""\n"" | 8037 |
| cfg_prompt_insertion_depth | number | Insertion Depth: — [min=0 max=99] | 8040 |

#### #logprobsViewer — Token Probabilities panel (opened via #option_toggle_logprobs)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| logprobsViewer | div | container .drawer-content | 8048 |
| logprobsViewerheader | button | Drag handle | 8054 |
| logprobsMaximizeToggle | button | Maximize panel | 8057 |
| logprovsViewerBlockToggle | button | Collapse panel | 8060 |
| logprobsViewerClose | button | Close panel | 8063 |
| logprobsReroll | button | tip: Reroll with the entire prefix | 8069 |
| logprobs_generation_output | div | container | 8074 |
| logprobs_selected_top_logprobs | div | container .logprobs_candidate_list | 8075 |

#### Chat column & bottom form (#sheld, #chat, #form_sheld, #send_form)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| sheld | div | Main chat column | 8080 |
| sheldheader | div | container .drag-grabber | 8081 |
| chat | div | Chat message container (#message_template instances) | 8082 |
| form_sheld | div | Bottom input area wrapper | 8084 |
| dialogue_del_mes | div | Delete-messages mode bar (shown instead of input) | 8085 |
| dialogue_del_mes_ok | button | Delete | 8086 |
| dialogue_del_mes_cancel | button | Cancel | 8087 |
| send_form | div | Bottom input form; class no-connection until API connected; holds attached-file preview (File Name/File Size) | 8089 |
| file_form | form | container | 8090 |
| file_form_input | file | Hidden file input: attach file to message (multiple) | 8092 |
| embed_file_input | file | Hidden file input: embed file into existing message (multiple) | 8093 |
| file_form_reset | button | tip: Remove the file | 8097 |
| nonQRFormItems | div | container | 8102 |
| leftSendForm | div | container | 8103 |
| options_button | button | Burger (fa-bars): toggles #options menu | 8104 |
| send_textarea | textarea | Main chat input; placeholder switches "Not connected to API!" / "Type a message, or /? for help" | 8106 |
| rightSendForm | div | container | 8107 |
| stscript_continue | button | tip: Continue script execution | 8108 |
| stscript_pause | button | tip: Pause script execution | 8111 |
| stscript_stop | button | tip: Abort script execution | 8114 |
| mes_stop | button | Stop generation — tip: Abort request | 8117 |
| mes_impersonate | button | tip: Ask AI to write your message for you | 8120 |
| mes_continue | button | tip: Continue the last message | 8121 |
| send_but | button | tip: Send a message | 8122 |

#### Extensions wand menu (#extensionsMenuButton / #extensionsMenu) — NOT in index.html; injected at runtime by `addExtensionsButtonAndMenu()` (public/scripts/extensions.js:688): button appended to #leftSendForm (index.html:8103), menu appended to body; Popper-positioned, closes on outside click; button hidden when no visible items (extensions.js:230-238)

| element id | type | purpose | file:line |
|---|---|---|---|
| extensionsMenuButton | button | Magic-wand icon (fa-magic-wand-sparkles), tip: Extensions; toggles #extensionsMenu | public/scripts/templates/wandButton.html:1 |
| extensionsMenu | div .options-content | Popup list; each extension mounts its item(s) into a slot below | public/scripts/templates/wandMenu.html:1 |
| data_bank_wand_container | div slot (filled by extension) | Data Bank / attachments manager | public/scripts/templates/wandMenu.html:2 |
| attach_file_wand_container | div slot (filled by extension) | Attach a file | public/scripts/templates/wandMenu.html:3 |
| sd_wand_container | div slot (filled by extension) | Image generation (Stable Diffusion etc.) | public/scripts/templates/wandMenu.html:4 |
| caption_wand_container | div slot (filled by extension) | Caption image | public/scripts/templates/wandMenu.html:5 |
| gallery_wand_container | div slot (filled by extension) | Character gallery | public/scripts/templates/wandMenu.html:6 |
| tts_wand_container | div slot (filled by extension) | TTS controls | public/scripts/templates/wandMenu.html:7 |
| screen_share_wand_container | div slot (filled by extension) | Screen share | public/scripts/templates/wandMenu.html:8 |
| prompt_inspector_wand_container | div slot (filled by extension) | Inspect prompts | public/scripts/templates/wandMenu.html:9 |
| emulatorjs_wand_container | div slot (filled by extension) | EmulatorJS | public/scripts/templates/wandMenu.html:10 |
| notebook_wand_container | div slot (filled by extension) | Notebook | public/scripts/templates/wandMenu.html:11 |
| chess_wand_container | div slot (filled by extension) | Chess | public/scripts/templates/wandMenu.html:12 |
| token_counter_wand_container | div slot (filled by extension) | Token counter | public/scripts/templates/wandMenu.html:13 |
| dice_wand_container | div slot (filled by extension) | Roll dice | public/scripts/templates/wandMenu.html:14 |
| objective_wand_container | div slot (filled by extension) | Objective | public/scripts/templates/wandMenu.html:15 |
| translate_wand_container | div slot (filled by extension) | Translate chat | public/scripts/templates/wandMenu.html:16 |

#### #options menu (burger popup, left of input)

| element id | type | purpose | index.html:line |
|---|---|---|---|
| options | div | container | 8129 |
| option_close_chat | a | Close chat (first copy, class displayNone; duplicate id, see :8168) | 8131 |
| option_settings | a | Toggle Panels | 8135 |
| option_toggle_AN | a | Author's Note | 8139 |
| option_toggle_CFG | a | CFG Scale | 8143 |
| option_toggle_logprobs | a | Token Probabilities | 8147 |
| option_back_to_main | a | Back to parent chat | 8151 |
| option_new_bookmark | a | Save checkpoint | 8155 |
| option_convert_to_group | a | Convert to group | 8159 |
| option_start_new_chat | a | Start new chat | 8164 |
| option_close_chat | a | Close chat | 8168 |
| option_select_chat | a | Manage chat files | 8172 |
| option_delete_mes | a | Delete messages | 8177 |
| option_regenerate | a | Regenerate | 8181 |
| option_impersonate | a | Impersonate — tip: Ask AI to write your message for you | 8185 |
| option_continue | a | Continue — tip: Continue the last message | 8189 |
| secrets_datalists | div | container | 8197 |

#### Notes for parity

- Element visibility per API is driven by attributes, not separate pages: `data-tg-type` (textgen backend, from #textgen_type :2430), `data-source` (chat-completion source, from #chat_completion_source :2922), and the per-API blocks (#kobold_api-presets/#novel_api-presets/#openai_api-presets/#textgenerationwebui_api-presets :91-235; #kobold_api-settings/#novel_api-settings/#textgenerationwebui_api-settings/#openai_settings :1006-1878) toggled by #main_api :2290.
- Prompt Manager list (#completion_prompt_manager :2274) is generated by `PromptManager` JS; its editor UI is #completion_prompt_manager_popup (:7276).
- Lists are template-cloned: characters (#character_template :7232), groups (#group_list_template :7571), tag folders (#bogus_folder_template :7589), personas (#user_avatar_template :6443), messages (#message_template :7391), WI entries (#entry_edit_template :6816), past chats (#past_chat_template :6777), backgrounds (#background_template :6701).



### 1.2 `power_user` settings (public/scripts/power-user.js:116-343)

Persisted inside settings.json as `power_user`; loaded by `loadPowerUserSettings(settings, data)` (power-user.js:1554), applied by `applyPowerUserSettings()` (:1475). "Def line" = key in default object; "Handler" = UI binding in power-user.js unless another file given.

Enums: `avatar_styles` ROUND 0 / RECTANGULAR 1 / SQUARE 2 / ROUNDED 3 (:95-100); `chat_styles` DEFAULT 0 / BUBBLES 1 / DOCUMENT 2 (:102-106); `send_on_enter_options` DISABLED -1 / AUTO 0 (= on unless mobile) / ENABLED 1 (:108-112; RossAscends-mods.js:149-160); `toastPositionClasses` toast-{top,bottom}-{left,center,right} (:73-80, default top-center :93); `persona_description_positions` IN_PROMPT 0 / AFTER_CHAR 1 (deprecated) / TOP_AN 2 / BOTTOM_AN 3 / AT_DEPTH 4 (personas.js:88-96); `names_behavior_types` none/force/always (instruct-mode.js:17-21); `tag_import_setting` ASK 1 / NONE 2 / ALL 3 / ONLY_EXISTING 4 (tags.js:246-251); `tag_sort_mode` manual/alphabetical/by_entries (tags.js:254-258); `MEDIA_DISPLAY` list/gallery, `IMAGE_OVERSWIPE` generate/rollover (constants.js:86-98). Context limits: default 8192 ctx / 2048 resp; unlocked 512K / 64K (:82-88).

| Key | Default | Meaning | UI control (handler line) | Def line |
|---|---|---|---|---|
| charListGrid | false | character list grid view | `#charListGridToggle` (script.js:12571, 10797) | 117 |
| tokenizer | BEST_MATCH | tokenizer for counting | `#tokenizer` (3633) | 118 |
| token_padding | 64 | safety padding subtracted from context | `#token_padding` (3675) | 119 |
| collapse_newlines | false | collapse consecutive newlines in output | `#collapse-newlines-checkbox` (3215) | 120 |
| pin_examples / strip_examples | false / false | example messages: always keep / never send | `#example_messages_behavior` select (3314) | 121-122 |
| trim_sentences | false | trim incomplete sentences | `#trim_sentences_checkbox` (3223) | 123 |
| always_force_name2 | false | always prefix char name (groups/instruct) | `#always-force-name2-checkbox` (3277) | 124 |
| user_prompt_bias | '' | "Start Reply With" prefill | `#start_reply_with` (3288); `/start-reply-with` | 125 |
| show_user_prompt_bias | true | show prefill in reply | `#chat-show-reply-prefix-checkbox` (3293) | 126 |
| auto_continue.{enabled,allow_chat_completions,target_length} | false,false,400 | auto-continue until N tokens | `#auto_continue_enabled/_allow_chat_completions/_target_length` (3299-3310) | 127-131 |
| markdown_escape_strings | '' | comma list of strings excluded from Markdown | `#markdown_escape_strings` (3282) | 132 |
| chat_truncation | 100 | messages rendered initially ("# msgs to load") | `#chat_truncation` (3400) | 133 |
| streaming_fps | 30 | stream render FPS | `#streaming_fps` (3406) | 134 |
| smooth_streaming / _no_think / _speed | false/false/50 | typewriter smoothing (skip for reasoning) | `#smooth_streaming*` (3412-3422) | 135-137 |
| stream_fade_in | false | fade-in streamed words (morphdom) | `#stream_fade_in` (3427) | 138 |
| fast_ui_mode | true | "No Blur Effect" (`body.no-blur`) | `#fast_ui_mode` (3339; switchUiMode 959) | 140 |
| avatar_style | ROUND | avatar shape | `#avatar_style` (3371) | 141 |
| chat_display | DEFAULT | flat / bubbles / document | `#chat_display` (3378) | 142 |
| toastr_position | top-center | toast placement | `#toastr_position` (3385) | 143 |
| chat_width | 50 | chat column width % | `#chat_width_slider` (3392) | 144 |
| never_resize_avatars | false | skip avatar crop on upload | `#never_resize_avatars` (3534) | 145 |
| show_card_avatar_urls | false | show avatar filenames in list | `#show_card_avatar_urls` (3539) | 146 |
| play_message_sound / play_sound_unfocused | false / true | ping on new message (only when unfocused) | (3545, 3550); `playMessageSound` 383 | 147-148 |
| auto_save_msg_edits | false | autosave message edits while typing | (3555) | 149 |
| confirm_message_delete | true | confirm before deleting | (3649) | 150 |
| sort_field / sort_order / sort_rule | 'name'/'asc'/null | character list sort | `#character_sort_order` (3560) | 152-154 |
| font_scale | 1 | `--fontScale` CSS var | `#font_scale` (1175-1184) | 155 |
| blur_strength | 10 | backdrop blur px | `#blur_strength` (1162) | 156 |
| shadow_width | 2 | text shadow width | `#shadow_width` (1168) | 157 |
| main/italics/underline/quote_text_color | CSS `--SmartTheme*` | text colors | `#main-text-color-picker`, `#italics-color-picker`, `#underline-color-picker`, `#quote-color-picker` (3455-3473) | 159-162 |
| blur_tint/chat_tint/user_mes_blur_tint/bot_mes_blur_tint/shadow/border_color | CSS vars | UI tints | `#blur-tint-…`, `#chat-tint-…`, `#user-mes-blur-tint-…`, `#bot-mes-blur-tint-…`, `#shadow-…`, `#border-color-picker` (3479-3509) | 163-168 |
| custom_css | '' | user CSS injected | `#customCSS` (3351) | 170 |
| waifuMode | false | Visual Novel mode | `#waifuMode` (3345); `/vn` | 172 |
| movingUI / movingUIState / movingUIPreset | false/{}/'' | draggable panels; per-element {top,left,right,bottom,width,height,margin}; preset name | `#movingUImode` (3357), `#movingUIPresets` (3521); RossAscends-mods.js:491-499 | 173-175 |
| noShadows | false | disable text shadows | `#noShadowsmode` (3363) | 176 |
| theme | 'Default (Dark) 1.7.1' | active theme | `#themes` (3514) | 177 |
| gestures | true | swipe gestures | `#gestures-checkbox` (3572); RossAscends-mods.js:910 | 179 |
| auto_swipe, _minimum_length, _blacklist, _blacklist_threshold | false,0,[],2 | auto-regenerate short/blacklisted replies | `#auto_swipe*` (3577-3599) | 180-183 |
| auto_scroll_chat_to_bottom | true | autoscroll | (3628) | 184 |
| auto_fix_generated_markdown | true | close unclosed md | (3607); `fixMarkdown` 429 | 185 |
| send_on_enter | AUTO | Enter sends | `#send_on_enter` (3643) | 186 |
| console_log_prompts | false | log prompts | (3613) | 187 |
| request_token_probabilities | false | request logprobs | (3618) | 188 |
| show_group_chat_queue | false | show group reply queue | (3623) | 189 |
| allow_name1_display / allow_name2_display | false | show "User:"/"Char:" prefixes in output | (3663, 3669) | 190-191 |
| hotswap_enabled | true | favorites hotswap bar | `#hotswapEnabled` (3756) | 192 |
| timer_enabled / timestamps_enabled / timestamp_model_icon / mesIDDisplay_enabled / hideChatAvatars_enabled / message_token_count_enabled | true/true/false/false/false/false | per-message gen timer, timestamp, model icon, #id, hide avatars, token count | `#messageTimerEnabled` (3680), `#messageTimestampsEnabled` (3687), `#messageModelIconEnabled` (3694), `#mesIDDisplayEnabled` (3742), `#hideChatAvatarsEnabled` (3749), `#messageTokensEnabled` (3701) | 193-199 |
| max_context_unlocked | false | allow >default context | `#max_context_unlocked` (1863) | 198 |
| expand_message_actions | false | always show all mes buttons | `#expandMessageActions` (3708) | 200 |
| enableZenSliders / enableLabMode | false | zen slider UI / unlocked sampler ranges | (3715, 3728) | 201-202 |
| prefer_character_prompt / prefer_character_jailbreak | true | card system prompt / post-history override wins | (3763, 3769) | 203-204 |
| quick_continue / quick_impersonate / continue_on_send | false | show continue/impersonate buttons; empty send = continue | (3781, 3788, 3775) | 205-207 |
| trim_spaces | true | trim whitespace in messages | (3795) | 208 |
| relaxed_api_urls | false | don't normalize API URLs | (3801) | 209 |
| world_import_dialog | true | prompt to import embedded lorebook | (3807) | 210 |
| enable_auto_select_input | false | select text on focus | (3813) | 211 |
| enable_md_hotkeys | false | Ctrl+B/I/U/K etc. in textareas | (3819); input-md-formatting.js | 212 |
| tag_import_setting | ASK | import tags from cards | `#tag_import_setting` (3924) | 213 |
| tag_sort_mode | MANUAL | tag list order | `#tag_sort_mode_select` (tags.js:1720-1747) | 214 |
| disable_group_trimming | false | don't trim group replies at other names | (3896) | 215 |
| single_line | false | single-line replies | `#single_line` (3228) | 216 |
| instruct.* | enabled false, preset 'Alpaca', input_sequence '### Instruction:', output_sequence '### Response:', *_suffix/system/first/last sequences '', story_string_prefix/suffix, stop_sequence, wrap true, macro true, names_behavior FORCE, activation_regex, bind_to_context, user_alignment_message, system_same_as_user, separator_sequence (deprecated), sequences_as_stop_strings true, skip_examples | Instruct template | `#instruct_*` controls (instruct-mode.js:24-47) | 218-244 |
| context.* | preset 'Default', story_string (Handlebars), chat_start '***', example_separator '***', use_stop_strings true, names_as_stop_strings true, story_string_position IN_PROMPT, _role SYSTEM, _depth 1 | Context template | `#context_*` (contextControls :354-367) | 247-257 |
| instruct_derived / context_derived / context_size_derived | false | derive templates / ctx size from backend model | (3244, 3234, 3254) | 259-261 |
| model_templates_mappings / chat_template_hash | {} / '' | model→template bindings | `#bind_model_templates` (3269) | 263-265 |
| sysprompt.{enabled,name,content,post_history} | true,'Neutral - Chat',default text,'' | system prompt template | sysprompt.js:109-181 | 267-272 |
| reasoning.{name,auto_parse,add_to_prompts,auto_expand,show_hidden,prefix '<think>',suffix '</think>',separator '\n',max_additions 1} | – | reasoning parsing/formatting | reasoning.js:804-868 | 274-284 |
| personas / default_persona / persona_descriptions | {} / null / {} | avatar→name map; default; per-avatar {description,position,depth,role,lorebook,title} | personas.js:905-914 | 286-288 |
| persona_description, _position, _role, _depth, _lorebook | '',IN_PROMPT,0,2,'' | active persona fields | personas.js:626-630, 911-914 | 290-294 |
| persona_show_notifications | true | toast on persona switch | (3853) | 295 |
| persona_sort_order | 'asc' | persona list order | personas.js:199 | 296 |
| custom_stopping_strings / _macro | '' / true | JSON array of stop strings; expand macros | (3838, 3843); `/stop-strings` | 298-299 |
| fuzzy_search | false | Fuse fuzzy search | `#fuzzy_search_checkbox` (3848); 2101-2214 | 300 |
| encode_tags | false | escape `<tags>` in display | (3868) | 301 |
| experimental_macro_engine | true | new chevrotain macro engine | (3874) | 302 |
| servers | [] | API URL history `{label,url,lastConnection}` | server-history.js:11-74 | 303 |
| bogus_folders | false | tags as folders | (3905) | 304 |
| zoomed_avatar_magnification | false | magnifier on zoomed avatar | (3911) | 305 |
| show_tag_filters | false | show tag filter bar | tags.js:214-233 | 306 |
| aux_field | 'character_version' | secondary field in list | `#aux_field` (3917) | 307 |
| stscript.{matching 'fuzzy', autocomplete{state ALWAYS, autoHide, style 'theme', font.scale 0.8, width{left,right}, select TAB+ENTER, showInAllMacroFields}, parser.flags} | – | STscript editor/autocomplete | `#stscript_*` (3930-3995) | 308-329 |
| restore_user_input | true | persist unsent input (localStorage `<handle>_userInput`) | (4009); RossAscends-mods.js:450-469 | 330 |
| reduced_motion | false | disable animations (also honors OS pref) | (4014); :512 | 331 |
| compact_input_area | true | compact send form | (4020) | 332 |
| show_swipe_num_all_messages | false | swipe counter on all msgs | (4026) | 333 |
| auto_connect / auto_load_chat | false | connect API / open last chat on start | `#auto-connect-checkbox` (4032), `#auto-load-chat-checkbox` (4037) | 334-335 |
| forbid_external_media, external_media_allowed_overrides, _forbidden_overrides | true,[],[] | block remote media; per-char/group overrides | (4042); chats.js:833-858, 2305-2321 | 336-338 |
| pin_styles | true | pin card `<style>` globally | (4048); applyStylePins 1497 | 339 |
| click_to_edit | false | click message text to edit | (4054) | 340 |
| media_display | LIST | attachment display | `#media_display` (4083) | 341 |
| image_overswipe | GENERATE | swipe past last image: generate vs rollover | `#image_overswipe` (4091) | 342 |
| spoiler_free_mode *(not in defaults)* | – | hide card definitions | `#spoiler_free_mode` (3826), `#spoiler_free_desc_button` (3832) | 983 |
| persona_allow_multi_connections, persona_auto_lock *(not in defaults)* | – | persona connection options | (3858, 3863) | – |

Other power-user.js items:

| Item | Detail | Cite |
|---|---|---|
| Theme object | name + blur_strength, 10 colors, shadow_width, font_scale, fast_ui_mode, waifuMode, avatar_style, chat_display, toastr_position, noShadows, chat_width, timer/timestamps/model_icon/mesID/hideChatAvatars/token_count, expand_message_actions, enableZenSliders, enableLabMode, hotswap_enabled, custom_css, bogus_folders, zoomed_avatar_magnification, reduced_motion, compact_input_area, show_swipe_num_all_messages, click_to_edit, media_display | power-user.js:2535-2578 |
| Theme apply table | key → selector/type mapping used by `applyTheme(name)` | :1227-1300 |
| Theme endpoints | `POST /api/themes/save` (:2499), `POST /api/themes/delete` (:2404; template `themeDelete`), import file (`#ui_preset_import_button/_file`, :4059-4063, warns via `themeImportWarning` :2460), export JSON (`#ui_preset_export_button`, :4079, 2432); save/update/delete buttons `#ui-preset-save-button/-update-button/-delete-button` (:3529-3531); list from `data.themes` (:1604) |
| Moving UI presets | `POST /api/moving-ui/save {name, movingUIState}` (:2598-2610); `#movingui-preset-save-button` (:3532); list `data.movingUIPresets` (:1608); `#movingUIreset` (:3369) → `resetMovablePanels` (:2655) emits `MOVABLE_PANELS_RESET`; `loadMovingUIState()` desktop only (:1835) |
| Context presets | `context_presets` from `data.context` (:1614) |
| Debug menu | `registerDebugFunction(id,name,desc,fn)` (:1466); `#debug_menu` (:3901) opens `debug` template popup (:1471) |
| Fuzzy search | `performFuzzySearch`, `fuzzySearchCharacters/WorldInfo/Personas/Tags/Groups` (:2101-2230) |
| Story string | `renderStoryString` compiles Handlebars (:2234-2245) |
| Stop strings | `getCustomStoppingStrings`, `addEphemeralStoppingString`, `flushEphemeralStoppingStrings`, `generatedTextFiltered` (:3017-3078) |
| `#reload_chat` | reload current chat (:3654) |
| Slash commands | `/vn` toggle VN (:4115), `/newchat [delete]` (:4120), `/random [tag]` random char (:4138), `/del [toolcalls] N` delete mode/last N (:4150), `/cut` messages by index/range (:4171), `/resetpanels` (:4211), `/bgcol` auto UI color from bg (:4217), `/theme` (:4242), `/css-var varname= to=` set CSS var (:4272), `/movingui` preset (:4361), `/stop-strings` (:4374), `/start-reply-with` (:4437) |

### 1.3 RossAscends-mods.js

| Feature | Detail | Cite |
|---|---|---|
| Device detection | `getParsedUA()` = Bowser parse; `isMobile()` = platform type mobile/tablet | RossAscends-mods.js:127-147 |
| Send on Enter | `shouldSendOnEnter()` per `send_on_enter` (AUTO → desktop only) | :149-160 |
| Date helpers | `humanizeGenTime`, `humanizedDateTime` (`YYYY-MM-DD@HHhMMmSSsMSms`), `getMessageTimeStamp` | :103, 169, 192 |
| Char token counter | MutationObserver + debounced `RA_CountCharTokens()` on create/edit form & advanced defs, shows perm/total tokens | :64-101, 199-270 |
| Auto-load last chat | `RA_autoloadchat()` (power_user.auto_load_chat) | :272-303 |
| Favorites hotswap | `favsToHotswap()` | :305-322 |
| Online status | `RA_checkOnlineStatus` (debounced) on `#main_api` change / `#api_button` | :324-353, 705-710 |
| Auto-connect | `RA_autoconnect(PrevApi)` clicks the correct `#api_button*` per main_api (kobold, novel, textgen, openai…) when `auto_connect` | :355-426 |
| Drawer pinning | pin checkboxes for right nav (`NavLockOn`), left nav (`LNavLockOn`), WI (`WINavLockOn`) + opened states (`NavOpened`, `LNavOpened`, `WINavOpened`) in accountStorage; restore on load **desktop only** | :428-448, 712-828 |
| Selected tab memory | `SelectedNavTab` (`rm_button_selected_ch` / `rm_button_characters`) | :843-844 |
| Restore user input | localStorage `<userHandle>_userInput`, debounced save | :450-469 |
| Moving UI | `dragElement($el)` drag+resize (header grabber, ResizeObserver; saves `power_user.movingUIState[name]`, emits `resizeUI`); disabled on mobile | :477-655 |
| `initMovingUI()` | apply drag to `#sheld`, `#left-nav-panel`, `#right-nav-panel`, `#WorldInfo`, `#floatingPrompt`, `#logprobsViewer`, `#cfgConfig` if movingUI && !mobile | :657-669 |
| Textarea autofit | `autoFitSendTextArea` grow `#send_textarea`, keep chat scrolled | :671-689 |
| `#send_textarea` focus/blur | toggles focus class | :831-841 |
| Character click | `.character_select`, `.group_select` handlers | :849-860 |
| Swipe gestures | `swiped-left` → click `.swipe_right:last`; `swiped-right` → `.swipe_left:last`; skipped if `gestures` false, editing, or modal/QR popup | :908-958 |
| Hotkeys | see 1.4 keyboard table | :979-1283 |

### 1.4 Other UI modules

**Keyboard shortcuts** (RossAscends-mods.js:992-1283 unless noted; suppressed while a popup is open :993)

| Keys | Action | Cite |
|---|---|---|
| Enter (no mods, not composing) | send (if shouldSendOnEnter) | :1001 |
| Enter in dialogue popup input | confirm (desktop) | :1007-1013 |
| Ctrl+Shift+↑ | scroll to context line (`.lastInContext`) | :1015 |
| Ctrl+Shift+↓ | scroll to bottom | :1026 |
| Alt+Enter | Continue | :1035 |
| Ctrl+Enter | Regenerate (or save edit; or send if input text) | :1044-1066 |
| ← / → | swipe left / right (when chat focused, input empty, no gallery lightbox) | :1107-1135 |
| Ctrl+↑ | edit last user message (input empty) | :1139 |
| ↑ | edit last message (input empty) | :1157 |
| Escape | close dialogs/panels in order (popup, chat select, character popup, WI, drawers…) except while editing/streaming | :1176-1275 |
| Ctrl+1..9 | reserved (QR) | :1277 |
| Enter on `.interactable` | click focused control | keyboard.js:213-235 |
| MD hotkeys (`textarea.mdHotkeys`, if enabled) | Ctrl+B `**`, Ctrl+I `*`, Ctrl+U `__`, Ctrl+K `` ` ``, Ctrl+Shift+` `~~` | input-md-formatting.js:4-45 |
| Help | `hotkeys` template via `/help hotkeys` | system-messages.js:55 |

| Module | Purpose / API | Cite |
|---|---|---|
| keyboard.js | `.interactable`/`.custom_interactable`/`.not_focusable`/`.disabled` classes; `registerInteractableType(selector,{disabledByDefault,notFocusableByDefault})`, `makeKeyboardInteractable`, MutationObserver auto-tabindex, scroll reset; `initKeyboard()` | keyboard.js:36-40, 64-254 |
| a11y.js | MutationObserver assigning ARIA roles: button (`.menu_button`, `.mes_button`, `.swipe_left/right`, `.character_select`, …), list, listitem, toolbar, tablist, tab, toast→status; shared with login page | a11y.js:1-30, 60-90, 91-135 |
| dragdrop.js | `class DragAndDropHandler(selector, onDrop(files,event), {noAnimation})` with dragover/leave/drop overlay; `destroy()`. Used for: whole page (char/card import or URL drop, script.js:12556), chat import in `#select_chat_popup` (:12563), `#form_sheld` file attach (chats.js:2392), Data Bank popup (chats.js:1510), gallery upload (extensions/gallery/index.js:251) | dragdrop.js:8-40 |
| dom-handlers.js | `initDomHandlers()`: wheel over focused number inputs adjusts value+linked slider (25 ms throttle) | dom-handlers.js:3-30 |
| browser-fixes.js | Firefox copy sanitization of inline quotes; Safari class `body.safari`; mobile resize/orientation `position:fixed` reflow fix | browser-fixes.js:5, 56-94 |
| f-localStorage.js | legacy `SaveLocal/LoadLocal/LoadLocalBool/CheckLocal/ClearLocal` | f-localStorage.js:6-39 |
| loader.js | legacy `showLoader/hideLoader` (deprecated in favor of action-loader `loader`) | loader.js:23, 52; st-context.js:196-199 |
| dynamic-styles.js | generates `:focus-visible` rules mirroring `:hover` rules (incl. extension stylesheets) | dynamic-styles.js:35-188 |
| templates.js | `renderTemplate(Async)(id, data, sanitize, localize, fullPath)`: fetch `scripts/templates/<id>.html`, Handlebars compile + cache, DOMPurify, `applyLocale` | templates.js:15-110 |
| setting-search.js | `#settingsSearch` input highlights matching labels inside `#user-settings-block-content` (skips headers) | setting-search.js:4-53 |

**Popup system (popup.js)**

| Item | Values | Cite |
|---|---|---|
| `POPUP_TYPE` | TEXT 1, CONFIRM 2, INPUT 3, DISPLAY 4 (no buttons, X), CROP 5 (Cropper.js) | popup.js:9-20 |
| `POPUP_RESULT` | AFFIRMATIVE 1, NEGATIVE 0, CANCELLED null, CUSTOM1-9 = 1001-1009 | :24-37 |
| `PopupOptions` | okButton, cancelButton, rows, placeholder, tooltip, wide, wider, large, transparent, allowHorizontalScrolling, allowVerticalScrolling, leftAlign, animation (slow/fast/none), defaultResult, customButtons[{text,tooltip,result,classes,icon,action,appendAtEnd}], customInputs[{id,label,tooltip,defaultState,type checkbox/text/textarea/number,rows,min,max,step,disabled,autoFocus}], allowEscapeClose, onClosing, onClose, onOpen, cropAspect, cropImage | :40-91 |
| API | `class Popup` (:149; `show()`, `complete(result)`, `completeCancelled()`, `inputResults` Map), `Popup.show.input/confirm/text` (:97-148, 858), `Popup.util.popups` stack (:865), `callGenericPopup(content,type,inputValue,options)` (:914), `PopupUtils.BuildTextWithHeader` (:896), `getTopmostModalLayer` (:930), `fixToastrForDialogs` (:939); `<dialog>` + polyfill (:1, 240); legacy `callPopup` in script.js:9067 |

**BulkEditOverlay.js** (character list multi-select)

| Item | Detail | Cite |
|---|---|---|
| States | browse 0 / select 1; long-press 2500 ms (touchstart/touchend/touchmove) or `#bulkEditButton` (bulk-edit.js:121) to enter select; shift-click ranges; `#bulkSelectedCount` | BulkEditOverlay.js:356-389, 497-609, 676 |
| Context menu `#character_context_menu` | favorite (merge-attributes `fav`), duplicate (`POST /api/characters/duplicate`), convert to persona, tag (BulkTagPopupHandler: add / remove mutual / reset), delete (+ delete chats) | :27-147, 44-115, 170-355, 756-890 |
| Endpoints | `/api/characters/duplicate` (:48), `/api/characters/merge-attributes` (:84) | |
| Event | `CHARACTER_GROUP_OVERLAY_STATE_CHANGE_AFTER` | :476 |

**welcome-screen.js**

| Feature | Detail | Cite |
|---|---|---|
| Trigger | `openWelcomeScreen` runs first on `CHAT_CHANGED` / `APP_READY` when no chat open | welcome-screen.js:925-931, 226 |
| Recent chats | `POST /api/chats/recent` → list (char & group), open, rename (`chatRename` template), delete, pin (`pinnedChats` accountStorage), "show more", hide section (`WelcomePage_RecentChatsHidden`); settings popup `maxRecentChats`, `collapsedRecentChats` (`recentChatsSettings`) | :40-42, 52-138, 315-476, 478-650, 690-760, 763 |
| Temporary chat | `button.openTemporaryChat` → `newAssistantChat({temporary:true})` | :384-386; script.js:10922 |
| Permanent Assistant | `assistant` key in accountStorage; create default assistant card via `POST /api/characters/create`; open/assign from char dropdown (`CHARACTER_MANAGEMENT_DROPDOWN`) | :204-216, 819-923, 933 |
| Templates | `welcomePanel`, `welcomePrompt`, assistant greeting | :329; system-messages.js:82 |

**backgrounds.js**

| Feature | Detail | Cite |
|---|---|---|
| Settings | `background_settings {name '__transparent.png', url, fitting 'classic', animation false, sortOrder 'az', thumbnailColumns}` | backgrounds.js:108-114, 211-245 |
| Fitting | classic / cover / contain / stretch / center (`#background_fitting`) | index.html:5664-5670; backgrounds.js:1632, 1838 |
| Sort | az / za / newest / oldest (`#bg-sort`) | :78-83, 1802 |
| Animated | mp4/webp/gif/apng; `#background_thumbnails_animation`; video upload converted via `globalThis.convertVideoToAnimatedWebp` | :55, 1411, 1509-1545, 1844 |
| Tabs | system vs chat-specific backgrounds (jQuery UI tabs `#bg_tabs`) | :661-707, 1685-1691 |
| Chat background | lock/unlock per chat in `chat_metadata.custom_background`; chat bg list `chat_metadata.chat_backgrounds`; upload chat bg saves as user image | :14-15, 303-366, 1579-1620 |
| Endpoints | `/api/backgrounds/all` (:709), `/upload` (:1554), `/delete` (:1450), `/rename` (:512), `/folders` (:766); `/api/image-metadata/all` (:742), `/folders/create|update|delete|set-thumbnails` (:1168, 1197, 1225, 793) |
| Folders | create/rename/delete/cover, multi-select mode `#bg_selection_mode_button`, add/remove to folder | :764-1355, 1797-1799 |
| Thumbnails | IndexedDB `SillyTavern_Thumbnails`, lazy loader, zoom `#bg_thumb_zoom_in/out` | :42, 431-470, 1357, 1790-1795 |
| Per-item menu | `.jg-button` lock/unlock/edit/delete/copy(to system)/folder; mobile `.mobile-only-menu-toggle` | :1716-1760 |
| Auto background | `#auto_background` / `/autobg` → `generateQuietPrompt` choosing from list | :622-660, 1796, 1831 |
| Filter | `#bg-filter` with Fuse | :636, 1654, 1801 |
| Slash | `/lockbg` (:1813), `/unlockbg` (:1822), `/autobg` (:1831) |
| Events | `CHAT_CHANGED`, `FORCE_SET_BACKGROUND` | :1694-1695 |

**i18n.js + public/locales**

| Item | Detail | Cite |
|---|---|---|
| Locale choice | localStorage `language` override else `navigator.language`, lowercased | i18n.js:4-6, 15 |
| Files | `locales/lang.json` (16 entries: ar-sa, zh-cn, zh-tw, nl-nl, de-de, fr-fr, is-is, it-it, ja-jp, ko-kr, pt-pt, ru-ru, es-es, uk-ua, vi-vn, th-th) + `en.json` (empty → source text) — each ~1450-3650 keys | public/locales/*.json |
| Markup | `data-i18n="key"` (textContent) or `"[attr]key"`; multiple `;`-separated | i18n.js:153-175 |
| API | `t` tagged template, `translate(text,key)`, `addLocaleData(locale,data)`, `applyLocale(root)`, `getCurrentLocale()`; MutationObserver on `data-i18n` | :22, 49-60, 81, 101, 246 |
| UI | `#ui_language_select`, `#onboarding_ui_language_select` → reload | :284-296 |
| Debug | `getMissingTranslations`, `trackDynamicTranslate` | :181, 305-320 |

**Templates (public/scripts/templates/*.html, 59 files)** — render site in parens: admin (user.js:827), assistantNote (system-messages.js:90), changeName (user.js:440), changePassword (user.js:298), charTagImport (tags.js:1058; +.i18n), chatLorebook (world-info.js:5959), chatRename (welcome-screen.js:541), createCheckpoint (bookmarks.js:100), customEndpointAdditionalParameters (openai.js:6202), dataMaidCategory/Dialog (data-maid.js:218/103), debug (power-user.js:1471), deleteConfirm (script.js:11421), deleteTag (tags.js:2122), deleteUser (user.js:356), duplicateConfirm (script.js:6054), emptyBlock/hiddenBlock (script.js:924/935), exportPreset (openai.js:4875), forbidMedia (chats.js:829), formatting/help/hotkeys/welcome/welcomePrompt (system-messages.js:59/47/55/67/82), globalStylesPopup/Preference (chats.js:763/715), importCharacters (script.js:12540), installExtension / thirdPartyExtensionWarning (extensions.js:2234/1721), itemizationChat/Text (itemized-prompts.js:264-265), masterExport/Import (preset-manager.js:340/298), migrateInstructPrompt (sysprompt.js:82), newChatConfirm (script.js:11616), personaLorebook (personas.js:1284), promptManagerHeader/Footer/ListHeader (PromptManager.js:1614/1635/1662), proxyConnectionWarning (openai.js:553), quotaError (openai.js:1695), resetSettings (user.js:403), samplerSelector (samplerSelect.js:30), scenarioOverride (script.js:9007), secretKeyManager(+ListItem) (secrets.js:661/704), snapshotsView (user.js:587), tabbyDownloader (textgen-models.js:1262), tagManagement (tags.js:1714), themeDelete/themeImportWarning (power-user.js:2397/2460), userProfile (user.js:676), userReset (user.js:637), wandButton/wandMenu (extensions.js:689-690), welcomePanel (welcome-screen.js:329), worldInfoKeywordHeaders (world-info.js:2415).

### 1.5 Moving UI, themes, CSS

| Item | Detail | Cite |
|---|---|---|
| Moving UI | `power_user.movingUI` toggled by `#movingUImode`; `dragElement` per panel saves geometry into `movingUIState`; `resetMovableStyles(id)`; `/resetpanels`; presets via `/api/moving-ui/save`; never active on mobile | RossAscends-mods.js:477-669; power-user.js:2643-2660, 1835 |
| Themes | full UI theme JSON (see 1.2 theme object); defaults shipped in `default/content/themes` served via settings; import warns if custom CSS present | power-user.js:2432-2535 |
| CSS vars | `--SmartThemeBodyColor/EmColor/UnderlineColor/QuoteColor/BlurTintColor/ChatTintColor/UserMesBlurTintColor/BotMesBlurTintColor/ShadowColor/BorderColor`, `--fontScale`, `--blurStrength`, `--shadowWidth`, `--sheldWidth` (chat_width vw) | power-user.js:159-168, 1087, 1104, 1161, 1175 |
| Custom CSS | `power_user.custom_css` into `<style id="custom-style">` (power-user.js:1149); server user CSS `/css/user.css` | power-user.js:3351; src/middleware/userCss.js |
| CSS files | style.css @imports (animations, popup, promptmanager, loader, character-group-overlay, file-form, logprobs, accounts, tags, scrollable-button, welcome, data-maid, secrets, backgrounds, chat-backups, streaming-display) + link tags (st-tailwind utility classes, rm-groups, group-avatars, toggle-dependent, world-info, extensions-panel, select2-overrides, mobile-styles, macros, user) | style.css:3-18; index.html:36-46 |

### 1.6 Mobile-specific behaviors already present

| Behavior | Cite |
|---|---|
| `isMobile()` = Bowser platform mobile/tablet (UA-based, not width) | RossAscends-mods.js:143-147 |
| Enter doesn't send on mobile by default (AUTO) | RossAscends-mods.js:157-158 |
| Drawer pin states ignored/not restored on mobile; pin checkboxes hidden ≤1000px | RossAscends-mods.js:429, 767; css/mobile-styles.css:12-15 |
| Moving UI disabled on mobile | RossAscends-mods.js:522, 658; power-user.js:1836 |
| Swipe gestures (swiped-events lib, threshold 20) left/right → swipe messages | RossAscends-mods.js:908-958; lib/swiped-events.js:49 |
| Long-press (500 ms, `addLongPressEvent`) replaces click: swipe counter → swipe picker, `#persona_lore_button`, `#world_button`, `.chat_lorebook_button` | utils.js:3098-3116; swipe-picker.js:425-427; personas.js:2932; world-info.js:6335, 6359 |
| Long-press 2500 ms on character → bulk select mode | BulkEditOverlay.js:389, 497-504, 571 |
| Range sliders: 300 ms touch-hold unlock before dragging (prevents scroll hijack) | script.js:11755-11776 |
| Sortable drag delay 750 ms mobile vs 50 ms; PromptManager uses `.drag-handle` on mobile | utils.js:362-364; PromptManager.js:1921 |
| Select2 not used on mobile (native selects): OpenRouter/textgen models, horde, WI filters, chat lorebook extras, tag merge | openai.js:7216; textgen-models.js:1419; horde.js:465; world-info.js:3091, 6369; script.js:9607; tags.js:2127 |
| WI key input plain text on mobile | world-info.js:2939 |
| Itemized prompt raw view opens extra popup on mobile | itemized-prompts.js:315-320 |
| Zoom-level report skipped on mobile | power-user.js:3158 |
| Viewport resize/orientation reflow fix | browser-fixes.js:73-90 |
| iOS/Safari body class; iOS TTS requires user gesture to unlock speech | browser-fixes.js:56-66; extensions/tts/system.js:122-127 |
| VN group sprites disabled on mobile | extensions/expressions/index.js:139 |
| Outside-tap closing on `touchstart mousedown` | script.js:12152 |
| `{{isMobile}}` macro & `/ismobile`-style command | macros/definitions/env-macros.js:203; slash-commands.js:3265 |
| Background thumbnail columns default by `(max-width:480px)` | backgrounds.js:229 |
| PWA standalone class `body.PWA`; `manifest.json` display standalone | script.js:799-804 |
| CSS breakpoints: ≤1000px main mobile layout (656-line mobile-styles.css), landscape ≤1000px, ≤450px; style.css 1000/768/750px rules; `prefers-contrast`, `prefers-reduced-motion` | css/mobile-styles.css:2, 511, 537; style.css:2505, 2636, 3731, 6092, 6316, 6443; power-user.js:512 |
| touch-punch for jQuery UI sortable/slider on touch | index.html:8205 |


## 2. Built-in extensions (public/scripts/extensions/*)

### Part A: assets, attachments, caption, connection-manager, expressions, gallery, memory, quick-reply, shared.js

Covers assets, attachments (Data Bank), caption, connection-manager, expressions, gallery, memory (Summarize), quick-reply, plus `shared.js`. Citations are `path:line` relative to the SillyTavern root.

Notes that apply to every extension below:
- No manifest in this group declares `i18n` or `generate_interceptor`. In the whole extensions folder only `stable-diffusion` and `vectors` declare an interceptor (public/scripts/extensions/stable-diffusion/manifest.json:8, public/scripts/extensions/vectors/manifest.json:8).
- Every manifest declares `hooks.activate: "init"`, so the loader calls the exported `init()`.
- Global defaults for several of these extensions are seeded in `extension_settings` (public/scripts/extensions.js:141-226): `expressionOverrides: []` (:147), `memory: {}` (:148), `caption: {refine_mode:false}` (:154-156), `expressions: {...}` (:157-171), `connectionManager: {selectedProfile:'', profiles:[]}` (:172-176), `quickReply: {}` (:195), `attachments: []` (:211), `character_attachments: {}` (:215), `disabled_attachments: []` (:219), `gallery: {folders:{}, sort:'dateAsc'}` (:220-225).

---

### 2.1 assets

**Manifest** (public/scripts/extensions/assets/manifest.json)

| field | value | line |
|---|---|---|
| display_name | "Assets" | :2 |
| loading_order | 15 | :3 |
| requires / optional | [] / [] | :4-5 |
| js / css | index.js / style.css | :6-7 |
| hooks.activate | init | :11-13 |

**Purpose:** A browser and installer for downloadable content (extensions, characters, ambient sounds, BGM, blip sounds). It loads a JSON asset index from a URL and installs or deletes each item through the server.

**Asset list URL:** the default is `https://raw.githubusercontent.com/SillyTavern/SillyTavern-Content/main/index.json` (public/scripts/extensions/assets/index.js:19). The URL is editable in `#assets-json-url-field` (public/scripts/extensions/assets/window.html:29), and the field is pre-filled with the default (public/scripts/extensions/assets/index.js:537-538).
- Clicking connect asks for confirmation, with a "Don't ask again for this URL" checkbox. That choice is stored in `accountStorage` under the key `Assets_SkipConfirm_<hash(url)>` (public/scripts/extensions/assets/index.js:560-571).
- The list must be a JSON array (:297-299). Items are grouped by `type` (:249-253), and `extension` sorts first (:257).

**Asset item fields used:** `id`, `name`, `description`, `url`, `type` (:65, :154-156); `tool` (extension tag "Adds a function tool", :159, :167-172); `highlight` (characters: trophy icon, or the "contest winners" list, :196-197, :480).

**Asset types**

| type | label | install path | line |
|---|---|---|---|
| extension | Extensions | core `installExtension(url,false)`; delete through `deleteExtension` | public/scripts/extensions/assets/index.js:56, :382-387, :425-430 |
| character | Characters | `/api/assets/download`, then the blob goes to `processDroppedFiles` (character import) | :57, :398-405 |
| ambient | Ambient sounds | `/api/assets/download` | :58, :389-395 |
| bgm | Background music | same | :59 |
| blip | Blip sounds | same | :60 |
| (any other) | type string shown raw | same | :264 |

- Valid categories on the server side are `bgm, ambient, blip, live2d, vrm, character, temp` (src/endpoints/assets.js:14).
- Extensions are split into "official" and "community" lists by `isOfficialExtension(url)` (public/scripts/extensions/assets/index.js:225-229).
- An installed extension is detected by a `third-party/` name prefix. An installed character is detected by its avatar filename (:350-369).
- Deleting a character is not supported. The code instead runs `/go <id>` (:106-110).
- An audio preview plays `.mp3/.ogg/.wav` inline (:319-339).

**Settings:** there is no `extension_settings.assets` key. The only persisted value is the `accountStorage` skip-confirm key above.

**Server endpoints**

| endpoint | method / body | purpose | file:line |
|---|---|---|---|
| `<asset list URL>` (external) | GET, no-cache | fetch the index | public/scripts/extensions/assets/index.js:292, :461 |
| /api/assets/download | POST `{url, category, filename}` | download a non-extension asset | :389-395 (server src/endpoints/assets.js:190) |
| /api/assets/delete | POST `{category, filename}` | delete an asset | :432-438 (server src/endpoints/assets.js:269) |
| /api/assets/get | POST | list installed assets by category | :515-518 (server src/endpoints/assets.js:108) |

**Slash commands:** none. It only invokes the existing `/go` (:108, :499).

**Events:** `eventSource.on(OPEN_CHARACTER_LIBRARY, forceDefault => openCharacterBrowser(forceDefault))` (:595-597). **Macros:** none.

**UI**
- window.html is appended to `#assets_container`. It is an inline drawer titled "Download Extensions & Assets" containing the URL field, a connect button `#assets-connect-button`, a type select `#assets_type_select`, a search box `#assets_search`, a "Characters" button `#assets-characters-button`, and the list `#assets_menu` (public/scripts/extensions/assets/window.html:1-46; public/scripts/extensions/assets/index.js:592-593).
- The character browser popup is built from market.html and character.html templates and shown with `callGenericPopup(... wide, large)` (:477-505).
- The extension section prepends installation.html (:216-218).
- The "Install extension" hint link flashes `#third_party_extension_button` (:545-550).

---

### 2.2 attachments (Data Bank)

**Manifest** (public/scripts/extensions/attachments/manifest.json)

| field | value | line |
|---|---|---|
| display_name | "Data Bank (Chat Attachments)" | :2 |
| loading_order | 3 | :3 |
| requires / optional | [] / [] | :4-5 |
| js / css | index.js / style.css | :6-7 |

**Purpose:** The extension itself is thin. It adds wand-menu buttons, slash commands and cleanup hooks. The Data Bank manager, file conversion, upload and storage logic live in core `public/scripts/chats.js`, and the scrapers live in `public/scripts/scrapers.js`.

**Data Bank scopes (storage)**

| scope | storage location | persisted by | file:line |
|---|---|---|---|
| global | `extension_settings.attachments[]` | saveSettingsDebounced | public/scripts/chats.js:1733-1736, :1781, :1800 |
| character | `extension_settings.character_attachments[<avatar>][]` | saveSettingsDebounced ("Saved locally. Not exported.") | public/scripts/chats.js:1741-1744, :1783, :1804; public/scripts/extensions/attachments/manager.html:103 |
| chat | `chat_metadata.attachments[]` | saveMetadataDebounced | public/scripts/chats.js:1737-1740, :1782, :1802 |
| disabled list | `extension_settings.disabled_attachments[]` (URLs) | settings | public/scripts/chats.js:1228, :1240, :1328 |

- Scope enum is `ATTACHMENT_SOURCE = {GLOBAL:'global', CHARACTER:'character', CHAT:'chat'}` (public/scripts/chats.js:77-81). The extension's own constant is `TYPES = ['global','character','chat']` (public/scripts/extensions/attachments/index.js:16).
- An attachment object is `{url, size, name, created}` (public/scripts/chats.js:1723-1728).
- `getDataBankAttachments(includeDisabled)` merges global, chat and character attachments (public/scripts/chats.js:1779-1786).
- Server verification runs per source through `/api/files/verify` (public/scripts/chats.js:1817-1832).

**File types / conversion (Data Bank and chat file attachments)**
- Converters by MIME type: `application/pdf`, `text/html`, `text/markdown`, `application/epub+zip`, docx, xlsx, pptx, odt, odp, ods (public/scripts/chats.js:86-97).
- Size limit is 350 MB for non-media files (public/scripts/chats.js:76, :334-337). Binary files that are not media and not convertible are rejected (:339-343).
- Every Data Bank upload is stored server-side as text, named `${Date.now()}_${hash(name)}.txt` (public/scripts/chats.js:1698-1716).
- The manager UI lists "Plain Text, PDF, Markdown, HTML, EPUB" as supported (public/scripts/extensions/attachments/manager.html:11).

**Chat (message) attachments**
- The `#attachFile` button triggers `#file_form_input` (multiple) and then `onFileAttach` (public/scripts/chats.js:2184-2207; public/index.html:8092).
- Images, video and audio go to `message.extra.media[]` (public/scripts/chats.js:225-227).
- Other files are converted, uploaded as `.txt`, and pushed to `message.extra.files[] {url,size,name,created}` (public/scripts/chats.js:229-257).
- Message-level controls are `.mes_file_delete`, `.mes_file_open` and `.mes_embed` (public/scripts/chats.js:2122-2133, :2214-2218).

**Scrapers** (registered for the Data Bank in public/scripts/scrapers.js:575-580; listed and run from the manager at public/scripts/chats.js:1421-1440, :1657-1683)

| id | name | backend | file:line |
|---|---|---|---|
| file | File (upload from computer) | none | public/scripts/scrapers.js:218-221 |
| text | Notepad | none | public/scripts/scrapers.js:96-99 |
| web | Web | POST /api/search/visit | public/scripts/scrapers.js:143-146, :193 |
| mediawiki | MediaWiki | /api/plugins/fandom/probe-mediawiki, /scrape-mediawiki (needs the server plugin) | public/scripts/scrapers.js:251-254, :260, :301 |
| fandom | Fandom | /api/plugins/fandom/probe, /scrape | public/scripts/scrapers.js:340-343, :353, :408 |
| youtube | YouTube | POST /api/search/transcript | public/scripts/scrapers.js:465-468, :558 |

**Settings keys:** `extension_settings.attachments`, `.character_attachments`, `.disabled_attachments` (described above). There is no `extension_settings.attachments.*` object; it is a plain array.
- On `APP_READY`, `cleanUpAttachments()` strips any legacy inline `text` field from stored attachments (public/scripts/extensions/attachments/index.js:200-217, :247).

**Server endpoints** (used by core; the extension has no direct fetch calls)

| endpoint | purpose | file:line |
|---|---|---|
| /api/files/upload | upload base64 file | public/scripts/chats.js:276 (server src/endpoints/files.js:28) |
| GET `<attachment url>` | read text content | public/scripts/chats.js:305 |
| /api/files/delete | delete file | public/scripts/chats.js:1130 (server src/endpoints/files.js:54) |
| /api/images/delete | delete media | public/scripts/chats.js:1099 |
| /api/files/verify | verify that files exist | public/scripts/chats.js:1832 (server src/endpoints/files.js:78) |

**Slash commands** (all in public/scripts/extensions/attachments/index.js)

| name | aliases | args | returns / purpose | line |
|---|---|---|---|---|
| db | databank, data-bank | none | clicks `#manageAttachments` to open the Data Bank | :274-282 |
| db-list | databank-list, data-bank-list | `source` (global/character/chat; default all), `field` (name/url; default url) | JSON array of URLs or names | :284-294 |
| db-get | databank-get, data-bank-get | `source`; unnamed name-or-URL (required) | text content | :296-314 |
| db-add | databank-add, data-bank-add | `source` (default chat), `name` (default `new Date().toLocaleString()`); unnamed content (required) | URL; uploads as text/plain | :316-329, :103-109 |
| db-update | databank-update, data-bank-update | `source` (default chat), `name`, `url`; unnamed content | new URL; deletes and re-uploads, keeping the name | :331-355, :117-131 |
| db-disable | databank-disable, data-bank-disable | `source`; unnamed name-or-URL | pushes to `disabled_attachments` | :357-373, :159-174 |
| db-enable | databank-enable, data-bank-enable | `source`; unnamed name-or-URL | removes from `disabled_attachments` | :375-391, :182-198 |
| db-delete | databank-delete, data-bank-delete | `source` (default chat); unnamed name-or-URL | deletes the file | :393-409, :139-151 |

Lookup is case-insensitive: an exact URL match wins over a name match (:38-43).

**Events**

| event | handler | line |
|---|---|---|
| APP_READY | cleanUpAttachments | public/scripts/extensions/attachments/index.js:247 |
| CHARACTER_DELETED | delete `character_attachments[avatar]` | :223-230, :248 |
| CHARACTER_RENAMED | move `character_attachments[old]` to `[new]` | :237-244, :249 |

**Macros:** none.

**UI**
- manage-button.html "Open Data Bank" (`#manageAttachments`, icon fa-book-open-reader) is appended to `#data_bank_wand_container` (public/scripts/extensions/attachments/manage-button.html:3-6; public/scripts/extensions/attachments/index.js:250-252).
- attach-button.html "Attach a File" (`#attachFile`, icon fa-paperclip) is appended to `#attach_file_wand_container` (public/scripts/extensions/attachments/attach-button.html:1-4; public/scripts/extensions/attachments/index.js:251-253).
- The manager popup (manager.html) has:
  - search and sort by date, name or size, ascending or descending (manager.html:19-36);
  - bulk edit: select all, select none, disable, enable, delete (:42-66);
  - three sections, Global / Character / Chat, each with an Add button (:72-124).
- Other templates: notepad.html, web-scrape.html, fandom-scrape.html, mediawiki-scrape.html, youtube-scrape.html, files-dropped.html, move-attachment.html (files in the extension folder).

---

### 2.3 caption (Image Captioning)

**Manifest** (public/scripts/extensions/caption/manifest.json)

| field | value | line |
|---|---|---|
| display_name | "Image Captioning" | :2 |
| loading_order | 4 | :3 |
| requires | [] | :4 |
| optional | ["caption"] (Extras module) | :5-7 |
| js / css | index.js / style.css | :8-9 |

**Purpose:** Captions uploaded or chat images (and video for some APIs). The caption is wrapped in a template and sent as a user message, or written back into the existing message's media title.

**Settings** (`extension_settings.caption.*`)

| key | default | meaning | file:line |
|---|---|---|---|
| source | 'extras' if unset (migration); legacy `local:true` becomes 'local'; 'openai' becomes 'multimodal' | 'local' / 'multimodal' / 'extras' / 'horde' | public/scripts/extensions/caption/index.js:31-46; options in public/scripts/extensions/caption/settings.html:10-14 |
| multimodal_api | 'openai' | multimodal provider | public/scripts/extensions/caption/index.js:53-55 |
| multimodal_model | 'gpt-5.6-luna' | model id or a special token (see below) | :57-59 |
| prompt | "What's in this image?" | caption prompt (multimodal only) | :19, :61-63 |
| template | `[{{user}} sends {{char}} a picture that contains: {{caption}}]` | message wrapper; `{{caption}}` is appended if missing | :20, :65-67, :105-113 |
| show_in_chat | false | show the image inline in the sent message | :69-71, :213, :708-709 |
| refine_mode | false (extensions.js:155) | edit-before-send popup | public/scripts/extensions/caption/index.js:115-125, :400-401 |
| prompt_ask | undefined (falsy) | ask for a custom prompt each time (multimodal) | :327-333, :674 |
| auto_mode | undefined | auto-caption uploaded media on MESSAGE_SENT / MESSAGE_FILE_EMBEDDED | :678, :726-756 |
| allow_reverse_proxy | undefined | use the chat-completion reverse proxy for openai, anthropic, google, mistral, vertexai, xai, zai, moonshot | :670; public/scripts/extensions/shared.js:17-21 |
| alt_endpoint_enabled / alt_endpoint_url | undefined | secondary server URL for ollama, llamacpp, ooba, koboldcpp, vllm | public/scripts/extensions/caption/index.js:700-706; public/scripts/extensions/shared.js:77-108 |
| ollama_custom_model | '' | tag used with `ollama_custom` | public/scripts/extensions/caption/index.js:712-713; public/scripts/extensions/shared.js:73-75 |
| custom_model | '' | model used with `custom_custom` | public/scripts/extensions/caption/index.js:716-717; public/scripts/extensions/shared.js:115-117 |

- Google and Vertex model migration map: `gemini-3.1-flash-lite-preview` becomes `gemini-3.1-flash-lite`, and so on (public/scripts/extensions/caption/index.js:21-25, :48-51).

**Caption sources**

| source | how it works | endpoint | file:line |
|---|---|---|---|
| local | server transformers.js image-to-text; default model `Xenova/vit-gpt2-image-captioning` (config `extensions.models.captioning`) | POST /api/extra/caption `{image}` | public/scripts/extensions/caption/index.js:283-296; src/transformers.js:26-29; src/server-startup.js:174 |
| extras | deprecated Extras server, `modules` must include 'caption' | `<extras>/api/caption` via doExtrasFetch | public/scripts/extensions/caption/index.js:253-276 |
| horde | AI Horde | POST /api/horde/caption-image | :303-316; src/endpoints/horde.js:235 |
| multimodal | `getMultimodalCaption()` in shared.js | see the table below | :324-339; public/scripts/extensions/shared.js:16-163 |

Dispatch happens in `doCaptionRequest` (public/scripts/extensions/caption/index.js:233-245).

**Multimodal APIs** (settings.html:19-42) and how each is routed (public/scripts/extensions/shared.js:137-148):
- Google AI Studio (`google`) and Vertex (`vertexai`) go to /api/google/caption-image.
- `anthropic` goes to /api/anthropic/caption-image.
- `ollama` goes to /api/backends/text-completions/ollama/caption-image.
- Everything else (openai, openrouter, mistral, groq, cohere, xai, zai, moonshot, aimlapi, nanogpt, chutes, electronhub, pollinations, workers_ai, custom, llamacpp, ooba, koboldcpp, vllm) goes to /api/openai/caption-image.

Request body is `{image, prompt, reverse_proxy, proxy_password, api, model}`, plus per-API extras: Vertex auth/region/project (shared.js:52-66), `server_url` for local backends (:68-108), custom URL/headers/body (:110-123), `zai_endpoint` (:125-127), `pollinations_endpoint` (:129-131), `workers_ai_account_id` (:133-135).

Image preprocessing (shared.js:33-47):
- Thumbnailed to max 2048 px when google, openrouter, mistral, groq or vertexai receive more than 2 MB.
- Always thumbnailed for ooba and koboldcpp.
- Converted when the MIME type is not jpeg, png or webp.
- The `data:` prefix is stripped for Ollama.

Key and URL validation errors are listed in `throwIfInvalidModel` (shared.js:165-298).

**Models**
Static options in settings.html:54-184, with `data-type` set to the API:
- cohere: c4ai-aya-vision-8b/32b, command-a-vision-07-2025.
- openai: gpt-6-astra, gpt-5.6 (and sol/terra/luna), gpt-5.5, 5.4 (mini, nano), 5.3/5.2/5.1/5 (and chat-latest, mini, nano), gpt-4.1 (mini, nano), gpt-4-turbo, gpt-4o, gpt-4o-mini, o1, o3, o4-mini.
- anthropic: claude-fable-5-1, claude-fable-5, claude-opus-5, claude-sonnet-5, claude-opus-4-8/4-7/4-6/4-5, claude-sonnet-4-6/4-5, claude-haiku-4-5, claude-opus-4-0, claude-sonnet-4-0 (with dated variants).
- google and vertexai: gemini-3.7/3.6/3.5-flash, 3.5-flash-lite, 3.1-pro-preview, 3.1-flash-lite, 3.1-flash-image, 3-pro-image, 3-flash-preview, 2.5-pro/flash/flash-lite/flash-image, 2.0-flash* (deprecated). Google only: learnlm-2.0-flash-experimental, gemini-robotics-er-1.5-preview, gemma-4-31b-it, gemma-4-26b-a4b-it, gemma-3-27b/12b/4b-it.
- groq: llama-4-scout, llama-4-maverick.
- ollama: `ollama_current`, `ollama_custom`, bakllava, llava, llava-llama3, llava-phi3, moondream, gemma3, minicpm-v, qwen2.5vl, granite3.2-vision, mistral-small3.1/3.2, llama3.2-vision, llama4.
- zai: glm-5v-turbo, glm-4.6v (flashx, flash), glm-4.5v, autoglm-phone-multilingual.
- Special tokens: `llamacpp_current`, `ooba_current`, `koboldcpp_current`, `vllm_current`, `custom_current`, `custom_custom`.

Dynamic model lists are fetched with POST and appended to the dropdown (public/scripts/extensions/caption/index.js:596-638) from these endpoints:
- /api/openrouter/models/multimodal
- /api/backends/chat-completions/multimodal-models/{aimlapi, pollinations, nanogpt, chutes, electronhub, mistral, xai, moonshot, workers_ai}

Availability checks:
- Video captioning only works for multimodal google, vertexai or zai (:462-468).
- The wand button checks keys, reverse proxy and server URLs before opening the file picker (:480-557).

**Other endpoints:**
- `saveBase64AsFile` writes to POST /api/images/upload under the `name2` folder (public/scripts/extensions/caption/index.js:386; public/scripts/utils.js:1651-1661).
- `fetch(mediaAttachment.url)` re-reads existing chat media (:155, :433).

**Slash commands**

| name | aliases | args | purpose | line |
|---|---|---|---|---|
| caption | none | `quiet` (bool, default false), `mesId` (number), `index` (number, media index from 0); unnamed `prompt` (optional) | captions an uploaded image, or one from a message; returns the caption | public/scripts/extensions/caption/index.js:780-820 (callback :410-457) |

**Events**

| event | handler | line |
|---|---|---|
| MESSAGE_SENT | `onMessageEvent`: auto-captions `extra.media[]` with source UPLOAD that are not yet captioned, when `auto_mode` is on | public/scripts/extensions/caption/index.js:726-755 |
| MESSAGE_FILE_EMBEDDED | same | :756 |

**Macros:** none. The template uses the dynamic macro `{{caption}}` through `substituteParams` (:113).

**UI**
- settings.html is appended to `#caption_container` (:591-594).
- Wand button "Generate Caption" (`#send_picture`, fa-image) goes into `#caption_wand_container` (:471-478).
- A hidden form `#img_form` / `#img_file` (accept image/*,video/*) is added to `#form_sheld` (:560-572).
- Message button `.mes_img_caption` re-captions an existing media item (:758-778).
- Refine popup (:116-120), prompt-ask popup (:328).
- "Refresh models" button `#caption_refresh_models` (:720-724).
- The "ollama pull" link opens core `#ollama_download_model` (:681-688).
- `body` gets class `caption` (:822).

What a caption produces:
- A new message: `{is_user:true, mes:wrapped, extra:{media:[{url,type,title,captioned:true,source:CAPTIONED}], media_display:GALLERY, media_index:0, inline_image:show_in_chat}}` (:190-223).
- Captioning an existing message: if `mes` is empty, `mes` becomes the caption; otherwise `media.title` is set with `append_title:true` and `inline_image=true` (:168-180).

---

### 2.4 connection-manager (Connection Profiles)

**Manifest** (public/scripts/extensions/connection-manager/manifest.json)

| field | value | line |
|---|---|---|
| display_name | "Connection Profiles" | :2 |
| loading_order | 1 (loads first) | :3 |
| requires / optional | [] / [] | :4-5 |
| js / css | index.js / style.css | :6-7 |

**Purpose:** Saves and restores snapshots of API, model, preset and template settings. A profile is captured by running core slash commands with no argument (read) and applied by running them with the stored value (write).

**Settings** (`extension_settings.connectionManager.*`)

| key | default | meaning | file:line |
|---|---|---|---|
| profiles | [] | `ConnectionProfile[]` | public/scripts/extensions/connection-manager/index.js:27-30, :699-705 |
| selectedProfile | null | id of the active profile | same |

**Profile fields** (typedef at public/scripts/extensions/connection-manager/index.js:159-182)
- Core fields: `id` (uuid), `mode` ('cc' when `main_api==='openai'`, otherwise 'tc'), `name`, `exclude[]` (:258-266).
- Each other field is the return value of the core slash command of the same name.

| field key = slash cmd | display name | CC | TC | line |
|---|---|---|---|---|
| api | API | yes (run twice; the preset may override it) | yes | :38-42, :55 |
| preset | Settings Preset | yes | yes | :40, :56 |
| api-url | Server URL | yes | yes | :43, :57 |
| model | Model | yes | yes | :44, :58 |
| proxy | Proxy Preset | yes | no | :45 |
| sysprompt | System Prompt Name | no | yes | :59 |
| sysprompt-state | Use System Prompt | no | yes | :60 |
| instruct | Instruct Template | no | yes | :61 |
| context | Context Template | no | yes | :62 |
| instruct-state | Instruct Mode | no | yes | :63 |
| tokenizer | Tokenizer | no | yes | :64 |
| stop-strings | Custom Stopping Strings (empty allowed) | yes | yes | :46, :65, :33-36 |
| start-reply-with | Start Reply With (empty allowed) | yes | yes | :47, :66 |
| reasoning-template | Reasoning Template | yes | yes | :48, :67 |
| prompt-post-processing | Prompt Post-Processing | yes | no | :49 |
| secret-id | Secret | yes | yes | :50, :68 |
| regex-preset | Regex Preset | yes | yes | :51, :69 |

Display names are defined in FANCY_NAMES (:72-90).

Behaviour:
- Read: every command is called with `quiet:'true'` (:140-151, :215-235).
- `cleanUp` on update: when `*-state` is 'false', the paired value is deleted, and fields that belong only to the other mode are deleted (:237-250).
- Apply: the list is applied in order. Empty values are skipped unless the field is in ALLOW_EMPTY, which is applied with `force:'true'`. A spinner shows, and the apply aborts if a new apply starts (:392-424, :95-132).
- Name: the suggested name is `"<api> <model> - <preset>"`, made unique. A name equal to `<None>` or to an existing name is rejected (:292-308).
- Lookup by name tries an exact match, then a Fuse fuzzy match (:189-207).

**Server endpoints:** none directly. All work happens through core slash commands.

**Slash commands**

| name | aliases | args | purpose | line |
|---|---|---|---|---|
| profile | none | unnamed name (enum of profiles plus `<None>`); `await` (bool, default true); `timeout` (ms, default 2000) | switch profile, or return the current name; `<None>` deselects; waits for CONNECTION_PROFILE_LOADED and `online_status` | public/scripts/extensions/connection-manager/index.js:893-962 |
| profile-list | none | none | JSON array of names | :964-969 |
| profile-create | none | unnamed name (required) | create from current settings and select it | :971-999 |
| profile-update | none | none | re-read the current settings into the selected profile | :1001-1018 |
| profile-get | none | unnamed name (optional) | JSON of the profile, the selected one by default | :1020-1047 |
| profile-genstream | none | `lock` (default off), `profile` (id or name), `reasoning` (default false), `system`, `length` (default 2048), `generating` ("Generating..."), `completed` ("Generated"), `delay` (ms, default 3000; "infinite" or negative keeps it open), `stop` (default true), `onStop` (closure), `onComplete` (closure); unnamed prompt (required) | streaming generation through a profile with a StreamingDisplay overlay | :1049-1157 (callback :489-697) |

**Events emitted:**
- CONNECTION_PROFILE_LOADED with name or `<None>` (:727, :740, :752, :765, :781, :797, :806).
- CONNECTION_PROFILE_CREATED (:780, :996).
- CONNECTION_PROFILE_UPDATED (old, new) (:796, :879, :1015).
- CONNECTION_PROFILE_DELETED (:347).

**Events listened:** only `eventSource.once(CONNECTION_PROFILE_LOADED)` inside `/profile` (:944). **Macros:** none.

**UI**
- settings.html is inserted at the start of `#rm_api_block`, the API Connections panel rather than the Extensions panel (:707-709).
- It contains `#connection_profiles` (select, with `<None>` first, sorted by name) (:440-460), plus view, create, update, edit, reload and delete buttons (public/scripts/extensions/connection-manager/settings.html:12-18).
- A spinner `#connection_profile_spinner` (:9) and a details pane `#connection_profile_details_content` that uses view.html (:20).
- Create popup profile.html: checkboxes per field (uncheck to exclude) and a name input (public/scripts/extensions/connection-manager/profile.html:1-22).
- Edit popup edit.html: included-settings checkboxes and a name field, with a "Save and Update" custom button (public/scripts/extensions/connection-manager/edit.html:1-12; index.js:809-882).
- Update, reload and delete are disabled when no profile is selected (:716-721).

---

### 2.5 expressions (Character Expressions)

**Manifest** (public/scripts/extensions/expressions/manifest.json)

| field | value | line |
|---|---|---|
| display_name | "Character Expressions" | :2 |
| loading_order | 6 | :3 |
| requires | [] | :4 |
| optional | ["classify"] (Extras) | :5-7 |
| js / css | index.js / style.css | :8-9 |

**Purpose:** Classifies the emotion of the last character message and shows the matching sprite image. In group chats it can show all members at once (VN mode).

**Default expression labels (28)** (public/scripts/extensions/expressions/index.js:46-75): admiration, amusement, anger, annoyance, approval, caring, confusion, curiosity, desire, disappointment, disapproval, disgust, embarrassment, excitement, fear, gratitude, grief, joy, love, nervousness, optimism, pride, realization, relief, remorse, sadness, surprise, neutral.
- The default fallback is `joy` (:44).
- Special values: `#none` (no fallback image), `#emoji` (default emoji images), `#reset` (clear the sprite) (:77-79).

**Classification sources** (`EXPRESSION_API` at :83-89; the select in public/scripts/extensions/expressions/settings.html:24-29)

| value | name | method | file:line |
|---|---|---|---|
| 0 | local | POST /api/extra/classify `{text}`, returns `classification[0].label`; labels from POST /api/extra/classify/labels; server model `Cohee/distilbert-base-uncased-go-emotions-onnx` | public/scripts/extensions/expressions/index.js:1111-1122, :1503-1514; src/transformers.js:20-23 |
| 1 | extras (deprecated) | `<extras>/api/classify`, labels from `<extras>/api/classify/labels` | :1169-1186, :1486-1500 |
| 2 | llm (Main API) | `promptType` 'raw' uses `generateRaw({prompt:text, systemPrompt})`; 'full' uses `generateQuietPrompt({quietPrompt})`. Waits up to 3 s for a connection. When supported, a JSON schema `{emotion: enum}` plus `top_k:1` is injected on TEXT_COMPLETION_SETTINGS_READY. The response is parsed as JSON, then Fuse fuzzy match, then substring match | :1124-1151, :999-1079 |
| 3 | webllm | `generateWebLlmChatPrompt([{role:'user', content: text+'\n\n'+prompt}])` | :1153-1167 |
| 99 | none | returns '', so the fallback is used | :1188-1191 |

Classification details:
- Input text is sampled first: macros substituted, `*` and `"` stripped. For non-LLM sources, text over 500 characters is trimmed to its first and last 250 characters at sentence boundaries (:970-992).
- Optional pre-translation to English through `globalThis.translate`, provided by the translate extension (:1097-1099).
- The label list comes from the API for local and extras, otherwise the default list plus custom expressions (:1456-1523).
- `filterAvailable` (LLM and WebLLM only) limits labels to expressions that have sprites (:1464-1476).

**Settings** (`extension_settings.expressions.*` plus the global `expressionOverrides`)

| key | default | meaning | file:line |
|---|---|---|---|
| api | none (99) via migration | classifier source | public/scripts/extensions/expressions/index.js:2219-2222; public/scripts/extensions.js:159 |
| local | (legacy) | if true, migrated to api=local, then deleted | public/scripts/extensions/expressions/index.js:2224-2231 |
| custom | [] | custom expression labels: a-z, 0-9, `-`, `_`; cannot equal or start with a default label | :1397-1400, :1760-1794; extensions.js:161 |
| showDefault | false | use default emojis as fallback (`#emoji`) | :1849-1858, :2243-2246; extensions.js:162 |
| translate | false | translate to English before classifying | :2279-2282; extensions.js:163 |
| fallback_expression | undefined | fallback label (null for none or emoji) | :1845-1858; extensions.js:165 |
| llmPrompt | `Ignore previous instructions. Classify the emotion of the last message. Output just one word, e.g. "joy" or "anger". Choose only one of the following labels: {{labels}}` | LLM and WebLLM prompt; `{{labels}}` is a quoted, comma-joined list | :45, :2233-2236, :2309-2318 |
| allowMultiple | true | allow several sprites per label and pick one at random | :2238-2241, :2283-2286, :1582-1589 |
| rerollIfSame | false | avoid repeating the same sprite file | :2287-2290, :1585-1587 |
| filterAvailable | undefined | only offer labels that have sprites (LLM and WebLLM) | :2291-2294 |
| promptType | 'raw' | 'raw' or 'full' (LLM) | :92-95, :2248-2251, :2319-2327 |
| `extension_settings.expressionOverrides[]` | [] | `{name: avatarFileNameWithoutExt, path: overrideFolder}` | :2030-2058, :625-636; extensions.js:147 |

**Sprite folders**
- The sprite folder name defaults to the character's display name (`name2`, or the message name in groups) (:625-628).
- `expressionOverrides` is matched by avatar filename without extension (:629-634, :639-655).
- On the server, the files live in `<user>/characters/<name>/`. A name containing `/` means the subfolder `characters/<char>/<sub>` (src/endpoints/sprites.js:18-38). Each file is served as `/characters/<name>/<file>?t=<mtime>` (src/endpoints/sprites.js:140-143).
- The label is parsed from the filename as `^(.+?)(?:[-.].*?)?$`, so `joy-2.png` and `joy.alt.png` both map to `joy` (src/endpoints/sprites.js:135-138).
- On the client, files are grouped by label; the file named exactly like the label comes first and the rest are typed 'additional' (public/scripts/extensions/expressions/index.js:1350-1390).
- New multi-sprite names follow `<expr>-<n>` (:884-891).
- Default emoji images are at `/img/default-expressions/<expr>.png` (:1719-1727). The missing-image placeholder is `/img/No-Image-Placeholder.svg` (:127-136).

**VN mode** (talkinghead does not exist in this version; there are no references in the extension folder)
- Active when `!isMobile() && power_user.waifuMode && groupId` (:138-140).
- `#visual-novel-wrapper` holds one holder per active group member (:2266-2273, :150-166).
- Inactive or disabled members are removed. Layers are ordered by who spoke most recently (:168-175, :299-320). `hideMutedSprites` comes from group-chats (:8).
- Outside VN mode, a single draggable `#expression-holder` / `#expression-image` is used (:2255-2265, :2344).

**Worker:** `moduleWorker` runs every 2000 ms (:42, :2340-2343).
- It reclassifies only when the last message changes (:569-574).
- It skips while the LLM is streaming (:577-579).
- During streaming it throttles to every 10000 ms (:43, :587-596).
- It skips if the character has no sprites and `showDefault` is off (:565-567).

**Server endpoints**

| endpoint | purpose | file:line |
|---|---|---|
| GET /api/sprites/get?name= | list sprites | public/scripts/extensions/expressions/index.js:1354 (server src/endpoints/sprites.js:118) |
| POST /api/sprites/upload (FormData name, label, avatar, spriteName) | upload one sprite | :951, :2006 (server sprites.js:239) |
| POST /api/sprites/upload-zip (FormData name, avatar=zip) | upload a sprite pack | :2112-2117 (server sprites.js:186) |
| POST /api/sprites/delete `{name,label,spriteName}` | delete a sprite | :2157-2161 (server sprites.js:152) |
| POST /api/extra/classify, /api/extra/classify/labels | local classifier | :1112, :1504 (server src/endpoints/classify.js:14,25) |
| `<extras>`/api/classify, /api/classify/labels | Extras classifier | :1171, :1488 |
| GET `<imageUrl>` | download for /uploadsprite | :941 |
| GET sprite URLs (no-cache) | cache-bust after upload | :2203-2211 |

**Slash commands**

| name | aliases | args | purpose | line |
|---|---|---|---|---|
| expression-set | sprite, emote | `type` = expression \| sprite (default expression); unnamed label or sprite name (required; `#reset` allowed) | force-set the current expression; returns the label | public/scripts/extensions/expressions/index.js:2397-2429 |
| expression-fallback | none | unnamed label / `#none` / `#emoji` (optional) | get or set the global fallback | :2430-2465 |
| expression-folder-override | spriteoverride, costume | `name` (character); unnamed folder (empty resets; a leading `/` or `\` means a subfolder) | set the sprite folder override | :2466-2494 |
| expression-last | lastsprite | unnamed character name or avatar key | last expression for the character | :2495-2509 |
| expression-list | expressions | `return` (default pipe; object or popup allowed), `filter` (default true), `custom` (true / false / only) | list expressions | :2510-2548 |
| expression-classify | classify | `api` (local/extras/llm/webllm/none), `filter` (default true), `prompt`; unnamed text (required) | classify the text and return a label | :2549-2595 |
| expression-upload | uploadsprite | unnamed URL (required); `name`, `label` (required), `folder`, `spriteName` | upload a sprite from a URL | :2596-2651 |

**Macros:** `{{defaultExpression}}`, `{{lastExpression}}` (or `{{lastExpression::name}}` in the new engine), and `{{availableExpressions}}`.
- With the experimental engine they are registered through `macros.register` (:2653-2697).
- Otherwise they go through `MacrosParser.registerMacro` (:2698-2720).

**Events**

| event | handler | line |
|---|---|---|
| CHAT_CHANGED | clear the sprite, cache and lastExpression, clear the override HTML, empty the VN wrapper, update with newChat:true | :2345-2364 |
| MOVABLE_PANELS_RESET | debounced VN update | :2365 |
| GROUP_UPDATED | debounced VN update | :2366 |
| TEXT_COMPLETION_SETTINGS_READY (once per LLM call) | inject the JSON schema | :1134, :1067-1079 |

**UI**
- settings.html is appended to `#expressions_container` (:2274-2276).
- It contains:
  - checkboxes: translate (settings.html:10), allow multiple (:14), reroll if same (:18), filter available (:34);
  - classifier select (:24-29);
  - LLM prompt textarea and restore button (:39-44), prompt type radios (:51-55);
  - fallback select (:62), custom expression select with add and remove buttons (:68-70);
  - override folder input and submit (:83-84), upload sprite pack button (:87), remove all overrides button (:91);
  - sprite set list `#image_list` with per-item upload and delete (:110-113);
  - hidden inputs `#expression_upload_pack` (zip) and `#expression_upload` (image/*) (:119-120).
- Popups: add-custom-expression.html, remove-custom-expression.html, templates/upload-expression.html (for a name conflict when allowMultiple is on, :1945-1960), and a delete confirmation (:2146-2148).
- Clicking a list item sets that sprite (:2300).

---

### 2.6 gallery

**Manifest** (public/scripts/extensions/gallery/manifest.json)

| field | value | line |
|---|---|---|
| display_name | "Gallery" | :2 |
| loading_order | 6 | :3 |
| requires / optional | [] / [] | :4-6 |
| js / css | index.js / style.css | :7-8 |
| version | 1.5.0 (author City-Unit) | :9-10 |

**Purpose:** An image and video gallery for each character or group, backed by `user/images/<folder>/`. Supports drag-and-drop upload, delete mode, sorting and folder override. It uses nanogallery2, lazy-loaded the first time it opens (public/scripts/extensions/gallery/index.js:310-321).

**Settings** (`extension_settings.gallery.*`)

| key | default | meaning | file:line |
|---|---|---|---|
| folders | {} | `{[avatar]: folderName}` override; the default folder is the character name | public/scripts/extensions/gallery/index.js:70-73, :101-103, :569-616 |
| sort | 'dateAsc' | one of nameAsc, nameDesc, dateDesc, dateAsc | :63-68, :191-203 |

The folder for a group is the group id (:325-328).

**Server endpoints**

| endpoint | body | purpose | file:line |
|---|---|---|---|
| POST /api/images/list | `{folder, sortField, sortOrder, type: IMAGE\|VIDEO}` | list files; items become `user/images/<folder>/<file>`; video thumbnails are made client-side | public/scripts/extensions/gallery/index.js:112-152 (server src/endpoints/images.js:80) |
| POST /api/images/folders | none | folder autocomplete | :158-174 (server images.js:115) |
| POST /api/images/delete | via `deleteMediaFromServer` | delete an item | :180-185; public/scripts/chats.js:1099 |
| POST /api/images/upload | via `saveBase64AsFile` | drag-drop or "Add Image" upload | :350-365; public/scripts/utils.js:1661 |

**Slash commands** (registered at module load, outside `init`)

| name | aliases | args | purpose | line |
|---|---|---|---|---|
| show-gallery | sg | none | open the gallery | public/scripts/extensions/gallery/index.js:754-762 |
| list-gallery | lg | `char` (name), `group` (name) | JSON array of image `src` paths | :763-798 |

**Events**

| event | handler | line |
|---|---|---|
| CHARACTER_RENAMED | move `folders[old]` to `folders[new]` | :823-831 |
| CHARACTER_DELETED | delete `folders[avatar]` | :832-838 |
| CHARACTER_MANAGEMENT_DROPDOWN | `'show_char_gallery'` opens the gallery | :839-843 |
| 'resizeUI' (custom), CHAT_CHANGED (once), 'galleryRemoved' (once) | resize and close | :273-279, :46 |

**Macros:** none.

**UI**
- Wand button "Show Gallery" (`#show_gallery_wand_button`, fa-sd-card) goes into `#gallery_wand_container`, falling back to `#extensionsMenu` (:800-818).
- An option `#show_char_gallery` is added to `#char-management-dropdown` (:846-851).
- The draggable window is cloned from `#generic_draggable_template` (:375).
- Window controls: sort select (:394-412), "Add Image" button (:415-418), folder input with autocomplete (:490-540), accept (fa-check), delete mode toggle (fa-trash), restore folder (fa-recycle) (:497-522).
- Clicking an image opens it in a draggable viewer (`makeDragImg`), or deletes it when delete mode is on (:733-750).
- Paging: thumbnail height 150, max rows `clamp(floor((innerHeight*0.9-75)/150),1,10)` (:216-219).
- i18n stub: index.i18n.html contains only "Show Gallery".

---

### 2.7 memory (Summarize)

**Manifest** (public/scripts/extensions/memory/manifest.json)

| field | value | line |
|---|---|---|
| display_name | "Summarize" | :2 |
| loading_order | 9 | :3 |
| requires | [] | :4 |
| optional | ["summarize"] (Extras) | :5-7 |
| js / css | index.js / style.css | :8-9 |

**Purpose:** Keeps a rolling summary of the chat. The summary is stored on chat messages (`message.extra.memory`) and injected as an extension prompt under the key `1_memory` (public/scripts/extensions/memory/index.js:36, :964-965).

**Summary sources** (`summary_sources` at :93-97; select in public/scripts/extensions/memory/settings.html:13-16)

| source | method | file:line |
|---|---|---|
| main | `prompt_builder`: DEFAULT(0) uses `generateQuietPrompt({quietPrompt, skipWIAN, responseLength})`; RAW_BLOCKING(1) and RAW_NON_BLOCKING(2) use `generateRaw({prompt: chat buffer, systemPrompt, responseLength})`, and blocking also disables send buttons; reasoning is stripped | :681-753, :99-103 |
| extras (deprecated) | `<extras>/api/summarize` `{text, params:{}}`; GPT-2 token counting; context 1024-64 | :896-923, :54-57, :70-72, :827-889 |
| webllm | `generateWebLlmChatPrompt([system: prompt, user: rawPrompt], {max_tokens})` | :628-679 |

Raw prompt building (`getRawSummaryPrompt`):
- Starts from the latest summary, then appends `name:\nmes` messages after the last summarized index, excluding the last message and system messages.
- Stops at the context size (padding 64) or at `maxMessagesPerRequest`.
- Returns `lastUsedIndex`, and the summary is saved on that message (:761-825).

**Trigger / frequency logic** (`getSummaryPromptForNow`, :565-626)
- Skipped if `promptInterval` is 0, unless forced.
- Waits for group generation to finish and for `is_send_press` (up to 30 s).
- Needs `chat.length >= promptInterval`.
- Counts messages and words since the last `extra.memory`. It fires when messages ≥ `promptInterval`, or when `promptForceWords` > 0 and words ≥ `promptForceWords`.
- `{{words}}` in the prompt is replaced by `promptWords`.

`onChatEvent` (:417-471):
- Skips if the source is unavailable, streaming is in progress, a call is already running, or `memoryFrozen` is set.
- If messages were deleted, it restores the latest stored memory.
- If the last message was edited or swiped, it deletes that message's stored memory.

**Settings** (`extension_settings.memory.*`; defaults at :108-139, filled by `loadSettings` at :141-150)

| key | default | meaning | file:line |
|---|---|---|---|
| memoryFrozen | false | pause automatic updates | :109, :260 |
| SkipWIAN | false | omit WI and Author's Note (main source) | :110, :266, :485 |
| source | 'extras' | main / extras / webllm | :111, :246 |
| prompt | defaultPrompt ("Ignore previous instructions. Summarize the most important facts and events in the story so far. … Limit the summary to {{words}} words or less. …") | summary request | :105, :112 |
| template | `[Summary: {{summary}}]` | injection wrapper; if empty, `Summary: <v>` is used | :106, :113, :77-89 |
| position | 0 (IN_PROMPT, after main prompt) | -1 none, 0 after main prompt / story string, 2 before main prompt, 1 in-chat @ depth | :114; public/script.js:484-489; settings.html:121-135 |
| role | 0 SYSTEM | role for in-chat injection (0 system, 1 user, 2 assistant) | :115; public/script.js:494-498; settings.html:137-140 |
| scan | false | include the summary in WI scanning | :116, :324 |
| depth | 2 | in-chat depth | :117, :303 |
| promptWords | 200 (range 25-1000, step 25) | target words (`{{words}}`) | :118-121 |
| promptInterval | 10 (range 0-250, step 1; 0 disables auto) | update every N messages | :122-125 |
| promptForceWords | 0 (range 0-10000, step 100) | update every N words | :126-129 |
| overrideResponseLength | 0 (range 0-4096, step 16) | API response length in tokens (0 means default) | :130-133 |
| maxMessagesPerRequest | 0 (range 0-250; 0 unlimited) | Raw/WebLLM batch cap | :134-137 |
| prompt_builder | 0 DEFAULT | 0 default / 1 raw blocking / 2 raw non-blocking | :138 |

Auto-adjust buttons estimate the interval or word values from chat metrics (`onPromptForceWordsAutoClick` :170-206, `onPromptIntervalAutoClick` :208-244).

**Per-chat data:** `chat[i].extra.memory = summary` (:974-984). The latest summary is found by scanning backwards (:357-397). "Restore" deletes the current summary from its message and goes back to the previous one (:925-940).

**Server endpoints:** only Extras `<extras>/api/summarize` (:901-914). The main source uses core generation; there are no direct `/api` calls.

**Slash commands**

| name | aliases | args | purpose | line |
|---|---|---|---|---|
| summarize | none | `source` (extras/main/webllm), `prompt`, `quiet` (default false); unnamed text (optional) | summarize the given text, or force-summarize the current chat when no text is given (not supported for extras) | public/scripts/extensions/memory/index.js:1084-1108 (callback :507-539, force :478-500) |

**Macros:** `{{summary}}` returns the UI summary box content, or else the latest chat memory. Registered with `macros.register` (experimental engine, category CHAT) or `MacrosParser.registerMacro` (:1110-1130).

**Events**

| event | handler | line |
|---|---|---|
| CHAT_CHANGED | load the latest memory into the prompt | :1079, :411-415 |
| CHARACTER_MESSAGE_RENDERED (makeLast) | onChatEvent | :1080 |
| MESSAGE_DELETED, MESSAGE_UPDATED, MESSAGE_SWIPED | onChatEvent | :1081-1083 |

**UI**
- settings.html is appended to `#summarize_container` (:1066-1069). It contains:
  - popout button `#summaryExtensionPopoutButton` (settings.html:6; `doPopout` :987-1034);
  - source select (:13), summary textarea `#memory_contents` (:28), restore button (:23);
  - "Summarize now" `#memory_force_summarize` (main and webllm) (:30), Pause (:34), Skip WI/AN (:35-36);
  - settings drawer toggle (:41-46): prompt builder radios (:52-60), prompt and restore (:67-71), sliders (:73-106) with auto buttons (:92, :102), template (:112), WI scan (:116), position radios and depth (:121-135), role select (:137-140).
- Controls are shown or hidden per source through `data-summary-source` (:253-258).

---

### 2.8 quick-reply (Quick Replies v2)

**Manifest** (public/scripts/extensions/quick-reply/manifest.json)

| field | value | line |
|---|---|---|
| display_name | "Quick Replies" | :2 |
| loading_order | 12 | :3 |
| requires / optional | [] / [] | :4-5 |
| js / css | index.js / style.css (generated from style.less) | :6-7 |
| version | 2.0.0 | :9 |

**Purpose:** User-defined buttons, grouped into sets, that send text or run STscript. Sets can be attached at global, chat or character scope. The extension also provides auto-execute triggers, nested context menus, a script editor with a debugger, a JS API (`globalThis.quickReplyApi`), and slash commands.

**Data model**

*QuickReplySet* (public/scripts/extensions/quick-reply/src/QuickReplySet.js). Each set is one server file, saved through `/api/quick-replies/save` and loaded from `settings/get → quickReplyPresets`.

| field | default | meaning | line |
|---|---|---|---|
| version | 2 | schema version | :364 |
| name | none | unique set name (file key) | :30 |
| disableSend | false | insert into the input instead of sending (not for `/` commands) | :32, :163, :191-194 |
| placeBeforeInput | false | when injecting, put the QR before the user input | :33, :154-158 |
| injectInput | false | auto-append the current input (otherwise use `{{input}}`) | :34, :153 |
| color | 'transparent' | button color (CSS var `--qr--color`) | :35, :77-92 |
| onlyBorderColor | false | apply the color as an accent only | :36, :82-86 |
| qrList | [] | QuickReply[] | :37 |
| idIndex | 0 | id counter | :38, :211-212 |
| scope (runtime) | 'global' | not serialized | :31 |

*QuickReply* (public/scripts/extensions/quick-reply/src/QuickReply.js; toJSON at :1902-1922)

| field | default | meaning | line |
|---|---|---|---|
| id | none | numeric id within the set | :30 |
| icon | undefined | FontAwesome class | :31 |
| showLabel | false | show the label when an icon is set | :33 |
| label | '' | button text, also the lookup key | :32 |
| title | '' | tooltip (falls back to the message) | :34, :118 |
| message | '' | text or STscript | :35 |
| contextList | [] | `QuickReplyContextLink {set, isChained}` | :37; src/QuickReplyContextLink.js:11-19 |
| preventAutoExecute | true | block nested auto-execution while this QR runs | :39; src/AutoExecuteHandler.js:16-31 |
| isHidden | false | invisible (auto-execute or library only) | :40 |
| executeOnStartup | false | trigger: app startup (APP_READY) | :41 |
| executeOnUser | false | trigger: user message | :42 |
| executeOnAi | false | trigger: AI message | :43 |
| executeOnChatChange | false | trigger: chat change | :44 |
| executeOnGroupMemberDraft | false | trigger: group member drafted | :45 |
| executeOnNewChat | false | trigger: new chat | :46 |
| executeBeforeGeneration | false | trigger: before generation | :47 |
| automationId | '' | runs when a World Info entry with the same automationId activates | :48; src/AutoExecuteHandler.js:85-103 |

V1 sets (no `version`) are migrated: `quickActionEnabled` becomes disableSend, `placeBeforeInputEnabled` becomes placeBeforeInput, `AutoInputInject` becomes injectInput, `quickReplySlots[]` become a qrList, each `autoExecute_*` maps to the matching `execute*`, and `contextMenu[{preset,chain}]` becomes contextList (public/scripts/extensions/quick-reply/index.js:64-92).

**Settings** (`extension_settings.quickReplyV2`; serialized at src/QuickReplySettings.js:89-105)

| key | default | meaning | file:line |
|---|---|---|---|
| isEnabled | false | master switch (buttons and auto-exec) | public/scripts/extensions/quick-reply/index.js:31; src/QuickReplySettings.js:18 |
| isCombined | false | merge all sets into one button row | index.js:32; QuickReplySettings.js:19 |
| isPopout | false | show the buttons in a floating draggable popout instead of above `#send_form` | QuickReplySettings.js:20; src/ui/ButtonUi.js:18-46 |
| showPopoutButton | true | show the popout trigger (desktop) | QuickReplySettings.js:21; ButtonUi.js:65-79 |
| config.setList[] | `[{set:'Default', isVisible:true}]` | global set links `{set: name, isVisible}` | index.js:23-28; src/QuickReplySetLink.js:115-120 |
| characterConfigs | {} | `{[avatar]: {setList[]}}` (private to the character, local only; empty ones are not saved) | QuickReplySettings.js:23, :90-96; index.js:143-170 |

- Chat-scope sets are stored in `chat_metadata.quickReply = {setList[]}` (QuickReplySettings.js:74-77; index.js:262-266).
- Legacy migration: from `extension_settings.quickReply.{quickReplyEnabled, selectedPreset|name}` (index.js:106-123).

Execution order:
- Buttons, auto-exec lookup and `/run` all use global sets, then chat sets, then character sets (src/ui/ButtonUi.js:87-90; src/AutoExecuteHandler.js:35-45; index.js:185-193).
- `/qr <index>` only counts global and chat sets (api/QuickReplyApi.js:58-68).

**Execution semantics** (`QuickReplySet.executeWithOptions`, src/QuickReplySet.js:139-195)
- Input composition:
  - Manual click with `injectInput` on and non-empty input: `msg + ' ' + input` if `placeBeforeInput`, otherwise `input + ' ' + msg`.
  - Any other case: `msg + ' '`.
- If the text starts with `/` and `disableSend` is false, it runs as STscript:
  - auto-execute or run: `executeSlashCommandsWithOptions`;
  - editor: with abort and progress;
  - manual click: `executeSlashCommandsOnChatInput`;
  - the source is `"<set>.<label>"`, and the pipe value is returned.
- Otherwise `#send_textarea` is set to `substituteParams(input)`, and `#send_but` is clicked unless `disableSend`.
- Arguments: `qr.execute(args)` sets scope macros `{{arg::key}}` for every key not starting with `_` (excluding `isAutoExecute`) and `{{arg::*}}=''` (src/QuickReply.js:1878-1899). `/qr-arg` sets fallbacks.
- `globalThis.executeQuickReplyByName(name, args, options)` backs `/run` and `/:`. It finds a label among active sets, then falls back to `Set.Label` (index.js:184-207).

**Auto-execute triggers** (src/AutoExecuteHandler.js; event wiring in index.js)
- Every trigger requires `settings.isEnabled`, and is suppressed while a parent QR with `preventAutoExecute` is running (AutoExecuteHandler.js:16-31).
- Events that arrive before APP_READY are queued and run after the startup QRs (index.js:133-141, :219-232).

| flag | event | handler | line |
|---|---|---|---|
| executeOnStartup | APP_READY → finalizeInit | handleStartup | index.js:214, :221; AutoExecuteHandler.js:47-50 |
| executeOnUser | USER_MESSAGE_RENDERED (makeFirst) | handleUser | index.js:279-282; :52-55 |
| executeOnAi | CHARACTER_MESSAGE_RENDERED (makeFirst); skipped if the message is '...' (swipe placeholder) | handleAi | index.js:284-292; :57-60 |
| executeOnChatChange | CHAT_CHANGED (after loading the chat and character configs) | handleChatChanged | index.js:257-275; :62-65 |
| executeOnGroupMemberDraft | GROUP_MEMBER_DRAFTED | handleGroupMemberDraft | index.js:294-297; :67-70 |
| executeOnNewChat | CHAT_CREATED, GROUP_CHAT_CREATED | handleNewChat | index.js:304-308; :72-75 |
| executeBeforeGeneration | GENERATION_AFTER_COMMANDS (skipped for dryRun and the group pre-wrapper call) | handleBeforeGeneration | index.js:310-321; :77-80 |
| automationId | WORLD_INFO_ACTIVATED (entries with a matching automationId) | handleWIActivation | index.js:299-302; :85-103 |

Other listeners:
- CHARACTER_DELETED purges `characterConfigs[avatar]` (index.js:235-243, :276).
- CHARACTER_RENAMED moves the character config to the new avatar (:245-255, :277).

**Context menus** (src/ui/ctx/ContextMenu.js, MenuItem.js, SubMenu.js, MenuHeader.js)
- Opened by right-click on a QR that has a context list, or by its `⋮` expander (src/QuickReply.js:119-127, :150-160).
- Ctrl+click opens the editor (:128-131).
- For each linked set, the menu shows a header and that set's visible QRs, built recursively. Cycles are prevented by a hierarchy check (ContextMenu.js:31-80).
- Chained links (`isChained`) prefix the parent message: `parent | child` (ContextMenu.js:37, :63).
- `%%parent%%` and `%%parent-N%%` in the message are replaced with ancestor labels (ContextMenu.js:73-75).
- If a QR's own set is linked, the menu shows only that set's hidden QRs that have an icon ("burger menu" pattern) (ContextMenu.js:47-60).

**Server endpoints**

| endpoint | purpose | file:line |
|---|---|---|
| POST /api/settings/get | read `quickReplyPresets` (all set files) at init | public/scripts/extensions/quick-reply/index.js:55-63; src/endpoints/settings.js:261,281 |
| POST /api/quick-replies/save (body = set JSON) | save a set (debounced 200 ms) | src/QuickReplySet.js:45, :376-389; server src/endpoints/quick-replies.js:10 |
| POST /api/quick-replies/delete (body = set JSON) | delete a set | src/QuickReplySet.js:391-410; server quick-replies.js:21 |
| GET /scripts/extensions/quick-reply/html/settings.html, qrEditor.html | templates | src/ui/SettingsUi.js:54; src/QuickReply.js:385 |

Renaming a set sets the name and saves; the code calls no delete for the old name (src/ui/SettingsUi.js:333-376).

**Slash commands** (src/SlashCommandHandler.js)

| name | aliases | args | purpose | line |
|---|---|---|---|---|
| qr | none | unnamed number (required) | run the QR at that 0-based index across global and chat sets | :78-86 |
| qrset | none | none | deprecated; only shows a warning | :87-93 |
| qr-set | none | `visible` (default true); unnamed set name | toggle a global set | :94-113 |
| qr-set-on | none | `visible` (default true); unnamed set | add a global set | :114-133 |
| qr-set-off | none | unnamed set | remove a global set | :134-148 |
| qr-chat-set | none | `visible`; unnamed set | toggle a chat set | :149-168 |
| qr-chat-set-on | none | `visible`; unnamed set | add a chat set | :170-189 |
| qr-chat-set-off | none | unnamed set | remove a chat set | :190-204 |
| qr-set-list | none | unnamed all/global/chat (default all) | JSON list of set names | :205-215 |
| qr-list | none | unnamed set (required) | JSON list of QR labels in the set | :216-231 |
| qr-create | none | `set` (required), `label`, `icon`, `showlabel`, `hidden`, `startup`, `user`, `bot`, `load`, `new`, `group`, `generation`, `title`; unnamed command (required) | create a QR | :233-269, :281-303 (impl :854-878) |
| qr-get | none | `set`, `label`, `id` | dictionary of QR properties | :304-344 |
| qr-update | none | `newlabel`, `id`, plus the qr-create args (label optional); unnamed command | update a QR | :270-279, :345-375 |
| qr-delete | none | `set`, `label`, `id`; unnamed label | delete a QR | :376-410 |
| qr-contextadd | none | `set`, `label`, `id`, `chain` (default false); unnamed context set name | add a context-menu link | :411-469 |
| qr-contextdel | none | `set`, `label`, `id`; unnamed context set | remove a context link | :470-520 |
| qr-contextclear | none | `set`, `id`; unnamed label | clear the context menu | :521-564 |
| qr-set-create | qr-presetadd | `nosend`, `before`, `inject`; unnamed name (required) | create a set (overwrites an existing one) | :566-600 (impl :952-965) |
| qr-set-update | qr-presetupdate | `nosend`, `before`, `inject`; unnamed name | update set flags | :602-626 |
| qr-set-delete | qr-presetdelete | unnamed name | delete a set | :627-650 |
| qr-arg | none | unnamed key and value (split into 2) | set a fallback `{{arg::key}}`; wildcard if the key contains `*` | :652-678 |
| import | none | `from` = "Set.Label" or a label (required); unnamed list "x" or "x as y" | import closures assigned with /let or /var from another QR into the current scope | :680-780 |

There are no character-scope slash commands: there is no qr-char-set (only global and chat, :94-204). Errors in handler methods are shown as toasts (:799-1008).

**JS API** (`globalThis.quickReplyApi`, api/QuickReplyApi.js; index.js:209, :216):
- Lookup and run: `getSetByQr`, `getSetByName`, `getQrByLabel`, `executeQuickReplyByIndex`, `executeQuickReply(set, label, args, options)`.
- Set toggles: `toggleGlobalSet`, `addGlobalSet`, `removeGlobalSet`, `toggleChatSet`, `addChatSet`, `removeChatSet`.
- QR editing: `createQuickReply`, `updateQuickReply`, `deleteQuickReply`, `createContextItem`, `deleteContextItem`, `clearContextMenu`.
- Set editing: `createSet`, `updateSet`, `deleteSet`.
- Listing: `listSets`, `listGlobalSets`, `listChatSets`, `listQuickReplies`, `listAutomationIds` (api/QuickReplyApi.js:23-495).

**Macros:** none registered. It uses scope macros `{{arg::*}}` and the core `{{input}}`.

**UI**
- Settings (html/settings.html, rendered into `#qr_container`; index.js:177-178):
  - general: Enable (`#qr--isEnabled`), Combine (`#qr--isCombined`), Show Popout Button (`#qr--showPopoutButton`) (settings.html:9-15);
  - set lists, each sortable with an add button and, per row, a set select, "Buttons" visibility, edit and remove (QuickReplySetLink.js:24-90): Global (:20-27), Chat (:32-39), Character "(Private)" (:44-52);
  - set editor: select `#qr--set` with rename, new, import (.json), export, duplicate and delete (:57-68); set flags disableSend, placeBeforeInput, injectInput, color picker plus Clear, onlyBorderColor (:71-88);
  - QR list, sortable, with add, paste-from-clipboard and import-from-file (:90-94).
- Set management (src/ui/SettingsUi.js):
  - Delete removes the set from all three scopes (:301-331). Rename checks for duplicates (:333-376). New or overwrite (:379-425).
  - Import validates an integer `version` and a string `name` (:432-488).
  - Export downloads `<name>.json` (:490-499). Duplicate defaults to "<name> (Copy)" (:501+).
  - Per-QR transfer or copy popup to another set (src/QuickReplySet.js:272-354).
- Button bar: `#qr--bar` is inserted before the first child of `#send_form`. Each visible set renders a `.qr--buttons` div of `.qr--button` items (icon, label, ⋮ expander). The popout `#qr--popout` is draggable and has a close button (src/ui/ButtonUi.js:31-154; src/QuickReplySet.js:56-69; src/QuickReply.js:108-165).
- QR editor popup (html/qrEditor.html; src/QuickReply.js:384+):
  - icon picker, label, show-label, QR switcher, title, message textarea with stscript highlighting by hljs (qrEditor.html:5-54);
  - options word wrap, tab size, Ctrl+Enter to execute, syntax highlight, persisted in `accountStorage` keys `qr--wrap`, `qr--tabSize` (default 4), `qr--executeShortcut` (default true), `qr--syntax` (default true) (QuickReply.js:543-613);
  - keys: Tab and Shift+Tab indent, F9 toggles a `/breakpoint |` line, Ctrl+key toggles block comments (QuickReply.js:647-857; qrEditor.html:50);
  - context-menu editor with set and "Chaining" per entry (qrEditor.html:66-81);
  - Auto-Execute checkboxes and Automation ID (:85-125);
  - Testing: Execute, Pause/continue, Stop, progress, errors and result, plus the debugger state view (:130-147).

---

### 2.9 shared.js (public/scripts/extensions/shared.js)

Shared helpers used by caption, memory, expressions, connection-manager and third-party extensions.

| export | purpose | line |
|---|---|---|
| `getMultimodalCaption(base64Img, prompt)` | the caption "multimodal" backend: reverse-proxy decision, thumbnailing, per-API request body, endpoint routing (/api/google, /api/anthropic, /api/backends/text-completions/ollama, /api/openai `/caption-image`); returns the trimmed caption | :16-163 |
| `throwIfInvalidModel` (internal) | per-API key, URL and model validation errors | :165-298 |
| `isWebLlmSupported()` | needs `navigator.gpu` and `SillyTavern.llm` (the third-party Extension-WebLLM); shows toasts once per session (sessionStorage keys `webllm_browser_warning_shown`, `webllm_extension_warning_shown`); the toast links to https://github.com/SillyTavern/Extension-WebLLM | :304-333 |
| `generateWebLlmChatPrompt(messages, params)` | `SillyTavern.llm.generateChatPrompt` | :341-351 |
| `countWebLlmTokens(text)` | `engine.countTokens`, falling back to `getTokenCountAsync` | :359-372 |
| `getWebLlmContextSize()` | `loadModel()` then `getCurrentModelInfo().context_size` | :378-387 |
| `ConnectionManagerRequestService` | profile-based generation for extensions | :392-788 |

`ConnectionManagerRequestService` members:
- `defaultSendRequestParams` = `{stream:false, signal:null, extractData:true, includePreset:true, includeInstruct:true, instructSettings:{}}` (:393-400).
- `getAllowedTypes()` returns `{openai:'Chat Completion', textgenerationwebui:'Text Completion'}` (:402-407).
- `sendRequest(profileId, prompt, maxTokens, custom, overridePayload)`:
  - Throws if connection-manager is disabled.
  - CC: `ChatCompletionService.processRequest({stream, messages, max_tokens, model, chat_completion_source, secret_id, custom_url/vertexai_region/zai_endpoint/siliconflow_endpoint/minimax_endpoint/pollinations_endpoint = profile['api-url'], reverse_proxy/proxy_password from the proxy preset, custom_prompt_post_processing}, {presetName})`.
  - TC: `TextCompletionService.processRequest({stream, prompt, max_tokens, model, api_type, api_server, secret_id}, {instructName, presetName, instructSettings})` (:423-492).
- `constructPrompt(prompt, profileId, instructSettings)`: CC passes the prompt through; TC calls `TextCompletionService.constructPrompt` (:501-524).
- `getSupportedProfiles()`, `getProfile(id)`, `getProfileIcon(id)` (`createModelIcon`) (:530-573).
- `isProfileSupported(profile)` checks `CONNECT_API_MAP[api]` is cc with a source or tc with a type (:579-598).
- `validateProfile` (:605-623).
- `handleDropdown(selector, initialId, onChange, onCreate, onUpdate, onDelete)`: builds a `<select>` with optgroups per type and keeps it in sync through CONNECTION_PROFILE_CREATED, UPDATED and DELETED listeners (:634-787).

Slash commands, macros, settings: none. It reads `extension_settings.caption.*` (:16-135) and `extensionSettings.connectionManager.*` (:536, :547, :563).


### Part B: regex, image generation, token counter, translate, TTS, vectors, third-party

All paths are relative to the SillyTavern root. Every built-in extension's `manifest.json` has `"hooks": {"activate": "init"}`, so the loader calls the exported `init()`.

---

### 2.10 Regex

**Manifest** (`public/scripts/extensions/regex/manifest.json:1-14`)

| field | value | cite |
|---|---|---|
| display_name | "Regex" | public/scripts/extensions/regex/manifest.json:2 |
| loading_order | 1 | public/scripts/extensions/regex/manifest.json:3 |
| requires / optional | [] / [] | public/scripts/extensions/regex/manifest.json:4-5 |
| js / css | index.js / style.css | public/scripts/extensions/regex/manifest.json:6-7 |
| generate_interceptor | none | n/a |

**Purpose.** User-defined find/replace regex scripts. Core code calls `getRegexedString()` on user input, AI output, slash-command output, World Info content and reasoning. Scripts can be global, character-scoped or preset-scoped.

**Script object** (typedef `RegexScriptData`, public/scripts/char-data.js:88-102; the editor builds it at public/scripts/extensions/regex/index.js:848-868)

| field | type | meaning | cite |
|---|---|---|---|
| id | string (uuid) | unique ID | public/scripts/char-data.js:89 |
| scriptName | string | name, used by /regex lookups | public/scripts/char-data.js:90 |
| findRegex | string | `/pattern/flags` string, compiled by `regexFromString` | public/scripts/char-data.js:91; public/scripts/extensions/regex/engine.js:54-56 |
| replaceString | string | replacement. `{{match}}` becomes `$0`; supports `$1..$n` and `$<name>` | public/scripts/extensions/regex/engine.js:421-430 |
| trimStrings | string[] | strings removed from each captured group (macros are substituted first) | public/scripts/extensions/regex/engine.js:457-465 |
| placement | number[] | where the script runs (enum below) | public/scripts/char-data.js:94 |
| disabled | bool | skip the script | public/scripts/extensions/regex/engine.js:393 |
| markdownOnly | bool | display-only: alters rendered text, not stored text | public/scripts/extensions/regex/engine.js:350 |
| promptOnly | bool | only alters the outgoing prompt | public/scripts/extensions/regex/engine.js:352 |
| runOnEdit | bool | also runs when a message is edited | public/scripts/extensions/regex/engine.js:356 |
| substituteRegex | number | macro substitution inside findRegex (enum below) | public/scripts/extensions/regex/engine.js:397-409 |
| minDepth / maxDepth | number\|null | message-depth window. minDepth is honored when ≥ -1; maxDepth when ≥ 0 | public/scripts/extensions/regex/engine.js:362-371 |

**`regex_placement` enum** (public/scripts/extensions/regex/engine.js:281-292): MD_DISPLAY=0 (deprecated), USER_INPUT=1, AI_OUTPUT=2, SLASH_COMMAND=3, (4 = legacy sendAs), WORLD_INFO=5, REASONING=6. The editor checkboxes use values 1,2,3,5,6 (public/scripts/extensions/regex/editor.html:82-106).

**`substitute_find_regex` enum** (public/scripts/extensions/regex/engine.js:298-302): NONE=0 ("Don't substitute"), RAW=1, ESCAPED=2. ESCAPED escapes regex metacharacters in macro values (public/scripts/extensions/regex/engine.js:304-324). Labels: public/scripts/extensions/regex/editor.html:143-145.

**Defaults for a new script.** markdownOnly, runOnEdit and placement=[USER_INPUT] are pre-checked (public/scripts/extensions/regex/index.js:797-809).

**Migration.** MD_DISPLAY placement is converted to markdownOnly+promptOnly, and placement 4 becomes SLASH_COMMAND (public/scripts/extensions/regex/index.js:1374-1415).

**Scopes / types** (`SCRIPT_TYPES`, public/scripts/extensions/regex/engine.js:11-16). Order defines priority: GLOBAL=0, SCOPED=1, PRESET=2. `getRegexScripts()` concatenates them in `Object.values` order (public/scripts/extensions/regex/engine.js:98-100).

| type | storage | allow-gate | cite |
|---|---|---|---|
| GLOBAL | `extension_settings.regex` | always on | public/scripts/extensions/regex/engine.js:115-116,143-145 |
| SCOPED (character) | `character.data.extensions.regex_scripts` (written with `writeExtensionField`) | `extension_settings.character_allowed_regex` must include the avatar | public/scripts/extensions/regex/engine.js:117-120,147-148,166-207 |
| PRESET | current API preset's extension field `regex_scripts` (PresetManager) | `extension_settings.preset_allowed_regex[apiId]` must include the preset name | public/scripts/extensions/regex/engine.js:121-128,150-153,215-259 |

On chat change, if a character has embedded scripts that are not yet allowed, a one-time confirm popup is shown (accountStorage key `AlertRegex_<avatar>`) (public/scripts/extensions/regex/index.js:1606-1625). Presets work the same way (key `AlertRegex_<apiId>_<name>`) (public/scripts/extensions/regex/index.js:1650-1667).

**engine.js exports**

| export | purpose | cite |
|---|---|---|
| SCRIPT_TYPES, SCRIPT_TYPE_UNKNOWN(-1) | type enums | public/scripts/extensions/regex/engine.js:11,21 |
| class RegexProvider | LRU cache (max 1000) of compiled RegExp; resets lastIndex | public/scripts/extensions/regex/engine.js:40-91 |
| getRegexScripts({allowedOnly}) | all scripts across types | public/scripts/extensions/regex/engine.js:98 |
| getScriptsByType(type,{allowedOnly}) | scripts of one type | public/scripts/extensions/regex/engine.js:108 |
| saveScriptsByType(scripts,type) | persist one type | public/scripts/extensions/regex/engine.js:141 |
| isScopedScriptsAllowed / allowScopedScripts / disallowScopedScripts | character gate | public/scripts/extensions/regex/engine.js:166,175,194 |
| isPresetScriptsAllowed / allowPresetScripts / disallowPresetScripts | preset gate | public/scripts/extensions/regex/engine.js:215,228,247 |
| getCurrentPresetAPI / getCurrentPresetName | PresetManager helpers | public/scripts/extensions/regex/engine.js:265,273 |
| regex_placement, substitute_find_regex | enums | public/scripts/extensions/regex/engine.js:281,298 |
| getRegexedString(raw, placement, {characterOverride,isMarkdown,isPrompt,isEdit,depth}) | applies all allowed scripts; returns input unchanged if the extension is disabled | public/scripts/extensions/regex/engine.js:334-381 |
| runRegexScript(script, raw, {characterOverride}) | runs one script; final `substituteParams` on the replacement | public/scripts/extensions/regex/engine.js:391-448 |

**Core call sites (placement usage):** USER_INPUT on send (public/script.js:5875); AI_OUTPUT/USER_INPUT on generation result (public/script.js:6481); first message and greetings use AI_OUTPUT (public/script.js:7719,7724); display/prompt with depth (public/script.js:1860,4506); WORLD_INFO with `isPrompt:true` and depth (public/scripts/world-info.js:5205); REASONING (public/scripts/reasoning.js:429,1036,1215); SLASH_COMMAND for /sendas, /sys and similar (public/scripts/slash-commands.js:4716,5717,5944).

**Settings (extension_settings.\*)**

| key | default | meaning | cite |
|---|---|---|---|
| regex | [] | global scripts | public/scripts/extensions/regex/index.js:1713-1715 |
| regex_presets | [] | saved enable/disable snapshots `{id,name,isSelected,global:[{id}],scoped:[{id}],preset:[{id}]}` (only enabled IDs are stored) | public/scripts/extensions/regex/index.js:1717-1719,409-436 |
| character_allowed_regex | (created on demand) [] | avatars whose scoped scripts are allowed | public/scripts/extensions/regex/engine.js:180-185 |
| preset_allowed_regex | (on demand) {apiId:[names]} | allowed preset scripts | public/scripts/extensions/regex/engine.js:232-237 |

**Server endpoints.** None. Persistence goes through settings, character extension fields and preset files.

**Slash commands**

| name | args | purpose | cite |
|---|---|---|---|
| /regex | `name=` (required, script name); unnamed: input text | runs the named script on the input. Returns the input with a warning if the script is disabled | public/scripts/extensions/regex/index.js:2051-2070,1422-1446 |
| /regex-state | unnamed: script name (required) | returns "true" if enabled, "false" if disabled | public/scripts/extensions/regex/index.js:2071-2100 |
| /regex-toggle | `state=` on/off/toggle (default toggle), `quiet=` bool; unnamed: script name | enable/disable/toggle a script; returns the name | public/scripts/extensions/regex/index.js:2102-2146,1453-1492 |
| /regex-preset | `quiet=` bool; unnamed: preset name or ID (optional) | select a regex preset; returns the current preset ID | public/scripts/extensions/regex/index.js:264-310 |

**Events**

| event | handler | cite |
|---|---|---|
| MAIN_API_CHANGED | onMainApiChanged | public/scripts/extensions/regex/index.js:2148 |
| CHAT_CHANGED | checkCharEmbeddedRegexScripts | public/scripts/extensions/regex/index.js:2149 |
| CHARACTER_DELETED | purgeEmbeddedRegexScripts | public/scripts/extensions/regex/index.js:2150 |
| PRESET_RENAMED_BEFORE | onPresetRenamed | public/scripts/extensions/regex/index.js:2151 |
| PRESET_CHANGED | checkPresetEmbeddedRegexScripts | public/scripts/extensions/regex/index.js:2152 |
| PRESET_DELETED | purgePresetEmbeddedRegexScripts | public/scripts/extensions/regex/index.js:2153 |

No macros, no function tools and no generate interceptor.

**UI.** The panel goes in `#regex_container` (public/scripts/extensions/regex/index.js:1728-1729). Buttons: new global/preset/scoped script, import (JSON, multi-file, with an import-target popup), bulk edit (select all, enable, disable, move to global/preset/scoped, export, delete) and the Advanced Regex Debugger (public/scripts/extensions/regex/dropdown.html:11-67). The preset bar has select, create, update, apply and delete (public/scripts/extensions/regex/dropdown.html:73-85). There are three script lists, global, preset and scoped, each with an allow toggle (public/scripts/extensions/regex/dropdown.html:89-126). The editor popup has a live test mode (public/scripts/extensions/regex/index.js:811-841).

---

### 2.11 Image Generation (stable-diffusion)

**Manifest** (`public/scripts/extensions/stable-diffusion/manifest.json:1-17`)

| field | value | cite |
|---|---|---|
| display_name | "Image Generation" | public/scripts/extensions/stable-diffusion/manifest.json:2 |
| loading_order | 10 | public/scripts/extensions/stable-diffusion/manifest.json:3 |
| requires | [] | public/scripts/extensions/stable-diffusion/manifest.json:4 |
| optional | ["sd"] (Extras module) | public/scripts/extensions/stable-diffusion/manifest.json:5-7 |
| generate_interceptor | "SD_ProcessTriggers" (= `processTriggers`, assigned to `globalThis`) | public/scripts/extensions/stable-diffusion/manifest.json:8; public/scripts/extensions/stable-diffusion/index.js:436 |
| js / css | index.js / style.css | public/scripts/extensions/stable-diffusion/manifest.json:9-10 |

**Purpose.** Generates images (and video on some sources) from free text or from LLM-written prompts that describe the character, the user, the scene and so on, across 24 backends. The result is posted to chat as a message with a media attachment (public/scripts/extensions/stable-diffusion/index.js:4966-5000). MODULE_NAME is `'sd'` (public/scripts/extensions/stable-diffusion/index.js:69), so settings live in `extension_settings.sd`.

**Sources** (`sources` enum, public/scripts/extensions/stable-diffusion/index.js:73-98; UI labels at public/scripts/extensions/stable-diffusion/settings.html:44-67). Validity checks are in `isValidState` (public/scripts/extensions/stable-diffusion/index.js:5076-5137).

| key | UI label | generate fn → endpoint | models / samplers | readiness requirement |
|---|---|---|---|---|
| extras | Extras API (deprecated) | generateExtrasImage → Extras `/api/image` (index.js:3524-3526) | Extras `/api/image/samplers`, `/api/image/model(s)` (index.js:1784,2286,2294) | `modules.includes('sd')` (index.js:5078-5079) |
| horde | Stable Horde | generateHordeImage → `/api/horde/generate-image` (index.js:3757-3758) | `/api/horde/sd-samplers`, `/api/horde/sd-models` (index.js:1766,2262) | always (index.js:5080) |
| auto | Stable Diffusion Web UI (AUTOMATIC1111) | generateAutoImage → `/api/sd/generate` with `{url:auto_url,auth:auto_auth}` (index.js:3797-3848,442-443) | `/api/sd/samplers`, `/api/sd/models`, `/api/sd/get-model`, `/api/sd/set-model`, `/api/sd/upscalers`, `/api/sd/schedulers`, `/api/sd/vaes`, ping `/api/sd/ping` (index.js:1801,2317,1522,1642,1564,1583,2791,1353) | auto_url set |
| vlad | SD.Next (vladmandic) | generateAutoImage (same as auto) using vlad_url/vlad_auth (index.js:3346-3347,440-441) | `/api/sd/samplers`, `/api/sd/models`, `/api/sd/sd-next/upscalers`, ping `/api/sd/ping` (index.js:1872,2427,1602,1422) | vlad_url set |
| sdcpp | stable-diffusion.cpp server | generateSdcppImage → `/api/sd/sdcpp/generate` (index.js:3872-3898) | `/api/sd/sdcpp/models`, ping `/api/sd/sdcpp/ping` (index.js:1823,1376) | sdcpp_url set |
| drawthings | DrawThings HTTP API | generateDrawthingsImage → `/api/sd/drawthings/generate` (index.js:3922-3923) | `/api/sd/drawthings/get-model`, `/api/sd/drawthings/get-upscaler`, ping `/api/sd/drawthings/ping` (index.js:1541,1621,1399) | drawthings_url set |
| novel | NovelAI Diffusion | generateNovelImage → `/api/novelai/generate-image` (index.js:3963-3966) | loadNovelModels / loadNovelSamplers (index.js:2457,1888) | NOVEL key |
| openai | OpenAI | generateOpenAiImage → `/api/openai/generate-image`; Sora-2 models → `/api/openai/generate-video` (index.js:4073-4144) | loadOpenAiModels (index.js:2383) | OPENAI key |
| aimlapi | AI/ML API | → `/api/sd/aimlapi/generate-image` (index.js:4176-4198) | `/api/sd/aimlapi/models` (index.js:2401) | AIMLAPI key |
| comfy | ComfyUI (standard / RunPod serverless) | generateComfyImage → `/api/sd/comfy/generate`; RunPod → `/api/sd/comfyrunpod/generate` (index.js:4276,4314,4333) | `/api/sd/comfy/models`, `/samplers`, `/schedulers`, `/vaes`, `/workflows`, ping `/api/sd/comfy/ping` or `/api/sd/comfyrunpod/ping` (index.js:2553,1909,2671,2818,2837,1445,1469) | standard: comfy_url; runpod: comfy_runpod_url + COMFY_RUNPOD key (index.js:5096-5105) |
| togetherai | TogetherAI | → `/api/sd/together/generate` (index.js:3460-3461) | `/api/sd/together/models` (index.js:2177) | TOGETHERAI key |
| pollinations | Pollinations | → `/api/sd/pollinations/generate` (index.js:3491-3492) | `/api/sd/pollinations/models` (index.js:2159) | POLLINATIONS key |
| stability | Stability AI | → `/api/sd/stability/generate` (index.js:3711-3716) | loadStabilityModels (index.js:2086) | STABILITY key |
| huggingface | HuggingFace Inference API (serverless) | → `/api/sd/huggingface/generate` (index.js:4342-4343) | free-text model ID `#sd_huggingface_model_id` (settings.html:116-117) | HUGGINGFACE key |
| chutes | Chutes | → `/api/sd/chutes/generate` (index.js:4369-4370) | `/api/sd/chutes/models` (index.js:2195) | CHUTES key |
| electronhub | Electron Hub | → `/api/sd/electronhub/generate` (index.js:4400-4403) | `/api/sd/electronhub/models`, `/api/sd/electronhub/sizes` (index.js:2216,3650) | ELECTRONHUB key |
| nanogpt | NanoGPT | → `/api/sd/nanogpt/generate` (index.js:4431-4432) | `/api/sd/nanogpt/models` (index.js:2249) | NANOGPT key |
| bfl | BFL (Black Forest Labs) | → `/api/sd/bfl/generate` (index.js:4465-4466) | loadBflModels (index.js:2096) | BFL key |
| falai | FAL.AI | → `/api/sd/falai/generate` (index.js:4537-4538) | `/api/sd/falai/models` (index.js:2110) | FALAI key |
| xai | xAI (Grok) | → `/api/sd/xai/generate` (index.js:4498-4509) | loadXAIModels (index.js:2122) | XAI key |
| google | Google AI (AI Studio / Vertex) | → `/api/google/generate-image`; `veo-*` → `/api/google/generate-video` (index.js:4570-4603) | loadGoogleModels (index.js:2490) | MAKERSUITE, VERTEXAI or VERTEXAI_SERVICE_ACCOUNT key |
| zai | Z.AI | → `/api/sd/zai/generate`; cogvideox/vidu → `/api/sd/zai/generate-video` (index.js:4636-4688) | loadZaiModels (index.js:2515) | ZAI key |
| openrouter | OpenRouter | → `/api/openrouter/image/generate` (index.js:4716-4717) | `/api/openrouter/models/image` (index.js:2525) | OPENROUTER key |
| workersai | Cloudflare Workers AI | → `/api/sd/workersai/generate` (index.js:4737-4738) | `/api/sd/workersai/models` (index.js:2141) | `oai_settings.workers_ai_account_id` + WORKERS_AI key |

(In this table, "index.js" means public/scripts/extensions/stable-diffusion/index.js and "settings.html" means public/scripts/extensions/stable-diffusion/settings.html.) Source dispatch: public/scripts/extensions/stable-diffusion/index.js:3339-3420. Sampler loader switch: public/scripts/extensions/stable-diffusion/index.js:1672-1748. Model loader switch: public/scripts/extensions/stable-diffusion/index.js:1925-2001.

**Video generation.** Output counts as video when `format` is in `VIDEO_EXTENSIONS` (public/scripts/extensions/stable-diffusion/index.js:366). The attachment then gets `MEDIA_TYPE.VIDEO` (public/scripts/extensions/stable-diffusion/index.js:4971). Video-capable paths: OpenAI Sora-2 (public/scripts/extensions/stable-diffusion/index.js:4081,4124; duration `openai_duration`), Google Veo (public/scripts/extensions/stable-diffusion/index.js:4571-4576; `google_duration`), and Z.AI CogVideoX/Vidu (public/scripts/extensions/stable-diffusion/index.js:4637-4649).

**Generation modes** (`generationMode`, public/scripts/extensions/stable-diffusion/index.js:113-128). Labels: public/scripts/extensions/stable-diffusion/index.js:136-150.

| mode | value | /imagine trigger word (exact match) | interactive-mode phrases | wand item |
|---|---|---|---|---|
| TOOL | -2 | (function-tool prompt description) | - | - |
| MESSAGE | -1 | (chat message template) | - | - |
| CHARACTER | 0 | `you` | you, yourself | sd_you |
| USER | 1 | `me` | me, myself | sd_me |
| SCENARIO | 2 | `scene` | story, scenario, whole story | sd_world → "scene" |
| RAW_LAST | 3 | `raw_last` | - | sd_raw_last |
| NOW | 4 | `last` | last message | sd_last |
| FACE | 5 | `face` | face, portrait, selfie | sd_face |
| FREE | 6 | any other text | anything else after the activation regex | - |
| BACKGROUND | 7 | `background` | background, scene background, scene, scenery, surroundings, environment | sd_background |
| CHARACTER_MULTIMODAL / USER_MULTIMODAL / FACE_MULTIMODAL | 8 / 9 / 10 | replaces 0/1/5 when `multimodal_captioning` is on | | |
| FREE_EXTENDED | 11 | replaces FREE when `free_extend` is on | | |

Cites: trigger words at public/scripts/extensions/stable-diffusion/index.js:152-160; interactive regex and special cases at public/scripts/extensions/stable-diffusion/index.js:162-172; multimodal map at public/scripts/extensions/stable-diffusion/index.js:130-134; mode resolution at public/scripts/extensions/stable-diffusion/index.js:2860-2881; wand id→param map at public/scripts/extensions/stable-diffusion/index.js:5056-5064; dropdown at public/scripts/extensions/stable-diffusion/dropdown.html:4-10.

**Prompt templates** (`promptTemplates`, public/scripts/extensions/stable-diffusion/index.js:174-218; user-editable copies are stored in `extension_settings.sd.prompts`, and missing keys are back-filled at public/scripts/extensions/stable-diffusion/index.js:474-483):
- MESSAGE: `[{{char}} sends a picture that contains: {{prompt}}].` Rendered with `{char,prompt,prefixedPrompt}` (public/scripts/extensions/stable-diffusion/index.js:176,4969-4970).
- TOOL: JSON-schema description of the tool `prompt` (public/scripts/extensions/stable-diffusion/index.js:177-180).
- CHARACTER, FACE, USER, SCENARIO, NOW, RAW_LAST, BACKGROUND: instructions that ask the LLM for comma-delimited visual keywords, sent as a quiet prompt (`generateQuietPrompt`) (public/scripts/extensions/stable-diffusion/index.js:181-209,3292-3294).
- *_MULTIMODAL: captions the character or user avatar with `getMultimodalCaption` (public/scripts/extensions/stable-diffusion/index.js:210-212,3232-3265).
- FREE_EXTENDED: `{0}` is the subject, and `{{charPrefix}}` is applied if the subject is related to {{char}} (public/scripts/extensions/stable-diffusion/index.js:213; formatting with `stringFormat` at public/scripts/extensions/stable-diffusion/index.js:2883-2889).

Default prefix and negative: public/scripts/extensions/stable-diffusion/index.js:220-221.

**Prompt pipeline.**
1. `getPrompt` (public/scripts/extensions/stable-diffusion/index.js:3165).
2. `processReply` (public/scripts/extensions/stable-diffusion/index.js:2896-2928). Standard processing strips quotes and non-model characters and normalizes commas. Minimal processing only collapses whitespace.
3. Optional `refinePrompt` edit popup when `refine_mode` is on, for non-FREE modes (public/scripts/extensions/stable-diffusion/index.js:3189-3190,814-835).
4. The `SD_PROMPT_PROCESSING` event lets other extensions mutate the prompt (public/scripts/extensions/stable-diffusion/index.js:3054-3057).
5. Dimension auto-adjust: portrait for FACE, landscape (1.8x) for BACKGROUND, optional snap to known resolutions (public/scripts/extensions/stable-diffusion/index.js:3103-3144; resolution presets at public/scripts/extensions/stable-diffusion/index.js:1062-1092).
6. Save with `saveBase64AsFile`, then `sendMessage` (public/scripts/extensions/stable-diffusion/index.js:3446-3449).

**Message visibility per initiator.** The message is hidden (`is_system`) unless the matching `*_visible` flag is on (public/scripts/extensions/stable-diffusion/index.js:4985,5009-5022). Initiators are command, action, interactive, wand, swipe and tool (public/scripts/extensions/stable-diffusion/index.js:104-111).

**ComfyUI workflow handling.**
- Types: `standard` and `runpod_serverless` (public/scripts/extensions/stable-diffusion/index.js:99-102).
- The workflow JSON is fetched with `/api/sd/comfy/workflow` `{file_name: comfy_workflow}` (public/scripts/extensions/stable-diffusion/index.js:4222-4226).
- Placeholder strings `"%prompt%"`, `"%negative_prompt%"`, `"%seed%"` (random if seed < 0), `"%denoise%"` and `"%clip_skip%"` are replaced (public/scripts/extensions/stable-diffusion/index.js:4233-4243).
- Per-type placeholders: standard uses model, vae, sampler, scheduler, steps, scale, width and height; RunPod uses steps, scale, width and height (public/scripts/extensions/stable-diffusion/index.js:4304-4333).
- Custom `comfy_placeholders [{find,replace}]` have macros substituted (public/scripts/extensions/stable-diffusion/index.js:4248-4250).
- `"%user_avatar%"` and `"%char_avatar%"` become base64 PNGs, or a 1x1 transparent PNG as fallback (public/scripts/extensions/stable-diffusion/index.js:4251-4271,71).
- POST to `${basePath}/generate` with `{prompt: workflow}` (public/scripts/extensions/stable-diffusion/index.js:4274-4283).
- Workflow editor: placeholder list at public/scripts/extensions/stable-diffusion/comfyWorkflowEditor.html:12-28 and found/not-found highlighting at public/scripts/extensions/stable-diffusion/index.js:4781-4784.
- Workflow file management: `/api/sd/comfy/save-workflow` (public/scripts/extensions/stable-diffusion/index.js:4842,4866), `/api/sd/comfy/delete-workflow` (public/scripts/extensions/stable-diffusion/index.js:4889), `/api/sd/comfy/rename-workflow` (public/scripts/extensions/stable-diffusion/index.js:4936).

**Settings (extension_settings.sd.\*)**: defaults at public/scripts/extensions/stable-diffusion/index.js:233-363. Missing keys are back-filled at public/scripts/extensions/stable-diffusion/index.js:469-473.

| key | default | meaning | cite |
|---|---|---|---|
| source | 'extras' | active backend | public/scripts/extensions/stable-diffusion/index.js:234 |
| scale (min 1 / max 30 / step 0.1) | 7 | CFG | public/scripts/extensions/stable-diffusion/index.js:237-240 |
| steps (1/150/1) | 20 | sampler steps | public/scripts/extensions/stable-diffusion/index.js:243-246 |
| scheduler | 'normal' | scheduler | public/scripts/extensions/stable-diffusion/index.js:249 |
| width / height (64/2048/64) | 512 / 512 | dimensions | public/scripts/extensions/stable-diffusion/index.js:252-256 |
| prompt_prefix / negative_prompt | defaultPrefix / defaultNegative | global positive prefix and negative prompt | public/scripts/extensions/stable-diffusion/index.js:258-259 |
| sampler / model / vae | 'DDIM' / '' / '' | selections | public/scripts/extensions/stable-diffusion/index.js:260-262 |
| seed | -1 | -1 means random | public/scripts/extensions/stable-diffusion/index.js:263 |
| restore_faces / enable_hr / adetailer_face | false | A1111 options | public/scripts/extensions/stable-diffusion/index.js:266-268 |
| horde / horde_nsfw / horde_karras / horde_sanitize | false/false/true/true | Horde options | public/scripts/extensions/stable-diffusion/index.js:271-274 |
| refine_mode | false | edit the prompt before generating | public/scripts/extensions/stable-diffusion/index.js:277 |
| interactive_mode | false | natural-language trigger ("send me a picture of…") | public/scripts/extensions/stable-diffusion/index.js:278 |
| multimodal_captioning | false | avatar caption for you/me/face | public/scripts/extensions/stable-diffusion/index.js:279 |
| snap | false | snap to known resolutions | public/scripts/extensions/stable-diffusion/index.js:280 |
| free_extend | false | LLM-extend free prompts | public/scripts/extensions/stable-diffusion/index.js:281 |
| function_tool | false | register the GenerateImage tool | public/scripts/extensions/stable-diffusion/index.js:282 |
| minimal_prompt_processing | false | minimal sanitizing | public/scripts/extensions/stable-diffusion/index.js:283 |
| prompts | promptTemplates | per-mode templates | public/scripts/extensions/stable-diffusion/index.js:285 |
| auto_url / auto_auth | 'http://localhost:7860' / '' | A1111 | public/scripts/extensions/stable-diffusion/index.js:288-289 |
| sdcpp_url | 'http://127.0.0.1:1234' | sd.cpp | public/scripts/extensions/stable-diffusion/index.js:292 |
| vlad_url / vlad_auth | 'http://localhost:7860' / '' | SD.Next | public/scripts/extensions/stable-diffusion/index.js:294-295 |
| drawthings_url / drawthings_auth | 'http://localhost:7860' / '' | DrawThings | public/scripts/extensions/stable-diffusion/index.js:297-298 |
| hr_upscaler, hr_scale (1-4), denoising_strength (0-1), hr_second_pass_steps (0-150) | 'Latent', 1.0, 0.7, 0 | hi-res fix | public/scripts/extensions/stable-diffusion/index.js:300-312 |
| clip_skip (1-12) | 1 | CLIP skip | public/scripts/extensions/stable-diffusion/index.js:315-318 |
| novel_anlas_guard / novel_sm / novel_sm_dyn / novel_decrisper / novel_variety_boost | false | NovelAI options | public/scripts/extensions/stable-diffusion/index.js:321-325 |
| openai_style / openai_quality / openai_quality_gpt / openai_duration | 'vivid' / 'standard' / 'auto' / '8' | OpenAI DALL-E / gpt-image / Sora | public/scripts/extensions/stable-diffusion/index.js:328-331 |
| style / styles | 'Default' / [{name,negative,prefix}] | saved style presets | public/scripts/extensions/stable-diffusion/index.js:333-334,223-229 |
| comfy_type / comfy_url / comfy_workflow / comfy_runpod_url | 'standard' / 'http://127.0.0.1:8188' / 'Default_Comfy_Workflow.json' / '' | ComfyUI | public/scripts/extensions/stable-diffusion/index.js:337-342 |
| pollinations_enhance | false | Pollinations prompt enhance | public/scripts/extensions/stable-diffusion/index.js:345 |
| wand_visible / command_visible / interactive_visible / tool_visible | false | whether results are visible to the LLM context | public/scripts/extensions/stable-diffusion/index.js:348-351 |
| stability_style_preset | 'anime' | Stability preset | public/scripts/extensions/stable-diffusion/index.js:354 |
| bfl_upsampling | false | BFL prompt upsampling | public/scripts/extensions/stable-diffusion/index.js:357 |
| google_api / google_enhance / google_duration | 'makersuite' / true / 6 | Google Imagen/Veo | public/scripts/extensions/stable-diffusion/index.js:360-362 |
| character_prompts / character_negative_prompts | {} (keyed by char filename) | per-character prefixes | public/scripts/extensions/stable-diffusion/index.js:486-492 |
| comfy_placeholders | (on demand) [] | custom `{find,replace}` | public/scripts/extensions/stable-diffusion/index.js:4825-4826 |
| huggingface_model_id | (undefined) | HF model ID | public/scripts/extensions/stable-diffusion/index.js:557 |
| electronhub_quality | (undefined) | Electron Hub quality | public/scripts/extensions/stable-diffusion/index.js:5899-5902 |
| original_seed | transient | restored to seed on load | public/scripts/extensions/stable-diffusion/index.js:498-502 |

Character prompts can also be shared in the card as `data.extensions.sd_character_prompt {positive,negative}` (public/scripts/char-data.js:84; public/scripts/extensions/stable-diffusion/index.js:5965-5973).

**Slash commands**

| name | aliases | args | purpose | cite |
|---|---|---|---|---|
| /imagine | sd, img, image | unnamed: trigger word or free prompt. Named: quiet, negative, extend, edit, multimodal, snap, processing (standard\|minimal), seed, width, height, steps, cfg, skip, model, sampler, scheduler, vae, upscaler, hires, scale, denoise, 2ndpass, faces | generate; returns the image URL. Overrides are temporary and restored afterwards | public/scripts/extensions/stable-diffusion/index.js:5492-5726 |
| /imagine-source | sd-source, img-source | unnamed: source key (forceEnum) | get or set the source | public/scripts/extensions/stable-diffusion/index.js:5727-5757 |
| /imagine-style | sd-style, img-style | unnamed: style name | get or set the style | public/scripts/extensions/stable-diffusion/index.js:5759-5785 |
| /imagine-comfy-workflow | icw | unnamed: workflow name (required) | switch the ComfyUI workflow | public/scripts/extensions/stable-diffusion/index.js:5787-5799 |

**Function tool.** `GenerateImage` ("Generate Image"). Its single parameter `prompt` uses the TOOL template description. The action calls `generatePicture(initiators.tool, …)` and returns the URL. It is registered only when `function_tool` is on, otherwise unregistered (public/scripts/extensions/stable-diffusion/index.js:5452-5486). When the tool is active, the interactive interceptor is skipped (public/scripts/extensions/stable-diffusion/index.js:380-382).

**Generate interceptor.** `processTriggers(chat,_,abort,type)` skips quiet generations. It acts only when `interactive_mode` is on and the last message is from the user. It matches `messageTrigger.activationRegex` and then generates (public/scripts/extensions/stable-diffusion/index.js:375-434).

**Macros.** `{{charPrefix}}` and `{{charNegativePrefix}}` return the character's SD prompt prefixes. They are registered with the new macro engine (`macros.register`) when `power_user.experimental_macro_engine` is on, otherwise with `MacrosParser.registerMacro` (public/scripts/extensions/stable-diffusion/index.js:5977-5997).

**Events**

| event | handler | cite |
|---|---|---|
| EXTRAS_CONNECTED | reload options if source = extras | public/scripts/extensions/stable-diffusion/index.js:5924-5928 |
| CHAT_CHANGED | onChatChanged | public/scripts/extensions/stable-diffusion/index.js:5930 |
| IMAGE_SWIPED | onImageSwiped → regenerate a media swipe | public/scripts/extensions/stable-diffusion/index.js:5931,5343 |
| SECRET_WRITTEN / DELETED / ROTATED | reload options for bfl, falai, stability, aimlapi, comfy(runpod), pollinations, workersai | public/scripts/extensions/stable-diffusion/index.js:5933-5950 |
| emits SD_PROMPT_PROCESSING | prompt hook | public/scripts/extensions/stable-diffusion/index.js:5056 |
| emits MESSAGE_RECEIVED, CHARACTER_MESSAGE_RENDERED | after posting the image | public/scripts/extensions/stable-diffusion/index.js:4997-4999 |

**UI.**
- Wand menu "Generate Image" (`#sd_gen`) with a popper dropdown of modes (public/scripts/extensions/stable-diffusion/button.html:1-4; public/scripts/extensions/stable-diffusion/dropdown.html:1-12; public/scripts/extensions/stable-diffusion/index.js:5024-5072).
- Per-message `.sd_message_gen` button (public/scripts/extensions/stable-diffusion/index.js:5039).
- Settings drawer in `#sd_container` with the full list of control bindings (public/scripts/extensions/stable-diffusion/index.js:5804-5922).
- Style save/rename/delete, per-character prompt and negative prompt (with share-to-card), ComfyUI workflow editor and new/rename/delete buttons, and a resolution preset select.

---

### 2.12 Token Counter

**Manifest** (`public/scripts/extensions/token-counter/manifest.json:1-14`): display_name "Token Counter" (:2), loading_order 15 (:3), requires/optional [] (:4-5), js index.js, css style.css (:6-7), no interceptor.

**Purpose.** Opens a popup that counts tokens for pasted text using the current API's tokenizer, and shows colour-coded token chunks and token IDs (public/scripts/extensions/token-counter/index.js:12-43,50-86).

**Settings.** None.

**Endpoints.** Called indirectly through `getTextTokens` and `getTokenCountAsync` in public/scripts/tokenizers.js:1157,443. These hit `/api/tokenizers/{gpt2|openai|llama|nerdstash|nerdstash_v2|mistral|yi|claude|llama3|gemma|jamba|qwen2|command-r}/encode` and `/api/tokenizers/remote/kobold/count` (public/scripts/tokenizers.js:67-132). When `main_api=='openai'` it forces the OPENAI tokenizer (public/scripts/extensions/token-counter/index.js:19).

**Slash command**

| name | args | purpose | cite |
|---|---|---|---|
| /count | none | counts tokens of all non-system chat messages joined by spaces; shows a toast and returns the number | public/scripts/extensions/token-counter/index.js:88-102,112-117 |

**Events, macros, tools.** None.

**UI.** Wand item "Token Counter" (`#token_counter`, appended to `#token_counter_wand_container`) (public/scripts/extensions/token-counter/index.js:104-111). The popup shows the tokenizer name, input textarea, count, tokenized-chunk display and a read-only ID textarea (public/scripts/extensions/token-counter/window.html:1-16). Count updates are debounced (`debounce_timeout.relaxed`) (public/scripts/extensions/token-counter/index.js:17-40).

---

### 2.13 Chat Translation (translate)

**Manifest** (`public/scripts/extensions/translate/manifest.json:1-14`): display_name "Chat Translation" (:2), loading_order 1 (:3), requires/optional [] (:4-5), js/css (:6-7), no interceptor.

**Purpose.** Translates messages for display while keeping the original text.
- Incoming text goes to `message.extra.display_text` (public/scripts/extensions/translate/index.js:197-215) and reasoning to `extra.reasoning_display_text` (public/scripts/extensions/translate/index.js:225-243).
- Outgoing user messages are translated into `internal_language`. The original is kept as `display_text` and the translation replaces `mes` (public/scripts/extensions/translate/index.js:524-538).
- Inline markdown images are preserved (public/scripts/extensions/translate/index.js:472-479).
- `globalThis.translate = translate` is exposed for other extensions (public/scripts/extensions/translate/index.js:708).

**Settings (extension_settings.translate.\*)** (defaults at public/scripts/extensions/translate/index.js:33-39)

| key | default | meaning | cite |
|---|---|---|---|
| target_language | 'en' | language shown to the user (~100 codes, `languageCodes`) | public/scripts/extensions/translate/index.js:34,41-147 |
| internal_language | 'en' | language sent to the LLM for outgoing messages (no UI) | public/scripts/extensions/translate/index.js:35 |
| provider | 'google' | provider key | public/scripts/extensions/translate/index.js:36 |
| auto_mode | 'none' | none \| responses \| inputs \| both | public/scripts/extensions/translate/index.js:23-28,37 |
| deepl_endpoint | 'free' | free \| pro | public/scripts/extensions/translate/index.js:38 |

**Providers / endpoints** (dispatch at public/scripts/extensions/translate/index.js:494-521; UI options at public/scripts/extensions/translate/index.html:17-26)

| provider key | label | endpoint | body | chunking | key / URL secret | cite |
|---|---|---|---|---|---|---|
| libre | LibreTranslate | POST /api/translate/libre | {text,lang} | none | key required + optional URL | public/scripts/extensions/translate/index.js:274-278,149-150 |
| google | Google | POST /api/translate/google | {text,lang} | 5000 | - | public/scripts/extensions/translate/index.js:295-299,504-505 |
| lingva | Lingva | POST /api/translate/lingva | {text,lang} | 5000 | URL | public/scripts/extensions/translate/index.js:316-320,506-507 |
| deepl | DeepL API | POST /api/translate/deepl | {text,lang,endpoint} | none | key required (`secret_state.deepl`) | public/scripts/extensions/translate/index.js:337-346 |
| deeplx | DeepLX | POST /api/translate/deeplx | {text,lang} | 1500 | URL | public/scripts/extensions/translate/index.js:363-367,510-511 |
| oneringtranslator | OneRingTranslator | POST /api/translate/onering | {text,from_lang,to_lang} | none | URL | public/scripts/extensions/translate/index.js:249-257 |
| bing | Bing | POST /api/translate/bing | {text,lang} | 1000 | - | public/scripts/extensions/translate/index.js:384-388,514-515 |
| yandex | Yandex | POST /api/translate/yandex | {chunks[],lang} (pre-split at 5000) | client split | - | public/scripts/extensions/translate/index.js:405-416 |

KEY_REQUIRED = deepl, libre. LOCAL_URL (optional custom URL stored as secret `<provider>_url`) = libre, oneringtranslator, deeplx, lingva (public/scripts/extensions/translate/index.js:149-158).

**Auto modes.** Incoming translation runs for responses/both, outgoing for inputs/both (public/scripts/extensions/translate/index.js:30-31,540-542,698-706).

**Slash command**

| name | args | purpose | cite |
|---|---|---|---|
| /translate | `target=` language code (optional), `provider=` (optional, enum from dropdown); unnamed: text (required) | translates text and returns the string | public/scripts/extensions/translate/index.js:778-802 |

**Events**

| event | handler | cite |
|---|---|---|
| CHARACTER_MESSAGE_RENDERED (makeFirst) | incoming translate if auto | public/scripts/extensions/translate/index.js:768 |
| USER_MESSAGE_RENDERED (makeFirst) | outgoing translate if auto | public/scripts/extensions/translate/index.js:769 |
| MESSAGE_SWIPED | incoming | public/scripts/extensions/translate/index.js:770 |
| IMPERSONATE_READY | translate the impersonated text in the textarea | public/scripts/extensions/translate/index.js:771,185-195 |
| MESSAGE_UPDATED | re-translate the edit | public/scripts/extensions/translate/index.js:772,622-640 |
| MESSAGE_REASONING_EDITED / DELETED | re-translate / remove reasoning display text | public/scripts/extensions/translate/index.js:773-774 |
| SECRET_WRITTEN / DELETED / ROTATED | key/URL button state | public/scripts/extensions/translate/index.js:755-764 |

No macros, tools or interceptor.

**UI.**
- Settings drawer: auto-mode, provider, DeepL endpoint, Key/URL buttons, target language, Clear Translations (public/scripts/extensions/translate/index.html:1-48).
- Wand items "Translate Chat" and "Translate Input" (public/scripts/extensions/translate/buttons.html:1-8).
- Per-message `.mes_translate` toggle that translates or reverts (public/scripts/extensions/translate/index.js:671-697,753).
- Adds the `translate` body class (public/scripts/extensions/translate/index.js:776).

---

### 2.14 TTS

**Manifest** (`public/scripts/extensions/tts/manifest.json:1-18`): display_name "TTS" (:2), loading_order 10 (:3), requires [] (:4), optional ["silero-tts","edge-tts","coqui-tts"] (Extras modules) (:5-9), js index.js / css style.css (:10-11), no interceptor.

**Purpose.** Reads messages aloud with a per-character voice map. Messages are queued and audio is played through an `<audio>` element. Other code can add providers with `registerTtsProvider(name, class)` (public/scripts/extensions/tts/index.js:104-122).

**Global settings (extension_settings.tts.\*)**: defaults at public/scripts/extensions/tts/index.js:903-913, UI bindings at public/scripts/extensions/tts/index.js:876-899.

| key | default | meaning | cite |
|---|---|---|---|
| currentProvider | 'ElevenLabs' | provider name (key in `ttsProviders`) | public/scripts/extensions/tts/index.js:906,1056 |
| enabled | (undef → false) | master switch (`ttsEnabled` is a legacy default key) | public/scripts/extensions/tts/index.js:905,942-950 |
| auto_generation | true | narrate new messages automatically | public/scripts/extensions/tts/index.js:907,1108 |
| periodic_auto_generation | (false) | "Narrate by paragraphs (when streaming)": a 1 s timer during generation | public/scripts/extensions/tts/settings.html:31-32; public/scripts/extensions/tts/index.js:1231-1250 |
| narrate_by_paragraphs | (false) | "Narrate by paragraphs (when not streaming)" | public/scripts/extensions/tts/settings.html:35-36; public/scripts/extensions/tts/index.js:963-967 |
| narrate_user | false | narrate user messages | public/scripts/extensions/tts/index.js:908,974-977 |
| narrate_quoted_only | (false) | only "quoted" text | public/scripts/extensions/tts/index.js:701-703 |
| narrate_dialogues_only | (false) | ignore *asterisk* text, including quotes inside it | public/scripts/extensions/tts/index.js:686 |
| narrate_translated_only | (false) | use `extra.display_text` | public/scripts/extensions/tts/index.js:671 |
| skip_codeblocks / skip_tags | (false) | strip code blocks and `<tagged>` blocks | public/scripts/extensions/tts/index.js:676-683 |
| pass_asterisks | (false) | keep asterisks when sending to the engine | public/scripts/extensions/tts/index.js:685 |
| multi_voice_enabled | false | separate voices for "Quotes", *asterisks* and other text. Voice-map keys are `Char ("Quotes")`, `Char (*Text inside asterisks*)`, `Char (Other text)` | public/scripts/extensions/tts/index.js:910,537-598,618-636,1326-1334 |
| apply_regex / regex_pattern | false / '' | remove text matching a user regex | public/scripts/extensions/tts/index.js:911-912,691-698 |
| playback_rate | 1 | audio speed (hidden for System) | public/scripts/extensions/tts/index.js:909,1553-1559,897 |
| voiceMap | '' | legacy | public/scripts/extensions/tts/index.js:904 |
| `<ProviderName>` | {} | that provider's own settings object, including `voiceMap {char: voiceId}` | public/scripts/extensions/tts/index.js:1061-1065,1081-1086,1383-1386 |

Special voice-map values: `[Default Voice]` and `disabled` (public/scripts/extensions/tts/index.js:55-56). Voice-map rows are the default voice, user, and current character or group members (public/scripts/extensions/tts/index.js:1296-1335).

**Providers** (registry at public/scripts/extensions/tts/index.js:124-153). Each class has `settingsHtml`, `loadSettings`, `checkReady`, `onRefreshClick`, `getVoice`, `generateTts`, `fetchTtsVoiceObjects` and usually `fetchTtsGeneration`.

| registry name | class (file:line) | defaults (cite) | generation endpoint | voices source |
|---|---|---|---|---|
| AllTalk | AllTalkTtsProvider (public/scripts/extensions/tts/alltalk.js:7) | provider_endpoint 'http://localhost:7851', server_version 'v2', language 'en', at_generation_method 'standard_generation', narrator_enabled, narrator voice, RVC voices and pitch, finetuned_model (public/scripts/extensions/tts/alltalk.js:15-28) | `{ep}/api/tts-generate` form POST via doExtrasFetch (public/scripts/extensions/tts/alltalk.js:1018-1019) | `{ep}/api/voices`, `/api/rvcvoices`, `/api/currentsettings`, `/api/ready`; also `/api/reload`, `/api/deepspeed`, `/api/lowvramsetting`, `/api/previewvoice/` (public/scripts/extensions/tts/alltalk.js:348,373,399,439,632,707,733,903) |
| Azure | AzureTtsProvider (public/scripts/extensions/tts/azure.js:6) | region '', voiceMap (public/scripts/extensions/tts/azure.js:16-19) | `/api/azure/generate` (public/scripts/extensions/tts/azure.js:191) | `/api/azure/list` (public/scripts/extensions/tts/azure.js:143) |
| Chatterbox | ChatterboxTtsProvider (public/scripts/extensions/tts/chatterbox.js:5) | endpoint 'http://localhost:8004', voice_mode 'predefined', predefined_voice 'S1', temperature 0.8, exaggeration 0.5, cfg_weight 0.5, seed -1, speed_factor 1, language 'en', split_text, chunk_size 120, output_format 'wav' (public/scripts/extensions/tts/chatterbox.js:13-28) | `{ep}/tts` (public/scripts/extensions/tts/chatterbox.js:609) | `{ep}/get_predefined_voices`, `{ep}/get_reference_files`; ready check `{ep}/api/ui/initial-data` (public/scripts/extensions/tts/chatterbox.js:307,325,278) |
| Chutes | ChutesTtsProvider (public/scripts/extensions/tts/chutes.js:7) | model 'kokoro', speed 1 (public/scripts/extensions/tts/chutes.js:13-17) | `/api/openai/chutes/generate-voice` (public/scripts/extensions/tts/chutes.js:201) | static list (public/scripts/extensions/tts/chutes.js:219-226) |
| Coqui | CoquiTtsProvider (public/scripts/extensions/tts/coqui.js:56) | voiceMap, customVoices, voiceIds, voiceMapDict (public/scripts/extensions/tts/coqui.js:63-68) | Extras `/api/text-to-speech/coqui/generate-tts` (public/scripts/extensions/tts/coqui.js:678-679) | Extras `/api/text-to-speech/coqui/local/get-models`, `coqui-api/check-model-state`, `coqui-api/install-model`; model JSON `/scripts/extensions/tts/coqui_api_models_settings*.json` (public/scripts/extensions/tts/coqui.js:647,595,619,159,179) |
| CosyVoice (Unofficial) | CosyVoiceProvider (public/scripts/extensions/tts/cosyvoice.js:5) | endpoint 'http://localhost:9880', format 'wav', lang 'auto', streaming false (public/scripts/extensions/tts/cosyvoice.js:42-48) | `{ep}/` (public/scripts/extensions/tts/cosyvoice.js:176-178) | `{ep}/speakers` (public/scripts/extensions/tts/cosyvoice.js:138) |
| Edge | EdgeTtsProvider (public/scripts/extensions/tts/edge.js:14) | rate 0, provider 'extras' \| 'plugin' (public/scripts/extensions/tts/edge.js:9-12,24-28) | Extras `/api/edge-tts/generate` or server plugin `/api/plugins/edge-tts/generate` (public/scripts/extensions/tts/edge.js:212-221) | `/api/edge-tts/list` or `/api/plugins/edge-tts/list`; probe `/api/plugins/edge-tts/probe` (public/scripts/extensions/tts/edge.js:230-239,260) |
| ElevenLabs | ElevenLabsTtsProvider (public/scripts/extensions/tts/elevenlabs.js:7) | stability 0.75, similarity_boost 0.75, style_exaggeration 0, speaker_boost true, speed 1.0, model 'eleven_turbo_v2_5' (public/scripts/extensions/tts/elevenlabs.js:12-20) | `/api/speech/elevenlabs/synthesize`; history `/history`, `/history-audio` (public/scripts/extensions/tts/elevenlabs.js:348,388,370) | `/api/speech/elevenlabs/voices`, `/voice-settings`, `/voices/add` (public/scripts/extensions/tts/elevenlabs.js:304,316,424) |
| Electron Hub | ElectronHubTtsProvider (public/scripts/extensions/tts/electronhub.js:7) | model 'tts-1', speed 1, temperature 1, top_p 1, instructions, Dia speaker_transcript / cfg_filter_top_k 25 / cfg_scale 3, MS speech_rate / pitch_adjustment / emotional_style (public/scripts/extensions/tts/electronhub.js:14-30) | `/api/openai/electronhub/generate-voice` (public/scripts/extensions/tts/electronhub.js:443) | models `/api/openai/electronhub/models` (public/scripts/extensions/tts/electronhub.js:188) |
| Google Translate | GoogleTranslateTtsProvider (public/scripts/extensions/tts/google-translate.js:6) | region '' (public/scripts/extensions/tts/google-translate.js:12-15) | `/api/google/generate-voice` (public/scripts/extensions/tts/google-translate.js:124) | `/api/google/list-voices` (public/scripts/extensions/tts/google-translate.js:86) |
| Google Gemini TTS | GoogleNativeTtsProvider (public/scripts/extensions/tts/google-native.js:7) | model 'gemini-3.1-flash-tts-preview', apiType 'makersuite' (public/scripts/extensions/tts/google-native.js:13-17) | `/api/google/generate-native-tts` (public/scripts/extensions/tts/google-native.js:164) | `/api/google/list-native-voices` (public/scripts/extensions/tts/google-native.js:96) |
| GSVI | GSVITtsProvider (public/scripts/extensions/tts/gsvi.js:5) | endpoint 'http://127.0.0.1:5000', language '多语种混合', cha_name, character_emotion 'default', speed 1, top_k 6, top_p 0.85, temperature 0.75, batch_size 10, stream false, stream_chunk_size 100 (public/scripts/extensions/tts/gsvi.js:34-51) | URL `{ep}/tts?…` (public/scripts/extensions/tts/gsvi.js:251) | `{ep}/character_list` (public/scripts/extensions/tts/gsvi.js:55) |
| GPT-SoVITS-Adapter | GptSoVITSAdapterProvider (public/scripts/extensions/tts/gpt-sovits-adapter.js:11) | endpoint 'http://localhost:9881', format 'wav', lang 'auto', streaming false, text_lang 'zh', media_type 'auto' (public/scripts/extensions/tts/gpt-sovits-adapter.js:33-40) | `{ep}/` (public/scripts/extensions/tts/gpt-sovits-adapter.js:210-212) | `{ep}/speakers` (public/scripts/extensions/tts/gpt-sovits-adapter.js:158) |
| GPT-SoVITS-V2 (Unofficial) | GptSovitsV2Provider (public/scripts/extensions/tts/gpt-sovits-v2.js:5) | endpoint 'http://localhost:9880', format 'wav', lang 'auto', streaming false, text_lang 'zh', prompt_lang 'zh' (public/scripts/extensions/tts/gpt-sovits-v2.js:39-47) | `{ep}/` (public/scripts/extensions/tts/gpt-sovits-v2.js:191-193) | `{ep}/speakers` (public/scripts/extensions/tts/gpt-sovits-v2.js:143) |
| Kokoro | KokoroTtsProvider (public/scripts/extensions/tts/kokoro.js:5) | modelId 'onnx-community/Kokoro-82M-v1.0-ONNX', dtype 'q8' (q8/fp32/fp16/q4/q4f16), device 'wasm', defaultVoice 'af_heart', speakingRate 1.0 (public/scripts/extensions/tts/kokoro.js:7-14,213-219) | in-browser Web Worker `kokoro-worker.js` using `lib/kokoro.web.js`; no server (public/scripts/extensions/tts/kokoro.js:91) | static voice list af_*/am_*/bf_*/bm_* (public/scripts/extensions/tts/kokoro.js:16-44) |
| MiniMax | MiniMaxTtsProvider (public/scripts/extensions/tts/minimax.js:8) | apiHost 'https://api.minimax.io', model 'speech-02-hd', speed / volume / pitch ranges, audioSampleRate 32000, bitrate 128000, format 'mp3', customModels, customVoices, customVoiceId (public/scripts/extensions/tts/minimax.js:18-31) | `/api/minimax/generate-voice` (public/scripts/extensions/tts/minimax.js:800) | built-in and custom voices; no listing API (public/scripts/extensions/tts/minimax.js:704-719) |
| Novel | NovelTtsProvider (public/scripts/extensions/tts/novel.js:9) | customVoices [] (public/scripts/extensions/tts/novel.js:19-22) | `/api/novelai/generate-voice` (public/scripts/extensions/tts/novel.js:198) | static list (Ligeia, Aini, …) plus custom seeds (public/scripts/extensions/tts/novel.js:148-160) |
| OpenAI | OpenAITtsProvider (public/scripts/extensions/tts/openai.js:6) | customVoices, model 'tts-1', speed 1, characterInstructions {} (public/scripts/extensions/tts/openai.js:24-30) | `/api/openai/generate-voice` (public/scripts/extensions/tts/openai.js:239) | static `OpenAITtsProvider.voices` (public/scripts/extensions/tts/openai.js:214-215) |
| OpenAI Compatible | OpenAICompatibleTtsProvider (public/scripts/extensions/tts/openai-compatible.js:7) | model 'tts-1', speed 1, available_voices [alloy, echo, fable, onyx, nova, shimmer], provider_endpoint 'http://127.0.0.1:8000/v1/audio/speech' (public/scripts/extensions/tts/openai-compatible.js:14-20) | `/api/openai/custom/generate-voice` (proxied) (public/scripts/extensions/tts/openai-compatible.js:161) | from settings (public/scripts/extensions/tts/openai-compatible.js:136) |
| Pollinations | PollinationsTtsProvider (public/scripts/extensions/tts/pollinations.js:5) | model 'openai-audio' (public/scripts/extensions/tts/pollinations.js:11-15) | `/api/speech/pollinations/generate` (public/scripts/extensions/tts/pollinations.js:133) | `/api/speech/pollinations/voices` (public/scripts/extensions/tts/pollinations.js:91) |
| SBVits2 | SBVits2TtsProvider (public/scripts/extensions/tts/sbvits2.js:5) | endpoint 'http://localhost:5000', sdp_ratio 0.2, noise 0.6, noisew 0.8, length 1, language 'JP', auto_split, split_interval 0.5, assist_text, style 'Neutral', style_weight 1, reference_audio_path (public/scripts/extensions/tts/sbvits2.js:39-53) | `{ep}/voice?…` (public/scripts/extensions/tts/sbvits2.js:303-305) | `{ep}/models/info` (public/scripts/extensions/tts/sbvits2.js:241) |
| Silero | SileroTtsProvider (public/scripts/extensions/tts/silerotts.js:6) | provider_endpoint 'http://localhost:8001/tts' (or Extras `/api/tts`) (public/scripts/extensions/tts/silerotts.js:16-19,58-60) | `{ep}/generate`; session `{ep}/session` (public/scripts/extensions/tts/silerotts.js:124-125,149-150) | `{ep}/speakers` (public/scripts/extensions/tts/silerotts.js:114) |
| SpeechT5 | SpeechT5TtsProvider (public/scripts/extensions/tts/speecht5.js:7) | speakers [], speaker '' (public/scripts/extensions/tts/speecht5.js:18-22) | `/api/speech/synthesize` (server-side transformers) (public/scripts/extensions/tts/speecht5.js:175-176) | static (public/scripts/extensions/tts/speecht5.js:162) |
| System | SystemTtsProvider (public/scripts/extensions/tts/system.js:77) | rate 1, pitch 1 (public/scripts/extensions/tts/system.js:91-95) | browser `speechSynthesis.speak`; plays silence.mp3 as a placeholder (public/scripts/extensions/tts/system.js:237,276) | `speechSynthesis.getVoices()` (public/scripts/extensions/tts/system.js:209) |
| TTS WebUI | TtsWebuiProvider (public/scripts/extensions/tts/tts-webui.js:5) | model 'chatterbox', speed 1, volume 1, endpoint 'http://127.0.0.1:7778/v1/audio/speech', streaming true, chunking and sampling params, seed -1 (public/scripts/extensions/tts/tts-webui.js:15-40) | POST `provider_endpoint` (public/scripts/extensions/tts/tts-webui.js:538) | `{ep with /speech→/voices/<model>}` (public/scripts/extensions/tts/tts-webui.js:373-374) |
| VITS | VITSTtsProvider (public/scripts/extensions/tts/vits.js:5) | endpoint 'http://localhost:23456', format 'wav', lang 'auto', length 1, noise 0.33, noisew 0.4, segment_size 50, streaming false, dim_emotion, sdp_ratio 0.2, emotion, text_prompt, style_text, style_weight (public/scripts/extensions/tts/vits.js:48-63) | `{ep}/voice/{model_type}` (public/scripts/extensions/tts/vits.js:351-358) | `{ep}/voice/speakers` (public/scripts/extensions/tts/vits.js:282) |
| XTTSv2 | XTTSTtsProvider (public/scripts/extensions/tts/xtts.js:6) | endpoint 'http://localhost:8020' (or Extras `/api/tts`), language 'en', temperature 0.75, length_penalty 1, repetition_penalty 5, top_k 50, top_p 0.85, speed 1, enable_text_splitting true, stream_chunk_size 100, streaming false (public/scripts/extensions/tts/xtts.js:51-64,169-171) | `{ep}/tts_to_audio/` or stream `{ep}/tts_stream/?…`; settings `{ep}/set_tts_settings` (public/scripts/extensions/tts/xtts.js:297-301,266-267) | `{ep}/speakers` (public/scripts/extensions/tts/xtts.js:252) |
| Volcengine | VolcengineTtsProvider (public/scripts/extensions/tts/volcengine.js:7) | customVoices, resource_id '', speed 0, provider_endpoint 'https://openspeech.bytedance.com/api/v3/tts/unidirectional' (public/scripts/extensions/tts/volcengine.js:52-58) | `/api/volcengine/generate-voice` (public/scripts/extensions/tts/volcengine.js:297) | built-in and custom list (public/scripts/extensions/tts/volcengine.js:86) |

**Slash command**

| name | aliases | args | purpose | cite |
|---|---|---|---|---|
| /speak | narrate, tts | `voice=` character name from the voice map (optional); unnamed: text (required) | narrate text with the current character's voice or the named one | public/scripts/extensions/tts/index.js:1582-1618,173-209 |

**Events**

| event | handler | cite |
|---|---|---|
| MESSAGE_SWIPED | resetTtsPlayback | public/scripts/extensions/tts/index.js:1574 |
| CHAT_CHANGED, GROUP_UPDATED | onChatChanged (reinit voice map) | public/scripts/extensions/tts/index.js:1575,1577 |
| MESSAGE_DELETED | onMessageDeleted | public/scripts/extensions/tts/index.js:1576 |
| GENERATION_STARTED / ENDED | periodic paragraph narration timer | public/scripts/extensions/tts/index.js:1578-1579 |
| CHARACTER_MESSAGE_RENDERED, USER_MESSAGE_RENDERED (makeLast) | onMessageEvent (auto-narrate) | public/scripts/extensions/tts/index.js:1580-1581 |
| emits TTS_JOB_STARTED, TTS_AUDIO_READY, TTS_JOB_COMPLETE | job lifecycle | public/scripts/extensions/tts/index.js:509,518,533 |

A `ModuleWorkerWrapper(moduleWorker)` runs every 1000 ms (public/scripts/extensions/tts/index.js:43-44,1573). No macros, tools or interceptor.

**UI.**
- Settings: provider select, Reload, the checkboxes listed above, regex block, playback-rate slider, voice-map block, provider settings form, "Available voices" popup with preview (public/scripts/extensions/tts/settings.html:1-102; public/scripts/extensions/tts/index.js:376-398).
- Wand items "TTS Playback" (play/pause) and "Narrate All Chat" (public/scripts/extensions/tts/index.js:430-444).
- Per-message `.mes_narrate` button (public/scripts/extensions/tts/index.js:1567).

---

### 2.15 Vector Storage (vectors)

**Manifest** (`public/scripts/extensions/vectors/manifest.json:1-17`)

| field | value | cite |
|---|---|---|
| display_name | "Vector Storage" | public/scripts/extensions/vectors/manifest.json:2 |
| loading_order | 100 | public/scripts/extensions/vectors/manifest.json:3 |
| optional | ["embeddings"] (Extras) | public/scripts/extensions/vectors/manifest.json:5-7 |
| generate_interceptor | "vectors_rearrangeChat" (= `rearrangeChat`) | public/scripts/extensions/vectors/manifest.json:8; public/scripts/extensions/vectors/index.js:891 |
| js / css | index.js / style.css | public/scripts/extensions/vectors/manifest.json:9-10 |

**Purpose.** Retrieval-augmented generation (RAG) over three kinds of content:
- (a) chat history: relevant past messages are injected under prompt tag `3_vectors`;
- (b) files, meaning chat attachments and Data Bank attachments (tag `4_vectors_data_bank`);
- (c) World Info: vector-activated entries.

Tags are defined at public/scripts/extensions/vectors/index.js:52-53. The batch size is 1 for transformers and ollama, otherwise 5 (public/scripts/extensions/vectors/index.js:56).

**Embedding sources.** UI list at public/scripts/extensions/vectors/settings.html:13-32; server whitelist at src/endpoints/vectors.js:22-43; request-body builder at public/scripts/extensions/vectors/index.js:931-1000; key/URL validation at public/scripts/extensions/vectors/index.js:1074-1120.

| source | label | model setting (default) | extra body / requirement |
|---|---|---|---|
| transformers | Local (Transformers) | server-side | default source (public/scripts/extensions/vectors/index.js:60) |
| openai | OpenAI | openai_model 'text-embedding-ada-002' (ada-002, 3-small, 3-large) (public/scripts/extensions/vectors/index.js:65; settings.html:124-126) | OPENAI key |
| cohere | Cohere | cohere_model 'embed-english-v3.0' (public/scripts/extensions/vectors/index.js:68; settings.html:134-141) | COHERE key |
| mistral | MistralAI | (fixed server-side) | MISTRALAI key (public/scripts/extensions/vectors/index.js:1082) |
| togetherai | TogetherAI | togetherai_model 'togethercomputer/m2-bert-80M-32k-retrieval' (public/scripts/extensions/vectors/index.js:64; settings.html:149-156) | TOGETHERAI key |
| nomicai | NomicAI | (fixed) | NOMICAI key (public/scripts/extensions/vectors/index.js:1084) |
| ollama | Ollama | ollama_model 'mxbai-embed-large', ollama_keep false | apiUrl = alt endpoint or textgen Ollama URL; `keep` (public/scripts/extensions/vectors/index.js:953-957) |
| llamacpp | llama.cpp | - | apiUrl (public/scripts/extensions/vectors/index.js:958-960) |
| vllm | vLLM | vllm_model '' | apiUrl + model (public/scripts/extensions/vectors/index.js:961-964) |
| koboldcpp | KoboldCpp | - | embeddings computed client-side via `/api/backends/kobold/embed` and passed as `embeddings` (public/scripts/extensions/vectors/index.js:1010-1015,1454-1475) |
| webllm | WebLLM Extension | webllm_model '' | embeddings computed in-browser by `WebLlmVectorProvider` (public/scripts/extensions/vectors/index.js:1007-1009,1435; public/scripts/extensions/vectors/webllm.js:1) |
| extras | Extras (deprecated) | - | extrasUrl/extrasKey; needs the `embeddings` module (public/scripts/extensions/vectors/index.js:934-937,1110-1112) |
| palm | Google AI Studio | google_model 'text-embedding-005' (gemini-embedding-001, gemini-embedding-2-preview, …) (public/scripts/extensions/vectors/index.js:73; settings.html:173-178) | api 'makersuite' (public/scripts/extensions/vectors/index.js:968-971) |
| vertexai | Google Vertex AI | google_model | api 'vertexai' + auth mode / region / express project ID from oai_settings (public/scripts/extensions/vectors/index.js:972-978) |
| electronhub | Electron Hub | electronhub_model 'text-embedding-3-small'; list from `/api/openai/electronhub/models` filtered to `/v1/embeddings` | (public/scripts/extensions/vectors/index.js:66,166-172) |
| openrouter | OpenRouter | openrouter_model 'openai/text-embedding-3-large'; list `/api/openrouter/models/embedding` | (public/scripts/extensions/vectors/index.js:67,173-178) |
| chutes | Chutes | chutes_model 'chutes-qwen-qwen3-embedding-8b'; list `/api/openai/chutes/models/embedding` | (public/scripts/extensions/vectors/index.js:74,153-159) |
| nanogpt | NanoGPT | nanogpt_model 'text-embedding-3-small'; list `/api/openai/nanogpt/models/embedding` | (public/scripts/extensions/vectors/index.js:75,160-165) |
| siliconflow | SiliconFlow | siliconflow_model 'Qwen/Qwen3-Embedding-0.6B'; list `/api/openai/siliconflow/models/embedding` | + siliconflow_endpoint (public/scripts/extensions/vectors/index.js:76,179-184,985-988) |
| workers_ai | Cloudflare Workers AI | workers_ai_model (fallback '@cf/baai/bge-m3'); list `/api/openai/workers-ai/models/embedding` | + workers_ai_account_id (public/scripts/extensions/vectors/index.js:185-190,989-992) |

(Here settings.html means public/scripts/extensions/vectors/settings.html.) Sources that need a URL: llamacpp, vllm, ollama, koboldcpp (public/scripts/extensions/vectors/index.js:139). Remote model lists are loaded by `loadRemoteEmbeddingModels` (public/scripts/extensions/vectors/index.js:1324-1365).

**Settings (extension_settings.vectors.\*)**: defaults at public/scripts/extensions/vectors/index.js:58-120. On first run the defaults object is used directly, and it is merged on later loads (public/scripts/extensions/vectors/index.js:1729-1738).

| key | default | meaning | cite |
|---|---|---|---|
| source | 'transformers' | embedding source | :60 |
| alt_endpoint_url / use_alt_endpoint | '' / false | custom URL for ollama, llamacpp, vllm, koboldcpp (`#vector_altEndpoint_address`, `#vector_altEndpointUrl_enabled`) | :61-62, :1767-1775 |
| include_wi | false | "Include in World Info scanning": injected text is scanned by WI | :63 |
| `<src>_model` keys | see source table | model per source | :64-76 |
| summarize / summarize_sent | false / false | vectorize LLM summaries of messages / also summarize the query messages | :77-78, :473-477, :916-922 |
| summary_source | 'main' | main \| extras \| webllm | :79; settings.html:488-490 |
| summary_prompt | "Ignore previous instructions. Summarize…250 words…" | prompt | :80 |
| summary_retries / summary_threshold | 2 / 200 (chars) | retries; minimum length to summarize | :81-82 |
| force_chunk_delimiter | '' | custom chunk boundary | :83, :283-296 |
| enabled_chats | false | chat vectorization | :86 |
| keep_hidden | false | include hidden (system) messages | :87, :463 |
| template | 'Past events:\n{{text}}' | chat injection template | :88, :865-870 |
| depth / position | 2 / IN_PROMPT | chat injection depth and position (radios 2 = before main prompt, 0 = after, 1 = in-chat @ depth) | :89-90; settings.html:431-445 |
| protect | 5 | last N messages are never moved | :91, :806-809,823 |
| insert | 3 | number of chat messages retrieved | :92, :819 |
| query | 2 | number of recent messages used as the query | :93, :910-913 |
| message_chunk_size | 400 | chunk size (chars) for long messages | :94, :487 |
| score_threshold | 0.25 | minimum similarity | :95 |
| enabled_files | false | vectorize chat file attachments | :98, :578 |
| translate_files | false | translate files to English via `globalThis.translate` first | :99, :731-733 |
| size_threshold / chunk_size / chunk_count / overlap_percent | 10 KB / 5000 / 2 / 0 | chat-attachment chunking and retrieval count | :100-103, :599-626 |
| only_custom_boundary | false | split only on the custom delimiter | :104 |
| size_threshold_db / chunk_size_db / chunk_count_db / overlap_percent_db | 5 KB / 2500 / 5 / 0 | Data Bank chunking and retrieval | :107-110, :663-666,680 |
| file_template_db | 'Related information:\n{{text}}' | Data Bank injection template | :111, :695 |
| file_position_db / file_depth_db / file_depth_role_db | IN_PROMPT / 4 / SYSTEM | Data Bank injection position, depth, role (0 System, 1 User, 2 Assistant) | :112-114; settings.html:378-394 |
| enabled_world_info | false | vector-activate WI | :117, :1624 |
| enabled_for_all | false | treat all WI entries as vectorized (otherwise only entries with `vectorized` set; WI field `extensions.vectorized`, public/scripts/world-info.js:2716) | :118, :1659 |
| max_entries | 5 | maximum WI entries activated | :119, :1706 |

(":N" = public/scripts/extensions/vectors/index.js:N; settings.html = public/scripts/extensions/vectors/settings.html.)

**Collection IDs.** Chat collections use the chat ID (public/scripts/extensions/vectors/index.js:455-464). Files use `file_<hash(url)>` (public/scripts/extensions/vectors/index.js:199-200). World Info uses `world_<hash(worldName)>` (public/scripts/extensions/vectors/index.js:1680). Items are `{hash,text,index}` (public/scripts/extensions/vectors/index.js:1687).

**Server endpoints**

| endpoint | purpose | cite |
|---|---|---|
| POST /api/vector/list | saved hashes of a collection | public/scripts/extensions/vectors/index.js:1027 |
| POST /api/vector/insert | embed and insert items | public/scripts/extensions/vectors/index.js:1055 |
| POST /api/vector/delete | delete hashes | public/scripts/extensions/vectors/index.js:1129 |
| POST /api/vector/query | top-K for one collection | public/scripts/extensions/vectors/index.js:1153 |
| POST /api/vector/query-multi | top-K across collections with threshold | public/scripts/extensions/vectors/index.js:1183 |
| POST /api/vector/purge | purge a collection (chat or file) | public/scripts/extensions/vectors/index.js:1216,1246 |
| POST /api/vector/purge-all | purge everything | public/scripts/extensions/vectors/index.js:1272 |
| POST /api/backends/kobold/embed | KoboldCpp client-side embeddings | public/scripts/extensions/vectors/index.js:1455 |
| Extras /api/summarize | Extras summarizer | public/scripts/extensions/vectors/index.js:323-324 |
| model lists (see source table) | populate model selects | public/scripts/extensions/vectors/index.js:152-191 |

Server routes: src/endpoints/vectors.js:472,492,512,530,547,565,583.

**Generate interceptor `rearrangeChat(chat, ctx, abort, type)`** (public/scripts/extensions/vectors/index.js:776-859):
1. Skips quiet generations.
2. Clears both extension prompts.
3. Runs `processFiles` (chat files and Data Bank) and `activateWorldInfo`. WI activation emits `WORLDINFO_FORCE_ACTIVATE` (public/scripts/extensions/vectors/index.js:1725).
4. For chats, queries `insert` matches, removes them from their original positions (keeping the last `protect` messages) and injects them with `template` at `position`/`depth`.

**Slash commands**

| name | aliases | args | purpose | cite |
|---|---|---|---|---|
| /db-ingest | databank-ingest, data-bank-ingest | - | force ingestion of all Data Bank attachments | public/scripts/extensions/vectors/index.js:2100-2108 |
| /db-purge | databank-purge, data-bank-purge | - | purge vectors of all Data Bank attachments | public/scripts/extensions/vectors/index.js:2110-2123 |
| /db-search | databank-search, data-bank-search | `threshold=` 0-1, `count=`, `source=` global\|character\|chat, `return=` chunks or standard return types (default object); unnamed: query | vector search over the Data Bank; returns file URLs or chunk text | public/scripts/extensions/vectors/index.js:2125-2182 |
| /vector-threshold | - | unnamed number 0-1 (optional) | get or set score_threshold | public/scripts/extensions/vectors/index.js:2184-2212 |
| /vector-query | - | unnamed number > 0 | get or set `query` | public/scripts/extensions/vectors/index.js:2214-2242 |
| /vector-max-entries | - | unnamed number > 0 | get or set WI `max_entries` | public/scripts/extensions/vectors/index.js:2244-2272 |
| /vector-chats-state | - | unnamed bool | get or set `enabled_chats` | public/scripts/extensions/vectors/index.js:2274-2298 |
| /vector-files-state | - | unnamed bool | get or set `enabled_files` | public/scripts/extensions/vectors/index.js:2300-2324 |
| /vector-worldinfo-state | - | unnamed bool | get or set `enabled_world_info` | public/scripts/extensions/vectors/index.js:2326-2350 |

**Events**

| event | handler | cite |
|---|---|---|
| MESSAGE_DELETED / EDITED / SENT / RECEIVED / SWIPED | debounced `synchronizeChat` (incremental insert and delete of chat hashes) | public/scripts/extensions/vectors/index.js:2086-2090,893,440-470 |
| CHAT_DELETED, GROUP_CHAT_DELETED | purgeVectorIndex | public/scripts/extensions/vectors/index.js:2091-2092 |
| FILE_ATTACHMENT_DELETED | purgeFileVectorIndex | public/scripts/extensions/vectors/index.js:2093 |
| EXTENSION_SETTINGS_LOADED | load WebLLM models when the WebLLM extension loads | public/scripts/extensions/vectors/index.js:2094-2098 |
| SECRET_WRITTEN / DELETED / ROTATED | NomicAI key indicator | public/scripts/extensions/vectors/index.js:2078-2083 |

No macros or tools.

**UI** (in `#vectors_container`, public/scripts/extensions/vectors/index.js:1742-1743):
- Vectorization source and per-source model fields, query messages, score threshold, chunk boundary, include-in-WI (public/scripts/extensions/vectors/settings.html:12-242).
- World Info section (public/scripts/extensions/vectors/settings.html:254-281).
- Files and Data Bank section with Vectorize All / Purge (public/scripts/extensions/vectors/settings.html:294-403).
- Chat section with template, position/depth, chunk size, protect/insert, summarization, and Vectorize All / Purge / View Stats buttons (public/scripts/extensions/vectors/settings.html:415-519; handlers at public/scripts/extensions/vectors/index.js:203,1488,1501,1529,1606).

---

### 2.16 third-party (folder)

| aspect | detail | cite |
|---|---|---|
| repo contents | only an empty `.gitkeep`; contents are git-ignored | public/scripts/extensions/third-party/.gitkeep; .gitignore:18,54 |
| role | "global" (all-user) extensions dir: `PUBLIC_DIRECTORIES.globalExtensions = 'public/scripts/extensions/third-party'` | src/constants.js:6 |
| per-user dir | `data/<user>/extensions` (`USER_DIRECTORY_TEMPLATE.extensions = 'extensions'`) | src/constants.js:37 |
| install | `POST /api/extensions/install {url, global, branch}`: shallow `git clone --depth 1` (optional branch) into the global dir (admin only) or the user dir | src/endpoints/extensions.js:92-139 |
| update / branches / switch / move / version / delete | POST `/api/extensions/{update,branches,switch,move,version,delete}`; global operations need admin | src/endpoints/extensions.js:169,217,266,329,387,442 |
| discovery | `GET /api/extensions/discover`: built-ins are subfolders of `public/scripts/extensions` except `third-party` (type `system`); user extensions are `third-party/<name>` (type `local`); global extensions are `third-party/<name>` (type `global`, skipped when a user extension has the same name). Folders without `manifest.json` are skipped | src/endpoints/extensions.js:480-530 |
| serving | `/scripts/extensions/third-party/*` is served from the user dir first and falls back to the global dir, with path-traversal checks | src/users.js:1219,1092-1117 |
| client | `fetch('/api/extensions/discover')`; names are prefixed `third-party/` | public/scripts/extensions.js:300,503-526 |
| feature flag | config `extensions.enabled` guards all endpoints | src/endpoints/extensions.js:72-81 |


## 3. Extension system (public/scripts/extensions.js, st-context.js)

### 3.1 Discovery, types and serving

| Aspect | Behavior | Cite |
|---|---|---|
| Discovery call | `GET /api/extensions/discover` → `[{name,type}]` | public/scripts/extensions.js:298-312 |
| Built-in ("system") | every dir in `public/scripts/extensions/` except `third-party`, must contain manifest.json | src/endpoints/extensions.js:505-511 |
| User-local ("local") | dirs in `<user>/extensions`, exposed as `third-party/<folder>` | src/endpoints/extensions.js:514-518 |
| Global ("global") | dirs in `public/scripts/extensions/third-party` (`PUBLIC_DIRECTORIES.globalExtensions`); user-local wins on name clash | src/endpoints/extensions.js:522-527; src/constants.js:6 |
| Serving 3rd-party files | `/scripts/extensions/third-party/*` routed to the user's extensions dir behind `extensionsEnabledFeatureGuard` | src/users.js:1219; src/endpoints/extensions.js:72-81 |
| Repo third-party folder | ships empty (`.gitkeep` only) — populated by installs "for all users" | public/scripts/extensions/third-party/.gitkeep |
| Server kill-switch | `config.yaml extensions.enabled` (old `enableExtensions`); `extensions.autoUpdate`; extension model IDs (`classification`, `captioning`, `embedding`, `speechToText`) | default/config.yaml:297-309; src/config-init.js:45-58 |
| Client when disabled | settings copied but no load; buttons `#third_party_extension_button`, `#extensions_details`, `#extensions_connect` get `.disabled` | public/script.js:8021-8030 |
| Global object | `globalThis.SillyTavern = { libs, getContext }` | public/script.js:293-296 |
| Official check | URL matches `https://github.com/SillyTavern/*` → no 3rd-party warning | public/scripts/extensions.js:69-75 |

### 3.2 manifest.json format

| Field | Type | Meaning | Cite |
|---|---|---|---|
| `display_name` | string | UI name; secondary sort key | extensions.js:49-50, 917 |
| `loading_order` | number | activation order (ascending, `parseInt`), tie → display_name; also interceptor order | extensions.js:49, 571, 2033 |
| `requires` | string[] | **Extras API** modules required (from `GET <extras>/api/modules`); missing → not loaded | extensions.js:578, 592-602, 745-754 |
| `optional` | string[] | Extras modules shown as "Optional modules:" if missing | extensions.js:972-981 |
| `dependencies` | string[] | other extension names required (must exist & be enabled) | extensions.js:579, 604-624 |
| `minimum_client_version` | semver | compared to `CLIENT_VERSION` | extensions.js:570, 580, 586-589 |
| `js` | path | ES module entry, injected as `<script type=module id="<name>-js">` | extensions.js:813-841 |
| `css` | path | `<link rel=stylesheet id="<name>-css">` | extensions.js:781-805 |
| `i18n` | `{locale: file}` | JSON merged via `addLocaleData(currentLocale, …)` before js/css load | extensions.js:848-878 |
| `generate_interceptor` | string | name of a `globalThis` function `(chat, contextSize, abort(immediately), type)` | extensions.js:2024-2047 |
| `hooks` | `{install,update,delete,clean,enable,disable,activate: exportedFnName}` | functions exported from `js` module, awaited max 5000 ms | extensions.js:383-468 |
| `auto_update` | bool | 3rd-party auto-update / update-notification eligibility | extensions.js:1958, 1989, 2008 |
| `version`, `author`, `homePage` | string | shown in details list | extensions.js:918; e.g. public/scripts/extensions/assets/manifest.json |

Built-in manifests (all use `hooks.activate: "init"`):

| Folder | display_name | loading_order | optional (Extras) | interceptor |
|---|---|---|---|---|
| connection-manager | Connection Profiles | 1 | – | – |
| regex | Regex | 1 | – | – |
| translate | Chat Translation | 1 | – | – |
| attachments | Data Bank (Chat Attachments) | 3 | – | – |
| caption | Image Captioning | 4 | caption | – |
| expressions | Character Expressions | 6 | classify | – |
| gallery | Gallery | 6 | – | – |
| memory | Summarize | 9 | summarize | – |
| stable-diffusion | Image Generation | 10 | sd | `SD_ProcessTriggers` (stable-diffusion/index.js:436) |
| tts | TTS | 10 | silero-tts, edge-tts, coqui-tts | – |
| quick-reply | Quick Replies | 12 | – | – |
| assets | Assets | 15 | – | – |
| token-counter | Token Counter | 15 | – | – |
| vectors | Vector Storage | 100 | embeddings | `vectors_rearrangeChat` (vectors/index.js:891) |

(Source: each `public/scripts/extensions/<name>/manifest.json`.)

### 3.3 Load / activation order

| Step | What | Cite |
|---|---|---|
| 1 | `firstLoadInit` → `initExtensions()` (wand button+menu, panel handlers) before settings load | public/script.js:747; extensions.js:2302-2324 |
| 2 | `initExtensionSlashCommands()`, `ToolManager.initToolSlashCommands()` | public/script.js:748-749 |
| 3 | `getSettings()` → if `data.enable_extensions` → `loadExtensionSettings(settings, versionChanged, autoUpdate)` | public/script.js:8021-8025 |
| 4 | merge `settings.extension_settings` into `extension_settings`; populate Extras URL/key/autoconnect/notify inputs | extensions.js:1783-1792 |
| 5 | emit `EXTENSIONS_FIRST_LOAD`; discover; fetch every `manifest.json` in parallel; drop folders w/o manifest | extensions.js:1794-1807, 537-562 |
| 6 | if version changed & autoUpdate → `autoUpdateExtensions(false)` (only `auto_update` 3rd-party; non-admins skip global) | extensions.js:1809-1811, 1988-2015 |
| 7 | `activateExtensions()`: sort by loading_order; check min version, Extras `requires`, `dependencies`, disabled list; load locale → (js+css in parallel) sequentially awaited; add to `activeExtensions`; call `activate` hook; failures → `extensionLoadErrors` (details button turns `.warning`) | extensions.js:568-665 |
| 8 | if `autoConnect` → `connectToApi(apiUrl)` → `GET <url>/api/modules` (Bearer apiKey) → sets `modules`, re-runs activation, emits `EXTRAS_CONNECTED` | extensions.js:1813-1815, 739-760, 260-280 |
| 9 | emit `EXTENSION_SETTINGS_LOADED` | public/script.js:8025 |
| 10 | `doDailyExtensionUpdatesCheck()` (if `notifyUpdates`) then `APP_INITIALIZED`, `APP_READY` | public/script.js:786-790; extensions.js:1819-1825 |

Enable/disable: `enableExtension/disableExtension(name, reload)` call `enable`/`disable` hook, edit `extension_settings.disabledExtensions`, `saveSettings()`, reload page (or defer reload until popup closes) — extensions.js:473-503. There is no hot-unload; every state change reloads the page (extensions.js:1324-1326).

### 3.4 Core `extension_settings` object (persisted in settings.json)

Keys & defaults at extensions.js:141-226: `apiUrl` ('http://localhost:5100', :63), `apiKey`, `autoConnect`, `notifyUpdates`, `disabledExtensions[]`, `expressionOverrides[]`, `memory{}`, `note{default,chara[],wiAddition[]}` (Author's Note), `caption{refine_mode}`, `expressions{api,custom[],showDefault,translate,fallback_expression,llmPrompt,allowMultiple:true,rerollIfSame,promptType:'raw'}`, `connectionManager{selectedProfile,profiles[]}`, `dice{}`, `regex[]`, `regex_presets[]`, `character_allowed_regex[]`, `preset_allowed_regex{}`, `tts{}`, `sd{prompts,character_prompts,character_negative_prompts}`, `chromadb{}`, `translate{}`, `objective{}`, `quickReply{}`, `randomizer{controls[],fluctuation:0.1,enabled}`, `speech_recognition{}`, `rvc{}`, `hypebot{}`, `vectors{}`, `variables{global{}}` (global vars live here), `attachments[]` (global Data Bank), `character_attachments{avatar:[]}`, `disabled_attachments[]`, `gallery{folders{},sort:'dateAsc'}`.

### 3.5 Extensions panel & management UI (frontend side)

| Element / action | Behavior | Cite |
|---|---|---|
| `#rm_extensions_block` drawer | top-bar Extensions panel | public/index.html:5754 |
| `#extensions_notify_updates` | toggles `notifyUpdates`, runs update check | index.html:5761; extensions.js:725-732 |
| `#extensions_details` ("Manage extensions") | popup `.extensions_info`: built-in list, installed list, load errors, Extras module info; toolbar Update all / Update enabled / Sort (by loading order vs name, `accountStorage['extensions_sortByName']`) / bulk Toggle + Restore | index.html:5764; extensions.js:1125-1327 |
| per-extension card | toggle, type icon (global=fa-server, local=fa-user, system=fa-cog), name/author/version, Extras module status, buttons `btn_update` (hidden until update found), `btn_clean` (if `clean` hook), `btn_branch` & `btn_move` (admin only), `btn_delete` | extensions.js:890-1056 |
| Version check | `POST /api/extensions/version {extensionName, global}` → `isUpToDate`, `remoteUrl`; 5 concurrent | extensions.js:1599-1625, 1831-1928 |
| Update | `POST /api/extensions/update {extensionName, global}` → `shortCommitHash`; then `update` hook; non-admin can't update global | extensions.js:1335-1395 |
| Delete | confirm (+ optional "clean data" checkbox if `clean` hook) → `delete` hook → `POST /api/extensions/delete`; reload | extensions.js:1404-1428, 1561-1595 |
| Clean | calls `clean` hook only | extensions.js:1430-1455 |
| Branches | `POST /api/extensions/branches` list, `POST /api/extensions/switch {branch}` | extensions.js:1457-1493, 1632-1696 |
| Move local↔global | `POST /api/extensions/move {extensionName, source, destination}` (admin) | extensions.js:1495-1559 |
| Install | `#third_party_extension_button` → `openThirdPartyExtensionMenu(suggestUrl)`: template `installExtension`, INPUT popup with URL + optional `extension_branch_name`; admins get "Install just for me" / "Install for all users" (global=true) | index.html:5768; extensions.js:2232-2264 |
| Install request | only http/https URLs; non-official → `thirdPartyExtensionWarning` confirm (dismissable via `accountStorage['extensionInstallationWarningShown']`); `POST /api/extensions/install {url, global, branch}` → `{display_name, extensionPath, folderName}`; reload settings, emit `EXTENSION_SETTINGS_LOADED`, call `install` hook | extensions.js:1698-1776 |
| Auto-check updates | `checkForExtensionUpdates(force)` → toast listing names (only manifests with `auto_update`) | extensions.js:1929-1986 |
| Extras API block | `#extensions_url`, `#extensions_api_key`, `#extensions_connect`, `#extensions_autoconnect`, `#extensions_status` | index.html:5816-5827; extensions.js:668-686, 767-775 |
| Settings mount points | `#extensions_settings` (assets, typing_indicator, expressions, sd, tts, rvc, stt, audio, silence, objective, blip, live2d, vrm, timelines, webllm, rss) and `#extensions_settings2` (websearch, emulatorjs, qr, translation, caption, idle, summarize, hypebot, regex, vectors, randomizer, chromadb) `*_container` divs | index.html:5773-5805 |
| Wand menu | `#extensionsMenuButton` appended to `#leftSendForm`; `#extensionsMenu` (Popper `top-start`) with containers `data_bank_wand_container, attach_file_wand_container, sd_wand_container, caption_wand_container, gallery_wand_container, tts_wand_container, screen_share_…, prompt_inspector_…, emulatorjs_…, notebook_…, chess_…, token_counter_…, dice_…, objective_…, translate_wand_container`; button hidden until a menu item is visible (1 s poll) | extensions.js:228-243, 688-723; public/scripts/templates/wandMenu.html:1-17; templates/wandButton.html:1 |

Extension slash commands (public/scripts/extensions-slashcommands.js): `/extension-enable` (:88, arg `reload`), `/extension-disable` (:129), `/extension-toggle` (:170, `reload`, `state`), `/extension-state` (:229), `/extension-exists` (:265), `/reload-page` (:297).

### 3.6 `getContext()` API surface (public/scripts/st-context.js:115-309)

Obtained via `import { getContext } from '../../extensions.js'` (re-export, extensions.js:14-18) or `SillyTavern.getContext()` (script.js:293).

| Group | Members (line) |
|---|---|
| State (live refs/snapshots) | `accountStorage` 117, `chat` 118, `characters` 119, `groups` 120, `name1` 121, `name2` 122, `characterId` 123, `groupId` 124, `chatId` 125-127, `onlineStatus` 133, `maxContext` 134, `chatMetadata` 135, `streamingProcessor` 137, `extensionPrompts` 152, `mainApi` 200, `extensionSettings` 201, `tags` 218, `tagMap` 219, `menuType` 220, `chatCompletionSettings` 227, `textCompletionSettings` 228, `powerUserSettings` 229, `CONNECT_API_MAP` 285 |
| Chat ops | `getCurrentChatId` 128, `reloadCurrentChat` 130, `renameChat` 131, `saveChat` (saveChatConditional) 155, `openCharacterChat` 156, `openGroupChat` 157, `printMessages` 290, `clearChat` 291, `addOneMessage` 140, `deleteLastMessage` 141, `deleteMessage` 142, `sendSystemMessage` 159, `saveReply` 162, `updateMessageBlock` 238, `appendMediaToMessage` 239, `ensureMessageMediaIsArray` 240, `getMediaDisplay` 241, `getMediaIndex` 242, `scrollChatToBottom` 243, `scrollOnMediaLoad` 244, `messageFormatting` 211, `messageFormatter` 246 |
| Swipes | `swipe.{left,right,to,show,hide,refresh,isAllowed,state()}` 248-257 |
| Metadata / settings save | `saveSettingsDebounced` 132, `saveMetadataDebounced` 136, `updateChatMetadata` 154, `saveMetadata` 158 |
| Events | `eventSource` 138, `eventTypes` 139, `event_types` (deprecated alias) 223 |
| Generation | `generate` (Generate) 143, `sendStreamingRequest` 144, `sendGenerationRequest` 145, `stopGeneration` 146, `generateQuietPrompt` 204, `generateRaw` 205, `generateRawData` 206, `extractMessageFromData` 287, `ChatCompletionService` 292, `TextCompletionService` 293, `ConnectionManagerRequestService` 294, `getTextGenServer` 286, `getChatCompletionModel` 289, `getPresetManager` 288, `activateSendButtons` 160, `deactivateSendButtons` 161, `setExtensionPrompt` 153 |
| Tokenizers | `tokenizers` 147, `getTextTokens` 148, `getTokenCount` (deprecated) 150, `getTokenCountAsync` 151, `getTokenizerModel` 203 |
| Macros / substitution | `substituteParams` 163, `substituteParamsExtended` 164, `registerMacro` / `unregisterMacro` (deprecated → `macros`) 180-182, `macros` 245, `registerHelper` (no-op) 178 |
| Slash commands | `SlashCommandParser` 165, `SlashCommand` 166, `SlashCommandArgument` 167, `SlashCommandNamedArgument` 168, `SlashCommandEnumValue` 169, `ARGUMENT_TYPE` 170, `executeSlashCommandsWithOptions` 171, `registerSlashCommand` (deprecated) 173, `executeSlashCommands` (deprecated) 175 |
| Tools | `registerFunctionTool` 183, `unregisterFunctionTool` 184, `isToolCallingSupported` 185, `canPerformToolCalls` 186, `ToolManager` 187 |
| UI / popups / templates | `renderExtensionTemplate` (deprecated) 190, `renderExtensionTemplateAsync` 191, `callPopup` (deprecated) 194, `callGenericPopup` 195, `Popup` 224, `POPUP_TYPE` 225, `POPUP_RESULT` 226, `showLoader`/`hideLoader` (deprecated) 197-199, `loader` 247, `updateReasoningUI` 295 |
| Characters / groups | `getCharacters` 230, `getOneCharacter` 231, `getCharacterCardFields` 232, `getCharacterSource` 233, `selectCharacterById` 210, `createCharacterData` (create_save) 221, `writeExtensionField` 207, `writeExtensionFieldBulk` 208, `unshallowCharacter` 298, `unshallowGroupMembers` 299, `getThumbnailUrl` 209, `importTags` 235, `importFromExternalUrl` 234 |
| World Info | `loadWorldInfo` 278, `saveWorldInfo` 279, `reloadWorldInfoEditor` 280, `updateWorldInfoList` 281, `convertCharacterBook` 282, `getWorldInfoPrompt` 283, `getWorldInfoNames()` 284 |
| Variables | `variables.local.{get,set,del,add,inc,dec,has}` 259-267, `variables.global.{…}` 268-276 |
| Reasoning | `parseReasoningFromString` 296, `getReasoningTemplateByName` 297 |
| i18n | `t` 214, `translate` 215, `getCurrentLocale` 216, `addLocaleData` 217 |
| Data Bank | `registerDataBankScraper` 192 |
| Extensions meta | `ModuleWorkerWrapper` 202, `getExtensionManifest` 300, `openThirdPartyExtensionMenu` 301, `registerDebugFunction` 188 |
| Utils | `timestampToMoment` 176, `uuidv4` 236, `humanizedDateTime` 237, `shouldSendOnEnter` 212, `isMobile` 213, `getRequestHeaders` 129 |
| Constants | `symbols.ignore` (IGNORE_SYMBOL) 302-304, `constants.unset` (UNSET_VALUE) 305-307 |

Other `extensions.js` exports used by built-ins: `extension_settings`, `getApiUrl`, `modules`, `doExtrasFetch` (adds `Authorization: Bearer <apiKey>`, :260-280), `renderExtensionTemplateAsync`, `saveMetadataDebounced` (:89), `cancelDebouncedMetadataSave` (:81), `findExtension` (:508), `enableExtension`/`disableExtension`, `installExtension`, `deleteExtension`, `writeExtensionField` (writes `data.extensions.<key>` then `POST /api/characters/merge-attributes`, :2070-2111), `writeExtensionFieldBulk` (:2152-2175), `UNSET_VALUE` (:2061), `isOfficialExtension`, `getAuthorFromUrl` (:2283), `EMPTY_AUTHOR`.

### 3.7 Hook points for extensions

| Hook | Contract | Cite |
|---|---|---|
| Module worker | `ModuleWorkerWrapper` = `SimpleMutex`: `new ModuleWorkerWrapper(fn)`, `.update(...args)` skips if previous call still running. Used by expressions (2000 ms interval, 10 s while streaming), tts (1000 ms), vectors (debounced on chat events) | util/SimpleMutex.js:4-44; extensions/expressions/index.js:42-43, 2340-2343; extensions/tts/index.js:43-44, 1573; extensions/vectors/index.js:122, 893 |
| Events | `eventSource.on/once/makeFirst/makeLast/removeListener`, async `emit` (awaits listeners sequentially), `emitAndWait`; `APP_READY` & `APP_INITIALIZED` auto-fire for late listeners | lib/eventemitter.js:31-195; scripts/events.js:113 |
| Event types | APP_INITIALIZED, APP_READY, EXTRAS_CONNECTED, MESSAGE_SWIPED/SENT/RECEIVED/EDITED/DELETED/UPDATED/FILE_EMBEDDED/REASONING_EDITED/REASONING_DELETED/SWIPE_DELETED, MORE_MESSAGES_LOADED, IMPERSONATE_READY, CHAT_CHANGED (`chat_id_changed`), CHAT_LOADED (`chatLoaded`), GENERATION_AFTER_COMMANDS/STARTED/STOPPED/ENDED, SD_PROMPT_PROCESSING, EXTENSIONS_FIRST_LOAD, EXTENSION_SETTINGS_LOADED, SETTINGS_LOADED/UPDATED/LOADED_BEFORE/LOADED_AFTER, GROUP_UPDATED, MOVABLE_PANELS_RESET, CHATCOMPLETION_SOURCE_CHANGED/MODEL_CHANGED, OAI_PRESET_CHANGED_BEFORE/AFTER/EXPORT_READY/IMPORT_READY, WORLDINFO_SETTINGS_UPDATED/UPDATED, CHARACTER_EDITOR_OPENED/EDITED/PAGE_LOADED, CHARACTER_GROUP_OVERLAY_STATE_CHANGE_BEFORE/AFTER, USER_MESSAGE_RENDERED, CHARACTER_MESSAGE_RENDERED, FORCE_SET_BACKGROUND, CHAT_DELETED/CREATED/RENAMED, GROUP_CHAT_DELETED/CREATED, GENERATE_BEFORE_COMBINE_PROMPTS, GENERATE_AFTER_COMBINE_PROMPTS, GENERATE_AFTER_DATA, GROUP_MEMBER_DRAFTED, GROUP_WRAPPER_STARTED/FINISHED, WORLD_INFO_ACTIVATED, TEXT_COMPLETION_SETTINGS_READY, CHAT_COMPLETION_SETTINGS_READY, CHAT_COMPLETION_PROMPT_READY, CHARACTER_FIRST_MESSAGE_SELECTED, CHARACTER_DELETED (`characterDeleted`), CHARACTER_DUPLICATED/RENAMED/RENAMED_IN_PAST_CHAT, STREAM_TOKEN_RECEIVED (+deprecated SMOOTH_ alias), STREAM_REASONING_DONE, FILE_ATTACHMENT_DELETED, WORLDINFO_FORCE_ACTIVATE, OPEN_CHARACTER_LIBRARY, ONLINE_STATUS_CHANGED, IMAGE_SWIPED, CONNECTION_PROFILE_LOADED/CREATED/DELETED/UPDATED, TOOL_CALLS_PERFORMED/RENDERED, CHARACTER_MANAGEMENT_DROPDOWN, SECRET_WRITTEN/DELETED/ROTATED/EDITED, PRESET_CHANGED/DELETED/RENAMED/RENAMED_BEFORE, MAIN_API_CHANGED, WORLDINFO_ENTRIES_LOADED, WORLDINFO_SCAN_DONE, MEDIA_ATTACHMENT_DELETED, PERSONA_CHANGED/CREATED/UPDATED/RENAMED/DELETED, TTS_JOB_STARTED/AUDIO_READY/JOB_COMPLETE, ITEMIZED_PROMPTS_LOADED/SAVED/DELETED | public/scripts/events.js:3-111 |
| Generate interceptors | `runGenerationInterceptors(coreChat, this_max_context, type)` called from `Generate()`; each `globalThis[manifest.generate_interceptor](chat, contextSize, abort, type)` in loading order; may mutate `chat` copy; `abort(true)` stops chain; returns aborted | extensions.js:2024-2047; public/script.js:4564 |
| Prompt injection | `setExtensionPrompt(key, value, position, depth, scan=false, role=SYSTEM, filter=null)`; positions `NONE -1, IN_PROMPT 0, IN_CHAT 1, BEFORE_PROMPT 2`; roles `SYSTEM 0, USER 1, ASSISTANT 2`; `MAX_INJECTION_DEPTH 10000` | public/script.js:8926-8935, 484-500 |
| Macros (new engine) | `macros.register(name, {handler, description, category, unnamedArgs, list, strictArgs, returns, returnType, displayOverride, exampleUsage, delayArgResolution, aliases[]})`, `macros.registerAlias`, `macros.registry.unregisterMacro`; handler ctx has `args, unnamedArgs, list, flags, isScoped, raw, env, resolve(), normalize(), trimContent()` | macros/macro-system.js:44-60; macros/engine/MacroRegistry.js:63-124, 198, 327 |
| Macros (legacy) | `MacrosParser.registerMacro/unregisterMacro` (deprecated) | st-context.js:179-182 |
| Function tools | `ToolManager.registerFunctionTool({name, displayName, description, parameters(JSON schema), action, formatMessage, shouldRegister, stealth})`; `unregisterFunctionTool`, `invokeFunctionTool`, `isToolCallingSupported`, `canPerformToolCalls(type)`; stealth = result not shown, no follow-up | tool-calling.js:115-200, 247-305, 325, 614, 694 |
| Slash commands | `SlashCommandParser.addCommandObject(SlashCommand.fromProps({...}))` | st-context.js:165-175; extensions-slashcommands.js:88 |
| Data Bank scrapers | `registerDataBankScraper(scraper)` | st-context.js:192 |
| Character card storage | `writeExtensionField(chid, key, value)` → `data.extensions[key]` | extensions.js:2070 |
| Chat metadata | `chatMetadata[...]` + `saveMetadataDebounced()` | st-context.js:135-136 |
| Templates | `renderExtensionTemplateAsync(name, id, data, sanitize, localize)` loads `scripts/extensions/<name>/<id>.html` via Handlebars, DOMPurify, i18n | extensions.js:137-139; templates.js:60-110 |
| Locales | manifest `i18n` or `addLocaleData(locale, data)` | extensions.js:848-878 |


## 4. Other frontend modules

All paths are relative to the SillyTavern root. "SC" means slash command. Every network call goes through `getRequestHeaders()` (CSRF and JSON headers) unless stated otherwise. `login.js` is the exception: it sends `X-CSRF-Token` by hand (public/scripts/login.js:26-29).

### 4.1 data-maid.js — Data Maid cleanup dialog

| Aspect | Detail |
|---|---|
| Purpose | Scans user data for orphaned or loose files and lets the user view, download or permanently delete them (public/scripts/data-maid.js:15-17) |
| Entry | `initDataMaid()` binds `#data_maid_button` and opens `new DataMaidDialog().open()` (public/scripts/data-maid.js:396-403) |
| Categories (report keys) | `files`, `images`, `chats`, `groupChats`, `avatarThumbnails`, `backgroundThumbnails`, `personaThumbnails`, `chatBackups`, `settingsBackups` (public/scripts/data-maid.js:23-59). Each record is `{name, hash (SHA-256 of path), parent?, size?, mtime?}` (src/endpoints/data-maid.js:28-32) |
| Endpoints | `POST /api/data-maid/report` returns `{report, token}` (public/scripts/data-maid.js:69, :9-11); `POST /api/data-maid/finalize {token}` runs when the dialog closes (public/scripts/data-maid.js:87-90, :390-392); `GET /api/data-maid/view?hash=&token=` is used for both view and download (public/scripts/data-maid.js:285); `POST /api/data-maid/delete {hashes[], token}` (public/scripts/data-maid.js:332-336) |
| UI | The main dialog is a `POPUP_TYPE.TEXT` popup, wide and large (public/scripts/data-maid.js:388). Each category comes from the `dataMaidCategory` template (public/scripts/data-maid.js:218). Per item: view (image/audio/video element for `images`, `avatarThumbnails` and `backgroundThumbnails`, otherwise a read-only textarea) in a `POPUP_TYPE.DISPLAY` popup (public/scripts/data-maid.js:315-321, :373-379). Also download, delete-one and delete-all, each with a "THIS CANNOT BE UNDONE" confirm (public/scripts/data-maid.js:241-260) |
| Settings | None |

### 4.2 chat-backups.js — Chat backups browser (in the Chat History popup)

| Aspect | Detail |
|---|---|
| Purpose | Adds a collapsible "Backups" button before `#select_chat_search` and a backup list before `#select_chat_div` (public/scripts/chat-backups.js:268-319) |
| Export | `addChatBackupsBrowser()` renders the button and list and refreshes them if open (public/scripts/chat-backups.js:326-335). It is called from public/script.js:8578 |
| Endpoints | `POST /api/backups/chat/get` lists backups as `ChatInfo[]` (`file_name`, `last_mes`, `file_size`, `chat_items`), sorted by `last_mes` (public/scripts/chat-backups.js:156-169). `POST /api/backups/chat/download {name}` is used to view (public/scripts/chat-backups.js:29) and to restore (public/scripts/chat-backups.js:74). `POST /api/backups/chat/delete {name}` (public/scripts/chat-backups.js:128) |
| Restore flow | Downloads the blob and accepts only `.jsonl`. Builds FormData `{file_type, avatar(file), avatar_url, user_name, character_name}` and calls `importGroupChat` or `importCharacterChat`, then `displayPastChats` (public/scripts/chat-backups.js:86-113) |
| UI | View opens a read-only textarea in a TEXT popup (public/scripts/chat-backups.js:60). Delete asks `Popup.show.confirm` first (public/scripts/chat-backups.js:123). Each list row shows the date in `lll` format, the size and the message count (public/scripts/chat-backups.js:179) |

### 4.3 server-history.js — Server URL history autocomplete

| Aspect | Detail |
|---|---|
| Purpose | Adds a jQuery-UI autocomplete to every `[data-server-history="<label>"]` input. It suggests the 5 most recently used URLs per label (public/scripts/server-history.js:9-24, :39-50) |
| Behaviour | Picking a suggestion triggers a click on the matching `[data-server-connect]` buttons (public/scripts/server-history.js:26-37). Clicking a connect button stores `{label, url, lastConnection}`, valid URLs only (public/scripts/server-history.js:56-81) |
| Settings | `power_user.servers[]`, saved with `saveSettingsDebounced` (public/scripts/server-history.js:73-79) |
| Export | `initServerHistory()` (public/scripts/server-history.js:83-86) |

### 4.4 user.js — Multi-user account UI and admin panel

Exports: `currentUser`, `accountsEnabled` (public/scripts/user.js:10-11), `setUserControls(isEnabled)` (public/scripts/user.js:21), `isAdmin()` (returns true when accounts are disabled) (public/scripts/user.js:38-48), `getCurrentUserHandle()` (falls back to `'default-user'`) (public/scripts/user.js:54-56).

Top-bar buttons: `#logout_button`, `#admin_button` (shown only when accounts are on and the user is admin) and `#account_button` (public/scripts/user.js:920-928, :73). A keep-alive `POST /api/ping?extend=1` runs every 10 minutes while logged in (public/scripts/user.js:14, :906, :929-933).

| Feature | UI / template | Endpoint (body) |
|---|---|---|
| Get current user | — | `GET /api/users/me` (public/scripts/user.js:64) |
| List users (admin) | `admin` template, `.usersList` (public/scripts/user.js:780-827) | `POST /api/users/get` (public/scripts/user.js:85) |
| Enable / disable | `.userEnableButton` / `.userDisableButton` (public/scripts/user.js:794-795) | `/api/users/enable {handle}` (public/scripts/user.js:108); `/api/users/disable {handle}` (public/scripts/user.js:128) |
| Promote / demote | `.userPromoteButton` / `.userDemoteButton` (public/scripts/user.js:796-797) | `/api/users/promote` (public/scripts/user.js:154); `/api/users/demote` (public/scripts/user.js:179) |
| Create user | `.userCreateForm` with fields `_name`, `handle`, `password`, `confirm`. The handle is auto-slugified from the display name (public/scripts/user.js:834-850; public/scripts/templates/admin.html:89-102). Password and confirm must match; the leading `_` is stripped from keys (public/scripts/user.js:201-228) | `/api/users/create` (public/scripts/user.js:230); `/api/users/slugify {text}` returns text (public/scripts/user.js:884) |
| Change password | `changePassword` template. The current-password field is hidden for admins (public/scripts/user.js:296-320) | `/api/users/change-password {handle,newPassword,oldPassword}` (public/scripts/user.js:322-325) |
| Delete user | `deleteUser` template. The handle must be retyped; the `deleteUserData` checkbox sets `purge`; deleting yourself is blocked (public/scripts/user.js:346-374) | `/api/users/delete {handle,purge}` (public/scripts/user.js:376-379) |
| Change name | `changeName` INPUT popup (public/scripts/user.js:438-441) | `/api/users/change-name {handle,name}` (public/scripts/user.js:449-452) |
| Change avatar | File input, then a CROP popup with 1:1 aspect (public/scripts/user.js:744-755). Remove sends `''` (public/scripts/user.js:717-721) | `/api/users/change-avatar {avatar(base64),handle}` (public/scripts/user.js:764-767) |
| Backup user data | Downloads a zip named from `Content-Disposition`. Warns if secrets are excluded (`canViewSecrets`) (public/scripts/user.js:255-287) | `/api/users/backup {handle}` (public/scripts/user.js:258) |
| Reset settings | `resetSettings` template, asks for password (public/scripts/user.js:400-411) | `/api/users/reset-settings {handle,password}` (public/scripts/user.js:413-416) |
| Reset everything | Two steps: step 1 requests a code, then the `userReset` popup asks for `password` and `code` (public/scripts/user.js:621-670) | `/api/users/reset-step1` (public/scripts/user.js:623); `/api/users/reset-step2 {password,code}` (public/scripts/user.js:655-658) |
| Settings snapshots | `snapshotsView` template: list with name, date and size, a lazily loaded content drawer, restore and make buttons (public/scripts/user.js:586-613) | `/api/settings/get-snapshots` (public/scripts/user.js:539); `/api/settings/load-snapshot {name}` (public/scripts/user.js:511); `/api/settings/restore-snapshot {name}` then reload (public/scripts/user.js:486); `/api/settings/make-snapshot` (public/scripts/user.js:565) |
| User profile | `userProfile` template with buttons for snapshots, name, password, backup, reset settings, reset all and avatar. `[data-require-accounts]` elements are hidden when accounts are off (public/scripts/user.js:674-735) | — |
| Logout | Redirects with `?noauto=true` so auto-login (Authelia, Basic, single user) is skipped (public/scripts/user.js:861-876) | `/api/users/logout` (public/scripts/user.js:862) |

### 4.5 login.js and public/login.html — Login page

| Aspect | Detail |
|---|---|
| Page | Standalone page that loads jQuery and `scripts/login.js` as a module (public/login.html:86-87). Elements: `#userList`, `#handleEntryBlock`/`#userHandle`, `#passwordEntryBlock`/`#userPassword`/`#loginButton`/`#recoverPassword`, `#passwordRecoveryBlock`/`#recoveryCode`/`#newPassword`/`#sendRecovery`/`#cancelRecovery`, `#errorMessage`, `#normalLoginPrompt`/`#discreetLoginPrompt` (public/login.html:42-78) |
| CSRF | `GET /csrf-token` returns `{token}` (public/scripts/login.js:13-16). The token is sent as `X-CSRF-Token` on every POST |
| User list | `POST /api/users/list` (public/scripts/login.js:24). **HTTP 204 turns on discreet login** (public/scripts/login.js:37-40). The server returns 204 when discreet login is on (src/endpoints/users-public.js:31) |
| Normal login | Renders user tiles (avatar, name, handle) (public/scripts/login.js:221-237). Users without a password log in immediately (public/scripts/login.js:148-150). Otherwise the password block is shown (public/scripts/login.js:152-157) |
| Discreet login | Shows handle and password inputs; the user list is hidden (public/scripts/login.js:242-267) |
| Login | `POST /api/users/login {handle,password}`. On `data.handle` it redirects to `/` and drops `noauto` (public/scripts/login.js:108-139, :184-197) |
| Password recovery | `POST /api/users/recover-step1 {handle}` (the server prints a code to its console) and shows the recovery block (public/scripts/login.js:52-68). Then `POST /api/users/recover-step2 {handle,code,newPassword}` and automatic login (public/scripts/login.js:77-100) |
| Keyboard | Enter submits recovery or login, depending on which block is visible (public/scripts/login.js:282-290) |
| SSO / Basic auth | Not in client code. The server's `tryAutoLogin` runs single-user (no password), Authelia header, Authentik header, then per-user Basic auth, and skips all of them when `?noauto` is present (src/users.js:751-775; flags at src/users.js:30-32). Basic-auth middleware: src/middleware/basicAuth.js:23-75 |

### 4.6 scrapers.js — Data Bank scrapers

`ScraperManager` (static) offers `registerDataBankScraper(scraper)` (rejects duplicate ids), `getDataBankScrapers()` (returns `{id,name,description,iconClass,iconAvailable}`), `runDataBankScraper(id)` and `isScraperAvailable(id)` (public/scripts/scrapers.js:30-87). Extensions reach it through `registerDataBankScraper` in st-context.js. Every scraper implements `isAvailable()` and `scrape()`, which returns `File[]` (public/scripts/scrapers.js:17). `initScrapers()` registers them in this order: file, text, web, mediawiki, fandom, youtube (public/scripts/scrapers.js:574-581).

| id | Name | Params / UI | Endpoint | Output |
|---|---|---|---|---|
| `file` | File | Native multi-file picker, `accept=*/*` (public/scripts/scrapers.js:216-246) | — | Selected files |
| `text` | Notepad | `notepad` template: `notepadFileName` (default `Untitled - <date>`), `notepadFileContent`. CONFIRM popup with Save (public/scripts/scrapers.js:94-136) | — | `Notepad - <name>.txt` |
| `web` | Web | `web-scrape` INPUT popup, one URL per line, 4 rows; invalid URLs dropped (public/scripts/scrapers.js:141-185) | `POST /api/search/visit {url}`, once per link (public/scripts/scrapers.js:193-197) | `<title> - <domain> - <ts>.html` (public/scripts/scrapers.js:199-204) |
| `mediawiki` | MediaWiki | `mediawiki-scrape`: `scrapeInput` (URL), `scrapeFilter` (regex), `scrapeOutput` (`single` or `multi`) (public/scripts/scrapers.js:249-298) | Availability: `POST /api/plugins/fandom/probe-mediawiki` (public/scripts/scrapers.js:260). Scrape: `POST /api/plugins/fandom/scrape-mediawiki {url,filter}` (public/scripts/scrapers.js:301-305) | multi: one `<title>.txt` per page. single: `<url>.txt` combined (public/scripts/scrapers.js:315-330) |
| `fandom` | Fandom | `fandom-scrape`: `fandomScrapeInput` (name or URL; the id comes from the subdomain), `fandomScrapeFilter`, `fandomScrapeOutput` (public/scripts/scrapers.js:338-405, :373-378) | Availability: `POST /api/plugins/fandom/probe` (public/scripts/scrapers.js:353). Scrape: `POST /api/plugins/fandom/scrape {fandom,filter}` (public/scripts/scrapers.js:408-412) | Same as MediaWiki (public/scripts/scrapers.js:422-437) |
| `youtube` | YouTube | `youtube-scrape` INPUT popup (URL or 11-character ID) plus `youtubeLanguageCode` (public/scripts/scrapers.js:528-546). The ID parser handles youtu.be, shorts, embed and `v=` links (public/scripts/scrapers.js:513-522) | `POST /api/search/transcript {id,lang}` returns text (public/scripts/scrapers.js:558-562) | `YouTube - <id> - <ts>.txt` |
| Git/GitHub | — | **Not in 1.19 core.** Only the six scrapers above are registered (public/scripts/scrapers.js:574-581). Git scrapers come from third-party extensions through `registerDataBankScraper` | — | — |

The only slash command here is `/yt-script [lang=<iso639-1>] <url|id>`, which returns the transcript (public/scripts/scrapers.js:473-498). The Fandom and MediaWiki scrapers need the server plugin `fandom`; `isAvailable` is false without it.

### 4.7 audio-player.js — Custom inline audio player

| Aspect | Detail |
|---|---|
| Export | `class AudioPlayer(audioElement, containerElement, options)` with options `{title, autoplay, volume, onPlay, onPause, onEnded, onTimeUpdate, onVolumeChange}` (public/scripts/audio-player.js:3-30) |
| Elements | `.audio-player-title`, `-play-pause`, `-current-time`, `-total-time`, `-progress`, `-progress-bar`, `-volume` (public/scripts/audio-player.js:93-110). Template at public/index.html:7714-7727 |
| API | `play`, `pause`, `togglePlay`, `seek`, `setVolume`, `mute`, `unmute`, `toggleMute`, `setSrc`, `setTitle`, `destroy`. A MutationObserver destroys the player when its DOM is removed (public/scripts/audio-player.js:117, :465-581) |
| Usage | One instance per audio attachment in a message (public/script.js:2351) |
| Endpoints / settings | None |

### 4.8 bookmarks.js — Checkpoints, branches and solo-to-group conversion

| Aspect | Detail |
|---|---|
| Concepts | **Checkpoint**: a named copy of the chat up to `mesId`. The source message stores `extra.bookmark_link`; the new chat metadata is `{main_chat, integrity: uuid}` (public/scripts/bookmarks.js:255-305). **Branch**: an automatically named copy (`<main> - Branch #N`). It is opened right away and pushed to `extra.branches[]`. It can take a specific swipe (public/scripts/bookmarks.js:187-245, :452-472) |
| Exports | `showBookmarksButtons` (public/scripts/bookmarks.js:129), `createBranch` (public/scripts/bookmarks.js:187), `createNewBookmark` (public/scripts/bookmarks.js:255), `updateBookmarkDisplay` (public/scripts/bookmarks.js:309), `convertSoloToGroupChat` (public/scripts/bookmarks.js:331), `branchChat` (public/scripts/bookmarks.js:452), `initBookmarks` (public/scripts/bookmarks.js:683) |
| Endpoints | `POST /api/characters/chats {avatar_url, simple:true}` lists existing names (public/scripts/bookmarks.js:72-75). Group conversion: `POST /api/groups/create` (public/scripts/bookmarks.js:381), then `POST /api/chats/group/save {id, chat}` (public/scripts/bookmarks.js:431) |
| UI | Options menu entries `#option_new_bookmark`, `#option_back_to_main` and `#option_convert_to_group` (public/scripts/bookmarks.js:684-686). The checkpoint name comes from `Popup.show.input('Create Checkpoint')` (public/scripts/bookmarks.js:101). Clicking `.mes_bookmark` (flag) or `.select_chat_block` opens that chat; Shift-click creates a new checkpoint (public/scripts/bookmarks.js:688-723). Message buttons: `.mes_create_bookmark` and `.mes_create_branch` (public/scripts/bookmarks.js:725-737). The convert confirm says it cannot be reverted (public/scripts/bookmarks.js:342). Itemized prompts are copied to the new chat (public/scripts/bookmarks.js:285, :465) |
| SC | `/branch-create` (public/scripts/bookmarks.js:495), `/checkpoint-create` (public/scripts/bookmarks.js:524), `/checkpoint-go` (public/scripts/bookmarks.js:577), `/checkpoint-exit` (public/scripts/bookmarks.js:613), `/checkpoint-parent` (public/scripts/bookmarks.js:622), `/checkpoint-get` (public/scripts/bookmarks.js:631), `/checkpoint-list` (public/scripts/bookmarks.js:654) |

### 4.9 samplerSelect.js — Text Completion sampler visibility selector

| Aspect | Detail |
|---|---|
| Purpose | `#samplerSelectButton` opens a popup that lists the Text Completion samplers (`TGsamplerNames` minus meta keys) with checkboxes. It shows or hides the related DOM blocks (public/scripts/samplerSelect.js:26-66, :219-226, :439) |
| Sampler to block mapping | json_schema, grammar, cfg, mirostat, dry, xtc, dynatemp, banned_tokens, sampler_order (kcpp and lcpp), sampler_priority (ooba and aphrodite), contrastive, beam search, smoothing (public/scripts/samplerSelect.js:71-160) |
| Storage | localforage instance `SillyTavern_TextCompletions`, key `selectedSamplers` = `{[tcApiType]: {[sampler]: bool, st_manual_priority: bool}}` (public/scripts/samplerSelect.js:22, :316-336) |
| Exports | `validateDisabledSamplers` (public/scripts/samplerSelect.js:275), `loadApiSelectedSamplers` (public/scripts/samplerSelect.js:316), `saveApiSelectedSamplers` (public/scripts/samplerSelect.js:330), `resetApiSelectedSamplers` (public/scripts/samplerSelect.js:345), `setApiSamplersState` (public/scripts/samplerSelect.js:367), `getAllManualApiSamplers` (public/scripts/samplerSelect.js:381), `getActiveManualApiSamplers` (public/scripts/samplerSelect.js:394), `toggleSamplerManualPriority` (public/scripts/samplerSelect.js:416), `isSamplerManualPriorityEnabled` (public/scripts/samplerSelect.js:429), `initCustomSelectedSamplers` (public/scripts/samplerSelect.js:437) |
| UI | `#resetSelectedSamplers`, and `#prioritizeManuallySelectedSamplers` (manual selection overrides automatic visibility) (public/scripts/samplerSelect.js:41-64) |

### 4.10 logprobs.js — Token Probabilities panel and token reroll

| Aspect | Detail |
|---|---|
| State | In memory only: `state.messageLogprobs` is a Map keyed by a hash of `{name, chat index, text}`. It keeps the last 100 (`MAX_MESSAGE_LOGPROBS`) (public/scripts/logprobs.js:20, :61-64, :543-552, :531-540). Data per message: `{created, hash, messageId, swipeId, api, messageLogprobs: [{token, topLogprobs:[[tok, logprob]]}], continueFrom}` (public/scripts/logprobs.js:31-50) |
| Exports | `saveLogprobsForActiveMessage(logprobs, continueFrom)`. For NovelAI it converts token IDs to text with `decodeTextTokens` (public/scripts/logprobs.js:509-541, :575-600). `initLogprobs()` (public/scripts/logprobs.js:603) |
| UI | `#logprobsViewer` panel toggled by `#option_toggle_logprobs` and closed by `#logprobsViewerClose` (public/scripts/logprobs.js:332-368, :606-607). It shows `#logprobs_generation_output` (clickable tokens with visible whitespace) and the top-candidates list as `exp(logprob)` percentages with the log value in a tooltip (public/scripts/logprobs.js:74-236). It explains when `power_user.request_token_probabilities` is off or smooth streaming is on (public/scripts/logprobs.js:84-90) |
| Alternative-token reroll | Takes the tokens up to the clicked one, swaps in the alternative, creates a new swipe holding that prefix, and runs `Generate('continue')` (public/scripts/logprobs.js:257-279, :316-325, :369). This is unsupported for the OpenAI API and shows a popup (public/scripts/logprobs.js:262-267). `#logprobsReroll` and prefix clicks reroll from the continue prefix (public/scripts/logprobs.js:21, :292-300). New swipes keep a reasoning block based on the `power_user.reasoning` prefix and suffix (public/scripts/logprobs.js:381-383) |
| Re-render events | CHAT_CHANGED, CHARACTER_MESSAGE_RENDERED, IMPERSONATE_READY, MESSAGE_DELETED, MESSAGE_EDITED, MESSAGE_SWIPED (public/scripts/logprobs.js:608-613) |
| Endpoints / SC | None |

### 4.11 swipe-picker.js — "Jump to Swipe" picker

| Aspect | Detail |
|---|---|
| Eligibility | `canOpenSwipePickerForMessage`: the message has more than 1 swipe, is not a user message, is not a small system message, and is not `swipeable:false` (public/scripts/swipe-picker.js:17-35). `canJumpToSwipeForMessage` also requires swiping to be allowed (public/scripts/swipe-picker.js:42-45) |
| Triggers | Click on `.swipes-counter.swipe-picker-enabled` (long-press on mobile), Space key, or the `.mes_swipe_picker` button (public/scripts/swipe-picker.js:412-443) |
| Popup | Large and wider, with a "Swipe ID" input (1..N, validated) (public/scripts/swipe-picker.js:299-335). Each row shows `#n [Current]`, swipe details and a preview; it has an expand toggle, a copy button, a branch button that calls `branchChat(messageId,{swipeId})`, and delete with confirm and `deleteSwipe` (public/scripts/swipe-picker.js:160-265, :211-221, :387-388). Double-click jumps to the swipe |
| Jump | `swipe(null, direction, {source: SWIPE_SOURCE.SWIPE_PICKER, forceMesId, forceSwipeId})` (public/scripts/swipe-picker.js:401-409) |
| Endpoints / settings | None |

### 4.12 stats.js — User and character statistics

| Aspect | Detail |
|---|---|
| Data | `charStats[avatar] = {total_gen_time, user_msg_count, non_user_msg_count, user_word_count, non_user_word_count, total_swipe_count, date_last_chat, date_first_chat}` (public/scripts/stats.js:157-167). Totals are aggregated for the user view (public/scripts/stats.js:40-100) |
| Endpoints | `POST /api/stats/get {}` (public/scripts/stats.js:179); `POST /api/stats/recreate {}` (debug function `refreshStats`) (public/scripts/stats.js:202, :331); `POST /api/stats/update <charStats>` (public/scripts/stats.js:238-241) |
| Update hook | `statMesProcess(line, type, ...)` runs per generated or sent message. It counts words and handles append, continue, appendFinal and swipe (public/scripts/stats.js:271-324) |
| UI | `.rm_stats_button` opens character stats (public/scripts/stats.js:327-329). `userStatsHandler()` is called from public/script.js:12536. The report is a TEXT popup (public/scripts/stats.js:101-129) |
| Exports | `userStatsHandler`, `characterStatsHandler`, `getStats`, `statMesProcess`, `charStats`, `initStats` (public/scripts/stats.js:326, :334) |

### 4.13 itemized-prompts.js — Prompt inspector

| Aspect | Detail |
|---|---|
| Storage | localforage `SillyTavern_Prompts`, keyed by chatId, holding an `itemizedPrompts[]` array (public/scripts/itemized-prompts.js:15-16, :22-56) |
| Exports | `itemizedPrompts` (public/scripts/itemized-prompts.js:16), `loadItemizedPrompts` (public/scripts/itemized-prompts.js:22), `saveItemizedPrompts` (public/scripts/itemized-prompts.js:46), `replaceItemizedPromptText` (public/scripts/itemized-prompts.js:65), `deleteItemizedPrompts` (public/scripts/itemized-prompts.js:83), `clearItemizedPrompts` (public/scripts/itemized-prompts.js:99), `itemizedParams` (public/scripts/itemized-prompts.js:109), `findItemizedPromptSet` (public/scripts/itemized-prompts.js:228), `promptItemize` (public/scripts/itemized-prompts.js:248), `initItemizedPrompts` (public/scripts/itemized-prompts.js:335), `swapItemizedPrompts` (public/scripts/itemized-prompts.js:365), `deleteItemizedPromptForMessage` (public/scripts/itemized-prompts.js:389) |
| Token breakdown fields | `charDescriptionTokens`, `charPersonalityTokens`, `scenarioTextTokens`, `userPersonaStringTokens`, `worldInfoStringTokens`, `allAnchorsTokens`, `summarizeStringTokens`, `authorsNoteStringTokens`, `smartContextStringTokens`, before/after scenario anchors, `zeroDepthAnchorTokens`, `chatInjects`, `chatVectorsStringTokens`, `dataBankVectorsStringTokens`, `modelUsed`, `apiUsed`, `presetName`, `messagesCount`, `examplesCount` (public/scripts/itemized-prompts.js:109-227) |
| UI | Clicking `.mes_prompt` (pointerup) opens the inspector (public/scripts/itemized-prompts.js:344-350). Template `itemizationChat` for the OpenAI API, `itemizationText` otherwise (public/scripts/itemized-prompts.js:263-265). Buttons: `#diffPrevPrompt` (diff against the previous prompt with diff_match_patch), `#copyPromptToClipboard`, and `#showRawPrompt` (raw prompt popup) (public/scripts/itemized-prompts.js:270-329) |
| Events | Deletes stored prompts on CHAT_DELETED and GROUP_CHAT_DELETED (public/scripts/itemized-prompts.js:352-357). Emits ITEMIZED_PROMPTS_LOADED, SAVED and DELETED (public/scripts/itemized-prompts.js:34, :53, :89) |
| Debug | `clearPrompts` (public/scripts/itemized-prompts.js:336) |

### 4.14 reasoning.js — Reasoning (thinking) blocks, UI side

| Aspect | Detail |
|---|---|
| Settings (`power_user.reasoning`) | `name` (template), `prefix`, `suffix`, `separator`, `auto_parse`, `auto_expand`, `show_hidden`, `add_to_prompts`, `max_additions`. Bound to `#reasoning_select`, `#reasoning_prefix`, `#reasoning_suffix`, `#reasoning_separator`, `#reasoning_auto_parse`, `#reasoning_auto_expand`, `#reasoning_show_hidden`, `#reasoning_add_to_prompts`, `#reasoning_max_additions` (public/scripts/reasoning.js:40-50, :801-870) |
| Templates | `reasoning_templates` load from settings data (`data.reasoning`). Default is `'Think XML'`. Legacy custom values migrate to a `[Migrated] Custom` preset through the reasoning preset manager (public/scripts/reasoning.js:32-34, :1655-1690) |
| Message fields | `extra.reasoning`, `extra.reasoning_duration`, `extra.reasoning_type` (`model`, `parsed`, `manual`, `edited`), `extra.reasoning_display_text`, signature (public/scripts/reasoning.js:57-62, :355-368, :428-437) |
| Classes | `ReasoningHandler`: streaming state machine (`none`, `thinking`, `done`, `hidden`), auto-parse while streaming using prefix and suffix, DOM updates (public/scripts/reasoning.js:265-276, :481-520). `PromptReasoning`: adds past reasoning to prompts (prefix + reasoning + suffix + separator), capped at `max_additions` (public/scripts/reasoning.js:671-760) |
| Exports | `extractReasoningFromData` (per API, e.g. OpenRouter `choices[0].reasoning`, Ollama `thinking`, OpenAI only when `show_thoughts` is on) (public/scripts/reasoning.js:112-130); `extractReasoningSignatureFromData` (public/scripts/reasoning.js:179); `isHiddenReasoningModel` (public/scripts/reasoning.js:221); `updateReasoningUI` (public/scripts/reasoning.js:254); `removeReasoningFromString` (public/scripts/reasoning.js:1429); `getReasoningTemplateByName` (public/scripts/reasoning.js:1444); `parseReasoningFromString(str,{strict})` (regex `^\s*?prefix(.*?)suffix`) (public/scripts/reasoning.js:1461-1490); `formatReasoning` (public/scripts/reasoning.js:1503); `parseReasoningInSwipes` (public/scripts/reasoning.js:1533); `loadReasoningTemplates` (public/scripts/reasoning.js:1655); `initReasoning` (public/scripts/reasoning.js:1696) |
| Message UI actions | Toggle details and header (public/scripts/reasoning.js:1220-1226), copy (public/scripts/reasoning.js:1245, :1396), edit / done / cancel (public/scripts/reasoning.js:1250, :1303, :1328), close all (public/scripts/reasoning.js:1295), add reasoning while editing `.mes_edit_add_reasoning` (public/scripts/reasoning.js:1342), delete with confirm (public/scripts/reasoning.js:1372-1388) |
| Auto-parse hooks | MESSAGE_RECEIVED and MESSAGE_UPDATED parse the message; GENERATION_STOPPED, GENERATION_ENDED and CHAT_CHANGED clear the latest prompt reasoning; IMPERSONATE_READY runs first (public/scripts/reasoning.js:1554-1630) |
| Macros | `{{reasoningPrefix}}`, `{{reasoningSuffix}}`, `{{reasoningSeparator}}` (public/scripts/reasoning.js:1190-1205) |
| SC | `/reasoning-get` (alias `get-reasoning`) (public/scripts/reasoning.js:901); `/reasoning-set` (`set-reasoning`) with args `at` and `collapse` (public/scripts/reasoning.js:921); `/reasoning-parse` (`parse-reasoning`) with args `regex`, `return`, `strict` (public/scripts/reasoning.js:971); `/reasoning-format` (`format-reasoning`) (public/scripts/reasoning.js:1041); `/reasoning-template` (`reasoning-formatting`, `reasoning-preset`) with `quiet` (public/scripts/reasoning.js:1079); `/reasoning-collapse` (public/scripts/reasoning.js:1146); `/reasoning-expand` (public/scripts/reasoning.js:1158); `/reasoning-toggle` (public/scripts/reasoning.js:1170) |

### 4.15 tags.js — Tag management and folder tags

| Aspect | Detail |
|---|---|
| Data | `tags[]` and `tag_map{entityKey: tagId[]}`, loaded from settings (default tags: Plain Text, OpenAI, W++, Boostyle, PList, AliChat) (public/scripts/tags.js:290-297, :614-617). Tag object: `{id, name, folder_type, filter_state, sort_order, color, color2, create_date, is_hidden_on_character_card}` (public/scripts/tags.js:323-338) |
| Folder tags ("bogus folders") | `TAG_FOLDER_TYPES` `OPEN`, `CLOSED`, `NONE` (default `NONE`) (public/scripts/tags.js:316-321). Gated by `power_user.bogus_folders` (public/scripts/tags.js:604). Helpers: `isBogusFolder`, `getOpenBogusFolders`, `chooseBogusFolder` (public/scripts/tags.js:447-503). Toggled with `.tag_as_folder` (public/scripts/tags.js:2082) |
| Actionable filter tags | FAV, GROUP, FOLDER, VIEW (Manage tags), HINT (Show tag list), UNFILTER (Clear all) (public/scripts/tags.js:269-276) |
| Exports | `TAG_FOLDER_TYPES`, `TAG_FOLDER_DEFAULT_TYPE`, `tags`, `tag_map`, `filterByTagState`, `isBogusFolder`, `isBogusFolderOpen`, `chooseBogusFolder`, `getTagBlock`, `loadTagsSettings`, `printTagFilters`, `getTagsList`, `printTagList`, `appendTagToList`, `createTagMapFromList`, `renameTagKey`, `importTags`, `sortTags`, `compareTagsForSort`, `removeTagFromMap` (public/scripts/tags.js:34-56). Also `tag_filter_type` (public/scripts/tags.js:198), `tag_import_setting` (ASK, NONE, ALL, ONLY_EXISTING) (public/scripts/tags.js:246-251), `tag_sort_mode` (manual, alphabetical, by_entries) (public/scripts/tags.js:254-258), `getTagKeyForEntity` (public/scripts/tags.js:691), `searchCharByName` (public/scripts/tags.js:760), `addTagsToEntity` (public/scripts/tags.js:782), `removeTagFromEntity` (public/scripts/tags.js:821), `applyTagsOnCharacterSelect` / `applyTagsOnGroupSelect` (public/scripts/tags.js:1665, :1678), `createTagInput` (public/scripts/tags.js:1700), `applyCharacterTagsToMessageDivs` (public/scripts/tags.js:2610), `initTags` (public/scripts/tags.js:2779) |
| Import | On character import, `handleTagImport` follows `power_user.tag_import_setting`. The ASK popup can remember the choice (public/scripts/tags.js:999-1078) |
| Manage Tags popup | `tagManagement` template (public/scripts/tags.js:1711-1731). Sort mode `#tag_sort_mode_select` sets `power_user.tag_sort_mode` (public/scripts/tags.js:1719-1723). Drag reorder sets `sort_order` (public/scripts/tags.js:1734-1745). Actions: create (public/scripts/tags.js:1983), rename (public/scripts/tags.js:2166), two color pickers (bg `color`, fg `color2`) (public/scripts/tags.js:2185), delete with confirm (public/scripts/tags.js:2117-2137), backup to `tags_YYYYMMDD.json` `{tags, tag_map}` (public/scripts/tags.js:1934-1943), restore with overwrite prompt (public/scripts/tags.js:1811-1836), prune unused tags (public/scripts/tags.js:1945-1959) |
| Filter persistence | `accountStorage` keys `<CharacterList|GroupCandidates|GroupMembers>_tag_<id>` and actionable keys (public/scripts/tags.js:177-190, :534-541, :1376-1380, :1397-1400). Tag filter bar visibility: `power_user.show_tag_filters`, `show_tag_filters_group_candidates`, `show_tag_filters_group_members` (public/scripts/tags.js:211-243) |
| SC | `/tag-add` (public/scripts/tags.js:2352), `/tag-remove` (public/scripts/tags.js:2399), `/tag-exists` (public/scripts/tags.js:2445), `/tag-list` (public/scripts/tags.js:2489), `/tag-import` with `mode` (public/scripts/tags.js:2526) |
| Endpoints | None. Tags save with settings |

### 4.16 filters.js — FilterHelper

| Aspect | Detail |
|---|---|
| Enums | `FILTER_TYPES`: search, tag, folder, fav, group, world_info_search, persona_search (public/scripts/filters.js:15-23). `FILTER_STATES`: SELECTED, EXCLUDED, UNDEFINED, with default UNDEFINED (public/scripts/filters.js:35-41). `fuzzySearchCategories`: characters, worldInfo, personas, tags, groups (public/scripts/filters.js:62-68) |
| Class | `FilterHelper(onDataChanged)` (public/scripts/filters.js:78-97). Methods: `hasAnyFilter`, `wiSearchFilter`, `personaSearchFilter`, `tagFilter`, `favFilter`, `groupFilter`, `folderFilter`, `searchFilter`, `setFilterData`, `getFilterData`, `applyFilters`, score cache methods, `clearFuzzySearchCaches` (public/scripts/filters.js:113-455) |
| Fuzzy search | When `power_user.fuzzy_search` is on, it scores characters, groups and tags with Fuse and caches the scores (public/scripts/filters.js:325-337) |
| Export | `isFilterState(a,b)` (public/scripts/filters.js:49) |

### 4.17 chats.js — Attachments, embedded media, hidden messages, Data Bank manager

| Area | Detail |
|---|---|
| Hidden messages | `hideChatMessageRange(start,end,unhide,nameFilter)` sets `message.is_system` and the DOM `is_system` attribute, then saves (public/scripts/chats.js:147-169). `hideChatMessage` and `unhideChatMessage` are deprecated wrappers (public/scripts/chats.js:178-191). Buttons `.mes_hide` and `.mes_unhide` (public/scripts/chats.js:2110-2121). The SCs `/hide` and `/unhide` live in public/scripts/slash-commands.js:1836, :1859 |
| Message file attach | `populateFileAttachment(message)`: media files (image, video, audio) are saved with `saveBase64AsFile` into `extra.media[] {url,type}`, plus `extra.media_index` and `extra.inline_image`. Other files are uploaded into `extra.files[] {url,size,name,created}` (public/scripts/chats.js:198-262). `#attachFile` collects multiple files through a DataTransfer (public/scripts/chats.js:2185-2208). `validateFile`: 350 MB limit for non-media, and binary files are rejected unless a converter exists (public/scripts/chats.js:60, :329-346) |
| Converters | pdf, html, markdown, epub, docx/xlsx/pptx, odt/odp/ods are converted to text (public/scripts/chats.js:67-78). `registerFileConverter(mime, fn)` adds more (public/scripts/chats.js:1887) |
| Prompt inclusion | `appendFileContent(message, text)` adds the text of `extra.files` (public/scripts/chats.js:508-534) |
| File / media UI | `.mes_file_open` / `.mes_file_delete` (public/scripts/chats.js:2122-2137). `viewMessageFile` (public/scripts/chats.js:434). `.mes_embed` (`embedMessageFile`) (public/scripts/chats.js:468, :2214). Media: expand (public/scripts/chats.js:875), delete with confirm (public/scripts/chats.js:978-988), display-mode switch `extra.media_display` (public/scripts/chats.js:1068-1086), image swipe through `extra.media_index` (public/scripts/chats.js:2061-2108) |
| Endpoints | `POST /api/files/upload {name,data(base64)}` (public/scripts/chats.js:276-282); `GET <file url>` (public/scripts/chats.js:305); `POST /api/images/delete {path}` (public/scripts/chats.js:1099-1102); `POST /api/files/delete {path}` (public/scripts/chats.js:1130-1133); `POST /api/files/verify {urls}` (public/scripts/chats.js:1832-1835) |
| Data Bank (attachment manager) | `#manageAttachments` opens the manager (public/scripts/chats.js:2210, :1334-1623). Sources: `global` (`extension_settings.attachments`), `character` (`extension_settings.character_attachments[avatar]`), `chat` (`chat_metadata.attachments`) (public/scripts/chats.js:61-65, :1733-1745). Disabled list: `extension_settings.disabled_attachments[url]` (public/scripts/chats.js:1226-1244). Actions: open (public/scripts/chats.js:1157), edit (public/scripts/chats.js:1174), download (public/scripts/chats.js:1210), enable / disable, move (public/scripts/chats.js:1252), delete (public/scripts/chats.js:1287), run scraper (public/scripts/chats.js:1657). Sort is stored in `accountStorage` `DataBank_sortField` / `DataBank_sortOrder` (public/scripts/chats.js:1529-1547) |
| Data Bank exports | `uploadFileAttachmentToServer(file,target)` saves as `<ts>_<hash>.txt` after conversion (public/scripts/chats.js:1691-1748); `getDataBankAttachments(includeDisabled)` (public/scripts/chats.js:1779); `getDataBankAttachmentsForSource` (public/scripts/chats.js:1794); `deleteAttachment` (public/scripts/chats.js:1287) |
| Creator-notes styles | `StylesPreference` stored in accountStorage as `AllowGlobalStyles-<avatar>` (public/scripts/chats.js:643-674). Confirm dialog on CHAT_CHANGED (public/scripts/chats.js:745-765, :2421). `#creators_note_styles_button` (public/scripts/chats.js:2327). `formatCreatorNotes` (public/scripts/chats.js:684) |
| External media | `isExternalMediaAllowed()` uses `power_user.forbid_external_media` plus per-entity allowed and forbidden overrides (public/scripts/chats.js:852-867). Overrides dialog `.open_media_overrides` (public/scripts/chats.js:821, :2301-2325). DOMPurify hooks (public/scripts/chats.js:1901). The `mediaWarningShown:` key is in accountStorage (public/scripts/chats.js:2037-2048) |
| Assistant (neutral) chat | `preserveNeutralChat` / `restoreNeutralChat` (public/scripts/chats.js:1857-1865). Export and import of the temporary assistant chat as JSONL via `.assistant_note_export` / `.assistant_note_import` (public/scripts/chats.js:2138-2183) |
| Misc | `.editor_maximize` opens a full-size editor popup (public/scripts/chats.js:2220-2288). `encodeStyleTags` / `decodeStyleTags` scope message `<style>` tags (public/scripts/chats.js:536-551). `initChatUtilities` (public/scripts/chats.js:2109) |

### 4.18 action-loader.js and action-loader-slashcommands.js — Loader overlay and toasts

| Aspect | Detail |
|---|---|
| Options | `{blocking=true, toastMode='stoppable', slug, message='Generating...', title, stopTooltip='Stop', overlayContent, onStop (default stopGeneration), onHide}` (public/scripts/action-loader.js:32-43). `ActionLoaderToastMode`: none, static, stoppable (public/scripts/action-loader.js:23-30) |
| API | `loader = {show, hide, active, get, isBlocking, ToastMode, Handle, createOverlay}` (public/scripts/action-loader.js:328-376). `showActionLoader` (public/scripts/action-loader.js:423), `hideActionLoader` (public/scripts/action-loader.js:432), `getActiveLoaderHandles` (public/scripts/action-loader.js:453), `getLoaderHandleById` (public/scripts/action-loader.js:462), `createDefaultLoaderOverlay` (public/scripts/action-loader.js:487). `ActionLoaderHandle` has `id`, `slug`, `stop()`, `hide()` (public/scripts/action-loader.js:76, :237-292). The overlay is a popup and counts blocking loaders (public/scripts/action-loader.js:63, :530-555) |
| SC | `/loader-wrap` (public/scripts/action-loader-slashcommands.js:60), `/loader-show` (public/scripts/action-loader-slashcommands.js:180), `/loader-hide` (public/scripts/action-loader-slashcommands.js:292), `/loader-stop` (public/scripts/action-loader-slashcommands.js:332). Arguments: blocking, toast, message, title, slug, stopTooltip, onStop, onHide, handle (public/scripts/action-loader-slashcommands.js:95-346) |

### 4.19 custom-request.js — Direct generation services (used by extensions and Connection Manager)

| Class | Detail |
|---|---|
| `TextCompletionService` (`TYPE='textgenerationwebui'`) | `createRequestData` (public/scripts/custom-request.js:86), `sendRequest(data, extract, signal)`: non-streaming goes to `getGenerateUrl(TYPE)`; streaming goes to `POST /api/backends/text-completions/generate` and returns an async generator (public/scripts/custom-request.js:118-147). `constructPrompt` applies an instruct template to a message array (public/scripts/custom-request.js:198). `processRequest(data,{presetName,instructName,instructSettings})` (public/scripts/custom-request.js:283-300). `presetToGeneratePayload` (public/scripts/custom-request.js:395) |
| `ChatCompletionService` (`TYPE='openai'`) | `createRequestData` (public/scripts/custom-request.js:428), `sendRequest` calls `POST /api/backends/chat-completions/generate` (public/scripts/custom-request.js:462-463), `processRequest` (public/scripts/custom-request.js:544), `presetToGeneratePayload` (public/scripts/custom-request.js:575) |
| Output | Extracted `{content, reasoning}` (via `extractReasoningFromData`) (public/scripts/custom-request.js:137-144) |

### 4.20 streaming-display.js — Floating streaming panel

`StreamingDisplay` is a toast-like panel for progress outside the chat, such as Connection Manager streams. It attaches inside the topmost open `<dialog>` (public/scripts/streaming-display.js:1-19, :35). Methods: `show({label, icon, onStop})` (public/scripts/streaming-display.js:76), `toggleMinimize` (public/scripts/streaming-display.js:206), `setLabel` (public/scripts/streaming-display.js:248), `updateReasoning` (public/scripts/streaming-display.js:261), `updateContent` (rendered with `messageFormatting`) (public/scripts/streaming-display.js:276), `markStopped` (public/scripts/streaming-display.js:301), `complete({label, delay=3000})` (public/scripts/streaming-display.js:334), `hide` (public/scripts/streaming-display.js:372). No endpoints or settings.

### 4.21 sse-stream.js — SSE parser and smooth streaming

| Aspect | Detail |
|---|---|
| `EventSourceStream` | A TransformStream that splits events on blank lines and parses `data:`, `event:` and `id:` fields into `MessageEvent` objects (public/scripts/sse-stream.js:10-70) |
| `SmoothEventSourceStream` | Re-emits provider deltas one character at a time with a delay. The delay comes from `power_user.smooth_streaming_speed`, with punctuation pauses ×25 for `.!?` and ×12.5 for `,` and newline (public/scripts/sse-stream.js:88-106, :340-379). Reasoning is not delayed when `smooth_streaming_no_think` is on, and there is no delay when the window lacks focus (public/scripts/sse-stream.js:365) |
| Providers parsed | Cohere, Claude (text and thinking), Google AI Studio and Vertex, NovelAI / KoboldCpp, OpenAI-compatible (public/scripts/sse-stream.js:115-213) |
| Export | `getEventSourceStream()` returns the smooth stream when `power_user.smooth_streaming` is on (public/scripts/sse-stream.js:381-387). Default export `EventSourceStream` (public/scripts/sse-stream.js:389) |

### 4.22 system-messages.js — Built-in system messages

| Aspect | Detail |
|---|---|
| Types | help, welcome, empty, generic, narrator, comment, slash_commands, formatting, hotkeys, macros, welcome_prompt, assistant_note, assistant_message (public/scripts/system-messages.js:18-32) |
| Init | `initSystemMessages()` builds messages from the templates `help`, `hotkeys`, `formatting`, `welcome` (with `displayVersion`), `welcomePrompt` and `assistantNote`. All have `swipeable:false` (public/scripts/system-messages.js:34-95). `SAFETY_CHAT` holds a "You deleted a character/chat…" message (public/scripts/system-messages.js:13, :107) |
| Exports | `getSystemMessageByType(type,text,extra)`: `slash_commands` and `macros` get generated help (public/scripts/system-messages.js:122-151). `sendSystemMessage` pushes the message to the chat and mounts `SlashCommandBrowser` or `MacroBrowser` in place of the help spinner (public/scripts/system-messages.js:158-182) |

### 4.23 authors-note.js — Author's Note

| Aspect | Detail |
|---|---|
| Per-chat metadata keys | `note_prompt`, `note_interval`, `note_depth`, `note_position`, `note_role` (public/scripts/authors-note.js:30-36) |
| Global settings `extension_settings.note` | `default`, `defaultPosition` (1), `defaultDepth` (4), `defaultInterval` (1), `defaultRole` (SYSTEM), `allowWIScan`, and `chara[] {name, prompt, useChara, position: replace 0 / before 1 / after 2}` (public/scripts/authors-note.js:38-42, :271-321) |
| Injection | `setFloatingPrompt()` counts down by interval and combines the character note with the chat note. It calls `setExtensionPrompt('2_floating_prompt', prompt, position, depth, allowWIScan, role)` (public/scripts/authors-note.js:26, :324-392) |
| UI | AN panel `#option_toggle_AN` / `#ANClose`. Inputs: `#extension_floating_prompt`, `_interval`, `_depth`, `_role`, `_chara`, `#extension_use_floating_chara`, `#extension_floating_default`, `#extension_default_depth/interval/role`, `#extension_floating_allow_wi_scan`. Token counters and a countdown in `#extension_floating_counter` (public/scripts/authors-note.js:476-499, :389) |
| SC | `/note` (public/scripts/authors-note.js:502), `/note-depth` (alias `depth`) (public/scripts/authors-note.js:517), `/note-frequency` (`freq`, `note-freq`) (public/scripts/authors-note.js:533), `/note-position` (`pos`, `note-pos`) (public/scripts/authors-note.js:550), `/note-role` (public/scripts/authors-note.js:567) |
| Macros | `{{authorsNote}}`, `{{charAuthorsNote}}`, `{{defaultAuthorsNote}}`, through the new engine or the legacy MacrosParser (public/scripts/authors-note.js:587-616) |

### 4.24 cfg-scale.js — Classifier-Free Guidance (Text Completion)

| Aspect | Detail |
|---|---|
| Scopes | `cfgType` chat 0, chara 1, global 2 (public/scripts/cfg-scale.js:383-387) |
| Chat metadata keys | `cfg_guidance_scale`, `cfg_negative_prompt`, `cfg_positive_prompt`, `cfg_prompt_combine[]`, `cfg_groupchat_individual_chars`, `cfg_prompt_insertion_depth`, `cfg_prompt_separator` (JSON string) (public/scripts/cfg-scale.js:389-397, :437-450) |
| Settings | `extension_settings.cfg.global {guidance_scale, negative_prompt, positive_prompt}` and `extension_settings.cfg.chara[] {name, guidance_scale, ...}` (public/scripts/cfg-scale.js:35-90, :229-232, :321-334). Migrated from `power_user.guidance_scale` and `negative_prompt` (public/scripts/cfg-scale.js:235-246) |
| Exports | `getGuidanceScale()`: chat, then character, then global; a scale of 1 is ignored (public/scripts/cfg-scale.js:401-430). `getCfgPrompt(scale, isNegative)` returns the combined prompt and insertion depth (public/scripts/cfg-scale.js:459-497). `initCfg` (public/scripts/cfg-scale.js:279) |
| UI | `#option_toggle_CFG` / `#CFGClose`. Inputs for chat, character and global scale and prompts, `#cfg_prompt_insertion_depth`, `#cfg_prompt_separator`, `#groupchat_cfg_use_chara` (public/scripts/cfg-scale.js:280-375) |

### 4.25 logit-bias.js — Logit bias list editor

`displayLogitBias(logitBias, containerSelector)` shows a sortable list that reorders by `id` (public/scripts/logit-bias.js:13-31). `createNewLogitBiasEntry` (public/scripts/logit-bias.js:57), `createLogitBiasListItem` (public/scripts/logit-bias.js:71), `BIAS_CACHE` (public/scripts/logit-bias.js:5). `getLogitBiasListResult(preset, tokenizer, fn)` reads the entry `text` in three forms (public/scripts/logit-bias.js:104-141):

- `{verbatim}` is tokenized as-is.
- `[1,2,3]` is a list of raw token IDs.
- Anything else is tokenized with a leading space.

Entries are `{id, text, value}`. They are stored in the owning preset (Text Completion or Chat Completion), not here.

### 4.26 char-data.js — Character card JSDoc types (no runtime)

Typedefs only: `v2DataWorldInfoEntry` and its `extensions` (public/scripts/char-data.js:2-41), `v2WorldInfoBook` (public/scripts/char-data.js:45-47), `v2CharData` (public/scripts/char-data.js:51-66), `v2CharDataExtensionInfos` (talkativeness, fav, world, depth_prompt{depth,prompt,role}, regex_scripts, pygmalion_id, github_repo, source_url, chub, risuai, sd_character_prompt) (public/scripts/char-data.js:69-84), `RegexScriptData` (public/scripts/char-data.js:88-101), `v1CharData` (including `chat`, `avatar`, `json_data`, `shallow`) (public/scripts/char-data.js:105-122). The only export is a dummy `export default 0` (public/scripts/char-data.js:124).

### 4.27 bulk-edit.js — Character list bulk select

| Aspect | Detail |
|---|---|
| UI | `#bulkEditButton` toggles bulk mode, `#bulkSelectAllButton` selects or deselects all, `#bulkDeleteButton` (public/scripts/bulk-edit.js:121-123). Checkboxes `.bulk_select_checkbox` are prepended to `.character_select`; groups and folders are disabled (public/scripts/bulk-edit.js:80-112) |
| Backing | `characterGroupOverlay` (BulkEditOverlay.js). The context menu offers tag, duplicate (`POST /api/characters/duplicate`) (public/scripts/BulkEditOverlay.js:48), favorite (`POST /api/characters/merge-attributes`) (public/scripts/BulkEditOverlay.js:84), convert to persona, and delete (public/scripts/BulkEditOverlay.js:34-147, :776-872). Long press is 2500 ms (public/scripts/BulkEditOverlay.js:389) |
| Export | `initBulkEdit()` (public/scripts/bulk-edit.js:115-127) |

### 4.28 tool-calling.js — ToolManager (function calling)

| Aspect | Detail |
|---|---|
| Register API | `ToolManager.registerFunctionTool({name, displayName, description, parameters(JSON schema), action(args), formatMessage(args), shouldRegister(), stealth})` (public/scripts/tool-calling.js:269). `unregisterFunctionTool(name)` (public/scripts/tool-calling.js:297). Internal `ToolDefinition` with `toFunctionOpenAI()` (public/scripts/tool-calling.js:115, :190) |
| Runtime | `invokeFunctionTool` (public/scripts/tool-calling.js:325), `isStealthTool` (public/scripts/tool-calling.js:352), `formatToolCallMessage` (public/scripts/tool-calling.js:367), `getDisplayName` (public/scripts/tool-calling.js:387). `registerFunctionToolsOpenAI(data)` sets `data.tools` and `tool_choice:'auto'` (public/scripts/tool-calling.js:400-418). `parseToolCalls` merges streaming deltas (public/scripts/tool-calling.js:427, :565). `hasToolCalls` (public/scripts/tool-calling.js:776). `invokeFunctionTools(data)` returns `{invocations, errors, stealthCalls}` (public/scripts/tool-calling.js:786-800); it is called from public/script.js:5418, :5545 |
| Support gate | Chat Completion only, with `oai_settings.function_calling` on (public/scripts/openai.js:392). Supported sources: OpenAI, Custom, Mistral, Claude, OpenRouter, AIMLAPI, Groq, Cohere, DeepSeek, MakerSuite, VertexAI, AI21, xAI, Pollinations, Moonshot, Fireworks, CometAPI, Chutes, ElectronHub, Azure OpenAI, ZAI, SiliconFlow, NanoGPT, Workers AI, MiniMax. Some sources also check model capabilities (public/scripts/tool-calling.js:614-684) |
| Display | Each invocation becomes a system message with `extra.isSmallSys` and `extra.tool_invocations`, rendered as `<details>` "Tool calls: name (n)" with pretty-printed JSON (public/scripts/tool-calling.js:863-926). Emits TOOL_CALLS_PERFORMED and TOOL_CALLS_RENDERED. Tool-invocation system messages are kept in the prompt (public/script.js:4496). Errors appear in a toast that opens a popup on click (public/scripts/tool-calling.js:928-933) |
| SC | `/tools-list` with `return` (public/scripts/tool-calling.js:942), `/tools-invoke` with `parameters` (public/scripts/tool-calling.js:966), `/tools-register` with name, description, parameters, displayName, formatMessage, shouldRegister, stealth, and a closure as the action (public/scripts/tool-calling.js:1001-1146), `/tools-unregister` (public/scripts/tool-calling.js:1152) |

### 4.29 macros.js and public/scripts/macros/ — Macro engine

**Two engines.** Legacy: regex-based `evaluateMacros` (public/scripts/macros.js:610). New: the Chevrotain lexer, parser and CST walker, used when `power_user.experimental_macro_engine` is on (public/scripts/macros.js:82, :719; public/scripts/macros/engine/MacroLexer.js).

The legacy `MacrosParser.registerMacro(key, value, description)` / `unregisterMacro` is deprecated. It forwards to the new registry (public/scripts/macros.js:39-42, :81-131, :183-240). Legacy-only regex macros cover `<USER>`, `<BOT>`, `<CHAR>`, `<GROUP>`, `<CHARIFNOTGROUP>`, the dice roll, and `{{time_UTC±N}}` (public/scripts/macros.js:618-700).

**Extension registration (new).** Import `{ macros, MacroCategory }` from `scripts/macros/macro-system.js` and call `macros.register(name, {handler, category, description, unnamedArgs, list, strictArgs, returns, returnType, displayOverride, exampleUsage, delayArgResolution, aliases:[{alias, visible}]})`. Also available: `macros.registerAlias(target, alias)` and `macros.registry.unregisterMacro` (public/scripts/macros/macro-system.js; public/scripts/macros/engine/MacroRegistry.js:64-75, :198, :241, :327). Handler context: `{name, args, unnamedArgs, list, namedArgs, flags, isScoped, raw, env, normalize, trimContent, resolve, warn, ...}` (public/scripts/macros/engine/MacroRegistry.js:106-128). Categories: utility, random, names, character, chat, time, variable, prompts, state, misc, uncategorized (public/scripts/macros/engine/MacroRegistry.js:19-41). Flags: `!` immediate, `?` delayed, `~` re-evaluate, `>` filter/pipe, `/` closing block, `#` preserve whitespace (public/scripts/macros/engine/MacroFlags.js:27-73).

| Definition file | Registered macros (line) |
|---|---|
| core-macros.js | space 32, newline 50, noop 68, trim 77, if 134, else 217, input 228, maxPrompt 236 (alias maxPromptTokens), maxContext 246 (maxContextTokens), maxResponse 256 (maxResponseTokens), reverse 266, `//` 282 (hidden alias `comment`), roll 303, random 340, pick 363, banned 425, outlet 450 (public/scripts/macros/definitions/core-macros.js) |
| env-macros.js | user 16, char 23, group 30 (hidden alias charIfNotGroup), groupNotMuted 38, notChar 45, charPrompt 53, charInstruction 60, charDescription 67 (description), charPersonality 75 (personality), charScenario 83 (scenario), persona 91, mesExamplesRaw 98, mesExamples 105, charDepthPrompt 128, charCreatorNotes 135 (creatorNotes), charFirstMessage 143 (greeting), charVersion 168 (version, char_version), model 180, original 187, isMobile 198 (public/scripts/macros/definitions/env-macros.js) |
| chat-macros.js | lastMessage 9, lastMessageId 16, lastUserMessage 24, lastCharMessage 31, firstIncludedMessageId 38, firstDisplayedMessageId 46, lastSwipeId 54, currentSwipeId 62, allChatRange 70 (public/scripts/macros/definitions/chat-macros.js) |
| time-macros.js | time 11, date 41, weekday 48, isotime 55, isodate 62, datetimeformat 69, idleDuration 85 (idle_duration), timeDiff 94 (public/scripts/macros/definitions/time-macros.js) |
| state-macros.js | lastGenerationType 35, hasExtension 43 (public/scripts/macros/definitions/state-macros.js) |
| variable-macros.js | setvar 11, addvar 35, incvar 59, decvar 79, getvar 99, hasvar 119 (varexists), deletevar 139 (flushvar), setvarkey 159 (setvarindex), getvarkey 189 (getvarindex), setglobalvar 215, addglobalvar 239, incglobalvar 263, decglobalvar 283, getglobalvar 303, hasglobalvar 323 (globalvarexists), deleteglobalvar 343 (flushglobalvar), setglobalvarkey 363 (setglobalvarindex), getglobalvarkey 393 (getglobalvarindex) (public/scripts/macros/definitions/variable-macros.js) |
| instruct-macros.js | instructStoryStringPrefix/Suffix, instructUserPrefix (instructInput), instructUserSuffix, instructAssistantPrefix (instructOutput), instructAssistantSuffix (instructSeparator), instructSystemPrefix/Suffix, instructFirstAssistantPrefix (instructFirstOutputPrefix), instructLastAssistantPrefix (instructLastOutputPrefix), instructStop, instructUserFiller, instructSystemInstructionPrefix, instructFirstUserPrefix (instructFirstInput), instructLastUserPrefix (instructLastInput), defaultSystemPrompt (instructSystem, instructSystemPrompt) (public/scripts/macros/definitions/instruct-macros.js:34-57); systemPrompt (public/scripts/macros/definitions/instruct-macros.js:59) |
| Elsewhere | authorsNote / charAuthorsNote / defaultAuthorsNote (public/scripts/authors-note.js:589-614); reasoningPrefix / Suffix / Separator (public/scripts/reasoning.js:1191-1201); legacy lastGenerationType and isMobile (public/scripts/macros.js:723, :738); summary (public/scripts/extensions/memory/index.js:1120); defaultExpression, lastExpression, availableExpressions (public/scripts/extensions/expressions/index.js:2654-2716); charPrefix, charNegativePrefix (public/scripts/extensions/stable-diffusion/index.js:5978-5993) |

Registration order: core, env, state, chat, time, variable, instruct (public/scripts/macros/macro-system.js `initRegisterMacros`).

Engine files in `macros/engine/`:

| File | Purpose |
|---|---|
| MacroLexer | Tokens |
| MacroParser | CST |
| MacroCstWalker | Evaluation |
| MacroEngine | `evaluate`, pre/post processors at public/scripts/macros/engine/MacroEngine.js:58-117 |
| MacroEnvBuilder | Env object |
| MacroEnv.types | Typedefs |
| MacroDiagnostics | Runtime errors |
| MacroFlags | Flag definitions |
| MacroBrowser | `/?macros` documentation UI |
| MacroRegistry | Registry |

### 4.30 variables.js — Local and global variables

Storage: local variables in `chat_metadata.variables`, global variables in `extension_settings.variables.global`. Values are strings; JSON is used for indexes (public/scripts/variables.js:22-130).

SC names with lines: listvar 904 (listchatvar), setvar 934 (setchatvar), getvar 983 (getchatvar), addvar 1027 (addchatvar), setglobalvar 1061, getglobalvar 1109, addglobalvar 1151, incvar 1184 (incchatvar), decvar 1213 (decchatvar), incglobalvar 1242, decglobalvar 1270, if 1298, while 1399, times 1489, flushvar 1534 (flushchatvar), flushglobalvar 1560, add 1587, mul 1622, max 1657, min 1692, sub 1727, div 1762, mod 1798, pow 1834, sin 1870, cos 1898, log 1926, abs 1955, sqrt 1983, round 2011, len 2039 (length), sort 2077, rand 2123, var 2177, let 2242, closure-serialize 2288, closure-deserialize 2319 (public/scripts/variables.js).

### 4.31 public/scripts/autocomplete/

| File | Purpose |
|---|---|
| AutoComplete.js | Core autocomplete popup engine for textareas: width, select key and state enums, details panel (public/scripts/autocomplete/AutoComplete.js:11-32) |
| AutoCompleteOption.js, BlankAutoCompleteOption.js | Base option with name, type, icon, details rendering, and a blank placeholder (public/scripts/autocomplete/AutoCompleteOption.js:4; public/scripts/autocomplete/BlankAutoCompleteOption.js:3) |
| AutoCompleteNameResultBase.js, AutoCompleteNameResult.js, AutoCompleteSecondaryNameResult.js | Result containers for primary and secondary (argument) completions (public/scripts/autocomplete/AutoCompleteNameResultBase.js:4; public/scripts/autocomplete/AutoCompleteNameResult.js:5; public/scripts/autocomplete/AutoCompleteSecondaryNameResult.js:3) |
| AutoCompleteFuzzyScore.js | Fuzzy-match score holder (public/scripts/autocomplete/AutoCompleteFuzzyScore.js:1) |
| MacroAutoComplete.js | Attaches macro autocomplete to inputs with `data-macros`, `data-macros-autocomplete` and `data-macros-autocomplete-style` (public/scripts/autocomplete/MacroAutoComplete.js:19-25) |
| MacroAutoCompleteHelper.js | Unclosed-scope detection, variable shorthand and enhanced macro option builders (public/scripts/autocomplete/MacroAutoCompleteHelper.js:70-461) |
| MacroAutoCompleteOption.js, EnhancedMacroAutoCompleteOption.js | Macro, flag and variable-shorthand options (public/scripts/autocomplete/MacroAutoCompleteOption.js:3; public/scripts/autocomplete/EnhancedMacroAutoCompleteOption.js:67, :444, :530) |

### 4.32 public/scripts/slash-commands/ (STscript runtime)

| File | Purpose |
|---|---|
| SlashCommandParser.js | Parser, command registry (`addCommandObject`) and `PARSER_FLAG` (public/scripts/slash-commands/SlashCommandParser.js:38-43) |
| SlashCommand.js | Command definition (`fromProps`: name, callback, aliases, args, returns, helpString) (public/scripts/slash-commands/SlashCommand.js:29) |
| SlashCommandArgument.js | `ARGUMENT_TYPE`, `SlashCommandArgument`, `SlashCommandNamedArgument` (public/scripts/slash-commands/SlashCommandArgument.js:10-82) |
| SlashCommandClosure.js, SlashCommandClosureResult.js | Executable closure `{: ... :}` and its result (public/scripts/slash-commands/SlashCommandClosure.js:16; public/scripts/slash-commands/SlashCommandClosureResult.js:1) |
| SlashCommandExecutor.js, SlashCommandBreak.js, SlashCommandBreakPoint.js, SlashCommandBreakController.js | Executor nodes for `/break` and `/breakpoint` (public/scripts/slash-commands/SlashCommandExecutor.js:7; public/scripts/slash-commands/SlashCommandBreak.js:3; public/scripts/slash-commands/SlashCommandBreakPoint.js:3; public/scripts/slash-commands/SlashCommandBreakController.js:1) |
| SlashCommandScope.js | Variable scopes and their errors (public/scripts/slash-commands/SlashCommandScope.js:4-115) |
| SlashCommandAbortController.js, AbstractEventTarget.js | Abort handling (public/scripts/slash-commands/SlashCommandAbortController.js:3-29; public/scripts/slash-commands/AbstractEventTarget.js:5) |
| SlashCommandDebugController.js | Step debugger (public/scripts/slash-commands/SlashCommandDebugController.js:4) |
| SlashCommandEnumValue.js, SlashCommandCommonEnumsProvider.js | Enum values, icons and shared enum providers (public/scripts/slash-commands/SlashCommandEnumValue.js:10-42; public/scripts/slash-commands/SlashCommandCommonEnumsProvider.js:17-361) |
| `*AutoCompleteOption.js`, SlashCommandAutoCompleteNameResult.js | Autocomplete options for commands, named arguments, enums, variables and Quick Replies (public/scripts/slash-commands/SlashCommandAutoCompleteNameResult.js:10) |
| SlashCommandNamedArgumentAssignment.js, SlashCommandUnnamedArgumentAssignment.js | Parsed argument nodes |
| SlashCommandBrowser.js | `/?slash` help browser (public/scripts/slash-commands/SlashCommandBrowser.js:4) |
| SlashCommandReturnHelper.js | Shared `return=` handling (public/scripts/slash-commands/SlashCommandReturnHelper.js:10) |
| SlashCommandParserError.js, SlashCommandExecutionError.js | Error types (public/scripts/slash-commands/SlashCommandParserError.js:1; public/scripts/slash-commands/SlashCommandExecutionError.js:1) |

### 4.33 public/scripts/util/

| File | Purpose |
|---|---|
| AccountStorage.js | `accountStorage` replaces localStorage with a server-synced key/value store. It is persisted in `settings.accountStorage` through `saveSettingsDebounced` and initialised at public/script.js:7935. Methods: `init`, `getItem`, `setItem`, `removeItem`, `getState`. It migrates whitelisted localStorage keys once (`__migrated`) (public/scripts/util/AccountStorage.js:3-37, :69-145) |
| AbortReason.js | Abort reason wrapper with `toString` (public/scripts/util/AbortReason.js:1-9) |
| SimpleMutex.js | Async mutex (public/scripts/util/SimpleMutex.js:4) |
| StructuredCloneMap.js | Map that deep-clones values (public/scripts/util/StructuredCloneMap.js:7) |
| ThemeGenerator.js | `extractDominantColor`, `generateThemePalette` and `deriveBackgroundName` for theme generation from the background, used in power-user.js:65 (public/scripts/util/ThemeGenerator.js:117-307) |
| showdown-patch.js | Patches showdown `unhashHTMLSpans` for nested spans (public/scripts/util/showdown-patch.js:5-30) |
| stream-fadein.js | `Intl.Segmenter` word fade-in during streaming: `isSegmenterSupported`, `segmentTextInElement`, `applyStreamFadeIn` (public/scripts/util/stream-fadein.js:7-65) |


## 5. Third-party libraries

### 5.1 `public/lib/*` (vendored, loaded by `<script>` in index.html or dynamic `import()`)

| File | Library | Loaded | Used for (example call sites) | Android note |
|---|---|---|---|---|
| polyfill.js | hand-written polyfills (`Object.hasOwn`, `Array.findLastIndex`, `toSorted`, …) for old Safari | index.html:8200 | global | not needed natively |
| jquery-3.5.1.min.js | jQuery 3.5.1 | index.html:8201; login.html:86 | all DOM work; `$.ajaxPrefilter` adds `X-CSRF-Token` (script.js:667-669) | – |
| jquery-ui.min.js (+css/jquery-ui.min.css) | jQuery UI | index.html:8202, :27 | `.sortable()` (PromptManager.js:1919, kai-settings.js:492, tags.js:1757), `.slider()` (power-user.js:798), `.autocomplete()` (tags.js:1703), `.tabs()` (backgrounds.js:1690) | reorder lists, sliders, autocomplete, tabs |
| jquery.transit.min.js | CSS3 transitions for jQuery | index.html:8203 | `.transition({...})` (authors-note.js:406, logprobs.js:341) | animations |
| jquery-cookie-1.4.1.min.js | jQuery Cookie | index.html:8204 | no core call found (legacy) | – |
| jquery.ui.touch-punch.min.js | maps touch events → mouse for jQuery UI | index.html:8205 | makes sortable/slider work on touch (no explicit call) | touch support |
| cropper.min.js + jquery-cropper.min.js (+css/cropper.min.css) | Cropper.js | index.html:8206-8207, :29 | avatar crop popup `POPUP_TYPE.CROP` → `$(this.cropImage).cropper({...})` (popup.js:492) | image crop |
| toastr.min.js (+css) | toastr notifications | index.html:8208, :30 | `toastr.info/success/warning/error` in ~87 files (e.g. swipe-picker.js:56) | toasts/snackbars |
| select2.min.js (+css, select2-overrides.css) + select2-search-placeholder.js | Select2 searchable selects | index.html:8209-8210, :31, :43 | model pickers (textgen-models.js:1420-1434 — 14 uses), openai.js (6), world-info.js (7), horde.js, tags.js, utils.js:2401 | searchable dropdowns |
| pagination.js | paginationjs | index.html:8211 | character list `#rm_print_characters_pagination` (script.js:1021), personas (personas.js:304), group member add (group-chats.js:1621), model lists (textgen-models.js:803), `/list` paging (slash-commands.js:4818) | paging/lazy lists |
| toolcool-color-picker.js | `<toolcool-color-picker>` web component | index.html:8212 | theme colors (power-user), tag colors (tags.js:2022-2040) | color picker |
| jquery.izoomify.js | hover zoom | index.html:8213 | zoomed avatar `.zoomed_avatar_container` (script.js:12270-12284) | pinch zoom |
| structured-clone/* | `structuredClone` polyfill (monkey-patch if missing) | index.html:8214; structured-clone/monkey-patch.js:1-5 | 23 files use `structuredClone` | n/a |
| swiped-events.js | swipe gesture DOM events (`swiped-left/right/up/down`, `data-swipe-threshold` default 20) | index.html:8215; lib/swiped-events.js:49 | message swipe gestures (RossAscends-mods.js:909, 933) | native gestures |
| eventemitter.js | async EventEmitter with `autoFireAfterEmit`, `makeFirst/makeLast`, `emitAndWait` | index.html:8216; events.js:1 | app event bus `eventSource` (events.js:113) | event bus |
| dialog-polyfill.esm.js (+css) | `<dialog>` polyfill | popup.js:1, 240-245 | Popup class | n/a |
| epub.min.js + jszip.min.js | ePub.js (+JSZip dependency) | lazy `import()` utils.js:2071-2076 | extract text from .epub for Data Bank / attachments | doc parsing |
| pdf.min.mjs + pdf.worker.min.mjs | pdf.js | lazy `import()` utils.js:2030-2032 | extract text from PDF attachments | doc parsing |
| toastr.js.map | source map | – | – | – |

### 5.2 `public/lib.js` (webpack bundle of npm packages, served at `/lib.js`)

Bundled by webpack (`webpack.config.js` "public/lib.js" config, served by `src/middleware/webpack-serve.js:15-44`). Exports at public/lib.js:84-138; legacy `window.*` shims for Fuse, DOMPurify, hljs, localforage, Handlebars, diff_match_patch, SVGInject, showdown, moment, Popper, droll (lib.js:34-82, called at script.js:730).

| Export | npm pkg (lib.js line) | Used for | Example cite |
|---|---|---|---|
| lodash | lodash (:5) | deep get/set of preset extension fields | preset-manager.js:861-892 |
| Fuse | fuse.js (:6) | fuzzy search (presets, system prompts, backgrounds, etc. — 10 files) | preset-manager.js:956; sysprompt.js:140; backgrounds.js:636 |
| DOMPurify | dompurify (:7) | HTML sanitization of messages/templates (18 files) | templates.js:1; PromptManager.js:1608 |
| hljs | highlight.js (:8) | code-block highlighting in messages, QR editor syntax | script.js:2482; extensions/quick-reply/src/QuickReply.js:910, 1306 |
| localforage | localforage (:9) | IndexedDB stores: `SillyTavern_Prompts` (itemized prompts), `SillyTavern_Thumbnails` (bg thumbs), `SillyTavern_TextCompletions`, `SillyTavern_ChatCompletions` (model lists cache) | itemized-prompts.js:15; backgrounds.js:42; samplerSelect.js:22; tokenizers.js:157 |
| Handlebars | handlebars (:10) | HTML templates (`renderTemplate*`), story-string/context template compile | templates.js:65, 107; power-user.js:2245 |
| css | @adobe/css-tools (:11) | parse+re-stringify `<style>` in messages (scoping) | chats.js:616-621 |
| Bowser | bowser (:12) | UA parsing for mobile detection / device info | RossAscends-mods.js:130 |
| DiffMatchPatch | diff-match-patch (:13) | prompt itemization diff view | itemized-prompts.js:274 |
| Readability, isProbablyReaderable | @mozilla/readability (:14) | web-page text extraction (scrapers) | utils.js:2006-2013 |
| SVGInject | @iconfu/svg-inject (:15) | inline SVG icons in streaming display | streaming-display.js:101 |
| showdown | showdown (:16) | Markdown→HTML for messages | script.js:522; showdown-exclusion.js:6 |
| moment | moment (:17) | dates/timestamps, humanized dates | utils.js:1084 |
| seedrandom | seedrandom (:18) | `{{random}}`/`{{pick}}` seeded RNG | macros.js:503, 539 |
| Popper | @popperjs/core (:19) | dropdown positioning (wand menu, chat file menu, SD menu) | extensions.js:699; chats.js:1466; extensions/stable-diffusion/index.js:5035 |
| droll | droll (:20) | `{{roll}}` dice macros | macros.js:559-566 |
| morphdom | morphdom (:21) | DOM diff for streaming fade-in, QR editor | util/stream-fadein.js:68; quick-reply/src/QuickReply.js:1 |
| slideToggle | slidetoggle (:22) | exposed; options helper `getSlideToggleOptions` | script.js:660 |
| chalk | chalk (:23) | exposed only; no core usage found | lib.js:23 |
| yaml | yaml (:24) | exposed only; no core usage found | lib.js:24 |
| chevrotain | chevrotain (:25) | new macro engine lexer/parser (CST) | macros/engine/MacroParser.js:1-4 |
| gzip, gzipSync | fflate (:26) | request body compression | request-compression.js:36 |
| sha256 | js-sha256 (:27) | secret hashing/fingerprints | secrets.js:516 |

### 5.3 Static assets
- Stylesheets: index.html:20-47 — `fontawesome.min.css`, `solid.min.css`, `brands.min.css` (icons; `public/webfonts/fa-*.woff2`), `bright.min.css`, `jquery-ui.min.css`, `cropper.min.css`, `toastr.min.css`, `select2.min.css`, `style.css`, `st-tailwind.css`, `rm-groups.css`, `group-avatars.css`, `toggle-dependent.css`, `world-info.css`, `extensions-panel.css`, `select2-overrides.css`, `mobile-styles.css`, `macros.css`, `user.css` (server-injected custom CSS, src/middleware/userCss.js). Other `public/css/*.css` (accounts, animations, backgrounds, character-group-overlay, chat-backups, data-maid, file-form, loader, login, logprobs, popup, popup-safari-fix, promptmanager, scrollable-button, secrets, streaming-display, tags, welcome) are `@import`ed by style.css (style.css:3-18); `popup-safari-fix.css` is imported from css/popup.css.
- Fonts: `public/webfonts/NotoSans`, `NotoSansMono` (index.html:22-23).
- PWA: `public/manifest.json` (`display: standalone`); `initStandaloneMode()` adds `body.PWA` when `(display-mode: standalone)` (script.js:799-804).
