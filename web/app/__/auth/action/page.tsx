"use client";

import { useEffect, useState } from "react";
import { applyActionCode } from "firebase/auth";
import { auth } from "@/lib/firebase";

export default function FirebaseAuthActionPage() {
  const [message, setMessage] = useState("Verifying your email link…");
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.get("mode") !== "verifyEmail" || !params.get("oobCode")) {
      setFailed(true);
      setMessage("This verification link is invalid or incomplete.");
      return;
    }
    void applyActionCode(auth, params.get("oobCode")!)
      .then(() => setMessage("Email verified. You can now sign in to Attend-IQ."))
      .catch(() => {
        setFailed(true);
        setMessage("This verification link has expired or was already used. Request a new one from sign-in.");
      });
  }, []);

  return <main className="portal-bg"><section className="glass-panel action-card"><span className="brand-icon">IQ</span><p className="eyebrow">Attend-IQ identity</p><h1>{failed ? "Verification incomplete" : "Email verification"}</h1><p>{message}</p><a className="primary-button action-link" href="/">Return to sign in</a></section></main>;
}
