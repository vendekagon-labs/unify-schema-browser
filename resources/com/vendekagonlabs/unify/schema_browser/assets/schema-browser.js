/* Unify Schema Browser. Copyright 2026 Vendekagon Labs, LLC. Apache 2.0.
 * Header search (all pages) and the interactive kind graph (index page).
 * No dependencies; works from file:// (search index is a script global). */
(function () {
  "use strict";

  // ---------- search ----------

  var TYPE_RANK = { kind: 0, enum: 1, attribute: 2, value: 3 };
  var MAX_RESULTS = 25;

  function escapeHtml(s) {
    return String(s).replace(/[&<>"']/g, function (c) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c];
    });
  }

  function highlight(name, q) {
    var i = name.toLowerCase().indexOf(q);
    if (i < 0) return escapeHtml(name);
    return escapeHtml(name.slice(0, i)) + "<mark>" + escapeHtml(name.slice(i, i + q.length)) +
      "</mark>" + escapeHtml(name.slice(i + q.length));
  }

  // lower score = better: exact < prefix < segment prefix < substring < doc match
  function score(entry, q) {
    var n = entry.n.toLowerCase();
    var last = n.split("/").pop();
    var s;
    if (n === q || last === q) s = 0;
    else if (n.indexOf(q) === 0 || last.indexOf(q) === 0) s = 1;
    else if (new RegExp("[./-]" + q.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")).test(n)) s = 2;
    else if (n.indexOf(q) >= 0) s = 3;
    else if (entry.d && entry.d.toLowerCase().indexOf(q) >= 0) s = 5;
    else return -1;
    return s * 10 + (TYPE_RANK[entry.t] || 0);
  }

  function initSearch() {
    var input = document.getElementById("search");
    var box = document.getElementById("search-results");
    var index = window.SCHEMA_SEARCH_INDEX || [];
    if (!input || !box) return;
    var active = -1;

    function links() { return box.querySelectorAll("a"); }

    function setActive(i) {
      var ls = links();
      if (!ls.length) return;
      active = (i + ls.length) % ls.length;
      ls.forEach(function (a, j) { a.classList.toggle("active", j === active); });
      ls[active].scrollIntoView({ block: "nearest" });
    }

    function close() { box.hidden = true; active = -1; }

    function run() {
      var q = input.value.trim().toLowerCase();
      if (!q) { close(); return; }
      var hits = [];
      for (var i = 0; i < index.length; i++) {
        var s = score(index[i], q);
        if (s >= 0) hits.push([s, index[i]]);
      }
      hits.sort(function (a, b) { return a[0] - b[0] || a[1].n.length - b[1].n.length; });
      if (!hits.length) {
        box.innerHTML = '<div class="sr-empty">No matches for “' + escapeHtml(input.value.trim()) + '”</div>';
      } else {
        box.innerHTML = hits.slice(0, MAX_RESULTS).map(function (h) {
          var e = h[1];
          return '<a href="' + escapeHtml(e.u) + '" role="option"><div class="sr-top">' +
            '<span class="sr-type">' + escapeHtml(e.t) + '</span>' +
            '<span class="sr-name">' + highlight(e.n, q) + '</span></div>' +
            (e.d ? '<div class="sr-doc">' + escapeHtml(e.d) + '</div>' : "") + "</a>";
        }).join("");
      }
      box.hidden = false;
      active = -1;
    }

    input.addEventListener("input", run);
    input.addEventListener("focus", function () { if (input.value.trim()) run(); });
    input.addEventListener("keydown", function (ev) {
      if (ev.key === "ArrowDown") { ev.preventDefault(); setActive(active + 1); }
      else if (ev.key === "ArrowUp") { ev.preventDefault(); setActive(active - 1); }
      else if (ev.key === "Enter") {
        var ls = links();
        var target = ls[active >= 0 ? active : 0];
        if (target) { ev.preventDefault(); window.location.href = target.getAttribute("href"); }
      } else if (ev.key === "Escape") { close(); input.blur(); }
    });
    document.addEventListener("click", function (ev) {
      if (!box.contains(ev.target) && ev.target !== input) close();
    });
    document.addEventListener("keydown", function (ev) {
      var tag = (ev.target.tagName || "").toLowerCase();
      if (ev.key === "/" && tag !== "input" && tag !== "textarea") { ev.preventDefault(); input.focus(); }
    });
  }

  // ---------- graph ----------

  function initGraph() {
    var viewport = document.getElementById("graph-viewport");
    var svg = document.getElementById("schema-graph");
    if (!viewport || !svg) return;

    var base = svg.viewBox.baseVal;
    var full = { x: base.x, y: base.y, w: base.width, h: base.height };
    var view = { x: full.x, y: full.y, w: full.w, h: full.h };
    svg.setAttribute("preserveAspectRatio", "xMidYMid meet");

    function apply() { svg.setAttribute("viewBox", [view.x, view.y, view.w, view.h].join(" ")); }

    // svg units per screen pixel (meet: the limiting axis)
    function unitsPerPx() {
      var r = viewport.getBoundingClientRect();
      return Math.max(view.w / r.width, view.h / r.height);
    }

    function zoomAt(factor, cx, cy) {
      var minW = full.w / 12, maxW = full.w * 1.5;
      var w = Math.min(maxW, Math.max(minW, view.w * factor));
      var f = w / view.w;
      view.x = cx - (cx - view.x) * f;
      view.y = cy - (cy - view.y) * f;
      view.w = w;
      view.h = view.h * f;
      apply();
    }

    function center() { return [view.x + view.w / 2, view.y + view.h / 2]; }

    function fit() { view = { x: full.x, y: full.y, w: full.w, h: full.h }; apply(); }

    function clientToSvg(clientX, clientY) {
      var pt = svg.createSVGPoint();
      pt.x = clientX; pt.y = clientY;
      var m = svg.getScreenCTM();
      if (!m) return center();
      var p = pt.matrixTransform(m.inverse());
      return [p.x, p.y];
    }

    // wheel: zoom only with ctrl/meta (trackpad pinch sends ctrlKey) so the
    // page still scrolls normally over the graph
    viewport.addEventListener("wheel", function (ev) {
      if (!(ev.ctrlKey || ev.metaKey)) return;
      ev.preventDefault();
      var p = clientToSvg(ev.clientX, ev.clientY);
      zoomAt(Math.exp(ev.deltaY * 0.01), p[0], p[1]);
    }, { passive: false });

    // drag to pan; suppress the click that ends a real drag
    var drag = null, dragged = false;
    viewport.addEventListener("pointerdown", function (ev) {
      if (ev.button !== 0) return;
      drag = { x: ev.clientX, y: ev.clientY, vx: view.x, vy: view.y, k: unitsPerPx() };
      dragged = false;
    });
    window.addEventListener("pointermove", function (ev) {
      if (!drag) return;
      var dx = ev.clientX - drag.x, dy = ev.clientY - drag.y;
      if (!dragged && Math.abs(dx) + Math.abs(dy) > 4) {
        dragged = true;
        viewport.classList.add("dragging");
      }
      if (dragged) {
        view.x = drag.vx - dx * drag.k;
        view.y = drag.vy - dy * drag.k;
        apply();
      }
    });
    window.addEventListener("pointerup", function () {
      drag = null;
      viewport.classList.remove("dragging");
    });
    viewport.addEventListener("click", function (ev) {
      if (dragged) { ev.preventDefault(); ev.stopPropagation(); dragged = false; }
    }, true);

    function button(id, fn) {
      var b = document.getElementById(id);
      if (b) b.addEventListener("click", fn);
    }
    button("graph-zoom-in", function () { var c = center(); zoomAt(1 / 1.4, c[0], c[1]); });
    button("graph-zoom-out", function () { var c = center(); zoomAt(1.4, c[0], c[1]); });
    button("graph-fit", fit);

    // hover a kind: highlight it, its edges and its neighbours
    var edges = Array.prototype.slice.call(svg.querySelectorAll("g.edge"));
    var byKind = {};
    edges.forEach(function (e) {
      var parts = (e.id || "").split("__"); // e__<from>__<to>
      if (parts.length !== 3) return;
      [parts[1], parts[2]].forEach(function (k) { (byKind[k] = byKind[k] || []).push(e); });
    });

    function kindOf(node) { return (node.id || "").replace(/^k__/, ""); }

    function clear() {
      svg.classList.remove("focusing");
      svg.querySelectorAll(".hl, .focus").forEach(function (el) { el.classList.remove("hl", "focus"); });
    }

    svg.querySelectorAll("g.node").forEach(function (node) {
      node.addEventListener("mouseenter", function () {
        if (drag && dragged) return;
        var k = kindOf(node);
        clear();
        svg.classList.add("focusing");
        node.classList.add("hl", "focus");
        (byKind[k] || []).forEach(function (e) {
          e.classList.add("hl");
          var parts = e.id.split("__");
          [parts[1], parts[2]].forEach(function (n) {
            var el = document.getElementById("k__" + n);
            if (el) el.classList.add("hl");
          });
        });
      });
      node.addEventListener("mouseleave", clear);
    });
  }

  // ---------- theme ----------

  // light by default; dark is opt-in and remembered per browser
  function initTheme() {
    var btn = document.getElementById("theme-toggle");
    if (!btn) return;
    var root = document.documentElement;
    function sync() { btn.setAttribute("aria-pressed", root.dataset.theme === "dark" ? "true" : "false"); }
    sync();
    btn.addEventListener("click", function () {
      var dark = root.dataset.theme !== "dark";
      if (dark) root.dataset.theme = "dark"; else delete root.dataset.theme;
      try { localStorage.setItem("schema-browser-theme", dark ? "dark" : "light"); } catch (e) {}
      sync();
    });
  }

  function init() { initTheme(); initSearch(); initGraph(); }
  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
