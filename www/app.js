// -----------------------
// STATO E RIFERIMENTI DOM
// -----------------------

// VISTE PRINCIPALI
const homeView = document.getElementById("homeView");
const editorView = document.getElementById("editorView");
const labelsView = document.getElementById("labelsView");
const settingsView = document.getElementById("settingsView");

const notesGrid = document.getElementById("notesGrid");
const searchInput = document.getElementById("searchInput");
const noteContextMenu = document.getElementById("noteContextMenu");
let longPressTimer = null;
let contextNoteId = null;

const newNoteBtn = document.getElementById("newNoteBtn");
const backToHomeBtn = document.getElementById("backToHomeBtn");
const saveNoteBtn = document.getElementById("saveNoteBtn");
const cancelEditBtn = document.getElementById("cancelEditBtn");
const deleteNoteBtn = document.getElementById("deleteNoteBtn");

const scrollTopBtn = document.getElementById("scrollTopBtn");

// FOOTER
const footerNav = document.getElementById("footerNav");
const footerItems = footerNav ? footerNav.querySelectorAll(".footer-item") : [];

// EDITOR
const noteTitleInput = document.getElementById("noteTitle");
const noteBodyInput = document.getElementById("noteBody");
const noteTagsInput = document.getElementById("noteTags"); // hidden
const editorTitle = document.getElementById("editorTitle");
const addTagBtn = document.getElementById("addTagBtn");
const editorTagsBadge = document.getElementById("editorTagsBadge");
const editorTagsText = document.getElementById("editorTagsText");

// LABELS / ETICHETTE
const labelsListEl = document.getElementById("labelsList");
const labelsEmptyEl = document.getElementById("labelsEmpty");
const tagSuggestionsEl = document.getElementById("tagSuggestions");
const noteTagsVisibleInput = document.getElementById("noteTagsInputVisible");
const labelSearchInput = document.getElementById("labelSearchInput");

// RIFERIMENTI BIBLICI
const bibleChapterDisplay = document.getElementById("bibleChapterDisplay");
const bibleVerseInput = document.getElementById("bibleVerse");
const bibleRefRow = document.getElementById("bibleRefRow");
const addBibleRefBtn = document.getElementById("addBibleRefBtn");

// EVIDENZIAZIONE
const highlightToolbar = document.getElementById("highlightToolbar");
const highlightButtons = highlightToolbar ? highlightToolbar.querySelectorAll("button") : [];

// MODALI LIBRI / CAPITOLI
const bookModal = document.getElementById("bookModal");
const closeBookModal = document.getElementById("closeBookModal");
const bookTiles = document.querySelectorAll(".book-tile");

const chapterModal = document.getElementById("verseModal");
const closeChapterModal = document.getElementById("closeVerseModal");
const chapterGrid = document.getElementById("verseGrid");

// IMPORT / EXPORT
const importNotesBtn = document.getElementById("importNotesBtn");
const exportNotesBtn = document.getElementById("exportNotesBtn");
const importFileInput = document.getElementById("importFileInput");

// IMPOSTAZIONI
const toggleOpenModeSwitch = document.getElementById("toggleOpenModeSwitch");

// TEMA: pallini
const themeOptionsEl = document.getElementById("themeOptions");
let themeDots = [];
if (themeOptionsEl) {
  themeDots = Array.from(themeOptionsEl.querySelectorAll(".theme-dot"));
}

// LINGUA: bottoni
const languageOptionsEl = document.getElementById("languageOptions");
let languageButtons = [];
if (languageOptionsEl) {
  languageButtons = Array.from(languageOptionsEl.querySelectorAll(".lang-btn"));
}

// STATO
let notes = JSON.parse(localStorage.getItem("stickyNotes")) || [];
let allLabels = JSON.parse(localStorage.getItem("allLabels")) || [];
let activeId = null;
const TAG_SEPARATOR = ",";
let selectedBookFull = null;
let selectedBookAbbr = null;
let selectedChapter = null;

// jwlibrary oppure jworg
let openMode = localStorage.getItem("openMode") || "jworg";

// tema: light/dark/sepia/blue
let theme = localStorage.getItem("theme") || "light";

// vista corrente logica (home/labels/settings)
let currentView = "home";

// NUMERO CAPITOLI PER LIBRO
const BOOK_CHAPTERS = {
  "Genesi": 50, "Esodo": 40, "Levitico": 27, "Numeri": 36, "Deuteronomio": 34,
  "Giosuè": 24, "Giudici": 21, "Rut": 4, "1 Samuele": 31, "2 Samuele": 24,
  "1 Re": 22, "2 Re": 25, "1 Cronache": 29, "2 Cronache": 36, "Esdra": 10,
  "Neemia": 13, "Ester": 10, "Giobbe": 42, "Salmi": 150, "Proverbi": 31,
  "Ecclesiaste": 12, "Cantico dei Cantici": 8, "Isaia": 66, "Geremia": 52,
  "Lamentazioni": 5, "Ezechiele": 48, "Daniele": 12, "Osea": 14, "Gioele": 3,
  "Amos": 9, "Abdia": 1, "Giona": 4, "Michea": 7, "Naum": 3, "Abacuc": 3,
  "Sofonia": 3, "Aggeo": 2, "Zaccaria": 14, "Malachia": 4, "Matteo": 28,
  "Marco": 16, "Luca": 24, "Giovanni": 21, "Atti": 28, "Romani": 16,
  "1 Corinti": 16, "2 Corinti": 13, "Galati": 6, "Efesini": 6, "Filippesi": 4,
  "Colossesi": 4, "1 Tessalonicesi": 5, "2 Tessalonicesi": 3, "1 Timoteo": 6,
  "2 Timoteo": 4, "Tito": 3, "Filemone": 1, "Ebrei": 13, "Giacomo": 5,
  "1 Pietro": 5, "2 Pietro": 3, "1 Giovanni": 5, "2 Giovanni": 1, "3 Giovanni": 1,
  "Giuda": 1, "Rivelazione": 22
};

// Mappa libro -> numero 1-66
const BOOK_NUMBERS = {
  "Genesi": 1, "Esodo": 2, "Levitico": 3, "Numeri": 4, "Deuteronomio": 5,
  "Giosuè": 6, "Giudici": 7, "Rut": 8, "1 Samuele": 9, "2 Samuele": 10,
  "1 Re": 11, "2 Re": 12, "1 Cronache": 13, "2 Cronache": 14, "Esdra": 15,
  "Neemia": 16, "Ester": 17, "Giobbe": 18, "Salmi": 19, "Proverbi": 20,
  "Ecclesiaste": 21, "Cantico dei Cantici": 22, "Isaia": 23, "Geremia": 24,
  "Lamentazioni": 25, "Ezechiele": 26, "Daniele": 27, "Osea": 28, "Gioele": 29,
  "Amos": 30, "Abdia": 31, "Giona": 32, "Michea": 33, "Naum": 34, "Abacuc": 35,
  "Sofonia": 36, "Aggeo": 37, "Zaccaria": 38, "Malachia": 39, "Matteo": 40,
  "Marco": 41, "Luca": 42, "Giovanni": 43, "Atti": 44, "Romani": 45,
  "1 Corinti": 46, "2 Corinti": 47, "Galati": 48, "Efesini": 49,
  "Filippesi": 50, "Colossesi": 51, "1 Tessalonicesi": 52, "2 Tessalonicesi": 53,
  "1 Timoteo": 54, "2 Timoteo": 55, "Tito": 56, "Filemone": 57, "Ebrei": 58,
  "Giacomo": 59, "1 Pietro": 60, "2 Pietro": 61, "1 Giovanni": 62,
  "2 Giovanni": 63, "3 Giovanni": 64, "Giuda": 65, "Rivelazione": 66
};

// -----------------------
// UTILITY
// -----------------------

function saveToStorage() {
  localStorage.setItem("stickyNotes", JSON.stringify(notes));
  localStorage.setItem("allLabels", JSON.stringify(allLabels));
}

function formatDate(ts) {
  const d = new Date(ts);
  return d.toLocaleDateString("it-IT", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric"
  });
}

function buildBibleRefString(ref) {
  if (!ref || !ref.book) return "";
  const chap = ref.chapter ? ref.chapter : "";
  const verse = ref.verse ? ref.verse : "";
  if (chap && verse) return `${ref.book} ${chap}:${verse}`;
  if (chap) return `${ref.book} ${chap}`;
  return ref.book;
}

// NOTE ORDINATE PER CREAZIONE (nuove in alto)
function getNotesSortedByCreationDesc(list) {
  const all = [...list];

  const pinned = all.filter(n => n.pinned);
  const others = all.filter(n => !n.pinned);

  const maxPinned = pinned.slice(0, 4);
  const extraPinned = pinned.slice(4);
  extraPinned.forEach(n => { n.pinned = false; });

  maxPinned.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));
  others.sort((a, b) => (b.createdAt || 0) - (a.createdAt || 0));

  return [...maxPinned, ...others];
}

// LINK JW
function buildJwBibleCodeFromRefString(refStr) {
  if (!refStr) return null;

  const m = refStr.match(/^(.+?)\s+(\d+)(?::(.+))?$/);
  if (!m) return null;

  const bookName = m[1].trim();
  const chapter = parseInt(m[2], 10);
  const versePart = (m[3] || "").trim();

  const bookNumber = BOOK_NUMBERS[bookName];
  if (!bookNumber || !chapter) return null;

  const b = String(bookNumber).padStart(2, "0");
  const c = String(chapter).padStart(3, "0");

  if (!versePart) {
    return `${b}${c}001`;
  }

  const numbers = versePart
    .split(/[^0-9]+/)
    .map(v => v.trim())
    .filter(v => v.length)
    .map(v => parseInt(v, 10))
    .filter(v => !isNaN(v));

  if (!numbers.length) return null;

  const firstVerse = Math.min(...numbers);
  const lastVerse = Math.max(...numbers);

  const vStart = String(firstVerse).padStart(3, "0");
  const vEnd = String(lastVerse).padStart(3, "0");

  if (firstVerse === lastVerse) {
    return `${b}${c}${vStart}`;
  }

  return `${b}${c}${vStart}-${b}${c}${vEnd}`;
}

function buildJwLibraryLinkFromRef(refStr) {
  const bibleCode = buildJwBibleCodeFromRefString(refStr);
  if (!bibleCode) {
    const encoded = encodeURIComponent(refStr.trim());
    return `jwlibrary:/finder?srcid=jwlshare&wtlocale=I&prefer=lang&pub=nwtsty&q=${encoded}`;
  }
  return `jwlibrary:/finder?srcid=jwlshare&wtlocale=I&prefer=lang&pub=nwtsty&bible=${bibleCode}`;
}

function buildJwOrgLinkFromRef(refStr) {
  const bibleCode = buildJwBibleCodeFromRefString(refStr);
  const encoded = encodeURIComponent(refStr.trim());
  if (!bibleCode) {
    return `https://www.jw.org/finder?srcid=jwlshare&wtlocale=I&prefer=lang&q=${encoded}&pub=nwtsty`;
  }
  return `https://www.jw.org/finder?srcid=jwlshare&wtlocale=I&prefer=lang&pub=nwtsty&bible=${bibleCode}`;
}

function buildJwLinkFromRef(refStr) {
  if (openMode === "jworg") return buildJwOrgLinkFromRef(refStr);
  return buildJwLibraryLinkFromRef(refStr);
}

function getAllTags() {
  const set = new Set(allLabels || []);
  notes.forEach(n => {
    if (n.tags) {
      n.tags.forEach(t => {
        if (t && t.trim()) set.add(t.trim());
      });
    }
  });
  return Array.from(set).sort((a, b) => a.localeCompare(b, "it"));
}

// -----------------------
// TEMA: CSS + PALLINI
// -----------------------

function applyTheme() {
  document.documentElement.setAttribute("data-theme", theme);
}

function updateThemeDots() {
  if (!themeDots.length) return;
  themeDots.forEach(dot => {
    const value = dot.dataset.theme;
    dot.classList.toggle("is-selected", value === theme);
  });
}

function setTheme(newTheme) {
  theme = newTheme;
  localStorage.setItem("theme", theme);
  applyTheme();
  updateThemeDots();
}

if (themeDots.length) {
  themeDots.forEach(dot => {
    dot.addEventListener("click", () => {
      const value = dot.dataset.theme;
      if (!value) return;
      setTheme(value);
    });
  });
}

// -----------------------
// LINGUA: bottoni
// -----------------------

function updateLanguageButtons() {
  if (!languageButtons.length) return;
  languageButtons.forEach(btn => {
    const value = btn.dataset.lang;
    btn.classList.toggle("is-selected", value === language);
  });
}

if (languageButtons.length) {
  languageButtons.forEach(btn => {
    btn.addEventListener("click", () => {
      const value = btn.dataset.lang;
      if (!value) return;
      setLanguage(value); // definita in i18n.js
      updateLanguageButtons();

      if (!activeId) {
        editorTitle.textContent = t("editor.newTitle");
      } else {
        editorTitle.textContent = t("editor.editTitle");
      }
    });
  });
}

// -----------------------
// VISTE
// -----------------------

function showHomeView() {
  homeView.classList.add("active");
  editorView.classList.remove("active");
  labelsView.classList.remove("active");
  settingsView.classList.remove("active");
  if (footerNav) footerNav.style.display = "flex";
}

function showEditorView() {
  homeView.classList.remove("active");
  labelsView.classList.remove("active");
  settingsView.classList.remove("active");
  editorView.classList.add("active");
  if (footerNav) footerNav.style.display = "none";
}

function showLabelsView() {
  homeView.classList.remove("active");
  editorView.classList.remove("active");
  settingsView.classList.remove("active");
  labelsView.classList.add("active");
  if (footerNav) footerNav.style.display = "flex";
  renderLabelsView();
}

function showSettingsView() {
  homeView.classList.remove("active");
  editorView.classList.remove("active");
  labelsView.classList.remove("active");
  settingsView.classList.add("active");
  if (footerNav) footerNav.style.display = "flex";
}

// -----------------------
// NOTE: filtro e render
// -----------------------

function filteredNotes() {
  const base = getNotesSortedByCreationDesc(notes);

  const q = searchInput.value.trim().toLowerCase();
  if (!q) return base;

  return base.filter(n => {
    const title = (n.title || "").toLowerCase();
    const body = (n.bodyText || "").toLowerCase();
    const tags = (n.tags || []).join(" ").toLowerCase();
    const bibleStr = buildBibleRefString(n.bibleRef || {}).toLowerCase();
    return (
      title.includes(q) ||
      body.includes(q) ||
      tags.includes(q) ||
      bibleStr.includes(q)
    );
  });
}

function renderNotes() {
  const list = filteredNotes();
  if (!list.length) {
    notesGrid.innerHTML = `<div class="empty-state">${t("empty.notes")}</div>`;
    return;
  }

  notesGrid.innerHTML = "";
  list.forEach(note => {
    const div = document.createElement("div");
    div.className = "note-card";
    div.dataset.id = note.id;

    const rawHtml = note.bodyHtml || "";
    const tagsStr = note.tags && note.tags.length ? note.tags.map(t => `#${t}`).join(" ") : "";
    const withRefsAsLinks = rawHtml;

    const isPinned = !!note.pinned;

			div.innerHTML = `
			  ${isPinned ? '<div class="note-pin"></div>' : ''}
			  <div class="note-text">
				${note.title && note.title.trim() ? `<strong>${note.title}</strong><br>` : ""}
				${withRefsAsLinks}
			  </div>
			  <div class="note-meta">
				<span>${formatDate(note.updatedAt)}</span>
			  </div>
			  <div class="note-meta">
				<span>${tagsStr || ""}</span>
			  </div>
			`;

			div.addEventListener("click", () => {
			  openEditor(note.id);
			});

			notesGrid.appendChild(div);
			});
}

// -----------------------
// TAG BADGE + SUGGERIMENTI
// -----------------------

function updateTagsBadge() {
  const raw = noteTagsInput.value.trim();
  if (!raw) {
    editorTagsBadge.style.display = "none";
    editorTagsText.textContent = "";
    return;
  }

  const tags = raw.split(TAG_SEPARATOR).map(t => t.trim()).filter(t => t.length);
  if (!tags.length) {
    editorTagsBadge.style.display = "none";
    editorTagsText.textContent = "";
    return;
  }

  editorTagsBadge.style.display = "flex";
  editorTagsText.innerHTML = tags.map(t => {
    const esc = t.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
    return `
      <span class="label-chip" data-tag="${esc}">
        <span class="tag-pill-text">${esc}</span>
        <span class="tag-pill-remove" data-remove="${esc}">×</span>
      </span>
    `;
  }).join("");
}

if (editorTagsBadge) {
  editorTagsBadge.addEventListener("click", (e) => {
    const btn = e.target.closest(".tag-pill-remove");
    if (!btn) return;
    const tagToRemove = btn.getAttribute("data-remove");
    if (!tagToRemove) return;
    let tags = noteTagsInput.value
      .split(TAG_SEPARATOR)
      .map(t => t.trim())
      .filter(t => t.length && t !== tagToRemove);
    noteTagsInput.value = tags.join(TAG_SEPARATOR);
    updateTagsBadge();
  });
}

function renderLabelsView() {
  if (!labelsListEl || !labelsEmptyEl) return;
  let tags = getAllTags();

  if (labelSearchInput) {
    const q = labelSearchInput.value.trim().toLowerCase();
    if (q) {
      tags = tags.filter(t => t.toLowerCase().includes(q));
    }
  }

  labelsListEl.innerHTML = "";
  if (!tags.length) {
    labelsEmptyEl.style.display = "block";
    labelsEmptyEl.textContent = t("labels.empty");
    return;
  }
  labelsEmptyEl.style.display = "none";

  tags.forEach(tag => {
    const row = document.createElement("div");
    row.className = "label-row";
    row.innerHTML = `
      <div class="label-row-left">
        <span class="label-chip">${tag}</span>
      </div>
      <div class="label-row-actions">
        <button data-action="filter" data-tag="${tag}">${t("labels.action.search")}</button>
        <button data-action="rename" data-tag="${tag}">${t("labels.action.rename")}</button>
        <button data-action="delete" data-tag="${tag}">${t("labels.action.delete")}</button>
      </div>
    `;

    row.addEventListener("click", (e) => {
      const btn = e.target.closest("button");
      if (!btn) return;
      const action = btn.dataset.action;
      const targetTag = btn.dataset.tag;
      if (!action || !targetTag) return;

      if (action === "filter") {
        searchInput.value = targetTag;
        footerItems.forEach(b => b.classList.remove("is-active"));
        footerItems.forEach(b => {
          if (b.dataset.view === "home") b.classList.add("is-active");
        });
        currentView = "home";
        renderNotes();
        showHomeView();
      } else if (action === "rename") {
        const msg = t("prompt.renameLabel").replace("{name}", targetTag);
        const newName = prompt(msg, targetTag);
        if (!newName || !newName.trim) return;
        const trimmed = newName.trim();
        if (!trimmed || trimmed === targetTag) return;

        notes.forEach(n => {
          if (n.tags && n.tags.length) {
            n.tags = n.tags.map(t => (t === targetTag ? trimmed : t));
          }
        });
        allLabels = allLabels.map(t => (t === targetTag ? trimmed : t));
        saveToStorage();
        renderNotes();
        renderLabelsView();
      } else if (action === "delete") {
        const msg = t("confirm.deleteLabel").replace("{name}", targetTag);
        const ok = confirm(msg);
        if (!ok) return;

        notes.forEach(n => {
          if (n.tags && n.tags.length) {
            n.tags = n.tags.filter(t => t !== targetTag);
          }
        });

        allLabels = allLabels.filter(t => t !== targetTag);

        saveToStorage();
        renderNotes();
        renderLabelsView();
      }
    });

    labelsListEl.appendChild(row);
  });
}

function showTagSuggestions() {
  if (!tagSuggestionsEl || !noteTagsVisibleInput) return;
  const allTags = getAllTags();
  if (!allTags.length) {
    tagSuggestionsEl.style.display = "none";
    return;
  }

  const currentRaw = noteTagsVisibleInput.value.trim();
  const existingTags = noteTagsInput.value
    ? noteTagsInput.value.split(TAG_SEPARATOR).map(t => t.trim()).filter(t => t.length)
    : [];

  const filtered = allTags.filter(
    t => !existingTags.includes(t) &&
      (!currentRaw || t.toLowerCase().startsWith(currentRaw.toLowerCase()))
  );

  if (!filtered.length) {
    tagSuggestionsEl.style.display = "none";
    return;
  }

  tagSuggestionsEl.innerHTML = filtered
    .map(t => `<div class="tag-suggestions-item" data-tag="${t}">${t}</div>`)
    .join("");
  tagSuggestionsEl.style.display = "block";
}

function hideTagSuggestions() {
  if (!tagSuggestionsEl) return;
  tagSuggestionsEl.style.display = "none";
}

if (tagSuggestionsEl) {
  tagSuggestionsEl.addEventListener("click", (e) => {
    const item = e.target.closest(".tag-suggestions-item");
    if (!item || !noteTagsVisibleInput) return;
    const chosen = item.dataset.tag;
    if (!chosen) return;

    let tags = noteTagsInput.value
      ? noteTagsInput.value.split(TAG_SEPARATOR).map(t => t.trim()).filter(t => t.length)
      : [];

    if (!tags.includes(chosen)) {
      tags.push(chosen);
    }

    noteTagsInput.value = tags.join(TAG_SEPARATOR);
    updateTagsBadge();

    noteTagsVisibleInput.value = "";
    hideTagSuggestions();
    noteTagsVisibleInput.style.display = "none";
  });
}

// -----------------------
// EDITOR: reset / open / save / delete
// -----------------------

function resetEditor() {
  activeId = null;
  editorTitle.textContent = t("editor.newTitle");

  noteTitleInput.value = "";
  noteBodyInput.innerHTML = "";

  noteTagsInput.value = "";
  if (typeof noteTagsVisibleInput !== "undefined" && noteTagsVisibleInput) {
    noteTagsVisibleInput.value = "";
    noteTagsVisibleInput.style.display = "none";
  }
  updateTagsBadge();

  selectedBookFull = null;
  selectedBookAbbr = null;
  selectedChapter = null;
  bibleChapterDisplay.value = "";
  bibleVerseInput.value = "";
  bibleRefRow.style.display = "none";

  if (deleteNoteBtn) deleteNoteBtn.style.display = "none";
}

function openEditor(id) {
  const note = notes.find(n => n.id === id);
  if (!note) return;

  activeId = id;
  editorTitle.textContent = t("editor.editTitle");

  noteTitleInput.value = note.title || "";
  noteBodyInput.innerHTML = note.bodyHtml || "";

  noteTagsInput.value = (note.tags || []).join(TAG_SEPARATOR);
  if (typeof noteTagsVisibleInput !== "undefined" && noteTagsVisibleInput) {
    noteTagsVisibleInput.value = "";
    noteTagsVisibleInput.style.display = "none";
  }
  updateTagsBadge();

  const ref = note.bibleRef || null;
  if (ref && ref.book) {
    selectedBookFull = ref.book;
    selectedBookAbbr = ref.book;
    selectedChapter = ref.chapter || null;
    bibleChapterDisplay.value = selectedChapter || "";
    bibleVerseInput.value = ref.verse || "";
    bibleRefRow.style.display = "none";
  } else {
    selectedBookFull = null;
    selectedBookAbbr = null;
    selectedChapter = null;
    bibleChapterDisplay.value = "";
    bibleVerseInput.value = "";
    bibleRefRow.style.display = "none";
  }

  if (deleteNoteBtn) deleteNoteBtn.style.display = "inline-flex";

  currentView = "editor";
  if (scrollTopBtn) scrollTopBtn.classList.remove("show");
  
  showEditorView();
  noteBodyInput.focus();
}

function deleteNote(id) {
  if (!confirm(t("confirm.deleteNote"))) return;
  notes = notes.filter(n => n.id !== id);
  if (activeId === id) resetEditor();
  saveToStorage();
  renderNotes();
  if (currentView === "labels") {
    showLabelsView();
  } else if (currentView === "settings") {
    showSettingsView();
  } else {
    showHomeView();
  }
}

function handleNoteContextAction(action, noteId) {
  const note = notes.find(n => n.id === noteId);
  if (!note) return;

  if (action === "copy") {
    copyNoteContent(note);
  } else if (action === "pin") {
    togglePinNote(note);
  } else if (action === "edit") {
    openEditor(noteId);
  } else if (action === "delete") {
    deleteNote(noteId);
  }
}

function copyNoteContent(note) {
  const text = (note.title ? note.title + "\n\n" : "") + (note.bodyText || "");
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).catch(() => {});
  }
}

function togglePinNote(note) {
  if (note.pinned) {
    note.pinned = false;
  } else {
    const pinnedCount = notes.filter(n => n.pinned).length;
    if (pinnedCount >= 4) {
      alert("Puoi fissare al massimo 4 note.");
      return;
    }
    note.pinned = true;
  }
  saveToStorage();
  renderNotes();
}

function saveNote() {
  const title = noteTitleInput.value.trim();
  const bodyHtml = noteBodyInput.innerHTML.trim();
  const bodyText = noteBodyInput.innerText.trim();

  const tags = noteTagsInput.value
    .split(TAG_SEPARATOR)
    .map(t => t.trim())
    .filter(t => t.length);

  const book = selectedBookFull ? selectedBookFull.trim() : "";
  const chapterVal = selectedChapter ? String(selectedChapter) : "";
  const verseVal = bibleVerseInput.value.trim();

  const bibleRef = (book || chapterVal || verseVal)
    ? {
        book,
        chapter: chapterVal ? parseInt(chapterVal, 10) : null,
        verse: verseVal || null
      }
    : null;

  if (!bodyText && !title && !tags.length && !bibleRef) {
    alert(t("alert.emptyNote"));
    return;
  }

  const now = Date.now();
  if (activeId) {
    const idx = notes.findIndex(n => n.id === activeId);
    if (idx !== -1) {
      notes[idx].title = title;
      notes[idx].bodyHtml = bodyHtml;
      notes[idx].bodyText = bodyText;
      notes[idx].tags = tags;
      notes[idx].bibleRef = bibleRef;
      notes[idx].updatedAt = now;
    }
  } else {
    const id = `${now}-${Math.random().toString(16).slice(2)}`;
    notes.push({
      id,
      title,
      bodyHtml,
      bodyText,
      tags,
      bibleRef,
      createdAt: now,
      updatedAt: now,
	  pinned: false
    });
    activeId = id;
  }

  noteTagsInput.value = tags.join(TAG_SEPARATOR);
  if (noteTagsVisibleInput) {
    noteTagsVisibleInput.value = "";
    noteTagsVisibleInput.style.display = "none";
  }
  updateTagsBadge();
  saveToStorage();
  renderNotes();

  if (currentView === "labels") {
    showLabelsView();
  } else if (currentView === "settings") {
    showSettingsView();
  } else {
    showHomeView();
  }
}

// -----------------------
// WIZARD BIBLICO
// -----------------------

function openBookModal() {
  if (!bookModal) return;
  bookModal.style.display = "flex";
}

function closeBookModalFn() {
  if (!bookModal) return;
  bookModal.style.display = "none";
}

function openChapterModalAfterBook() {
  if (!selectedBookFull || !chapterGrid || !chapterModal) return;
  chapterGrid.innerHTML = "";
  const maxChapters = BOOK_CHAPTERS[selectedBookFull] || 50;
  for (let i = 1; i <= maxChapters; i++) {
    const btn = document.createElement("button");
    btn.className = "book-tile";
    btn.textContent = i;
    btn.dataset.chapter = i;
    btn.addEventListener("click", () => {
      selectedChapter = i;
      closeChapterModalFn();
      askForVerseAndInsert();
    });
    chapterGrid.appendChild(btn);
  }
  chapterModal.style.display = "flex";
}

function closeChapterModalFn() {
  if (!chapterModal) return;
  chapterModal.style.display = "none";
}

function startBibleRefWizard() {
  selectedBookFull = null;
  selectedBookAbbr = null;
  selectedChapter = null;
  openBookModal();
}

function askForVerseAndInsert() {
  const book = selectedBookFull ? selectedBookFull.trim() : "";
  const chapterVal = selectedChapter ? String(selectedChapter) : "";
  if (!book || !chapterVal) {
    alert(t("alert.missingBookOrChapter"));
    return;
  }
  const verseVal = prompt(t("prompt.verse"), "");
  if (verseVal === null) return;
  const verse = verseVal.trim();
  const refObj = {
    book,
    chapter: parseInt(chapterVal, 10),
    verse
  };
  const refStr = buildBibleRefString(refObj);
  if (!refStr) return;

  const url = buildJwLinkFromRef(refStr);
  const html = `<a href="${url}" target="_blank" rel="noopener noreferrer">${refStr}</a> `;

  noteBodyInput.focus();
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) {
    noteBodyInput.insertAdjacentHTML("beforeend", html);
    return;
  }
  let range = sel.getRangeAt(0);
  if (!noteBodyInput.contains(range.commonAncestorContainer)) {
    sel.removeAllRanges();
    range = document.createRange();
    range.selectNodeContents(noteBodyInput);
    range.collapse(false);
    sel.addRange(range);
  }

  range.deleteContents();
  const temp = document.createElement("span");
  temp.innerHTML = html;
  const frag = document.createDocumentFragment();
  let node, lastNode;
  while ((node = temp.firstChild)) {
    lastNode = frag.appendChild(node);
  }
  range.insertNode(frag);
  if (lastNode) {
    range.setStartAfter(lastNode);
    range.collapse(true);
    sel.removeAllRanges();
    sel.addRange(range);
  }

  bibleRefRow.style.display = "none";
  bibleChapterDisplay.value = "";
  bibleVerseInput.value = "";
  setTimeout(() => {
    bibleVerseInput.focus();
  }, 50);
}

// -----------------------
// EVIDENZIAZIONE
// -----------------------

function applyHighlight(color) {
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) return;
  let range = sel.getRangeAt(0);
  if (!noteBodyInput.contains(range.commonAncestorContainer)) return;
  noteBodyInput.focus();

  if (color === "transparent") {
    document.execCommand("removeFormat", false, null);
    return;
  }
  try {
    document.execCommand("styleWithCSS", false, true);
  } catch (e) {
  }
  document.execCommand("backColor", false, color);
}

function showHighlightToolbarAtSelection() {
  if (!highlightToolbar) return;
  const sel = window.getSelection();
  if (!sel || sel.rangeCount === 0) {
    hideHighlightToolbar();
    return;
  }
  const range = sel.getRangeAt(0);
  if (!noteBodyInput.contains(range.commonAncestorContainer)) {
    hideHighlightToolbar();
    return;
  }
  const text = sel.toString();
  if (!text.trim()) {
    hideHighlightToolbar();
    return;
  }
  const rect = range.getBoundingClientRect();
  if (!rect) {
    hideHighlightToolbar();
    return;
  }
  const editorRect = noteBodyInput.getBoundingClientRect();
  const top = rect.top - editorRect.top - 36;
  const left = rect.left - editorRect.left;
  highlightToolbar.style.top = `${Math.max(top, 0)}px`;
  highlightToolbar.style.left = `${Math.max(left, 0)}px`;
  highlightToolbar.style.opacity = "1";
  highlightToolbar.style.pointerEvents = "auto";
}

function hideHighlightToolbar() {
  if (!highlightToolbar) return;
  highlightToolbar.style.opacity = "0";
  highlightToolbar.style.pointerEvents = "none";
}

// -----------------------
// EVENTI BASE UI
// -----------------------


if (addBibleRefBtn) {
  addBibleRefBtn.addEventListener("click", () => {
    startBibleRefWizard();
  });
}

if (labelSearchInput) {
  labelSearchInput.addEventListener("input", () => {
    renderLabelsView();
  });
}

if (newNoteBtn) {
  newNoteBtn.addEventListener("click", () => {
    currentView = "editor";   // ← non più "home"
    resetEditor();
    showEditorView();
    noteBodyInput.focus();
    if (scrollTopBtn) scrollTopBtn.classList.remove("show");
  });
}

if (backToHomeBtn) {
  backToHomeBtn.addEventListener("click", () => {
    saveNote();
    currentView = "home";
    if (scrollTopBtn) scrollTopBtn.classList.remove("show");

    showHomeView();
  });
}

if (saveNoteBtn) {
  saveNoteBtn.addEventListener("click", saveNote);
}

if (cancelEditBtn) {
  cancelEditBtn.addEventListener("click", () => {
    const ok = confirm(t("confirm.cancelEdit"));
    if (!ok) return;

    if (currentView === "labels") {
      showLabelsView();
    } else if (currentView === "settings") {
      showSettingsView();
    } else {
      showHomeView();
    }
  });
}

if (deleteNoteBtn) {
  deleteNoteBtn.addEventListener("click", () => {
    if (!activeId) return;
    deleteNote(activeId);
  });
}

if (searchInput) {
  searchInput.addEventListener("input", renderNotes);
}
if (notesGrid) {
  notesGrid.addEventListener("touchstart", onNoteTouchStart, { passive: true });
  notesGrid.addEventListener("touchend", onNoteTouchEnd);
  notesGrid.addEventListener("touchmove", onNoteTouchEnd);
  notesGrid.addEventListener("contextmenu", e => e.preventDefault());
}

function onNoteTouchStart(e) {
  const card = e.target.closest(".note-card");
  if (!card) return;

  const noteId = card.dataset.id;
  const touch = e.touches[0];
  const x = touch.clientX;
  const y = touch.clientY;

  longPressTimer = setTimeout(() => {
    const note = notes.find(n => n.id === noteId);
    contextNoteId = noteId;
    updateNoteContextMenuLabels(note);
    showNoteContextMenu(x, y);
  }, 500);
}

function onNoteTouchEnd() {
  if (longPressTimer) {
    clearTimeout(longPressTimer);
    longPressTimer = null;
  }
}

function updatePinButtonLabel(noteId) {
  if (!noteContextMenu) return;
  const note = notes.find(n => n.id === noteId);
  if (!note) return;

  const pinBtn = noteContextMenu.querySelector('button[data-action="pin"]');
  if (!pinBtn) return;

  pinBtn.textContent = note.pinned ? "Sgancia" : "Fissa";
}

function showNoteContextMenu(x, y) {
  if (!noteContextMenu) return;
  noteContextMenu.style.left = x + "px";
  noteContextMenu.style.top = y + "px";
  noteContextMenu.style.display = "block";
}

function hideNoteContextMenu() {
  if (!noteContextMenu) return;
  noteContextMenu.style.display = "none";
  contextNoteId = null;
}

function updateNoteContextMenuLabels(note) {
  if (!noteContextMenu) return;

  const copyBtn = noteContextMenu.querySelector('button[data-action="copy"]');
  const pinBtn = noteContextMenu.querySelector('button[data-action="pin"]');
  const editBtn = noteContextMenu.querySelector('button[data-action="edit"]');
  const deleteBtn = noteContextMenu.querySelector('button[data-action="delete"]');

  if (copyBtn) copyBtn.textContent = t("context.copy");
  if (editBtn) editBtn.textContent = t("context.edit");
  if (deleteBtn) deleteBtn.textContent = t("context.delete");
  if (pinBtn) {
    pinBtn.textContent = note && note.pinned ? t("context.unpin") : t("context.pin");
  }
}

if (noteContextMenu) {
  noteContextMenu.addEventListener("click", (e) => {
    const btn = e.target.closest("button");
    if (!btn || !contextNoteId) return;

    const action = btn.dataset.action;
    handleNoteContextAction(action, contextNoteId);
    hideNoteContextMenu();
  });
}

document.addEventListener("click", (e) => {
  if (!noteContextMenu) return;
  if (!noteContextMenu.contains(e.target)) {
    hideNoteContextMenu();
  }
});

// Mostra / nasconde il bottone torna-su
window.addEventListener("scroll", () => {
  if (!scrollTopBtn) return;
  if (currentView !== "home") {
    scrollTopBtn.classList.remove("show");
    return;
  }

  const y = window.scrollY || document.documentElement.scrollTop || 0;
  if (y > 150) {
    scrollTopBtn.classList.add("show");
  } else {
    scrollTopBtn.classList.remove("show");
  }
}, { passive: true });

// Click sul bottone: torna all'inizio
if (scrollTopBtn) {
  scrollTopBtn.addEventListener("click", () => {
    window.scrollTo({
      top: 0,
      behavior: "smooth"
    });
  });
}

window.addEventListener("scroll", () => {
  hideNoteContextMenu();
}, { passive: true });

if (noteTagsVisibleInput) {
  noteTagsVisibleInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      const newTag = noteTagsVisibleInput.value.trim();
      if (!newTag) {
        hideTagSuggestions();
        noteTagsVisibleInput.blur();
        noteTagsVisibleInput.style.display = "none";
        return;
      }

      let tags = noteTagsInput.value
        ? noteTagsInput.value.split(TAG_SEPARATOR).map(t => t.trim()).filter(t => t.length)
        : [];

      if (!tags.includes(newTag)) {
        tags.push(newTag);
      }

      noteTagsInput.value = tags.join(TAG_SEPARATOR);
      updateTagsBadge();

      noteTagsVisibleInput.value = "";
      hideTagSuggestions();
      noteTagsVisibleInput.blur();
      noteTagsVisibleInput.style.display = "none";
    }
  });

  noteTagsVisibleInput.addEventListener("input", () => {
    showTagSuggestions();
  });

  noteTagsVisibleInput.addEventListener("blur", () => {
    setTimeout(() => {
      hideTagSuggestions();
      noteTagsVisibleInput.style.display = "none";
      noteTagsVisibleInput.value = "";
    }, 150);
  });
}

document.addEventListener("click", (e) => {
  if (!tagSuggestionsEl) return;
  if (tagSuggestionsEl.contains(e.target)) return;
  if (e.target === addTagBtn) return;
  if (noteTagsVisibleInput && noteTagsVisibleInput.contains(e.target)) return;
  hideTagSuggestions();
  if (noteTagsVisibleInput && document.activeElement !== noteTagsVisibleInput) {
    noteTagsVisibleInput.style.display = "none";
  }
});

if (addTagBtn) {
  addTagBtn.addEventListener("click", () => {
    if (!noteTagsVisibleInput) return;
    noteTagsVisibleInput.style.display = "block";
    noteTagsVisibleInput.value = "";
    noteTagsVisibleInput.focus();
    showTagSuggestions();
  });
}

// CLICK LINK DENTRO EDITOR
if (noteBodyInput) {
  noteBodyInput.addEventListener("click", (e) => {
    const a = e.target.closest("a");
    if (!a) return;
    e.preventDefault();
    e.stopPropagation();
    const text = a.textContent || "";
    const refMatch = text.match(/^(.+?)\s+(\d+)(?::(\d+))?$/);
    const refStr = refMatch ? refMatch[0].trim() : null;
    if (!refStr) {
      const href = a.getAttribute("href");
      if (href) window.open(href, "_blank", "noopener,noreferrer");
      return;
    }
    const url = buildJwLinkFromRef(refStr);
    window.open(url, "_blank", "noopener,noreferrer");
  });
}

// MODAL LIBRI
bookTiles.forEach(btn => {
  btn.addEventListener("click", () => {
    const full = btn.dataset.book;
    const abbr = btn.dataset.abbr;
    selectedBookFull = full;
    selectedBookAbbr = abbr;
    selectedChapter = null;
    closeBookModalFn();
    openChapterModalAfterBook();
  });
});

if (bookModal) {
  bookModal.addEventListener("click", (e) => {
    if (e.target === bookModal) closeBookModalFn();
  });
}
if (closeBookModal) {
  closeBookModal.addEventListener("click", closeBookModalFn);
}

// MODAL CAPITOLI
if (bibleChapterDisplay) {
  bibleChapterDisplay.addEventListener("click", () => {
    if (!selectedBookFull) {
      alert(t("alert.selectBookFirst"));
      return;
    }
    openChapterModalAfterBook();
  });
}
if (closeChapterModal) {
  closeChapterModal.addEventListener("click", closeChapterModalFn);
}
if (chapterModal) {
  chapterModal.addEventListener("click", (e) => {
    if (e.target === chapterModal) closeChapterModalFn();
  });
}

// TOOLBAR EVIDENZIAZIONE
highlightButtons.forEach(btn => {
  btn.addEventListener("click", () => {
    const color = btn.dataset.color;
    applyHighlight(color);
  });
});

document.addEventListener("selectionchange", showHighlightToolbarAtSelection);
document.addEventListener("mousedown", (e) => {
  if (!noteBodyInput.contains(e.target) && !highlightToolbar.contains(e.target)) {
    hideHighlightToolbar();
  }
});

// EXPORT / IMPORT NOTE
if (exportNotesBtn) {
  exportNotesBtn.addEventListener("click", () => {
    exportNotesToFile();
  });
}

if (importNotesBtn && importFileInput) {
  importNotesBtn.addEventListener("click", () => {
    importFileInput.click();
  });

  importFileInput.addEventListener("change", () => {
    const file = importFileInput.files && importFileInput.files[0];
    if (!file) return;
    importNotesFromFile(file);
    importFileInput.value = "";
  });
}

// FOOTER NAV
if (footerItems.length) {
  footerItems.forEach(btn => {
    btn.addEventListener("click", () => {
      const view = btn.dataset.view;
      if (!view) return;
      footerItems.forEach(b => b.classList.remove("is-active"));
      btn.classList.add("is-active");

      if (scrollTopBtn) scrollTopBtn.classList.remove("show");

      if (view === "home") {
        currentView = "home";
        showHomeView();
      } else if (view === "labels") {
        currentView = "labels";
        showLabelsView();
      } else if (view === "settings") {
        currentView = "settings";
        showSettingsView();
      }
    });
  });
}

// TOGGLE OPEN MODE
function updateOpenModeSwitch() {
  if (!toggleOpenModeSwitch) return;
  toggleOpenModeSwitch.checked = openMode === "jwlibrary";
}

if (toggleOpenModeSwitch) {
  toggleOpenModeSwitch.addEventListener("change", () => {
    openMode = toggleOpenModeSwitch.checked ? "jwlibrary" : "jworg";
    localStorage.setItem("openMode", openMode);
  });
}

//CLICK PULSANTE AGGIUNGI tags
const newLabelBtn = document.getElementById("newLabelBtn");

if (newLabelBtn) {
  newLabelBtn.addEventListener("click", () => {
    const name = prompt(t("prompt.newLabel"), "");
    if (!name) return;
    const trimmed = name.trim();
    if (!trimmed) return;

    if (!allLabels.includes(trimmed)) {
      allLabels.push(trimmed);
      allLabels.sort((a, b) => a.localeCompare(b, "it"));
      saveToStorage();
    }
    renderLabelsView();
  });
}

// -----------------------
// EXPORT / IMPORT FUNZIONI
// -----------------------

async function exportNotesToFile() {
  if (!notes || !Array.isArray(notes) || !notes.length) {
    alert(t("empty.notes"));
    return;
  }

  const payload = {
    version: 1,
    exportedAt: Date.now(),
    notes,
    allLabels: allLabels || []
  };

  const json = JSON.stringify(payload, null, 2);
  const dateStr = new Date().toISOString().slice(0, 10);
  const fileName = `bible-notes-backup-${dateStr}.json`;

  console.log("Export avviato, file:", fileName);

  const hasCapacitor = !!window.Capacitor;
  const hasPlugins = hasCapacitor && !!window.Capacitor.Plugins;
  const hasFs = hasPlugins && !!window.Capacitor.Plugins.Filesystem;
  const hasShare = hasPlugins && !!window.Capacitor.Plugins.Share;

  console.log("Capacitor:", hasCapacitor, "Filesystem:", hasFs, "Share:", hasShare);

  // Ambiente Capacitor con Filesystem + Share
  if (hasFs && hasShare) {
    const Filesystem = window.Capacitor.Plugins.Filesystem;
    const Share = window.Capacitor.Plugins.Share;

    try {
      const Directory = Filesystem.Directory || Filesystem.constants?.Directory;
      const directory = Directory ? Directory.Documents || 'Documents' : 'Documents';

      console.log("Scrittura file in directory:", directory);

      const writeResult = await Filesystem.writeFile({
        path: fileName,
        data: json,
        directory,
        encoding: 'utf8'
      });

      console.log("writeResult:", writeResult);

      const uri = writeResult.uri || writeResult.path || fileName;

      await Share.share({
        title: 'Bible Notes backup',
        text: 'File di backup delle note',
        url: uri,
        dialogTitle: 'Esporta backup'
      });

    } catch (err) {
      console.error('Errore export (Filesystem + Share):', err);
      alert('Errore durante il salvataggio o la condivisione del backup.');
    }

    return;
  }

  // Fallback web (browser)
  console.log("Export in modalità web (download)");
  const blob = new Blob([json], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = fileName;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
}

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

      notes = data.notes;
      allLabels = Array.isArray(data.allLabels) ? data.allLabels : [];

      saveToStorage();
      renderNotes();
      renderLabelsView();

      alert("Note importate correttamente.");
    } catch (err) {
      console.error("Errore import backup:", err);
      alert("Errore durante l'importazione del file.");
    }
  };
  reader.readAsText(file);
}

// -----------------------
// INIT UI
// -----------------------

applyTheme();
updateThemeDots();
updateOpenModeSwitch();
updateLanguageButtons();
applyLanguage();

if (!activeId) {
  editorTitle.textContent = t("editor.newTitle");
} else {
  editorTitle.textContent = t("editor.editTitle");
}

renderNotes();
showHomeView();

// -----------------------
// HARDWARE BACK BUTTON (Capacitor @capacitor/app)
// -----------------------

if (window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.App) {
  const CapacitorApp = window.Capacitor.Plugins.App;

  CapacitorApp.addListener("backButton", ({ canGoBack }) => {
		if (editorView.classList.contains("active")) {
	  saveNote();

	  if (currentView === "labels") {
		showLabelsView();
	  } else if (currentView === "settings") {
		showSettingsView();
	  } else {
		currentView = "home";
		if (scrollTopBtn) scrollTopBtn.classList.remove("show");
		showHomeView();
	  }
	  return;
	}

    if (labelsView.classList.contains("active") || settingsView.classList.contains("active")) {
      currentView = "home";
	  if (scrollTopBtn) scrollTopBtn.classList.remove("show");
      showHomeView();
      return;
    }

    CapacitorApp.exitApp();
  });
}