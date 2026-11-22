"use client";

import type React from "react";
import { EmptyState } from "../../../ui/EmptyState";

interface EmptyStateV3Props {
  icon: string;
  title: string;
  hint?: string;
}

/** Salami: leerer Zustand der Profil-Reiter – das gemeinsame `EmptyState` (Symbol auf einer Scheibe). */
export const EmptyStateV3: React.FC<EmptyStateV3Props> = ({ icon, title, hint }) => (
  <EmptyState
    icon={icon}
    message={title}
    description={hint}
    compact
    smallDescription
    fullHeight={false}
    className="py-12"
  />
);
