"use client";

import { useCallback, useEffect, useState } from "react";
import { onAuthStateChanged, signOut } from "firebase/auth";
import { useRouter } from "next/navigation";
import { QRCodeSVG } from "qrcode.react";
import { auth } from "@/lib/firebase";
import { api } from "@/lib/api";
import WorkspaceHeader from "@/components/workspace-header";

type Course = { id: number; code: string; name: string };
type RosterEntry = { id: number; registerNumber: string; fullName: string; status: string | null };
type StaffQueueEntry = { id: number; studentId: number; registerNumber: string; fullName: string; type: string; startDate: string; endDate: string; reason: string; proofMetadata?: string };
type Defaulter = { studentId: number; registerNumber: string; fullName: string; courseCode: string; percentage: number; classesNeeded: number };
type Trend = { dayOfWeek: string; period: number; sessions: number; attendancePercentage: number };
type GeofenceFlag = { id: number; classSessionId: number; studentId: number; fullName: string; registerNumber: string; courseCode: string; courseTitle: string; classDate: string; period: number; reason: string; distanceMeters: number; accuracyMeters: number; status: string; createdAt: string };
type AuditEntry = { registerNumber: string; fullName: string; eventType: string; previousStatus: string | null; newStatus: string | null; actorEmail: string | null; reason: string; recordedAt: string; ipAddress: string | null };
type FlagFilter = "OPEN" | "REVIEWED" | "ALL";

export default function StaffPage() {
  const router = useRouter();
  const [courses, setCourses] = useState<Course[]>([]);
  const [courseId, setCourseId] = useState("");
  const [sessionId, setSessionId] = useState<number | null>(null);
  const [roster, setRoster] = useState<RosterEntry[]>([]);
  const [qr, setQr] = useState<{ token: string; expiresAt: string; closesAt: string } | null>(null);
  const [queue, setQueue] = useState<StaffQueueEntry[]>([]);
  const [defaulters, setDefaulters] = useState<Defaulter[]>([]);
  const [trends, setTrends] = useState<Trend[]>([]);
  const [geofenceFlags, setGeofenceFlags] = useState<GeofenceFlag[]>([]);
  const [sessionAudit, setSessionAudit] = useState<AuditEntry[]>([]);
  const [day, setDay] = useState(new Date().toISOString().slice(0, 10));
  const [period, setPeriod] = useState(1);
  const [durationMinutes, setDurationMinutes] = useState(10);
  const [defaulterThreshold, setDefaulterThreshold] = useState(75);
  const [flagFilter, setFlagFilter] = useState<FlagFilter>("OPEN");
  const [clock, setClock] = useState(Date.now());
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [presentationMode, setPresentationMode] = useState(false);

  const refreshGeofenceFlags = useCallback(async () => {
    try {
      setGeofenceFlags(await api<GeofenceFlag[]>(`/staff/geofence-flags?status=${flagFilter}`));
    } catch (error) { setMessage(error instanceof Error ? error.message : "Unable to load geofence flags."); }
  }, [flagFilter]);

  const loadWorkspace = useCallback(async () => {
    try {
      const [courseRows, odRows, defaulterRows, trendRows, flagRows] = await Promise.all([
        api<Course[]>("/staff/courses"),
        api<StaffQueueEntry[]>("/staff/requests/od"),
        api<Defaulter[]>(`/staff/reports/defaulters?threshold=${defaulterThreshold}`),
        api<Trend[]>("/staff/reports/trends"),
        api<GeofenceFlag[]>(`/staff/geofence-flags?status=${flagFilter}`),
      ]);
      setCourses(courseRows);
      setQueue(odRows);
      setDefaulters(defaulterRows);
      setTrends(trendRows);
      setGeofenceFlags(flagRows);
      setCourseId((current) => current || (courseRows.length ? String(courseRows[0].id) : ""));
    } catch (error) { setMessage(error instanceof Error ? error.message : "Unable to load staff workspace."); }
  }, [defaulterThreshold, flagFilter]);

  useEffect(() => onAuthStateChanged(auth, async (user) => {
    if (!user) { router.replace("/"); return; }
    await loadWorkspace();
  }), [router, loadWorkspace]);

  const refreshRoster = useCallback(async (id: number) => {
    try { setRoster(await api<RosterEntry[]>(`/staff/sessions/${id}/roster`)); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Unable to load class roster."); }
  }, []);

  const refreshAudit = useCallback(async (id: number) => {
    try { setSessionAudit(await api<AuditEntry[]>(`/staff/sessions/${id}/audit`)); }
    catch (error) { setMessage(error instanceof Error ? error.message : "Unable to load the session audit."); }
  }, []);

  useEffect(() => {
    if (!sessionId) return;
    let active = true;
    const refresh = async () => {
      try {
        const result = await api<{ token: string; expiresAt: string; closesAt: string }>(`/staff/sessions/${sessionId}/qr`);
        if (active) setQr(result);
        if (active) {
          await Promise.all([refreshRoster(sessionId), refreshAudit(sessionId), refreshGeofenceFlags()]);
        }
      } catch (error) {
        if (active) {
          const detail = error instanceof Error ? error.message : "QR session ended.";
          if (detail.toLowerCase().includes("closed")) {
            setSessionId(null);
            setQr(null);
          }
          setMessage(detail);
        }
      }
    };
    void refresh();
    const timer = window.setInterval(() => { void refresh(); }, 5000);
    return () => { active = false; window.clearInterval(timer); };
  }, [sessionId, refreshAudit, refreshGeofenceFlags, refreshRoster]);

  useEffect(() => {
    const timer = window.setInterval(() => setClock(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, []);

  async function startSession() {
    if (!courseId) return;
    setBusy(true);
    setMessage("");
    try {
      const created = await api<{ id: number }>("/staff/sessions", {
        method: "POST", body: JSON.stringify({ courseId: Number(courseId), date: day, period }),
      });
      const active = await api<{ token: string; expiresAt: string; closesAt: string }>(
        `/staff/sessions/${created.id}/qr`, { method: "POST", body: JSON.stringify({ durationMinutes }) });
      setSessionId(created.id);
      setQr(active);
      await Promise.all([refreshRoster(created.id), refreshAudit(created.id)]);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Unable to start attendance."); }
    finally { setBusy(false); }
  }

  async function markAll(status: string) {
    if (!sessionId) return;
    setBusy(true);
    try {
      await api(`/staff/sessions/${sessionId}/attendance`, {
        method: "PUT",
        body: JSON.stringify({ records: roster.map((entry) => ({ studentId: entry.id, status })) }),
      });
      await Promise.all([refreshRoster(sessionId), refreshAudit(sessionId)]);
      setMessage(`Roster updated: ${status}.`);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Bulk update failed."); }
    finally { setBusy(false); }
  }

  async function setStatus(studentId: number, status: string) {
    if (!sessionId) return;
    try {
      await api(`/staff/sessions/${sessionId}/attendance`, {
        method: "PUT", body: JSON.stringify({ records: [{ studentId, status }] }),
      });
      await Promise.all([refreshRoster(sessionId), refreshAudit(sessionId)]);
    } catch (error) { setMessage(error instanceof Error ? error.message : "Attendance update failed."); }
  }

  async function review(requestId: number, approved: boolean) {
    try {
      await api(`/staff/requests/od/${requestId}`, { method: "PUT", body: JSON.stringify({ approved }) });
      await loadWorkspace();
      setMessage(approved ? "Request approved and attendance updated." : "Request rejected.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Review failed."); }
  }

  async function reviewGeofenceFlag(flagId: number) {
    try {
      await api(`/staff/geofence-flags/${flagId}/review`, { method: "PUT" });
      await refreshGeofenceFlags();
      setMessage("Flag reviewed. This does not change attendance; use a separate audited roster action if a correction is needed.");
    } catch (error) { setMessage(error instanceof Error ? error.message : "Geofence flag review failed."); }
  }

  async function exportCsv() {
    if (!sessionId || !auth.currentUser) return;
    try {
      const token = await auth.currentUser.getIdToken();
      const response = await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api"}/staff/reports/attendance.csv?sessionId=${sessionId}`, {
        headers: { Authorization: `Bearer ${token}` },
      });
      if (!response.ok) throw new Error("CSV export could not be generated.");
      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `attendiq-session-${sessionId}.csv`;
      anchor.click();
      URL.revokeObjectURL(url);
    } catch (error) { setMessage(error instanceof Error ? error.message : "CSV export failed."); }
  }

  const enrolledCount = roster.length;
  const checkedInCount = roster.filter((student) => ["PRESENT", "LATE", "ON_DUTY"].includes(student.status ?? "")).length;
  const qrSecondsRemaining = qr ? Math.max(0, Math.ceil((new Date(qr.expiresAt).getTime() - clock) / 1000)) : null;
  const sessionMinutesRemaining = qr ? Math.max(0, Math.ceil((new Date(qr.closesAt).getTime() - clock) / 60000)) : null;

  return (
    <main className="portal-bg">
      <WorkspaceHeader role="STAFF" name={auth.currentUser?.displayName ?? "Staff"} email={auth.currentUser?.email ?? ""}
        facts={[{ label: "Assigned courses", value: String(courses.length) }]}
        presentationMode={presentationMode} onTogglePresentation={() => setPresentationMode((current) => !current)}
        onSignOut={() => void signOut(auth)} />
      <section className="portal-content">
        <div className="page-intro" id="overview"><div><p className="eyebrow">Staff workspace · Today</p><h1>Your teaching, in focus.</h1><p>Start a secure check-in, see who is present, and keep every decision reviewable.</p></div><button className="secondary-button" onClick={() => void loadWorkspace()}>Refresh workspace</button></div>
        {message && <div className="notice glass-panel" role="status">{message}</div>}
        <div className="staff-grid">
          <section className="glass-panel session-card" id="session">
            <div><p className="eyebrow">Session control</p><h2>{sessionId ? "Your class is live." : "Open a class session."}</h2><p>Rotating QR tokens, location checks, and attendance cutoffs are enforced by the server.</p></div>
            <div className="session-controls">
              <label className="field-label" htmlFor="session-course">Course<select id="session-course" value={courseId} onChange={(event) => setCourseId(event.target.value)}>{courses.map((course) => <option value={course.id} key={course.id}>{course.code} · {course.name}</option>)}</select></label>
              <label className="field-label" htmlFor="session-date">Date<input id="session-date" type="date" value={day} onChange={(event) => setDay(event.target.value)} /></label>
              <label className="field-label" htmlFor="session-period">Period<input id="session-period" type="number" min="1" max="8" value={period} onChange={(event) => setPeriod(Number(event.target.value))} /></label>
              <label className="field-label" htmlFor="session-duration">Session length<select id="session-duration" value={durationMinutes} onChange={(event) => setDurationMinutes(Number(event.target.value))}><option value={10}>10 minutes</option><option value={15}>15 minutes</option><option value={20}>20 minutes</option></select></label>
              <button className="primary-button" onClick={startSession} disabled={busy || !courses.length || Boolean(sessionId)}>{busy ? "Opening session…" : `Start ${durationMinutes}-minute session`}</button>
            </div>
            {!courses.length && <p className="empty-state">No courses are assigned to this staff account yet.</p>}
            {qr && <div className="qr-display"><QRCodeSVG value={qr.token} size={190} bgColor="transparent" fgColor="#234b3b" includeMargin /><div><span className="live-state">Live session · {sessionMinutesRemaining} min left</span><p className="eyebrow">Rotating QR · next refresh in {qrSecondsRemaining}s</p><code>{qr.token}</code><p>Each signed code expires on its 30-second rotation boundary. The session closes at {new Date(qr.closesAt).toLocaleTimeString()}.</p><button className="quiet-button" onClick={() => navigator.clipboard.writeText(qr.token)}>Copy current token</button></div></div>}
          </section>
          <section className="glass-panel roster-card" id="roster"><div className="section-heading"><div><p className="eyebrow">Live student status</p><h2>Class roster</h2></div>{sessionId ? <span className="live-state">Refreshing live</span> : <span className="status-pill">{roster.length} enrolled</span>}</div>
            <div className="roster-summary"><b>{checkedInCount}<span> / {enrolledCount}</span></b><small>checked in</small><div className="course-meter"><span style={{ width: `${enrolledCount ? checkedInCount / enrolledCount * 100 : 0}%` }} /></div></div>
            <div className="button-row"><button className="secondary-button" disabled={!sessionId || busy} onClick={() => void markAll("PRESENT")}>All present</button><button className="secondary-button" disabled={!sessionId || busy} onClick={() => void markAll("ABSENT")}>All absent</button><button className="secondary-button" disabled={!sessionId || busy} onClick={() => void markAll("ON_DUTY")}>All OD</button></div>
            <div className="button-row export-actions"><button className="quiet-button" disabled={!sessionId || presentationMode} title={presentationMode ? "CSV export is disabled while personal details are hidden." : undefined} onClick={() => void exportCsv()}>Export CSV</button><button className="quiet-button" onClick={() => window.print()}>Print / Save PDF</button></div>
            <div className="roster-list">{roster.map((student, index) => <div className="roster-row" key={student.id}><div><b>{presentationMode ? `Student ${String(index + 1).padStart(2, "0")}` : student.fullName}</b><span>{presentationMode ? "Personal details hidden" : student.registerNumber}</span></div><select aria-label={`Attendance for ${presentationMode ? `student ${index + 1}` : student.fullName}`} value={student.status ?? ""} onChange={(event) => void setStatus(student.id, event.target.value)} disabled={!sessionId}><option value="">Unmarked</option><option value="PRESENT">Present</option><option value="LATE">Late</option><option value="ABSENT">Absent</option><option value="ON_DUTY">On-duty</option></select></div>)}{roster.length === 0 && <p className="empty-state">Start a session to load enrolled students and check-in status.</p>}</div>
          </section>
        </div>
        {sessionId && <details className="glass-panel drawer section-block">
          <summary><span><span className="eyebrow">Session record</span><br />Attendance audit log <span className="status-pill">{sessionAudit.length}</span></span></summary>
          <div className="drawer-body">{sessionAudit.map((entry, index) => <div className="audit-row" key={`${entry.registerNumber}-${entry.recordedAt}-${index}`}><div><strong>{presentationMode ? "Student record" : entry.fullName} · {entry.previousStatus ?? "—"} → {entry.newStatus ?? "—"}</strong><span>{presentationMode ? "Audit details hidden in presentation view." : `${entry.registerNumber} · ${entry.reason}`}</span><span>{presentationMode ? "Actor and network details hidden" : `${entry.actorEmail ?? "System"}${entry.ipAddress ? ` · ${entry.ipAddress}` : ""}`}</span></div><time dateTime={entry.recordedAt}>{new Date(entry.recordedAt).toLocaleString()}</time></div>)}{sessionAudit.length === 0 && <p className="empty-state">No changes have been recorded for this session yet.</p>}</div>
        </details>}
        <section className="section-block" id="insights"><div className="staff-grid">
          <section className="glass-panel"><div className="section-heading"><div><p className="eyebrow">Eligibility watch</p><h2>Attendance risk</h2></div><label className="field-label" htmlFor="defaulter-threshold">Below<select id="defaulter-threshold" className="filter-select" value={defaulterThreshold} onChange={(event) => setDefaulterThreshold(Number(event.target.value))}><option value={75}>75%</option><option value={70}>70%</option><option value={65}>65%</option><option value={60}>60%</option></select></label></div>
            <span className="status-pill detained_risk">{defaulters.length} students below {defaulterThreshold}%</span>
            <div className="roster-list">{defaulters.map((item, index) => <div className="roster-row" key={`${item.studentId}-${item.courseCode}`}><div><b>{presentationMode ? `Student ${String(index + 1).padStart(2, "0")}` : item.fullName} · {item.courseCode}</b><span>{presentationMode ? "Personal details hidden" : item.registerNumber} · {item.classesNeeded} classes to target</span></div><span className="risk-value">{item.percentage.toFixed(1)}%</span></div>)}{defaulters.length === 0 && <p className="muted">No students below the configured threshold.</p>}</div>
          </section>
          <section className="glass-panel"><div className="section-heading"><div><p className="eyebrow">Class patterns</p><h2>Period & weekday trends</h2></div></div>
            <div className="trend-list">{trends.map((trend) => <div className="trend-row" key={`${trend.dayOfWeek}-${trend.period}`}><span>{trend.dayOfWeek.slice(0, 3)} · P{trend.period}</span><div className="course-meter"><span style={{ width: `${Math.min(trend.attendancePercentage, 100)}%` }} /></div><b>{trend.attendancePercentage.toFixed(0)}%</b></div>)}{trends.length === 0 && <p className="muted">Trend data appears once attendance is recorded.</p>}</div>
          </section>
        </div></section>
        <section className="section-block" id="integrity">
          <div className="section-heading"><div><p className="eyebrow">Location integrity</p><h2>Geofence review</h2><p>Borderline location scans remain rejected until a separate, audited attendance decision is made.</p></div><label className="field-label" htmlFor="geofence-filter">Show<select id="geofence-filter" className="filter-select" value={flagFilter} onChange={(event) => setFlagFilter(event.target.value as FlagFilter)}><option value="OPEN">Needs review</option><option value="REVIEWED">Reviewed</option><option value="ALL">All flags</option></select></label></div>
          <div className="request-grid">{geofenceFlags.map((flag, index) => <article className="flag-card" key={flag.id}>
            <div className="flag-heading"><div><strong>{presentationMode ? `Student ${String(index + 1).padStart(2, "0")}` : flag.fullName} · {flag.courseCode}</strong><span>{presentationMode ? "Personal details hidden" : flag.registerNumber} · {flag.classDate} · Period {flag.period}</span></div><span className={`status-pill ${flag.status === "OPEN" ? "condonation" : "eligible"}`}>{flag.status}</span></div>
            <p className="flag-details">{flag.reason === "LOW_ACCURACY" ? "GPS accuracy exceeded the configured limit but stayed within the review window." : "Reported position was just beyond the configured geofence boundary."} · {Math.round(flag.distanceMeters)} m from campus · ±{Math.round(flag.accuracyMeters)} m accuracy.</p>
            {flag.status === "OPEN" && <button className="secondary-button" onClick={() => void reviewGeofenceFlag(flag.id)}>Mark reviewed</button>}
          </article>)}{geofenceFlags.length === 0 && <p className="empty-state">{flagFilter === "OPEN" ? "No borderline geofence scans need review." : "No geofence flags match this filter."}</p>}</div>
        </section>
        <section className="section-block" id="requests"><div className="section-heading"><div><p className="eyebrow">Student requests</p><h2>OD & medical leave review</h2></div><span className="status-pill">{queue.length} requests</span></div>
          <div className="request-grid">{queue.map((request, index) => <article className="glass-panel request-card" key={request.id}><div className="request-meta"><span>{request.type.replace("_", " ")}</span><span>{request.startDate} → {request.endDate}</span></div><h3>{presentationMode ? `Student ${String(index + 1).padStart(2, "0")}` : request.fullName} <small>{presentationMode ? "Personal details hidden" : request.registerNumber}</small></h3><p>{presentationMode ? "Request details hidden in presentation view." : request.reason}</p>{request.proofMetadata && <p className="muted">{presentationMode ? "Proof reference hidden" : `Proof reference: ${request.proofMetadata}`}</p>}<div className="button-row"><button className="primary-button" onClick={() => void review(request.id, true)}>Approve</button><button className="secondary-button" onClick={() => void review(request.id, false)}>Reject</button></div></article>)}{queue.length === 0 && <p className="empty-state">No OD or medical requests are waiting for review.</p>}</div>
        </section>
      </section>
    </main>
  );
}
