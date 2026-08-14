[한국어](README.md) · **English**

# Legislative Impact Analyzer (LIA)

**How a soon-to-take-effect law changes things for *you*, and what you should do about it** —
ask in plain language, get an answer grounded in cited articles.

---

## What it does for you

Laws are promulgated **before** they take effect. In that gap, it's hard for an ordinary person
to know "what does this law actually change for me?" LIA picks out laws that are **not yet in force**
and translates them into the impact on you and the actions you should take.

Just ask, in plain language:

- "If the Housing Act changes, what happens to my jeonse (lease) contract?"
- "Find upcoming laws that will affect me."
- "This amendment — what do I need to do, and by when?"

Answers come in four parts:

| | |
|---|---|
| **Plain summary** | what this law is |
| **What changes** | articles that differ from current law |
| **Impact on you** | tailored to your situation (lease, occupation, etc.) |
| **What to do** | what, and by when |

### Example

> **Q.** "The Housing Act amendment taking effect on 2026-08-04 — what changes for a jeonse tenant?"
>
> **A.** You gain a new **right to request an on-site inspection** before the use-approval
> (Housing Act Art. 49). It takes effect on **2026-08-04** and applies to use-approval
> applications filed on or after that date.
> — *Basis: Housing Act Art. 49 · Addendum Art. 3*

---

## Answers you can trust

Wrong legal information is dangerous. LIA aims for **grounded answers**, not merely plausible ones.

- **It doesn't make things up** — it uses only articles verified from an authoritative source
  (the Korean National Law Information Center). No basis, no answer.
- **Every claim links to an article** — you can click through and verify the source yourself.
- **It distinguishes the unverified** — "not yet promulgated" is presented differently from
  "a law that doesn't exist (a rumor)."
- **It is not legal advice** — it's reference information, and always says so.

### Why not just ask ChatGPT or Claude?

| Just asking an LLM | LIA |
|---|---|
| **Doesn't know** — a specific upcoming amendment is outside its training | **Retrieves** the actual current articles and uses them as basis |
| **Fabricates** — confidently hallucinates plausible article numbers | **No basis, no answer** (doesn't invent) |
| **Can't pin the right version** | Identifies the version and changed articles for you |
| **Unverifiable** | Every answer carries an **article link** |

LIA does not compete with an LLM's reasoning. Its job is to solve what makes a raw LLM
unusable for *legal* information — knowledge gaps, hallucination, and verification.

---

## What it covers — laws awaiting enforcement

There are many bills that "might someday" become law, but LIA covers only laws that are
**already finalized and merely awaiting their effective date**.

| | Bills under deliberation | **Laws promulgated, awaiting enforcement** |
|---|---|---|
| Certainty | uncertain (low passage rate) | **100% confirmed** |
| Effective date | undecided | **fixed date** |
| In LIA | not covered | **analyzed** |

Analyzing a bill that may not pass means telling you about *something that may never happen*.
For laws awaiting enforcement, the effective date is fixed, so "do X by when" finally holds
without qualification.

---

## Privacy

- **No name, contact details, or national ID.** Personalization uses only a **self-reported profile**
  at sign-up (purpose of use, age, occupation group, housing type, etc. — all optional).
- Region is collected only down to the province level; income and detailed address are not collected.
- We follow data-minimization; the full privacy policy will be provided at service launch.

---

## For developers

Technical highlights:

- **Cost-aware design** — a natural-language question is structured so the LLM is called *only as much
  as needed*. Summaries and comparisons are precomputed; only personalized impact/action analysis
  reaches the model. Only changed articles are selected (measured: 137 → 6), cutting throughput cost.
- **Grounding first** — every claim cites an article; without a citation the response is blocked
  (fail-closed).
- **Measure-first operations** — Prometheus · Loki · Grafana · Tempo + k6. Concurrency optimizations
  are applied only after metrics prove the bottleneck.

Stack: Java 21 · Spring Boot 4.0 · Spring AI 2.0 · PostgreSQL + pgvector · Claude (Opus · Haiku).

Internal design docs, decision history, component specs, backend, and troubleshooting live in
[`docs/`](docs/). Status, tech spec, and the implementation checklist are in
[`docs/status.md`](docs/status.md); new here? Start with [`docs/onboarding.md`](docs/onboarding.md).

```bash
cd core && ./gradlew test
```

---

## Disclaimer

This is a **reference-information service, not legal advice**.
Every result is traceable to primary sources — statute articles, addenda, amendment texts —
and when the basis is insufficient, no answer is produced; the response is blocked.
