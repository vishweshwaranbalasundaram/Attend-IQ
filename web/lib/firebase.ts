"use client";

import { getApp, getApps, initializeApp } from "firebase/app";
import { connectAuthEmulator, getAuth } from "firebase/auth";
import { connectStorageEmulator, getStorage } from "firebase/storage";

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY ?? "demo-api-key",
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN ?? "localhost",
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID ?? "demo-attendiq",
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID ?? "demo-app-id",
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET ?? "demo-attendiq.appspot.com",
};

const app = getApps().length ? getApp() : initializeApp(firebaseConfig);
export const auth = getAuth(app);
export const storage = getStorage(app);

const emulatorUrl = process.env.NEXT_PUBLIC_FIREBASE_AUTH_EMULATOR_HOST;
if (emulatorUrl && typeof window !== "undefined" && !(auth as typeof auth & { _attendiqEmulatorConnected?: boolean })._attendiqEmulatorConnected) {
  connectAuthEmulator(auth, emulatorUrl, { disableWarnings: true });
  (auth as typeof auth & { _attendiqEmulatorConnected?: boolean })._attendiqEmulatorConnected = true;
}

const storageEmulatorUrl = process.env.NEXT_PUBLIC_FIREBASE_STORAGE_EMULATOR_HOST;
if (storageEmulatorUrl && typeof window !== "undefined" && !(storage as typeof storage & { _attendiqEmulatorConnected?: boolean })._attendiqEmulatorConnected) {
  const endpoint = new URL(storageEmulatorUrl);
  connectStorageEmulator(storage, endpoint.hostname, Number(endpoint.port || 9199));
  (storage as typeof storage & { _attendiqEmulatorConnected?: boolean })._attendiqEmulatorConnected = true;
}
