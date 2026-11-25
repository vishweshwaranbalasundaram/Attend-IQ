import { beforeUserCreated, beforeUserSignedIn } from "firebase-functions/v2/identity";
import { HttpsError } from "firebase-functions/v2/https";

type InstitutionalIdentity = {
  role: "STAFF" | "STUDENT";
  yearOfJoining?: number;
  department?: string;
};

const domain = (process.env.ALLOWED_EMAIL_DOMAIN ?? "example.edu").trim().toLowerCase();
const escapedDomain = domain.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const staffPattern = new RegExp(`^[a-z]{1,4}\\.[a-z]+@${escapedDomain}$`);
const studentPattern = new RegExp(`^([a-z]+)\\.([a-z]{1,4})\\.(20\\d{2})\\.([a-z]{2,10})@${escapedDomain}$`);

function identify(email: string | undefined): InstitutionalIdentity {
  const normalized = email?.trim().toLowerCase();
  if (!normalized || normalized.length > 254) {
    throw new HttpsError("permission-denied", "A valid institutional email is required.");
  }
  if (staffPattern.test(normalized)) return { role: "STAFF" };
  const student = studentPattern.exec(normalized);
  if (student) {
    return {
      role: "STUDENT",
      yearOfJoining: Number(student[3]),
      department: student[4],
    };
  }
  throw new HttpsError("permission-denied", "Use the exact institutional student or staff email format.");
}

export const enforceInstitutionalAccountCreation = beforeUserCreated((event) => {
  const user = event.data;
  if (!user) throw new HttpsError("internal", "Firebase user data was not provided.");
  const identity = identify(user.email);
  const passwordProvider = user.providerData.some((provider) => provider.providerId === "password");
  if (!user.emailVerified && !passwordProvider) {
    throw new HttpsError("permission-denied", "Verify your institutional email before creating an account.");
  }
  return {
    customClaims: {
      attendiqEmailRole: identity.role,
      ...(identity.yearOfJoining ? { yearOfJoining: identity.yearOfJoining } : {}),
      ...(identity.department ? { department: identity.department } : {}),
    },
  };
});

export const enforceVerifiedInstitutionalSignIn = beforeUserSignedIn((event) => {
  const user = event.data;
  if (!user) throw new HttpsError("internal", "Firebase user data was not provided.");
  const identity = identify(user.email);
  if (!user.emailVerified) {
    throw new HttpsError("permission-denied", "Verify your institutional email before signing in.");
  }
  return {
    customClaims: {
      attendiqEmailRole: identity.role,
      ...(identity.yearOfJoining ? { yearOfJoining: identity.yearOfJoining } : {}),
      ...(identity.department ? { department: identity.department } : {}),
    },
  };
});
