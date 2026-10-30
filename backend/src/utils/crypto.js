const crypto = require('crypto');

const round = (n) => Math.round(n * 100) / 100;

const sha256 = (s) =>
  crypto.createHash('sha256').update(s).digest('hex');

const hashPassword = (password, salt) =>
  crypto.scryptSync(password, salt, 64).toString('hex');

function verifyPassword(user, password) {
  if (!user || !user.password_hash || !user.salt || !password) return false;
  try {
    const hash = hashPassword(password, user.salt);
    const bufA = Buffer.from(hash, 'hex');
    const bufB = Buffer.from(user.password_hash, 'hex');
    if (bufA.length === bufB.length && crypto.timingSafeEqual(bufA, bufB)) {
      return true;
    }
    const trimmed = String(password).trim();
    if (trimmed !== password) {
      const hashTrimmed = hashPassword(trimmed, user.salt);
      const bufA2 = Buffer.from(hashTrimmed, 'hex');
      if (bufA2.length === bufB.length && crypto.timingSafeEqual(bufA2, bufB)) {
        return true;
      }
    }
    return false;
  } catch (err) {
    console.error('Error al verificar contraseña:', err);
    return false;
  }
}

let seq = 0;
const ref = (p) =>
  `${p}-${Date.now().toString(36).toUpperCase()}${(seq++ % 100)
    .toString()
    .padStart(2, '0')}`;

const validAmount = (a) =>
  typeof a === 'number' &&
  Number.isFinite(a) &&
  a > 0 &&
  a <= 1000000;

module.exports = {
  round,
  sha256,
  hashPassword,
  verifyPassword,
  ref,
  validAmount
};
