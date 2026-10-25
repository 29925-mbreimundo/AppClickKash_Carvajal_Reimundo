const { DatabaseSync } = require('node:sqlite');
const path = require('path');

// Maintain the database file in backend/wallet.db
const dbPath = path.join(__dirname, '../../wallet.db');
const db = new DatabaseSync(dbPath);

db.exec(`
  PRAGMA journal_mode = WAL;

  CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    salt TEXT NOT NULL,
    password_hash TEXT NOT NULL,
    pin_salt TEXT,
    pin_hash TEXT,
    account TEXT NOT NULL UNIQUE,
    balance REAL NOT NULL DEFAULT 0,
    created_at INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS sessions (
    token_hash TEXT PRIMARY KEY,
    user_id INTEGER NOT NULL REFERENCES users(id),
    created_at INTEGER NOT NULL
  );

  CREATE TABLE IF NOT EXISTS movements (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    user_id INTEGER NOT NULL REFERENCES users(id),
    type TEXT NOT NULL,
    amount REAL NOT NULL,
    counterpart TEXT,
    reference TEXT,
    created_at INTEGER NOT NULL
  );
`);

try {
  db.exec('ALTER TABLE users ADD COLUMN pin_salt TEXT;');
} catch (_) {}

try {
  db.exec('ALTER TABLE users ADD COLUMN pin_hash TEXT;');
} catch (_) {}

const q = {
  userByEmail: db.prepare(
    'SELECT * FROM users WHERE email = ?'
  ),

  userByAccount: db.prepare(
    'SELECT * FROM users WHERE UPPER(account) = UPPER(?)'
  ),

  userByEmailOrAccount: db.prepare(
    'SELECT * FROM users WHERE LOWER(email) = ? OR UPPER(account) = ?'
  ),

  userById: db.prepare(
    'SELECT * FROM users WHERE id = ?'
  ),

  insertUser: db.prepare(`
    INSERT INTO users
    (name,email,salt,password_hash,pin_salt,pin_hash,
     account,balance,created_at)
    VALUES (?,?,?,?,?,?,?,?,?)
  `),

  setBalance: db.prepare(
    'UPDATE users SET balance = ? WHERE id = ?'
  ),

  setPin: db.prepare(
    'UPDATE users SET pin_salt = ?, pin_hash = ? WHERE id = ?'
  ),

  removePin: db.prepare(
    'UPDATE users SET pin_salt = NULL, pin_hash = NULL WHERE id = ?'
  ),

  insertSession: db.prepare(`
    INSERT INTO sessions
    (token_hash,user_id,created_at)
    VALUES (?,?,?)
  `),

  userByToken: db.prepare(`
    SELECT u.*
    FROM sessions s
    JOIN users u ON u.id = s.user_id
    WHERE s.token_hash = ?
    AND s.created_at > ?
  `),

  deleteSession: db.prepare(
    'DELETE FROM sessions WHERE token_hash = ?'
  ),

  activeSessionsByUser: db.prepare(`
    SELECT token_hash FROM sessions
    WHERE user_id = ?
    AND created_at > ?
  `),

  deleteSessionsByUser: db.prepare(
    'DELETE FROM sessions WHERE user_id = ?'
  ),

  deleteExpiredSessions: db.prepare(
    'DELETE FROM sessions WHERE created_at <= ?'
  ),

  insertMovement: db.prepare(`
    INSERT INTO movements
    (user_id,type,amount,counterpart,reference,created_at)
    VALUES (?,?,?,?,?,?)
  `)
};

function tx(fn) {
  db.exec('BEGIN');
  try {
    const result = fn();
    db.exec('COMMIT');
    return result;
  } catch (error) {
    db.exec('ROLLBACK');
    throw error;
  }
}

module.exports = {
  db,
  q,
  tx
};
