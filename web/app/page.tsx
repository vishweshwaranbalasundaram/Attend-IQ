"use client";

import { FormEvent, useMemo, useState } from "react";
import { GoogleAuthProvider, createUserWithEmailAndPassword, sendEmailVerification, sendPasswordResetEmail, signInWithEmailAndPassword, signInWithPopup, signOut } from "firebase/auth";
import { useRouter } from "next/navigation";
import { auth } from "@/lib/firebase";
import { emailMatchesRole, PortalRole, roleHint } from "@/lib/identity";

const DOMAIN = process.env.NEXT_PUBLIC_ALLOWED_EMAIL_DOMAIN ?? "example.edu";

export default function Home() {
  const router = useRouter();
  const [role, setRole] = useState<PortalRole>("STUDENT");
  const [tourStep, setTourStep] = useState(0);
  const [registering, setRegistering] = useState(false);
  const [recovering, setRecovering] = useState(false);
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const formatValid = useMemo(() => emailMatchesRole(email, role, DOMAIN), [email, role]);
  const identityPreview = useMemo(() => {
    if (!email.trim()) return "Your institutional identity preview appears here.";
    if (!formatValid) return "This address does not match the selected role format yet.";
    const localPart = email.trim().toLowerCase().split("@")[0];
    const parts = localPart.split(".");
    return role === "STAFF"
      ? `Staff identity · ${parts[0]}.${parts[1]}`
      : `Student identity · joining ${parts[2]} · ${parts[3].toUpperCase()}`;
  }, [email, formatValid, role]);
  const passwordResetConfirmation = process.env.NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_HOST
    ? "If an account exists, find its reset link in the output of `docker compose logs --tail=100 auth-emulator`."
    : "If an account exists for this address, a password reset link has been sent to its inbox.";
  const tourStops = [
    { number: "01", label: "Eligibility", title: "Know where you stand.", description: "A live attendance compass turns your real course records into eligibility and safe-skip guidance." },
    { number: "02", label: "Check-in", title: "A check-in with guardrails.", description: "Short-lived QR codes work alongside server-verified campus location and GPS accuracy checks." },
    { number: "03", label: "Staff review", title: "Every decision stays reviewable.", description: "Staff can manage sessions, review requests, and follow attendance changes in an audit trail." },
  ];

  async function finishSignIn() {
    if (!auth.currentUser) throw new Error("Authentication did not complete.");
    if (!auth.currentUser.emailVerified) {
      await sendEmailVerification(auth.currentUser);
      await signOut(auth);
      throw new Error(process.env.NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_HOST
        ? "The local Firebase Auth Emulator does not send email. Run `docker compose logs --tail=100 auth-emulator` in the Attend-IQ folder and open the latest verification link for this email."
        : "A verification email was sent. Open its link, then sign in again.");
    }
    const token = await auth.currentUser.getIdToken(true);
    const response = await fetch(`${process.env.NEXT_PUBLIC_API_BASE_URL ?? "/api"}/auth/me`, {
      headers: { Authorization: `Bearer ${token}` },
      cache: "no-store",
    });
    if (!response.ok) {
      await signOut(auth);
      const body = await response.json().catch(() => null) as { message?: string } | null;
      throw new Error(body?.message ?? "This verified email is not assigned to an Attend-IQ account.");
    }
    const account = await response.json() as { role: PortalRole };
    router.push(account.role === "STAFF" ? "/staff" : "/student");
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setMessage("");
    if (!formatValid) {
      setError("Enter an email that matches the selected institutional account format.");
      return;
    }
    setBusy(true);
    try {
      if (recovering) {
        await sendPasswordResetEmail(auth, email.trim().toLowerCase());
        setMessage(passwordResetConfirmation);
      } else if (registering) {
        const credential = await createUserWithEmailAndPassword(auth, email.trim().toLowerCase(), password);
        await sendEmailVerification(credential.user);
        await signOut(auth);
        setMessage(process.env.NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_HOST
          ? "Account created. Run `docker compose logs --tail=100 auth-emulator` in the Attend-IQ folder and open the verification link for this email."
          : "Account created. Open the verification link in your institutional inbox before signing in.");
      } else {
        await signInWithEmailAndPassword(auth, email.trim().toLowerCase(), password);
        await finishSignIn();
      }
    } catch (reason) {
      if (recovering && typeof reason === "object" && reason !== null
          && "code" in reason && reason.code === "auth/user-not-found") {
        setMessage(passwordResetConfirmation);
      } else {
        setError(reason instanceof Error ? reason.message : "Sign-in failed. Please try again.");
      }
    } finally {
      setBusy(false);
    }
  }

  async function googleSignIn() {
    setError("");
    setMessage("");
    setBusy(true);
    try {
      const credential = await signInWithPopup(auth, new GoogleAuthProvider());
      const providerEmail = credential.user.email ?? "";
      if (!emailMatchesRole(providerEmail, role, DOMAIN)) {
        await signOut(auth);
        throw new Error("Choose a verified institutional account matching the selected portal.");
      }
      await finishSignIn();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : "Google sign-in failed.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <main className="auth-page">
      <div className="mesh mesh-one" />
      <div className="mesh mesh-two" />
      <section className="auth-layout auth-centered">
        <div className="auth-context">
          <a className="brand" href="/" aria-label="Attend-IQ home"><span className="brand-icon">IQ</span><span>Attend<span className="brand-accent">-IQ</span></span></a>
          <p className="eyebrow">A clearer campus day</p>
          <h1>Know where you stand.<br /><span>Choose your next step.</span></h1>
          <p>One trusted place for attendance, eligibility, and every class that counts.</p>
          <section className="showcase-tour" aria-label="Attend-IQ product tour">
            <div className="campus-diorama" aria-hidden="true">
              <svg viewBox="0 0 260 170" role="presentation">
                <ellipse cx="130" cy="146" rx="104" ry="15" fill="#315c4d" opacity=".16" />
                <path d="m33 104 96-42 99 37-99 44z" fill="#b8cdb5" />
                <path d="m57 99 71-32 73 28-72 33z" fill="#e7e5d3" />
                <path d="m57 99 72 29v-58z" fill="#c9d4c2" />
                <path d="m129 70 72 25-72 33z" fill="#9db8a6" />
                <path d="m78 91 51-23 51 18-51 24z" fill="#567c67" />
                <path d="m78 91 51 19v-42z" fill="#739782" />
                <path d="m129 68 51 18-51 24z" fill="#416651" />
                <path d="m91 87 38-17 38 14-38 18z" fill="#dce8d7" />
                <path d="m91 87 38 15V70z" fill="#b6cbbb" />
                <path d="m129 70 38 14-38 18z" fill="#8ba997" />
                <path d="M103 89v-9l9-4v9zm17 7v-9l8-4v9zm17-9v-9l8 3v9zm17 7v-9l8 3v9z" fill="#f4efd9" opacity=".88" />
                <path d="M45 113 27 121l18 7 18-8zm154 1-19 8 19 7 19-9z" fill="#50775b" />
                <circle cx="41" cy="111" r="10" fill="#78966a" /><circle cx="215" cy="111" r="11" fill="#78966a" />
                <path d="m110 140 19-8 20 8-20 8z" fill="#e9dfc8" />
                <path d="m125 139 4-2 4 2-4 2z" fill="#9c835b" />
              </svg>
              <span className="diorama-label">A campus day, in perspective</span>
            </div>
            <div className="tour-content">
              <div className="tour-heading"><span className="eyebrow">A 30-second tour · {tourStops[tourStep].number} / 03</span><span className="tour-orbit" aria-hidden="true" /></div>
              <h2>{tourStops[tourStep].title}</h2>
              <p>{tourStops[tourStep].description}</p>
              <div className="tour-steps" role="tablist" aria-label="Product tour chapters">
                {tourStops.map((stop, index) => (
                  <button type="button" role="tab" aria-selected={tourStep === index} aria-label={`Tour ${stop.number}: ${stop.label}`}
                    key={stop.number} onClick={() => setTourStep(index)}>
                    <span>{stop.number}</span>{stop.label}
                  </button>
                ))}
              </div>
            </div>
          </section>
        </div>
        <div className="login-wrap">
          <div className="login-card glass-clear">
            <div className="login-heading">
              <p className="eyebrow">Welcome to Attend-IQ</p>
              <h2>{recovering ? "Reset your password" : registering ? "Create your account" : "Sign in to continue"}</h2>
              <p>{recovering ? "We’ll send a secure password reset link for your institutional account." : "Use your verified institutional identity to open your workspace."}</p>
            </div>
            <div className="role-switch" role="tablist" aria-label="Account type">
              {(["STUDENT", "STAFF"] as const).map((value) => (
                <button key={value} role="tab" aria-selected={role === value} onClick={() => { setRole(value); setError(""); }}>
                  {value === "STUDENT" ? "Student" : "Staff"}
                </button>
              ))}
            </div>
            <form onSubmit={submit}>
              <label className="field-label" htmlFor="email">Institutional email</label>
              <input id="email" type="email" autoComplete="email" required value={email}
                onChange={(event) => setEmail(event.target.value)} placeholder={role === "STAFF" ? `k.arun@${DOMAIN}` : `name.i.2025.cse@${DOMAIN}`} />
              <p className={`field-hint ${email ? (formatValid ? "valid" : "invalid") : ""}`}>{roleHint(role, DOMAIN)}</p>
              <div className={`identity-preview ${email ? (formatValid ? "valid" : "invalid") : ""}`} aria-live="polite">
                <span>Identity preview</span><strong>{identityPreview}</strong>
              </div>
              {!recovering && <>
                <label className="field-label" htmlFor="password">Password</label>
                <input id="password" type="password" autoComplete={registering ? "new-password" : "current-password"}
                  minLength={8} required value={password} onChange={(event) => setPassword(event.target.value)} placeholder="At least 8 characters" />
              </>}
              {message && <div className="notice success" role="status">{message}</div>}
              {error && <div className="notice error" role="alert">{error}</div>}
              <button className="primary-button" type="submit" disabled={busy || (email.length > 0 && !formatValid)}>
                {busy ? "Please wait…" : recovering ? "Send reset link" : registering ? "Create account & verify email" : "Continue with email"}
              </button>
            </form>
            {!registering && !recovering && <p className="switch-mode">
              <button className="quiet-button" type="button" onClick={() => { setRecovering(true); setPassword(""); setError(""); setMessage(""); }}>
                Forgot password?
              </button>
            </p>}
            {!recovering && <>
              <div className="divider"><span>or</span></div>
              <button className="secondary-button" onClick={googleSignIn} disabled={busy}>Continue with Google Workspace</button>
            </>}
            <p className="switch-mode">
              {recovering ? "Remembered your password?" : registering ? "Already verified?" : "New to Attend-IQ?"}{" "}
              <button type="button" onClick={() => {
                if (recovering) {
                  setRecovering(false);
                } else {
                  setRegistering(!registering);
                }
                setError("");
                setMessage("");
              }}>
                {recovering ? "Back to sign in" : registering ? "Sign in" : "Create account"}
              </button>
            </p>
            <p className="privacy-note">You must use a verified institutional email. Access roles and data scope are enforced by the server.</p>
          </div>
        </div>
      </section>
    </main>
  );
}
