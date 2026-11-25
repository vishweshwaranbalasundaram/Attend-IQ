"use client";

import Image from "next/image";
import { ChangeEvent, FormEvent, useEffect, useState } from "react";
import { updateProfile } from "firebase/auth";
import { getDownloadURL, ref, uploadBytes } from "firebase/storage";
import { auth, storage } from "@/lib/firebase";

export type WorkspaceRole = "STUDENT" | "STAFF";
export type ProfileFact = { label: string; value: string };
type NavigationLink = { label: string; href: string };
type NavigationGroup = { label: string; href?: string; items?: NavigationLink[] };

type WorkspaceHeaderProps = {
  role: WorkspaceRole;
  name: string;
  email: string;
  facts: ProfileFact[];
  presentationMode: boolean;
  onTogglePresentation: () => void;
  onSignOut: () => void;
};

const navigation = {
  STUDENT: [
    { label: "Overview", href: "#overview" },
    { label: "Attendance", items: [
      { label: "Check-in", href: "#check-in" },
      { label: "Courses", href: "#courses" },
      { label: "History", href: "#history" },
    ] },
    { label: "Requests", items: [
      { label: "OD & medical leave", href: "#requests" },
      { label: "Request status", href: "#requests" },
      { label: "Audit record", href: "#requests" },
    ] },
  ],
  STAFF: [
    { label: "Overview", href: "#overview" },
    { label: "Session", items: [
      { label: "Session control", href: "#session" },
      { label: "Live roster", href: "#roster" },
    ] },
    { label: "Insights", href: "#insights" },
    { label: "Reviews", items: [
      { label: "Geofence integrity", href: "#integrity" },
      { label: "OD & medical requests", href: "#requests" },
    ] },
  ],
} satisfies Record<WorkspaceRole, NavigationGroup[]>;

export default function WorkspaceHeader({
  role,
  name,
  email,
  facts,
  presentationMode,
  onTogglePresentation,
  onSignOut,
}: WorkspaceHeaderProps) {
  const menuLinks = navigation[role].reduce<NavigationLink[]>((links, item) => {
    if (item.href) links.push({ label: item.label, href: item.href });
    if (item.items) links.push(...item.items);
    return links;
  }, []);

  return (
    <header className="workspace-header">
      <div className="workspace-utility-bar">
        <span>ATTEND-IQ <i aria-hidden="true">/</i> STUDENT &amp; STAFF PORTAL</span>
        <span>SECURE INSTITUTIONAL ACCESS <i aria-hidden="true">·</i> EN</span>
      </div>
      <div className="workspace-header-main">
        <a className="brand" href={role === "STAFF" ? "/staff" : "/student"}>
          <span className="brand-icon">IQ</span>
          <span>Attend<span className="brand-accent">-IQ</span></span>
        </a>
        <nav className="workspace-navigation" aria-label="Workspace navigation">
          {navigation[role].map((item) => item.href
            ? <a key={item.label} href={item.href}>{item.label}</a>
            : <details className="workspace-nav-dropdown" key={item.label}>
              <summary>{item.label}<span aria-hidden="true">⌄</span></summary>
              <div className="workspace-nav-dropdown-panel">
                {item.items?.map((link) => <a key={link.label} href={link.href}>{link.label}</a>)}
              </div>
            </details>)}
        </nav>
        <details className="workspace-navigation-mobile">
          <summary aria-label="Open workspace menu"><span>Menu</span><span aria-hidden="true">⌄</span></summary>
          <nav className="workspace-navigation-panel" aria-label="Workspace menu">
            {menuLinks.map((link) => <a key={link.label} href={link.href}>{link.label}</a>)}
          </nav>
        </details>
        <ProfileMenu
          role={role}
          name={name}
          email={email}
          facts={facts}
          presentationMode={presentationMode}
          onTogglePresentation={onTogglePresentation}
          onSignOut={onSignOut}
        />
      </div>
    </header>
  );
}

function ProfileMenu({
  role,
  name,
  email,
  facts,
  presentationMode,
  onTogglePresentation,
  onSignOut,
}: WorkspaceHeaderProps) {
  const [displayName, setDisplayName] = useState(auth.currentUser?.displayName ?? name);
  const [photoURL, setPhotoURL] = useState(auth.currentUser?.photoURL ?? "");
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [feedback, setFeedback] = useState("");

  useEffect(() => {
    setDisplayName(auth.currentUser?.displayName ?? name);
    setPhotoURL(auth.currentUser?.photoURL ?? "");
  }, [name]);

  useEffect(() => {
    if (presentationMode) setEditing(false);
  }, [presentationMode]);

  const label = role === "STAFF" ? "Staff" : "Student";
  const visibleName = presentationMode ? label : displayName.trim() || name;
  const initials = visibleName.split(/\s+/).slice(0, 2).map((part) => part[0] ?? "").join("").toUpperCase();

  async function saveDisplayName(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const user = auth.currentUser;
    const nextName = displayName.trim();
    if (!user || !nextName) {
      setFeedback("Sign in again and enter a display name.");
      return;
    }

    setBusy(true);
    setFeedback("");
    try {
      await updateProfile(user, { displayName: nextName });
      setFeedback("Display name saved.");
      setEditing(false);
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : "Could not save your display name.");
    } finally {
      setBusy(false);
    }
  }

  async function uploadPhoto(file: File) {
    const user = auth.currentUser;
    if (!user) {
      setFeedback("Sign in again before changing your profile photo.");
      return;
    }
    if (!["image/jpeg", "image/png", "image/webp"].includes(file.type)) {
      setFeedback("Choose a JPG, PNG, or WebP image.");
      return;
    }
    if (file.size > 3 * 1024 * 1024) {
      setFeedback("Profile photos must be 3 MB or smaller.");
      return;
    }

    setBusy(true);
    setFeedback("");
    try {
      const photoRef = ref(storage, `profile-photos/${user.uid}/avatar`);
      await uploadBytes(photoRef, file, { contentType: file.type, cacheControl: "no-cache" });
      const url = await getDownloadURL(photoRef);
      await updateProfile(user, { photoURL: url, displayName: displayName.trim() || name });
      setPhotoURL(url);
      setFeedback("Profile photo updated.");
    } catch (error) {
      setFeedback(error instanceof Error ? error.message : "Could not upload your profile photo.");
    } finally {
      setBusy(false);
    }
  }

  function onPhotoSelected(event: ChangeEvent<HTMLInputElement>) {
    const file = event.currentTarget.files?.[0];
    event.currentTarget.value = "";
    if (file) void uploadPhoto(file);
  }

  return (
    <details className="account-menu">
      <summary aria-label="Open profile and account menu">
        <span className="profile-avatar">
          {!presentationMode && photoURL
            ? <Image src={photoURL} alt="" width={40} height={40} unoptimized />
            : <span>{initials || label[0]}</span>}
        </span>
        <span className="profile-chip-copy"><strong>My account</strong><small>{visibleName}</small></span>
        <span className="profile-chevron" aria-hidden="true">⌄</span>
      </summary>
      <div className="profile-popover">
        <div className="profile-popover-heading">
          <strong>{presentationMode ? `${label} profile` : displayName.trim() || name}</strong>
          <span>{role === "STAFF" ? "Institutional staff" : "Student workspace"}</span>
        </div>
        <dl className="profile-facts">
          <div><dt>Username</dt><dd>{presentationMode ? "Hidden in presentation view" : visibleName}</dd></div>
          <div><dt>Official name</dt><dd>{presentationMode ? "Hidden in presentation view" : name}</dd></div>
          <div><dt>Institutional email</dt><dd>{presentationMode ? "Hidden in presentation view" : email}</dd></div>
          {facts.map((fact) => (
            <div key={fact.label}><dt>{fact.label}</dt><dd>{presentationMode ? "Hidden in presentation view" : fact.value}</dd></div>
          ))}
        </dl>
        {editing && !presentationMode && (
          <form className="profile-edit-form" onSubmit={saveDisplayName}>
            <label className="field-label" htmlFor="profile-display-name">Username / display name</label>
            <input id="profile-display-name" required maxLength={60} value={displayName}
              onChange={(event) => setDisplayName(event.target.value)} />
            <button type="submit" className="secondary-button" disabled={busy}>{busy ? "Saving…" : "Save display name"}</button>
            <label className="profile-upload-button">{busy ? "Uploading…" : "Choose profile photo"}
              <input className="profile-file-input" type="file" accept="image/png,image/jpeg,image/webp"
                onChange={onPhotoSelected} disabled={busy} />
            </label>
            <small>JPG, PNG, or WebP · up to 3 MB</small>
          </form>
        )}
        {feedback && <p className="profile-feedback" role="status">{feedback}</p>}
        <div className="profile-actions">
          {!presentationMode && <button type="button" className="profile-action" onClick={() => { setEditing(!editing); setFeedback(""); }}>
            {editing ? "Close profile editor" : "Edit profile"}
          </button>}
          <button type="button" className="profile-action" aria-pressed={presentationMode} onClick={onTogglePresentation}>
            {presentationMode ? "Show personal details" : "Hide personal details"}
          </button>
          <button type="button" className="profile-action signout-action" onClick={onSignOut}>Sign out</button>
        </div>
      </div>
    </details>
  );
}
