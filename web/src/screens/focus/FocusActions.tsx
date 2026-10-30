import { Button } from "../../components";
import { type Task, useStore } from "../../state";

interface Props {
  task: Task;
  running: boolean;
  finished: boolean;
  onExtend: () => void;
}

/** Pause/resume (or five more minutes once time is up), end the session, or complete the task. */
export function FocusActions({ task, running, finished, onExtend }: Props) {
  const s = useStore();
  return (
    <div className="stack-8">
      {finished ? (
        <Button
          onClick={() => {
            onExtend();
            s.resumeFocus();
          }}
        >
          {s.t("addFive")}
        </Button>
      ) : (
        <Button onClick={running ? s.pauseFocus : s.resumeFocus}>{running ? s.t("pauseFocus") : s.t("resumeFocus")}</Button>
      )}
      <Button
        variant="secondary"
        onClick={() => {
          s.endFocus();
          s.back();
        }}
      >
        {s.t("endSession")}
      </Button>
      <Button
        variant="secondary"
        onClick={() => {
          s.toggleDone(task.id);
          s.tab("today");
        }}
      >
        {s.t("completeTask")}
      </Button>
    </div>
  );
}
