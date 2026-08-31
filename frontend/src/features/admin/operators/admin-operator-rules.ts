export function operatorEmailChanged(currentEmail: string, candidateEmail: string) {
  return currentEmail.trim().toLowerCase() !== candidateEmail.trim().toLowerCase();
}

export function roleSelectionChanges(currentIds: string[], selectedIds: string[]) {
  const current = new Set(currentIds);
  const selected = new Set(selectedIds);
  return {
    added: [...selected].filter((id) => !current.has(id)),
    removed: [...current].filter((id) => !selected.has(id)),
  };
}
