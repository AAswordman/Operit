# OperitX — แผนพัฒนาต่อยอดจาก Operit สู่ Mobile AI Agent เต็มรูปแบบ

> เอกสารนี้คือแผนงานฉบับสมบูรณ์ ตั้งแต่สถาปัตยกรรมจนถึง deploy สำหรับให้ AI Coding Agent (เช่น Claude Code) นำไปใช้ลงมือทำโปรเจกต์แบบต่อเนื่อง
> **Base:** Fork จาก `AAswordman/Operit` (Kotlin, Android native) — iPad/แท็บเล็ตอื่นเข้าถึงผ่าน Web/Remote Client แยกต่างหาก ไม่ใช่ native iOS app

---

## 0. หลักการทำงานที่ agent ต้องยึดตลอดโปรเจกต์

1. **ห้าม hardcode** ชื่อ provider หรือโมเดลใด ๆ ลงในโค้ด core — ทุกอย่างต้องผ่าน Provider Abstraction Layer (ดูข้อ 3)
2. **ห้าม compile/build ส่งให้ทดสอบ** ก่อนตรวจโค้ดที่แก้ไข/เพิ่มทั้งหมดในรอบนั้นด้วยตัวเองก่อน (ดู Quality Gate ในข้อ 9)
3. ทุก feature ใหม่ต้อง**มี unit test อย่างน้อย 1 เคส** และห้าม merge ถ้า build แดง
4. ทุกครั้งที่เจอปัญหา/แก้บั๊ก ต้องบันทึกลง `docs/SOLUTIONS.md` ทันที (รูปแบบดูข้อ 8.4)
5. หลีกเลี่ยงการ refactor module ที่ไม่เกี่ยวกับ task ปัจจุบันเว้นแต่จำเป็นจริง ๆ

---

## 1. Requirement Mapping (สเปค 23 ข้อ → โมดูลที่รับผิดชอบ)

| # | ความต้องการ | โมดูล/ระบบที่ implement | หมายเหตุ |
|---|---|---|---|
| 1 | Multi-provider + ราคาโมเดลตอนเลือก | `ProviderRegistry`, `ModelPricingService` | ดึงราคาจาก config/remote JSON แบบ pluggable |
| 2 | Terminal ในตัว + root access | `TerminalModule` (fork จาก Operit Termux integration) | ต่อยอดจาก issue auth Termux ที่ Operit เจอ (ดูข้อ 10) |
| 3 | Tool call รันบน terminal ได้จริง | `ToolExecutionEngine` เชื่อม `TerminalModule` | tool call → shell exec → capture stdout/stderr กลับเป็น tool result |
| 4 | Multi-AI ทำงานร่วมกันลื่นไหล | `OrchestrationEngine` (Hermes-style) | ดูสถาปัตยกรรมเต็มในข้อ 4 |
| 5 | หลายโหมด CLI/IDE/Chat/Image-gen | `ModeSwitcher` + 4 UI surface | shared session state ข้ามโหมด |
| 6 | ติดตั้ง Skills/MCP | `SkillManager`, `MCPClientManager` | ใช้โครง ToolPkg ของ Operit เป็นฐาน |
| 7 | ยิ่งใช้ยิ่งฉลาดแบบ Hermes | `LongTermLearningLoop` | อ่านข้อ 4.3 + ข้อ 14 |
| 8 | กำหนดหน้าที่ leader/worker ใน multi-AI | `RoleAssignmentConfig` ใน Orchestration | ดูข้อ 4.2 |
| 9 | ตั้ง system prompt ได้ | `PromptProfileManager` | ต่อ agent/role ได้หลายชุด |
| 10 | ตั้งได้หลาย rule | `RuleEngine` (layered: global/project/session) | rule conflict resolution ตามลำดับชั้น |
| 11 | ตั้ง AI permission | `PermissionMatrix` | จำกัดสิทธิ์ต่อ agent/role/tool |
| 12 | Thinking / no-thinking mode + แสดงการคิด | `ThinkingModeToggle` + UI stream renderer | ต้อง handle โมเดลที่ไม่รองรับ thinking gracefully |
| 13 | จัดการ context/token แบบอัจฉริยะ | `ContextBudgetManager` | ดูข้อ 5 |
| 14 | จำนิสัยผู้ใช้แบบถาวร + ข้ามโปรเจค | `GlobalMemoryStore` | แยกจาก per-project memory |
| 15 | AI ถามคำถามกลับผู้ใช้ได้ | `AgentQuestionChannel` | เฉพาะตอนก่อนเริ่มงาน/ติด blocker จริง (ดู mode ข้อ 10 ของ prompt) |
| 16 | จัดระเบียบไฟล์/โค้ด + auto comment | `CodeOrganizerAgent` | รันเป็น background pass หลังแก้โค้ดเสร็จ |
| 17 | แสดง % ความคืบหน้าโปรเจกต์ | `ProgressTracker` อ่านจาก `Progress.md` | คำนวณจาก task graph ที่ทำเสร็จ/ทั้งหมด |
| 18 | Prompt อัจฉริยะประหยัด token อัตโนมัติ | `SmartPromptCompressor` | ดูข้อ 5.3 |
| 19 | Export/Import ความรู้ข้ามเครื่อง | `KnowledgeExportService` (.opk bundle) | zip ของ memory + project docs + config |
| 20 | บันทึกลง Project.md / Progress.md / SOLUTIONS.md อัตโนมัติ | `AutoDocWriter` | ดูข้อ 8 |
| 21 | จำ session history เพื่อทำต่อได้ | `SessionResumeManager` | เก็บ checkpoint ทุก task สำเร็จ |
| 22 | UI สะอาด ลื่น รองรับมือถือ+iPad(web) | `frontend-design` guideline + responsive layout | ดูข้อ 6 |
| 23 | ไม่มี bug/crash, ตรวจโค้ดก่อน compile บังคับ | Quality Gate (ข้อ 9) | บังคับทุก commit |

---

## 2. สถาปัตยกรรมโดยรวม

```
┌─────────────────────────────────────────────────────────┐
│                     UI Layer (4 modes)                    │
│   Chat UI │ IDE UI │ CLI UI │ Image-gen UI                │
├─────────────────────────────────────────────────────────┤
│                  Mode Switcher / Session State             │
├─────────────────────────────────────────────────────────┤
│  Orchestration Engine (Leader/Worker Multi-AI, Hermes-like)│
├───────────────┬───────────────┬────────────────┬──────────┤
│ Provider       │ Tool Execution │ Skill/MCP       │ Memory & │
│ Abstraction    │ Engine         │ Manager         │ Context  │
│ Layer          │ (→ Terminal)   │                 │ Engine   │
├───────────────┴───────────────┴────────────────┴──────────┤
│         Terminal Module (Termux-style + root exec)          │
├─────────────────────────────────────────────────────────┤
│   Local Storage: Room DB / Encrypted File Store / .opk     │
└─────────────────────────────────────────────────────────┘
```

**เหตุผลที่ fork Operit:** มี Termux authorization flow, MCP client, ToolPkg system, workspace file access และ workflow engine อยู่แล้ว — ประหยัดเวลากว่าเริ่มจากศูนย์มาก จุดที่ต้อง "เขียนเพิ่ม" คือ Orchestration Engine (multi-AI leader/worker), Context Budget Manager, Long-term Global Memory, และ Auto-Doc system ซึ่ง Operit เดิมไม่มี

---

## 3. Provider Abstraction Layer (ข้อ 1)

```kotlin
interface AIProvider {
    val id: String
    suspend fun listModels(): List<ModelInfo>
    suspend fun getPricing(modelId: String): ModelPricing?
    suspend fun chatCompletion(request: ChatRequest): Flow<ChatChunk>
    val supportsToolCalling: Boolean
    val supportsThinking: Boolean
}

data class ModelPricing(
    val inputPerMillionTokens: Double,
    val outputPerMillionTokens: Double,
    val currency: String,
    val lastUpdated: Instant
)
```

- Provider ใหม่ = implement interface เดียว + ลงทะเบียนใน `ProviderRegistry` (config-driven, ไม่ต้องแก้ core)
- ราคาโมเดล: ดึงจาก remote pricing JSON (cache ไว้ local, fallback เป็นค่าที่ผู้ใช้กรอกเองถ้าดึงไม่ได้) แสดงในหน้าเลือกโมเดลทันทีที่กด dropdown
- รองรับ custom endpoint / API key pool (ต่อยอดจากของ Operit เดิมที่มีอยู่แล้ว)

---

## 4. Multi-AI Orchestration (ข้อ 4, 7, 8)

### 4.1 โครงสร้าง
- **Leader Agent**: รับ task จากผู้ใช้ → แตกเป็น subtask → มอบหมายให้ Worker → ตรวจผลงาน Worker → ถ้าผิดสั่งแก้ (retry loop มี max retry ป้องกัน infinite loop) → รวมผลส่งผู้ใช้
- **Worker Agent(s)**: รับ subtask เดียว ลงมือทำ (เรียก tool/terminal) แล้วรายงานกลับ Leader พร้อม log การทำงาน
- รองรับ Worker หลายตัวทำงานขนาน (parallel subtask) เมื่อ subtask ไม่ dependent กัน

### 4.2 Role Assignment Config
```yaml
roles:
  leader:
    model: <model ที่ฉลาดที่สุดที่ตั้งไว้>
    responsibilities: [decompose_task, review_output, dispatch_fix]
    permission: full
  worker:
    model: <model ประหยัด/เร็ว>
    responsibilities: [execute_subtask, report_result]
    permission: restricted   # จำกัดตาม PermissionMatrix ข้อ 11
```
ผู้ใช้ปรับ role/จำนวน worker/โมเดลต่อ role ได้จากหน้า settings

### 4.3 Long-term Learning Loop (ยิ่งใช้ยิ่งฉลาด)
- ทุกครั้งที่ Leader "สั่งแก้งาน" → บันทึก pattern ความผิดพลาด + วิธีแก้ลง `GlobalMemoryStore`
- ก่อนเริ่ม subtask ใหม่ที่คล้ายของเก่า → ระบบ inject บทเรียนที่เกี่ยวข้องเข้า context อัตโนมัติ (ไม่ต้องให้ผู้ใช้สั่งเอง)
- คล้ายแนวทาง `SOUL.md` ที่ใช้กับ Hermes เดิม — ใช้ไฟล์ policy ถาวรต่อ agent role

---

## 5. Context & Token Budget Management (ข้อ 13, 18)

### 5.1 ContextBudgetManager
- ตั้ง budget ต่อ session (token/บาท) ผู้ใช้กำหนดเพดานได้
- Priority tiers เมื่อ context ใกล้เต็ม: (1) system rules (2) task ปัจจุบัน (3) memory ที่ inject (4) ประวัติแชทเก่า → ตัดจากท้ายลำดับก่อน
- Auto-summarize ประวัติแชทเก่าเป็นสรุปสั้นแทนการลบทิ้งดื้อ ๆ

### 5.2 Cache-aware routing
- ใช้ prompt caching ของ provider ที่รองรับเป็นค่า default (อิงจากพฤติกรรม cache hit สูงที่เคยพิสูจน์แล้วกับ DeepSeek V4 Flash) — จัดกลุ่มข้อความ static (system prompt, rules) ไว้หัว request เสมอเพื่อให้ cache hit ง่าย

### 5.3 SmartPromptCompressor
- บีบอัด prompt อัตโนมัติ: ตัด boilerplate ซ้ำ, แปลง log ยาวเป็นสรุป, ใช้ reference แทนการแปะซ้ำ
- มี toggle ปิดได้ถ้าผู้ใช้ต้องการความแม่นยำเต็มร้อยเหนือกว่าประหยัด token

---

## 6. UI/UX (ข้อ 5, 12, 22)

- ใช้แนวทางจาก `frontend-design` guideline: minimal, ไม่ยัด element แน่นจอ, ใช้ spacing/typography ให้หายใจ
- 4 โหมดหลัก sharing เดียวกันคือ session/context: **Chat**, **IDE** (file tree + editor + diff view), **CLI** (terminal เต็มจอ), **Image-gen** (canvas + prompt bar)
- Thinking mode: แสดง collapsible "AI is thinking..." block แยกจากคำตอบจริง, ปิด/เปิดได้ต่อ request
- Responsive layout breakpoint สำหรับมือถือ/แท็บเล็ต; iPad ใช้ผ่าน Web Client (responsive เว็บ, ไม่ใช่ native iOS)
- Zero-crash policy: ทุก UI state ต้องมี loading/error/empty state ครบ ห้ามปล่อย null state ให้ crash

---

## 7. Skill / MCP / Rule / Permission System (ข้อ 6, 9, 10, 11)

- ต่อยอดจาก ToolPkg + MCP integration ของ Operit เดิมโดยตรง
- `PromptProfileManager`: เก็บ system prompt หลายชุด ผูกกับ role/project เลือกสลับได้
- `RuleEngine`: rule แบ่งเป็น 3 ชั้น — Global (ทุกโปรเจกต์) → Project (เฉพาะโปรเจกต์นี้) → Session (ชั่วคราว) — ชั้นที่แคบกว่าชนะเมื่อขัดแย้งกัน
- `PermissionMatrix`: กำหนดสิทธิ์ tool/terminal/root ต่อ agent role แยกกัน (Leader อาจอนุมัติ root command, Worker ทำได้แค่ sandbox)

---

## 8. Auto-Documentation System (ข้อ 16, 17, 19, 20, 21)

### 8.1 Project.md (auto-generate/update)
เก็บ: วัตถุประสงค์โปรเจกต์, สถาปัตยกรรมสรุป, decision log, tech stack — อัปเดตทุกครั้งที่มีการเปลี่ยนแปลงสำคัญ

### 8.2 Progress.md
```markdown
## Task: <ชื่องาน>
- Status: In Progress / Done / Blocked
- % Complete: 40%
- Last updated: <timestamp>
- Next step: <รายละเอียด>
```
`ProgressTracker` คำนวณ % จาก task graph (จำนวน subtask เสร็จ/ทั้งหมด) แสดงเป็น progress bar ใน UI

### 8.3 CodeOrganizerAgent
- รันเป็น pass หลังแก้โค้ดทุกครั้ง: จัดโฟลเดอร์ตาม convention, เพิ่ม comment อธิบาย function/class ที่ยังไม่มี, ลบ dead code ที่ตรวจพบชัดเจน (ต้องมี dry-run diff ให้ผู้ใช้ยืนยันก่อน apply ในเคสที่เสี่ยง)

### 8.4 SOLUTIONS.md format
```markdown
## [YYYY-MM-DD] ปัญหา: <หัวข้อ>
**อาการ:** ...
**สาเหตุ:** ...
**วิธีแก้:** ...
**Tag:** #tool-calling #provider-x
```

### 8.5 Export/Import (.opk bundle)
ไฟล์ `.opk` = zip ของ `GlobalMemoryStore` snapshot + `Project.md` + `Progress.md` + config ที่เลือก → import กลับเครื่องอื่นแล้ว merge เข้า memory เดิมแบบไม่ overwrite ของเก่าทิ้ง

### 8.6 SessionResumeManager
- Checkpoint ทุกครั้งที่ subtask สำเร็จ (task graph state + context snapshot pointer)
- เปิดแอปใหม่ → เสนอ "ทำต่อจากที่ค้างไว้" อัตโนมัติ พร้อมสรุปว่าค้างอะไรอยู่

---

## 9. Quality Gate (ข้อ 23) — บังคับทุก commit

ก่อน build/compile ทุกครั้ง agent ต้อง:
1. ตรวจโค้ดที่แก้ไข**ทุกบรรทัด**ด้วยตัวเอง (self-review pass) — ไล่ตาม diff ทีละไฟล์
2. รัน static analysis / lint ที่มีอยู่ในโปรเจกต์
3. รัน unit test ที่เกี่ยวข้อง — ต้องผ่าน 100% ก่อนเสนอ build
4. เช็ค UI state ครบ (loading/error/empty) ถ้าแตะ UI
5. ถ้าพบว่าโค้ดไม่ผ่านข้อใดข้อหนึ่ง **ห้ามส่งให้ผู้ใช้ทดสอบ** — แก้ให้ผ่านก่อนเท่านั้น แล้วค่อยแจ้งว่าพร้อมทดสอบ

---

## 10. บทเรียนจาก Operit เดิมที่ต้องระวัง/แก้ตั้งแต่แรก

จากการสำรวจ issue ของ Operit ต้นทาง มีจุดเสี่ยงที่ fork นี้ต้องแก้ไม่ให้เกิดซ้ำ:

| ปัญหาเดิม | จุดที่ต้องแก้ใน OperitX |
|---|---|
| Tool-call param ถูก XML tag ปนเปื้อนจนพัง JSON parsing | เขียน sanitizer แยก parse layer ก่อนส่งเข้า `ToolExecutionEngine`, unit test ด้วย input ที่มี XML/markdown ปนมาโดยเฉพาะ |
| Third-party provider (เช่น DeepSeek ผ่าน proxy) error 400 เมื่อ trigger tool call | ทำ compatibility test suite รัน mock request ผ่านทุก provider ก่อนปล่อย release |
| ขอสิทธิ์ Termux ไม่สำเร็จบนบางเครื่อง (ColorOS ฯลฯ) | ทำ fallback flow: ถ้า auto-authorize ไม่ยืนยันภายใน timeout ให้ขึ้น manual verify step พร้อมคำแนะนำเฉพาะยี่ห้อเครื่อง |

---

## 11. Development Phases

| Phase | เนื้อหา | Output |
|---|---|---|
| 0. Setup | Fork repo, ตั้ง CI, ตั้ง project structure ใหม่สำหรับโมดูลเพิ่มเติม | Build ผ่าน, CI เขียว |
| 1. Provider Layer | ProviderRegistry + Pricing service + UI เลือกโมเดล | เลือก/สลับ provider พร้อมราคาได้ |
| 2. Terminal + Tool Exec | ต่อยอด Termux module, sanitizer, root exec permission | Tool call รันผ่าน terminal ได้เสถียร |
| 3. Multi-Agent Core | OrchestrationEngine, RoleAssignmentConfig | Leader/Worker ทำงานร่วมกันได้ในเคสง่าย |
| 4. Memory & Context | GlobalMemoryStore, ContextBudgetManager, SmartPromptCompressor | จำข้ามโปรเจกต์ได้ ควบคุม token ได้ |
| 5. Modes & UI | Chat/IDE/CLI/Image-gen UI, thinking toggle | ใช้ได้ครบ 4 โหมด UI ไม่ crash |
| 6. Auto-Doc & Progress | AutoDocWriter, ProgressTracker, SessionResumeManager | Project.md/Progress.md/SOLUTIONS.md ทำงานอัตโนมัติ |
| 7. Skill/Rule/Permission | RuleEngine, PermissionMatrix, PromptProfileManager | ตั้งค่าได้ครบตามข้อ 9-11 |
| 8. Export/Import | KnowledgeExportService (.opk) | ย้ายความรู้ข้ามเครื่องได้จริง |
| 9. Hardening | Quality Gate เข้มข้น, แก้จุดเสี่ยงจากข้อ 10, stress test | ไม่มี known crash, ผ่านทุก test |
| 10. Deploy | ดูข้อ 12 | Release พร้อมใช้งานจริง |

แต่ละ phase ต้องปิด Quality Gate ก่อนไป phase ถัดไป — ไม่ข้ามลำดับ

---

## 12. Deployment Plan

1. **Build:** ใช้ Android signing config เดิมของ Operit เป็นฐาน (V3 signing rotation), แยก debug/release package identity
2. **Distribution:** GitHub Releases (APK) เป็นช่องทางหลัก, เว็บไซต์แยกสำหรับ Web Client (iPad/desktop)
3. **Versioning:** Semantic versioning, changelog auto-generate จาก commit ที่ผ่าน Quality Gate
4. **Monitoring หลัง release:** เก็บ crash report แบบ opt-in, error log ย้อนกลับเข้า `SOLUTIONS.md` อัตโนมัติเมื่อพบ pattern ซ้ำ
5. **Rollback plan:** เก็บ release ก่อนหน้าไว้เสมอ 1 เวอร์ชัน พร้อม downgrade path ถ้า release ใหม่พบ critical bug

---

## 13. Agent Execution Prompt (ใช้บังคับ AI Agent ที่ลงมือทำโปรเจกต์นี้)

```
คุณคือ AI Coding Agent ที่รับผิดชอบโปรเจกต์ OperitX ทั้งหมดตามเอกสาร PROJECT_PLAN.md

กฎการทำงาน (บังคับ 100% ห้ามฝ่าฝืน):

1. ก่อนเริ่มลงมือทำงานในแต่ละ Phase/Task เท่านั้น — คุณถามคำถามที่สงสัยได้ทั้งหมด
   จนกว่าจะชัดเจนพอจะเริ่มงาน หลังจากเริ่มลงมือแล้ว ห้ามหยุดถามอีกเด็ดขาด

2. เมื่อเริ่มลงมือทำ Task ใดแล้ว ให้ทำต่อเนื่องจนจบ Task นั้น โดย:
   - ห้ามหยุดกลางคันเพื่อถามคำถาม
   - ห้ามหยุดเพราะ "ไม่แน่ใจ" — ให้เลือกทางที่สมเหตุสมผลที่สุดจาก context ที่มี
     แล้วบันทึกสมมติฐานที่ใช้ไว้ใน Project.md แทนการถาม
   - ถ้าเจอ blocker จริง (เช่น ต้องใช้ credential ที่ไม่มี, ต้องตัดสินใจ
     สถาปัตยกรรมที่กระทบทั้งระบบและไม่มีในแผน) ให้บันทึกไว้ใน Progress.md
     ว่า "Blocked" พร้อมเหตุผล แล้ว**ทำ task อื่นที่ไม่ติด blocker ต่อ**
     ไม่ใช่หยุดทั้งหมดรอคำตอบ

3. ทำงานต่อเนื่องแบบ 24/7 ไม่พัก จนกว่าจะ:
   - ทำครบทุก Phase ในแผน หรือ
   - เจอ blocker ที่ทำให้ทุก task ที่เหลือติดหมดจริง ๆ (ไม่มี task อื่นให้ทำต่อ)

4. ก่อน compile/build ทุกครั้ง ต้องผ่าน Quality Gate (ข้อ 9 ในแผน) ครบทุกข้อ
   ห้ามส่งให้ผู้ใช้ทดสอบถ้ายังไม่ผ่าน

5. หลังจบทุก Task ต้องอัปเดต Project.md, Progress.md (พร้อม % ความคืบหน้า)
   และ SOLUTIONS.md (ถ้ามีปัญหาที่แก้ระหว่างทาง) ก่อนไป Task ถัดไปเสมอ

6. ห้ามข้ามลำดับ Phase ตามข้อ 11 ของแผน เว้นแต่ผู้ใช้อนุญาตชัดเจนไว้ก่อนเริ่มงาน

หากคุณเข้าใจกฎทั้งหมดแล้ว ให้เริ่มด้วยการถามคำถามที่จำเป็นทั้งหมดสำหรับ Phase 0
เป็นชุดเดียวก่อน แล้วจึงเริ่มลงมือทำตามกฎข้างต้น
```

---

## 14. สิ่งที่ยังต้องตัดสินใจเพิ่มก่อน Phase 0 (ผู้ใช้/agent ควรถามตอนเริ่มจริง)

- ชื่อแอปจริง + package name ใหม่ (แยกจาก Operit เดิมเพื่อไม่ชนกัน)
- งบประมาณ/เพดาน token เริ่มต้นของ ContextBudgetManager
- รายชื่อ provider ที่จะ implement ก่อนใน Phase 1 (ระบบ pluggable แต่ต้องมีตัวอย่างอย่างน้อย 1-2 ตัวใช้งานได้จริงตั้งแต่ต้น)
- จำนวน Worker agent สูงสุดต่อ session (จำกัดไว้ป้องกัน cost บาน)
