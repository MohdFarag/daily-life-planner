import { describe, expect, it } from "vitest";
import { captureFromText, splitCaption } from "./capture";

describe("captureFromText", () => {
  it("turns a lone URL into a link titled by its host", () => {
    expect(captureFromText("  https://www.example.com/a  ")).toEqual({ kind: "link", title: "example.com", body: "", url: "https://www.example.com/a" });
    expect(captureFromText("www.example.com").url).toBe("https://www.example.com");
  });

  it("keeps a URL followed by more text as a note", () => {
    expect(captureFromText("https://example.com\nread later")).toMatchObject({ kind: "text", title: "https://example.com", body: "read later" });
  });

  it("titles a note by its first line and keeps a long line whole in the body", () => {
    expect(captureFromText("Call the plumber\nAsk about Thursday")).toEqual({ kind: "text", title: "Call the plumber", body: "Ask about Thursday" });
    const long = "x".repeat(100);
    expect(captureFromText(long)).toEqual({ kind: "text", title: "x".repeat(80), body: long });
  });
});

it("splitCaption separates the first line", () => {
  expect(splitCaption(" Title\nline 1\nline 2 ")).toEqual({ first: "Title", rest: "line 1\nline 2" });
  expect(splitCaption("")).toEqual({ first: "", rest: "" });
});
