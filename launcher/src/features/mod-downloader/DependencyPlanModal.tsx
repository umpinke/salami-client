import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Icon } from "@iconify/react";
import { Modal } from "../../components/ui/Modal";
import { Button } from "../../components/ui/buttons/Button";
import { Checkbox } from "../../components/ui/Checkbox";
import type { DependencyPlan, PlannedDependency } from "./dependency-plan";
import "./i18n";

export interface DependencyPlanDecision {
  proceed: boolean;
  optional: PlannedDependency[];
  dontAskAgain: boolean;
}

interface Props {
  name: string;
  plan: DependencyPlan;
  onDone: (decision: DependencyPlanDecision) => void;
}

/** Bestätigungsdialog vor einer Installation: Pflicht-, optionale und Konflikt-Abhängigkeiten. */
export function DependencyPlanModal({ name, plan, onDone }: Props) {
  const { t } = useTranslation();
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [dontAsk, setDontAsk] = useState(false);
  const hasConflicts = plan.conflicts.length > 0;

  const label = (dep: PlannedDependency) =>
    dep.name ||
    t("salami.downloader.plan.unknown_project", {
      platform: dep.platform === "modrinth" ? "Modrinth" : "CurseForge",
      id: dep.projectId,
    });

  const toggle = (id: string) =>
    setSelected((prev) => {
      const next = new Set(prev);
      if (!next.delete(id)) next.add(id);
      return next;
    });

  const finish = (proceed: boolean) =>
    onDone({
      proceed,
      optional: proceed ? plan.optional.filter((d) => selected.has(d.projectId)) : [],
      dontAskAgain: proceed && dontAsk,
    });

  return (
    <Modal
      title={t("salami.downloader.plan.title", { name })}
      titleIcon={<Icon icon="solar:box-bold" className="w-6 h-6" />}
      onClose={() => finish(false)}
      width="md"
      footer={
        <div className="flex items-center justify-between gap-3 w-full">
          <Checkbox
            checked={dontAsk}
            onChange={(e) => setDontAsk(e.target.checked)}
            label={t("salami.downloader.plan.dont_ask")}
            size="sm"
          />
          <div className="flex gap-2">
            <Button variant="secondary" onClick={() => finish(false)}>
              {t("salami.downloader.plan.cancel")}
            </Button>
            <Button variant={hasConflicts ? "warning" : "success"} onClick={() => finish(true)}>
              {t(hasConflicts ? "salami.downloader.plan.install_anyway" : "salami.downloader.plan.install")}
            </Button>
          </div>
        </div>
      }
    >
      <div className="space-y-4 p-1">
        {hasConflicts && (
          <Section icon="solar:danger-triangle-bold" title={t("salami.downloader.plan.conflicts")} tone="text-amber-300">
            <p className="text-sm text-white/70 mb-2">{t("salami.downloader.plan.conflict_hint", { name })}</p>
            {plan.conflicts.map((dep) => (
              <Row key={dep.projectId} text={label(dep)} />
            ))}
          </Section>
        )}
        {plan.required.length > 0 && (
          <Section icon="solar:download-minimalistic-bold" title={t("salami.downloader.plan.required")}>
            {plan.required.map((dep) => (
              <Row key={dep.projectId} text={label(dep)} />
            ))}
          </Section>
        )}
        {plan.optional.length > 0 && (
          <Section icon="solar:add-circle-bold" title={t("salami.downloader.plan.optional")}>
            {plan.optional.map((dep) => (
              <Checkbox
                key={dep.projectId}
                checked={selected.has(dep.projectId)}
                onChange={() => toggle(dep.projectId)}
                label={label(dep)}
                size="sm"
              />
            ))}
          </Section>
        )}
        {plan.alreadyInstalled.length > 0 && (
          <Section icon="solar:check-circle-bold" title={t("salami.downloader.plan.already")} tone="text-white/50">
            {plan.alreadyInstalled.map((dep) => (
              <Row key={dep.projectId} text={label(dep)} dim />
            ))}
          </Section>
        )}
      </div>
    </Modal>
  );
}

function Section({ icon, title, tone = "text-white", children }: { icon: string; title: string; tone?: string; children: React.ReactNode }) {
  return (
    <div>
      <div className={`flex items-center gap-2 font-minecraft text-xl lowercase mb-1 ${tone}`}>
        <Icon icon={icon} className="w-5 h-5" />
        {title}
      </div>
      <div className="pl-7 space-y-1">{children}</div>
    </div>
  );
}

function Row({ text, dim }: { text: string; dim?: boolean }) {
  return <div className={`text-sm ${dim ? "text-white/50" : "text-white/85"}`}>• {text}</div>;
}
