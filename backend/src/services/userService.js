const crypto = require('crypto');
const { q } = require('../database/db');
const { hashPassword, sha256 } = require('../utils/crypto');

const SESSION_TTL_MS = 24 * 60 * 60 * 1000;

function newAccount() {
  for (;;) {
    const account = 'ACC-' + (1000 + crypto.randomInt(9000));
    if (!q.userByAccount.get(account)) {
      return account;
    }
  }
}

function createUser(name, email, password, pin = null, balance = 0, account = newAccount()) {
  const salt = crypto.randomBytes(16).toString('hex');
  const passwordHash = hashPassword(password, salt);

  let pinSalt = null;
  let pinHash = null;

  if (pin && /^\d{4}$/.test(pin)) {
    pinSalt = crypto.randomBytes(16).toString('hex');
    pinHash = hashPassword(pin, pinSalt);
  }

  q.insertUser.run(
    name,
    email,
    salt,
    passwordHash,
    pinSalt,
    pinHash,
    account,
    balance,
    Date.now()
  );

  return q.userByEmail.get(email);
}

function setUserPin(userId, pin) {
  const pinSalt = crypto.randomBytes(16).toString('hex');
  const pinHash = hashPassword(pin, pinSalt);
  q.setPin.run(pinSalt, pinHash, userId);
}

function removeUserPin(userId) {
  q.removePin.run(userId);
}

function openSession(user) {
  const token = crypto.randomBytes(32).toString('hex');
  q.insertSession.run(sha256(token), user.id, Date.now());
  return token;
}

function activeUser(token) {
  if (!token) return null;
  return q.userByToken.get(sha256(token), Date.now() - SESSION_TTL_MS);
}

const publicUser = (u) => ({
  name: u.name,
  email: u.email,
  account: u.account,
  balance: u.balance,
  hasPin: Boolean(u.pin_hash && u.pin_salt)
});

module.exports = {
  createUser,
  setUserPin,
  removeUserPin,
  openSession,
  activeUser,
  publicUser,
  SESSION_TTL_MS
};
