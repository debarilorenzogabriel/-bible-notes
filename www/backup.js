// backup.js

// esporta notes + allLabels come JSON e scarica un file
function exportNotesToFile() {
  if (!window.notes || !Array.isArray(window.notes)) {
    alert("Nessuna nota da esportare.");
    return;
  }

  const payload = {
    version: 1,
    exportedAt: Date.now(),
    notes: window.notes,
    allLabels: window.allLabels || []
  };

  const json = JSON.stringify(payload, null, 2);
  const blob = new Blob([json], { type: "application/json" });
  const url = URL.createObjectURL(blob);

  const a = document.createElement("a");
  const dateStr = new Date().toISOString().slice(0, 10); // yyyy-mm-dd
  a.href = url;
  a.download = `bible-notes-backup-${dateStr}.json`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

// importa JSON e sostituisce notes + allLabels
function importNotesFromFile(file) {
  if (!file) return;

  const reader = new FileReader();
  reader.onload = (e) => {
    try {
      const text = e.target.result;
      const data = JSON.parse(text);

      if (!data || !Array.isArray(data.notes)) {
        alert("File di backup non valido.");
        return;
      }

      // sovrascrivi interamente
      window.notes = data.notes;
      window.allLabels = Array.isArray(data.allLabels) ? data.allLabels : [];

      if (typeof window.saveToStorage === "function") {
        window.saveToStorage();
      }

      if (typeof window.renderNotes === "function") {
        window.renderNotes();
      }
      if (typeof window.renderLabelsView === "function") {
        window.renderLabelsView();
      }

      alert("Note importate correttamente.");
    } catch (err) {
      console.error("Errore import backup:", err);
      alert("Errore durante l'importazione del file.");
    }
  };
  reader.readAsText(file);
}