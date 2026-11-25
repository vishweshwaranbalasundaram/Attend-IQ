"use client";

import { FormEvent, useEffect, useState } from "react";
import { onAuthStateChanged, signOut } from "firebase/auth";
import { useRouter } from "next/navigation";
import { Html5QrcodeScanner } from "html5-qrcode";
import { auth } from "@/lib/firebase";
import { api } from "@/lib/api";
import WorkspaceHeader from "@/components/workspace-header";

type Dashboard = {
  student: { fullName: string; email: string; registerNumber: string; program: string; semester: number; yearOfJoining: number; emailDepartment: string };
  overallPercentage: number;
  eligibility: string;
  classesHeld: number;
  classesAttended: number;
  classesNeeded: number;
  safeBunksRemaining: number;
  courses: Array<{ courseCode: string; courseTitle: string; percentage: number; eligibility: string; classesNeeded: number; safeBunksRemaining: number }>;
};
type AttendanceEntry = { classDate: string; period: number; courseCode: string; courseTitle: string; status: string };
type LeaveRequest = { id: number; type: string; startDate: string; startPeriod: number; endDate: string; endPeriod: number; status: string };
type AuditEntry = { classDate: string; period: number; courseCode: string; eventType: string; oldStatus: string | null; newStatus: string | null; actorEmail: string | null; reason: string; recordedAt: string };
type CompassStep = "standing" | "target" | "plan";
type LocationState = "idle" | "checking" | "ready" | "attention";

function AttendanceSparkline({ records }: { records: AttendanceEntry[] }) {
  const recent = records.slice(0, 8).reverse();
  if (recent.length === 0) return <p className="sparkline-empty">Trend appears after a class is recorded.</p>;
  const points = recent.map((entry, index) => ({
    x: recent.length === 1 ? 60 : 4 + index * (112 / (recent.length - 1)),
    y: ["PRESENT", "LATE", "ON_DUTY"].includes(entry.status) ? 7 : 25,
    status: entry.status,
  }));
  const path = points.map((point, index) => `${index === 0 ? "M" : "L"}${point.x},${point.y}`).join(" ");
  return (
    <div className="course-trend">
      <span>{recent.length} recent {recent.length === 1 ? "class" : "classes"}</span>
      <svg viewBox="0 0 120 32" role="img" aria-label={`Attendance trend for ${recent.length} recent classes`}>
        <path d={path} fill="none" stroke="#39775b" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" />
        {points.map((point, index) => <circle key={`${point.x}-${index}`} cx={point.x} cy={point.y} r="3.3" className={point.y === 25 ? "sparkline-absent" : "sparkline-attended"}>
          <title>{point.status.replace("_", " ")}</title>
        </circle>)}
      </svg>
    </div>
  );
}

export default function StudentPage() {
  const router = useRouter();
  const [dashboard, setDashboard] = useState<Dashboard | null>(null);
  const [history, setHistory] = useState<AttendanceEntry[]>([]);
  const [audit, setAudit] = useState<AuditEntry[]>([]);
  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [courseFilter, setCourseFilter] = useState("");
  const [compassStep, setCompassStep] = useState<CompassStep>("standing");
  const [locationState, setLocationState] = useState<LocationState>("idle");
  const [locationAccuracy, setLocationAccuracy] = useState<number | null>(null);
  const [token, setToken] = useState("");
  const [request, setRequest] = useState({ type: "ON_DUTY", startDate: "", startPeriod: 1, endDate: "", endPeriod: 1, reason: "", proofMetadata: "" });
  const [message, setMessage] = useState("");
  const [busy, setBusy] = useState(false);
  const [scannerOpen, setScannerOpen] = useState(false);
  const [presentationMode, setPresentationMode] = useState(false);

  useEffect(() => onAuthStateChanged(auth, async (user) => {
    if (!user) { router.replace("/"); return; }
    try {
      const [summary, records, leaveRequests] = await Promise.all([
        api<Dashboard>("/student/dashboard"),
        api<AttendanceEntry[]>("/student/history"),
        api<LeaveRequest[]>("/student/requests/od"),
      ]);
      setDashboard(summary);
      setHistory(records);
      setRequests(leaveRequests);
      setAudit(await api<AuditEntry[]>("/student/audit"));
    }
    catch (error) { setMessage(error instanceof Error ? error.message : "Unable to load attendance."); }
  }), [router]);

  useEffect(() => {
    if (!scannerOpen) return;
    const scanner = new Html5QrcodeScanner("qr-reader", { fps: 8, qrbox: { width: 220, height: 220 } }, false);
    scanner.render((decoded) => {
      setToken(decoded);
      setScannerOpen(false);
      void scanner.clear();
    }, () => {});
    return () => { void scanner.clear(); };
  }, [scannerOpen]);

  async function checkIn(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setLocationState("checking");
    setLocationAccuracy(null);
    setMessage("Requesting a precise location fix…");
    try {
      if (!navigator.geolocation) throw new Error("Location services are not available in this browser.");
      const position = await new Promise<GeolocationPosition>((resolve, reject) =>
        navigator.geolocation.getCurrentPosition(resolve, reject, { enableHighAccuracy: true, timeout: 15000, maximumAge: 0 }));
      setLocationAccuracy(position.coords.accuracy);
      setLocationState("ready");
      const result = await api<{ status: string; alreadyRecorded: boolean; distanceMeters: number }>("/student/scan", {
        method: "POST",
        body: JSON.stringify({ token, latitude: position.coords.latitude, longitude: position.coords.longitude, accuracyMeters: position.coords.accuracy }),
      });
      setMessage(`${result.alreadyRecorded ? "Already recorded" : "Check-in complete"}: ${result.status} · ${result.distanceMeters} m from class.`);
      setToken("");
      const [records, auditRecords] = await Promise.all([
        api<AttendanceEntry[]>("/student/history"),
        api<AuditEntry[]>("/student/audit"),
      ]);
      setHistory(records);
      setAudit(auditRecords);
    } catch (error) {
      setLocationState("attention");
      setMessage(error instanceof Error ? error.message : "Check-in failed.");
    } finally { setBusy(false); }
  }

  async function submitOd(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    try {
      await api("/student/requests/od", { method: "POST", body: JSON.stringify(request) });
      setMessage("Your request has been submitted for staff review.");
      setRequest({ ...request, reason: "", proofMetadata: "" });
      setRequests(await api<LeaveRequest[]>("/student/requests/od"));
    } catch (error) { setMessage(error instanceof Error ? error.message : "Request could not be submitted."); }
    finally { setBusy(false); }
  }

  if (!dashboard) return <main className="portal-bg"><div className="loading glass-panel">Connecting to your attendance workspace…</div></main>;
  const percent = Math.max(0, Math.min(100, dashboard.overallPercentage));
  const visibleCourses = dashboard.courses.filter((course) => !courseFilter || course.courseCode === courseFilter);
  const visibleHistory = history.filter((entry) => !courseFilter || entry.courseCode === courseFilter);
  const compassCopy = {
    standing: `You have attended ${dashboard.classesAttended} of ${dashboard.classesHeld} recorded classes. Your current standing is ${percent.toFixed(1)}%.`,
    target: "The exam eligibility target is 75%. The 65%–74.9% range is the condonation zone; below 65% is a detained-risk standing.",
    plan: dashboard.classesNeeded > 0
      ? `Attend ${dashboard.classesNeeded} consecutive classes to reach 75%. You can safely miss ${dashboard.safeBunksRemaining} more classes at your current standing.`
      : `You are at or above the 75% target, with ${dashboard.safeBunksRemaining} safe skips remaining.`,
  } satisfies Record<CompassStep, string>;
  return (
    <main className="portal-bg">
      <WorkspaceHeader role="STUDENT" name={dashboard.student.fullName} email={dashboard.student.email}
        facts={[
          { label: "Year of joining", value: String(dashboard.student.yearOfJoining) },
          { label: "Department", value: dashboard.student.emailDepartment.toUpperCase() },
          { label: "Program", value: dashboard.student.program },
          { label: "Semester", value: String(dashboard.student.semester) },
          { label: "Register number", value: dashboard.student.registerNumber },
        ]}
        presentationMode={presentationMode} onTogglePresentation={() => setPresentationMode((current) => !current)}
        onSignOut={() => void signOut(auth)} />
      <section className="portal-content">
        <div className="page-intro" id="overview"><div><p className="eyebrow">Student workspace</p><h1>{presentationMode ? "Your attendance, in focus." : `Good to see you, ${dashboard.student.fullName.split(" ")[0]}.`}</h1><p>Your attendance at a glance, with the next step already clear.</p></div><span className={`status-pill ${dashboard.eligibility.toLowerCase()}`}>{dashboard.eligibility.replace("_", " ")}</span></div>
        <div className="student-grid">
          <section className="glass-panel compass-card" aria-labelledby="compass-title">
            <div className="compass-copy">
              <p className="eyebrow">Your attendance compass</p>
              <h2 id="compass-title">Eligibility &amp; Safe-Skip Compass</h2>
              <p>Move through your current standing, the eligibility thresholds, and a practical plan for your next classes.</p>
              <div className="compass-steps" role="tablist" aria-label="Attendance compass steps">
                {([
                  ["standing", "01", "Standing"],
                  ["target", "02", "Thresholds"],
                  ["plan", "03", "Your plan"],
                ] as const).map(([step, number, label]) => (
                  <button key={step} type="button" className="compass-step" role="tab"
                    aria-selected={compassStep === step} aria-controls="compass-readout"
                    onClick={() => setCompassStep(step)}>
                    <span>{number}</span>{label}
                  </button>
                ))}
              </div>
              <div className="compass-readout" id="compass-readout" role="tabpanel" aria-live="polite">
                <strong>{compassStep === "standing" ? `${percent.toFixed(1)}%` : compassStep === "target" ? "75%" : dashboard.classesNeeded > 0 ? dashboard.classesNeeded : dashboard.safeBunksRemaining}</strong>
                <span>{compassCopy[compassStep]}</span>
              </div>
            </div>
            <div className="compass-visual" aria-label={`Attendance at ${percent.toFixed(1)} percent`}>
              <div className="compass-orbit" aria-hidden="true" />
              <div className="compass-needle" style={{ "--progress": `${percent}%` } as React.CSSProperties}>
                <div><strong>{percent.toFixed(1)}%</strong><span>current attendance</span></div>
              </div>
            </div>
          </section>
          <section className="glass-panel checkin-card" id="check-in"><p className="eyebrow">Live · campus geofence</p><h2>Check in for class</h2><p>Scan the current class QR. The server verifies your location, accuracy, enrollment, and session.</p>
            <div className="location-status" data-state={locationState} aria-live="polite">
              <span><i className="location-dot" />Location {locationState === "checking" ? "in progress" : locationState === "ready" ? "measured" : locationState === "attention" ? "needs attention" : "not requested"}</span>
              <strong>{locationAccuracy === null ? "High accuracy required" : `±${Math.round(locationAccuracy)} m`}</strong>
            </div>
            <form onSubmit={checkIn}><label className="field-label" htmlFor="qr-token">QR session token</label><input id="qr-token" value={token} required onChange={(event) => setToken(event.target.value)} placeholder="Scan the staff display…" />
              <div className="button-row"><button type="button" className="secondary-button" onClick={() => setScannerOpen(!scannerOpen)}>Scan QR with camera</button><button className="primary-button" disabled={busy}>{busy ? "Verifying location…" : "Check in"}</button></div>
            </form>{scannerOpen && <div className="scanner-frame"><div id="qr-reader" /></div>}
          </section>
        </div>
        {message && <div className="notice glass-panel" role="status">{message}</div>}
        <section className="section-block" id="courses"><div className="section-heading"><div><p className="eyebrow">Course progress</p><h2>Attendance by subject</h2></div><label className="field-label history-filter" htmlFor="course-filter">Show course<select id="course-filter" className="filter-select" value={courseFilter} onChange={(event) => setCourseFilter(event.target.value)}><option value="">All courses</option>{dashboard.courses.map((course) => <option key={course.courseCode} value={course.courseCode}>{course.courseCode}</option>)}</select></label></div>
          <div className="course-grid">{visibleCourses.map((course) => <article className="glass-panel course-card" key={course.courseCode}><div className="course-top"><span className="course-code">{course.courseCode}</span><span className={`status-pill ${course.eligibility.toLowerCase()}`}>{course.eligibility.replace("_", " ")}</span></div><h3>{course.courseTitle}</h3><div className="course-meter"><span style={{ width: `${Math.min(course.percentage, 100)}%` }} /></div><div className="course-foot"><b>{course.percentage.toFixed(1)}%</b><span>{course.classesNeeded} classes to 75% · {course.safeBunksRemaining} safe skips</span></div><AttendanceSparkline records={history.filter((entry) => entry.courseCode === course.courseCode)} /></article>)}{visibleCourses.length === 0 && <p className="empty-state">No course attendance matches this filter.</p>}</div>
        </section>
        <section className="section-block" id="history">
          <div className="section-heading"><div><p className="eyebrow">Personal record</p><h2>Attendance history</h2><p>Recorded classes, scoped to your account.</p></div></div>
          <div className="glass-panel history-list">{visibleHistory.slice(0, 30).map((entry, index) => <div className="history-row" key={`${entry.classDate}-${entry.period}-${entry.courseCode}-${index}`}><div><b>{entry.courseCode} · {entry.courseTitle}</b><span>{entry.classDate} · Period {entry.period}</span></div><span className={`status-pill ${entry.status.toLowerCase()}`}>{entry.status.replace("_", " ")}</span></div>)}{visibleHistory.length === 0 && <p className="empty-state">{history.length === 0 ? "No attendance records yet." : "No attendance records match this course filter."}</p>}</div>
        </section>
        <section className="section-block drawer-grid" id="requests" aria-label="Requests and audit">
          <details className="glass-panel drawer">
            <summary><span><span className="eyebrow">Request consideration</span><br />On-duty or medical leave</span></summary>
            <div className="drawer-body">
              <p className="muted">Submit dates, periods, and a reason for staff review. Proof is stored as metadata only.</p>
              <form className="od-form" onSubmit={submitOd}>
                <label className="field-label">Request type<select value={request.type} onChange={(event) => setRequest({ ...request, type: event.target.value })}><option value="ON_DUTY">On-duty</option><option value="MEDICAL">Medical leave</option></select></label>
                <label className="field-label">Start date<input type="date" required value={request.startDate} onChange={(event) => setRequest({ ...request, startDate: event.target.value })} /></label>
                <label className="field-label">Start period<input type="number" min="1" max="12" required value={request.startPeriod} onChange={(event) => setRequest({ ...request, startPeriod: Number(event.target.value) })} /></label>
                <label className="field-label">End date<input type="date" required value={request.endDate} onChange={(event) => setRequest({ ...request, endDate: event.target.value })} /></label>
                <label className="field-label">End period<input type="number" min="1" max="12" required value={request.endPeriod} onChange={(event) => setRequest({ ...request, endPeriod: Number(event.target.value) })} /></label>
                <label className="field-label">Reason<textarea required maxLength={1000} value={request.reason} onChange={(event) => setRequest({ ...request, reason: event.target.value })} /></label>
                <label className="field-label">Proof reference (optional)<input maxLength={1000} value={request.proofMetadata} onChange={(event) => setRequest({ ...request, proofMetadata: event.target.value })} placeholder="Document reference, not a public URL" /></label>
                <button className="primary-button" disabled={busy}>{busy ? "Submitting…" : "Submit for review"}</button>
              </form>
            </div>
          </details>
          <details className="glass-panel drawer">
            <summary><span><span className="eyebrow">Request status</span><br />My OD &amp; medical requests <span className="status-pill">{requests.length}</span></span></summary>
            <div className="drawer-body">
              {requests.map((item) => <div className="history-row" key={item.id}><div><b>{item.type.replace("_", " ")} · {item.startDate} – {item.endDate}</b><span>Periods {item.startPeriod}–{item.endPeriod}</span></div><span className="status-pill">{item.status}</span></div>)}
              {requests.length === 0 && <p className="empty-state">No requests submitted.</p>}
            </div>
          </details>
          <details className="glass-panel drawer">
            <summary><span><span className="eyebrow">Immutable record</span><br />Attendance audit log <span className="status-pill">{audit.length}</span></span></summary>
            <div className="drawer-body">
              {audit.map((entry, index) => <div className="audit-row" key={`${entry.classDate}-${entry.period}-${entry.courseCode}-${entry.recordedAt}-${index}`}>
                <div><strong>{entry.courseCode} · {entry.oldStatus ?? "—"} → {entry.newStatus ?? "—"}</strong><span>{entry.classDate} · Period {entry.period} · {presentationMode ? "Details hidden in presentation view." : entry.reason}</span><span>{presentationMode ? "Actor details hidden" : entry.actorEmail ? `Recorded by ${entry.actorEmail}` : "System recorded"}</span></div>
                <time dateTime={entry.recordedAt}>{new Date(entry.recordedAt).toLocaleString()}</time>
              </div>)}
              {audit.length === 0 && <p className="empty-state">No audit entries yet.</p>}
            </div>
          </details>
        </section>
      </section>
    </main>
  );
}
