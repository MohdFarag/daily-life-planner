import { Upload } from "lucide-react";
import { ICON_STROKE } from "../../components";
import { useObjectUrl } from "../../hooks/useObjectUrl";
import { useStore } from "../../state";

/** Picks a photo (camera on phones, with a preview) or any file. */
export function FilePicker({ photo, file, onChange }: { photo: boolean; file: File | null; onChange: (file: File | null) => void }) {
  const s = useStore();
  const preview = useObjectUrl(photo ? file : null);
  return (
    <>
      <label className="btn btn-secondary" style={{ cursor: "pointer" }}>
        <Upload size={20} strokeWidth={ICON_STROKE} />
        <span className="truncate">{file ? file.name : photo ? s.t("choosePhoto") : s.t("chooseFile")}</span>
        <input
          type="file"
          hidden
          accept={photo ? "image/*" : undefined}
          capture={photo ? "environment" : undefined}
          onChange={(e) => onChange(e.target.files?.[0] ?? null)}
        />
      </label>
      {preview && <img src={preview} alt="" style={{ maxHeight: 120, borderRadius: 12, objectFit: "cover" }} />}
    </>
  );
}
