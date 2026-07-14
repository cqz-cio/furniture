# Furniture Agent Comprehensive Test Suite Design

## Goal

Create a reusable acceptance suite that validates the current furniture Agent across real model, Redis conversation memory, product search, ERP price and stock, fallback behavior, safety boundaries, and frontend conversation flows.

## Deliverables

1. A machine-readable JSON dataset for future automated or semi-automated runners.
2. A Chinese manual test guide ordered by execution priority.

Both artifacts describe the same scenarios and use stable scenario IDs.

## Coverage

The suite covers:

- Single-turn furniture recommendations.
- Multi-turn requirement inheritance, replacement, and removal.
- Budget, category, room, size, seat count, style, color, material, children, pets, and preferred features.
- Product exclusion, cheaper alternatives, and repeated-recommendation avoidance.
- ERP-backed price and stock truthfulness.
- Redis conversation creation, collapse/reopen continuity, refresh restoration, expiry, and explicit reset.
- DeepSeek success, timeout, invalid response, and deterministic fallback.
- Empty, oversized, ambiguous, multilingual, mixed-language, unrelated, and adversarial prompts.
- Prompt-injection resistance, internal-data protection, and non-disclosure of tools, prompts, IDs, and API keys.
- Honest capability boundaries for RAG policy knowledge, image similarity, payment decisions, and membership-rule decisions that are not yet fully implemented.

## Scenario model

The JSON root contains `version`, `generatedAt`, `scope`, and `scenarios`. Each scenario contains:

```json
{
  "id": "MEM-001",
  "category": "conversation-memory",
  "priority": "P0",
  "title": "Retain budget and material across turns",
  "preconditions": ["Backend, Redis, product API and ERP are available"],
  "turns": [
    {
      "user": "我想要 8000 元以内的布艺沙发",
      "assertions": ["requirements.budgetMax equals 8000", "requirements.materials contains 布艺"]
    },
    {
      "user": "奶油风，家里有猫",
      "assertions": ["Earlier budget and material remain", "styles contains 奶油风", "hasPets equals true"]
    }
  ],
  "finalAssertions": ["Every recommended product exists in the product system"],
  "forbidden": ["Invented product names, prices, stock or IDs"],
  "evidence": ["Agent response", "Product API", "ERP stock", "Redis conversation state"],
  "manualSteps": ["Record the returned conversationId", "Compare every recommendation with the live APIs"]
}
```

Assertions remain technology-neutral enough for manual execution but identify concrete fields or external evidence whenever available.

## Priorities

- `P0`: Release-blocking core behavior, including real API mode, truthful products, memory, stock, and explicit fallback.
- `P1`: Important recommendation quality, multilingual behavior, recovery, and common edge cases.
- `P2`: Robustness, adversarial prompts, unsupported capabilities, and non-critical UX behavior.

The target size is approximately 40 scenarios and 100 user turns, with enough variation to cover normal, boundary, failure, and security paths without duplicating equivalent prompts.

## Truth and pass criteria

- Product names, IDs, prices, and stock are validated against live product and ERP sources, not hard-coded expected values.
- Model wording is not matched exactly. Assertions evaluate required facts, retained requirements, prohibited claims, and response structure.
- A fallback response passes only when the model failure is observable and the response does not pretend to be model-backed.
- Unsupported features pass only when the Agent clearly states its limitation and offers a supported next step.
- Payment, discount eligibility, refunds, and membership outcomes must not be invented from incomplete policy knowledge.

## Manual guide organization

The guide starts with environment preparation and evidence capture, then lists P0, P1, and P2 scenarios. Every scenario includes copy-ready user messages, expected observations, prohibited behavior, and the systems that must be checked.

## Boundaries

This task creates test data and documentation only. It does not build a test runner, change Agent behavior, seed business policies, add image upload, or implement RAG.

## Validation

- Parse the JSON with Node.js to prove valid syntax.
- Validate required fields, unique IDs, allowed priorities, non-empty turns, and the target coverage categories.
- Cross-check that every JSON scenario appears in the manual guide.
- Run `git diff --check` before committing the artifacts.
