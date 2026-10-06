/* ==========================================================================
   Payflow API — MkDocs Material Interactive Enhancements
   - SVG href setter polyfill (resolves instant navigation TypeError)
   - Diagram hover zoom/pan controls, fullscreen modal viewer
   - Dynamic coverage link path synchronization
   ========================================================================== */

(function () {
  "use strict";

  /* --------------------------------------------------------------------------
     1. Fix Material for MkDocs Instant Navigation with SVG href/src
     -------------------------------------------------------------------------- */
  (function fixSvgHrefSetter() {
    function patchSvgHref(proto) {
      if (!proto) return;
      try {
        var desc = Object.getOwnPropertyDescriptor(proto, "href");
        if (desc && desc.get && !desc.set && desc.configurable) {
          Object.defineProperty(proto, "href", {
            get: desc.get,
            set: function (val) {
              if (typeof val === "string") {
                this.setAttribute("href", val);
              } else if (val && typeof val.baseVal === "string") {
                this.setAttribute("href", val.baseVal);
              }
            },
            configurable: true,
            enumerable: desc.enumerable
          });
        }
      } catch (e) {}
    }

    var targets = [
      typeof SVGElement !== "undefined" ? SVGElement.prototype : null,
      typeof SVGImageElement !== "undefined" ? SVGImageElement.prototype : null,
      typeof SVGUseElement !== "undefined" ? SVGUseElement.prototype : null,
      typeof SVGAElement !== "undefined" ? SVGAElement.prototype : null
    ];

    targets.forEach(function (t) {
      var curr = t;
      while (curr && curr !== Object.prototype) {
        patchSvgHref(curr);
        curr = Object.getPrototypeOf(curr);
      }
    });
  })();

  /* --------------------------------------------------------------------------
     2. Fullscreen Modal Viewer for Diagrams
     -------------------------------------------------------------------------- */
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

  /* --------------------------------------------------------------------------
     3. Diagram Controls & Viewport Wrapping
     -------------------------------------------------------------------------- */
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
    if (parent && parent.tagName.toLowerCase() === "p" && parent.childNodes.length === 1) {
      parent.parentNode.insertBefore(wrapper, parent);
      parent.remove();
    } else if (parent) {
      parent.insertBefore(wrapper, targetEl);
    }
    viewport.appendChild(targetEl);
    wrapper.appendChild(controls);
    wrapper.appendChild(viewport);

    let currentScale = 1.0;
    let isDragging = false;
    let startX, startY, scrollLeft, scrollTop;

    function updateScale(newScale) {
      currentScale = Math.min(Math.max(0.4, newScale), 3.5);
      if (Math.abs(currentScale - 1.0) < 0.05) {
        currentScale = 1.0;
        targetEl.style.transform = "none";
        viewport.style.overflow = "visible";
        viewport.style.cursor = "default";
      } else {
        targetEl.style.transform = `scale(${currentScale})`;
        viewport.style.overflow = "auto";
        viewport.style.cursor = "grab";
      }
    }

    controls.querySelector('[data-action="zoom-in"]').addEventListener("click", (e) => {
      e.preventDefault();
      updateScale(currentScale + 0.25);
    });

    controls.querySelector('[data-action="zoom-out"]').addEventListener("click", (e) => {
      e.preventDefault();
      updateScale(currentScale - 0.25);
    });

    controls.querySelector('[data-action="reset"]').addEventListener("click", (e) => {
      e.preventDefault();
      updateScale(1.0);
      viewport.scrollLeft = 0;
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

    // Mouse drag panning when zoomed
    viewport.addEventListener("mousedown", (e) => {
      if (currentScale <= 1.0 || e.target.closest(".diagram-controls")) return;
      isDragging = true;
      viewport.style.cursor = "grabbing";
      startX = e.pageX - viewport.offsetLeft;
      startY = e.pageY - viewport.offsetTop;
      scrollLeft = viewport.scrollLeft;
      scrollTop = viewport.scrollTop;
    });

    window.addEventListener("mouseup", () => {
      if (isDragging) {
        isDragging = false;
        viewport.style.cursor = currentScale > 1.0 ? "grab" : "default";
      }
    });

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
      if (e.ctrlKey || e.metaKey) {
        e.preventDefault();
        const delta = e.deltaY > 0 ? -0.15 : 0.15;
        updateScale(currentScale + delta);
      }
    }, { passive: false });
  }

  /* --------------------------------------------------------------------------
     4. Diagram Initialization & Scan
     -------------------------------------------------------------------------- */
  function initDiagramZoom() {
    // Strictly target architectural diagram images under diagrams/ (never emojis or badges)
    const images = document.querySelectorAll('.md-content img[src*="diagrams/"]');
    images.forEach((img) => {
      if (img.classList.contains("twemoji") || img.classList.contains("emoji") || img.closest(".twemoji")) return;
      attachDiagramControls(img);
    });

    const mermaidBlocks = document.querySelectorAll(".mermaid");
    mermaidBlocks.forEach((block) => {
      attachDiagramControls(block);
    });
  }

  /* --------------------------------------------------------------------------
     5. Dynamic Coverage Link Path Synchronization
     -------------------------------------------------------------------------- */
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

  /* --------------------------------------------------------------------------
     6. Master Setup & SPA Lifecycle Subscription
     -------------------------------------------------------------------------- */
  function setupAll() {
    initDiagramZoom();
    adjustCoverageLink();
  }

  function registerInstantObserver() {
    if (typeof document$ !== "undefined") {
      document$.subscribe(() => {
        setupAll();
      });
    } else {
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

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", () => {
      setupAll();
      registerInstantObserver();
    });
  } else {
    setupAll();
    registerInstantObserver();
  }

  window.addEventListener("popstate", () => {
    setTimeout(setupAll, 50);
  });
})();
