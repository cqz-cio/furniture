export const MAX_EMAIL_LENGTH = 255;
export const MIN_PASSWORD_LENGTH = 4;
export const MAX_PASSWORD_LENGTH = 16;

export const isEmailAddress = (value) => {
  const email = String(value || "").trim();
  return email.length <= MAX_EMAIL_LENGTH && /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email);
};

export const isPasswordInRange = (value) => {
  const password = String(value || "");
  return password.length >= MIN_PASSWORD_LENGTH && password.length <= MAX_PASSWORD_LENGTH;
};

export const isSixDigitCode = (value) => /^\d{6}$/.test(String(value || "").trim());
