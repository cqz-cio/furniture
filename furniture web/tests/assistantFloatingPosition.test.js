import { describe, expect, it } from "vitest";
import {
  getDefaultLauncherPosition,
  getDefaultPanelState,
  snapHorizontalPosition,
} from "../src/utils/assistantFloatingPosition.js";

describe("assistant floating position", () => {
  it("places launcher and panel in the bottom-right safe area", () => {
    expect(getDefaultLauncherPosition({ width: 1365, height: 918 }, 56, 24))
      .toEqual({ left: 1285, top: 838 });
    expect(getDefaultPanelState({ width: 1365, height: 918 }, {
      width: 460,
      height: 660,
      margin: 24,
    })).toEqual({ left: 881, top: 234, width: 460, height: 660 });
  });

  it("snaps to the nearest horizontal edge", () => {
    expect(snapHorizontalPosition(120, 460, 1365, 24)).toBe(24);
    expect(snapHorizontalPosition(760, 460, 1365, 24)).toBe(881);
  });
});
