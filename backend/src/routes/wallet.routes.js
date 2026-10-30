const express = require('express');
const { q, tx } = require('../database/db');
const { validAmount, ref, round } = require('../utils/crypto');
const { authenticateToken } = require('../middleware/auth');
const { push } = require('../websocket/socket');

const router = express.Router();

const fail = (res, code, message) => res.status(code).json({ ok: false, message });

router.post('/deposit', authenticateToken, (req, res) => {
  const { amount } = req.body;

  if (!validAmount(amount)) return fail(res, 400, 'Monto inválido');

  const balance = round(req.user.balance + amount);
  const reference = ref('DEP');

  tx(() => {
    q.setBalance.run(balance, req.user.id);
    q.insertMovement.run(req.user.id, 'DEPOSIT', amount, null, reference, Date.now());
  });

  res.json({ ok: true, message: 'Depósito exitoso', balance, reference });
});

router.post('/withdraw', authenticateToken, (req, res) => {
  const { amount } = req.body;

  if (!validAmount(amount)) return fail(res, 400, 'Monto inválido');
  if (req.user.balance < amount) return fail(res, 409, 'Saldo insuficiente');

  const balance = round(req.user.balance - amount);
  const reference = ref('RET');

  tx(() => {
    q.setBalance.run(balance, req.user.id);
    q.insertMovement.run(req.user.id, 'WITHDRAW', amount, null, reference, Date.now());
  });

  res.json({ ok: true, message: 'Retiro exitoso', balance, reference });
});

function normalizeAccount(input) {
  let s = String(input || '').trim().toUpperCase().replace(/[–—]/g, '-');
  s = s.replace(/^ACC[\s\-]*/, 'ACC-');
  if (!s.startsWith('ACC-') && /^[\dBILOSZ]+$/i.test(s)) {
    s = 'ACC-' + s;
  }
  if (s.startsWith('ACC-')) {
    let numPart = s.substring(4);
    numPart = numPart.replace(/B/g, '8')
                     .replace(/O/g, '0')
                     .replace(/[IL]/g, '1')
                     .replace(/S/g, '5')
                     .replace(/Z/g, '2');
    s = 'ACC-' + numPart;
  }
  return s;
}

router.post('/account/validate', authenticateToken, (req, res) => {
  const rawInput = String(req.body.to || '').trim();
  if (!rawInput) return fail(res, 400, 'Ingresa el número de cuenta');

  const cleanAccount = normalizeAccount(rawInput);
  const dest = q.userByAccount.get(cleanAccount) ||
               q.userByEmailOrAccount.get(rawInput.toLowerCase(), cleanAccount);

  if (!dest) {
    return fail(res, 404, 'La cuenta de destino no existe');
  }

  if (dest.id === req.user.id || dest.account === req.user.account) {
    return fail(res, 400, 'No puedes transferirte a ti mismo');
  }

  res.json({
    ok: true,
    account: dest.account,
    name: dest.name
  });
});

router.post('/transfer', authenticateToken, (req, res) => {
  const rawInput = String(req.body.to || '').trim();
  const { amount } = req.body;

  if (!rawInput || !validAmount(amount)) return fail(res, 400, 'Datos inválidos');

  const cleanAccount = normalizeAccount(rawInput);
  const dest = q.userByAccount.get(cleanAccount) ||
               q.userByEmailOrAccount.get(rawInput.toLowerCase(), cleanAccount);

  if (!dest) return fail(res, 404, 'La cuenta de destino no existe');
  if (dest.id === req.user.id || dest.account === req.user.account) {
    return fail(res, 400, 'No puedes transferirte a ti mismo');
  }
  if (req.user.balance < amount) return fail(res, 409, 'Saldo insuficiente');

  const mine = round(req.user.balance - amount);
  const theirs = round(dest.balance + amount);
  const reference = ref('TRF');
  const now = Date.now();

  tx(() => {
    q.setBalance.run(mine, req.user.id);
    q.setBalance.run(theirs, dest.id);

    q.insertMovement.run(req.user.id, 'TRANSFER_OUT', amount, dest.name, reference, now);
    q.insertMovement.run(dest.id, 'TRANSFER_IN', amount, req.user.name, reference, now);
  });

  push(dest.account, {
    type: 'TRANSFER_IN',
    from: req.user.name,
    fromAccount: req.user.account,
    amount,
    balance: theirs,
    timestamp: now,
    reference
  });

  res.json({ ok: true, message: 'Transferencia enviada', balance: mine, reference, timestamp: now });
});

module.exports = router;
