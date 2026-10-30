const express = require('express');
const crypto = require('crypto');
const { q } = require('../database/db');
const { createUser, openSession, publicUser, setUserPin, removeUserPin, SESSION_TTL_MS } = require('../services/userService');
const { verifyPassword, hashPassword } = require('../utils/crypto');
const { loginMiddleware, pinLimiter } = require('../middleware/rateLimiter');
const { authenticateToken } = require('../middleware/auth');
const { sockets } = require('../websocket/socket');

const router = express.Router();

const fail = (res, code, message) => res.status(code).json({ ok: false, message });

router.post('/login', loginMiddleware, (req, res) => {
  const identifier = String(
    req.body.email || req.body.account || req.body.identifier || ''
  ).trim().toLowerCase();

  const password = req.body.password != null ? String(req.body.password) : null;
  const pin = req.body.pin != null ? String(req.body.pin).trim() : null;

  if (pin && !password) {
    return handlePinLogin(req, res, identifier, pin);
  }

  const user = q.userByEmail.get(identifier);

  if (!user || !verifyPassword(user, password || '')) {
    return fail(res, 401, 'Correo o contraseña incorrectos');
  }

  return checkSingleSession(req, res, user, 'Bienvenido');
});

router.post('/login-pin', pinLimiter, (req, res) => {
  const identifier = String(
    req.body.email || req.body.account || req.body.identifier || ''
  ).trim().toLowerCase();
  const pin = String(req.body.pin || '').trim();
  handlePinLogin(req, res, identifier, pin);
});

function checkSingleSession(req, res, user, welcomeMessage) {
  const force = req.body.force === true;
  const existingSessions = q.activeSessionsByUser.all(user.id, Date.now() - SESSION_TTL_MS);

  if (existingSessions.length > 0 && !force) {
    return res.status(200).json({
      ok: false,
      activeSession: true,
      message: 'Tu cuenta ya está abierta en otro dispositivo. Si continúas, se cerrará la sesión anterior.'
    });
  }

  if (existingSessions.length > 0 && force) {
    q.deleteSessionsByUser.run(user.id);

    sockets.forEach((clients) => {
      clients.forEach((ws) => {
        if (ws.userId === user.id || ws.userAccount === user.account) {
          try {
            ws.send(JSON.stringify({
              type: 'SESSION_REPLACED',
              message: 'Tu sesión se cerró porque tu cuenta fue iniciada en otro dispositivo. Si no reconoces esta actividad, cambia tu contraseña.'
            }));
          } catch (_) {}
          try {
            ws.close(1008, 'Sesión reemplazada');
          } catch (_) {}
        }
      });
    });
  }

  res.json({
    ok: true,
    message: welcomeMessage,
    token: openSession(user),
    user: publicUser(user)
  });
}

function handlePinLogin(req, res, identifier, pin) {
  const sPin = String(pin || '').trim();
  if (!identifier) return fail(res, 400, 'Ingresa tu correo o número de cuenta');
  if (!/^[0-9]{4}$/.test(sPin)) return fail(res, 400, 'El PIN debe ser de 4 dígitos numéricos');

  const user = q.userByEmailOrAccount.get(identifier, identifier.toUpperCase());

  if (!user) return fail(res, 401, 'Usuario, cuenta o PIN incorrectos');

  if (!user.pin_hash || !user.pin_salt) {
    return fail(res, 400, 'Esta cuenta no tiene un PIN configurado. Inicia sesión con contraseña.');
  }

  const hash = hashPassword(sPin, user.pin_salt);
  const good = crypto.timingSafeEqual(Buffer.from(hash, 'hex'), Buffer.from(user.pin_hash, 'hex'));

  if (!good) return fail(res, 401, 'Usuario, cuenta o PIN incorrectos');

  return checkSingleSession(req, res, user, 'Bienvenido con PIN');
}

function isWeakPin(pin) {
  const s = String(pin || '').trim();
  if (!/^[0-9]{4}$/.test(s)) return true;
  if (new Set(s).size === 1) return true;
  const weak = [
    '1234', '0123', '2345', '3456', '4567', '5678', '6789',
    '4321', '3210', '5432', '6543', '7654', '8765', '9876',
    '1212', '1122'
  ];
  return weak.includes(s);
}

router.post('/register', (req, res) => {
  const name = String(req.body.name || '').trim();
  const email = String(req.body.email || '').trim().toLowerCase();
  const password = String(req.body.password || '');
  const pin = req.body.pin ? String(req.body.pin).trim() : null;

  if (name.length < 2) return fail(res, 400, 'Ingresa tu nombre');
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return fail(res, 400, 'Correo no válido');
  if (password.length < 8 || password.length > 128 || !/[a-z]/.test(password) || !/[A-Z]/.test(password) || !/\d/.test(password)) {
    return fail(res, 400, 'La contraseña debe tener mínimo 8 caracteres, con mayúscula, minúscula y número');
  }
  if (pin) {
    if (!/^[0-9]{4}$/.test(pin)) {
      return fail(res, 400, 'El PIN debe ser de 4 dígitos numéricos');
    }
    if (isWeakPin(pin)) {
      return fail(res, 400, 'Elige un PIN menos fácil de adivinar (ej. evita 1234 o números repetidos)');
    }
  }
  if (q.userByEmail.get(email)) return fail(res, 409, 'Ese correo ya está registrado');

  const user = createUser(name, email, password, pin);

  res.status(201).json({
    ok: true,
    message: 'Cuenta creada',
    token: openSession(user),
    user: publicUser(user)
  });
});

router.post('/logout', authenticateToken, (req, res) => {
  q.deleteSession.run(req.sessionTokenHash);

  sockets.forEach((clients) => {
    clients.forEach((ws) => {
      if (ws.sessionTokenHash === req.sessionTokenHash) {
        ws.close(1008, 'Sesión cerrada');
      }
    });
  });

  res.json({ ok: true, message: 'Sesión cerrada' });
});

router.post('/set-pin', authenticateToken, (req, res) => {
  const password = String(req.body.password || '');
  const pin = String(req.body.pin || '').trim();

  if (isWeakPin(pin)) return fail(res, 400, 'Elige un PIN menos fácil de adivinar (ej. evita 1234 o números repetidos)');

  // Si el usuario ya tiene un PIN configurado, exigir contraseña para cambiarlo
  if (req.user.pin_hash && req.user.pin_salt) {
    if (!password) return fail(res, 400, 'Ingresa tu contraseña actual');
    if (!verifyPassword(req.user, password)) return fail(res, 401, 'Contraseña actual incorrecta');
  } else if (password) {
    if (!verifyPassword(req.user, password)) return fail(res, 401, 'Contraseña actual incorrecta');
  }

  setUserPin(req.user.id, pin);
  res.json({ ok: true, message: 'PIN guardado correctamente' });
});

router.post('/remove-pin', authenticateToken, (req, res) => {
  const password = String(req.body.password || '');

  if (!password) return fail(res, 400, 'Ingresa tu contraseña actual');
  if (!verifyPassword(req.user, password)) return fail(res, 401, 'Contraseña actual incorrecta');

  removeUserPin(req.user.id);
  res.json({ ok: true, message: 'PIN eliminado correctamente' });
});

module.exports = router;
