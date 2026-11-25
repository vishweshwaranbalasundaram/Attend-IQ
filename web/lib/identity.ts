export type PortalRole = "STUDENT" | "STAFF";

export function emailMatchesRole(email: string, role: PortalRole, domain: string): boolean {
  const escapedDomain = domain.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = role === "STAFF"
    ? new RegExp(`^[a-z]{1,4}\\.[a-z]+@${escapedDomain}$`)
    : new RegExp(`^[a-z]+\\.[a-z]{1,4}\\.(20\\d{2})\\.([a-z]{2,10})@${escapedDomain}$`);
  return pattern.test(email.trim().toLowerCase());
}

export function roleHint(role: PortalRole, domain: string): string {
  return role === "STAFF"
    ? `Staff format: initial.name@${domain}`
    : `Student format: name.initial.year.department@${domain}`;
}
