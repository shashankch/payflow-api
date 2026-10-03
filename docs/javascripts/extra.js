/* ==========================================================================
   Payflow API — MkDocs Material Interactive Enhancements
   Mermaid diagram rendering, dynamic theme synchronization, interactive zoom/pan,
   and fullscreen modal viewer.
   ========================================================================== */

(function () {
  "use strict";

  // Determine current theme scheme
  function getCurrentScheme() {
    const palette = typeof __md_get !== "undefined" ? __md_get("__palette") : null;
    if (palette && palette.color && palette.color.scheme === "slate") {
      return "slate";
    }
    const htmlScheme = document.documentElement.getAttribute("data-md-color-scheme");
    if (htmlScheme === "slate") return "slate";
    if (window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches) {
      return "slate";
    }
    return "default";
  }

  // Ensure modal element exists
  function ensureModal() {
    let modal = document.getElementById("mermaid-fullscreen-modal");
    if (!modal) {
      modal = document.createElement("div");
      modal.id = "mermaid-fullscreen-modal";
      modal.className = "mermaid-modal";
      modal.innerHTML = `
        <div class="mermaid-modal-header">
          <div class="mermaid-modal-controls">
            <button class="modal-zoom-btn" data-modal-action="zoom-in" title="Zoom In (+)">➕ Zoom In</button>
            <button class="modal-zoom-btn" data-modal-action="zoom-out" title="Zoom Out (-)">➖ Zoom Out</button>
            <button class="modal-zoom-btn" data-modal-action="reset" title="Reset Zoom">↺ Reset</button>
          </div>
          <button class="mermaid-modal-close" title="Close Fullscreen (Esc)">&times; Close</button>
        </div>
        <div class="mermaid-modal-viewport">
          <div class="mermaid-modal-content"></div>
        </div>
      `;
      document.body.appendChild(modal);

      let modalScale = 1.0;
      const content = modal.querySelector(".mermaid-modal-content");
      const viewport = modal.querySelector(".mermaid-modal-viewport");

      function updateModalScale(scale) {
        modalScale = Math.min(Math.max(0.4, scale), 4.0);
        content.style.transform = `scale(${modalScale})`;
      }

      modal.querySelector('[data-modal-action="zoom-in"]').addEventListener("click", (e) => {
        e.preventDefault();
        updateModalScale(modalScale + 0.25);
      });

      modal.querySelector('[data-modal-action="zoom-out"]').addEventListener("click", (e) => {
        e.preventDefault();
        updateModalScale(modalScale - 0.25);
      });

      modal.querySelector('[data-modal-action="reset"]').addEventListener("click", (e) => {
        e.preventDefault();
        updateModalScale(1.0);
        viewport.scrollLeft = (content.scrollWidth - viewport.clientWidth) / 2;
        viewport.scrollTop = (content.scrollHeight - viewport.clientHeight) / 2;
      });

      modal.querySelector(".mermaid-modal-close").addEventListener("click", () => {
        modal.classList.remove("active");
        content.innerHTML = "";
      });

      window.addEventListener("keydown", (e) => {
        if (e.key === "Escape" && modal.classList.contains("active")) {
          modal.classList.remove("active");
          content.innerHTML = "";
        }
      });
    }
    return modal;
  }

  // Render Mermaid diagrams
  async function renderMermaid() {
    if (typeof mermaid === "undefined") {
      console.warn("Mermaid library not loaded yet.");
      return;
    }

    const scheme = getCurrentScheme();
    const mermaidTheme = scheme === "slate" ? "dark" : "default";

    mermaid.initialize({
      startOnLoad: false,
      theme: mermaidTheme,
      securityLevel: "loose",
      fontFamily: "Inter, -apple-system, BlinkMacSystemFont, sans-serif"
    });

    const mermaidBlocks = document.querySelectorAll(".mermaid");
    if (!mermaidBlocks.length) return;

    for (const block of mermaidBlocks) {
      // Save raw definition if not already saved
      if (!block.getAttribute("data-processed-source")) {
        block.setAttribute("data-processed-source", block.textContent.trim());
      } else if (block.getAttribute("data-rendered-theme") === mermaidTheme) {
        continue;
      }

      const rawSource = block.getAttribute("data-processed-source");
      const id = "mermaid-svg-" + Math.random().toString(36).substring(2, 9);

      try {
        const { svg } = await mermaid.render(id, rawSource);
        block.innerHTML = svg;
        block.setAttribute("data-rendered-theme", mermaidTheme);
        attachDiagramControls(block);
      } catch (err) {
        console.error("Mermaid rendering failed for block:", err);
      }
    }
  }

  function attachDiagramControls(mermaidEl) {
    if (mermaidEl.closest(".mermaid-wrapper")) return;

    const modal = ensureModal();

    const wrapper = document.createElement("div");
    wrapper.className = "mermaid-wrapper";

    const controls = document.createElement("div");
    controls.className = "diagram-controls";
    controls.innerHTML = `
      <button class="zoom-btn" data-action="zoom-in" title="Zoom In (+)">➕</button>
      <button class="zoom-btn" data-action="zoom-out" title="Zoom Out (-)">➖</button>
      <button class="zoom-btn" data-action="reset" title="Reset (100%)">↺</button>
      <button class="zoom-btn" data-action="fullscreen" title="Open Fullscreen (HD)">⛶</button>
    `;

    const viewport = document.createElement("div");
    viewport.className = "mermaid-viewport";

    mermaidEl.parentNode.insertBefore(wrapper, mermaidEl);
    viewport.appendChild(mermaidEl);
    wrapper.appendChild(controls);
    wrapper.appendChild(viewport);

    let currentScale = 1;
    let isDragging = false;
    let startX, startY, scrollLeft, scrollTop;

    function updateScale(newScale) {
      currentScale = Math.min(Math.max(0.5, newScale), 3.0);
      mermaidEl.style.transform = `scale(${currentScale})`;
    }

    controls.querySelector('[data-action="zoom-in"]').addEventListener("click", (e) => {
      e.preventDefault();
      updateScale(currentScale + 0.2);
    });

    controls.querySelector('[data-action="zoom-out"]').addEventListener("click", (e) => {
      e.preventDefault();
      updateScale(currentScale - 0.2);
    });

    controls.querySelector('[data-action="reset"]').addEventListener("click", (e) => {
      e.preventDefault();
      updateScale(1.0);
      viewport.scrollLeft = (mermaidEl.scrollWidth - viewport.clientWidth) / 2;
      viewport.scrollTop = 0;
    });

    controls.querySelector('[data-action="fullscreen"]').addEventListener("click", (e) => {
      e.preventDefault();
      const svg = mermaidEl.querySelector("svg");
      const content = modal.querySelector(".mermaid-modal-content");
      content.innerHTML = "";
      if (svg) {
        const clone = svg.cloneNode(true);
        clone.removeAttribute("width");
        clone.removeAttribute("height");
        clone.style.width = "100%";
        clone.style.height = "100%";
        content.appendChild(clone);
      } else {
        content.innerHTML = mermaidEl.innerHTML;
      }
      content.style.transform = "scale(1)";
      modal.classList.add("active");
    });

    // Mouse drag panning
    viewport.addEventListener("mousedown", (e) => {
      if (e.target.closest(".diagram-controls")) return;
      isDragging = true;
      startX = e.pageX - viewport.offsetLeft;
      startY = e.pageY - viewport.offsetTop;
      scrollLeft = viewport.scrollLeft;
      scrollTop = viewport.scrollTop;
    });

    viewport.addEventListener("mouseleave", () => { isDragging = false; });
    viewport.addEventListener("mouseup", () => { isDragging = false; });

    viewport.addEventListener("mousemove", (e) => {
      if (!isDragging) return;
      e.preventDefault();
      const x = e.pageX - viewport.offsetLeft;
      const y = e.pageY - viewport.offsetTop;
      const walkX = (x - startX) * 1.5;
      const walkY = (y - startY) * 1.5;
      viewport.scrollLeft = scrollLeft - walkX;
      viewport.scrollTop = scrollTop - walkY;
    });

    // Wheel zoom
    viewport.addEventListener("wheel", (e) => {
      if (e.ctrlKey || e.metaKey || e.altKey) {
        e.preventDefault();
        const delta = e.deltaY > 0 ? -0.1 : 0.1;
        updateScale(currentScale + delta);
      }
    }, { passive: false });
  }

  // Setup on page load and instant navigation
  function setup() {
    renderMermaid();

    // Listen for theme palette toggle
    const toggles = document.querySelectorAll("[data-md-color-scheme]");
    const observer = new MutationObserver(() => {
      renderMermaid();
    });
    observer.observe(document.body, { attributes: true, attributeFilter: ["data-md-color-scheme"] });
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ["data-md-color-scheme"] });
  }

  if (typeof document$ !== "undefined") {
    document$.subscribe(() => {
      setTimeout(setup, 100);
    });
  } else {
    document.addEventListener("DOMContentLoaded", () => {
      setTimeout(setup, 100);
    });
  }
})();
