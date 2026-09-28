/** Preserve Iconify alias chains separately from icons (which require a body). */
export function subsetIconCollection(data, names) {
  const icons = {};
  const aliases = {};
  const visiting = new Set();
  function include(name) {
    if (icons[name] || aliases[name]) return true;
    if (visiting.has(name)) return false;
    if (data.icons?.[name]) {
      icons[name] = data.icons[name];
      return true;
    }
    const alias = data.aliases?.[name];
    if (!alias) return false;
    visiting.add(name);
    const found = include(alias.parent);
    visiting.delete(name);
    if (found) aliases[name] = alias;
    return found;
  }
  names.forEach(include);
  return { ...data, icons, aliases };
}
