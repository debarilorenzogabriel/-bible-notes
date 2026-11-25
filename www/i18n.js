// i18n.js

// lingua attuale (default: it)
let language = localStorage.getItem("language") || "it";

const translations = {
  it: {
    // HOME
    "home.title": "Le mie note",
    "home.search": "Cerca note o riferimenti...",
    "home.newNote": "Nuova nota",

    // EDITOR
    "editor.newTitle": "Nuova nota",
    "editor.editTitle": "Modifica nota",
    "editor.titlePlaceholder": "Titolo (facoltativo)",
    "editor.addBibleRef": "Aggiungi riferimento biblico",
    "editor.addTag": "Aggiungi etichetta",
    "editor.addTagPlaceholder": "Aggiungi etichetta e premi invio",
    "editor.highlightLabel": "Evidenzia:",
    "editor.save": "Salva",
    "editor.cancel": "Annulla",
    "editor.delete": "Elimina Nota",
    "editor.back": "← Indietro",
    "context.copy": "Copia",
    "context.pin": "Fissa",
    "context.unpin": "Sgancia",
    "context.edit": "Modifica",
    "context.delete": "Elimina",

    // LABELS
    "labels.title": "Etichette",
    "labels.new": "Nuova etichetta",
    "labels.search": "Cerca etichette...",
    "labels.empty": "Nessuna etichetta ancora presente.",

    // SETTINGS
    "settings.title": "Impostazioni",
    "settings.jw.title": "Apri con JW Library",
    "settings.jw.desc": "Apre riferimenti su JW library.",
    "settings.theme.title": "Tema",
    "settings.theme.desc": "Scegli lo stile dei colori dell’app.",
    "settings.language.title": "Lingua",
    "settings.language.desc": "Scegli la tua lingua.",
    "settings.export.title": "Esporta/importa note",
    "settings.export.desc": "Esporta o importa le tue note.",

    // FOOTER
    "footer.notes": "Note",
    "footer.labels": "Etichette",
    "footer.settings": "Impostazioni",

    // MESSAGGI DI SISTEMA
    "empty.notes": "Nessuna nota trovata. Crea una nuova nota o modifica la ricerca.",
    "confirm.deleteNote": "Eliminare questa nota?",
    "confirm.cancelEdit": "Sei sicuro di voler annullare le modifiche?",
    "alert.emptyNote": "La nota è vuota.",
    "prompt.newLabel": "Nome della nuova etichetta:",
    "prompt.renameLabel": 'Rinomina etichetta "{name}" in:',
    "confirm.deleteLabel": 'Eliminare l\'etichetta "{name}" da tutte le note?',
    "prompt.verse": "Inserisci il versetto (es. 3 o 3-5):",
    "alert.missingBookOrChapter": "Libro o capitolo mancanti.",
    "alert.selectBookFirst": "Seleziona prima un libro tramite il wizard.",
	
	 // LABEL ACTIONS
    "labels.action.search": "Cerca",
    "labels.action.rename": "Rinomina",
    "labels.action.delete": "Elimina",

    // IMPORT / EXPORT
    "settings.import": "Importa",
    "settings.export": "Esporta"
  },

  en: {
    // HOME
    "home.title": "My notes",
    "home.search": "Search notes or references...",
    "home.newNote": "New note",

    // EDITOR
    "editor.newTitle": "New note",
    "editor.editTitle": "Edit note",
    "editor.titlePlaceholder": "Title (optional)",
    "editor.addBibleRef": "Add Bible reference",
    "editor.addTag": "Add label",
    "editor.addTagPlaceholder": "Add label and press Enter",
    "editor.highlightLabel": "Highlight:",
    "editor.save": "Save",
    "editor.cancel": "Cancel",
    "editor.delete": "Delete note",
    "editor.back": "← Back",
	"context.copy": "Copy",
    "context.pin": "Pin",
    "context.unpin": "Unpin",
    "context.edit": "Edit",
    "context.delete": "Delete",

    // LABELS
    "labels.title": "Labels",
    "labels.new": "New label",
    "labels.search": "Search labels...",
    "labels.empty": "No labels yet.",

    // SETTINGS
    "settings.title": "Settings",
    "settings.jw.title": "Open with JW Library",
    "settings.jw.desc": "Open references in JW Library.",
    "settings.theme.title": "Theme",
    "settings.theme.desc": "Choose the app color style.",
    "settings.language.title": "Language",
    "settings.language.desc": "Choose your language.",
    "settings.export.title": "Export/import notes",
    "settings.export.desc": "Import or export your notes.",

    // FOOTER
    "footer.notes": "Notes",
    "footer.labels": "Labels",
    "footer.settings": "Settings",

    // SYSTEM MESSAGES
    "empty.notes": "No notes found. Create a new note or change your search.",
    "confirm.deleteNote": "Delete this note?",
    "confirm.cancelEdit": "Are you sure you want to discard changes?",
    "alert.emptyNote": "The note is empty.",
    "prompt.newLabel": "New label name:",
    "prompt.renameLabel": 'Rename label "{name}" to:',
    "confirm.deleteLabel": 'Delete label "{name}" from all notes?',
    "prompt.verse": "Enter the verse (e.g. 3 or 3-5):",
    "alert.missingBookOrChapter": "Missing book or chapter.",
    "alert.selectBookFirst": "Select a book first using the wizard.",
	
	    // LABEL ACTIONS
    "labels.action.search": "Search",
    "labels.action.rename": "Rename",
    "labels.action.delete": "Delete",

    // IMPORT / EXPORT
    "settings.import": "Import",
    "settings.export": "Export"
  }
};

// Ritorna la stringa tradotta
function t(key) {
  const dict = translations[language] || translations.it;
  return dict[key] || key;
}

// Applica le traduzioni al DOM
function applyLanguage() {
  // testi interni (innerText / textContent)
  const nodes = document.querySelectorAll("[data-i18n]");
  nodes.forEach(el => {
    const key = el.getAttribute("data-i18n");
    if (!key) return;
    el.textContent = t(key);
  });

  // placeholder
  const placeholderNodes = document.querySelectorAll("[data-i18n-placeholder]");
  placeholderNodes.forEach(el => {
    const key = el.getAttribute("data-i18n-placeholder");
    if (!key) return;
    el.setAttribute("placeholder", t(key));
  });

  // footer labels (in realtà già coperti da data-i18n, ma lasciamo il pattern se serve)
  const footerButtons = document.querySelectorAll(".footer-item");
  footerButtons.forEach(btn => {
    const view = btn.dataset.view;
    const labelSpan = btn.querySelector(".footer-label");
    if (!labelSpan || !view) return;
    if (view === "home") labelSpan.textContent = t("footer.notes");
    else if (view === "labels") labelSpan.textContent = t("footer.labels");
    else if (view === "settings") labelSpan.textContent = t("footer.settings");
  });
}

// Cambia lingua e salva in localStorage
function setLanguage(newLang) {
  language = newLang;
  localStorage.setItem("language", language);
  applyLanguage();
}