import { describe, expect, it } from "vitest";
import {
  REGISTRY_VISIBILITY,
  createGiftRegistryDraft,
  getGiftRegistrySteps,
  getRegistryShareState,
  normalizeRegistryAddress,
  updateGiftRegistryDraft,
} from "../src/services/giftRegistry.js";

describe("gift registry model", () => {
  it("creates an Oakved registry draft with event, registrant, address and privacy defaults", () => {
    expect(createGiftRegistryDraft()).toMatchObject({
      event: {
        type: "Wedding",
        date: "",
      },
      registrants: {
        primaryName: "",
        coRegistrantName: "",
        email: "",
        phone: "",
      },
      addresses: {
        beforeEvent: { label: "Before Event", kind: "local", line1: "" },
        afterEvent: { label: "After Event", kind: "local", line1: "" },
      },
      privacy: {
        visibility: "public",
        emailSubscription: true,
        giftCardPreference: true,
      },
    });
  });

  it("normalizes registry delivery addresses", () => {
    expect(normalizeRegistryAddress({ line1: "  123 Oak Road ", city: " Boston ", kind: "custom" })).toMatchObject({
      kind: "custom",
      line1: "123 Oak Road",
      city: "Boston",
    });
  });

  it("updates nested draft sections without losing other sections", () => {
    const draft = updateGiftRegistryDraft(createGiftRegistryDraft(), {
      event: { type: "Housewarming", date: "2026-09-15" },
      privacy: { visibility: REGISTRY_VISIBILITY.inviteOnly },
    });

    expect(draft.event).toEqual({ type: "Housewarming", date: "2026-09-15" });
    expect(draft.privacy.visibility).toBe("invite_only");
    expect(draft.registrants.primaryName).toBe("");
  });

  it("summarizes step completion for create registry flow", () => {
    const draft = updateGiftRegistryDraft(createGiftRegistryDraft(), {
      event: { type: "Wedding", date: "2026-10-01" },
      registrants: { primaryName: "Avery Stone", email: "avery@example.com" },
    });

    expect(getGiftRegistrySteps(draft).map((step) => step.key)).toEqual([
      "event",
      "registrants",
      "addresses",
      "privacy",
      "share",
    ]);
    expect(getGiftRegistrySteps(draft).find((step) => step.key === "event").complete).toBe(true);
    expect(getGiftRegistrySteps(draft).find((step) => step.key === "addresses").complete).toBe(false);
  });

  it("builds share state after a registry has enough public identity", () => {
    const draft = updateGiftRegistryDraft(createGiftRegistryDraft(), {
      id: "registry-avery-2026",
      event: { type: "Wedding", date: "2026-10-01" },
      registrants: { primaryName: "Avery Stone", email: "avery@example.com" },
    });

    expect(getRegistryShareState(draft)).toMatchObject({
      ready: true,
      publicUrl: "/gift-registry/registry-avery-2026",
      purchasedAutoMarking: true,
    });
  });
});
