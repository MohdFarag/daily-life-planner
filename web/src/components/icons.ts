/** Shared icon choices so every screen draws the same glyph for the same thing. */
import { BookOpen, Camera, ChevronLeft, FileText, Link2, type LucideIcon, Mic, Type } from "lucide-react";
import type { ItemKind } from "../state";

export const ICON_STROKE = 1.75;

export const KIND_ICON: Record<ItemKind, LucideIcon> = {
  text: Type,
  link: BookOpen,
  voice: Mic,
  photo: Camera,
  file: FileText,
};

export const LinkIcon = Link2;

export const BackIcon = ChevronLeft;
