// Shared CMS snippet runtime. Keep the VANZ and Oakved copies in sync.
// Only published configuration enters this loader; it never reads admin drafts.
const runtimes = new WeakMap();
const positions = ["header", "body", "footer"];
const permittedCategories = new Set(["necessary", "analytics", "marketing", "preferences"]);

export function snippetRequirements(node) {
  if (node.nodeType !== 1) return [];
  const explicit = node.getAttribute("data-cms-consent");
  if (explicit) {
    const values = explicit.split(/[ ,]+/).filter(Boolean);
    return values.length && values.every((value) => permittedCategories.has(value))
      ? values.filter((value) => value !== "necessary") : ["analytics", "marketing"];
  }
  const tag = node.tagName.toLowerCase();
  if (tag === "meta" || tag === "style" ||
      (tag === "link" && node.getAttribute("rel") === "stylesheet") ||
      (tag === "script" && ["application/ld+json", "application/json"].includes(node.getAttribute("type")))) return [];
  return ["analytics", "marketing"];
}

export function hasSnippetConsent(requirements, consent = {}) {
  return requirements.every((category) => consent[category] === true);
}

export function createWebsiteCodeRuntime({
  document: doc = globalThis.document,
  loadPublished,
  reload = () => globalThis.location.reload(),
  scriptTimeoutMs = 8000,
  onError = (error) => console.warn("Website code could not be loaded.", error),
}) {
  if (runtimes.has(doc)) return runtimes.get(doc);
  let consent = {};
  let entries = [];
  let queue = Promise.resolve();
  let reloadRequested = false;
  let disposed = false;
  const inserted = new Set();
  const executedRequirements = new Set();

  // Recreate scripts to execute them. innerHTML alone leaves scripts inert.
  const appendNode = async (source, parent, before, requirements) => {
    if (disposed || !hasSnippetConsent(requirements, consent)) return;
    if (source.nodeType !== 1) {
      parent.insertBefore(source.cloneNode(true), before);
      return;
    }
    const isScript = source.tagName.toLowerCase() === "script";
    const node = doc.createElement(source.tagName.toLowerCase());
    for (const attr of Array.from(source.attributes)) node.setAttribute(attr.name, attr.value);
    if (isScript) {
      const nonce = doc.querySelector("script[nonce]")?.nonce;
      if (nonce) node.nonce = nonce;
      node.textContent = source.textContent;
      const executable = !["application/ld+json", "application/json"].includes(source.getAttribute("type"));
      const external = node.hasAttribute("src");
      const ordered = external && !node.hasAttribute("async");
      if (ordered) node.async = false;
      const complete = ordered ? new Promise((resolve) => {
        const timer = setTimeout(() => { onError(new Error("CMS script load timed out")); resolve(); }, scriptTimeoutMs);
        const finish = () => { clearTimeout(timer); resolve(); };
        node.addEventListener("load", finish, { once: true });
        node.addEventListener("error", finish, { once: true });
      }) : null;
      if (executable) requirements.forEach((category) => executedRequirements.add(category));
      parent.insertBefore(node, before);
      if (complete) await complete;
      return;
    }
    // Optional frames and resources must also stop when their consent is withdrawn.
    requirements.forEach((category) => executedRequirements.add(category));
    parent.insertBefore(node, before);
    for (const child of Array.from(source.childNodes)) {
      await appendNode(child, node, null, requirements);
    }
  };
  const apply = () => {
    queue = queue.then(async () => {
      for (const entry of entries) {
        if (disposed || inserted.has(entry) || !hasSnippetConsent(entry.requirements, consent)) continue;
        inserted.add(entry);
        await appendNode(entry.node, entry.parent, entry.anchor, entry.requirements);
      }
    }).catch(onError);
    return queue;
  };
  const runtime = {
    ready: null,
    updateConsent(next) {
      consent = next || {};
      // Removing a script element does not stop its timers/listeners. Reload the page
      // after withdrawal so no previously running tracker survives the new choice.
      if (!reloadRequested && Array.from(executedRequirements).some((category) => consent[category] !== true)) {
        reloadRequested = true; disposed = true; reload(); return Promise.resolve();
      }
      return apply();
    },
  };
  runtimes.set(doc, runtime);
  runtime.ready = Promise.resolve().then(loadPublished).then((payload) => {
    if (!payload?.version || !payload.content) return;
    for (const position of positions) {
      const section = payload.content[position];
      if (!section?.enabled || !section.code) continue;
      const template = doc.createElement("template");
      template.innerHTML = section.code;
      const parent = position === "header" ? doc.head : doc.body;
      const anchor = doc.createComment("cms-" + position);
      parent.insertBefore(anchor, position === "body" ? parent.firstChild : null);
      for (const node of Array.from(template.content.childNodes)) {
        // A wrapper inherits the union of the nested resources' consent categories.
        const requirements = new Set(snippetRequirements(node));
        if (node.querySelectorAll) for (const child of node.querySelectorAll("script,iframe,img,link")) {
          snippetRequirements(child).forEach((category) => requirements.add(category));
        }
        entries.push({ node, parent, anchor, requirements: Array.from(requirements) });
      }
    }
    return apply();
  }).catch(onError);
  return runtime;
}
