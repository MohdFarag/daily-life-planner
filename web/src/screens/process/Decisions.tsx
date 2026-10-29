import { Archive, Clock3, Ellipsis, Scissors, Trash2 } from "lucide-react";
import { useState } from "react";
import { Button, ICON_STROKE, MenuItem } from "../../components";
import { type TriageAction, useStore } from "../../state";

/** The three common decisions as buttons; the rest behind "other decisions". */
export function Decisions({ onDecide }: { onDecide: (action: TriageAction) => void }) {
  const s = useStore();
  const [menuOpen, setMenuOpen] = useState(false);
  return (
    <>
      <p className="t-h3">{s.t("whatBecome")}</p>
      <div className="stack-8">
        <Button onClick={() => onDecide("task")}>{s.t("createTask")}</Button>
        <Button variant="secondary" onClick={() => onDecide("now")}>
          {s.t("doNow")}
        </Button>
        <Button variant="secondary" onClick={() => onDecide("reference")}>
          {s.t("saveReference")}
        </Button>
        <button className="row-between" style={{ minHeight: 48 }} aria-expanded={menuOpen} onClick={() => setMenuOpen((v) => !v)}>
          <span className="t-small c-secondary">{s.t("otherDecisions")}</span>
          <Ellipsis size={24} strokeWidth={ICON_STROKE} className="c-secondary" />
        </button>
        {menuOpen && (
          <div className="card menu" role="menu">
            <MenuItem icon={Clock3} label={s.t("someday")} onClick={() => onDecide("someday")} />
            <MenuItem icon={Archive} label={s.t("waiting")} onClick={() => onDecide("waiting")} />
            <MenuItem icon={Scissors} label={s.t("split")} onClick={() => onDecide("split")} />
            <MenuItem icon={Trash2} label={s.t("trash")} onClick={() => onDecide("trash")} danger />
          </div>
        )}
      </div>
    </>
  );
}
