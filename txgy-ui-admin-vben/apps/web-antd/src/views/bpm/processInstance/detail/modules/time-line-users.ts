type User = { id?: number; nickname?: string };
type Task = { assigneeUser?: User; ownerUser?: User };

function displayedUser(task: Task): User | undefined {
  return task.assigneeUser ?? task.ownerUser;
}

function displayKey(user?: User): string | undefined {
  const name = user?.nickname?.trim();
  return name ? `name:${name}` : user?.id ? `id:${user.id}` : undefined;
}

export function getUniqueTaskUsers(tasks: Task[] = []): User[] {
  const seen = new Set<string>();
  return tasks.flatMap((task) => {
    const user = displayedUser(task);
    const key = displayKey(user);
    if (!user || !key || seen.has(key)) return [];
    seen.add(key);
    return [user];
  });
}

export function shouldShowTaskUser(tasks: Task[], index: number): boolean {
  const key = displayKey(displayedUser(tasks[index]!));
  if (!key) return true;
  return !tasks.slice(0, index).some(task => displayKey(displayedUser(task)) === key);
}

export function getVisibleCandidateUsers<T extends User>(tasks: Task[], candidates?: T[]): T[] {
  const seen = new Set(tasks.map(task => displayKey(displayedUser(task))).filter(Boolean));
  return (candidates ?? []).filter(user => {
    const key = displayKey(user);
    if (!key || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}
