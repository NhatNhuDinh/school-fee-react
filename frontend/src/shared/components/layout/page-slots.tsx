"use client";

import { createContext, useContext, useState, type PropsWithChildren, type ReactNode } from "react";
import { createPortal } from "react-dom";

/**
 * Two holes in the top bar that a screen fills from below: the left one takes the screen's name, the right one its
 * actions. The bar used to be a strip of session controls over a page that repeated its own name in a heading, which
 * left the bar half empty and the page one row too tall; with the name and the page's main button up here, the page
 * starts at its content.
 *
 * It is a portal rather than a lifted state value on purpose: the screen keeps rendering its own buttons, with its
 * own handlers and state, and they simply appear somewhere else in the DOM.
 */
type Slots = {
  title: HTMLElement | null;
  setTitle: (el: HTMLElement | null) => void;
  actions: HTMLElement | null;
  setActions: (el: HTMLElement | null) => void;
};

const SlotsContext = createContext<Slots | null>(null);

export function PageSlotsProvider({ children }: PropsWithChildren) {
  const [title, setTitle] = useState<HTMLElement | null>(null);
  const [actions, setActions] = useState<HTMLElement | null>(null);
  return <SlotsContext.Provider value={{ title, setTitle, actions, setActions }}>{children}</SlotsContext.Provider>;
}

/** Where the screen's name goes. Rendered once, by the top bar. */
export function PageTitleSlot({ className }: { className?: string }) {
  const slots = useContext(SlotsContext);
  return <div ref={slots?.setTitle} className={className} />;
}

/** Where the screen's actions go. Rendered once, by the top bar. */
export function PageActionsSlot({ className }: { className?: string }) {
  const slots = useContext(SlotsContext);
  return <div ref={slots?.setActions} data-print="hide" className={className} />;
}

/** The screen's name, shown in the top bar. Outside a shell with the slots, it stays where it is written. */
export function PageTitle({ children }: { children: ReactNode }) {
  const slots = useContext(SlotsContext);
  if (!slots) return <>{children}</>;
  return slots.title ? createPortal(children, slots.title) : null;
}

/** The screen's actions, shown in the top bar. Outside a shell with the slots, they stay where they are written. */
export function PageActions({ children }: { children: ReactNode }) {
  const slots = useContext(SlotsContext);
  if (!slots) return <>{children}</>;
  return slots.actions ? createPortal(children, slots.actions) : null;
}
