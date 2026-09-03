import { createHash } from "node:crypto";
import postcss from "postcss";
import selectorParser from "postcss-selector-parser";
import valueParser from "postcss-value-parser";

const componentPattern = /^[A-Za-z][A-Za-z0-9_.-]{2,255}$/;

export function scopeId(componentId) {
  if (!componentPattern.test(componentId)) throw new Error("Component ID must be a stable qualified name");
  return `w-${createHash("sha256").update(componentId, "utf8").digest("hex").slice(0, 12)}`;
}

export function scopeAttribute(componentId) {
  return { name: "data-woge-scope", value: scopeId(componentId) };
}

export async function scopeCss({ componentId, css, from = `${componentId}.css` }) {
  const id = scopeId(componentId);
  const keyframes = new Map();
  const plugin = {
    postcssPlugin: "woge-scope-spike",
    Once(root) {
      root.walkAtRules((atRule) => {
        if (!/^(?:-[a-z]+-)?keyframes$/i.test(atRule.name)) return;
        const globalMatch = atRule.params.trim().match(/^:global\(([-_a-zA-Z][-_a-zA-Z0-9]*)\)$/);
        if (globalMatch) {
          atRule.params = globalMatch[1];
          return;
        }
        const original = atRule.params.trim();
        const scoped = `${original}-${id}`;
        keyframes.set(original, scoped);
        atRule.params = scoped;
      });

      root.walkRules((rule) => {
        if (insideKeyframes(rule)) return;
        rule.selector = scopeSelector(rule.selector, id);
      });

      root.walkDecls((declaration) => {
        if (!/^animation(?:-name)?$/i.test(declaration.prop)) return;
        const parsed = valueParser(declaration.value);
        parsed.walk((node) => {
          if (node.type === "word" && keyframes.has(node.value)) node.value = keyframes.get(node.value);
        });
        declaration.value = parsed.toString();
      });
    }
  };

  const result = await postcss([plugin]).process(css, {
    from,
    to: `${componentId}.scoped.css`,
    map: { inline: false, annotation: false, sourcesContent: true }
  });

  return {
    css: result.css,
    map: result.map.toJSON(),
    scope: scopeAttribute(componentId),
    keyframes: Object.fromEntries(keyframes)
  };
}

function scopeSelector(source, id) {
  return selectorParser((selectors) => {
    selectors.each((selector) => {
      selector.walkPseudos((pseudo) => {
        if (pseudo.value !== ":global") return;
        if (!pseudo.nodes || pseudo.nodes.length !== 1 || pseudo.nodes[0].nodes.some((node) => node.type === "combinator")) {
          throw pseudo.error(":global(...) must contain exactly one compound selector");
        }
        const replacements = pseudo.nodes[0].nodes.map((node) => {
          const clone = node.clone();
          markGlobal(clone);
          return clone;
        });
        pseudo.replaceWith(...replacements);
      });

      const compounds = [[]];
      for (const node of selector.nodes) {
        if (node.type === "combinator") compounds.push([]);
        else compounds.at(-1).push(node);
      }
      const subject = compounds.reverse().find((compound) => compound.some(isLocalSubject));
      if (!subject) return;

      const scope = selectorParser.pseudo({ value: ":where" });
      const scopeSelector = selectorParser.selector();
      scopeSelector.append(selectorParser.attribute({
        attribute: "data-woge-scope",
        operator: "=",
        value: id,
        quoteMark: '"'
      }));
      scope.append(scopeSelector);

      const pseudoElement = subject.find((node) => node.type === "pseudo" && node.value.startsWith("::"));
      if (pseudoElement) pseudoElement.parent.insertBefore(pseudoElement, scope);
      else subject.at(-1).parent.insertAfter(subject.at(-1), scope);
    });
  }).processSync(source);
}

function markGlobal(node) {
  node.wogeGlobal = true;
  if (node.nodes) node.nodes.forEach(markGlobal);
}

function isLocalSubject(node) {
  if (node.wogeGlobal || node.type === "comment") return false;
  return node.type !== "pseudo" || !node.value.startsWith("::");
}

function insideKeyframes(rule) {
  let parent = rule.parent;
  while (parent) {
    if (parent.type === "atrule" && /^(?:-[a-z]+-)?keyframes$/i.test(parent.name)) return true;
    parent = parent.parent;
  }
  return false;
}
