/* ==========================================================================
   Payflow API — MkDocs Material Interactive Enhancements
   Diagram zoom/pan controls, fullscreen modal viewer, dynamic theme sync,
   and instant navigation lifecycle hooks.
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
    let modal = document.getElementById("diagram-fullscreen-modal");
    if (!modal) {
      modal = document.createElement("div");
      modal.id = "diagram-fullscreen-modal";
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

  // Attach zoom, pan, and fullscreen toolbar to a diagram image or svg
  function attachDiagramControls(targetEl) {
    if (targetEl.closest(".diagram-wrapper") || targetEl.closest(".mermaid-wrapper")) return;

    const modal = ensureModal();

    const wrapper = document.createElement("div");
    wrapper.className = "diagram-wrapper";

    const controls = document.createElement("div");
    controls.className = "diagram-controls";
    controls.innerHTML = `
      <button class="zoom-btn" data-action="zoom-in" title="Zoom In (+)">➕</button>
      <button class="zoom-btn" data-action="zoom-out" title="Zoom Out (-)">➖</button>
      <button class="zoom-btn" data-action="reset" title="Reset (100%)">↺</button>
      <button class="zoom-btn" data-action="fullscreen" title="Open Fullscreen (HD)">⛶</button>
    `;

    const viewport = document.createElement("div");
    viewport.className = "diagram-viewport";

    const parent = targetEl.parentNode;
    parent.insertBefore(wrapper, targetEl);
    viewport.appendChild(targetEl);
    wrapper.appendChild(controls);
    wrapper.appendChild(viewport);

    let currentScale = 1.0;
    let isDragging = false;
    let startX, startY, scrollLeft, scrollTop;

    function updateScale(newScale) {
      currentScale = Math.min(Math.max(0.4, newScale), 3.5);
      targetEl.style.transform = `scale(${currentScale})`;
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
      viewport.scrollLeft = (targetEl.scrollWidth - viewport.clientWidth) / 2;
      viewport.scrollTop = 0;
    });

    controls.querySelector('[data-action="fullscreen"]').addEventListener("click", (e) => {
      e.preventDefault();
      const content = modal.querySelector(".mermaid-modal-content");
      content.innerHTML = "";

      if (targetEl.tagName.toLowerCase() === "img") {
        const fullImg = document.createElement("img");
        fullImg.src = targetEl.src;
        fullImg.alt = targetEl.alt || "Diagram Fullscreen";
        content.appendChild(fullImg);
      } else if (targetEl.querySelector("svg")) {
        const svg = targetEl.querySelector("svg");
        const clone = svg.cloneNode(true);
        clone.removeAttribute("width");
        clone.removeAttribute("height");
        clone.style.width = "100%";
        clone.style.height = "100%";
        content.appendChild(clone);
      } else {
        content.innerHTML = targetEl.innerHTML;
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

  // Scan and initialize all diagram images in page content
  function initDiagramZoom() {
    const images = document.querySelectorAll(
      '.md-content img[src*="diagrams/"], .md-content img[src$=".svg"]:not([src*="img.shields.io"]):not([src*="icons/"])'
    );
    images.forEach((img) => {
      attachDiagramControls(img);
    });

    const mermaidBlocks = document.querySelectorAll(".mermaid");
    mermaidBlocks.forEach((block) => {
      attachDiagramControls(block);
    });
  }

  // Dynamic coverage report link adjuster
  function adjustCoverageLink() {
    const link = document.getElementById("jacoco-direct-link");
    if (!link) return;
    const path = window.location.pathname;
    if (path.includes("/payflow-api/")) {
      link.href = "/payflow-api/coverage-report/";
    } else {
      link.href = "/coverage-report/";
    }
  }

  // Master setup function executing on initial load & instant navigation
  function setupAll() {
    initDiagramZoom();
    adjustCoverageLink();
  }

  // Register with Material for MkDocs instant navigation observable
  function registerInstantObserver() {
    if (typeof document$ !== "undefined") {
      document$.subscribe(() => {
        setupAll();
      });
    } else {
      // Poll briefly for document$ if MkDocs bundle.js is still initializing
      let attempts = 0;
      const interval = setInterval(() => {
        attempts++;
        if (typeof document$ !== "undefined") {
          clearInterval(interval);
          document$.subscribe(() => {
            setupAll();
          });
        } else if (attempts >= 20) {
          clearInterval(interval);
        }
      }, 50);
    }
  }

  // Initial execution
  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      setupAll();
      registerInstantObserver();
    });
  } else {
    setupAll();
    registerInstantObserver();
  }

  // Safety fallback for popstate and hash navigation
  window.addEventListener("popstate", () => {
    setTimeout(setupAll, 50);
  });
})();
